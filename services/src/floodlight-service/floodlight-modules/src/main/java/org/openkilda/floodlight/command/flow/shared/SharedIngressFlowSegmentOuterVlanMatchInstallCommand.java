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

package org.openkilda.floodlight.command.flow.shared;

import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.floodlight.utils.MetadataAdapter;
import org.openkilda.floodlight.utils.OfFlowModBuilderFactory;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TableId;

import java.util.List;
import java.util.UUID;

public class SharedIngressFlowSegmentOuterVlanMatchInstallCommand
        extends SharedIngressFlowSegmentOuterVlanMatchCommand {
    @JsonCreator
    public SharedIngressFlowSegmentOuterVlanMatchInstallCommand(
            @JsonProperty("message_context") MessageContext messageContext,
            @JsonProperty("command_id") UUID commandId,
            @JsonProperty("metadata") FlowSegmentMetadata metadata,
            @JsonProperty("endpoint") FlowEndpoint endpoint) {
        super(messageContext, commandId, metadata, endpoint, OfFlowModBuilderFactory.makeFactory().actionAdd());
    }

    @Override
    protected List<OFInstruction> makeFlowModMessageInstructions() {
        OFFactory of = getSw().getOFFactory();
        MetadataAdapter.MetadataMatch metadata = MetadataAdapter.INSTANCE.addressOuterVlan(
                OFVlanVidMatch.ofVlan(endpoint.getOuterVlanId()));
        return ImmutableList.of(
                of.instructions().applyActions(ImmutableList.of(of.actions().popVlan())),
                of.instructions().writeMetadata(metadata.getValue(), metadata.getMask()),
                of.instructions().gotoTable(TableId.of(SwitchManager.INGRESS_TABLE_ID)));
    }

    @Override
    protected SegmentAction getSegmentAction() {
        return SegmentAction.INSTALL;
    }
}
