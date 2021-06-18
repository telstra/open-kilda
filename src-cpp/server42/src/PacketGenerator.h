#ifndef SERVER42_PACKETGENERATOR_H
#define SERVER42_PACKETGENERATOR_H

#include <pcapplusplus/DpdkDevice.h>

#include <boost/cstdint.hpp>

#include "FlowId.h"
#include "PacketPool.h"
#include "FlowMetadata.h"
#include "IslId.h"
#include "IslMetadata.h"

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


    using flow_pool_t = PacketPool<MBufAllocator, flow_endpoint_t, FlowMetadataContainer>;
    using isl_pool_t = PacketPool<MBufAllocator, isl_endpoint_t, IslMetadataContainer>;

    struct FlowCreateArgument{
        flow_pool_t& flow_pool;
        pcpp::DpdkDevice* device;
        const std::string& dst_mac;
        boost::int64_t tunnel_id;
        boost::int64_t inner_tunnel_id;
        boost::int64_t transit_tunnel_id;
        boost::uint16_t udp_src_port;
        const std::string& flow_id;
        bool direction;
        boost::int32_t hash;

        [[nodiscard]] inline const char* direction_str() const {
            return direction ? "reverse" : "forward";
        }
    };

    void generate_and_add_packet_for_flow(const FlowCreateArgument& arg);

    inline flow_endpoint_t get_flow_id(const FlowCreateArgument &flow) {
        return make_flow_endpoint(flow.flow_id, flow.direction);
    }

    struct IslCreateArgument{
        isl_pool_t& isl_pool;
        pcpp::DpdkDevice* device;
        const std::string& dst_mac;
        boost::int64_t transit_tunnel_id;
        boost::uint16_t udp_src_port;
        const std::string& switch_id;
        boost::uint16_t port;
        boost::int32_t hash;
    };

    void generate_and_add_packet_for_isl(const IslCreateArgument& arg);

    inline isl_endpoint_t get_isl_id(const IslCreateArgument &isl) {
        return make_isl_endpoint(isl.switch_id, isl.port);
    }
}

#endif //SERVER42_PACKETGENERATOR_H
