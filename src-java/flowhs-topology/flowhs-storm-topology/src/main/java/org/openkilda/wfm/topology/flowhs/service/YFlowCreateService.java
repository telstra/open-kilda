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
import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.Event;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class YFlowCreateService extends YFlowProcessingService<YFlowCreateFsm, Event, YFlowCreateContext,
        YFlowCreateHubCarrier> {
    private final YFlowCreateFsm.Factory fsmFactory;
    private final NoArgGenerator flowIdGenerator = Generators.timeBasedGenerator();
    private final String prefixForGeneratedYFlowId;
    private final String prefixForGeneratedSubFlowId;
    private final FlowCreateService flowCreateService;
    private final FlowDeleteService flowDeleteService;
    private final KildaConfigurationRepository kildaConfigurationRepository;

    public YFlowCreateService(YFlowCreateHubCarrier carrier, PersistenceManager persistenceManager,
                              PathComputer pathComputer, FlowResourcesManager flowResourcesManager,
                              FlowCreateService flowCreateService, FlowDeleteService flowDeleteService,
                              int resourceAllocationRetriesLimit, int speakerCommandRetriesLimit,
                              String prefixForGeneratedYFlowId, String prefixForGeneratedSubFlowId) {
        super(new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);
        fsmFactory = new YFlowCreateFsm.Factory(carrier, persistenceManager, pathComputer,
                flowResourcesManager, flowCreateService, flowDeleteService,
                resourceAllocationRetriesLimit, speakerCommandRetriesLimit);
        this.prefixForGeneratedYFlowId = prefixForGeneratedYFlowId;
        this.prefixForGeneratedSubFlowId = prefixForGeneratedSubFlowId;
        this.flowCreateService = flowCreateService;
        this.flowDeleteService = flowDeleteService;
        addFlowCreateEventListener();
        addFlowDeleteEventListener();
        kildaConfigurationRepository = persistenceManager.getRepositoryFactory().createKildaConfigurationRepository();
    }

    private void addFlowCreateEventListener() {
        flowCreateService.addEventListener(new FlowCreateEventListener() {
            @Override
            public void onResourcesAllocated(String flowId) {
                YFlowCreateFsm fsm = getFsmBySubFlowId(flowId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Received a FlowCreate.ResourcesAllocated event for unknown sub-flow " + flowId));
                YFlowCreateContext context = YFlowCreateContext.builder().subFlowId(flowId).build();
                fsm.fire(Event.SUB_FLOW_ALLOCATED, context);
            }

            @Override
            public void onCompleted(String flowId) {
                YFlowCreateFsm fsm = getFsmBySubFlowId(flowId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Received a FlowCreate.Completed event for unknown sub-flow " + flowId));
                YFlowCreateContext context = YFlowCreateContext.builder().subFlowId(flowId).build();
                fsm.fire(Event.SUB_FLOW_CREATED, context);
            }

            @Override
            public void onFailed(String flowId, String errorReason, ErrorType errorType) {
                YFlowCreateFsm fsm = getFsmBySubFlowId(flowId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Received a FlowCreate.Failed event for unknown sub-flow " + flowId));
                YFlowCreateContext context = YFlowCreateContext.builder()
                        .subFlowId(flowId)
                        .error(errorReason)
                        .errorType(errorType)
                        .build();
                fsm.fire(Event.SUB_FLOW_FAILED, context);
            }
        });
    }

    private void addFlowDeleteEventListener() {
        flowDeleteService.addEventListener(new FlowDeleteEventListener() {
            @Override
            public void onCompleted(String flowId) {
                YFlowCreateFsm fsm = getFsmBySubFlowId(flowId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Received a FlowDelete.Completed event for unknown sub-flow " + flowId));
                YFlowCreateContext context = YFlowCreateContext.builder().subFlowId(flowId).build();
                fsm.fire(Event.SUB_FLOW_REMOVED, context);
            }

            @Override
            public void onFailed(String flowId, String errorReason, ErrorType errorType) {
                YFlowCreateFsm fsm = getFsmBySubFlowId(flowId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Received a FlowDelete.Failed event for unknown sub-flow " + flowId));
                YFlowCreateContext context = YFlowCreateContext.builder()
                        .subFlowId(flowId)
                        .error(errorReason)
                        .errorType(errorType)
                        .build();
                fsm.fire(Event.SUB_FLOW_FAILED, context);
            }
        });
    }

    /**
     * Handles request for y-flow creation.
     *
     * @param key command identifier.
     * @param request request data.
     */
    public void handleRequest(String key, CommandContext commandContext, YFlowRequest request)
            throws DuplicateKeyException {
        String yFlowId = request.getYFlowId();
        log.debug("Handling y-flow create request with key {} and yFlowId {}", key, yFlowId);

        if (hasRegisteredFsmWithKey(key)) {
            throw new DuplicateKeyException(key, "There's another active FSM with the same key");
        }
        if (hasRegisteredFsmWithFlowId(yFlowId)) {
            sendErrorResponseToNorthbound(ErrorType.ALREADY_EXISTS, "Could not create y-flow",
                    format("Y-flow %s is already creating now", yFlowId), commandContext);
            return;
        }

        if (yFlowId == null) {
            yFlowId = generateFlowId(prefixForGeneratedYFlowId);
            request.setYFlowId(yFlowId);
        }
        request.getSubFlows().forEach(subFlow -> {
            if (subFlow.getFlowId() == null) {
                subFlow.setFlowId(generateFlowId(prefixForGeneratedSubFlowId));
            }
        });

        if (request.getEncapsulationType() == null) {
            request.setEncapsulationType(
                    kildaConfigurationRepository.getOrDefault().getFlowEncapsulationType());
        }
        if (request.getPathComputationStrategy() == null) {
            request.setPathComputationStrategy(
                    kildaConfigurationRepository.getOrDefault().getPathComputationStrategy());
        }

        YFlowCreateFsm fsm = fsmFactory.newInstance(commandContext, yFlowId, eventListeners);
        registerFsm(key, fsm);

        YFlowCreateContext context = YFlowCreateContext.builder()
                .targetFlow(request)
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
    public void handleAsyncResponse(String key, SpeakerFlowSegmentResponse flowResponse) throws UnknownKeyException {
        log.debug("Received flow command response: {}", flowResponse);
        YFlowCreateFsm fsm = getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        String flowId = flowResponse.getMetadata().getFlowId();
        if (fsm.getCreatingSubFlows().contains(flowId)) {
            flowCreateService.handleAsyncResponseByFlowId(flowId, flowResponse);
        } else if (fsm.getDeletingSubFlows().contains(flowId)) {
            flowDeleteService.handleAsyncResponseByFlowId(flowId, flowResponse);
        } else {
            YFlowCreateContext context = YFlowCreateContext.builder()
                    .speakerResponse(flowResponse)
                    .build();
            if (flowResponse instanceof FlowErrorResponse) {
                fsmExecutor.fire(fsm, Event.ERROR_RECEIVED, context);
            } else {
                fsmExecutor.fire(fsm, Event.RESPONSE_RECEIVED, context);
            }
        }

        // After handling an event by FlowCreate or FlowDelete services, we should propagate execution to the FSM.
        fsmExecutor.fire(fsm, Event.NEXT);

        removeIfFinished(fsm, key);
    }

    /**
     * Handles timeout case.
     *
     * @param key command identifier.
     */
    public void handleTimeout(String key) throws UnknownKeyException {
        log.debug("Handling timeout for {}", key);
        YFlowCreateFsm fsm = getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        // Propagate timeout event to all sub-flow processing FSMs.
        fsm.getCreatingSubFlows().forEach(flowId -> {
            try {
                flowCreateService.handleTimeoutByFlowId(flowId);
            } catch (UnknownKeyException e) {
                log.error("Failed to handle a timeout event by FlowCreateService for {}.", flowId);
            }
        });
        fsm.getDeletingSubFlows().forEach(flowId -> {
            try {
                flowDeleteService.handleTimeoutByFlowId(flowId);
            } catch (UnknownKeyException e) {
                log.error("Failed to handle a timeout event by FlowDeleteService for {}.", flowId);
            }
        });

        fsmExecutor.fire(fsm, Event.TIMEOUT);

        removeIfFinished(fsm, key);
    }

    private void removeIfFinished(YFlowCreateFsm fsm, String key) {
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
