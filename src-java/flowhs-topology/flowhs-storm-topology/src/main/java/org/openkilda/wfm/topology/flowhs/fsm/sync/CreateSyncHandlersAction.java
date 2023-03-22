/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.sync;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.model.SpeakerRequestBuildContext;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathChunk;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationConfig;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationDescriptor;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathRequest;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathRequest.PathChunkType;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilder;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilderFactory;
import org.openkilda.wfm.topology.flowhs.service.FlowSegmentRequestFactoriesSequence;
import org.openkilda.wfm.topology.flowhs.service.FlowSyncCarrier;

import lombok.NonNull;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CreateSyncHandlersAction<F extends SyncFsmBase<F, S, E>, S, E>
        extends FlowProcessingWithHistorySupportAction<F, S, E, FlowSyncContext> {
    private final FlowCommandBuilderFactory commandBuilderFactory;
    private final FlowPathOperationConfig flowPathOperationConfig;

    public CreateSyncHandlersAction(
            @NonNull PersistenceManager persistenceManager, @NonNull FlowResourcesManager resourcesManager,
            @NonNull FlowPathOperationConfig flowPathOperationConfig) {
        super(persistenceManager);

        this.commandBuilderFactory = new FlowCommandBuilderFactory(resourcesManager);
        this.flowPathOperationConfig = flowPathOperationConfig;
    }

    @Override
    protected void perform(S from, S to, E event, FlowSyncContext context, F stateMachine) {
        for (String flowId : stateMachine.getTargets()) {
            proceedFlow(stateMachine, getFlow(flowId));
        }
    }

    private void proceedFlow(F stateMachine, Flow flow) {
        FlowCommandBuilder commandBuilder = commandBuilderFactory.getBuilder(flow.getEncapsulationType());

        proceedPathPair(stateMachine, commandBuilder, flow, flow.getForwardPath(), flow.getReversePath());
        if (flow.getProtectedForwardPathId() != null) {
            proceedPathPairSkippingIngressChunk(
                    stateMachine, commandBuilder, flow, flow.getProtectedForwardPath(), flow.getProtectedReversePath());
        }
    }

    private void proceedPathPair(
            F stateMachine, FlowCommandBuilder commandBuilder, Flow flow, FlowPath path, FlowPath oppositePath) {
        SpeakerRequestBuildContext speakerContext = buildBaseSpeakerContextForInstall(
                path.getSrcSwitchId(), path.getDestSwitchId());

        CommandContext commandContext = stateMachine.getCommandContext();
        Pair<FlowSegmentRequestFactoriesSequence, FlowSegmentRequestFactoriesSequence> notIngressPair =
                commandBuilder.buildAllExceptIngressSeverally(commandContext, flow, path, oppositePath);
        Pair<FlowSegmentRequestFactoriesSequence, FlowSegmentRequestFactoriesSequence> ingressPair =
                commandBuilder.buildIngressOnlySeverally(commandContext, flow, path, oppositePath, speakerContext);

        proceedPath(stateMachine, flow.getFlowId(), path, notIngressPair.getLeft(), ingressPair.getLeft());
        proceedPath(stateMachine, flow.getFlowId(), oppositePath, notIngressPair.getRight(), ingressPair.getRight());
    }

    private void proceedPath(
            F stateMachine, String flowId, FlowPath path,
            FlowSegmentRequestFactoriesSequence notIngressSequence,
            FlowSegmentRequestFactoriesSequence ingressSequence) {
        List<FlowPathChunk> chunks = Arrays.asList(
                new FlowPathChunk(PathChunkType.NOT_INGRESS, notIngressSequence),
                new FlowPathChunk(PathChunkType.INGRESS, ingressSequence));
        FlowPathRequest request = new FlowPathRequest(flowId, path.getPathId(), chunks, false);
        launchPathInstall(stateMachine, path, request);
    }

    private void proceedPathPairSkippingIngressChunk(
            F stateMachine, FlowCommandBuilder commandBuilder, Flow flow, FlowPath path,
            FlowPath oppositePath) {
        Pair<FlowSegmentRequestFactoriesSequence, FlowSegmentRequestFactoriesSequence> sequences =
                commandBuilder.buildAllExceptIngressSeverally(
                        stateMachine.getCommandContext(), flow, path, oppositePath);
        proceedPathSkippingIngressChunk(stateMachine, flow.getFlowId(), path, sequences.getLeft());
        proceedPathSkippingIngressChunk(stateMachine, flow.getFlowId(), oppositePath, sequences.getRight());
    }

    private void proceedPathSkippingIngressChunk(
            F stateMachine, String flowId, FlowPath path, FlowSegmentRequestFactoriesSequence sequence) {
        List<FlowPathChunk> chunks = Collections.singletonList(
                new FlowPathChunk(PathChunkType.NOT_INGRESS, sequence));
        FlowPathRequest request = new FlowPathRequest(flowId, path.getPathId(), chunks, false);
        launchPathInstall(stateMachine, path, request);
    }

    private void launchPathInstall(F stateMachine, FlowPath path, FlowPathRequest request) {
        final FlowPathStatus initialStatus = path.getStatus();
        path.setStatus(FlowPathStatus.IN_PROGRESS);

        final FlowSyncCarrier carrier = stateMachine.getCarrier();
        try {
            carrier.launchFlowPathInstallation(request, flowPathOperationConfig, stateMachine.getCommandContext());
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException(
                    String.format(
                            "Unable to initiate flow path install operation due to path operations collision: %s",
                            e.getMessage()), e);
        }

        stateMachine.addPendingPathOperation(new FlowPathOperationDescriptor(request, initialStatus));
    }
}
