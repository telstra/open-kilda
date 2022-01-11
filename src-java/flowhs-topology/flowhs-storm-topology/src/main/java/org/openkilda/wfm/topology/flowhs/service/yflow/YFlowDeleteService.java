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

import static java.lang.String.format;

import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.messaging.command.yflow.YFlowDeleteRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.service.FlowDeleteService;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowProcessingEventListener;
import org.openkilda.wfm.topology.flowhs.service.common.YFlowProcessingService;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class YFlowDeleteService
        extends YFlowProcessingService<YFlowDeleteFsm, Event, YFlowDeleteContext, FlowGenericCarrier> {
    private final YFlowDeleteFsm.Factory fsmFactory;
    private final FlowDeleteService flowDeleteService;

    public YFlowDeleteService(@NonNull FlowGenericCarrier carrier, @NonNull PersistenceManager persistenceManager,
                              @NonNull FlowResourcesManager flowResourcesManager, @NonNull RuleManager ruleManager,
                              @NonNull FlowDeleteService flowDeleteService, int speakerCommandRetriesLimit) {
        super(new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);
        fsmFactory = new YFlowDeleteFsm.Factory(carrier, persistenceManager,
                flowResourcesManager, ruleManager, flowDeleteService, speakerCommandRetriesLimit);
        this.flowDeleteService = flowDeleteService;
        addFlowDeleteEventListener();
    }

    private void addFlowDeleteEventListener() {
        flowDeleteService.addEventListener(new FlowProcessingEventListener() {
            @Override
            public void onCompleted(String flowId) {
                YFlowDeleteFsm fsm = getFsmBySubFlowId(flowId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Received a FlowDelete.Completed event for unknown sub-flow " + flowId));
                YFlowDeleteContext context = YFlowDeleteContext.builder().subFlowId(flowId).build();
                fsm.fire(Event.SUB_FLOW_REMOVED, context);
            }

            @Override
            public void onFailed(String flowId, String errorReason, ErrorType errorType) {
                YFlowDeleteFsm fsm = getFsmBySubFlowId(flowId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Received a FlowDelete.Failed event for unknown sub-flow " + flowId));
                YFlowDeleteContext context = YFlowDeleteContext.builder()
                        .subFlowId(flowId)
                        .error(errorReason)
                        .errorType(errorType)
                        .build();
                fsm.fire(Event.SUB_FLOW_FAILED, context);
            }
        });
    }

    /**
     * Handles request for y-flow deletion.
     *
     * @param key command identifier.
     * @param request request data.
     */
    public void handleRequest(@NonNull String key, @NonNull CommandContext commandContext,
                              @NonNull YFlowDeleteRequest request) throws DuplicateKeyException {
        String yFlowId = request.getYFlowId();
        log.debug("Handling y-flow delete request with key {} and yFlowId {}", key, yFlowId);

        if (fsmRegister.hasRegisteredFsmWithKey(key)) {
            throw new DuplicateKeyException(key, "There's another active FSM with the same key");
        }
        if (fsmRegister.hasRegisteredFsmWithFlowId(yFlowId)) {
            sendErrorResponseToNorthbound(ErrorType.ALREADY_EXISTS, "Could not delete y-flow",
                    format("Y-flow %s is already creating now", yFlowId), commandContext);
            return;
        }

        YFlowDeleteFsm fsm = fsmFactory.newInstance(commandContext, yFlowId, eventListeners);
        fsmRegister.registerFsm(key, fsm);

        YFlowDeleteContext context = YFlowDeleteContext.builder().build();
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
        log.debug("Received flow command response: {}", speakerResponse);
        YFlowDeleteFsm fsm = fsmRegister.getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        if (speakerResponse instanceof SpeakerFlowSegmentResponse) {
            SpeakerFlowSegmentResponse response = (SpeakerFlowSegmentResponse) speakerResponse;
            String flowId = response.getMetadata().getFlowId();
            if (fsm.getDeletingSubFlows().contains(flowId)) {
                flowDeleteService.handleAsyncResponseByFlowId(flowId, response);
            }
        } else if (speakerResponse instanceof SpeakerCommandResponse) {
            SpeakerCommandResponse response = (SpeakerCommandResponse) speakerResponse;
            YFlowDeleteContext context = YFlowDeleteContext.builder()
                    .speakerResponse(response)
                    .build();
            fsmExecutor.fire(fsm, Event.RESPONSE_RECEIVED, context);
        } else {
            log.debug("Received unexpected speaker response: {}", speakerResponse);
        }

        // After handling an event by FlowDelete services, we should propagate execution to the FSM.
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
        YFlowDeleteFsm fsm = fsmRegister.getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        // Propagate timeout event to all sub-flow processing FSMs.
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

    private void removeIfFinished(YFlowDeleteFsm fsm, String key) {
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
