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

import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.command.flow.FlowSegmentCommand;
import org.openkilda.floodlight.command.flow.FlowSegmentReport;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.floodlight.utils.OfFlowModBuilderFactory;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;

import lombok.Getter;
import lombok.NonNull;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
abstract class SharedIngressFlowSegmentOuterVlanMatchCommand extends FlowSegmentCommand {
    protected final FlowEndpoint endpoint;

    protected final OfFlowModBuilderFactory flowModBuilderFactory;

    protected SharedIngressFlowSegmentOuterVlanMatchCommand(
            MessageContext messageContext, UUID commandId, FlowSegmentMetadata metadata,
            @NonNull FlowEndpoint endpoint, @NonNull OfFlowModBuilderFactory.Factory modBuilderMetaFactory) {
        super(messageContext, endpoint.getSwitchId(), commandId, metadata);
        this.endpoint = endpoint;

        this.flowModBuilderFactory = modBuilderMetaFactory
                .basePriority(FlowSegmentCommand.FLOW_PRIORITY)
                // valid only in multy-table mode (validate() method guarantee acceptance only multy-table requests)
                .multiTable(true)
                .make();
    }

    @Override
    protected CompletableFuture<FlowSegmentReport> makeExecutePlan(SpeakerCommandProcessor commandProcessor) {
        try (Session session = getSessionService().open(messageContext, getSw())) {
            return session.write(makeFlowModMessage())
                    .thenApply(ignore -> makeSuccessReport());
        }
    }

    @Override
    protected void validate() {
        super.validate();

        if (! metadata.isMultiTable()) {
            throw new IllegalArgumentException(String.format(
                    "%s is applicable only in multi-table mode", getClass().getSimpleName()));
        }
    }

    protected OFFlowMod makeFlowModMessage() {
        OFFactory of = getSw().getOFFactory();
        return flowModBuilderFactory.makeBuilder(of, TableId.of(SwitchManager.PRE_INGRESS_TABLE_ID))
                .setCookie(U64.of(metadata.getCookie().getValue()))
                .setMatch(of.buildMatch()
                                  .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                                  .setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(endpoint.getOuterVlanId()))
                                  .build())
                .setInstructions(makeFlowModMessageInstructions())
                .build();
    }

    protected abstract List<OFInstruction> makeFlowModMessageInstructions();

    /**
     * Make string representation of object (irreversible).
     */
    public String toString() {
        return String.format(
                "<shared-of-flow-outer-vlan-match-%s{id=%s, metadata=%s, endpoint=%s}>",
                getSegmentAction(), commandId, metadata, endpoint);
    }

    protected abstract SegmentAction getSegmentAction();
}
