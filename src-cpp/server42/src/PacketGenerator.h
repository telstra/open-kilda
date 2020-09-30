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

    enum class flow_id_members { flow_id, direction };
    using flow_id_t = std::tuple<std::string, bool>;
    using flow_pool_t = FlowPool<MBufAllocator, flow_id_t>;

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

    inline flow_id_t get_flow_id(const std::string& flow_id, bool direction)
    {
        return std::make_tuple(flow_id, direction);
    }

    inline flow_id_t get_flow_id(const FlowCreateArgument& flow)
    {
        return get_flow_id(flow.flow_id, flow.direction);
    }
}

#endif //SERVER42_PACKETGENERATOR_H
