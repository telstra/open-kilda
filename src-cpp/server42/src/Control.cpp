#include "Control.h"

#include <boost/log/trivial.hpp>

#include "control.pb.h"
#include "flow-rtt-control.pb.h"
#include "isl-rtt-control.pb.h"

#include "PacketGenerator.h"

namespace org::openkilda {

    using CommandPacket = server42::control::messaging::CommandPacket;
    using CommandPacketResponse = server42::control::messaging::CommandPacketResponse;
    using Command = server42::control::messaging::CommandPacket_Type;
    using AddFlow = server42::control::messaging::flowrtt::AddFlow;
    using RemoveFlow = server42::control::messaging::flowrtt::RemoveFlow;
    using Error = server42::control::messaging::Error;
    using Flow = server42::control::messaging::flowrtt::Flow;
    using ListFlowsFilter = server42::control::messaging::flowrtt::ListFlowsFilter;
    using ClearFlowsFilter = server42::control::messaging::flowrtt::ClearFlowsFilter;
    using IslEndpoint = server42::control::messaging::islrtt::IslEndpoint;
    using AddIsl = server42::control::messaging::islrtt::AddIsl;
    using RemoveIsl = server42::control::messaging::islrtt::RemoveIsl;
    using ListIslsFilter = server42::control::messaging::islrtt::ListIslsFilter;
    using ClearIslsFilter = server42::control::messaging::islrtt::ClearIslsFilter;

    buffer_t trivial_response_from(const CommandPacket &command_packet) {
        CommandPacketResponse response;
        response.set_communication_id(command_packet.communication_id());
        std::vector<boost::uint8_t> result(response.ByteSizeLong());
        response.SerializeToArray(result.data(), result.size());
        return result;
    }

    buffer_t error_response_from(boost::int64_t communication_id, const std::string &what) {
        CommandPacketResponse response;
        response.set_communication_id(communication_id);
        Error error;
        error.set_what(what);
        response.add_error()->PackFrom(error);
        std::vector<boost::uint8_t> result(response.ByteSizeLong());
        response.SerializeToArray(result.data(), result.size());
        return result;
    }

    void add_flow(AddFlow &addFlow, flow_pool_t &flow_pool, pcpp::DpdkDevice *device) {

        FlowCreateArgument arg = {
                .flow_pool = flow_pool,
                .device = device,
                .switch_id = addFlow.flow().switch_id(),
                .dst_mac = addFlow.flow().dst_mac(),
                .tunnel_id = addFlow.flow().tunnel_id(),
                .inner_tunnel_id = addFlow.flow().inner_tunnel_id(),
                .transit_tunnel_id = addFlow.flow().transit_tunnel_id(),
                .udp_src_port = addFlow.flow().udp_src_port(),
                .flow_id = addFlow.flow().flow_id(),
                .direction = addFlow.flow().direction(),
                .hash = addFlow.flow().hash_code()
        };

        generate_and_add_packet_for_flow(arg);
    }


    void remove_flow(org::openkilda::server42::control::messaging::flowrtt::RemoveFlow &remove_flow,
                     org::openkilda::flow_pool_t &flow_pool) {
        BOOST_LOG_TRIVIAL(info) << "Remove flow: " << remove_flow.flow().flow_id() << ":" << remove_flow.flow().direction();
        flow_pool.remove_packet(make_flow_endpoint(remove_flow.flow().flow_id(), remove_flow.flow().direction()));
    }


    buffer_t clear_flows(const CommandPacket &command_packet, flow_pool_t &pool, std::mutex &pool_guard) {
        CommandPacketResponse response;
        response.set_communication_id(command_packet.communication_id());

        if (command_packet.command_size() == 1) {
            ClearFlowsFilter filter;
            const google::protobuf::Any &any = command_packet.command(0);
            any.UnpackTo(&filter);
            auto flow_list = pool.get_metadata_db()->get_endpoint_from_switch(filter.dst_mac());
            if (flow_list.size() > 0) {
                std::lock_guard<std::mutex> guard(pool_guard);
                for (auto f : flow_list) {
                    pool.remove_packet(f);
                }
            }
        } else {
            std::lock_guard<std::mutex> guard(pool_guard);
            pool.clear();
        }
        return trivial_response_from(command_packet);
    }

    buffer_t get_list_flows(const CommandPacket &command_packet, flow_pool_t &pool) {
        BOOST_LOG_TRIVIAL(info) << "Get list flows";
        CommandPacketResponse response;
        response.set_communication_id(command_packet.communication_id());

        if (command_packet.command_size() == 1) {
            ListFlowsFilter filter;
            const google::protobuf::Any &any = command_packet.command(0);
            any.UnpackTo(&filter);
            auto flow_list = pool.get_metadata_db()->get_endpoint_from_switch(filter.dst_mac());
            BOOST_LOG_TRIVIAL(debug) << "Flow list size: " << flow_list.size() << " flows for switch " << filter.dst_mac();
            for (auto f : flow_list) {
                Flow flow;
                flow.set_flow_id(std::get<int(flow_endpoint_members::flow_id)>(f));
                flow.set_direction(std::get<int(flow_endpoint_members::direction)>(f));
                BOOST_LOG_TRIVIAL(debug) << "Flow: " << flow.flow_id() << ":" << flow.direction();
                response.add_response()->PackFrom(flow);
            }
        } else {
            BOOST_LOG_TRIVIAL(debug) << "Flow list size: " << pool.get_packetbyendpoint_table().size();
            for (const flow_endpoint_t &flow_endpoint : pool.get_packetbyendpoint_table()) {
                Flow flow;
                flow.set_flow_id(std::get<int(flow_endpoint_members::flow_id)>(flow_endpoint));
                flow.set_direction(std::get<int(flow_endpoint_members::direction)>(flow_endpoint));
                response.add_response()->PackFrom(flow);
            }
        }

        std::vector<boost::uint8_t> result(response.ByteSizeLong());
        response.SerializeToArray(result.data(), result.size());
        return result;
    }

    void add_isl(AddIsl &addIsl, isl_pool_t &isl_pool, pcpp::DpdkDevice *device) {
        if (server42::control::messaging::islrtt::AddIsl_EncapsulationType_VLAN !=
            addIsl.transit_encapsulation_type()) {
            BOOST_LOG_TRIVIAL(error) << "Non-VLAN as transit is not supported, skip add_isl'";
            return;
        }

        IslCreateArgument arg = {
                .isl_pool = isl_pool,
                .device = device,
                .dst_mac = addIsl.switch_mac(),
                .transit_tunnel_id = addIsl.transit_tunnel_id(),
                .udp_src_port = addIsl.udp_src_port(),
                .switch_id = addIsl.isl().switch_id(),
                .port = addIsl.isl().port(),
                .hash = addIsl.hash_code()
        };

        generate_and_add_packet_for_isl(arg);
    }

    void remove_isl(org::openkilda::server42::control::messaging::islrtt::RemoveIsl &remove_isl,
                     org::openkilda::isl_pool_t &isl_pool) {
        BOOST_LOG_TRIVIAL(info) << "Remove ISL: " << remove_isl.isl().switch_id() << ":" << remove_isl.isl().port();
        isl_pool.remove_packet(make_isl_endpoint(remove_isl.isl().switch_id(), remove_isl.isl().port()));
    }

    buffer_t clear_isls(const CommandPacket &command_packet, isl_pool_t &pool, std::mutex &pool_guard) {
        CommandPacketResponse response;
        response.set_communication_id(command_packet.communication_id());

        if (command_packet.command_size() == 1) {
            ClearIslsFilter filter;
            const google::protobuf::Any &any = command_packet.command(0);
            any.UnpackTo(&filter);
            auto isl_list = pool.get_metadata_db()->get_endpoint_from_switch(filter.switch_id());
            if (isl_list.size() > 0) {
                std::lock_guard<std::mutex> guard(pool_guard);
                for (auto f : isl_list) {
                    pool.remove_packet(f);
                }
            }
        } else {
            std::lock_guard<std::mutex> guard(pool_guard);
            pool.clear();
        }
        return trivial_response_from(command_packet);
    }

    buffer_t get_list_isls(const CommandPacket &command_packet, isl_pool_t &pool) {
        CommandPacketResponse response;
        response.set_communication_id(command_packet.communication_id());

        if (command_packet.command_size() == 1) {
            ListIslsFilter filter;
            const google::protobuf::Any &any = command_packet.command(0);
            any.UnpackTo(&filter);
            auto isl_list = pool.get_metadata_db()->get_endpoint_from_switch(filter.switch_id());
            for (auto f : isl_list) {
                IslEndpoint isl;
                isl.set_switch_id(std::get<int(isl_endpoint_members::switch_id)>(f));
                isl.set_port(std::get<int(isl_endpoint_members::port)>(f));
                response.add_response()->PackFrom(isl);
            }
        } else {
            for (const isl_endpoint_t &isl_endpoint : pool.get_packetbyendpoint_table()) {
                IslEndpoint isl;
                isl.set_switch_id(std::get<int(isl_endpoint_members::switch_id)>(isl_endpoint));
                isl.set_port(std::get<int(isl_endpoint_members::port)>(isl_endpoint));
                response.add_response()->PackFrom(isl);
            }
        }

        std::vector<boost::uint8_t> result(response.ByteSizeLong());
        response.SerializeToArray(result.data(), result.size());
        return result;
    }

    buffer_t handle_command_request(const void *data, int size, SharedContext &ctx) {
        CommandPacket command_packet;
        if (!command_packet.ParseFromArray(data, size)) {
            BOOST_LOG_TRIVIAL(error) << "Failed to parse CommandPacket";
            return error_response_from(0, "Failed to parse CommandPacket");
        } else {
            switch (command_packet.type()) {
                case Command::CommandPacket_Type_ADD_FLOW:
                case Command::CommandPacket_Type_REMOVE_FLOW: {
                    std::lock_guard<std::mutex> guard(ctx.pool_guard);
                    for (int i = 0; i < command_packet.command_size(); ++i) {
                        const google::protobuf::Any &any = command_packet.command(i);
                        if (command_packet.type() == Command::CommandPacket_Type_ADD_FLOW) {
                            AddFlow addFlow;
                            any.UnpackTo(&addFlow);
                            add_flow(addFlow, ctx.flow_pool, ctx.primary_device);
                        } else {
                            RemoveFlow removeFlow;
                            any.UnpackTo(&removeFlow);
                            remove_flow(removeFlow, ctx.flow_pool);
                        }
                    }
                    return trivial_response_from(command_packet);
                }
                case Command::CommandPacket_Type_CLEAR_FLOWS:
                    return clear_flows(command_packet, ctx.flow_pool, ctx.pool_guard);
                case Command::CommandPacket_Type_LIST_FLOWS:
                    return get_list_flows(command_packet, ctx.flow_pool);
                case Command::CommandPacket_Type_PUSH_SETTINGS:
                    // TODO: implement PUSH_SETTINGS
                    return trivial_response_from(command_packet);
                case Command::CommandPacket_Type_ADD_ISL:
                case Command::CommandPacket_Type_REMOVE_ISL: {
                    std::lock_guard<std::mutex> guard(ctx.pool_guard);
                    for (int i = 0; i < command_packet.command_size(); ++i) {
                        const google::protobuf::Any &any = command_packet.command(i);
                        if (command_packet.type() == Command::CommandPacket_Type_ADD_ISL) {
                            AddIsl addIsl;
                            any.UnpackTo(&addIsl);
                            add_isl(addIsl, ctx.isl_pool, ctx.primary_device);
                        } else {
                            RemoveIsl removeIsl;
                            any.UnpackTo(&removeIsl);
                            remove_isl(removeIsl, ctx.isl_pool);
                        }
                    }
                    return trivial_response_from(command_packet);
                }
                case Command::CommandPacket_Type_CLEAR_ISLS:
                    return clear_isls(command_packet, ctx.isl_pool, ctx.pool_guard);
                case Command::CommandPacket_Type_LIST_ISLS:
                    return get_list_isls(command_packet, ctx.isl_pool);
                default:
                    BOOST_LOG_TRIVIAL(error) << "Unknown command type: " << command_packet.type();
                    return error_response_from(command_packet.communication_id(), "Unknown command type");
            }
        }
    }


}