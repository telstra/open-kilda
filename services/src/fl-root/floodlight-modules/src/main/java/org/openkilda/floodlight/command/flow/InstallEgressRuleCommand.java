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

import org.openkilda.floodlight.command.MessageWriter;
import org.openkilda.messaging.MessageContext;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InstallEgressRuleCommand extends InstallTransitRuleCommand {

    final OutputVlanType outputVlanType;
    final Integer outputVlanId;

    public InstallEgressRuleCommand(@JsonProperty("command_id") String commandId,
                                    @JsonProperty("flowid") String flowId,
                                    @JsonProperty("message_context") MessageContext messageContext,
                                    @JsonProperty("cookie") Long cookie,
                                    @JsonProperty("switch_id") SwitchId switchId,
                                    @JsonProperty("input_port") Integer inputPort,
                                    @JsonProperty("output_port") Integer outputPort,
                                    @JsonProperty("transit_vlan_id") Integer transitVlanId,
                                    @JsonProperty("output_vlan_type") OutputVlanType outputVlanType,
                                    @JsonProperty("output_vlan_id")Integer outputVlanId) {
        super(commandId, flowId, messageContext, cookie, switchId, inputPort, outputPort, transitVlanId);
        this.outputVlanType = outputVlanType;
        this.outputVlanId = outputVlanId;
    }

    @Override
    public List<MessageWriter> getCommands(IOFSwitch sw, FloodlightModuleContext moduleContext) {
        List<OFAction> actionList = new ArrayList<>();
        OFFactory ofFactory = sw.getOFFactory();

        // output action based on encap scheme
        actionList.add(getOutputAction(ofFactory));

        // transmit packet from outgoing port
        actionList.add(setOutputPort(ofFactory));

        // build instruction with action list
        OFInstructionApplyActions actions = applyActions(ofFactory, actionList);

        // build FLOW_MOD command, no meter
        OFFlowMod flowMod = prepareFlowModBuilder(ofFactory, cookie & FLOW_COOKIE_MASK, FLOW_PRIORITY)
                .setMatch(matchFlow(inputPort, transitVlanId, ofFactory))
                .setInstructions(ImmutableList.of(actions))
                .build();

        return Collections.singletonList(new MessageWriter(flowMod));
    }

    private OFAction getOutputAction(OFFactory ofFactory) {
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
