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
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesEvent.FINISH;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesEvent.NEXT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesEvent.RULES_INSTALLED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesEvent.RULES_RECEIVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesEvent.TIMEOUT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesState.COMPUTE_INSTALL_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesState.FINISHED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesState.FINISHED_WITH_ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesState.INITIALIZED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesState.INSTALL_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesState.RECEIVE_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncRulesFsm.SwitchSyncRulesState.VALIDATE_RULES;

import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.BatchInstallForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DumpRulesForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.SwitchRulesSyncRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
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

import java.util.List;
import java.util.Set;

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
    private Set<Long> presentCookies;
    private ValidateRulesResult validateRulesResult;
    private List<BaseInstallFlow> missingRules;

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

        builder.externalTransition().from(VALIDATE_RULES).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
        builder.externalTransition().from(VALIDATE_RULES).to(FINISHED).on(FINISH)
                .callMethod(FINISHED_METHOD_NAME);
        builder.externalTransition().from(VALIDATE_RULES).to(COMPUTE_INSTALL_RULES).on(NEXT)
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
        builder.externalTransition().from(INSTALL_RULES).to(FINISHED).on(NEXT)
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
        this.presentCookies = (Set<Long>) context;
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
            validateRulesResult = validationService.validate(switchId, presentCookies);

            if (validateRulesResult.getMissingRules().isEmpty()) {
                fire(FINISH);
            }
        } catch (Exception e) {
            sendException(e);
        }
    }

    protected void computeInstallRules(SwitchSyncRulesState from, SwitchSyncRulesState to,
                                       SwitchSyncRulesEvent event, Object context) {
        log.info("Key: {}, compute install rules", key);
        try {
            CommandBuilder commandBuilder = new CommandBuilderImpl(persistenceManager);
            missingRules = commandBuilder.buildCommandsToSyncRules(switchId, validateRulesResult.getMissingRules());
        } catch (Exception e) {
            sendException(e);
        }
    }

    protected void installRules(SwitchSyncRulesState from, SwitchSyncRulesState to,
                                SwitchSyncRulesEvent event, Object context) {
        log.info("Key: {}, request to install switch rules has been sent", key);
        CommandMessage installCommandMessage = new CommandMessage(
                new BatchInstallForSwitchManagerRequest(switchId, missingRules), System.currentTimeMillis(), key);

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

    protected void finished(SwitchSyncRulesState from, SwitchSyncRulesState to,
                            SwitchSyncRulesEvent event, Object context) {
        SyncRulesResponse response = new SyncRulesResponse(validateRulesResult.getMissingRules(),
                validateRulesResult.getProperRules(),
                validateRulesResult.getExcessRules(),
                validateRulesResult.getMissingRules());
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

    public enum SwitchSyncRulesState {
        INITIALIZED,
        RECEIVE_RULES,
        COMPUTE_INSTALL_RULES,
        INSTALL_RULES,
        VALIDATE_RULES,
        FINISHED_WITH_ERROR,
        FINISHED
    }

    public enum SwitchSyncRulesEvent {
        NEXT,
        RULES_RECEIVED,
        RULES_INSTALLED,
        TIMEOUT,
        ERROR,
        FINISH
    }
}
