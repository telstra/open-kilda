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

import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.actionSetOutputPort;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.actionSetUdpSrcAction;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.buildInstructionApplyActions;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.prepareFlowModBuilder;
import static org.openkilda.floodlight.switchmanager.SwitchManager.INPUT_TABLE_ID;
import static org.openkilda.floodlight.switchmanager.SwitchManager.SERVER_42_ISL_RTT_FORWARD_UDP_PORT;
import static org.openkilda.floodlight.switchmanager.SwitchManager.SERVER_42_ISL_RTT_REVERSE_UDP_PORT;
import static org.openkilda.floodlight.switchmanager.SwitchManager.SERVER_42_ISL_RTT_TURNING_PRIORITY;
import static org.openkilda.model.cookie.Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE;

import org.openkilda.floodlight.KildaCore;
import org.openkilda.floodlight.switchmanager.factory.SwitchFlowTuple;
import org.openkilda.floodlight.switchmanager.factory.generator.SwitchFlowGenerator;

import com.google.common.collect.ImmutableList;
import lombok.Builder;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;

import java.util.List;

@Builder
public class Server42IslRttTurningFlowGenerator implements SwitchFlowGenerator {
    private final KildaCore kildaCore;

    @Builder
    public Server42IslRttTurningFlowGenerator(KildaCore kildaCore) {
        this.kildaCore = kildaCore;
    }

    @Override
    public SwitchFlowTuple generateFlow(IOFSwitch sw) {
        OFFactory ofFactory = sw.getOFFactory();
        Match match = buildMatch(ofFactory, MacAddress.of(kildaCore.getConfig().getServer42IslRttMagicMacAddress()));
        List<OFAction> actions = ImmutableList.of(
                actionSetUdpSrcAction(ofFactory, TransportPort.of(SERVER_42_ISL_RTT_REVERSE_UDP_PORT)),
                actionSetOutputPort(ofFactory, OFPort.IN_PORT));

        OFFlowMod flowMod = prepareFlowModBuilder(
                ofFactory, SERVER_42_ISL_RTT_TURNING_COOKIE, SERVER_42_ISL_RTT_TURNING_PRIORITY, INPUT_TABLE_ID)
                .setMatch(match)
                .setInstructions(ImmutableList.of(buildInstructionApplyActions(ofFactory, actions)))
                .build();
        return SwitchFlowTuple.builder()
                .sw(sw)
                .flow(flowMod)
                .build();
    }

    private static Match buildMatch(OFFactory ofFactory, MacAddress ethDstMacAddress) {
        return ofFactory.buildMatch()
                .setExact(MatchField.ETH_DST, ethDstMacAddress)
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                .setExact(MatchField.UDP_SRC, TransportPort.of(SERVER_42_ISL_RTT_FORWARD_UDP_PORT))
                .build();
    }
}
