#include "WriteThread.h"
#include "Payload.h"

#include <chrono>
#include <thread>

#include <stdlib.h>
#include <pcapplusplus/Packet.h>
#include <pcapplusplus/EthLayer.h>
#include <pcapplusplus/VlanLayer.h>
#include <pcapplusplus/IPv4Layer.h>
#include <pcapplusplus/TcpLayer.h>
#include <pcapplusplus/HttpLayer.h>
#include <pcapplusplus/UdpLayer.h>
#include <pcapplusplus/PayloadLayer.h>
#include <pcapplusplus/PcapFileDevice.h>
#include <netinet/in.h>
#include <FlowPool.h>
#include <sstream>
#include <iostream>
#include <rte_cycles.h>


WriteThread::WriteThread(pcpp::DpdkDevice *device, org::openkilda::flow_pool_t& pool, std::mutex& pool_guard) :
        m_Device(device), m_Stop(true), m_CoreId(MAX_NUM_OF_CORES + 1), m_pool(pool), m_pool_guard(pool_guard) {
}


bool WriteThread::run(uint32_t coreId) {
    // Register coreId for this worker
    m_CoreId = coreId;
    m_Stop = false;

    pcpp::MacAddress zero("00:00:00:00:00:00");
    pcpp::MacAddress dst("88:12:9c:7b:89:62");
    pcpp::MacAddress random_src("c4:f0:c4:3e:7e:0f");
    pcpp::MacAddress src(m_Device->getMacAddress());

    pcpp::EthLayer newEthernetLayer(random_src, dst);


    pcpp::IPv4Layer newIPLayer(pcpp::IPv4Address(std::string("192.168.0.1/24")),
                               pcpp::IPv4Address(std::string("192.168.1.1")));

    pcpp::UdpLayer newUdpLayer(1234, 5678);
    pcpp::Packet newPacket(64);

    newPacket.addLayer(&newEthernetLayer);
    newPacket.addLayer(&newIPLayer);
    newPacket.addLayer(&newUdpLayer);

    org::openkilda::Payload payload;

    using time_stamp = std::chrono::time_point<std::chrono::high_resolution_clock ,
            std::chrono::seconds>;

    time_stamp ts = std::chrono::time_point_cast<std::chrono::seconds>(std::chrono::high_resolution_clock::now());

    payload.t0 = htonl(ts.time_since_epoch().count());
    payload.t1 = 0;
    std::string flowid("flow1");

    size_t length = flowid.copy(payload.flow_id, sizeof(payload.flow_id) - 1);
    payload.flow_id[length] = '\0';

    pcpp::PayloadLayer payloadLayer(reinterpret_cast<uint8_t*>(&payload), sizeof(payload), false);
    newPacket.addLayer(&payloadLayer);
    newPacket.computeCalculateFields();
    pcpp::MBufRawPacket buff2[64];

    for (auto & i : buff2) {
        i.initFromRawPacket(newPacket.getRawPacketReadOnly(), m_Device);
    }

    pcpp::MBufRawPacket *mBufRawPacket[64] = {};

    for (uint i = 0; i < 64; ++i) {
        mBufRawPacket[i] = &buff2[i];
    }

    const uint64_t cycles_in_one_second = rte_get_timer_hz();
    const uint64_t cycles_in_500_ms = cycles_in_one_second / 2;
    // endless loop, until asking the thread to stop
    while (!m_Stop) {
        try {
            auto now = std::chrono::time_point_cast<std::chrono::milliseconds>(
                    std::chrono::high_resolution_clock::now());
            auto duration = now.time_since_epoch();
            long millis = std::chrono::duration_cast<std::chrono::milliseconds>(duration).count();
            std::cout
            << "tick " << millis / 1000 << "." << millis % 1000 << " "
            << "pool_size " << m_pool.table.size() << "\n"
            << std::flush;

            uint64_t start_tsc = rte_rdtsc();
            {
                std::lock_guard<std::mutex> guard(m_pool_guard);
                pcpp::MBufRawPacket **start = m_pool.table.data();
                pcpp::MBufRawPacket **end = start + m_pool.table.size();

                const int64_t chunk_size = 32L;
                uint_fast8_t error_count = 0;
                for (pcpp::MBufRawPacket **pos = start; pos < end; pos += chunk_size) {
                    uint16_t send = 0;
                    uint_fast8_t tries = 0;

                    pcpp::MBufRawPacket mbuf_send_buffer[32];
                    pcpp::MBufRawPacket *mbuf_send_buffer_p[32];

                    for (uint_fast8_t i = 0; i < std::min(chunk_size, end - pos); ++i) {
                        mbuf_send_buffer[i].initFromRawPacket(*(pos+i), m_Device);
                        mbuf_send_buffer_p[i] = &mbuf_send_buffer[i];
                        mbuf_send_buffer_p[i]->setFreeMbuf(true);
                    }


                    while (send != std::min(chunk_size, end - pos)) {
                        send = m_Device->sendPackets(mbuf_send_buffer_p, std::min(chunk_size, end - pos), 0, false);
                        if (send != std::min(chunk_size, end - pos)) {
                            if(++tries > 3) {
                                ++error_count;
                                break;
                            }
                        }
                    }
                    if (error_count > 5) {
                        std::cerr << "Errors while send packets, drop send try\n";
                        break;
                    }
                }
            }
            while (true) {
                uint64_t current_tsc = rte_rdtsc();
                if (unlikely(current_tsc - start_tsc >= cycles_in_500_ms)) {
                    break;
                }
            }
        } catch (std::exception &exception) {
            std::cerr << "Error " << exception.what() << "\n";
        } catch (...) {
            std::cerr << "unhandled Error\n";
        };

    }

    std::cerr << "Thread exit\n";

    return true;
}

void WriteThread::stop() {
    m_Stop = true;
}

uint32_t WriteThread::getCoreId() {
    return m_CoreId;
}
