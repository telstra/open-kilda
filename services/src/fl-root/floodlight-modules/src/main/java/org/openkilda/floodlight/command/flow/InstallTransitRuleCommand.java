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

import static org.projectfloodlight.openflow.protocol.OFVersion.OF_12;

import org.openkilda.floodlight.command.MessageWriter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InstallTransitRuleCommand extends FlowInstallCommand {
    final Integer transitVlanId;

    @JsonCreator
    public InstallTransitRuleCommand(@JsonProperty("command_id") String commandId,
                                     @JsonProperty("flowid") String flowId,
                                     @JsonProperty("message_context") MessageContext messageContext,
                                     @JsonProperty("cookie") Long cookie,
                                     @JsonProperty("switch_id") SwitchId switchId,
                                     @JsonProperty("input_port") Integer inputPort,
                                     @JsonProperty("output_port") Integer outputPort,
                                     @JsonProperty("transit_vlan_id") Integer transitVlanId) {
        super(commandId, flowId, messageContext, cookie, switchId, inputPort, outputPort);
        this.transitVlanId = transitVlanId;
    }

    @Override
    public List<MessageWriter> getCommands(IOFSwitch sw, FloodlightModuleContext moduleContext) {
        OFFactory factory = sw.getOFFactory();
        List<OFAction> actionList = new ArrayList<>();

        Match match = matchFlow(inputPort, transitVlanId, factory);
        actionList.add(setOutputPort(factory, OFPort.of(outputPort)));

        OFFlowMod flowMod = prepareFlowModBuilder(factory, cookie & FLOW_COOKIE_MASK, FLOW_PRIORITY)
                .setInstructions(ImmutableList.of(applyActions(factory, actionList)))
                .setMatch(match)
                .build();
        return Collections.singletonList(new MessageWriter(flowMod));
    }

    final OFAction setOutputPort(OFFactory ofFactory) {
        return setOutputPort(ofFactory, OFPort.of(outputPort));
    }

    final OFAction setOutputPort(OFFactory ofFactory, OFPort port) {
        OFActions actions = ofFactory.actions();
        return actions.buildOutput()
                .setMaxLen(0xFFFFFFFF)
                .setPort(port)
                .build();
    }

    final OFInstructionApplyActions applyActions(OFFactory ofFactory, List<OFAction> actionList) {
        return ofFactory.instructions().applyActions(actionList).createBuilder().build();
    }

    final OFAction actionReplaceVlan(final OFFactory factory, final int newVlan) {
        OFOxms oxms = factory.oxms();
        OFActions actions = factory.actions();
        OFVlanVidMatch vlanMatch = factory.getVersion() == OF_12
                ? OFVlanVidMatch.ofVlan(newVlan) : OFVlanVidMatch.ofRawVid((short) newVlan);

        return actions.buildSetField().setField(oxms.buildVlanVid()
                .setValue(vlanMatch)
                .build()).build();
    }

}
