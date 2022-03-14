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

package org.openkilda.wfm.topology.flowhs.service.yflow;

import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.messaging.command.yflow.YFlowRerouteRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.reroute.error.RerouteInProgressError;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteEventListener;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteService;
import org.openkilda.wfm.topology.flowhs.service.common.YFlowProcessingService;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class YFlowRerouteService
        extends YFlowProcessingService<YFlowRerouteFsm, Event, YFlowRerouteContext, YFlowRerouteHubCarrier> {
    private final YFlowRerouteFsm.Factory fsmFactory;
    private final FlowRerouteService flowRerouteService;

    public YFlowRerouteService(@NonNull YFlowRerouteHubCarrier carrier, @NonNull PersistenceManager persistenceManager,
                               @NonNull PathComputer pathComputer, @NonNull FlowResourcesManager flowResourcesManager,
                               @NonNull RuleManager ruleManager,
                               @NonNull FlowRerouteService flowRerouteService,
                               int resourceAllocationRetriesLimit, int speakerCommandRetriesLimit) {
        super(new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);
        this.fsmFactory = new YFlowRerouteFsm.Factory(carrier, persistenceManager, pathComputer, flowResourcesManager,
                ruleManager, flowRerouteService, resourceAllocationRetriesLimit, speakerCommandRetriesLimit);
        this.flowRerouteService = flowRerouteService;
        addFlowRerouteEventListener();
    }

    private void addFlowRerouteEventListener() {
        flowRerouteService.addEventListener(new FlowRerouteEventListener() {
            @Override
            public void onResourcesAllocated(String flowId) {
                YFlowRerouteFsm fsm = getFsmBySubFlowId(flowId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Received a FlowReroute.ResourcesAllocated event for unknown sub-flow " + flowId));
                YFlowRerouteContext context = YFlowRerouteContext.builder().subFlowId(flowId).build();
                fsm.fire(Event.SUB_FLOW_ALLOCATED, context);
            }

            @Override
            public void onCompleted(String flowId) {
                YFlowRerouteFsm fsm = getFsmBySubFlowId(flowId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Received a FlowReroute.Completed event for unknown sub-flow " + flowId));
                YFlowRerouteContext context = YFlowRerouteContext.builder().subFlowId(flowId).build();
                fsm.fire(Event.SUB_FLOW_REROUTED, context);
            }

            @Override
            public void onFailed(String flowId, String errorReason, ErrorType errorType) {
                YFlowRerouteFsm fsm = getFsmBySubFlowId(flowId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Received a FlowReroute.Failed event for unknown sub-flow " + flowId));
                YFlowRerouteContext context = YFlowRerouteContext.builder()
                        .subFlowId(flowId)
                        .error(errorReason)
                        .errorType(errorType)
                        .build();
                fsm.fire(Event.SUB_FLOW_FAILED, context);
            }
        });
    }

    /**
     * Handles request for y-flow rerouting.
     *
     * @param key command identifier.
     * @param request request data.
     */
    public void handleRequest(@NonNull String key, @NonNull CommandContext commandContext,
                              @NonNull YFlowRerouteRequest request) throws DuplicateKeyException {
        String yFlowId = request.getYFlowId();
        log.debug("Handling y-flow reroute request with key {} and flow ID: {}", key, request.getYFlowId());

        if (fsmRegister.hasRegisteredFsmWithKey(key)) {
            throw new DuplicateKeyException(key, "There's another active FSM with the same key");
        }
        if (fsmRegister.hasRegisteredFsmWithFlowId(yFlowId)) {
            carrier.sendYFlowRerouteResultStatus(yFlowId, new RerouteInProgressError(),
                    commandContext.getCorrelationId());
            return;
        }

        YFlowRerouteFsm fsm = fsmFactory.newInstance(commandContext, request.getYFlowId(), eventListeners);
        fsmRegister.registerFsm(key, fsm);

        YFlowRerouteContext context = YFlowRerouteContext.builder()
                .rerouteRequest(request)
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
    public void handleAsyncResponse(@NonNull String key, @NonNull SpeakerResponse speakerResponse)
            throws UnknownKeyException {
        log.debug("Received flow command response {}", speakerResponse);
        YFlowRerouteFsm fsm = fsmRegister.getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        if (speakerResponse instanceof SpeakerFlowSegmentResponse) {
            SpeakerFlowSegmentResponse response = (SpeakerFlowSegmentResponse) speakerResponse;
            String flowId = response.getMetadata().getFlowId();
            if (fsm.getReroutingSubFlows().contains(flowId)) {
                flowRerouteService.handleAsyncResponseByFlowId(flowId, response);
            }
        } else if (speakerResponse instanceof SpeakerCommandResponse) {
            SpeakerCommandResponse response = (SpeakerCommandResponse) speakerResponse;
            YFlowRerouteContext context = YFlowRerouteContext.builder()
                    .speakerResponse(response)
                    .build();
            fsmExecutor.fire(fsm, Event.RESPONSE_RECEIVED, context);
        } else {
            log.debug("Received unexpected speaker response: {}", speakerResponse);
        }

        // After handling an event by FlowReroute service, we should propagate execution to the FSM.
        if (!fsm.isTerminated()) {
            fsmExecutor.fire(fsm, Event.NEXT);
        }

        removeIfFinished(fsm, key);
    }

    /**
     * Handles timeout case.
     *
     * @param key command identifier.
     */
    public void handleTimeout(@NonNull String key) throws UnknownKeyException {
        log.debug("Handling timeout for {}", key);
        YFlowRerouteFsm fsm = fsmRegister.getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        // Propagate timeout event to all sub-flow processing FSMs.
        fsm.getReroutingSubFlows().forEach(flowId -> {
            try {
                flowRerouteService.handleTimeoutByFlowId(flowId);
            } catch (UnknownKeyException e) {
                log.error("Failed to handle a timeout event by FlowRerouteService for {}.", flowId);
            }
        });

        fsmExecutor.fire(fsm, Event.TIMEOUT);

        removeIfFinished(fsm, key);
    }

    private void removeIfFinished(YFlowRerouteFsm fsm, String key) {
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
