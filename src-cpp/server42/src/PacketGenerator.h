#ifndef SERVER42_PACKETGENERATOR_H
#define SERVER42_PACKETGENERATOR_H

#include <pcapplusplus/DpdkDevice.h>

#include <boost/cstdint.hpp>

#include "FlowPool.h"

namespace org::openkilda {

    class MBufAllocator {
    public:
        typedef pcpp::MBufRawPacket* value_t;

        static  value_t allocate(const pcpp::RawPacket* rawPacket, pcpp::DpdkDevice* device) {
            auto mbuf_raw_packet = new pcpp::MBufRawPacket();
            mbuf_raw_packet->initFromRawPacket(rawPacket, device);
            return mbuf_raw_packet;
        }

        static void dealocate(value_t v) {
            v->setFreeMbuf(true);
            v->clear();
            delete(v);
        }
    };

    typedef FlowPool<MBufAllocator> flow_pool_t;

    struct FlowCreateArgument{
        flow_pool_t& flow_pool;
        pcpp::DpdkDevice* device;
        const std::string& dst_mac;
        boost::int64_t tunnel_id;
        boost::int64_t transit_tunnel_id;
        boost::int64_t udp_src_port;
        const std::string& flow_id;
        bool direction;
    };

    void generate_and_add_packet_for_flow(const FlowCreateArgument& arg);
}

#endif //SERVER42_PACKETGENERATOR_H
