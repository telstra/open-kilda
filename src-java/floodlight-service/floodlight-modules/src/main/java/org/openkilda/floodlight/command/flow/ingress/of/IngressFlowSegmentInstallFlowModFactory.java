/* Copyright 2019 Telstra Open Source
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

package org.openkilda.floodlight.command.flow.ingress.of;

import static org.openkilda.floodlight.switchmanager.SwitchManager.SERVER_42_FLOW_RTT_FORWARD_UDP_PORT;
import static org.openkilda.floodlight.switchmanager.SwitchManager.STUB_VXLAN_UDP_SRC;
import static org.openkilda.floodlight.switchmanager.factory.generator.server42.Server42FlowRttInputFlowGenerator.buildServer42CopyFirstTimestamp;
import static org.openkilda.model.SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_COPY_FIELD;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;

import org.openkilda.floodlight.command.flow.ingress.IngressFlowSegmentCommand;
import org.openkilda.floodlight.error.NotImplementedEncapsulationException;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.floodlight.utils.OfFlowModBuilderFactory;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.SwitchFeature;

import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
abstract class IngressFlowSegmentInstallFlowModFactory extends IngressInstallFlowModFactory {
    private final IngressFlowSegmentCommand command;

    public IngressFlowSegmentInstallFlowModFactory(
            OfFlowModBuilderFactory flowModBuilderFactory, IngressFlowSegmentCommand command, IOFSwitch sw,
            Set<SwitchFeature> features) {
        super(flowModBuilderFactory, command, sw, features);
        this.command = command;
    }

    @Override
    protected List<OFAction> makeTransformActions(List<Integer> vlanStack, boolean groupIsPresent) {
        List<OFAction> actions = new ArrayList<>();
        FlowTransitEncapsulation encapsulation = command.getEncapsulation();
        switch (encapsulation.getType()) {
            case TRANSIT_VLAN:
                actions.addAll(makeVlanEncapsulationTransformActions(vlanStack, groupIsPresent));
                break;
            case VXLAN:
                actions.addAll(makeVxLanEncapsulationTransformActions(vlanStack, groupIsPresent));
                break;
            default:
                throw new NotImplementedEncapsulationException(
                        getClass(), encapsulation.getType(), command.getSwitchId(), command.getMetadata().getFlowId());
        }
        return actions;
    }

    @Override
    protected List<OFAction> makeServer42IngressFlowTransformActions(List<Integer> vlanStack) {
        List<OFAction> actions = new ArrayList<>();
        FlowTransitEncapsulation encapsulation = command.getEncapsulation();
        switch (encapsulation.getType()) {
            case TRANSIT_VLAN:
                MacAddress ethSrc = MacAddress.of(sw.getId());
                MacAddress ethDst = MacAddress.of(command.getEgressSwitchId().toLong());

                actions.add(of.actions().setField(of.oxms().ethSrc(ethSrc)));
                actions.add(of.actions().setField(of.oxms().ethDst(ethDst)));

                if (!getCommand().getMetadata().isMultiTable()) {
                    actions.add(of.actions()
                            .setField(of.oxms().udpSrc(TransportPort.of(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT))));
                    actions.add(of.actions()
                            .setField(of.oxms().udpDst(TransportPort.of(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT))));
                    if (switchFeatures.contains(NOVIFLOW_COPY_FIELD)) {
                        actions.add(buildServer42CopyFirstTimestamp(of));
                    }
                }
                actions.addAll(makeVlanEncapsulationTransformActions(vlanStack, false));
                break;
            case VXLAN:
                if (!getCommand().getMetadata().isMultiTable() && switchFeatures.contains(NOVIFLOW_COPY_FIELD)) {
                    actions.add(buildServer42CopyFirstTimestamp(of));
                }
                actions.add(pushVxlanAction(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT));
                break;
            default:
                throw new NotImplementedEncapsulationException(
                        getClass(), encapsulation.getType(), command.getSwitchId(), command.getMetadata().getFlowId());
        }
        return actions;
    }

    private List<OFAction> makeVlanEncapsulationTransformActions(List<Integer> vlanStack, boolean groupIsPresent) {
        List<Integer> desiredVlanStack = groupIsPresent ? Collections.emptyList()
                : FlowEndpoint.makeVlanStack(command.getEncapsulation().getId());
        return OfAdapter.INSTANCE.makeVlanReplaceActions(of, vlanStack, desiredVlanStack);
    }

    private List<OFAction> makeVxLanEncapsulationTransformActions(List<Integer> vlanStack, boolean groupIsPresent) {
        // Remove any remaining vlan's, because egress switch will reject vlan manipulation on flow that have no
        // vlan header matches
        List<OFAction> actions = new ArrayList<>(
                OfAdapter.INSTANCE.makeVlanReplaceActions(of, vlanStack, Collections.emptyList()));

        if (!groupIsPresent) {
            actions.add(pushVxlanAction(STUB_VXLAN_UDP_SRC));
        }

        return actions;
    }

    private OFAction pushVxlanAction(int udpSrcPort) {
        if (switchFeatures.contains(NOVIFLOW_PUSH_POP_VXLAN)) {
            return OfAdapter.INSTANCE.makeNoviflowPushVxlanAction(of, command.getEncapsulation().getId(),
                    MacAddress.of(sw.getId()), MacAddress.of(command.getEgressSwitchId().toLong()), udpSrcPort);
        } else if (switchFeatures.contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN)) {
            return OfAdapter.INSTANCE.makeOvsPushVxlanAction(of, command.getEncapsulation().getId(),
                    MacAddress.of(sw.getId()), MacAddress.of(command.getEgressSwitchId().toLong()), udpSrcPort);
        } else {
            throw new UnsupportedOperationException(String.format("To push VXLAN switch %s must support one of the "
                    + "following features [%s, %s]",
                    sw.getId(), NOVIFLOW_PUSH_POP_VXLAN, KILDA_OVS_PUSH_POP_MATCH_VXLAN));
        }
    }

    @Override
    protected OFAction makeOutputAction() {
        return super.makeOutputAction(OFPort.of(command.getIslPort()));
    }

    @Override
    protected List<OFInstruction> makeMetadataInstructions() {
        return Collections.emptyList();
    }
}
