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

package org.openkilda.floodlight.command.flow.egress;

import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.command.flow.FlowSegmentReport;
import org.openkilda.floodlight.command.flow.NotIngressFlowSegmentCommand;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;

import lombok.Getter;
import lombok.NonNull;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.MacAddress;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
abstract class EgressFlowSegmentBlankCommand extends NotIngressFlowSegmentCommand {
    protected final FlowEndpoint endpoint;
    protected final FlowEndpoint ingressEndpoint;

    EgressFlowSegmentBlankCommand(
            MessageContext messageContext, UUID commandId, FlowSegmentMetadata metadata,
            @NonNull FlowEndpoint endpoint, @NonNull FlowEndpoint ingressEndpoint, Integer islPort,
            FlowTransitEncapsulation encapsulation) {
        super(messageContext, endpoint.getDatapath(), commandId, metadata, islPort, encapsulation);
        this.endpoint = endpoint;
        this.ingressEndpoint = ingressEndpoint;
    }

    @Override
    protected CompletableFuture<FlowSegmentReport> makeExecutePlan(
            SpeakerCommandProcessor commandProcessor) {
        try (Session session = getSessionService().open(messageContext, getSw())) {
            return session.write(makeEgressModMessage())
                    .thenApply(ignore -> makeSuccessReport());
        }
    }

    protected OFFlowMod makeEgressModMessage() {
        OFFactory of = getSw().getOFFactory();

        return setFlowModTableId(makeFlowModBuilder(of), SwitchManager.EGRESS_TABLE_ID)
                .setMatch(makeTransitMatch(of))
                .setInstructions(makeEgressModMessageInstructions(of))
                .build();
    }

    @Override
    protected void makeTransitVxLanMatch(OFFactory of, Match.Builder match) {
        match.setExact(MatchField.ETH_DST, MacAddress.of(getSw().getId()));
        super.makeTransitVxLanMatch(of, match);
    }

    protected abstract List<OFInstruction> makeEgressModMessageInstructions(OFFactory of);

    public String toString() {
        return String.format(
                "<egress-flow-segment-%s{"
                        + "id=%s, metadata=%s, endpoint=%s, ingressEndpoint=%s, isl_port=%s, encapsulation=%s}>",
                getSegmentAction(), commandId, metadata, endpoint, ingressEndpoint, ingressIslPort, encapsulation);
    }

    protected abstract SegmentAction getSegmentAction();
}
