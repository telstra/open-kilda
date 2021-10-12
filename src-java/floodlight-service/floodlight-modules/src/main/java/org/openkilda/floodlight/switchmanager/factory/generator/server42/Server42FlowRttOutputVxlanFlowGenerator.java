/* Copyright 2020 Telstra Open Source
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

import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.actionPushVlan;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.actionReplaceVlan;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.actionSetDstMac;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.actionSetOutputPort;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.actionSetSrcMac;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.actionSetUdpSrcAction;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.buildInstructionApplyActions;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.convertDpIdToMac;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.prepareFlowModBuilder;
import static org.openkilda.floodlight.switchmanager.SwitchManager.INPUT_TABLE_ID;
import static org.openkilda.floodlight.switchmanager.SwitchManager.NOVIFLOW_TIMESTAMP_SIZE_IN_BITS;
import static org.openkilda.floodlight.switchmanager.SwitchManager.SERVER_42_FLOW_RTT_OUTPUT_VXLAN_PRIORITY;
import static org.openkilda.floodlight.switchmanager.SwitchManager.SERVER_42_FLOW_RTT_REVERSE_UDP_PORT;
import static org.openkilda.floodlight.switchmanager.SwitchManager.VXLAN_UDP_DST;
import static org.openkilda.model.SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_COPY_FIELD;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE;

import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.switchmanager.factory.SwitchFlowTuple;
import org.openkilda.floodlight.switchmanager.factory.generator.SwitchFlowGenerator;
import org.openkilda.model.MacAddress;
import org.openkilda.model.SwitchFeature;

import com.google.common.collect.ImmutableList;
import lombok.Builder;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Server42FlowRttOutputVxlanFlowGenerator implements SwitchFlowGenerator {

    private FeatureDetectorService featureDetectorService;
    private int server42Port;
    private int server42Vlan;
    private MacAddress server42MacAddress;

    @Builder
    public Server42FlowRttOutputVxlanFlowGenerator(
            FeatureDetectorService featureDetectorService, int server42Port, int server42Vlan,
            MacAddress server42MacAddress) {
        this.featureDetectorService = featureDetectorService;
        this.server42Port = server42Port;
        this.server42Vlan = server42Vlan;
        this.server42MacAddress = server42MacAddress;
    }

    @Override
    public SwitchFlowTuple generateFlow(IOFSwitch sw) {
        Set<SwitchFeature> features = featureDetectorService.detectSwitch(sw);
        if (!features.contains(NOVIFLOW_PUSH_POP_VXLAN) && !features.contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN)) {
            return SwitchFlowTuple.getEmpty();
        }

        OFFactory ofFactory = sw.getOFFactory();

        List<OFAction> actions = new ArrayList<>();

        actions.add(buildPopVxlanAction(ofFactory, features));
        if (server42Vlan > 0) {
            actions.add(actionPushVlan(ofFactory, EthType.VLAN_FRAME.getValue()));
            actions.add(actionReplaceVlan(ofFactory, server42Vlan));
        }
        actions.add(actionSetSrcMac(ofFactory, convertDpIdToMac(sw.getId())));
        actions.add(actionSetDstMac(ofFactory, org.projectfloodlight.openflow.types.MacAddress.of(
                        server42MacAddress.getAddress())));
        actions.add(actionSetUdpSrcAction(ofFactory, TransportPort.of(SERVER_42_FLOW_RTT_REVERSE_UDP_PORT)));

        if (features.contains(NOVIFLOW_COPY_FIELD)) {
            // NOTE: We must call copy field action after all set field actions. All set actions called after copy field
            // actions will be ignored. It's Noviflow bug.
            actions.add(buildCopyTimestamp(ofFactory));
        }
        actions.add(actionSetOutputPort(ofFactory, OFPort.of(server42Port)));

        OFFlowMod flowMod = prepareFlowModBuilder(
                ofFactory, SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE, SERVER_42_FLOW_RTT_OUTPUT_VXLAN_PRIORITY,
                INPUT_TABLE_ID)
                .setMatch(buildMatch(sw.getId(), ofFactory))
                .setInstructions(ImmutableList.of(buildInstructionApplyActions(ofFactory, actions)))
                .build();
        return SwitchFlowTuple.builder()
                .sw(sw)
                .flow(flowMod)
                .build();
    }

    private static Match buildMatch(DatapathId dpid, OFFactory ofFactory) {
        return ofFactory.buildMatch()
                .setExact(MatchField.ETH_DST, convertDpIdToMac(dpid))
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                .setExact(MatchField.UDP_SRC, TransportPort.of(SERVER_42_FLOW_RTT_REVERSE_UDP_PORT))
                .setExact(MatchField.UDP_DST, TransportPort.of(VXLAN_UDP_DST))
                .build();
    }

    private static OFAction buildCopyTimestamp(OFFactory factory) {
        OFOxms oxms = factory.oxms();
        return factory.actions().buildNoviflowCopyField()
                .setNBits(NOVIFLOW_TIMESTAMP_SIZE_IN_BITS)
                .setSrcOffset(0)
                .setDstOffset(NOVIFLOW_TIMESTAMP_SIZE_IN_BITS)
                .setOxmSrcHeader(oxms.buildNoviflowTxtimestamp().getTypeLen())
                .setOxmDstHeader(oxms.buildNoviflowUpdPayload().getTypeLen())
                .build();
    }

    private static OFAction buildPopVxlanAction(OFFactory factory, Set<SwitchFeature> features) {
        if (features.contains(NOVIFLOW_COPY_FIELD)) {
            return factory.actions().noviflowPopVxlanTunnel();
        } else {
            return factory.actions().kildaPopVxlanField();
        }
    }
}
