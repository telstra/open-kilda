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

package org.openkilda.wfm.topology.flowhs.service;

import static java.lang.String.format;

import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.flow.CreateFlowLoopRequest;
import org.openkilda.messaging.command.flow.DeleteFlowLoopRequest;
import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMapper;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class FlowUpdateService extends FsmBasedFlowProcessingService<FlowUpdateFsm, Event,
        FlowUpdateContext, FlowUpdateHubCarrier> {
    private final FlowUpdateFsm.Factory fsmFactory;
    private final KildaConfigurationRepository kildaConfigurationRepository;

    public FlowUpdateService(FlowUpdateHubCarrier carrier, PersistenceManager persistenceManager,
                             PathComputer pathComputer, FlowResourcesManager flowResourcesManager,
                             int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
                             int resourceAllocationRetriesLimit, int speakerCommandRetriesLimit) {
        super(new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);
        kildaConfigurationRepository = persistenceManager.getRepositoryFactory().createKildaConfigurationRepository();
        fsmFactory = new FlowUpdateFsm.Factory(carrier, persistenceManager, pathComputer, flowResourcesManager,
                pathAllocationRetriesLimit, pathAllocationRetryDelay, resourceAllocationRetriesLimit,
                speakerCommandRetriesLimit);
    }

    /**
     * Handles request for flow update.
     *
     * @param key command identifier.
     * @param request request data.
     */

    public void handleUpdateRequest(String key, CommandContext commandContext, FlowRequest request) {
        if (yFlowRepository.isSubFlow(request.getFlowId())) {
            sendForbiddenSubFlowOperationToNorthbound(request.getFlowId(), commandContext);
            return;
        }

        handleRequest(key, commandContext, request);
    }

    /**
     * Handles async response from worker.
     *
     * @param key command identifier.
     */
    public void handleAsyncResponse(String key, SpeakerFlowSegmentResponse flowResponse) {
        log.debug("Received flow command response {}", flowResponse);
        FlowUpdateFsm fsm = getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Failed to find a FSM: received response with key {} for non pending FSM", key);
            return;
        }

        FlowUpdateContext context = FlowUpdateContext.builder()
                .speakerFlowResponse(flowResponse)
                .build();

        if (flowResponse instanceof FlowErrorResponse) {
            fsmExecutor.fire(fsm, Event.ERROR_RECEIVED, context);
        } else {
            fsmExecutor.fire(fsm, Event.RESPONSE_RECEIVED, context);
        }

        removeIfFinished(fsm, key);
    }

    /**
     * Handles timeout case.
     *
     * @param key command identifier.
     */
    public void handleTimeout(String key) {
        log.debug("Handling timeout for {}", key);
        FlowUpdateFsm fsm = getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Failed to find a FSM: timeout event for non pending FSM with key {}", key);
            return;
        }

        fsmExecutor.fire(fsm, Event.TIMEOUT, null);

        removeIfFinished(fsm, key);
    }

    /**
     * Handles create flow loop request.
     *
     * @param request request to handle.
     */
    public void handleCreateFlowLoopRequest(String key, CommandContext commandContext,
                                            CreateFlowLoopRequest request) {
        if (yFlowRepository.isSubFlow(request.getFlowId())) {
            sendForbiddenSubFlowOperationToNorthbound(request.getFlowId(), commandContext);
            return;
        }

        Optional<Flow> flow = flowRepository.findById(request.getFlowId());
        if (flow.isPresent()) {
            FlowRequest flowRequest = RequestedFlowMapper.INSTANCE.toFlowRequest(flow.get());
            if (flowRequest.getLoopSwitchId() == null || flowRequest.getLoopSwitchId().equals(request.getSwitchId())) {
                flowRequest.setLoopSwitchId(request.getSwitchId());
                handleRequest(key, commandContext, flowRequest);
            } else {
                carrier.sendNorthboundResponse(buildFlowAlreadyLoopedErrorMessage(flowRequest, commandContext));
            }
        } else {
            carrier.sendNorthboundResponse(buildFlowNotFoundErrorMessage(request.getFlowId(), commandContext));
        }
    }

    /**
     * Handles delete flow loop request.
     *
     * @param request request to handle.
     */
    public void handleDeleteFlowLoopRequest(String key, CommandContext commandContext,
                                            DeleteFlowLoopRequest request) {
        if (yFlowRepository.isSubFlow(request.getFlowId())) {
            sendForbiddenSubFlowOperationToNorthbound(request.getFlowId(), commandContext);
            return;
        }

        Optional<Flow> flow = flowRepository.findById(request.getFlowId());
        if (flow.isPresent()) {
            FlowRequest flowRequest = RequestedFlowMapper.INSTANCE.toFlowRequest(flow.get());
            flowRequest.setLoopSwitchId(null);
            handleRequest(key, commandContext, flowRequest);
        } else {
            carrier.sendNorthboundResponse(buildFlowNotFoundErrorMessage(request.getFlowId(), commandContext));
        }
    }

    private void handleRequest(String key, CommandContext commandContext, FlowRequest request) {
        String flowId = request.getFlowId();
        log.debug("Handling flow update request with key {} and flow ID: {}", key, flowId);

        if (hasRegisteredFsmWithKey(key)) {
            log.error("Attempt to create a FSM with key {}, while there's another active FSM with the same key.", key);
            return;
        }
        if (hasRegisteredFsmWithFlowId(flowId)) {
            sendErrorResponseToNorthbound(ErrorType.REQUEST_INVALID, "Could not update flow",
                    format("Flow %s is updating now", flowId), commandContext);
            log.error("Attempt to create a FSM with key {}, while there's another active FSM for the same flowId {}.",
                    key, flowId);
            return;
        }

        FlowUpdateFsm fsm = fsmFactory.newInstance(commandContext, flowId);
        registerFsm(key, fsm);

        RequestedFlow requestedFlow = RequestedFlowMapper.INSTANCE.toRequestedFlow(request);
        if (requestedFlow.getFlowEncapsulationType() == null) {
            requestedFlow.setFlowEncapsulationType(kildaConfigurationRepository.getOrDefault()
                    .getFlowEncapsulationType());
        }
        FlowUpdateContext context = FlowUpdateContext.builder()
                .targetFlow(requestedFlow)
                .bulkUpdateFlowIds(request.getBulkUpdateFlowIds())
                .doNotRevert(request.isDoNotRevert())
                .build();
        fsmExecutor.fire(fsm, Event.NEXT, context);

        removeIfFinished(fsm, key);
    }

    private Message buildFlowNotFoundErrorMessage(String flowId, CommandContext commandContext) {
        String description = String.format("Flow '%s' not found.", flowId);
        ErrorData error = new ErrorData(ErrorType.NOT_FOUND, "Flow not found", description);
        return new ErrorMessage(error, commandContext.getCreateTime(), commandContext.getCorrelationId());
    }

    private Message buildFlowAlreadyLoopedErrorMessage(FlowRequest flow, CommandContext commandContext) {
        String description = String.format("Flow is already looped on switch '%s'", flow.getLoopSwitchId());
        ErrorData error = new ErrorData(ErrorType.UNPROCESSABLE_REQUEST,
                String.format("Can't create flow loop on '%s'", flow.getFlowId()), description);
        return new ErrorMessage(error, commandContext.getCreateTime(), commandContext.getCorrelationId());
    }

    private void removeIfFinished(FlowUpdateFsm fsm, String key) {
        if (fsm.isTerminated()) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            unregisterFsm(key);

            carrier.cancelTimeoutCallback(key);

            if (!isActive() && !hasAnyRegisteredFsm()) {
                carrier.sendInactive();
            }
        }
    }
}
