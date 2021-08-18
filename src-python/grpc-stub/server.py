# Copyright 2020 Telstra Open Source
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#

from concurrent import futures
import logging

import grpc

import noviflow_pb2
import noviflow_pb2_grpc


class Greeter(noviflow_pb2_grpc.NoviFlowGrpcServicer):

    def __init__(self):
        self.storage = Storage()

    def ShowStatsPacketInOut(self, request, context):
        resp = noviflow_pb2.PacketInOutStats(
            packet_in_total_packets=1,
            packet_in_total_packets_dataplane=2,
            packet_in_no_match_packets=3,
            packet_in_apply_action_packets=4,
            packet_in_invalid_ttl_packets=5,
            packet_in_action_set_packets=6,
            packet_in_group_packets=7,
            packet_in_packet_out_packets=8,
            packet_out_total_packets_dataplane=9,
            packet_out_total_packets_host=10,
            packet_out_eth0_interface_up=noviflow_pb2.YES,
            reply_status=0)
        yield resp

    def SetLoginDetails(self, request, context):
        return noviflow_pb2.CliReply(reply_status=0)

    def SetConfigLogicalPort(self, request, context):
        port = LogicalPort(logical_port_number=request.logicalportno,
                           name="port_" + str(request.logicalportno),
                           port_numbers=request.portno,
                           type=request.logicalporttype)
        self.storage.logical_ports[request.logicalportno] = port
        return noviflow_pb2.CliReply(reply_status=0)

    def ShowConfigLogicalPort(self, request, context):
        port_number = request.logicalportno

        if port_number:
            if port_number in self.storage.logical_ports:
                port = self.storage.logical_ports[port_number]
                yield noviflow_pb2.LogicalPort(
                    logicalportno=port.logical_port_number,
                    portno=port.port_numbers,
                    name=port.name,
                    logicalporttype=port.type,
                    reply_status=0)
            else:
                yield noviflow_pb2.LogicalPort(reply_status=191)
        else:
            for port in self.storage.logical_ports.values():
                yield noviflow_pb2.LogicalPort(
                    logicalportno=port.logical_port_number,
                    name=port.name,
                    portno=port.port_numbers,
                    logicalporttype=port.type,
                    reply_status=0)

    def DelConfigLogicalPort(self, request, context):
        port_number = request.logicalportno

        if port_number in self.storage.logical_ports:
            del self.storage.logical_ports[port_number]
            return noviflow_pb2.CliReply(reply_status=0)

        return noviflow_pb2.LogicalPort(reply_status=191)

    def SetLogMessages(self, request, context):
        return noviflow_pb2.CliReply(reply_status=0)

    def SetLogOferrors(self, request, context):
        return noviflow_pb2.CliReply(reply_status=0)

    def ShowStatusSwitch(self, request, context):
        status = noviflow_pb2.StatusSwitch(
            serial_number="123456789",
            kernel="kernel_v_1",
            uptime="146",
            cpu_percentage=0.50,
            mem_usage=1024,
            ssd_usage=256000,
            eth_links=[noviflow_pb2.StatusSwitchEthLink(name="oth0", status="UP")],
            builds=[
                noviflow_pb2.StatusSwitchBuild(
                    name="build_1",
                    ope_version_hash="12345",
                    ppe_version_hash="678910",
                    ez_driver_version="v1.0")
            ],
            reply_status=0)

        yield status

    def ShowConfigRemoteLogServer(self, request, context):
        resp = noviflow_pb2.RemoteLogServer(
            ipaddr=getattr(self.storage.log_server, "ip_address", ""),
            port=getattr(self.storage.log_server, "port", 0),
            reply_status=0)
        yield resp

    def SetConfigRemoteLogServer(self, request, context):
        self.storage.log_server = LogServer(request.ipaddr, request.port)
        return noviflow_pb2.CliReply(reply_status=0)

    def DelConfigRemoteLogServer(self, request, context):
        self.storage.log_server = None
        return noviflow_pb2.CliReply(reply_status=0)

    def SetConfigPort(self, request, context):
        return noviflow_pb2.CliReply(reply_status=0)

    def SetConfigLicense(self, request, context):
        return noviflow_pb2.CliReply(reply_status=0)


class Storage:
    def __init__(self):
        self.log_server = None
        self.logical_ports = {}


class LogServer:
    def __init__(self, ip_address, port):
        self.ip_address = ip_address
        self.port = port


class LogicalPort:
    def __init__(self, logical_port_number, name, port_numbers, type):
        self.logical_port_number = logical_port_number
        self.name = name
        self.port_numbers = port_numbers
        self.type = type


def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    noviflow_pb2_grpc.add_NoviFlowGrpcServicer_to_server(Greeter(), server)
    server.add_insecure_port('[::]:50051')
    server.start()
    server.wait_for_termination()


if __name__ == '__main__':
    logging.basicConfig()
    serve()
