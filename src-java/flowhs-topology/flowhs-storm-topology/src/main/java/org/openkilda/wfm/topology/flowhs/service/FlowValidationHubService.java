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

import org.openkilda.messaging.MessageData;
import org.openkilda.messaging.command.flow.FlowValidationRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.meter.SwitchMeterUnsupported;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.rule.SwitchGroupEntries;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.validation.FlowValidationFsm;
import org.openkilda.wfm.topology.flowhs.fsm.validation.FlowValidationFsm.Config;
import org.openkilda.wfm.topology.flowhs.fsm.validation.FlowValidationFsm.Event;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingFsmRegister;
import org.openkilda.wfm.topology.flowhs.service.common.FsmBasedProcessingService;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;

@Slf4j
public class FlowValidationHubService extends FsmBasedProcessingService<FlowValidationFsm, Event, Object,
        FlowProcessingFsmRegister<FlowValidationFsm>, FlowValidationEventListener> {
    private final FlowValidationHubCarrier carrier;
    private final FlowValidationFsm.Factory fsmFactory;

    public FlowValidationHubService(@NonNull FlowValidationHubCarrier carrier,
                                    @NonNull PersistenceManager persistenceManager,
                                    @NonNull FlowResourcesManager flowResourcesManager,
                                    long flowMeterMinBurstSizeInKbits, double flowMeterBurstCoefficient) {
        super(new FlowProcessingFsmRegister<>(), new FsmExecutor<>(Event.NEXT));
        this.carrier = carrier;

        Config fsmConfig = Config.builder()
                .flowMeterMinBurstSizeInKbits(flowMeterMinBurstSizeInKbits)
                .flowMeterBurstCoefficient(flowMeterBurstCoefficient)
                .build();
        fsmFactory = new FlowValidationFsm.Factory(carrier, persistenceManager, flowResourcesManager, fsmConfig);
    }

    /**
     * Handle flow validation request.
     */
    public void handleFlowValidationRequest(@NonNull String key, @NonNull CommandContext commandContext,
                                            @NonNull FlowValidationRequest request)
            throws DuplicateKeyException {
        startFlowValidation(key, commandContext, request.getFlowId());
    }

    /**
     * Start flow validation for the provided information.
     */
    public void startFlowValidation(@NonNull CommandContext commandContext, @NonNull String flowId) {
        try {
            startFlowValidation(flowId, commandContext, flowId);
        } catch (DuplicateKeyException e) {
            throw new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                    format("Failed to initiate flow validation for %s / %s: %s", flowId, e.getKey(),
                            e.getMessage()));
        }
    }

    private void startFlowValidation(@NonNull String key, @NonNull CommandContext commandContext,
                                     @NonNull String flowId) throws DuplicateKeyException {
        log.debug("Handling flow validation request with key {} and flow ID: {}", key, flowId);

        if (fsmRegister.hasRegisteredFsmWithKey(key)) {
            throw new DuplicateKeyException(key, "There's another active FSM with the same key");
        }

        FlowValidationFsm fsm = fsmFactory.newInstance(flowId, commandContext, eventListeners);
        fsmRegister.registerFsm(key, fsm);

        fsm.start();
        fsmExecutor.fire(fsm, Event.NEXT, flowId);

        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from worker.
     *
     * @param key command identifier.
     */
    public void handleAsyncResponse(@NonNull String key, @NonNull MessageData data) throws UnknownKeyException {
        log.debug("Received command response {}", data);
        FlowValidationFsm fsm = fsmRegister.getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        if (data instanceof SwitchFlowEntries) {
            fsmExecutor.fire(fsm, Event.RULES_RECEIVED, data);
        } else if (data instanceof SwitchMeterEntries) {
            fsmExecutor.fire(fsm, Event.METERS_RECEIVED, data);
        } else if (data instanceof SwitchMeterUnsupported) {
            SwitchMeterUnsupported meterUnsupported = (SwitchMeterUnsupported) data;
            log.info("Key: {}; Meters unsupported for switch '{};", key, meterUnsupported.getSwitchId());
            fsmExecutor.fire(fsm, Event.METERS_RECEIVED, SwitchMeterEntries.builder()
                    .switchId(meterUnsupported.getSwitchId())
                    .meterEntries(Collections.emptyList())
                    .build());
        } else if (data instanceof SwitchGroupEntries) {
            fsmExecutor.fire(fsm, Event.GROUPS_RECEIVED, data);
        } else if (data instanceof ErrorData) {
            fsmExecutor.fire(fsm, Event.ERROR, data);
        } else {
            log.warn("Key: {}; Unhandled message {}", key, data);
        }

        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from worker.
     * Used if the command identifier is unknown, so FSM is identified by the flow Id.
     */
    public void handleAsyncResponseByFlowId(@NonNull String flowId, @NonNull MessageData data)
            throws UnknownKeyException {
        String commandKey = fsmRegister.getKeyByFlowId(flowId)
                .orElseThrow(() -> new UnknownKeyException(flowId));
        handleAsyncResponse(commandKey, data);
    }

    /**
     * Handles timeout case.
     *
     * @param key command identifier.
     */
    public void handleTimeout(@NonNull String key) throws UnknownKeyException {
        log.debug("Handling timeout for {}", key);
        FlowValidationFsm fsm = fsmRegister.getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        ErrorData errorData = new ErrorData(ErrorType.OPERATION_TIMED_OUT, "Flow validation failed by timeout",
                "Error in FlowValidationHubService");
        fsmExecutor.fire(fsm, Event.ERROR, errorData);

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

    private void removeIfFinished(FlowValidationFsm fsm, String key) {
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
