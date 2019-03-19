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

package org.openkilda.wfm.topology.switchmanager.service.impl;

import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.switchmanager.SwitchValidationCarrier;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState;
import org.openkilda.wfm.topology.switchmanager.service.SwitchValidateService;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class SwitchValidateServiceImpl implements SwitchValidateService {

    private Map<String, SwitchValidateFsm> fsms = new HashMap<>();

    private PersistenceManager persistenceManager;
    private SwitchValidationCarrier carrier;
    private StateMachineBuilder<SwitchValidateFsm, SwitchValidateState, SwitchValidateEvent, Object> builder;

    public SwitchValidateServiceImpl(SwitchValidationCarrier carrier, PersistenceManager persistenceManager) {
        this.carrier = carrier;
        this.builder = SwitchValidateFsm.builder();
        this.persistenceManager = persistenceManager;
    }

    @Override
    public void handleSwitchValidateRequest(String key, SwitchValidateRequest request) {
        SwitchValidateFsm fsm =
                builder.newStateMachine(SwitchValidateState.INITIALIZED, carrier, key, request, persistenceManager);

        process(fsm);
    }

    @Override
    public void handleFlowEntriesResponse(String key, SwitchFlowEntries data) {
        SwitchValidateFsm fsm = fsms.get(key);
        if (fsm == null) {
            sendFsmNotFound(key);
            return;
        }

        Set<Long> presentCookies = data.getFlowEntries().stream()
                .map(FlowEntry::getCookie)
                .collect(Collectors.toSet());

        fsm.fire(SwitchValidateEvent.RULES_RECEIVED, presentCookies);
        process(fsm);
    }

    @Override
    public void handleMeterEntriesResponse(String key, SwitchMeterEntries data) {
        SwitchValidateFsm fsm = fsms.get(key);
        if (fsm == null) {
            sendFsmNotFound(key);
            return;
        }

        fsm.fire(SwitchValidateEvent.METERS_RECEIVED, data.getMeterEntries());
        process(fsm);
    }

    @Override
    public void handleTaskTimeout(String key) {
        SwitchValidateFsm fsm = fsms.get(key);
        if (fsm == null) {
            sendFsmNotFound(key);
            return;
        }

        fsm.fire(SwitchValidateEvent.TIMEOUT);
    }

    @Override
    public void handleTaskError(String key, ErrorMessage message) {
        SwitchValidateFsm fsm = fsms.get(key);
        if (fsm == null) {
            sendFsmNotFound(key);
            return;
        }

        fsm.fire(SwitchValidateEvent.ERROR, message);
    }

    private void sendFsmNotFound(String key) {
        String message = String.format("Switch validate FSM with key %s not found", key);
        log.error(message);
        ErrorData errorData = new ErrorData(ErrorType.INTERNAL_ERROR, message,
                "FSM not found");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);
        carrier.response(key, errorMessage);
    }

    void process(SwitchValidateFsm fsm) {
        final List<SwitchValidateState> stopStates = Arrays.asList(
                SwitchValidateState.RECEIVE_RULES,
                SwitchValidateState.RECEIVE_METERS,
                SwitchValidateState.FINISHED,
                SwitchValidateState.FINISHED_WITH_ERROR
        );

        while (!stopStates.contains(fsm.getCurrentState())) {
            fsms.put(fsm.getKey(), fsm);
            fsm.fire(SwitchValidateEvent.NEXT);
        }

        final List<SwitchValidateState> exitStates = Arrays.asList(
                SwitchValidateState.FINISHED,
                SwitchValidateState.FINISHED_WITH_ERROR
        );

        if (exitStates.contains(fsm.getCurrentState())) {
            fsms.remove(fsm.getKey());
        }
    }
}
