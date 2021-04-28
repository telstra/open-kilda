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
import org.openkilda.floodlight.command.flow.NotIngressFlowSegmentCommand;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.floodlight.utils.OfFlowModBuilderFactory;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.GroupId;
import org.openkilda.model.MirrorConfig;

import com.google.common.collect.ImmutableList;
import lombok.Getter;
import lombok.NonNull;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
abstract class EgressFlowSegmentCommand extends NotIngressFlowSegmentCommand {
    protected final FlowEndpoint endpoint;
    protected final FlowEndpoint ingressEndpoint;

    @SuppressWarnings("squid:S00107")
    EgressFlowSegmentCommand(
            MessageContext messageContext, UUID commandId, FlowSegmentMetadata metadata,
            @NonNull FlowEndpoint endpoint, @NonNull FlowEndpoint ingressEndpoint, int islPort,
            FlowTransitEncapsulation encapsulation, OfFlowModBuilderFactory flowModBuilderFactory,
            MirrorConfig mirrorConfig) {
        super(
                messageContext, endpoint.getSwitchId(), commandId, metadata, islPort, encapsulation,
                flowModBuilderFactory, mirrorConfig);
        this.endpoint = endpoint;
        this.ingressEndpoint = ingressEndpoint;
    }

    protected CompletableFuture<FlowSegmentReport> makeInstallPlan(SpeakerCommandProcessor commandProcessor) {
        CompletableFuture<GroupId> future = CompletableFuture.completedFuture(null);
        if (mirrorConfig != null) {
            if (mirrorConfig.isAddNewGroup()) {
                future = planGroupInstall(commandProcessor);
            } else {
                future = planGroupModify(commandProcessor);
            }
        }

        return future.thenCompose(this::planOfFlowsCommand)
                .thenApply(ignore -> makeSuccessReport());
    }

    protected CompletableFuture<FlowSegmentReport> makeRemovePlan(SpeakerCommandProcessor commandProcessor) {
        CompletableFuture<GroupId> future = CompletableFuture.completedFuture(null);
        if (mirrorConfig != null) {
            future = planGroupDryRun(commandProcessor)
                    .thenApply(this::handleGroupReport);
        }

        return future.thenCompose(this::planOfFlowsCommand)
                .thenCompose(effectiveGroupId -> planGroupRemove(commandProcessor, effectiveGroupId))
                .thenApply(ignore -> makeSuccessReport());
    }

    protected CompletableFuture<FlowSegmentReport> makeVerifyPlan(SpeakerCommandProcessor commandProcessor) {
        CompletableFuture<GroupId> future = CompletableFuture.completedFuture(null);
        if (mirrorConfig != null) {
            future = planGroupVerify(commandProcessor)
                    .thenApply(this::handleGroupReport);
        }

        return future.thenCompose(this::planOfFlowVerify);
    }

    private CompletableFuture<GroupId> planOfFlowsCommand(GroupId effectiveGroupId) {
        try (Session session = getSessionService().open(messageContext, getSw())) {
            return session.write(makeEgressModMessage(effectiveGroupId))
                    .thenApply(ignore -> effectiveGroupId);
        }
    }

    private CompletableFuture<FlowSegmentReport> planOfFlowVerify(GroupId effectiveGroupId) {
        return makeVerifyPlan(ImmutableList.of(makeEgressModMessage(effectiveGroupId)));
    }

    private OFFlowMod makeEgressModMessage(GroupId effectiveGroupId) {
        OFFactory of = getSw().getOFFactory();

        return flowModBuilderFactory.makeBuilder(of, TableId.of(SwitchManager.EGRESS_TABLE_ID))
                .setCookie(U64.of(metadata.getCookie().getValue()))
                .setMatch(makeTransitMatch(of))
                .setInstructions(makeEgressModMessageInstructions(of, effectiveGroupId))
                .build();
    }

    protected abstract List<OFInstruction> makeEgressModMessageInstructions(OFFactory of, GroupId effectiveGroupId);

    public String toString() {
        return String.format(
                "<egress-flow-segment-%s{"
                        + "id=%s, metadata=%s, endpoint=%s, ingressEndpoint=%s, isl_port=%s, encapsulation=%s}>",
                getSegmentAction(), commandId, metadata, endpoint, ingressEndpoint, ingressIslPort, encapsulation);
    }

    protected abstract SegmentAction getSegmentAction();
}
