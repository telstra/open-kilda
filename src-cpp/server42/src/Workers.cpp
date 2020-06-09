#include "Workers.h"

#include <cstdint>
#include <chrono>
#include <map>
#include <random>

#include <netinet/in.h>

#include <boost/atomic.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/log/trivial.hpp>
#include <boost/format.hpp>

#include <rte_branch_prediction.h>
#include <rte_ring.h>
#include <rte_mbuf.h>

#include <pcapplusplus/UdpLayer.h>
#include <pcapplusplus/MBufRawPacket.h>
#include <pcapplusplus/DpdkDevice.h>
#include <pcapplusplus/EthLayer.h>

#include <zmq.hpp>

#include "statistics.pb.h"

#include "Config.h"
#include "Payload.h"


namespace org::openkilda {

    bool write_thread(boost::atomic<bool> &alive,
                      pcpp::DpdkDevice* device,
                      org::openkilda::flow_pool_t& m_pool,
                      std::mutex& m_pool_guard) {

        const uint64_t cycles_in_one_second = rte_get_timer_hz();
        const uint64_t cycles_in_500_ms = cycles_in_one_second / 2;

        while (alive.load()) {
            try {
                BOOST_LOG_TRIVIAL(info) << "tick pool_size: " << m_pool.table.size();

                uint64_t start_tsc = rte_get_timer_cycles();
                {
                    std::lock_guard<std::mutex> guard(m_pool_guard);
                    pcpp::MBufRawPacket **start = m_pool.table.data();
                    pcpp::MBufRawPacket **end = start + m_pool.table.size();

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
            boost::shared_ptr<rte_ring> ring) {

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

            BOOST_LOG_TRIVIAL(debug) << "read_thread recived packets " << table_size;

            for (uint32_t i = 0; i < table_size; ++i) {
                mbuf_table[i] = mbuf_raw_ptr_table[i]->getMBuf();
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
            const pcpp::MacAddress &src_mac) {

        using Bucket = server42::stats::messaging::flowrtt::FlowLatencyPacketBucket;

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
                        if (udp->getLayerPayloadSize() == sizeof(Payload)) {
                            packet_id++;
                            auto payload = reinterpret_cast<const org::openkilda::Payload *>(udp->getLayerPayload());
                            auto packet = bucket.add_packet();

                            BOOST_LOG_TRIVIAL(debug) << "flow_id: " << payload->flow_id << " "
                                                     << "t0 raw: " << payload->t0 << " "
                                                     << "t1 raw: " << payload->t1 << " "
                                                     << "t0: " << be64toh(payload->t0) << " "
                                                     << "t1: " << be64toh(payload->t1) << " "
                                                     << "packet_id: " << packet_id << " "
                                                     << "direction: " << payload->direction;

                            packet->set_flow_id(payload->flow_id);
                            packet->set_t0(be64toh(payload->t0));
                            packet->set_t1(be64toh(payload->t1));
                            packet->set_packet_id(packet_id);
                            packet->set_direction(payload->direction);
                            BOOST_LOG_TRIVIAL(debug) << packet->DebugString();
                        } else {
                            BOOST_LOG_TRIVIAL(error)
                                << "drop packet by invalid payload size: " << udp->getLayerPayloadSize() << " "
                                << "expected: " << sizeof(Payload);
                        }
                    } else {
                        if (eth->getDestMac() != src_mac) {
                            BOOST_LOG_TRIVIAL(error)
                                << "drop packet by invalid mac dst: " << eth->getDestMac().toString() << " "
                                << "expected :" << src_mac.toString();
                        } else {
                            BOOST_LOG_TRIVIAL(error)
                                << "drop packet by missed udp layer";
                        }
                    }

                    rte_pktmbuf_free(mbuf_table[i]);
                }

                if (bucket.packet_size()) {

                    zmq::message_t message(bucket.ByteSizeLong());
                    BOOST_LOG_TRIVIAL(debug) << "flow_bucket <" << bucket.DebugString() << ">";
                    bucket.SerializeToArray(message.data(), message.size());

                    /*
                     * When a ZMQ_PUSH socket enters the mute state due to having reached the high water mark for all
                     * downstream nodes, or if there are no downstream nodes at all, then any zmq_send(3) operations on
                     * the socket shall block until the mute state ends or at least one downstream node becomes available
                     * for sending; messages are not discarded.
                     */
                    while (!socket.send(message, ZMQ_DONTWAIT) && alive) {}

                } else {
                    BOOST_LOG_TRIVIAL(info) << "flow_bucket packet_size==0 " << bucket.DebugString() << "\n" << std::flush;
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
        std::uniform_int_distribution<int> base_latency(10000, 20000);
        std::uniform_int_distribution<int> random_latency(0, 1000);


        while (alive.load()) {
            uint16_t num_of_packets = device->receivePackets(rx_mbuf, Config::chunk_size, 0);

            for (uint16_t i = 0; i < num_of_packets; ++i) {

                pcpp::Packet parsed_packet(rx_mbuf[i]);

                auto *eth = parsed_packet.getLayerOfType<pcpp::EthLayer>();

                eth->setDestMac(eth->getSourceMac());
                eth->setSourceMac(src);

                auto *udp_layer = parsed_packet.getLayerOfType<pcpp::UdpLayer>();

                auto *payload = reinterpret_cast<org::openkilda::Payload *>(udp_layer->getLayerPayload());

                auto now = std::chrono::time_point_cast<std::chrono::nanoseconds>(
                        std::chrono::high_resolution_clock::now());

                auto duration = now.time_since_epoch();
                uint64_t seconds = std::chrono::duration_cast<std::chrono::seconds>(duration).count();
                uint64_t nanoseconds = std::chrono::duration_cast<std::chrono::nanoseconds>(duration).count();

                std::string flow_id(payload->flow_id);

                if (!fake_duration.count(flow_id)) {
                    fake_duration[flow_id] = base_latency(rd);
                }

                uint64_t timestamp = (seconds << 32ul) + (nanoseconds & 0xFFFFFFFF);

                payload->t0 = htobe64(timestamp);
                payload->t1 = htobe64(timestamp +
                                      fake_duration[flow_id] +
                                      random_latency(rd));
            }

            if (num_of_packets > 0) {
                device->sendPackets(rx_mbuf, num_of_packets, 0, false);
            }
        }

        return true;
    }
}