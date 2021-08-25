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

package org.openkilda.wfm.topology.switchmanager.fsm;

import static java.util.Collections.emptyList;
import static org.apache.storm.shade.org.apache.commons.collections.ListUtils.union;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.EXCESS_RULES_REMOVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.GROUPS_INSTALLED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.GROUPS_MODIFIED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.GROUPS_REMOVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.LOGICAL_PORT_INSTALLED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.LOGICAL_PORT_REMOVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.METERS_REMOVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.MISCONFIGURED_METERS_MODIFIED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.MISCONFIGURED_RULES_REINSTALLED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.MISSING_RULES_INSTALLED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.NEXT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.RULES_SYNCHRONIZED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.TIMEOUT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_EXCESS_METERS;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_EXCESS_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_GROUP_MIRROR_CONFIGS;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_LOGICAL_PORTS_COMMANDS;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_MISCONFIGURED_METERS;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_MISCONFIGURED_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_MISSING_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.FINISHED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.FINISHED_WITH_ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.GROUPS_COMMANDS_SEND;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.INITIALIZED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.LOGICAL_PORTS_COMMANDS_SEND;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.METERS_COMMANDS_SEND;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.RULES_COMMANDS_SEND;

import org.openkilda.messaging.command.flow.BaseFlow;
import org.openkilda.messaging.command.flow.InstallFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.ModifyDefaultMeterForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.ReinstallDefaultFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.flow.RemoveFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.grpc.CreateLogicalPortRequest;
import org.openkilda.messaging.command.grpc.DeleteLogicalPortRequest;
import org.openkilda.messaging.command.switches.DeleteGroupRequest;
import org.openkilda.messaging.command.switches.DeleterMeterForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.InstallGroupRequest;
import org.openkilda.messaging.command.switches.ModifyGroupRequest;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.rule.SwitchSyncErrorData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowReinstallResponse;
import org.openkilda.messaging.info.switches.GroupInfoEntry;
import org.openkilda.messaging.info.switches.GroupSyncEntry;
import org.openkilda.messaging.info.switches.LogicalPortInfoEntry;
import org.openkilda.messaging.info.switches.LogicalPortsSyncEntry;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.MetersSyncEntry;
import org.openkilda.messaging.info.switches.RulesSyncEntry;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.model.GroupId;
import org.openkilda.model.MeterId;
import org.openkilda.model.MirrorConfig;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState;
import org.openkilda.wfm.topology.switchmanager.model.GroupInstallContext;
import org.openkilda.wfm.topology.switchmanager.model.ValidateGroupsResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateLogicalPortsResult;
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
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class SwitchSyncFsm extends AbstractBaseFsm<SwitchSyncFsm, SwitchSyncState, SwitchSyncEvent, Object> {

    private static final String COMMANDS_PROCESSING_FAILED_BY_TIMEOUT_METHOD_NAME = "commandsProcessingFailedByTimeout";
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
    private final List<Long> removedDefaultRulesCookies = new ArrayList<>();

    private List<BaseFlow> missingRules = emptyList();
    private List<ReinstallDefaultFlowForSwitchManagerRequest> misconfiguredRules = emptyList();
    private List<ModifyDefaultMeterForSwitchManagerRequest> misconfiguredMeters = emptyList();
    private List<RemoveFlow> excessRules = emptyList();
    private List<Long> excessMeters = emptyList();
    private List<GroupInstallContext> missingGroups = emptyList();
    private List<GroupInstallContext> misconfiguredGroups = emptyList();
    private List<Integer> excessGroups = emptyList();
    private List<CreateLogicalPortRequest> missingLogicalPorts = emptyList();
    private List<DeleteLogicalPortRequest> excessLogicalPorts = emptyList();

    private int missingRulesPendingResponsesCount = 0;
    private int excessRulesPendingResponsesCount = 0;
    private int reinstallDefaultRulesPendingResponsesCount = 0;
    private int excessMetersPendingResponsesCount = 0;
    private int misconfiguredMetersPendingResponsesCount = 0;
    private int missingGroupsPendingResponsesCount = 0;
    private int misconfiguredGroupsPendingResponsesCount = 0;
    private int excessGroupsPendingResponsesCount = 0;
    private int missingLogicalPortsPendingResponsesCount = 0;
    private int excessLogicalPortsPendingResponsesCount = 0;

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

        builder.externalTransition().from(COMPUTE_EXCESS_METERS).to(COMPUTE_MISCONFIGURED_METERS).on(NEXT)
                .callMethod("computeMisconfiguredMeters");
        builder.externalTransition().from(COMPUTE_MISCONFIGURED_METERS).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(COMPUTE_MISCONFIGURED_METERS).to(COMPUTE_GROUP_MIRROR_CONFIGS).on(NEXT)
                .callMethod("computeGroupMirrorConfigs");
        builder.externalTransition().from(COMPUTE_GROUP_MIRROR_CONFIGS).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(COMPUTE_GROUP_MIRROR_CONFIGS).to(COMPUTE_LOGICAL_PORTS_COMMANDS).on(NEXT)
                .callMethod("computeLogicalPortsCommands");
        builder.externalTransition().from(COMPUTE_LOGICAL_PORTS_COMMANDS).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(COMPUTE_LOGICAL_PORTS_COMMANDS).to(LOGICAL_PORTS_COMMANDS_SEND).on(NEXT)
                .callMethod("sendLogicalPortsCommandsCommands");
        builder.internalTransition().within(LOGICAL_PORTS_COMMANDS_SEND).on(LOGICAL_PORT_INSTALLED)
                .callMethod("logicalPortInstalled");
        builder.internalTransition().within(LOGICAL_PORTS_COMMANDS_SEND).on(LOGICAL_PORT_REMOVED)
                .callMethod("logicalPortRemoved");
        builder.externalTransition().from(LOGICAL_PORTS_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod(COMMANDS_PROCESSING_FAILED_BY_TIMEOUT_METHOD_NAME);
        builder.externalTransition().from(LOGICAL_PORTS_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(LOGICAL_PORTS_COMMANDS_SEND).to(GROUPS_COMMANDS_SEND).on(NEXT)
                .callMethod("sendGroupsCommands");
        builder.internalTransition().within(GROUPS_COMMANDS_SEND).on(GROUPS_INSTALLED).callMethod("groupsInstalled");
        builder.internalTransition().within(GROUPS_COMMANDS_SEND).on(GROUPS_MODIFIED).callMethod("groupsModified");
        builder.internalTransition().within(GROUPS_COMMANDS_SEND).on(GROUPS_REMOVED).callMethod("groupsRemoved");

        builder.externalTransition().from(GROUPS_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod(COMMANDS_PROCESSING_FAILED_BY_TIMEOUT_METHOD_NAME);
        builder.externalTransition().from(GROUPS_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(GROUPS_COMMANDS_SEND).to(RULES_COMMANDS_SEND).on(NEXT)
                .callMethod("sendRulesCommands");
        builder.internalTransition().within(RULES_COMMANDS_SEND).on(MISSING_RULES_INSTALLED)
                .callMethod("missingRuleInstalled");
        builder.internalTransition().within(RULES_COMMANDS_SEND).on(EXCESS_RULES_REMOVED)
                .callMethod("excessRuleRemoved");
        builder.internalTransition().within(RULES_COMMANDS_SEND).on(MISCONFIGURED_RULES_REINSTALLED)
                .callMethod("misconfiguredRuleReinstalled");

        builder.externalTransition().from(RULES_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod(COMMANDS_PROCESSING_FAILED_BY_TIMEOUT_METHOD_NAME);
        builder.externalTransition().from(RULES_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(RULES_COMMANDS_SEND).to(METERS_COMMANDS_SEND).on(RULES_SYNCHRONIZED)
                .callMethod("sendMetersCommands");
        builder.internalTransition().within(METERS_COMMANDS_SEND).on(METERS_REMOVED).callMethod("meterRemoved");
        builder.internalTransition().within(METERS_COMMANDS_SEND).on(MISCONFIGURED_METERS_MODIFIED)
                .callMethod("meterModified");

        builder.externalTransition().from(METERS_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod(COMMANDS_PROCESSING_FAILED_BY_TIMEOUT_METHOD_NAME);
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

    protected void computeMisconfiguredMeters(SwitchSyncState from, SwitchSyncState to,
                                             SwitchSyncEvent event, Object context) {
        if (!validationResult.isProcessMeters()) {
            return;
        }
        List<Long> modifyDefaultMeters = getModifyDefaultMeters();
        List<MeterInfoEntry> modifyFlowMeters = getModifyFlowMeters();
        if (!modifyDefaultMeters.isEmpty() || !modifyFlowMeters.isEmpty()) {
            log.info("Compute modify meters (switch={}, key={})", switchId, key);
            try {
                misconfiguredMeters = commandBuilder.buildCommandsToModifyMisconfiguredMeters(
                        switchId, modifyDefaultMeters, modifyFlowMeters);
            } catch (Exception e) {
                sendException(e);
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

    List<Long> getModifyDefaultMeters() {
        ValidateRulesResult validateRulesResult = validationResult.getValidateRulesResult();
        return validationResult.getValidateMetersResult().getMisconfiguredMeters().stream()
                .filter(meter -> MeterId.isMeterIdOfDefaultRule(meter.getMeterId()))
                .filter(meter -> !validateRulesResult.getMissingRules().contains(meter.getCookie()))
                .filter(meter -> !validateRulesResult.getMisconfiguredRules().contains(meter.getCookie()))
                .map(MeterInfoEntry::getMeterId)
                .collect(Collectors.toList());
    }

    List<MeterInfoEntry> getModifyFlowMeters() {
        ValidateRulesResult validateRulesResult = validationResult.getValidateRulesResult();
        return validationResult.getValidateMetersResult().getMisconfiguredMeters().stream()
                .filter(meter -> MeterId.isMeterIdOfFlowRule(meter.getMeterId()))
                .filter(meter -> !validateRulesResult.getMissingRules().contains(meter.getCookie()))
                .collect(Collectors.toList());
    }

    protected void sendMetersCommands(SwitchSyncState from, SwitchSyncState to,
                                      SwitchSyncEvent event, Object context) {
        if (excessMeters.isEmpty() && misconfiguredMeters.isEmpty()) {
            log.info("Nothing to do with meters (switch={}, key={})", switchId, key);
            fire(NEXT);
            return;
        }
        if (!excessMeters.isEmpty()) {
            log.info("Request to remove switch meters has been sent (switch={}, key={})", switchId, key);
            excessMetersPendingResponsesCount = excessMeters.size();

            for (Long meterId : excessMeters) {
                carrier.sendCommandToSpeaker(key, new DeleterMeterForSwitchManagerRequest(switchId, meterId));
            }
        }
        if (!misconfiguredMeters.isEmpty()) {
            log.info("Request to modify switch meters has been sent (switch={}, key={})", switchId, key);
            misconfiguredMetersPendingResponsesCount = misconfiguredMeters.size();

            for (ModifyDefaultMeterForSwitchManagerRequest request : misconfiguredMeters) {
                carrier.sendCommandToSpeaker(key, request);
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

    protected void meterModified(SwitchSyncState from, SwitchSyncState to,
                                 SwitchSyncEvent event, Object context) {
        log.info("Switch meter modified (switch={}, key={})", switchId, key);
        misconfiguredMetersPendingResponsesCount--;
        continueIfMetersCommandsDone();
    }

    protected void computeLogicalPortsCommands(SwitchSyncState from, SwitchSyncState to,
                                               SwitchSyncEvent event, Object context) {
        List<LogicalPortInfoEntry> missingPorts = Optional.ofNullable(validationResult)
                .map(ValidationResult::getValidateLogicalPortsResult)
                .map(ValidateLogicalPortsResult::getMissingLogicalPorts)
                .orElse(emptyList());
        if (!missingPorts.isEmpty()) {
            log.info("Compute logical port commands to install logical ports (switch={}, key={})", switchId, key);
            try {
                missingLogicalPorts = commandBuilder.buildLogicalPortInstallCommands(switchId, missingPorts);
            } catch (Exception e) {
                sendException(e);
            }
        }

        List<Integer> excessPortNumbers = Optional.ofNullable(validationResult)
                .map(ValidationResult::getValidateLogicalPortsResult)
                .map(ValidateLogicalPortsResult::getExcessLogicalPorts)
                .orElse(emptyList())
                .stream()
                .map(LogicalPortInfoEntry::getLogicalPortNumber)
                .collect(Collectors.toList());
        if (request.isRemoveExcess() && !excessPortNumbers.isEmpty()) {
            log.info("Compute logical port commands to remove logical ports (switch={}, key={})", switchId, key);
            try {
                excessLogicalPorts = commandBuilder.buildLogicalPortDeleteCommands(switchId, excessPortNumbers);
            } catch (Exception e) {
                sendException(e);
            }
        }
    }

    protected void sendLogicalPortsCommandsCommands(SwitchSyncState from, SwitchSyncState to,
                                               SwitchSyncEvent event, Object context) {
        if (missingLogicalPorts.isEmpty() && excessLogicalPorts.isEmpty()) {
            log.info("Nothing to do with logical ports (switch={}, key={})", switchId, key);
            fire(NEXT);
        }

        if (!missingLogicalPorts.isEmpty()) {
            log.info("Request to install logical ports has been sent (switch={}, key={})", switchId, key);
            missingLogicalPortsPendingResponsesCount = missingLogicalPorts.size();

            for (CreateLogicalPortRequest createRequest : missingLogicalPorts) {
                carrier.sendCommandToSpeaker(key, createRequest);
            }
        }

        if (!excessLogicalPorts.isEmpty()) {
            log.info("Request to remove logical ports has been sent (switch={}, key={})", switchId, key);
            excessLogicalPortsPendingResponsesCount = excessLogicalPorts.size();

            for (DeleteLogicalPortRequest deleteRequest : excessLogicalPorts) {
                carrier.sendCommandToSpeaker(key, deleteRequest);
            }
        }

        continueIfLogicalPortsSynchronized();
    }

    protected void logicalPortInstalled(SwitchSyncState from, SwitchSyncState to,
                                        SwitchSyncEvent event, Object context) {
        log.info("Logical port installed (switch={}, key={})", switchId, key);
        missingLogicalPortsPendingResponsesCount--;
        continueIfLogicalPortsSynchronized();

    }

    protected void logicalPortRemoved(SwitchSyncState from, SwitchSyncState to,
                                      SwitchSyncEvent event, Object context) {
        log.info("Logical port removed (switch={}, key={})", switchId, key);
        excessLogicalPortsPendingResponsesCount--;
        continueIfLogicalPortsSynchronized();
    }

    protected void computeGroupMirrorConfigs(SwitchSyncState from, SwitchSyncState to,
                                             SwitchSyncEvent event, Object context) {

        List<Integer> missingGroupIds = validationResult.getValidateGroupsResult().getMissingGroups().stream()
                .map(GroupInfoEntry::getGroupId)
                .collect(Collectors.toList());

        if (!missingGroupIds.isEmpty()) {
            log.info("Compute mirror configs for install groups (switch={}, key={})", switchId, key);
            try {
                missingGroups = commandBuilder.buildGroupInstallContexts(switchId, missingGroupIds);
            } catch (Exception e) {
                sendException(e);
            }
        }

        List<Integer> misconfiguredGroupIds = validationResult.getValidateGroupsResult().getMisconfiguredGroups()
                .stream()
                .map(GroupInfoEntry::getGroupId)
                .collect(Collectors.toList());
        if (!misconfiguredGroupIds.isEmpty()) {
            log.info("Compute mirror configs for modify groups (switch={}, key={})", switchId, key);
            try {
                misconfiguredGroups = commandBuilder.buildGroupInstallContexts(switchId, misconfiguredGroupIds);
            } catch (Exception e) {
                sendException(e);
            }
        }

        excessGroups = validationResult.getValidateGroupsResult().getExcessGroups().stream()
                .map(GroupInfoEntry::getGroupId)
                .collect(Collectors.toList());
    }

    protected void sendGroupsCommands(SwitchSyncState from, SwitchSyncState to,
                                      SwitchSyncEvent event, Object context) {
        if (missingGroups.isEmpty() && misconfiguredGroups.isEmpty() && excessGroups.isEmpty()) {
            log.info("Nothing to do with groups (switch={}, key={})", switchId, key);
            fire(NEXT);
        }

        if (!missingGroups.isEmpty()) {
            log.info("Request to install switch groups has been sent (switch={}, key={})", switchId, key);
            missingGroupsPendingResponsesCount = missingGroups.size();

            for (GroupInstallContext groupContext : missingGroups) {
                carrier.sendCommandToSpeaker(key, new InstallGroupRequest(switchId, groupContext.getMirrorConfig(),
                        groupContext.getEncapsulation(), groupContext.getEgressSwitchId()));
            }
        }

        if (!misconfiguredGroups.isEmpty()) {
            log.info("Request to modify switch groups has been sent (switch={}, key={})", switchId, key);
            misconfiguredGroupsPendingResponsesCount = misconfiguredGroups.size();

            for (GroupInstallContext groupContext : misconfiguredGroups) {
                carrier.sendCommandToSpeaker(key, new ModifyGroupRequest(switchId, groupContext.getMirrorConfig(),
                        groupContext.getEncapsulation(), groupContext.getEgressSwitchId()));
            }
        }

        if (!excessGroups.isEmpty()) {
            log.info("Request to remove switch groups has been sent (switch={}, key={})", switchId, key);
            excessGroupsPendingResponsesCount = excessGroups.size();

            for (Integer groupId : excessGroups) {
                carrier.sendCommandToSpeaker(key, new DeleteGroupRequest(switchId, new GroupId(groupId)));
            }
        }

        continueIfGroupsSynchronized();
    }

    protected void groupsInstalled(SwitchSyncState from, SwitchSyncState to,
                                   SwitchSyncEvent event, Object context) {
        log.info("Switch group installed (switch={}, key={})", switchId, key);
        missingGroupsPendingResponsesCount--;
        continueIfGroupsSynchronized();

    }

    protected void groupsModified(SwitchSyncState from, SwitchSyncState to,
                                  SwitchSyncEvent event, Object context) {
        log.info("Switch group modified (switch={}, key={})", switchId, key);
        misconfiguredGroupsPendingResponsesCount--;
        continueIfGroupsSynchronized();
    }

    protected void groupsRemoved(SwitchSyncState from, SwitchSyncState to,
                                 SwitchSyncEvent event, Object context) {
        log.info("Switch group removed (switch={}, key={})", switchId, key);
        excessGroupsPendingResponsesCount--;
        continueIfGroupsSynchronized();
    }

    private void continueIfRulesSynchronized() {
        if (missingRulesPendingResponsesCount == 0
                && excessRulesPendingResponsesCount == 0
                && reinstallDefaultRulesPendingResponsesCount == 0) {
            fire(RULES_SYNCHRONIZED);
        }
    }

    private void continueIfMetersCommandsDone() {
        if (excessMetersPendingResponsesCount == 0 && misconfiguredMetersPendingResponsesCount == 0) {
            fire(NEXT);
        }
    }

    private void continueIfGroupsSynchronized() {
        if (missingGroupsPendingResponsesCount == 0
                && misconfiguredGroupsPendingResponsesCount == 0
                && excessGroupsPendingResponsesCount == 0) {
            fire(NEXT);
        }
    }

    private void continueIfLogicalPortsSynchronized() {
        if (missingLogicalPortsPendingResponsesCount == 0 && excessLogicalPortsPendingResponsesCount == 0) {
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
        ValidateGroupsResult validateGroupsResult = validationResult.getValidateGroupsResult();
        ValidateLogicalPortsResult validateLogicalPortsResult = Optional.ofNullable(
                validationResult.getValidateLogicalPortsResult()).orElse(ValidateLogicalPortsResult.newEmpty());

        RulesSyncEntry rulesEntry = new RulesSyncEntry(
                new ArrayList<>(validateRulesResult.getMissingRules()),
                new ArrayList<>(validateRulesResult.getMisconfiguredRules()),
                new ArrayList<>(validateRulesResult.getProperRules()),
                new ArrayList<>(validateRulesResult.getExcessRules()),
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

        List<Integer> installedGroupsIds = missingGroups.stream().map(GroupInstallContext::getMirrorConfig)
                .map(MirrorConfig::getGroupId).map(GroupId::intValue).collect(Collectors.toList());
        List<Integer> modifiedGroupsIds = misconfiguredGroups.stream().map(GroupInstallContext::getMirrorConfig)
                .map(MirrorConfig::getGroupId).map(GroupId::intValue).collect(Collectors.toList());

        GroupSyncEntry groupEntry = GroupSyncEntry.builder()
                .proper(validateGroupsResult.getProperGroups())
                .misconfigured(validateGroupsResult.getMisconfiguredGroups())
                .missing(validateGroupsResult.getMissingGroups())
                .excess(validateGroupsResult.getExcessGroups())
                .installed(mapToGroupEntryList(installedGroupsIds, validateGroupsResult.getMissingGroups()))
                .modified(mapToGroupEntryList(modifiedGroupsIds, validateGroupsResult.getMisconfiguredGroups()))
                .removed(mapToGroupEntryList(excessGroups, validateGroupsResult.getExcessGroups()))
                .build();

        LogicalPortsSyncEntry logicalPortsEntry = LogicalPortsSyncEntry.builder()
                .proper(validateLogicalPortsResult.getProperLogicalPorts())
                .misconfigured(validateLogicalPortsResult.getMisconfiguredLogicalPorts())
                .excess(validateLogicalPortsResult.getExcessLogicalPorts())
                .missing(validateLogicalPortsResult.getMissingLogicalPorts())
                .installed(validateLogicalPortsResult.getMissingLogicalPorts())
                .removed(validateLogicalPortsResult.getExcessLogicalPorts())
                .build();

        SwitchSyncResponse response = new SwitchSyncResponse(switchId, rulesEntry, metersEntry, groupEntry,
                logicalPortsEntry);
        InfoMessage message = new InfoMessage(response, System.currentTimeMillis(), key);

        carrier.cancelTimeoutCallback(key);
        carrier.response(key, message);
    }

    private List<GroupInfoEntry> mapToGroupEntryList(List<Integer> groupIds,
                                                     List<GroupInfoEntry> groupEntries) {
        return groupEntries.stream()
                .filter(entry -> groupIds.contains(entry.getGroupId()))
                .collect(Collectors.toList());
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
        COMPUTE_MISCONFIGURED_METERS,
        COMPUTE_GROUP_MIRROR_CONFIGS,
        COMPUTE_LOGICAL_PORTS_COMMANDS,
        RULES_COMMANDS_SEND,
        METERS_COMMANDS_SEND,
        GROUPS_COMMANDS_SEND,
        LOGICAL_PORTS_COMMANDS_SEND,
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
        MISCONFIGURED_METERS_MODIFIED,
        GROUPS_INSTALLED,
        GROUPS_MODIFIED,
        GROUPS_REMOVED,
        LOGICAL_PORT_INSTALLED,
        LOGICAL_PORT_REMOVED,
        TIMEOUT,
        ERROR
    }
}
