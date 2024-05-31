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

    void generate_and_add_packet_for_flow(const FlowCreateArgument &arg) {

        flow_endpoint_t flow_id_key = get_flow_id(arg);
        auto db = arg.flow_pool.get_metadata_db();
        auto flow_meta = db->get(flow_id_key);
        if (flow_meta && flow_meta->get_hash() == arg.hash) {
            BOOST_LOG_TRIVIAL(debug)
                << "skip add_flow command for " << arg.flow_id << " and switch_id " << arg.switch_id
                << " and direction " << arg.direction_str() << " with hash " << arg.hash << " already exists";
            return;
        }

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

        uint16_t nextType2 = arg.inner_tunnel_id ? PCPP_ETHERTYPE_VLAN : PCPP_ETHERTYPE_IP;

        pcpp::VlanLayer newVlanLayer2(arg.tunnel_id, false, 1, nextType2);
        if (arg.tunnel_id) {
            newPacket.addLayer(&newVlanLayer2);
        }

        pcpp::VlanLayer newVlanLayer3(arg.inner_tunnel_id, false, 1, PCPP_ETHERTYPE_IP);
        if (arg.inner_tunnel_id) {
            newPacket.addLayer(&newVlanLayer3);
        }

        pcpp::IPv4Layer newIPLayer(pcpp::IPv4Address(std::string("192.168.0.1")),
                                   pcpp::IPv4Address(std::string("192.168.1.1")));

        newIPLayer.getIPv4Header()->timeToLive = 128;
        newPacket.addLayer(&newIPLayer);

        pcpp::UdpLayer newUdpLayer(arg.udp_src_port, Config::flow_rtt_generated_packet_udp_dst_port);
        newPacket.addLayer(&newUdpLayer);

        FlowPayload payload{};
        payload.direction = arg.direction;
        payload.flow_id_length = arg.flow_id.size();
        payload.flow_id_offset = sizeof payload;

        std::vector<uint8_t> buffer(sizeof payload + arg.flow_id.size());
        std::memcpy(buffer.data(), &payload, sizeof payload);
        std::memcpy(buffer.data() + payload.flow_id_offset, arg.flow_id.c_str(), arg.flow_id.length());

        pcpp::PayloadLayer payloadLayer(buffer.data(), buffer.size(), false);
        newPacket.addLayer(&payloadLayer);
        newPacket.computeCalculateFields();

        auto packet = flow_pool_t::allocator_t::allocate(newPacket.getRawPacket(), arg.device);

        BOOST_LOG_TRIVIAL(debug) << "add flow " << arg.flow_id << " and switch_id " << arg.switch_id
                << " and direction " << arg.direction_str() << " and dst_mac " << arg.dst_mac;

        if (flow_meta) {
            BOOST_LOG_TRIVIAL(debug)
                << "update flow " << arg.flow_id
                << " and direction " << arg.direction_str() << " with hash " << flow_meta->get_hash()
                << " to new flow with hash " << arg.hash;
            arg.flow_pool.remove_packet(flow_id_key);
        }

        auto meta = std::shared_ptr<FlowMetadata>(
                new FlowMetadata(arg.flow_id, arg.direction, arg.switch_id, arg.hash));

        bool success = arg.flow_pool.add_packet(flow_id_key, packet, meta);

        if (!success) {
            flow_pool_t::allocator_t::dealocate(packet);
        }
    }

    void generate_and_add_packet_for_isl(const IslCreateArgument &arg) {

        isl_endpoint_t isl_id_key = get_isl_id(arg);
        auto db = arg.isl_pool.get_metadata_db();
        auto isl_meta = db->get(isl_id_key);
        if (isl_meta && isl_meta->get_hash() == arg.hash) {
            BOOST_LOG_TRIVIAL(debug)
                    << "skip add_isl command for " << arg.switch_id
                    << " and port " << arg.port << " with hash " << arg.hash << " already exists";
            return;
        }

        pcpp::Packet newPacket(64);

        pcpp::MacAddress dst(arg.dst_mac);
        pcpp::MacAddress src(arg.device->getMacAddress());
        pcpp::EthLayer newEthernetLayer(src, dst);
        newPacket.addLayer(&newEthernetLayer);

        pcpp::VlanLayer newVlanLayer(arg.transit_tunnel_id, false, 1, PCPP_ETHERTYPE_IP);
        if (arg.transit_tunnel_id) {
            newPacket.addLayer(&newVlanLayer);
        }

        pcpp::IPv4Layer newIPLayer(pcpp::IPv4Address(std::string("192.168.0.1")),
                                   pcpp::IPv4Address(std::string("192.168.1.1")));

        newIPLayer.getIPv4Header()->timeToLive = 128;
        newPacket.addLayer(&newIPLayer);

        pcpp::UdpLayer newUdpLayer(arg.udp_src_port, Config::isl_rtt_generated_packet_udp_dst_port);
        newPacket.addLayer(&newUdpLayer);

        IslPayload payload{};

        size_t length = arg.switch_id.copy(payload.switch_id, sizeof(payload.switch_id) - 1);
        payload.switch_id[length] = '\0';

        payload.port = arg.port;

        pcpp::PayloadLayer payloadLayer(reinterpret_cast<uint8_t *>(&payload), sizeof(payload), false);
        newPacket.addLayer(&payloadLayer);
        newPacket.computeCalculateFields();

        auto packet = isl_pool_t::allocator_t::allocate(newPacket.getRawPacket(), arg.device);

        if (isl_meta) {
            BOOST_LOG_TRIVIAL(debug)
                    << "update isl " << arg.switch_id
                    << " and port " << arg.port << " with hash " << isl_meta->get_hash()
                    << " to new isl with hash " << arg.hash;
            arg.isl_pool.remove_packet(isl_id_key);
        }

        auto meta = std::shared_ptr<IslMetadata>(
                new IslMetadata(arg.switch_id, arg.port, arg.dst_mac, arg.hash));

        bool success = arg.isl_pool.add_packet(isl_id_key, packet, meta);

        if (!success) {
            isl_pool_t::allocator_t::dealocate(packet);
        }
    }
}