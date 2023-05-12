/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.service.haflow;

import org.openkilda.messaging.MessageData;
import org.openkilda.messaging.command.haflow.HaFlowValidationRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.flow.FlowDumpResponse;
import org.openkilda.messaging.info.group.GroupDumpResponse;
import org.openkilda.messaging.info.meter.MeterDumpResponse;
import org.openkilda.messaging.info.meter.SwitchMeterUnsupported;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.validation.HaFlowValidationFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.validation.HaFlowValidationFsm.Event;
import org.openkilda.wfm.topology.flowhs.service.FlowValidationEventListener;
import org.openkilda.wfm.topology.flowhs.service.FlowValidationHubCarrier;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingFsmRegister;
import org.openkilda.wfm.topology.flowhs.service.common.FsmBasedProcessingService;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;

@Slf4j
public class HaFlowValidationHubService extends FsmBasedProcessingService<HaFlowValidationFsm, Event, Object,
        FlowProcessingFsmRegister<HaFlowValidationFsm>, FlowValidationEventListener> {
    private final FlowValidationHubCarrier carrier;
    private final HaFlowValidationFsm.Factory haFsmFactory;

    public HaFlowValidationHubService(@NonNull FlowValidationHubCarrier carrier,
                                      @NonNull PersistenceManager persistenceManager,
                                      @NonNull RuleManager ruleManager) {
        super(new FlowProcessingFsmRegister<>(), new FsmExecutor<>(Event.NEXT));
        this.carrier = carrier;

        haFsmFactory = new HaFlowValidationFsm.Factory(carrier, persistenceManager, ruleManager);
    }

    /**
     * Handles the flow validation request by starting the flow validation process.
     *
     * @param key the key associated with the request
     * @param commandContext the command context
     * @param request the ha-flow validation request object containing the flow ID
     * @throws DuplicateKeyException if there is a duplicate key found during the process
     */
    public void handleFlowValidationRequest(@NonNull String key, @NonNull CommandContext commandContext,
                                            @NonNull HaFlowValidationRequest request)
            throws DuplicateKeyException {
        startFlowValidation(key, commandContext, request.getHaFlowId());
    }

    private void startFlowValidation(@NonNull String key, @NonNull CommandContext commandContext,
                                     @NonNull String haFlowId) throws DuplicateKeyException {
        log.debug("Handling Haflow validation request with key {} and haflow ID: {}", key, haFlowId);

        if (fsmRegister.hasRegisteredFsmWithKey(key)) {
            throw new DuplicateKeyException(key, "There's another active FSM with the same key");
        }

        HaFlowValidationFsm fsm = haFsmFactory.newInstance(haFlowId, commandContext, eventListeners);
        fsmRegister.registerFsm(key, fsm);

        fsm.start();
        fsmExecutor.fire(fsm, Event.NEXT, haFlowId);

        removeIfFinished(fsm, key);
    }

    /**
     * Handles the async response from the worker. This method is called when a response
     * is received from the worker.
     *
     * <p>This method first retrieves the fsm associated with the provided key from the fsmRegister.
     * The fsm is then used to determine which event to fire.
     * The event fired is based on the type of data object received. The events that may be fired are:
     * 1. RULES_RECEIVED - fired when a FlowDumpResponse message is received.
     * 2. METERS_RECEIVED - fired when a MeterDumpResponse or SwitchMeterUnsupported message is received.
     * 3. GROUPS_RECEIVED - fired when a GroupDumpResponse message is received.
     * 4. ERROR - fired when an ErrorData message is received
     *
     * <p>If the data object received is not one of the types mentioned above, a warning
     * is logged and no event is fired.
     * Once the event is fired, the fsm is checked to see if it is in a terminal state.
     * If it is, it is removed from the fsmRegister.
     *
     * @param key  a unique identifier for the command being handled.
     * @param data the message data object containing the response received from the worker.
     * @throws UnknownKeyException if the key used to retrieve the finite state machine (fsm)
     *                             from the fsmRegister is not found.
     */
    public void handleAsyncResponse(@NonNull String key, @NonNull MessageData data) throws UnknownKeyException {
        log.debug("Received the command response {}", data);
        HaFlowValidationFsm fsm = fsmRegister.getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        if (data instanceof FlowDumpResponse) {
            fsmExecutor.fire(fsm, Event.RULES_RECEIVED, data);
        } else if (data instanceof MeterDumpResponse) {
            fsmExecutor.fire(fsm, Event.METERS_RECEIVED, data);
        } else if (data instanceof SwitchMeterUnsupported) {
            SwitchMeterUnsupported meterUnsupported = (SwitchMeterUnsupported) data;
            log.info("The key: {}; Meters unsupported for the switch '{};", key, meterUnsupported.getSwitchId());
            fsmExecutor.fire(fsm, Event.METERS_RECEIVED,
                    MeterDumpResponse.builder()
                            .switchId(meterUnsupported.getSwitchId())
                            .meterSpeakerData(Collections.emptyList())
                            .build());
        } else if (data instanceof GroupDumpResponse) {
            fsmExecutor.fire(fsm, Event.GROUPS_RECEIVED, data);
        } else if (data instanceof ErrorData) {
            fsmExecutor.fire(fsm, Event.ERROR, data);
        } else {
            log.warn("The key: {}; Unhandled message {}", key, data);
        }

        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from a worker. This method is used when the command identifier is
     * known, and it identifies the FSM to handle the response.
     *
     * @param flowId the flow identifier.
     * @param data the async response from the worker.
     * @throws UnknownKeyException if the key cannot be retrieved from the FSM register.
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
        HaFlowValidationFsm fsm = fsmRegister.getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        ErrorData errorData = new ErrorData(ErrorType.OPERATION_TIMED_OUT,
                "The ha-flow validation failed by timeout",
                "The error in the HaFlowValidationHubService");
        fsmExecutor.fire(fsm, Event.ERROR, errorData);

        removeIfFinished(fsm, key);
    }

    private void removeIfFinished(HaFlowValidationFsm fsm, String key) {
        if (fsm.isTerminated()) {
            log.debug("FSM with the key {} is finished with the state {}", key, fsm.getCurrentState());
            fsmRegister.unregisterFsm(key);

            carrier.cancelTimeoutCallback(key);

            if (!isActive() && !fsmRegister.hasAnyRegisteredFsm()) {
                carrier.sendInactive();
            }
        }
    }
}
