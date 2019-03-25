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

package org.openkilda.wfm.topology.switchmanager.fsm;

import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesEvent.ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesEvent.EXCESS_RULES_EXIST;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesEvent.MISSING_RULES_EXIST;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesEvent.NEXT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesEvent.RULES_INSTALLED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesEvent.RULES_RECEIVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesEvent.RULES_REMOVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesEvent.TIMEOUT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesState.CHECK_EXCESS_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesState.CHECK_MISSING_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesState.COMPUTE_INSTALL_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesState.COMPUTE_REMOVE_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesState.FINISHED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesState.FINISHED_WITH_ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesState.INITIALIZED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesState.INSTALL_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesState.RECEIVE_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesState.REMOVE_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesState.VALIDATE_RULES;

import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.BaseFlow;
import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.BatchInstallFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.BatchRemoveFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.switches.DumpRulesForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.SwitchRulesSyncRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.switches.SyncRulesResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.switchmanager.SwitchSyncRulesCarrier;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesState;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.service.CommandBuilder;
import org.openkilda.wfm.topology.switchmanager.service.ValidationService;
import org.openkilda.wfm.topology.switchmanager.service.impl.CommandBuilderImpl;
import org.openkilda.wfm.topology.switchmanager.service.impl.ValidationServiceImpl;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class SwitchSyncRulesFsm
        extends AbstractStateMachine<SwitchSyncRulesFsm, SwitchSyncRulesState, SwitchSyncRulesEvent, Object> {

    private static final String FINISHED_WITH_ERROR_METHOD_NAME = "finishedWithError";
    private static final String FINISHED_METHOD_NAME = "finished";
    private static final String ERROR_LOG_MESSAGE = "Key: {}, message: {}";

    private final String key;
    private final SwitchRulesSyncRequest request;
    private final SwitchSyncRulesCarrier carrier;
    private final PersistenceManager persistenceManager;
    private SwitchId switchId;
    private List<FlowEntry> initialFlowEntries;
    private Set<Long> presentCookies;
    private ValidateRulesResult validateRulesResult;

    private List<BaseInstallFlow> missingRules;
    private List<RemoveFlow> excessRules;

    public SwitchSyncRulesFsm(SwitchSyncRulesCarrier carrier, String key, SwitchRulesSyncRequest request,
                              PersistenceManager persistenceManager) {
        this.carrier = carrier;
        this.key = key;
        this.request = request;
        this.persistenceManager = persistenceManager;
    }

    /**
     * FSM builder.
     */
    public static StateMachineBuilder<SwitchSyncRulesFsm, SwitchSyncRulesState,
            SwitchSyncRulesEvent, Object> builder() {
        StateMachineBuilder<SwitchSyncRulesFsm, SwitchSyncRulesState, SwitchSyncRulesEvent, Object> builder =
                StateMachineBuilderFactory.create(
                        SwitchSyncRulesFsm.class,
                        SwitchSyncRulesState.class,
                        SwitchSyncRulesEvent.class,
                        Object.class,
                        SwitchSyncRulesCarrier.class,
                        String.class,
                        SwitchRulesSyncRequest.class,
                        PersistenceManager.class);

        builder.onEntry(INITIALIZED).callMethod("initialized");
        builder.externalTransition().from(INITIALIZED).to(RECEIVE_RULES).on(NEXT)
                .callMethod("receiveRules");
        builder.internalTransition().within(RECEIVE_RULES).on(RULES_RECEIVED).callMethod("receivedRules");

        builder.externalTransition().from(RECEIVE_RULES).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod("receivingRulesFailedByTimeout");
        builder.externalTransition().from(RECEIVE_RULES).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
        builder.externalTransition().from(RECEIVE_RULES).to(VALIDATE_RULES).on(NEXT)
                .callMethod("validateRules");

        builder.externalTransition().from(VALIDATE_RULES).to(CHECK_MISSING_RULES).on(NEXT)
                .callMethod("checkMissingRulesExists");
        builder.externalTransition().from(VALIDATE_RULES).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(CHECK_MISSING_RULES).to(CHECK_EXCESS_RULES).on(NEXT)
                .callMethod("checkExcessRulesExists");
        builder.externalTransition().from(CHECK_MISSING_RULES).to(COMPUTE_INSTALL_RULES).on(MISSING_RULES_EXIST)
                .callMethod("computeInstallRules");

        builder.externalTransition().from(COMPUTE_INSTALL_RULES).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
        builder.externalTransition().from(COMPUTE_INSTALL_RULES).to(INSTALL_RULES).on(NEXT)
                .callMethod("installRules");

        builder.internalTransition().within(INSTALL_RULES).on(RULES_INSTALLED).callMethod("installedRules");
        builder.externalTransition().from(INSTALL_RULES).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod("installingRulesFailedByTimeout");
        builder.externalTransition().from(INSTALL_RULES).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
        builder.externalTransition().from(INSTALL_RULES).to(CHECK_EXCESS_RULES).on(NEXT)
                .callMethod("checkMissingRulesExists");

        builder.externalTransition().from(CHECK_EXCESS_RULES).to(FINISHED).on(NEXT)
                .callMethod(FINISHED_METHOD_NAME);
        builder.externalTransition().from(CHECK_EXCESS_RULES).to(COMPUTE_REMOVE_RULES).on(EXCESS_RULES_EXIST)
                .callMethod("computeRemoveRules");


        builder.externalTransition().from(COMPUTE_REMOVE_RULES).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
        builder.externalTransition().from(COMPUTE_REMOVE_RULES).to(REMOVE_RULES).on(NEXT)
                .callMethod("removeRules");

        builder.internalTransition().within(REMOVE_RULES).on(RULES_REMOVED).callMethod("removedRules");
        builder.externalTransition().from(REMOVE_RULES).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod("removingRulesFailedByTimeout");
        builder.externalTransition().from(REMOVE_RULES).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
        builder.externalTransition().from(REMOVE_RULES).to(FINISHED).on(NEXT)
                .callMethod(FINISHED_METHOD_NAME);

        return builder;
    }

    public String getKey() {
        return key;
    }

    protected void initialized(SwitchSyncRulesState from, SwitchSyncRulesState to,
                               SwitchSyncRulesEvent event, Object context) {
        log.info("Key: {}, FSM initialized", key);
        switchId = request.getSwitchId();
    }

    protected void receiveRules(SwitchSyncRulesState from, SwitchSyncRulesState to,
                                SwitchSyncRulesEvent event, Object context) {
        log.info("Key: {}, request to get switch rules has been sent", key);
        CommandMessage dumpCommandMessage = new CommandMessage(new DumpRulesForSwitchManagerRequest(switchId),
                System.currentTimeMillis(), key);

        carrier.sendCommand(key, dumpCommandMessage);
    }

    protected void receivedRules(SwitchSyncRulesState from, SwitchSyncRulesState to,
                                 SwitchSyncRulesEvent event, Object context) {
        log.info("Key: {}, switch rules received", key);

        this.initialFlowEntries = (List<FlowEntry>) context;
        this.presentCookies = initialFlowEntries.stream()
                .map(FlowEntry::getCookie)
                .collect(Collectors.toSet());

        fire(NEXT);
    }

    protected void receivingRulesFailedByTimeout(SwitchSyncRulesState from, SwitchSyncRulesState to,
                                                 SwitchSyncRulesEvent event, Object context) {
        ErrorData errorData = new ErrorData(ErrorType.OPERATION_TIMED_OUT, "Receiving rules failed by timeout",
                "Error when receive switch rules");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);

        log.warn(ERROR_LOG_MESSAGE, key, errorData.getErrorMessage());
        carrier.endProcessing(key);
        carrier.response(key, errorMessage);
    }

    protected void validateRules(SwitchSyncRulesState from, SwitchSyncRulesState to,
                                       SwitchSyncRulesEvent event, Object context) {
        log.info("Key: {}, validate rules", key);
        try {
            ValidationService validationService = new ValidationServiceImpl(persistenceManager);
            validateRulesResult = validationService.validateRules(switchId, presentCookies);
            fire(NEXT);
        } catch (Exception e) {
            sendException(e);
        }
    }

    protected void checkMissingRulesExists(SwitchSyncRulesState from, SwitchSyncRulesState to,
                                           SwitchSyncRulesEvent event, Object context) {
        log.info("Key: {}, is missing rules exists", key);

        if (validateRulesResult.getMissingRules().isEmpty()) {
            fire(NEXT);
        } else {
            fire(MISSING_RULES_EXIST);
        }
    }

    protected void computeInstallRules(SwitchSyncRulesState from, SwitchSyncRulesState to,
                                       SwitchSyncRulesEvent event, Object context) {
        log.info("Key: {}, compute install rules", key);
        try {
            CommandBuilder commandBuilder = new CommandBuilderImpl(persistenceManager);
            missingRules = commandBuilder.buildCommandsToCreateMissingRules(
                    switchId, validateRulesResult.getMissingRules());
        } catch (Exception e) {
            sendException(e);
        }
    }

    protected void installRules(SwitchSyncRulesState from, SwitchSyncRulesState to,
                                SwitchSyncRulesEvent event, Object context) {
        log.info("Key: {}, request to install switch rules has been sent", key);
        CommandMessage installCommandMessage = new CommandMessage(
                new BatchInstallFlowForSwitchManagerRequest(switchId, missingRules), System.currentTimeMillis(), key);

        carrier.sendCommand(key, installCommandMessage);
    }

    protected void installedRules(SwitchSyncRulesState from, SwitchSyncRulesState to,
                                  SwitchSyncRulesEvent event, Object context) {
        log.info("Key: {}, switch rules installed", key);
        fire(NEXT);
    }

    protected void installingRulesFailedByTimeout(SwitchSyncRulesState from, SwitchSyncRulesState to,
                                                  SwitchSyncRulesEvent event, Object context) {
        ErrorData errorData = new ErrorData(ErrorType.OPERATION_TIMED_OUT, "Installing rules failed by timeout",
                "Error when install switch rules");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);

        log.warn(ERROR_LOG_MESSAGE, key, errorData.getErrorMessage());
        carrier.endProcessing(key);
        carrier.response(key, errorMessage);
    }

    protected void checkExcessRulesExists(SwitchSyncRulesState from, SwitchSyncRulesState to,
                                           SwitchSyncRulesEvent event, Object context) {
        log.info("Key: {}, is excess rules exists", key);

        if (request.isRemoveExcessRules() && !validateRulesResult.getExcessRules().isEmpty()) {
            fire(EXCESS_RULES_EXIST);
        } else {
            fire(NEXT);
        }
    }

    protected void computeRemoveRules(SwitchSyncRulesState from, SwitchSyncRulesState to,
                                       SwitchSyncRulesEvent event, Object context) {
        log.info("Key: {}, compute remove rules", key);
        try {
            CommandBuilder commandBuilder = new CommandBuilderImpl(persistenceManager);
            excessRules = commandBuilder.buildCommandsToRemoveExcessRules(
                    switchId, initialFlowEntries, validateRulesResult.getExcessRules());
        } catch (Exception e) {
            sendException(e);
        }
    }

    protected void removeRules(SwitchSyncRulesState from, SwitchSyncRulesState to,
                                SwitchSyncRulesEvent event, Object context) {
        log.info("Key: {}, request to remove switch rules has been sent", key);

        CommandMessage removeCommand = new CommandMessage(
                new BatchRemoveFlowForSwitchManagerRequest(switchId, excessRules), System.currentTimeMillis(), key);

        carrier.sendCommand(key, removeCommand);
    }

    protected void removedRules(SwitchSyncRulesState from, SwitchSyncRulesState to,
                                  SwitchSyncRulesEvent event, Object context) {
        log.info("Key: {}, switch rules removed", key);
        fire(NEXT);
    }

    protected void removingRulesFailedByTimeout(SwitchSyncRulesState from, SwitchSyncRulesState to,
                                                  SwitchSyncRulesEvent event, Object context) {
        ErrorData errorData = new ErrorData(ErrorType.OPERATION_TIMED_OUT, "Removing rules failed by timeout",
                "Error when remove switch rules");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);

        log.warn(ERROR_LOG_MESSAGE, key, errorData.getErrorMessage());
        carrier.endProcessing(key);
        carrier.response(key, errorMessage);
    }

    protected void finished(SwitchSyncRulesState from, SwitchSyncRulesState to,
                            SwitchSyncRulesEvent event, Object context) {
        SyncRulesResponse response = new SyncRulesResponse(validateRulesResult.getMissingRules(),
                validateRulesResult.getProperRules(),
                validateRulesResult.getExcessRules(),
                mapBaseFlowsToCookies(missingRules),
                mapBaseFlowsToCookies(excessRules));
        InfoMessage message = new InfoMessage(response, System.currentTimeMillis(), key);
        carrier.endProcessing(key);
        carrier.response(key, message);
    }

    protected void finishedWithError(SwitchSyncRulesState from, SwitchSyncRulesState to,
                                     SwitchSyncRulesEvent event, Object context) {
        ErrorMessage message = (ErrorMessage) context;
        log.error(ERROR_LOG_MESSAGE, key, message.getData().getErrorMessage());
        carrier.endProcessing(key);
        carrier.response(key, message);
    }

    private void sendException(Exception e) {
        ErrorData errorData = new ErrorData(ErrorType.INTERNAL_ERROR, e.getMessage(),
                "Error in SwitchSyncRulesFsm");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);
        fire(ERROR, errorMessage);
    }

    private List<Long> mapBaseFlowsToCookies(List<? extends BaseFlow> flows) {
        if (flows == null) {
            return Collections.emptyList();
        }

        return flows.stream().map(BaseFlow::getCookie).collect(Collectors.toList());
    }

    public enum SwitchSyncRulesState {
        INITIALIZED,
        RECEIVE_RULES,
        CHECK_MISSING_RULES,
        COMPUTE_INSTALL_RULES,
        INSTALL_RULES,
        CHECK_EXCESS_RULES,
        COMPUTE_REMOVE_RULES,
        REMOVE_RULES,
        VALIDATE_RULES,
        FINISHED_WITH_ERROR,
        FINISHED
    }

    public enum SwitchSyncRulesEvent {
        NEXT,
        MISSING_RULES_EXIST,
        EXCESS_RULES_EXIST,
        RULES_RECEIVED,
        RULES_INSTALLED,
        RULES_REMOVED,
        TIMEOUT,
        ERROR,
        FINISH
    }
}
