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
import static org.apache.storm.shade.org.apache.commons.collections.ListUtils.union;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.EXCESS_RULES_REMOVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.METERS_REMOVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.MISCONFIGURED_RULES_REINSTALLED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.MISSING_RULES_INSTALLED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.NEXT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.RULES_SYNCHRONIZED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.TIMEOUT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_EXCESS_METERS;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_EXCESS_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_MISCONFIGURED_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_MISSING_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.FINISHED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.FINISHED_WITH_ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.INITIALIZED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.METERS_COMMANDS_SEND;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.RULES_COMMANDS_SEND;

import org.openkilda.messaging.command.flow.BaseFlow;
import org.openkilda.messaging.command.flow.InstallFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.ReinstallDefaultFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.flow.RemoveFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DeleterMeterForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.rule.SwitchSyncErrorData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowReinstallResponse;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.MetersSyncEntry;
import org.openkilda.messaging.info.switches.RulesSyncEntry;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidationResult;
import org.openkilda.wfm.topology.switchmanager.service.CommandBuilder;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.util.ArrayList;
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
    private final SwitchId switchId;
    private final ValidationResult validationResult;
    private List<Long> installedRulesCookies;
    private List<Long> removedFlowRulesCookies;
    private List<Long> removedDefaultRulesCookies = new ArrayList<>();

    private List<BaseFlow> missingRules = emptyList();
    private List<ReinstallDefaultFlowForSwitchManagerRequest> misconfiguredRules = emptyList();
    private List<RemoveFlow> excessRules = emptyList();
    private List<Long> excessMeters = emptyList();

    private int missingRulesPendingResponsesCount = 0;
    private int excessRulesPendingResponsesCount = 0;
    private int reinstallDefaultRulesPendingResponsesCount = 0;
    private int excessMetersPendingResponsesCount = 0;

    public SwitchSyncFsm(SwitchManagerCarrier carrier, String key, CommandBuilder commandBuilder,
                         SwitchValidateRequest request, ValidationResult validationResult) {
        this.carrier = carrier;
        this.key = key;
        this.commandBuilder = commandBuilder;
        this.request = request;
        this.validationResult = validationResult;
        this.switchId = request.getSwitchId();
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
        builder.externalTransition().from(INITIALIZED).to(COMPUTE_MISSING_RULES).on(NEXT)
                .callMethod("computeMissingRules");
        builder.externalTransition().from(COMPUTE_MISSING_RULES).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(COMPUTE_MISSING_RULES).to(COMPUTE_MISCONFIGURED_RULES).on(NEXT)
                .callMethod("computeMisconfiguredRules");
        builder.externalTransition().from(COMPUTE_MISCONFIGURED_RULES).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(COMPUTE_MISCONFIGURED_RULES).to(COMPUTE_EXCESS_RULES).on(NEXT)
                .callMethod("computeExcessRules");
        builder.externalTransition().from(COMPUTE_EXCESS_RULES).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(COMPUTE_EXCESS_RULES).to(COMPUTE_EXCESS_METERS).on(NEXT)
                .callMethod("computeExcessMeters");
        builder.externalTransition().from(COMPUTE_EXCESS_METERS).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(COMPUTE_EXCESS_METERS).to(RULES_COMMANDS_SEND).on(NEXT)
                .callMethod("sendRulesCommands");
        builder.internalTransition().within(RULES_COMMANDS_SEND).on(MISSING_RULES_INSTALLED)
                .callMethod("missingRuleInstalled");
        builder.internalTransition().within(RULES_COMMANDS_SEND).on(EXCESS_RULES_REMOVED)
                .callMethod("excessRuleRemoved");
        builder.internalTransition().within(RULES_COMMANDS_SEND).on(MISCONFIGURED_RULES_REINSTALLED)
                .callMethod("misconfiguredRuleReinstalled");

        builder.externalTransition().from(RULES_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod("commandsProcessingFailedByTimeout");
        builder.externalTransition().from(RULES_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(RULES_COMMANDS_SEND).to(METERS_COMMANDS_SEND).on(RULES_SYNCHRONIZED)
                .callMethod("sendMetersCommands");
        builder.internalTransition().within(METERS_COMMANDS_SEND).on(METERS_REMOVED).callMethod("meterRemoved");

        builder.externalTransition().from(METERS_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod("commandsProcessingFailedByTimeout");
        builder.externalTransition().from(METERS_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
        builder.externalTransition().from(METERS_COMMANDS_SEND).to(FINISHED).on(NEXT)
                .callMethod(FINISHED_METHOD_NAME);

        return builder;
    }

    public String getKey() {
        return key;
    }

    protected void initialized(SwitchSyncState from, SwitchSyncState to,
                               SwitchSyncEvent event, Object context) {
        log.info("The switch sync process for {} has been started (key={})", switchId, key);
    }

    protected void computeMissingRules(SwitchSyncState from, SwitchSyncState to,
                                       SwitchSyncEvent event, Object context) {
        installedRulesCookies = new ArrayList<>(validationResult.getValidateRulesResult().getMissingRules());

        if (!installedRulesCookies.isEmpty()) {
            log.info("Compute install rules (switch={}, key={})", switchId, key);
            try {
                missingRules = commandBuilder.buildCommandsToSyncMissingRules(switchId, installedRulesCookies);
            } catch (Exception e) {
                sendException(e);
            }
        }
    }

    protected void computeMisconfiguredRules(SwitchSyncState from, SwitchSyncState to,
                                             SwitchSyncEvent event, Object context) {
        List<Long> reinstallRules = getReinstallDefaultRules();
        if (!reinstallRules.isEmpty()) {
            log.info("Compute reinstall default rules (switch={}, key={})", switchId, key);
            try {
                misconfiguredRules = commandBuilder.buildCommandsToReinstallRules(switchId, reinstallRules);
            } catch (Exception e) {
                sendException(e);
            }
        }
    }

    protected void computeExcessRules(SwitchSyncState from, SwitchSyncState to,
                                      SwitchSyncEvent event, Object context) {
        removedFlowRulesCookies = new ArrayList<>(validationResult.getValidateRulesResult().getExcessRules());

        if (request.isRemoveExcess() && !removedFlowRulesCookies.isEmpty()) {
            log.info("Compute remove rules (switch={}, key={})", switchId, key);
            try {
                excessRules = commandBuilder.buildCommandsToRemoveExcessRules(
                        switchId, validationResult.getFlowEntries(), removedFlowRulesCookies);
            } catch (Exception e) {
                sendException(e);
            }
        }
    }

    protected void computeExcessMeters(SwitchSyncState from, SwitchSyncState to,
                                       SwitchSyncEvent event, Object context) {
        if (request.isRemoveExcess() && validationResult.isProcessMeters()) {
            ValidateMetersResult validateMetersResult = validationResult.getValidateMetersResult();

            if (!validateMetersResult.getExcessMeters().isEmpty()) {
                log.info("Compute remove meters (switch={}, key={})", switchId, key);
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

    protected void sendRulesCommands(SwitchSyncState from, SwitchSyncState to,
                                     SwitchSyncEvent event, Object context) {
        if (missingRules.isEmpty() && excessRules.isEmpty()) {
            log.info("Nothing to do with rules (switch={}, key={})", switchId, key);
            fire(NEXT);
        }

        if (!missingRules.isEmpty()) {
            log.info("Request to install switch rules has been sent (switch={}, key={})", switchId, key);
            missingRulesPendingResponsesCount = missingRules.size();

            for (BaseFlow command : missingRules) {
                carrier.sendCommandToSpeaker(key, new InstallFlowForSwitchManagerRequest(command));
            }
        }

        if (!excessRules.isEmpty()) {
            log.info("Request to remove switch rules has been sent (switch={}, key={})", switchId, key);
            excessRulesPendingResponsesCount = excessRules.size();

            for (RemoveFlow command : excessRules) {
                carrier.sendCommandToSpeaker(key, new RemoveFlowForSwitchManagerRequest(switchId, command));
            }
        }

        if (!misconfiguredRules.isEmpty()) {
            log.info("Request to reinstall default switch rules has been sent (switch={}, key={})", switchId, key);
            reinstallDefaultRulesPendingResponsesCount = misconfiguredRules.size();

            for (ReinstallDefaultFlowForSwitchManagerRequest command : misconfiguredRules) {
                carrier.sendCommandToSpeaker(key, command);
            }
        }

        continueIfRulesSynchronized();
    }

    private List<Long> getReinstallDefaultRules() {
        ValidateRulesResult validateRulesResult = validationResult.getValidateRulesResult();
        return validateRulesResult.getMisconfiguredRules().stream()
                .filter(Cookie::isDefaultRule)
                .collect(Collectors.toList());
    }

    protected void sendMetersCommands(SwitchSyncState from, SwitchSyncState to,
                                      SwitchSyncEvent event, Object context) {
        if (excessMeters.isEmpty()) {
            log.info("Nothing to do with meters (switch={}, key={})", switchId, key);
            fire(NEXT);
        } else {
            log.info("Request to remove switch meters has been sent (switch={}, key={})", switchId, key);
            excessMetersPendingResponsesCount = excessMeters.size();

            for (Long meterId : excessMeters) {
                carrier.sendCommandToSpeaker(key, new DeleterMeterForSwitchManagerRequest(switchId, meterId));
            }
        }
    }

    protected void missingRuleInstalled(SwitchSyncState from, SwitchSyncState to,
                                        SwitchSyncEvent event, Object context) {
        log.info("Switch rule installed (switch={}, key={})", switchId, key);
        missingRulesPendingResponsesCount--;
        continueIfRulesSynchronized();
    }

    protected void excessRuleRemoved(SwitchSyncState from, SwitchSyncState to,
                                     SwitchSyncEvent event, Object context) {
        log.info("Switch rule removed (switch={}, key={})", switchId, key);
        excessRulesPendingResponsesCount--;
        continueIfRulesSynchronized();
    }

    protected void misconfiguredRuleReinstalled(SwitchSyncState from, SwitchSyncState to,
                                                SwitchSyncEvent event, Object context) {
        log.info("Default switch rule reinstalled (switch={}, key={})", switchId, key);
        FlowReinstallResponse response = (FlowReinstallResponse) context;

        Long removedRule = response.getRemovedRule();
        if (removedRule != null && !removedDefaultRulesCookies.contains(removedRule)) {
            removedDefaultRulesCookies.add(removedRule);
        }

        Long installedRule = response.getInstalledRule();
        if (installedRule != null && !installedRulesCookies.contains(installedRule)) {
            installedRulesCookies.add(installedRule);
        }

        reinstallDefaultRulesPendingResponsesCount--;
        continueIfRulesSynchronized();
    }

    protected void meterRemoved(SwitchSyncState from, SwitchSyncState to,
                                SwitchSyncEvent event, Object context) {
        log.info("Switch meter removed (switch={}, key={})", switchId, key);
        excessMetersPendingResponsesCount--;
        continueIfMetersCommandsDone();
    }

    private void continueIfRulesSynchronized() {
        if (missingRulesPendingResponsesCount == 0
                && excessRulesPendingResponsesCount == 0
                && reinstallDefaultRulesPendingResponsesCount == 0) {
            fire(RULES_SYNCHRONIZED);
        }
    }

    private void continueIfMetersCommandsDone() {
        if (excessMetersPendingResponsesCount == 0) {
            fire(NEXT);
        }
    }

    protected void commandsProcessingFailedByTimeout(SwitchSyncState from, SwitchSyncState to,
                                                     SwitchSyncEvent event, Object context) {
        ErrorData errorData = new ErrorData(ErrorType.OPERATION_TIMED_OUT, "Commands processing failed by timeout",
                "Error when processing switch commands");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);

        log.warn(ERROR_LOG_MESSAGE, key, errorData.getErrorMessage());
        carrier.response(key, errorMessage);
    }

    protected void finished(SwitchSyncState from, SwitchSyncState to,
                            SwitchSyncEvent event, Object context) {
        ValidateRulesResult validateRulesResult = validationResult.getValidateRulesResult();
        ValidateMetersResult validateMetersResult = validationResult.getValidateMetersResult();

        RulesSyncEntry rulesEntry = new RulesSyncEntry(validateRulesResult.getMissingRules(),
                validateRulesResult.getMisconfiguredRules(),
                validateRulesResult.getProperRules(),
                validateRulesResult.getExcessRules(),
                installedRulesCookies,
                request.isRemoveExcess() ? union(removedFlowRulesCookies, removedDefaultRulesCookies)
                        : removedDefaultRulesCookies);

        MetersSyncEntry metersEntry = null;
        if (validationResult.isProcessMeters()) {
            metersEntry = new MetersSyncEntry(validateMetersResult.getMissingMeters(),
                    validateMetersResult.getMisconfiguredMeters(),
                    validateMetersResult.getProperMeters(),
                    validateMetersResult.getExcessMeters(),
                    validateMetersResult.getMissingMeters(),
                    request.isRemoveExcess() ? validateMetersResult.getExcessMeters() : emptyList());
        }

        SwitchSyncResponse response = new SwitchSyncResponse(switchId, rulesEntry, metersEntry);
        InfoMessage message = new InfoMessage(response, System.currentTimeMillis(), key);

        carrier.cancelTimeoutCallback(key);
        carrier.response(key, message);
    }

    protected void finishedWithError(SwitchSyncState from, SwitchSyncState to,
                                     SwitchSyncEvent event, Object context) {
        ErrorMessage sourceError = (ErrorMessage) context;
        ErrorMessage message = new ErrorMessage(sourceError.getData(), System.currentTimeMillis(), key);

        log.error(ERROR_LOG_MESSAGE, key, message.getData().getErrorMessage());

        carrier.cancelTimeoutCallback(key);
        carrier.response(key, message);
    }

    private void sendException(Exception e) {
        ErrorData errorData = new SwitchSyncErrorData(switchId, ErrorType.INTERNAL_ERROR, e.getMessage(),
                "Error in SwitchSyncFsm");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);
        fire(ERROR, errorMessage);
    }

    public enum SwitchSyncState {
        INITIALIZED,
        COMPUTE_MISSING_RULES,
        COMPUTE_MISCONFIGURED_RULES,
        COMPUTE_EXCESS_RULES,
        COMPUTE_EXCESS_METERS,
        RULES_COMMANDS_SEND,
        METERS_COMMANDS_SEND,
        FINISHED_WITH_ERROR,
        FINISHED
    }

    public enum SwitchSyncEvent {
        NEXT,
        MISSING_RULES_INSTALLED,
        EXCESS_RULES_REMOVED,
        MISCONFIGURED_RULES_REINSTALLED,
        RULES_SYNCHRONIZED,
        METERS_REMOVED,
        TIMEOUT,
        ERROR
    }
}
