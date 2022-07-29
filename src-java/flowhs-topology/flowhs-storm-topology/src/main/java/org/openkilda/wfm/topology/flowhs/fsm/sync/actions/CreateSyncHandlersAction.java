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

package org.openkilda.wfm.topology.flowhs.fsm.sync.actions;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.exceptions.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncContext;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncFsm;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncFsm.State;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathChunk;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationConfig;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationDescriptor;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathRequest;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathRequest.PathChunkType;
import org.openkilda.wfm.topology.flowhs.service.FlowSyncCarrier;
import org.openkilda.wfm.topology.flowhs.service.speaker.FlowCommandBuilder;
import org.openkilda.wfm.topology.flowhs.service.speaker.FlowCommandBuilderFactory;
import org.openkilda.wfm.topology.flowhs.service.speaker.FlowSegmentRequestFactoriesSequence;
import org.openkilda.wfm.topology.flowhs.service.speaker.SpeakerRequestBuildContext;

import lombok.NonNull;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CreateSyncHandlersAction
        extends FlowProcessingWithHistorySupportAction<FlowSyncFsm, State, Event, FlowSyncContext> {
    private final FlowSyncCarrier carrier;
    private final FlowCommandBuilderFactory commandBuilderFactory;
    private final FlowPathOperationConfig flowPathOperationConfig;

    public CreateSyncHandlersAction(
            @NonNull FlowSyncCarrier carrier, @NonNull PersistenceManager persistenceManager,
            @NonNull FlowResourcesManager resourcesManager,
            @NonNull FlowPathOperationConfig flowPathOperationConfig) {
        super(persistenceManager);

        this.carrier = carrier;
        this.commandBuilderFactory = new FlowCommandBuilderFactory(resourcesManager);
        this.flowPathOperationConfig = flowPathOperationConfig;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowSyncContext context, FlowSyncFsm stateMachine) {
        Flow flow = getFlow(stateMachine.getFlowId());
        FlowCommandBuilder commandBuilder = commandBuilderFactory.getBuilder(flow.getEncapsulationType());

        proceedPathPair(stateMachine, commandBuilder, flow, flow.getForwardPath(), flow.getReversePath());
        if (flow.getProtectedForwardPathId() != null) {
            proceedPathPairSkippingIngressChunk(
                    stateMachine, commandBuilder, flow, flow.getProtectedForwardPath(), flow.getProtectedReversePath());
        }
    }

    private void proceedPathPair(
            FlowSyncFsm stateMachine, FlowCommandBuilder commandBuilder, Flow flow, FlowPath path,
            FlowPath oppositePath) {
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
            FlowSyncFsm stateMachine, String flowId, FlowPath path,
            FlowSegmentRequestFactoriesSequence notIngressSequence,
            FlowSegmentRequestFactoriesSequence ingressSequence) {
        List<FlowPathChunk> chunks = Arrays.asList(
                new FlowPathChunk(PathChunkType.NOT_INGRESS, notIngressSequence),
                new FlowPathChunk(PathChunkType.INGRESS, ingressSequence));
        FlowPathRequest request = new FlowPathRequest(flowId, path.getPathId(), chunks, false);
        launchPathInstall(stateMachine, path, request);
    }

    private void proceedPathPairSkippingIngressChunk(
            FlowSyncFsm stateMachine, FlowCommandBuilder commandBuilder, Flow flow, FlowPath path,
            FlowPath oppositePath) {
        Pair<FlowSegmentRequestFactoriesSequence, FlowSegmentRequestFactoriesSequence> sequences =
                commandBuilder.buildAllExceptIngressSeverally(
                        stateMachine.getCommandContext(), flow, path, oppositePath);
        proceedPathSkippingIngressChunk(stateMachine, flow.getFlowId(), path, sequences.getLeft());
        proceedPathSkippingIngressChunk(stateMachine, flow.getFlowId(), oppositePath, sequences.getRight());
    }

    private void proceedPathSkippingIngressChunk(
            FlowSyncFsm stateMachine, String flowId, FlowPath path, FlowSegmentRequestFactoriesSequence sequence) {
        List<FlowPathChunk> chunks = Collections.singletonList(
                new FlowPathChunk(PathChunkType.NOT_INGRESS, sequence));
        FlowPathRequest request = new FlowPathRequest(flowId, path.getPathId(), chunks, false);
        launchPathInstall(stateMachine, path, request);
    }

    private void launchPathInstall(
            FlowSyncFsm stateMachine, FlowPath path, FlowPathRequest request) {
        FlowPathOperationDescriptor descriptor = new FlowPathOperationDescriptor(request, path.getStatus());
        path.setStatus(FlowPathStatus.IN_PROGRESS);

        try {
            carrier.launchFlowPathInstallation(request, flowPathOperationConfig, stateMachine.getCommandContext());
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException(
                    String.format(
                            "Unable to initiate flow path install operation due to path operations collision: %s",
                            e.getMessage()), e);
        }

        stateMachine.addPendingPathOperation(descriptor);
    }
}
