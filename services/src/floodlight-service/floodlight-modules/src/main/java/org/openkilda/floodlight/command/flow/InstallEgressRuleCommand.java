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

package org.openkilda.floodlight.command.flow;

import static org.openkilda.floodlight.switchmanager.SwitchManager.EGRESS_TABLE_ID;
import static org.openkilda.floodlight.switchmanager.SwitchManager.INPUT_TABLE_ID;
import static org.openkilda.floodlight.switchmanager.SwitchManager.convertDpIdToMac;
import static org.openkilda.messaging.Utils.ETH_TYPE;

import org.openkilda.floodlight.command.MessageWriter;
import org.openkilda.floodlight.command.SessionProxy;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.TableId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class InstallEgressRuleCommand extends InstallTransitRuleCommand {

    private final OutputVlanType outputVlanType;
    private final Integer outputVlanId;

    public InstallEgressRuleCommand(@JsonProperty("command_id") UUID commandId,
                                    @JsonProperty("flowid") String flowId,
                                    @JsonProperty("message_context") MessageContext messageContext,
                                    @JsonProperty("cookie") Cookie cookie,
                                    @JsonProperty("switch_id") SwitchId switchId,
                                    @JsonProperty("input_port") Integer inputPort,
                                    @JsonProperty("output_port") Integer outputPort,
                                    @JsonProperty("output_vlan_type") OutputVlanType outputVlanType,
                                    @JsonProperty("output_vlan_id") Integer outputVlanId,
                                    @JsonProperty("transit_encapsulation_id") Integer transitEncapsulationId,
                                    @JsonProperty("transit_encapsulation_type")
                                            FlowEncapsulationType transitEncapsulationType,
                                    @JsonProperty("multi_table") boolean multiTable) {
        super(commandId, flowId, messageContext, cookie, switchId, inputPort, outputPort,
                transitEncapsulationId, transitEncapsulationType, multiTable);
        this.outputVlanType = outputVlanType;
        this.outputVlanId = outputVlanId;
    }

    @Override
    public List<SessionProxy> getCommands(IOFSwitch sw, FloodlightModuleContext moduleContext) {
        List<OFAction> actionList = new ArrayList<>();
        OFFactory ofFactory = sw.getOFFactory();

        // output action based on encap scheme
        actionList.addAll(getOutputAction(ofFactory));

        // transmit packet from outgoing port
        actionList.add(setOutputPort(ofFactory));

        // build instruction with action list
        OFInstructionApplyActions actions = applyActions(ofFactory, actionList);

        // build FLOW_MOD command, no meter
        MacAddress dstMac = convertDpIdToMac(sw.getId());

        int targetTableId = multiTable ? EGRESS_TABLE_ID : INPUT_TABLE_ID;

        OFFlowMod flowMod = prepareFlowModBuilder(ofFactory)
                .setMatch(matchFlow(inputPort, transitEncapsulationId, transitEncapsulationType, dstMac, ofFactory))
                .setInstructions(ImmutableList.of(actions))
                .setTableId(TableId.of(targetTableId))
                .build();

        return Collections.singletonList(new MessageWriter(flowMod));
    }

    private List<OFAction> getOutputAction(OFFactory ofFactory) {
        switch (transitEncapsulationType) {
            case TRANSIT_VLAN:
                return Collections.singletonList(getOutputActionVlan(ofFactory));
            case VXLAN:
                return getOutputActionsForVxlan(ofFactory);
            default:
                throw new UnsupportedOperationException(
                        String.format("Unknown encapsulation type: %s", transitEncapsulationType));

        }

    }

    private List<OFAction> getOutputActionsForVxlan(OFFactory ofFactory) {
        List<OFAction> actionList = new ArrayList<>(2);
        actionList.add(ofFactory.actions().noviflowPopVxlanTunnel());
        if (outputVlanType == OutputVlanType.PUSH) {
            actionList.add(actionPushVlan(ofFactory, ETH_TYPE));
            actionList.add(actionReplaceVlan(ofFactory, outputVlanId));
        } else if (outputVlanType == OutputVlanType.REPLACE) {
            actionList.add(actionReplaceVlan(ofFactory, outputVlanId));
        } else if (outputVlanType == OutputVlanType.POP) {
            actionList.add(actionPopVlan(ofFactory));
        }
        return actionList;
    }

    private OFAction getOutputActionVlan(OFFactory ofFactory) {
        OFAction action;

        switch (outputVlanType) {
            case PUSH:
            case REPLACE:
                action = actionReplaceVlan(ofFactory, outputVlanId);
                break;
            case POP:
            case NONE:
                action = ofFactory.actions().popVlan();
                break;
            default:
                throw new UnsupportedOperationException(String.format("Unknown OutputVlanType: %s", outputVlanType));
        }

        return action;
    }

}
