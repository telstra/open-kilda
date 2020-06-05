#include "PacketGenerator.h"

#include <boost/log/trivial.hpp>

#include <pcapplusplus/EthLayer.h>
#include <pcapplusplus/VlanLayer.h>
#include <pcapplusplus/UdpLayer.h>
#include <pcapplusplus/IPv4Layer.h>
#include <pcapplusplus/PayloadLayer.h>

#include "Payload.h"
#include "Config.h"

namespace org::openkilda {

    void generate_and_add_packet_for_flow(const FlowCreateArgument& arg) {

        pcpp::Packet newPacket(64);

        pcpp::MacAddress dst(arg.dst_mac);
        pcpp::MacAddress src(arg.device->getMacAddress());
        pcpp::EthLayer newEthernetLayer(src, dst);
        newPacket.addLayer(&newEthernetLayer);

        uint16_t nextType = arg.tunnel_id ? PCPP_ETHERTYPE_VLAN : PCPP_ETHERTYPE_IP;

        pcpp::VlanLayer newVlanLayer(arg.transit_tunnel_id, false, 1, nextType);
        if (arg.transit_tunnel_id) {
            newPacket.addLayer(&newVlanLayer);
        }

        pcpp::VlanLayer newVlanLayer2(arg.tunnel_id, false, 1, PCPP_ETHERTYPE_IP);
        if (arg.tunnel_id) {
            newPacket.addLayer(&newVlanLayer2);
        }

        pcpp::IPv4Layer newIPLayer(pcpp::IPv4Address(std::string("192.168.0.1")),
                                   pcpp::IPv4Address(std::string("192.168.1.1")));

        newIPLayer.getIPv4Header()->timeToLive = 128;
        newPacket.addLayer(&newIPLayer);

        pcpp::UdpLayer newUdpLayer(arg.udp_src_port, Config::generated_packet_udp_dst_port);
        newPacket.addLayer(&newUdpLayer);

        Payload payload{};

        size_t length = arg.flow_id.copy(payload.flow_id, sizeof(payload.flow_id) - 1);
        payload.flow_id[length] = '\0';

        payload.direction = arg.direction;

        pcpp::PayloadLayer payloadLayer(reinterpret_cast<uint8_t *>(&payload), sizeof(payload), false);
        newPacket.addLayer(&payloadLayer);
        newPacket.computeCalculateFields();

        arg.flow_pool.add_flow(arg.flow_id,
                           org::openkilda::MBufAllocator::allocate(newPacket.getRawPacket(),
                                                                   arg.device));
    }
}