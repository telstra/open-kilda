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

package org.openkilda.wfm.topology.nbworker.services;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.nbtopology.request.FlowValidationRequest;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.nbworker.bolts.FlowValidationHubCarrier;
import org.openkilda.wfm.topology.nbworker.fsm.FlowValidationFsm;
import org.openkilda.wfm.topology.nbworker.fsm.FlowValidationFsm.FlowValidationEvent;
import org.openkilda.wfm.topology.nbworker.fsm.FlowValidationFsm.FlowValidationState;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class FlowValidationHubService {
    private Map<String, FlowValidationFsm> fsms = new HashMap<>();

    private PersistenceManager persistenceManager;
    private StateMachineBuilder<FlowValidationFsm, FlowValidationState, FlowValidationEvent, Object> builder;

    public FlowValidationHubService(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
        this.builder = FlowValidationFsm.builder();
    }

    /**
     * Handle flow validation request.
     */
    public void handleFlowValidationRequest(String key, FlowValidationRequest request,
                                            FlowValidationHubCarrier carrier) {
        FlowValidationFsm fsm =
                builder.newStateMachine(FlowValidationState.INITIALIZED, carrier, key, request, persistenceManager);
        process(fsm);
    }

    /**
     * Handle response from speaker worker.
     */
    public void handleAsyncResponse(String key, Message message) {
        FlowValidationFsm fsm = fsms.get(key);

        if (message instanceof InfoMessage) {
            InfoData data = ((InfoMessage) message).getData();
            if (data instanceof SwitchFlowEntries) {
                fsm.fire(FlowValidationEvent.RULES_RECEIVED, data);
            } else {
                log.warn("Key: {}; Unhandled message {}", key, message);
            }
        } else  if (message instanceof ErrorMessage) {
            fsm.fire(FlowValidationEvent.ERROR, message);
        }

        process(fsm);
    }

    /**
     * Handle timeout event.
     */
    public void handleTaskTimeout(String key) {
        FlowValidationFsm fsm = fsms.get(key);
        ErrorData errorData = new ErrorData(ErrorType.OPERATION_TIMED_OUT, "Flow validation failed by timeout",
                "Error in FlowValidationHubService");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);
        fsm.fire(FlowValidationEvent.ERROR, errorMessage);
    }

    private void process(FlowValidationFsm fsm) {
        final List<FlowValidationState> stopStates = Arrays.asList(
                FlowValidationState.RECEIVE_RULES,
                FlowValidationState.FINISHED,
                FlowValidationState.FINISHED_WITH_ERROR
        );

        while (!stopStates.contains(fsm.getCurrentState())) {
            fsms.put(fsm.getKey(), fsm);
            fsm.fire(FlowValidationEvent.NEXT);
        }

        final List<FlowValidationState> exitStates = Arrays.asList(
                FlowValidationState.FINISHED,
                FlowValidationState.FINISHED_WITH_ERROR
        );

        if (exitStates.contains(fsm.getCurrentState())) {
            fsms.remove(fsm.getKey());
        }
    }
}
