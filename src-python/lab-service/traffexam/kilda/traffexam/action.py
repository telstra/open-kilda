# Copyright 2017 Telstra Open Source
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

import functools
import operator
import pyroute2

from scapy.all import ARP, IP, TCP, UDP, Ether, sendp
from scapy.contrib import lldp, lacp

from kilda.traffexam import context as context_module
from kilda.traffexam import exc
from kilda.traffexam import proc
from kilda.traffexam import system
from scapy.contrib.lacp import SlowProtocol


class Abstract(context_module.ContextConsumer):
    def __init__(self, context, namespace):
        super().__init__(context)
        self._network_namespace_context = namespace

    def __call__(self, *args, **kwargs):
        raise NotImplementedError


class LLDPPush(Abstract):
    lldp_bcast_mac = '01:80:c2:00:00:0e'

    def __call__(self, iface, push_entry):
        pkt = self._encode(push_entry)

        with self._network_namespace_context:
            sendp(pkt, iface=iface.name, verbose=False, promisc=False)

    def _encode(self, entry):
        entries = (
            lldp.LLDPDUChassisID(
                subtype=lldp.LLDPDUChassisID.SUBTYPE_MAC_ADDRESS,
                id=entry.chassis_id),
            lldp.LLDPDUPortID(
                subtype=lldp.LLDPDUPortID.SUBTYPE_LOCALLY_ASSIGNED,
                id=entry.port_number),
            lldp.LLDPDUTimeToLive(ttl=entry.time_to_live))
        if entry.port_description:
            entries = entries + (lldp.LLDPDUPortDescription(description=entry.port_description),)
        if entry.system_name:
            entries = entries + (lldp.LLDPDUSystemName(system_name=entry.system_name),)
        if entry.system_description:
            entries = entries + (lldp.LLDPDUSystemDescription(description=entry.system_description),)

        entries = entries + (lldp.LLDPDUEndOfLLDPDU(),)
        payload = functools.reduce(operator.truediv, entries)

        return Ether(src=entry.mac_address, dst=self.lldp_bcast_mac) / payload


class LACPPush(Abstract):
    def __call__(self, iface, push_entry):
        pkt = self._encode(push_entry)

        with self._network_namespace_context:
            sendp(pkt, iface=iface.name, verbose=False, promisc=False)

    def _encode(self, entry):
        # entry -> b'10011010'
        actor_state_binary = functools.reduce(lambda a, b: a + b, map(self._bool_to_byte, [entry.expired,
                                                                                           entry.defaulted,
                                                                                           entry.distributing,
                                                                                           entry.collecting,
                                                                                           entry.synchronization,
                                                                                           entry.aggregation,
                                                                                           entry.lacp_timeout,
                                                                                           entry.lacp_activity]))
        # Kilda ignores everything except lacp actor state. If this changes - expand this method with more fields
        return Ether() / SlowProtocol() / lacp.LACP(actor_state=actor_state_binary)

    @staticmethod
    def _bool_to_byte(self, boolean):
        return b'1' if boolean else b'0'

class ARPPush(Abstract):
    dst_ipv4 = "8.8.8.9"
    eth_broadcast = "FF:FF:FF:FF:FF:FF"

    def __call__(self, iface, push_entry):
        pkt = self._encode(push_entry)
        with self._network_namespace_context:
            sendp(pkt, iface=iface.name, verbose=False, promisc=False)

    def _encode(self, entry):
        arp = ARP(hwsrc=entry.src_mac, psrc=entry.src_ipv4, pdst=self.dst_ipv4)
        return Ether(src=entry.src_mac, dst=self.eth_broadcast) / arp


class UDPPush(Abstract):
    def __call__(self, iface, push_entry):
        data = "TEST"
        ether = Ether(src=push_entry.src_mac_address, dst=push_entry.dst_mac_address, type=push_entry.eth_type)
        ip = IP(src=push_entry.src_ip, dst=push_entry.dst_ip)
        udp = UDP(sport=push_entry.src_port, dport=push_entry.dst_port)
        pkt = ether / ip / udp / data

        with self._network_namespace_context:
            sendp(pkt, iface=iface.name, verbose=False, promisc=False)


class TCPPush(Abstract):
    def __call__(self, iface, push_entry):
        data = "TEST"
        ether = Ether(src=push_entry.src_mac_address, dst=push_entry.dst_mac_address, type=push_entry.eth_type)
        ip = IP(src=push_entry.src_ip, dst=push_entry.dst_ip)
        tcp = TCP(sport=push_entry.src_port, dport=push_entry.dst_port)
        pkt = ether / ip / tcp / data

        with self._network_namespace_context:
            sendp(pkt, iface=iface.name, verbose=False, promisc=False)


class AddressStats(Abstract):
    def __call__(self, iface):
        with pyroute2.NetNS(self.context.make_network_namespace_name()) as ipr:
            return ipr.get_links(iface.index)[0].get_attr('IFLA_STATS')



class Adapter(object):
    def __init__(self, context):
        network_namespace = self._make_network_namespace_context(context)

        self.lldp_push = LLDPPush(context, network_namespace)
        self.lacp_push = LACPPush(context, network_namespace)
        self.arp_push = ARPPush(context, network_namespace)
        self.udp_push = UDPPush(context, network_namespace)
        self.tcp_push = TCPPush(context, network_namespace)
        self.address_stats = AddressStats(context, network_namespace)

    @staticmethod
    def _make_network_namespace_context(context):
        try:
            context.shared_registry.fetch(system.FakeNetworkNamespace)
            return proc.DummyNetworkNamespaceContext()
        except exc.RegistryLookupError:
            return proc.NetworkNamespaceContext(
                context.make_network_namespace_name())
