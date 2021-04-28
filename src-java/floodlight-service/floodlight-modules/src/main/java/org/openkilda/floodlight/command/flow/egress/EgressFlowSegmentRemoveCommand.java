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

package org.openkilda.floodlight.command.flow.egress;

import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.command.flow.FlowSegmentReport;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.floodlight.utils.OfFlowModBuilderFactory;
import org.openkilda.floodlight.utils.OfFlowModDelMultiTableMessageBuilderFactory;
import org.openkilda.floodlight.utils.OfFlowModDelSingleTableMessageBuilderFactory;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.GroupId;
import org.openkilda.model.MirrorConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EgressFlowSegmentRemoveCommand extends EgressFlowSegmentCommand {
    private static OfFlowModBuilderFactory makeFlowModBuilderFactory(boolean isMultiTable) {
        if (isMultiTable) {
            return new OfFlowModDelMultiTableMessageBuilderFactory(SwitchManager.FLOW_PRIORITY);
        } else {
            return new OfFlowModDelSingleTableMessageBuilderFactory(SwitchManager.FLOW_PRIORITY);
        }
    }

    @JsonCreator
    public EgressFlowSegmentRemoveCommand(
            @JsonProperty("message_context") MessageContext context,
            @JsonProperty("command_id") UUID commandId,
            @JsonProperty("metadata") FlowSegmentMetadata metadata,
            @JsonProperty("endpoint") FlowEndpoint endpoint,
            @JsonProperty("ingress_endpoint") FlowEndpoint ingressEndpoint,
            @JsonProperty("isl_port") int islPort,
            @JsonProperty("encapsulation") FlowTransitEncapsulation encapsulation,
            @JsonProperty("mirror_config") MirrorConfig mirrorConfig) {
        super(
                context, commandId, metadata, endpoint, ingressEndpoint, islPort, encapsulation,
                makeFlowModBuilderFactory(metadata.isMultiTable()), mirrorConfig);
    }

    public EgressFlowSegmentRemoveCommand(MessageContext context, UUID commandId, FlowSegmentMetadata metadata,
                                          FlowEndpoint endpoint, FlowEndpoint ingressEndpoint, int islPort,
                                          FlowTransitEncapsulation encapsulation,
                                          OfFlowModBuilderFactory flowModBuilderFactory, MirrorConfig mirrorConfig) {
        super(
                context, commandId, metadata, endpoint, ingressEndpoint, islPort, encapsulation,
                flowModBuilderFactory, mirrorConfig);
    }

    @Override
    protected CompletableFuture<FlowSegmentReport> makeExecutePlan(SpeakerCommandProcessor commandProcessor) {
        return makeRemovePlan(commandProcessor);
    }

    @Override
    protected List<OFInstruction> makeEgressModMessageInstructions(OFFactory of, GroupId effectiveGroupId) {
        return ImmutableList.of();  // do not add instructions into delete request
    }

    @Override
    protected SegmentAction getSegmentAction() {
        return SegmentAction.REMOVE;
    }
}
