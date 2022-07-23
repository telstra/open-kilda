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

package org.openkilda.wfm.topology.flowhs.service.yflow;

import static java.lang.String.format;

import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.messaging.command.yflow.YFlowPathSwapRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.exceptions.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exceptions.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowPathSwapService;
import org.openkilda.wfm.topology.flowhs.service.FlowProcessingEventListener;
import org.openkilda.wfm.topology.flowhs.service.common.YFlowProcessingService;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class YFlowPathSwapService
        extends YFlowProcessingService<YFlowPathSwapFsm, Event, YFlowPathSwapContext, FlowGenericCarrier> {
    private final YFlowPathSwapFsm.Factory fsmFactory;
    private final FlowPathSwapService flowPathSwapService;

    public YFlowPathSwapService(@NonNull FlowGenericCarrier carrier, @NonNull PersistenceManager persistenceManager,
                                @NonNull RuleManager ruleManager, @NonNull FlowPathSwapService flowPathSwapService,
                                int speakerCommandRetriesLimit) {
        super(new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);
        fsmFactory = new YFlowPathSwapFsm.Factory(carrier, persistenceManager, ruleManager, flowPathSwapService,
                speakerCommandRetriesLimit);
        this.flowPathSwapService = flowPathSwapService;
        addFlowPathSwapEventListener();
    }

    private void addFlowPathSwapEventListener() {
        flowPathSwapService.addEventListener(new FlowProcessingEventListener() {
            @Override
            public void onCompleted(String flowId) {
                YFlowPathSwapFsm fsm = getFsmBySubFlowId(flowId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Received a FlowPathSwap.Completed event for unknown sub-flow " + flowId));
                YFlowPathSwapContext context = YFlowPathSwapContext.builder().subFlowId(flowId).build();
                fsm.fire(Event.SUB_FLOW_SWAPPED, context);
            }

            @Override
            public void onFailed(String flowId, String errorReason, ErrorType errorType) {
                YFlowPathSwapFsm fsm = getFsmBySubFlowId(flowId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Received a FlowPathSwap.Failed event for unknown sub-flow " + flowId));
                YFlowPathSwapContext context = YFlowPathSwapContext.builder()
                        .subFlowId(flowId)
                        .error(errorReason)
                        .errorType(errorType)
                        .build();
                fsm.fire(Event.SUB_FLOW_FAILED, context);
            }
        });
    }

    /**
     * Handles request for y-flow path swapping.
     *
     * @param key command identifier.
     * @param request request data.
     */
    public void handleRequest(@NonNull String key, @NonNull CommandContext commandContext,
                              @NonNull YFlowPathSwapRequest request) throws DuplicateKeyException {
        String yFlowId = request.getYFlowId();
        log.debug("Handling y-flow path swap request with key {} and yFlowId {}", key, yFlowId);

        if (fsmRegister.hasRegisteredFsmWithKey(key)) {
            throw new DuplicateKeyException(key, "There's another active FSM with the same key");
        }
        if (fsmRegister.hasRegisteredFsmWithFlowId(yFlowId)) {
            sendErrorResponseToNorthbound(ErrorType.ALREADY_EXISTS, "Could not swap y-flow paths",
                    format("Y-flow %s is already in progress now", yFlowId), commandContext);
            return;
        }

        YFlowPathSwapFsm fsm = fsmFactory.newInstance(commandContext, yFlowId, eventListeners);
        fsmRegister.registerFsm(key, fsm);

        YFlowPathSwapContext context = YFlowPathSwapContext.builder().build();
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
        YFlowPathSwapFsm fsm = fsmRegister.getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        if (speakerResponse instanceof SpeakerFlowSegmentResponse) {
            SpeakerFlowSegmentResponse response = (SpeakerFlowSegmentResponse) speakerResponse;
            String flowId = response.getMetadata().getFlowId();
            if (fsm.getSwappingSubFlows().contains(flowId)) {
                flowPathSwapService.handleAsyncResponseByFlowId(flowId, response);
            }
        } else if (speakerResponse instanceof SpeakerCommandResponse) {
            SpeakerCommandResponse response = (SpeakerCommandResponse) speakerResponse;
            YFlowPathSwapContext context = YFlowPathSwapContext.builder()
                    .speakerResponse(response)
                    .build();
            fsmExecutor.fire(fsm, Event.RESPONSE_RECEIVED, context);
        } else {
            log.debug("Received unexpected speaker response: {}", speakerResponse);
        }

        // After handling an event by FlowPathSwap service, we should propagate execution to the FSM.
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
        YFlowPathSwapFsm fsm = fsmRegister.getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        // Propagate timeout event to all sub-flow processing FSMs.
        fsm.getSwappingSubFlows().forEach(flowId -> {
            try {
                flowPathSwapService.handleTimeoutByFlowId(flowId);
            } catch (UnknownKeyException e) {
                log.error("Failed to handle a timeout event by FlowPathSwapService for {}.", flowId);
            }
        });

        fsmExecutor.fire(fsm, Event.TIMEOUT);

        removeIfFinished(fsm, key);
    }

    private void removeIfFinished(YFlowPathSwapFsm fsm, String key) {
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
