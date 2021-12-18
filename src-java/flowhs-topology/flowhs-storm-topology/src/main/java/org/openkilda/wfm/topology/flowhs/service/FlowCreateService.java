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
import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.messaging.error.ErrorType;
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
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Config;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMapper;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingFsmRegister;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingService;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FlowCreateService extends FlowProcessingService<FlowCreateFsm, Event, FlowCreateContext,
        FlowGenericCarrier, FlowProcessingFsmRegister<FlowCreateFsm>, FlowCreateEventListener> {
    private final FlowCreateFsm.Factory fsmFactory;
    private final KildaConfigurationRepository kildaConfigurationRepository;

    public FlowCreateService(@NonNull FlowGenericCarrier carrier, @NonNull PersistenceManager persistenceManager,
                             @NonNull PathComputer pathComputer, @NonNull FlowResourcesManager flowResourcesManager,
                             int genericRetriesLimit, int pathAllocationRetriesLimit,
                             int pathAllocationRetryDelay, int speakerCommandRetriesLimit) {
        super(new FlowProcessingFsmRegister<>(), new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        kildaConfigurationRepository = repositoryFactory.createKildaConfigurationRepository();

        Config fsmConfig = Config.builder()
                .flowCreationRetriesLimit(genericRetriesLimit)
                .pathAllocationRetriesLimit(pathAllocationRetriesLimit)
                .pathAllocationRetryDelay(pathAllocationRetryDelay)
                .speakerCommandRetriesLimit(speakerCommandRetriesLimit)
                .build();
        fsmFactory = new FlowCreateFsm.Factory(carrier, persistenceManager, flowResourcesManager, pathComputer,
                fsmConfig);
    }

    /**
     * Handles request for flow creation.
     *
     * @param key command identifier.
     * @param request request data.
     */
    public void handleRequest(@NonNull String key, @NonNull CommandContext commandContext, @NonNull FlowRequest request)
            throws DuplicateKeyException {
        RequestedFlow requestedFlow = RequestedFlowMapper.INSTANCE.toRequestedFlow(request);
        startFlowCreation(key, commandContext, requestedFlow, requestedFlow.getFlowId());
    }

    /**
     * Start flow creation for the provided information.
     */
    public void startFlowCreation(@NonNull CommandContext commandContext, @NonNull RequestedFlow requestedFlow,
                                  String sharedBandwidthGroupId) {
        try {
            startFlowCreation(requestedFlow.getFlowId(), commandContext, requestedFlow, sharedBandwidthGroupId);
        } catch (DuplicateKeyException e) {
            throw new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                    format("Failed to initiate flow creation for %s / %s: %s", requestedFlow.getFlowId(), e.getKey(),
                            e.getMessage()));
        }
    }

    private void startFlowCreation(String key, CommandContext commandContext, RequestedFlow requestedFlow,
                                   String sharedBandwidthGroupId)
            throws DuplicateKeyException {
        String flowId = requestedFlow.getFlowId();
        log.debug("Handling flow create request with key {} and flow ID: {}", key, flowId);

        if (fsmRegister.hasRegisteredFsmWithKey(key)) {
            throw new DuplicateKeyException(key, "There's another active FSM with the same key");
        }
        if (fsmRegister.hasRegisteredFsmWithFlowId(flowId)) {
            sendErrorResponseToNorthbound(ErrorType.ALREADY_EXISTS, "Could not create flow",
                    format("Flow %s is already creating now", flowId), commandContext);
            throw new DuplicateKeyException(key, "There's another active FSM for the same flowId " + flowId);
        }

        FlowCreateFsm fsm = fsmFactory.newInstance(commandContext, flowId, eventListeners);
        fsm.setSharedBandwidthGroupId(sharedBandwidthGroupId);
        fsmRegister.registerFsm(key, fsm);

        if (requestedFlow.getFlowEncapsulationType() == null) {
            requestedFlow.setFlowEncapsulationType(
                    kildaConfigurationRepository.getOrDefault().getFlowEncapsulationType());
        }
        if (requestedFlow.getPathComputationStrategy() == null) {
            requestedFlow.setPathComputationStrategy(
                    kildaConfigurationRepository.getOrDefault().getPathComputationStrategy());
        }
        FlowCreateContext context = FlowCreateContext.builder()
                .targetFlow(requestedFlow)
                .build();
        fsm.start(context);
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
        FlowCreateFsm fsm = fsmRegister.getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        FlowCreateContext context = FlowCreateContext.builder()
                .speakerFlowResponse(flowResponse)
                .build();
        fsmExecutor.fire(fsm, Event.RESPONSE_RECEIVED, context);

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
        FlowCreateFsm fsm = fsmRegister.getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        fsm.setTimedOut(true);
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

    private void removeIfFinished(FlowCreateFsm fsm, String key) {
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
