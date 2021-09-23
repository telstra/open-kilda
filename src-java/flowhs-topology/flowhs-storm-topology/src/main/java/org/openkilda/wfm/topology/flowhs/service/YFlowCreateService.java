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

import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.history.FlowEventRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class YFlowCreateService {
    private final Map<String, YFlowCreateFsm> fsms = new HashMap<>();

    private final YFlowCreateFsm.Factory fsmFactory;
    private final FsmExecutor<YFlowCreateFsm, State, Event, YFlowCreateContext> fsmExecutor
            = new FsmExecutor<>(Event.NEXT);

    private final YFlowCreateHubCarrier carrier;
    private final FlowEventRepository flowEventRepository;

    private boolean active;

    public YFlowCreateService(YFlowCreateHubCarrier carrier, PersistenceManager persistenceManager,
                              PathComputer pathComputer, FlowResourcesManager flowResourcesManager,
                              FlowCreateService flowCreateService, FlowDeleteService flowDeleteService,
                              int resourceAllocationRetriesLimit, int speakerCommandRetriesLimit) {
        this.carrier = carrier;
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowEventRepository = repositoryFactory.createFlowEventRepository();
        fsmFactory = new YFlowCreateFsm.Factory(carrier, persistenceManager, pathComputer,
                flowResourcesManager, flowCreateService, flowDeleteService,
                resourceAllocationRetriesLimit, speakerCommandRetriesLimit);

        addFlowCreateEventListener(flowCreateService);
        addFlowDeleteEventListener(flowDeleteService);
    }

    private void addFlowCreateEventListener(FlowCreateService flowCreateService) {
        flowCreateService.addEventListener(new FlowCreateEventListener() {
            @Override
            public void onResourcesAllocated(String flowId) {
                YFlowCreateContext context = YFlowCreateContext.builder().subFlowId(flowId).build();
                fsms.forEach((k, fsm) -> {
                    if (fsm.getPendingSubFlows().contains(flowId)) {
                        fsmExecutor.fire(fsm, Event.SUB_FLOW_ALLOCATED, context);
                    }
                });
            }

            @Override
            public void onCompleted(String flowId) {
                YFlowCreateContext context = YFlowCreateContext.builder().subFlowId(flowId).build();
                fsms.forEach((k, fsm) -> {
                    if (fsm.getPendingSubFlows().contains(flowId)) {
                        fsmExecutor.fire(fsm, Event.SUB_FLOW_CREATED, context);
                    }
                });
            }

            @Override
            public void onFailed(String flowId, String errorReason, ErrorType errorType) {
                YFlowCreateContext context = YFlowCreateContext.builder()
                        .subFlowId(flowId)
                        .error(errorReason)
                        .errorType(errorType)
                        .build();
                fsms.forEach((k, fsm) -> {
                    if (fsm.getPendingSubFlows().contains(flowId)) {
                        fsmExecutor.fire(fsm, Event.SUB_FLOW_FAILED, context);
                    }
                });
            }
        });
    }

    private void addFlowDeleteEventListener(FlowDeleteService flowDeleteService) {
        flowDeleteService.addEventListener(new FlowDeleteEventListener() {
            @Override
            public void onCompleted(String flowId) {
                YFlowCreateContext context = YFlowCreateContext.builder().subFlowId(flowId).build();
                fsms.forEach((k, fsm) -> {
                    if (fsm.getPendingSubFlows().contains(flowId)) {
                        fsmExecutor.fire(fsm, Event.SUB_FLOW_REMOVED, context);
                    }
                });
            }

            public void onFailed(String flowId, String errorReason) {
                YFlowCreateContext context = YFlowCreateContext.builder()
                        .subFlowId(flowId)
                        .error(errorReason)
                        .build();
                fsms.forEach((k, fsm) -> {
                    if (fsm.getPendingSubFlows().contains(flowId)) {
                        fsmExecutor.fire(fsm, Event.SUB_FLOW_FAILED, context);
                    }
                });
            }
        });
    }

    /**
     * Handles request for y-flow creation.
     *
     * @param key command identifier.
     * @param request request data.
     */
    public void handleRequest(String key, CommandContext commandContext, YFlowRequest request) {
        log.debug("Handling y-flow create request with key {} and flow ID {}", key, request.getYFlowId());

        if (fsms.containsKey(key)) {
            log.error("Attempt to create a FSM with key {}, while there's another active FSM with the same key.", key);
            return;
        }

        String eventKey = commandContext.getCorrelationId();
        if (flowEventRepository.existsByTaskId(eventKey)) {
            log.error("Attempt to reuse key {}, but there's a history record(s) for it.", eventKey);
            return;
        }

        if (request.getYFlowId() == null) {
            request.setYFlowId(UUID.randomUUID().toString());
        }
        request.getSubFlows().forEach(subFlow -> {
            if (subFlow.getFlowId() == null) {
                subFlow.setFlowId(UUID.randomUUID().toString());
            }
        });

        YFlowCreateFsm fsm = fsmFactory.newInstance(commandContext, request.getYFlowId());
        fsms.put(key, fsm);

        YFlowCreateContext context = YFlowCreateContext.builder()
                .targetFlow(request)
                .build();
        fsm.setTargetFlow(request);

        fsmExecutor.fire(fsm, Event.NEXT, context);

        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from worker.
     *
     * @param key command identifier.
     */
    public void handleAsyncResponse(String key, SpeakerResponse flowResponse) {
        log.debug("Received flow command response: {}", flowResponse);
        YFlowCreateFsm fsm = fsms.get(key);
        if (fsm == null) {
            log.debug("Can't find a FSM: received response with key {} for non pending FSM", key);
            return;
        }

        YFlowCreateContext context = YFlowCreateContext.builder()
                .speakerResponse(flowResponse)
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
        YFlowCreateFsm fsm = fsms.get(key);
        if (fsm == null) {
            log.debug("Can't find a FSM: timeout event for non pending FSM with key {}", key);
            return;
        }

        fsmExecutor.fire(fsm, Event.TIMEOUT, null);

        removeIfFinished(fsm, key);
    }

    private void removeIfFinished(YFlowCreateFsm fsm, String key) {
        if (fsm.isTerminated()) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            fsms.remove(key);

            carrier.cancelTimeoutCallback(key);

            if (!active && fsms.isEmpty()) {
                carrier.sendInactive();
            }
        }
    }

    /**
     * Handles deactivate command.
     */
    public boolean deactivate() {
        active = false;
        if (fsms.isEmpty()) {
            return true;
        }
        return false;
    }

    /**
     * Handles activate command.
     */
    public void activate() {
        active = true;
    }
}
