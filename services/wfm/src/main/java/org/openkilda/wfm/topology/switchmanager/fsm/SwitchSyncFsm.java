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

import static java.util.Collections.emptyList;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.METERS_REMOVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.NEXT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.RULES_INSTALLED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.RULES_REMOVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.TIMEOUT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMMANDS_SEND;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_INSTALL_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_REMOVE_METERS;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_REMOVE_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.FINISHED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.FINISHED_WITH_ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.INITIALIZED;

import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.BatchInstallFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.BatchRemoveFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.switches.BatchRemoveMeters;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.MetersSyncEntry;
import org.openkilda.messaging.info.switches.RulesSyncEntry;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.topology.switchmanager.SwitchManagerCarrier;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidationResult;
import org.openkilda.wfm.topology.switchmanager.service.CommandBuilder;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class SwitchSyncFsm extends AbstractBaseFsm<SwitchSyncFsm, SwitchSyncState, SwitchSyncEvent, Object> {

    private static final String FINISHED_WITH_ERROR_METHOD_NAME = "finishedWithError";
    private static final String FINISHED_METHOD_NAME = "finished";
    private static final String ERROR_LOG_MESSAGE = "Key: {}, message: {}";

    private final String key;
    private final SwitchValidateRequest request;
    private final SwitchManagerCarrier carrier;
    private final CommandBuilder commandBuilder;
    private SwitchId switchId;
    private ValidationResult validationResult;

    private List<BaseInstallFlow> missingRules = emptyList();
    private List<RemoveFlow> excessRules = emptyList();
    private List<Long> excessMeters = emptyList();

    public SwitchSyncFsm(SwitchManagerCarrier carrier, String key, CommandBuilder commandBuilder,
                         SwitchValidateRequest request, ValidationResult validationResult) {
        this.carrier = carrier;
        this.key = key;
        this.commandBuilder = commandBuilder;
        this.request = request;
        this.validationResult = validationResult;
    }

    /**
     * FSM builder.
     */
    public static StateMachineBuilder<SwitchSyncFsm, SwitchSyncState,
            SwitchSyncEvent, Object> builder() {
        StateMachineBuilder<SwitchSyncFsm, SwitchSyncState, SwitchSyncEvent, Object> builder =
                StateMachineBuilderFactory.create(
                        SwitchSyncFsm.class,
                        SwitchSyncState.class,
                        SwitchSyncEvent.class,
                        Object.class,
                        SwitchManagerCarrier.class,
                        String.class,
                        CommandBuilder.class,
                        SwitchValidateRequest.class,
                        ValidationResult.class);

        builder.onEntry(INITIALIZED).callMethod("initialized");
        builder.externalTransition().from(INITIALIZED).to(COMPUTE_INSTALL_RULES).on(NEXT)
                .callMethod("computeInstallRules");
        builder.externalTransition().from(COMPUTE_INSTALL_RULES).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(COMPUTE_INSTALL_RULES).to(COMPUTE_REMOVE_RULES).on(NEXT)
                .callMethod("computeRemoveRules");
        builder.externalTransition().from(COMPUTE_REMOVE_RULES).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(COMPUTE_REMOVE_RULES).to(COMPUTE_REMOVE_METERS).on(NEXT)
                .callMethod("computeRemoveMeters");
        builder.externalTransition().from(COMPUTE_REMOVE_METERS).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(COMPUTE_REMOVE_METERS).to(COMMANDS_SEND).on(NEXT)
                .callMethod("sendCommands");
        builder.internalTransition().within(COMMANDS_SEND).on(RULES_INSTALLED).callMethod("rulesInstalled");
        builder.internalTransition().within(COMMANDS_SEND).on(RULES_REMOVED).callMethod("rulesRemoved");
        builder.internalTransition().within(COMMANDS_SEND).on(METERS_REMOVED).callMethod("metersRemoved");

        builder.externalTransition().from(COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod("commandsProcessingFailedByTimeout");
        builder.externalTransition().from(COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
        builder.externalTransition().from(COMMANDS_SEND).to(FINISHED).on(NEXT)
                .callMethod(FINISHED_METHOD_NAME);

        return builder;
    }

    public String getKey() {
        return key;
    }

    protected void initialized(SwitchSyncState from, SwitchSyncState to,
                               SwitchSyncEvent event, Object context) {
        log.info("Key: {}, sync FSM initialized", key);
        switchId = request.getSwitchId();
    }

    protected void computeInstallRules(SwitchSyncState from, SwitchSyncState to,
                                       SwitchSyncEvent event, Object context) {
        ValidateRulesResult validateRulesResult = validationResult.getValidateRulesResult();

        if (!validateRulesResult.getMissingRules().isEmpty()) {
            log.info("Key: {}, compute install rules", key);
            try {
                missingRules = commandBuilder.buildCommandsToSyncMissingRules(
                        switchId, validationResult.getValidateRulesResult().getMissingRules());
            } catch (Exception e) {
                sendException(e);
            }
        }
    }

    protected void computeRemoveRules(SwitchSyncState from, SwitchSyncState to,
                                      SwitchSyncEvent event, Object context) {
        ValidateRulesResult validateRulesResult = validationResult.getValidateRulesResult();

        if (request.isRemoveExcess() && !validateRulesResult.getExcessRules().isEmpty()) {
            log.info("Key: {}, compute remove rules", key);
            try {
                excessRules = commandBuilder.buildCommandsToRemoveExcessRules(
                        switchId, validationResult.getFlowEntries(), validateRulesResult.getExcessRules());
            } catch (Exception e) {
                sendException(e);
            }
        }
    }

    protected void computeRemoveMeters(SwitchSyncState from, SwitchSyncState to,
                                       SwitchSyncEvent event, Object context) {
        if (request.isRemoveExcess() && validationResult.isProcessMeters()) {
            ValidateMetersResult validateMetersResult = validationResult.getValidateMetersResult();

            if (!validateMetersResult.getExcessMeters().isEmpty()) {
                log.info("Key: {}, compute remove meters", key);
                try {
                    excessMeters = validateMetersResult.getExcessMeters().stream()
                            .map(MeterInfoEntry::getMeterId)
                            .collect(Collectors.toList());
                } catch (Exception e) {
                    sendException(e);
                }
            }
        }
    }

    protected void sendCommands(SwitchSyncState from, SwitchSyncState to,
                                SwitchSyncEvent event, Object context) {
        if (checkAllDone()) {
            log.info("Key: {}, nothing to do", key);
            fire(NEXT);
        }

        if (!missingRules.isEmpty()) {
            log.info("Key: {}, request to install switch rules has been sent", key);
            carrier.sendCommand(key, new CommandMessage(
                    new BatchInstallFlowForSwitchManagerRequest(switchId, missingRules),
                    System.currentTimeMillis(), key));
        }

        if (!excessRules.isEmpty()) {
            log.info("Key: {}, request to remove switch rules has been sent", key);
            carrier.sendCommand(key, new CommandMessage(
                    new BatchRemoveFlowForSwitchManagerRequest(switchId, excessRules),
                    System.currentTimeMillis(), key));
        }

        if (!excessMeters.isEmpty()) {
            log.info("Key: {}, request to remove switch meters has been sent", key);
            carrier.sendCommand(key, new CommandMessage(
                    new BatchRemoveMeters(switchId, excessMeters), System.currentTimeMillis(), key));
        }
    }

    private boolean checkAllDone() {
        return missingRules.isEmpty() && excessRules.isEmpty() && excessMeters.isEmpty();
    }

    protected void rulesInstalled(SwitchSyncState from, SwitchSyncState to,
                                  SwitchSyncEvent event, Object context) {
        log.info("Key: {}, switch rules installed", key);
        missingRules = emptyList();
        continueIfAllDone();
    }

    protected void rulesRemoved(SwitchSyncState from, SwitchSyncState to,
                                SwitchSyncEvent event, Object context) {
        log.info("Key: {}, switch rules removed", key);
        excessRules = emptyList();
        continueIfAllDone();
    }

    protected void metersRemoved(SwitchSyncState from, SwitchSyncState to,
                                 SwitchSyncEvent event, Object context) {
        log.info("Key: {}, switch meters removed", key);
        excessMeters = emptyList();
        continueIfAllDone();
    }

    private void continueIfAllDone() {
        if (checkAllDone()) {
            fire(NEXT);
        }
    }

    protected void commandsProcessingFailedByTimeout(SwitchSyncState from, SwitchSyncState to,
                                                  SwitchSyncEvent event, Object context) {
        ErrorData errorData = new ErrorData(ErrorType.OPERATION_TIMED_OUT, "Commands processing failed by timeout",
                "Error when processing switch commands");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);

        log.warn(ERROR_LOG_MESSAGE, key, errorData.getErrorMessage());
        carrier.endProcessing(key);
        carrier.response(key, errorMessage);
    }

    protected void finished(SwitchSyncState from, SwitchSyncState to,
                            SwitchSyncEvent event, Object context) {
        ValidateRulesResult validateRulesResult = validationResult.getValidateRulesResult();
        ValidateMetersResult validateMetersResult = validationResult.getValidateMetersResult();

        RulesSyncEntry rulesEntry = new RulesSyncEntry(validateRulesResult.getMissingRules(),
                validateRulesResult.getProperRules(),
                validateRulesResult.getExcessRules(),
                validateRulesResult.getMissingRules(),
                request.isRemoveExcess() ? validateRulesResult.getExcessRules() : emptyList());

        MetersSyncEntry metersEntry = null;
        if (validationResult.isProcessMeters()) {
            metersEntry = new MetersSyncEntry(validateMetersResult.getMissingMeters(),
                    validateMetersResult.getMisconfiguredMeters(),
                    validateMetersResult.getProperMeters(),
                    validateMetersResult.getExcessMeters(),
                    validateMetersResult.getMissingMeters(),
                    request.isRemoveExcess() ? validateMetersResult.getExcessMeters() : emptyList());
        }

        SwitchSyncResponse response = new SwitchSyncResponse(rulesEntry, metersEntry);
        InfoMessage message = new InfoMessage(response, System.currentTimeMillis(), key);
        carrier.endProcessing(key);
        carrier.response(key, message);
    }

    protected void finishedWithError(SwitchSyncState from, SwitchSyncState to,
                                     SwitchSyncEvent event, Object context) {
        ErrorMessage message = (ErrorMessage) context;
        log.error(ERROR_LOG_MESSAGE, key, message.getData().getErrorMessage());
        carrier.endProcessing(key);
        carrier.response(key, message);
    }

    private void sendException(Exception e) {
        ErrorData errorData = new ErrorData(ErrorType.INTERNAL_ERROR, e.getMessage(),
                "Error in SwitchSyncFsm");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);
        fire(ERROR, errorMessage);
    }

    public enum SwitchSyncState {
        INITIALIZED,
        COMPUTE_INSTALL_RULES,
        COMPUTE_REMOVE_RULES,
        COMPUTE_REMOVE_METERS,
        COMMANDS_SEND,
        FINISHED_WITH_ERROR,
        FINISHED
    }

    public enum SwitchSyncEvent {
        NEXT,
        RULES_INSTALLED,
        RULES_REMOVED,
        METERS_REMOVED,
        TIMEOUT,
        ERROR,
        FINISH
    }
}
