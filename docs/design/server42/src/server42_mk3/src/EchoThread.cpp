#include "EchoThread.h"

#include <pcapplusplus/UdpLayer.h>
#include <pcapplusplus/EthLayer.h>
#include <chrono>
#include <thread>
#include <pcapplusplus/IPv4Layer.h>

EchoThread::EchoThread(pcpp::DpdkDevice *device, uint32_t queue) :
        m_Device(device), m_Stop(true), m_CoreId(MAX_NUM_OF_CORES + 1), m_queue(queue) {
}

bool EchoThread::run(uint32_t coreId) {
    // Register coreId for this worker
    m_CoreId = coreId;
    m_Stop = false;

    // initialize a mbuf packet array
    pcpp::MBufRawPacket *mbufArr[32] = {};
    pcpp::Packet *packerArr[32] = {};


    pcpp::MacAddress zero("00:00:00:00:00:00");

    const pcpp::MacAddress dst("F8:F2:1E:3F:33:21");
    const pcpp::MacAddress src(m_Device->getMacAddress());

    // endless loop, until asking the thread to stop
    pcpp::Packet parsedPacket;
    while (!m_Stop) {
        // receive packets from RX device
        uint16_t numOfPackets = m_Device->receivePackets(mbufArr, 32, m_queue);

        for (int i = 0; i < numOfPackets; ++i) {

            pcpp::EthLayer eth((uint8_t *) mbufArr[i]->getRawData(), mbufArr[i]->getRawDataLen(), &parsedPacket);

            eth.setDestMac(dst);
            eth.setSourceMac(src);
        }

        if (numOfPackets > 0) {
            // send received packet on the TX device
            m_Device->sendPackets(mbufArr, numOfPackets, m_queue, false);
        }
    }

    return true;
}

void EchoThread::stop() {
    m_Stop = true;
}

uint32_t EchoThread::getCoreId() {
    return m_CoreId;
}
