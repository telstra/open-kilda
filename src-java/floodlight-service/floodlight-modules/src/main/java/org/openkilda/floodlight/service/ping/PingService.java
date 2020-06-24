/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.floodlight.service.ping;

import org.openkilda.floodlight.KildaCore;
import org.openkilda.floodlight.KildaCoreConfig;
import org.openkilda.floodlight.error.InvalidSignatureConfigurationException;
import org.openkilda.floodlight.error.PingImpossibleException;
import org.openkilda.floodlight.model.PingWiredView;
import org.openkilda.floodlight.pathverification.PathVerificationService;
import org.openkilda.floodlight.service.IService;
import org.openkilda.floodlight.service.of.InputService;
import org.openkilda.floodlight.shared.packet.Vxlan;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.floodlight.utils.DataSignature;
import org.openkilda.floodlight.utils.EthernetPacketToolbox;
import org.openkilda.messaging.model.Ping;
import org.openkilda.messaging.model.Ping.Errors;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.cookie.Cookie;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PingService implements IService {
    public static final U64 OF_CATCH_RULE_COOKIE = U64.of(Cookie.VERIFICATION_UNICAST_RULE_COOKIE);
    public static final U64 OF_CATCH_RULE_COOKIE_VXLAN = U64.of(Cookie.VERIFICATION_UNICAST_VXLAN_RULE_COOKIE);
    private static final String NET_L3_ADDRESS = "127.0.0.2";
    private static final int NET_L3_PORT = PathVerificationService.DISCOVERY_PACKET_UDP_PORT + 1;
    private static final byte NET_L3_TTL = 96;

    private DataSignature signature = null;
    private MacAddress magicSourceMacAddress;

    /**
     * Initialize internal data structures. Called by module that own this service. Called after all dependencies have
     * been loaded.
     */
    @Override
    public void setup(FloodlightModuleContext moduleContext) throws FloodlightModuleException {
        // FIXME(surabujin): avoid usage foreign module configuration
        Map<String, String> config = moduleContext.getConfigParams(PathVerificationService.class);
        try {
            signature = new DataSignature(config.get("hmac256-secret"));
        } catch (InvalidSignatureConfigurationException e) {
            throw new FloodlightModuleException(String.format("Unable to initialize %s", getClass().getName()), e);
        }

        InputService inputService = moduleContext.getServiceImpl(InputService.class);
        inputService.addTranslator(OFType.PACKET_IN, new PingInputTranslator());

        KildaCoreConfig coreConfig = moduleContext.getServiceImpl(KildaCore.class).getConfig();
        magicSourceMacAddress = MacAddress.of(coreConfig.getFlowPingMagicSrcMacAddress());
    }

    /**
     * Wrap ping data into L2, l3 and L4 network packages.
     */
    public IPacket wrapData(Ping ping, byte[] payload) throws PingImpossibleException {
        Data l7 = new Data(payload);

        UDP l4 = new UDP();
        l4.setPayload(l7);
        l4.setSourcePort(TransportPort.of(NET_L3_PORT));
        l4.setDestinationPort(TransportPort.of(NET_L3_PORT));

        IPv4 l3 = new IPv4();
        l3.setPayload(l4);
        l3.setSourceAddress(NET_L3_ADDRESS);
        l3.setDestinationAddress(NET_L3_ADDRESS);
        l3.setTtl(NET_L3_TTL);

        Ethernet l2 = new Ethernet();
        l2.setPayload(l3);
        l2.setEtherType(EthType.IPv4);
        l2.setSourceMACAddress(magicSourceMacAddress);
        DatapathId egressSwitch = DatapathId.of(ping.getDest().getDatapath().toLong());
        l2.setDestinationMACAddress(MacAddress.of(egressSwitch));

        if (FlowEncapsulationType.TRANSIT_VLAN.equals(ping.getTransitEncapsulation().getType())) {
            l2.setVlanID(ping.getTransitEncapsulation().getId().shortValue());
            return l2;

        } else if (FlowEncapsulationType.VXLAN.equals(ping.getTransitEncapsulation().getType())) {
            Vxlan vxlan = new Vxlan();
            vxlan.setPayload(l2);
            vxlan.setVni(ping.getTransitEncapsulation().getId());

            UDP udp = new UDP();
            udp.setPayload(vxlan);
            udp.setSourcePort(TransportPort.of(SwitchManager.STUB_VXLAN_UDP_SRC));
            udp.setDestinationPort(TransportPort.of(SwitchManager.VXLAN_UDP_DST));

            IPv4 ipv4 = new IPv4();
            ipv4.setPayload(udp);
            ipv4.setProtocol(IpProtocol.UDP);
            ipv4.setSourceAddress(SwitchManager.STUB_VXLAN_IPV4_SRC);
            ipv4.setDestinationAddress(SwitchManager.STUB_VXLAN_IPV4_DST);
            ipv4.setTtl(NET_L3_TTL);

            Ethernet ethernet = new Ethernet();
            ethernet.setPayload(ipv4);
            ethernet.setEtherType(EthType.IPv4);
            ethernet.setSourceMACAddress(magicSourceMacAddress);
            ethernet.setDestinationMACAddress(MacAddress.of(egressSwitch));

            return ethernet;
        }

        throw new PingImpossibleException(ping, Errors.INCORRECT_REQUEST);
    }

    /**
     * Unpack network package.
     * Verify all particular qualities used during discovery package creation time. Return packet payload.
     */
    public PingWiredView unwrapData(DatapathId dpId, Ethernet packet) {
        MacAddress targetL2Address = MacAddress.of(dpId);
        if (!packet.getDestinationMACAddress().equals(targetL2Address)) {
            return null;
        }

        List<Integer> vlanStack = new ArrayList<>();
        IPacket payload = EthernetPacketToolbox.extractPayload(packet, vlanStack);

        if (! (payload instanceof IPv4)) {
            return null;
        }
        IPv4 ip = (IPv4) payload;

        if (!NET_L3_ADDRESS.equals(ip.getSourceAddress().toString())) {
            return null;
        }
        if (!NET_L3_ADDRESS.equals(ip.getDestinationAddress().toString())) {
            return null;
        }

        if (!(ip.getPayload() instanceof UDP)) {
            return null;
        }
        UDP udp = (UDP) ip.getPayload();

        if (udp.getSourcePort().getPort() != NET_L3_PORT) {
            return null;
        }
        if (udp.getDestinationPort().getPort() != NET_L3_PORT) {
            return null;
        }

        return new PingWiredView(vlanStack, udp.getPayload().serialize());
    }

    public DataSignature getSignature() {
        return signature;
    }
}
