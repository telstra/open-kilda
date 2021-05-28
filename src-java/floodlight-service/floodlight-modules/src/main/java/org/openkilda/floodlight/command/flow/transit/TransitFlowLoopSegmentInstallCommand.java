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

package org.openkilda.floodlight.command.flow.transit;

import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.floodlight.utils.OfFlowModAddMultiTableMessageBuilderFactory;
import org.openkilda.floodlight.utils.OfFlowModAddSingleTableMessageBuilderFactory;
import org.openkilda.floodlight.utils.OfFlowModBuilderFactory;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.List;
import java.util.UUID;

public class TransitFlowLoopSegmentInstallCommand extends TransitFlowSegmentCommand {

    private static OfFlowModBuilderFactory makeFlowModBuilderFactory(boolean isMultiTable) {
        if (isMultiTable) {
            return new OfFlowModAddMultiTableMessageBuilderFactory(SwitchManager.FLOW_LOOP_PRIORITY);
        } else {
            return new OfFlowModAddSingleTableMessageBuilderFactory(SwitchManager.FLOW_LOOP_PRIORITY);
        }
    }

    @JsonCreator
    public TransitFlowLoopSegmentInstallCommand(
            @JsonProperty("message_context") MessageContext context,
            @JsonProperty("switch_id") SwitchId switchId,
            @JsonProperty("command_id") UUID commandId,
            @JsonProperty("metadata") FlowSegmentMetadata metadata,
            @JsonProperty("ingress_isl_port") int ingressIslPort,
            @JsonProperty("encapsulation") FlowTransitEncapsulation encapsulation,
            @JsonProperty("egress_isl_port") int egressIslPort) {
        super(
                context, switchId, commandId, metadata, ingressIslPort, encapsulation, egressIslPort,
                makeFlowModBuilderFactory(metadata.isMultiTable()), null);
    }

    @Override
    protected List<OFInstruction> makeTransitModMessageInstructions(OFFactory of) {
        List<OFAction> applyActions = ImmutableList.of(
                of.actions().buildOutput()
                        .setPort(OFPort.IN_PORT)
                        .build());
        return ImmutableList.of(of.instructions().applyActions(applyActions));
    }

    @Override
    protected int getTableId() {
        return SwitchManager.EGRESS_TABLE_ID;
    }

    @Override
    protected SegmentAction getSegmentAction() {
        return SegmentAction.INSTALL;
    }
}
