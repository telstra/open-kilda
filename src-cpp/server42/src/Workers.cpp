#include "Workers.h"

#include <cstdint>
#include <chrono>
#include <map>
#include <random>
#include <thread>

#include <netinet/in.h>

#include <boost/atomic.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/log/trivial.hpp>
#include <boost/format.hpp>

#include <rte_branch_prediction.h>
#include <rte_ring.h>
#include <rte_mbuf.h>
#include <rte_mbuf_dyn.h>

#include <pcapplusplus/UdpLayer.h>
#include <pcapplusplus/MBufRawPacket.h>
#include <pcapplusplus/DpdkDevice.h>
#include <pcapplusplus/EthLayer.h>

#include <zmq.hpp>
#include <queue>

#include "statistics.pb.h"

#include "Config.h"
#include "Payload.h"

using namespace std::literals::chrono_literals;

namespace org::openkilda {

    uint64_t get_current_timestamp() {
        auto now = std::chrono::time_point_cast<std::chrono::nanoseconds>(
                std::chrono::high_resolution_clock::now());
        auto duration = now.time_since_epoch();
        uint64_t seconds = std::chrono::duration_cast<std::chrono::seconds>(duration).count();
        uint64_t nanoseconds = std::chrono::duration_cast<std::chrono::nanoseconds>(duration - std::chrono::seconds(seconds)).count();
        uint64_t timestamp = (seconds << 32ul) + (nanoseconds & 0xFFFFFFFF);
        return timestamp;
    }

    void do_write_isl_packets(pcpp::DpdkDevice* device,
                              org::openkilda::isl_pool_t& m_isl_pool,
                              std::mutex& m_pool_guard) {

        BOOST_LOG_TRIVIAL(debug) << "tick isl_pool_size: " << m_isl_pool.table.size();

        std::lock_guard<std::mutex> guard(m_pool_guard);
        pcpp::MBufRawPacket **start = m_isl_pool.table.data();
        pcpp::MBufRawPacket **end = start + m_isl_pool.table.size();

        const uint64_t timestamp = htobe64(get_current_timestamp());

        const auto chunk_size = long(Config::chunk_size);
        uint_fast8_t error_count = 0;
        for (pcpp::MBufRawPacket **pos = start; pos < end; pos += chunk_size) {
            uint16_t send = 0;
            uint_fast8_t tries = 0;

            pcpp::MBufRawPacket mbuf_send_buffer[Config::chunk_size];
            pcpp::MBufRawPacket *mbuf_send_buffer_p[Config::chunk_size];

            for (uint_fast8_t i = 0; i < std::min(chunk_size, end - pos); ++i) {
                mbuf_send_buffer[i].initFromRawPacket(*(pos+i), device);
                mbuf_send_buffer_p[i] = &mbuf_send_buffer[i];
                mbuf_send_buffer_p[i]->setFreeMbuf(true);

                //TODO: can be switched to raw memory arithmetics
                pcpp::Packet parsed_packet(mbuf_send_buffer_p[i], false, pcpp::UDP);
                auto *udp_layer = parsed_packet.getLayerOfType<pcpp::UdpLayer>();
                auto *payload = reinterpret_cast<org::openkilda::IslPayload *>(udp_layer->getLayerPayload());
                payload->t0 = timestamp;
            }

            while (send != std::min(chunk_size, end - pos)) {
                send = device->sendPackets(mbuf_send_buffer_p, std::min(chunk_size, end - pos), 0, false);
                if (send != std::min(chunk_size, end - pos)) {
                    if(++tries > 3) {
                        ++error_count;
                        break;
                    }
                }
            }
            if (error_count > 5) {
                BOOST_LOG_TRIVIAL(error) << "Errors while send packets, drop send try";
                break;
            }
        }
    }

    bool write_thread(boost::atomic<bool> &alive,
                      pcpp::DpdkDevice* device,
                      org::openkilda::flow_pool_t& m_flow_pool,
                      std::mutex& m_pool_guard,
                      org::openkilda::isl_pool_t& m_isl_pool) {

        const uint64_t cycles_in_one_second = rte_get_timer_hz();
        const uint64_t cycles_in_500_ms = cycles_in_one_second / 2;

        while (alive.load()) {
            try {
                BOOST_LOG_TRIVIAL(debug) << "tick flow_pool_size: " << m_flow_pool.table.size();

                uint64_t start_tsc = rte_get_timer_cycles();
                {
                    std::lock_guard<std::mutex> guard(m_pool_guard);
                    pcpp::MBufRawPacket **start = m_flow_pool.table.data();
                    pcpp::MBufRawPacket **end = start + m_flow_pool.table.size();

                    const uint64_t timestamp = htobe64(get_current_timestamp());

                    const auto chunk_size = long(Config::chunk_size);
                    uint_fast8_t error_count = 0;
                    for (pcpp::MBufRawPacket **pos = start; pos < end; pos += chunk_size) {
                        uint16_t send = 0;
                        uint_fast8_t tries = 0;

                        pcpp::MBufRawPacket mbuf_send_buffer[Config::chunk_size];
                        pcpp::MBufRawPacket *mbuf_send_buffer_p[Config::chunk_size];

                        for (uint_fast8_t i = 0; i < std::min(chunk_size, end - pos); ++i) {
                            mbuf_send_buffer[i].initFromRawPacket(*(pos+i), device);
                            mbuf_send_buffer_p[i] = &mbuf_send_buffer[i];
                            mbuf_send_buffer_p[i]->setFreeMbuf(true);

                            //TODO: can be switched to raw memory arithmetics
                            pcpp::Packet parsed_packet(mbuf_send_buffer_p[i], false, pcpp::UDP);
                            auto *udp_layer = parsed_packet.getLayerOfType<pcpp::UdpLayer>();
                            auto *payload = reinterpret_cast<org::openkilda::FlowPayload *>(udp_layer->getLayerPayload());
                            payload->t0 = timestamp;
                        }

                        while (send != std::min(chunk_size, end - pos)) {
                            send = device->sendPackets(mbuf_send_buffer_p, std::min(chunk_size, end - pos), 0, false);
                            if (send != std::min(chunk_size, end - pos)) {
                                if(++tries > 3) {
                                    ++error_count;
                                    break;
                                }
                            }
                        }
                        if (error_count > 5) {
                            BOOST_LOG_TRIVIAL(error) << "Errors while send packets, drop send try";
                            break;
                        }
                    }
                }

                do_write_isl_packets(device, m_isl_pool, m_pool_guard);

                while (true) {
                    uint64_t current_tsc = rte_get_timer_cycles();
                    if (unlikely(current_tsc - start_tsc >= cycles_in_500_ms)) {
                        break;
                    }
                }
            } catch (std::exception &exception) {
                BOOST_LOG_TRIVIAL(error) << "Error " << exception.what();
            } catch (...) {
                BOOST_LOG_TRIVIAL(error) << "unhandled Error";
            };
        }

        return true;
    }

    bool read_thread(
            boost::atomic<bool> &alive,
            pcpp::DpdkDevice *device,
            boost::shared_ptr<rte_ring> ring,
            int mbuf_dyn_timestamp_offset) {

        BOOST_LOG_TRIVIAL(info) << "read_thread started ";
        pcpp::MBufRawPacket mbuf_raw_table[Config::chunk_size];

        pcpp::MBufRawPacket *mbuf_raw_ptr_table[Config::chunk_size] = {};

        rte_mbuf *mbuf_table[Config::chunk_size] = {};

        for (int i = 0; i < Config::chunk_size; ++i) {
            mbuf_raw_ptr_table[i] = &mbuf_raw_table[i];
            mbuf_raw_ptr_table[i]->setFreeMbuf(false);
        }

        while (alive.load()) {
            const uint32_t table_size = device->receivePackets(mbuf_raw_ptr_table, Config::chunk_size, 0);

            if (unlikely(table_size == 0)) {
                continue;
            }

            uint64_t timestamp = get_current_timestamp();

            BOOST_LOG_TRIVIAL(debug) << "read_thread recived packets " << table_size;

            for (uint32_t i = 0; i < table_size; ++i) {
                mbuf_table[i] = mbuf_raw_ptr_table[i]->getMBuf();
                uint64_t* ptr = RTE_MBUF_DYNFIELD(mbuf_table[i], mbuf_dyn_timestamp_offset, uint64_t *);
                *ptr = timestamp;
            }

            uint32_t enqueued = rte_ring_sp_enqueue_bulk(ring.get(), reinterpret_cast<void *const *>(mbuf_table), table_size,
                                                         nullptr);

            if (unlikely(enqueued == 0)) {
                //TODO: add_to_statistics
                BOOST_LOG_TRIVIAL(error) << "ring is full discard " << table_size << " packets";
                for (uint32_t i = 0; i < table_size; ++i) {
                    mbuf_raw_ptr_table[i]->setFreeMbuf(true);
                    mbuf_raw_ptr_table[i]->clear();
                    mbuf_raw_ptr_table[i]->setFreeMbuf(false);
                }
            }
        }
        return true;
    }

    bool process_thread(uint32_t core_id,
            boost::atomic<bool> &alive,
            boost::shared_ptr<rte_ring> rx_ring,
            uint16_t zmq_port,
            const pcpp::MacAddress &src_mac,
            int mbuf_dyn_timestamp_offset) {

        using Bucket = server42::stats::messaging::LatencyPacketBucket;

        zmq::context_t context(1);
        context.setctxopt(ZMQ_THREAD_AFFINITY_CPU_ADD, core_id);
        zmq::socket_t socket(context, ZMQ_PUSH);

        std::string endpoint = str(boost::format("tcp://*:%1%") % zmq_port);
        socket.bind(endpoint);

        BOOST_LOG_TRIVIAL(info)
            << "process_thread on core_id: " << core_id << " "
            << "bind zmq socket for push on " << endpoint;

        uint64_t packet_id = 0;
        pcpp::Packet dummy_packet;

        rte_mbuf *mbuf_table[Config::chunk_size] = {};

        void **const mbuf_table_prt = reinterpret_cast<void **>(mbuf_table);
        while (alive.load()) {
            try {
                uint32_t table_size =
                        rte_ring_mc_dequeue_bulk(rx_ring.get(), mbuf_table_prt, Config::chunk_size, nullptr);

                if (unlikely(table_size == 0)) {
                    BOOST_LOG_TRIVIAL(debug) << "process_thread table_size 0";
                    socket.send(zmq::str_buffer(""), zmq::send_flags::dontwait);
                    std::this_thread::sleep_for(100ms);
                    continue;
                }

                BOOST_LOG_TRIVIAL(debug) << "process_thread recived packets " << table_size;

                Bucket bucket;

                for (uint32_t i = 0; i < table_size; ++i) {

                    // A macro that points to the start of the data in the mbuf.
                    const uint8_t *mbuf = rte_pktmbuf_mtod(mbuf_table[i], const uint8_t*);
                    pcpp::RawPacket raw_packet(mbuf,
                                               rte_pktmbuf_pkt_len(mbuf_table[i]),
                                               timeval(),
                                               false,
                                               pcpp::LINKTYPE_ETHERNET);

                    pcpp::Packet parsed_packet(&raw_packet);

                    auto eth = parsed_packet.getLayerOfType<pcpp::EthLayer>();
                    auto udp = parsed_packet.getLayerOfType<pcpp::UdpLayer>();

                    if (likely(eth->getDestMac() == src_mac && udp)) {
                        auto udpSrcPort = ntohs(udp->getUdpHeader()->portSrc);
                        if (udpSrcPort == Config::flow_rtt_caught_packet_udp_src_port) {
                            packet_id++;
                            auto payload = reinterpret_cast<const org::openkilda::FlowPayload *>(udp->getLayerPayload());
                            auto packet = bucket.add_flowlatencypacket();

                            std::string flow_id(reinterpret_cast<const char*>(payload) + payload->flow_id_offset, payload->flow_id_length);

                            BOOST_LOG_TRIVIAL(debug) << "flow_id: " << flow_id << " "
                                                     << "t0 raw: " << payload->t0 << " "
                                                     << "t1 raw: " << payload->t1 << " "
                                                     << "t0: " << be64toh(payload->t0) << " "
                                                     << "t1: " << be64toh(payload->t1) << " "
                                                     << "packet_id: " << packet_id << " "
                                                     << "direction: " << payload->direction;

                            packet->set_flow_id(flow_id);
                            packet->set_t0(be64toh(payload->t0));

                            if (payload->t1) {
                                packet->set_t1(be64toh(payload->t1));
                            } else {
                                uint64_t* timestamp = RTE_MBUF_DYNFIELD(mbuf_table[i], mbuf_dyn_timestamp_offset, uint64_t *);
                                packet->set_t1(*timestamp);
                            }
                            packet->set_packet_id(packet_id);
                            packet->set_direction(payload->direction);
                            BOOST_LOG_TRIVIAL(debug) << packet->DebugString();
                        } else if (udpSrcPort == Config::isl_rtt_caught_packet_catch_udp_src_port
                                && udp->getLayerPayloadSize() == sizeof(IslPayload)) {
                            packet_id++;
                            auto payload = reinterpret_cast<const org::openkilda::IslPayload *>(udp->getLayerPayload());
                            auto packet = bucket.add_isllatencypacket();

                            BOOST_LOG_TRIVIAL(debug) << "switch_id: " << payload->switch_id << " "
                                                     << "port: " << payload->port << " "
                                                     << "t0 raw: " << payload->t0 << " "
                                                     << "t1 raw: " << payload->t1 << " "
                                                     << "t0: " << be64toh(payload->t0) << " "
                                                     << "t1: " << be64toh(payload->t1) << " "
                                                     << "packet_id: " << packet_id;

                            packet->set_switch_id(payload->switch_id);
                            packet->set_port(payload->port);
                            packet->set_t0(be64toh(payload->t0));

                            if (payload->t1) {
                                packet->set_t1(be64toh(payload->t1));
                            } else {
                                uint64_t* timestamp = RTE_MBUF_DYNFIELD(mbuf_table[i], mbuf_dyn_timestamp_offset, uint64_t *);
                                packet->set_t1(*timestamp);
                            }
                            packet->set_packet_id(packet_id);
                            BOOST_LOG_TRIVIAL(debug) << packet->DebugString();
                        } else {
                            BOOST_LOG_TRIVIAL(error)
                                << "drop packet by invalid payload size: " << udp->getLayerPayloadSize() << " "
                                << "or by invalid UDP src port: " << udpSrcPort;
                        }
                    } else {
                        if (eth->getDestMac() != src_mac) {
                            BOOST_LOG_TRIVIAL(error)
                                << "drop packet by invalid mac dst: " << eth->getDestMac().toString() << " "
                                << "expected: " << src_mac.toString();
                        } else {
                            BOOST_LOG_TRIVIAL(error)
                                << "drop packet by missed udp layer";
                        }
                    }

                    rte_pktmbuf_free(mbuf_table[i]);
                }

                if (bucket.flowlatencypacket_size() || bucket.isllatencypacket_size()) {

                    zmq::message_t message(bucket.ByteSizeLong());
                    BOOST_LOG_TRIVIAL(debug) << "latency_bucket <" << bucket.DebugString() << ">";
                    bucket.SerializeToArray(message.data(), message.size());

                    /*
                     * When a ZMQ_PUSH socket enters the mute state due to having reached the high water mark for all
                     * downstream nodes, or if there are no downstream nodes at all, then any zmq_send(3) operations on
                     * the socket shall block until the mute state ends or at least one downstream node becomes available
                     * for sending; messages are not discarded.
                     */
                    while (!socket.send(message, zmq::send_flags::dontwait) && alive) {}

                } else {
                    BOOST_LOG_TRIVIAL(info) << "latency_bucket packet_size==0 " << bucket.DebugString() << "\n" << std::flush;
                }

            } catch (zmq::error_t &exception) {
                BOOST_LOG_TRIVIAL(error) << "ZMQ Error " << exception.what();
            } catch (std::exception &exception) {
                BOOST_LOG_TRIVIAL(error) << "Error " << exception.what();
            }
        }

        return true;
    }


    bool echo_thread(boost::atomic<bool> &alive,
                     pcpp::DpdkDevice* device) {

        pcpp::MBufRawPacket *rx_mbuf[Config::chunk_size] = {};

        const pcpp::MacAddress src(device->getMacAddress());

        std::map<std::string, uint64_t> fake_duration;
        std::random_device rd{};
        std::uniform_int_distribution<int> base_latency(0, 900); // in ms

        typedef std::tuple<uint64_t, std::shared_ptr<pcpp::MBufRawPacket>> mbuf_container_t;

        // Using lambda to compare elements.
        auto cmp = [](mbuf_container_t& left, mbuf_container_t& right) { return std::get<0>(left) > std::get<0>(right); };
        std::priority_queue<mbuf_container_t, std::vector<mbuf_container_t>, decltype(cmp) > queue(cmp);

        const uint64_t cycles_in_one_second = rte_get_timer_hz();
        const uint64_t cycles_in_1_ms = cycles_in_one_second / 1000;

        while (alive.load()) {
            uint16_t num_of_packets = device->receivePackets(rx_mbuf, Config::chunk_size, 0);

            uint64_t start_tsc = rte_get_timer_cycles();

            for (uint16_t i = 0; i < num_of_packets; ++i) {

                pcpp::Packet parsed_packet(rx_mbuf[i]);

                auto *eth = parsed_packet.getLayerOfType<pcpp::EthLayer>();

                eth->setDestMac(eth->getSourceMac());
                eth->setSourceMac(src);

                auto *udp_layer = parsed_packet.getLayerOfType<pcpp::UdpLayer>();
                auto udpDstPort = ntohs(udp_layer->getUdpHeader()->portDst);

                if (udpDstPort == Config::flow_rtt_generated_packet_udp_dst_port) {
                    udp_layer->getUdpHeader()->portSrc = htons(Config::flow_rtt_caught_packet_udp_src_port);

                    auto *payload = reinterpret_cast<org::openkilda::FlowPayload *>(udp_layer->getLayerPayload());

                    std::string flow_id(reinterpret_cast<const char*>(payload) + payload->flow_id_offset, payload->flow_id_length);

                    if (!fake_duration.count(flow_id)) {
                        fake_duration[flow_id] = base_latency(rd) * cycles_in_1_ms;
                    }

                    std::shared_ptr <pcpp::MBufRawPacket> mbuf_raw_ptr(new pcpp::MBufRawPacket());

                    mbuf_raw_ptr->initFromRawPacket(rx_mbuf[i], device);

                    uint64_t latency_time = fake_duration[flow_id];
                    const uint64_t delta_based_direction = 3 * cycles_in_1_ms;
                    if (payload->direction) {
                        latency_time += delta_based_direction;
                    } else {
                        latency_time -= delta_based_direction;
                    }

                    queue.push(std::make_tuple(start_tsc + latency_time, mbuf_raw_ptr));
                } else if (udpDstPort == Config::isl_rtt_generated_packet_udp_dst_port
                        && udp_layer->getLayerPayloadSize() == sizeof(IslPayload)) {
                    udp_layer->getUdpHeader()->portSrc = htons(Config::isl_rtt_caught_packet_catch_udp_src_port);

                    auto *payload = reinterpret_cast<org::openkilda::IslPayload *>(udp_layer->getLayerPayload());

                    std::string isl_key(str(boost::format("%1%-%2%") % payload->switch_id % payload->port));

                    if (!fake_duration.count(isl_key)) {
                        fake_duration[isl_key] = base_latency(rd) * cycles_in_1_ms;
                    }

                    std::shared_ptr <pcpp::MBufRawPacket> mbuf_raw_ptr(new pcpp::MBufRawPacket());

                    mbuf_raw_ptr->initFromRawPacket(rx_mbuf[i], device);

                    uint64_t latency_time = fake_duration[isl_key];
                    const uint64_t delta_based_direction = 3 * cycles_in_1_ms;
                    latency_time += delta_based_direction;

                    queue.push(std::make_tuple(start_tsc + latency_time, mbuf_raw_ptr));
                } else {
                    BOOST_LOG_TRIVIAL(error)
                            << "drop packet by invalid payload size: " << udp_layer->getLayerPayloadSize() << " "
                            << "or by invalid UDP dst port: " << udpDstPort;
                }
            }


            std::vector<pcpp::MBufRawPacket *> send_buf;
            std::vector<std::shared_ptr<pcpp::MBufRawPacket>> holder;

            while (queue.size()) {
                uint64_t ts = std::get<0>(queue.top());
                if (start_tsc > ts) {
                    auto ptr = std::get<1>(queue.top());
                    send_buf.push_back(ptr.get());
                    holder.push_back(ptr);
                    queue.pop();
                } else {
                    break;
                }
            }

            if (send_buf.size() > 0) {
                device->sendPackets(send_buf.data(), send_buf.size(), 0, false);
            }
        }

        return true;
    }
}