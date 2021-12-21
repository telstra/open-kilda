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
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Config;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMapper;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingFsmRegister;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingService;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class FlowUpdateService extends FlowProcessingService<FlowUpdateFsm, Event, FlowUpdateContext,
        FlowUpdateHubCarrier, FlowProcessingFsmRegister<FlowUpdateFsm>, FlowUpdateEventListener> {
    private final FlowUpdateFsm.Factory fsmFactory;
    private final KildaConfigurationRepository kildaConfigurationRepository;

    public FlowUpdateService(@NonNull FlowUpdateHubCarrier carrier, @NonNull PersistenceManager persistenceManager,
                             @NonNull PathComputer pathComputer, @NonNull FlowResourcesManager flowResourcesManager,
                             int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
                             int resourceAllocationRetriesLimit, int speakerCommandRetriesLimit) {
        super(new FlowProcessingFsmRegister<>(), new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        kildaConfigurationRepository = repositoryFactory.createKildaConfigurationRepository();

        Config fsmConfig = Config.builder()
                .pathAllocationRetriesLimit(pathAllocationRetriesLimit)
                .pathAllocationRetryDelay(pathAllocationRetryDelay)
                .resourceAllocationRetriesLimit(resourceAllocationRetriesLimit)
                .speakerCommandRetriesLimit(speakerCommandRetriesLimit)
                .build();
        fsmFactory = new FlowUpdateFsm.Factory(carrier, fsmConfig, persistenceManager, pathComputer,
                flowResourcesManager);
    }

    /**
     * Handles request for flow update.
     *
     * @param key command identifier.
     * @param request request data.
     */
    public void handleUpdateRequest(@NonNull String key, @NonNull CommandContext commandContext,
                                    @NonNull FlowRequest request)
            throws DuplicateKeyException {
        if (yFlowRepository.isSubFlow(request.getFlowId())) {
            sendForbiddenSubFlowOperationToNorthbound(request.getFlowId(), commandContext);
            return;
        }

        RequestedFlow requestedFlow = RequestedFlowMapper.INSTANCE.toRequestedFlow(request);
        startFlowUpdating(key, commandContext, requestedFlow,
                request.isDoNotRevert(), request.getBulkUpdateFlowIds(), requestedFlow.getFlowId());
    }

    /**
     * Start flow updating without reverting for the provided information.
     */
    public void startFlowUpdating(@NonNull CommandContext commandContext, @NonNull RequestedFlow request,
                                  @NonNull String sharedBandwidthGroupId) {
        try {
            startFlowUpdating(request.getFlowId(), commandContext, request, true, Collections.emptySet(),
                    sharedBandwidthGroupId);
        } catch (DuplicateKeyException e) {
            throw new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                    format("Failed to initiate flow updating for %s / %s: %s", request.getFlowId(), e.getKey(),
                            e.getMessage()));
        }
    }

    private void startFlowUpdating(String key, CommandContext commandContext, RequestedFlow request,
                                   boolean doNotRevert, Set<String> bulkUpdateFlowIds, String sharedBandwidthGroupId)
            throws DuplicateKeyException {
        String flowId = request.getFlowId();
        log.debug("Handling flow update request with key {} and flow ID: {}", key, request.getFlowId());

        if (fsmRegister.hasRegisteredFsmWithKey(key)) {
            throw new DuplicateKeyException(key, "There's another active FSM with the same key");
        }

        if (fsmRegister.hasRegisteredFsmWithFlowId(flowId)) {
            sendErrorResponseToNorthbound(ErrorType.REQUEST_INVALID, "Could not update flow",
                    format("Flow %s is updating now", flowId), commandContext);
            log.error("Attempt to create a FSM with key {}, while there's another active FSM for the same flowId {}.",
                    key, flowId);
            return;
        }

        FlowUpdateFsm fsm = fsmFactory.newInstance(request.getFlowId(), commandContext, eventListeners);
        fsm.setSharedBandwidthGroupId(sharedBandwidthGroupId);
        fsmRegister.registerFsm(key, fsm);

        if (request.getFlowEncapsulationType() == null) {
            request.setFlowEncapsulationType(kildaConfigurationRepository.getOrDefault()
                    .getFlowEncapsulationType());
        }
        FlowUpdateContext context = FlowUpdateContext.builder()
                .targetFlow(request)
                .doNotRevert(doNotRevert)
                .bulkUpdateFlowIds(bulkUpdateFlowIds)
                .build();
        fsmExecutor.fire(fsm, Event.NEXT, context);

        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from worker.
     *
     * @param key command identifier.
     */
    public void handleAsyncResponse(@NonNull String key, @NonNull SpeakerFlowSegmentResponse flowResponse)
            throws UnknownKeyException {
        log.debug("Received flow command response {}", flowResponse);
        FlowUpdateFsm fsm = fsmRegister.getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

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
     * Handles async response from worker.
     * Used if the command identifier is unknown, so FSM is identified by the flow Id.
     */
    public void handleAsyncResponseByFlowId(@NonNull String flowId, @NonNull SpeakerFlowSegmentResponse flowResponse)
            throws UnknownKeyException {
        String commandKey = fsmRegister.getKeyByFlowId(flowId)
                .orElseThrow(() -> new UnknownKeyException(flowId));
        handleAsyncResponse(commandKey, flowResponse);
    }

    /**
     * Handles timeout case.
     *
     * @param key command identifier.
     */
    public void handleTimeout(@NonNull String key) throws UnknownKeyException {
        log.debug("Handling timeout for {}", key);
        FlowUpdateFsm fsm = fsmRegister.getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        fsmExecutor.fire(fsm, Event.TIMEOUT);

        removeIfFinished(fsm, key);
    }

    /**
     * Handles timeout case.
     * Used if the command identifier is unknown, so FSM is identified by the flow Id.
     */
    public void handleTimeoutByFlowId(@NonNull String flowId) throws UnknownKeyException {
        String commandKey = fsmRegister.getKeyByFlowId(flowId)
                .orElseThrow(() -> new UnknownKeyException(flowId));
        handleTimeout(commandKey);
    }

    /**
     * Handles create flow loop request.
     *
     * @param request request to handle.
     */
    public void handleCreateFlowLoopRequest(@NonNull String key, @NonNull CommandContext commandContext,
                                            @NonNull CreateFlowLoopRequest request) throws DuplicateKeyException {
        if (yFlowRepository.isSubFlow(request.getFlowId())) {
            sendForbiddenSubFlowOperationToNorthbound(request.getFlowId(), commandContext);
            return;
        }

        Optional<Flow> flow = flowRepository.findById(request.getFlowId());
        if (flow.isPresent()) {
            FlowRequest flowRequest = RequestedFlowMapper.INSTANCE.toFlowRequest(flow.get());
            if (flowRequest.getLoopSwitchId() == null || flowRequest.getLoopSwitchId().equals(request.getSwitchId())) {
                flowRequest.setLoopSwitchId(request.getSwitchId());
                handleUpdateRequest(key, commandContext, flowRequest);
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
    public void handleDeleteFlowLoopRequest(@NonNull String key, @NonNull CommandContext commandContext,
                                            @NonNull DeleteFlowLoopRequest request) throws DuplicateKeyException {
        if (yFlowRepository.isSubFlow(request.getFlowId())) {
            sendForbiddenSubFlowOperationToNorthbound(request.getFlowId(), commandContext);
            return;
        }

        Optional<Flow> flow = flowRepository.findById(request.getFlowId());
        if (flow.isPresent()) {
            FlowRequest flowRequest = RequestedFlowMapper.INSTANCE.toFlowRequest(flow.get());
            flowRequest.setLoopSwitchId(null);
            handleUpdateRequest(key, commandContext, flowRequest);
        } else {
            carrier.sendNorthboundResponse(buildFlowNotFoundErrorMessage(request.getFlowId(), commandContext));
        }
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
            fsmRegister.unregisterFsm(key);

            carrier.cancelTimeoutCallback(key);

            if (!isActive() && !fsmRegister.hasAnyRegisteredFsm()) {
                carrier.sendInactive();
            }
        }
    }
}
