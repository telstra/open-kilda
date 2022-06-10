/* Copyright 2017 Telstra Open Source
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

package org.openkilda.floodlight.test.standard;

import org.openkilda.floodlight.OFFactoryMock;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.model.cookie.Cookie;

import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;

import java.util.Arrays;

public interface OutputCommands {
    OFFactory ofFactory = new OFFactoryMock();

    /**
     * Create pass through egress isl vxlan default rule for table 0.
     *
     * @param dpid datapath of the switch.
     * @param port isl port.
     * @return created OFFlowAdd.
     */
    default OFFlowAdd installEgressIslVxlanRule(DatapathId dpid, int port) {
        Match match = ofFactory.buildMatch()
                .setExact(MatchField.ETH_DST, MacAddress.of(Arrays.copyOfRange(dpid.getBytes(), 2, 8)))
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                .setExact(MatchField.IN_PORT, OFPort.of(port))
                .setExact(MatchField.UDP_SRC, TransportPort.of(SwitchManager.STUB_VXLAN_UDP_SRC))
                .setExact(MatchField.UDP_DST, TransportPort.of(SwitchManager.VXLAN_UDP_DST))
                .build();
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(Cookie.encodeIslVxlanEgress(port)))
                .setPriority(SwitchManager.ISL_EGRESS_VXLAN_RULE_PRIORITY_MULTITABLE)
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setMatch(match)
                .setInstructions(ImmutableList.of(
                        ofFactory.instructions().gotoTable(TableId.of(SwitchManager.EGRESS_TABLE_ID))))
                .build();
    }

    /**
     * Create pass through transit isl vxlan default rule for table 0.
     *
     * @param dpid datapath of the switch.
     * @param port isl port.
     * @return created OFFlowAdd.
     */
    default OFFlowAdd installTransitIslVxlanRule(DatapathId dpid, int port) {
        Match match = ofFactory.buildMatch()
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                .setExact(MatchField.IN_PORT, OFPort.of(port))
                .setExact(MatchField.UDP_SRC, TransportPort.of(SwitchManager.STUB_VXLAN_UDP_SRC))
                .setExact(MatchField.UDP_DST, TransportPort.of(SwitchManager.VXLAN_UDP_DST))
                .build();
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(Cookie.encodeIslVxlanTransit(port)))
                .setPriority(SwitchManager.ISL_TRANSIT_VXLAN_RULE_PRIORITY_MULTITABLE)
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setMatch(match)
                .setInstructions(ImmutableList.of(
                        ofFactory.instructions().gotoTable(TableId.of(SwitchManager.TRANSIT_TABLE_ID))))
                .build();
    }

    /**
     * Create pass through egress isl vlan default rule for table 0.
     *
     * @param dpid datapath of the switch.
     * @param port isl port.
     * @return created OFFlowAdd.
     */
    default OFFlowAdd installEgressIslVlanRule(DatapathId dpid, int port) {
        Match match = ofFactory.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(port))
                .build();
        return ofFactory.buildFlowAdd()
                .setCookie(U64.of(Cookie.encodeIslVlanEgress(port)))
                .setPriority(SwitchManager.ISL_EGRESS_VLAN_RULE_PRIORITY_MULTITABLE)
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setMatch(match)
                .setInstructions(ImmutableList.of(
                        ofFactory.instructions().gotoTable(TableId.of(SwitchManager.EGRESS_TABLE_ID))))
                .build();
    }
}
