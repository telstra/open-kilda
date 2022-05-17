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

import org.openkilda.messaging.MessageData;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.flow.FlowValidationResponse;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationService;
import org.openkilda.wfm.topology.flowhs.service.FlowValidationEventListener;
import org.openkilda.wfm.topology.flowhs.service.FlowValidationHubService;
import org.openkilda.wfm.topology.flowhs.service.common.YFlowProcessingService;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class YFlowValidationHubService extends YFlowProcessingService<YFlowValidationFsm, Event,
        YFlowValidationContext, YFlowValidationHubCarrier> {
    private final YFlowValidationFsm.Factory fsmFactory;
    private final FlowValidationHubService flowValidationService;

    public YFlowValidationHubService(@NonNull YFlowValidationHubCarrier carrier,
                                     @NonNull PersistenceManager persistenceManager,
                                     @NonNull FlowValidationHubService flowValidationHubService,
                                     @NonNull YFlowValidationService yFlowValidationService) {
        super(new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);
        fsmFactory = new YFlowValidationFsm.Factory(carrier, persistenceManager, flowValidationHubService,
                yFlowValidationService);
        this.flowValidationService = flowValidationHubService;
        addFlowValidationEventListener();
    }

    private void addFlowValidationEventListener() {
        flowValidationService.addEventListener(new FlowValidationEventListener() {
            @Override
            public void onValidationResult(String flowId, List<FlowValidationResponse> validationResult) {
                YFlowValidationFsm fsm = getFsmBySubFlowId(flowId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Received a YFlowValidation.ValidationResult event for unknown sub-flow " + flowId));
                YFlowValidationContext context = YFlowValidationContext.builder()
                        .subFlowId(flowId)
                        .validationResult(validationResult)
                        .build();
                fsm.fire(Event.SUB_FLOW_VALIDATED, context);
            }

            @Override
            public void onFailedValidation(String flowId) {
                YFlowValidationFsm fsm = getFsmBySubFlowId(flowId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Received a YFlowValidation.Failed event for unknown sub-flow " + flowId));
                YFlowValidationContext context = YFlowValidationContext.builder()
                        .subFlowId(flowId)
                        .build();
                fsm.fire(Event.SUB_FLOW_FAILED, context);
            }
        });
    }

    /**
     * Handles request for y-flow validating.
     *
     * @param key command identifier.
     * @param yFlowId requested y-flow to validate.
     */
    public void handleRequest(@NonNull String key, @NonNull CommandContext commandContext, @NonNull String yFlowId)
            throws DuplicateKeyException {
        log.debug("Handling y-flow validation request with key {} and y-flow ID: {}", key, yFlowId);

        if (fsmRegister.hasRegisteredFsmWithKey(key)) {
            throw new DuplicateKeyException(key, "There's another active FSM with the same key");
        }
        if (fsmRegister.hasRegisteredFsmWithFlowId(yFlowId)) {
            sendErrorResponseToNorthbound(ErrorType.ALREADY_EXISTS, "Could not validate y-flow",
                    format("Y-flow %s is already validating now", yFlowId), commandContext);
            log.error("Attempt to create a FSM with key {}, while there's another active FSM for the same yFlowId {}.",
                    key, yFlowId);
            return;
        }

        YFlowValidationFsm fsm = fsmFactory.newInstance(commandContext, yFlowId, eventListeners);
        fsmRegister.registerFsm(key, fsm);

        YFlowValidationContext context = YFlowValidationContext.builder().build();
        fsm.start(context);
        fsmExecutor.fire(fsm, Event.NEXT, context);

        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from worker.
     *
     * @param key command identifier.
     */
    public void handleAsyncResponse(@NonNull String key, @NonNull String flowId, @NonNull MessageData data)
            throws UnknownKeyException {
        log.debug("Received worker response {}", data);
        YFlowValidationFsm fsm = fsmRegister.getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        if (fsm.getValidatingSubFlows().contains(flowId)) {
            flowValidationService.handleAsyncResponseByFlowId(flowId, data);
            // After handling an event by FlowUpdate service, we should propagate execution to the FSM.
            fsmExecutor.fire(fsm, Event.NEXT);
        } else {
            YFlowValidationContext context = YFlowValidationContext.builder()
                    .speakerResponse(data)
                    .build();
            if (data instanceof ErrorData) {
                fsmExecutor.fire(fsm, Event.ERROR_RECEIVED, context);
            } else {
                fsmExecutor.fire(fsm, Event.RESPONSE_RECEIVED, context);
            }
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
        YFlowValidationFsm fsm = fsmRegister.getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        // Propagate timeout event to all sub-flow processing FSMs.
        fsm.getValidatingSubFlows().forEach(flowId -> {
            try {
                flowValidationService.handleTimeoutByFlowId(flowId);
            } catch (UnknownKeyException e) {
                log.error("Failed to handle a timeout event by FlowUpdateService for {}.", flowId);
            }
        });

        fsmExecutor.fire(fsm, Event.TIMEOUT);

        removeIfFinished(fsm, key);
    }

    private void removeIfFinished(YFlowValidationFsm fsm, String key) {
        if (fsm.isTerminated()) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            fsmRegister.unregisterFsm(key);
            cancelProcessing(key);
        }
    }
}
