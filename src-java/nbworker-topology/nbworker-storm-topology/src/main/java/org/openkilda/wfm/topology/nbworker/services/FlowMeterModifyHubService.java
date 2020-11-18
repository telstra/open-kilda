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
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.nbtopology.request.MeterModifyRequest;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.nbworker.bolts.FlowHubCarrier;
import org.openkilda.wfm.topology.nbworker.fsm.FlowMeterModifyFsm;
import org.openkilda.wfm.topology.nbworker.fsm.FlowMeterModifyFsm.FlowMeterModifyEvent;
import org.openkilda.wfm.topology.nbworker.fsm.FlowMeterModifyFsm.FlowMeterModifyState;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class FlowMeterModifyHubService {
    private Map<String, FlowMeterModifyFsm> fsms = new HashMap<>();
    private boolean active = true;
    private FlowHubCarrier defaultCarrier;
    private PersistenceManager persistenceManager;
    private StateMachineBuilder<FlowMeterModifyFsm, FlowMeterModifyState, FlowMeterModifyEvent, Object> builder;

    public FlowMeterModifyHubService(PersistenceManager persistenceManager, FlowHubCarrier defaultCarrier) {
        this.persistenceManager = persistenceManager;
        this.builder = FlowMeterModifyFsm.builder();
        this.defaultCarrier = defaultCarrier;
    }

    /**
     * Handle flow meter modify request.
     */
    public void handleRequest(String key, MeterModifyRequest request, FlowHubCarrier carrier) {
        FlowMeterModifyFsm fsm =
                builder.newStateMachine(FlowMeterModifyState.INITIALIZED, carrier, key, request, persistenceManager);
        process(fsm);
    }

    /**
     * Handle response from speaker worker.
     */
    public void handleAsyncResponse(String key, Message message) {
        FlowMeterModifyFsm fsm = fsms.get(key);

        if (message instanceof InfoMessage) {
            InfoData data = ((InfoMessage) message).getData();
            if (data instanceof SwitchMeterEntries) {
                fsm.fire(FlowMeterModifyEvent.RESPONSE_RECEIVED, data);
            } else {
                log.warn("Key: {}; Unhandled message {}", key, message);
            }
        } else  if (message instanceof ErrorMessage) {
            fsm.fire(FlowMeterModifyEvent.ERROR, message);
        }

        process(fsm);
    }

    /**
     * Handle timeout event.
     */
    public void handleTaskTimeout(String key) {
        FlowMeterModifyFsm fsm = fsms.get(key);
        ErrorData errorData = new ErrorData(ErrorType.OPERATION_TIMED_OUT, "Flow meter modify failed by timeout",
                "Error in FlowMeterModifyHubService");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);
        fsm.fire(FlowMeterModifyEvent.ERROR, errorMessage);

        process(fsm);
    }

    private void process(FlowMeterModifyFsm fsm) {
        final List<FlowMeterModifyState> stopStates = Arrays.asList(
                FlowMeterModifyState.MODIFY_METERS,
                FlowMeterModifyState.FINISHED,
                FlowMeterModifyState.FINISHED_WITH_ERROR
        );

        while (!stopStates.contains(fsm.getCurrentState())) {
            fsms.put(fsm.getKey(), fsm);
            fsm.fire(FlowMeterModifyEvent.NEXT);
        }

        final List<FlowMeterModifyState> exitStates = Arrays.asList(
                FlowMeterModifyState.FINISHED,
                FlowMeterModifyState.FINISHED_WITH_ERROR
        );

        if (exitStates.contains(fsm.getCurrentState())) {
            fsms.remove(fsm.getKey());
            if (fsms.isEmpty() && !active) {
                defaultCarrier.sendInactive();
            }
        }
    }


    /**
     * Deactivates service.
     */
    public boolean deactivate() {
        active = false;
        if (fsms.isEmpty()) {
            return true;
        }
        return false;
    }

    public void activate() {
        active = true;
    }
}

