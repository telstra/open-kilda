/* Copyright 2021 Telstra Open Source
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

package org.openkilda.floodlight.switchmanager.factory.generator.server42;

import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.actionSetDstMac;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.actionSetOutputPort;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.actionSetUdpSrcAction;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.buildInstructionApplyActions;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.convertDpIdToMac;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.prepareFlowModBuilder;
import static org.openkilda.floodlight.switchmanager.SwitchManager.INPUT_TABLE_ID;
import static org.openkilda.floodlight.switchmanager.SwitchManager.SERVER_42_ISL_RTT_FORWARD_UDP_PORT;
import static org.openkilda.floodlight.switchmanager.SwitchManager.SERVER_42_ISL_RTT_INPUT_PRIORITY;
import static org.openkilda.model.cookie.Cookie.encodeServer42IslRttInput;

import org.openkilda.floodlight.KildaCore;
import org.openkilda.floodlight.switchmanager.factory.SwitchFlowTuple;
import org.openkilda.floodlight.switchmanager.factory.generator.SwitchFlowGenerator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import lombok.Builder;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;

import java.util.List;

public class Server42IslRttInputFlowGenerator implements SwitchFlowGenerator {
    private final KildaCore kildaCore;
    private final int server42Port;
    private final int islPort;

    @Builder
    public Server42IslRttInputFlowGenerator(KildaCore kildaCore, int server42Port, int islPort) {
        this.kildaCore = kildaCore;
        this.server42Port = server42Port;
        this.islPort = islPort;
    }

    /**
     * Generated OFFlowMod for Server 42 ISL RTT input rule.
     */
    public static OFFlowMod generateFlowMod(IOFSwitch sw, int udpOffset, int islPort, int server42Port,
                                            String magicIslRttMacAddress) {
        OFFactory ofFactory = sw.getOFFactory();

        Match match = buildMatch(ofFactory, server42Port, udpOffset + islPort, convertDpIdToMac(sw.getId()));

        List<OFAction> actions = Lists.newArrayList(
                actionSetUdpSrcAction(ofFactory, TransportPort.of(SERVER_42_ISL_RTT_FORWARD_UDP_PORT)),
                actionSetDstMac(ofFactory, org.projectfloodlight.openflow.types.MacAddress.of(magicIslRttMacAddress)),
                actionSetOutputPort(ofFactory, OFPort.of(islPort)));

        return prepareFlowModBuilder(
                ofFactory, encodeServer42IslRttInput(islPort), SERVER_42_ISL_RTT_INPUT_PRIORITY, INPUT_TABLE_ID)
                .setMatch(match)
                .setInstructions(ImmutableList.of(buildInstructionApplyActions(ofFactory, actions)))
                .build();
    }

    @Override
    public SwitchFlowTuple generateFlow(IOFSwitch sw) {
        OFFlowMod flowMod = generateFlowMod(
                sw, kildaCore.getConfig().getServer42IslRttUdpPortOffset(), islPort,
                server42Port, kildaCore.getConfig().getServer42IslRttMagicMacAddress());

        return SwitchFlowTuple.builder()
                .sw(sw)
                .flow(flowMod)
                .build();
    }

    private static Match buildMatch(OFFactory ofFactory, int server42Port, int udpSrcPort,
                                    org.projectfloodlight.openflow.types.MacAddress ethDstMacAddress) {
        return ofFactory.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(server42Port))
                .setExact(MatchField.ETH_DST, ethDstMacAddress)
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                .setExact(MatchField.UDP_SRC, TransportPort.of(udpSrcPort))
                .build();
    }
}
