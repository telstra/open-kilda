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

import org.openkilda.messaging.command.switches.SwitchRulesSyncRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.switchmanager.SwitchSyncRulesCarrier;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesState;
import org.openkilda.wfm.topology.switchmanager.service.SyncRulesService;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class SyncRulesServiceImpl implements SyncRulesService {

    private Map<String, SwitchSyncRulesFsm> fsms = new HashMap<>();

    private PersistenceManager persistenceManager;
    private SwitchSyncRulesCarrier carrier;
    private StateMachineBuilder<SwitchSyncRulesFsm, SwitchSyncRulesState, SwitchSyncRulesEvent, Object> builder;

    public SyncRulesServiceImpl(SwitchSyncRulesCarrier carrier, PersistenceManager persistenceManager) {
        this.carrier = carrier;
        this.persistenceManager = persistenceManager;
        this.builder = SwitchSyncRulesFsm.builder();
    }

    @Override
    public void handleSyncRulesRequest(String key, SwitchRulesSyncRequest request) {
        SwitchSyncRulesFsm fsm =
                builder.newStateMachine(SwitchSyncRulesState.INITIALIZED, carrier, key, request, persistenceManager);

        process(fsm);
    }

    @Override
    public void handleFlowEntriesResponse(String key, SwitchFlowEntries data) {
        SwitchSyncRulesFsm fsm = fsms.get(key);
        if (fsm == null) {
            sendFsmNotFound(key);
            return;
        }

        Set<Long> presentCookies = data.getFlowEntries().stream()
                .map(FlowEntry::getCookie)
                .collect(Collectors.toSet());

        fsm.fire(SwitchSyncRulesEvent.RULES_RECEIVED, presentCookies);
        process(fsm);
    }

    @Override
    public void handleInstallRulesResponse(String key) {
        SwitchSyncRulesFsm fsm = fsms.get(key);
        if (fsm == null) {
            sendFsmNotFound(key);
            return;
        }

        fsm.fire(SwitchSyncRulesEvent.RULES_INSTALLED);
        process(fsm);
    }

    @Override
    public void handleTaskTimeout(String key) {
        SwitchSyncRulesFsm fsm = fsms.get(key);
        if (fsm == null) {
            sendFsmNotFound(key);
            return;
        }

        fsm.fire(SwitchSyncRulesEvent.TIMEOUT);
    }

    @Override
    public void handleTaskError(String key, ErrorMessage message) {
        SwitchSyncRulesFsm fsm = fsms.get(key);
        if (fsm == null) {
            sendFsmNotFound(key);
            return;
        }

        fsm.fire(SwitchSyncRulesEvent.ERROR, message);
    }

    private void sendFsmNotFound(String key) {
        String message = String.format("FSM with key %s not found", key);
        log.error(message);
        ErrorData errorData = new ErrorData(ErrorType.INTERNAL_ERROR, message,
                "FSM not found");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);
        carrier.response(key, errorMessage);
    }

    void process(SwitchSyncRulesFsm fsm) {
        final List<SwitchSyncRulesState> stopStates = Arrays.asList(
                SwitchSyncRulesState.RECEIVE_RULES,
                SwitchSyncRulesState.INSTALL_RULES,
                SwitchSyncRulesState.FINISHED,
                SwitchSyncRulesState.FINISHED_WITH_ERROR
        );

        while (!stopStates.contains(fsm.getCurrentState())) {
            fsms.put(fsm.getKey(), fsm);
            fsm.fire(SwitchSyncRulesEvent.NEXT);
        }

        final List<SwitchSyncRulesState> exitStates = Arrays.asList(
                SwitchSyncRulesState.FINISHED,
                SwitchSyncRulesState.FINISHED_WITH_ERROR
        );

        if (exitStates.contains(fsm.getCurrentState())) {
            fsms.remove(fsm.getKey());
        }
    }
}
