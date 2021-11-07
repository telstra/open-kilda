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
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.COMMANDS_INSTALLED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.COMMANDS_REMOVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.EXCESS_RULES_REMOVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.GROUPS_REMOVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.LOGICAL_PORT_INSTALLED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.LOGICAL_PORT_REMOVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.METERS_REMOVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.NEXT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.SWITCH_SYNCHRONIZED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.TIMEOUT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_EXCESS_GROUPS;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_EXCESS_METERS;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_EXCESS_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_LOGICAL_PORTS_COMMANDS;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_MISCONFIGURED_METERS;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_MISCONFIGURED_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_MISSING_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.FINISHED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.FINISHED_WITH_ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.INITIALIZED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.LOGICAL_PORTS_COMMANDS_SEND;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.REMOVE_GROUPS_COMMANDS_SEND;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.REMOVE_METERS_COMMANDS_SEND;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.REMOVE_RULES_COMMANDS_SEND;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.SEND_COMMANDS;

import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.RemoveSpeakerCommandsRequest;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.flow.RemoveFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.grpc.CreateLogicalPortRequest;
import org.openkilda.messaging.command.grpc.DeleteLogicalPortRequest;
import org.openkilda.messaging.command.switches.DeleteGroupRequest;
import org.openkilda.messaging.command.switches.DeleteMeterForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.rule.SwitchSyncErrorData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.switches.GroupInfoEntry;
import org.openkilda.messaging.info.switches.GroupSyncEntry;
import org.openkilda.messaging.info.switches.LogicalPortInfoEntry;
import org.openkilda.messaging.info.switches.LogicalPortsSyncEntry;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.MetersSyncEntry;
import org.openkilda.messaging.info.switches.RulesSyncEntry;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.model.GroupId;
import org.openkilda.model.MirrorConfig;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.SpeakerCommandData;
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
import java.util.Set;
import java.util.UUID;
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
    private Set<Long> missingRulesCookies;
    private Set<Long> excessRulesCookies;

    private List<RemoveFlow> excessRules = emptyList();
    private List<Long> excessMeters = emptyList();
    private List<GroupInstallContext> missingGroups = emptyList();
    private List<GroupInstallContext> misconfiguredGroups = emptyList();
    private List<Integer> excessGroups = emptyList();
    private List<CreateLogicalPortRequest> missingLogicalPorts = emptyList();
    private List<DeleteLogicalPortRequest> excessLogicalPorts = emptyList();

    private List<SpeakerCommandData> removeOfElements = new ArrayList<>();
    private List<SpeakerCommandData> installOfElements = new ArrayList<>();

    private int excessRulesPendingResponsesCount = 0;
    private int excessMetersPendingResponsesCount = 0;
    private int excessGroupsPendingResponsesCount = 0;
    private int missingLogicalPortsPendingResponsesCount = 0;
    private int excessLogicalPortsPendingResponsesCount = 0;

    private boolean waitRemoveOfElementsResult = false;
    private boolean waitInstallOfElementsResult = false;

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

        builder.externalTransition().from(COMPUTE_EXCESS_RULES).to(COMPUTE_MISCONFIGURED_METERS).on(NEXT)
                .callMethod("computeMisconfiguredAndMissingMeters");
        builder.externalTransition().from(COMPUTE_MISCONFIGURED_METERS).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(COMPUTE_MISCONFIGURED_METERS).to(COMPUTE_EXCESS_METERS).on(NEXT)
                .callMethod("computeExcessMeters");
        builder.externalTransition().from(COMPUTE_EXCESS_METERS).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(COMPUTE_EXCESS_METERS).to(COMPUTE_EXCESS_GROUPS).on(NEXT)
                .callMethod("computeExcessGroups");
        builder.externalTransition().from(COMPUTE_EXCESS_GROUPS).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(COMPUTE_EXCESS_GROUPS).to(COMPUTE_LOGICAL_PORTS_COMMANDS).on(NEXT)
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

        builder.externalTransition().from(LOGICAL_PORTS_COMMANDS_SEND).to(REMOVE_GROUPS_COMMANDS_SEND).on(NEXT)
                .callMethod("sendExcessGroupsCommands");
        builder.internalTransition().within(REMOVE_GROUPS_COMMANDS_SEND).on(GROUPS_REMOVED).callMethod("groupsRemoved");

        builder.externalTransition().from(REMOVE_GROUPS_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod(COMMANDS_PROCESSING_FAILED_BY_TIMEOUT_METHOD_NAME);
        builder.externalTransition().from(REMOVE_GROUPS_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(REMOVE_GROUPS_COMMANDS_SEND).to(REMOVE_RULES_COMMANDS_SEND).on(NEXT)
                .callMethod("sendExcessRulesCommands");
        builder.internalTransition().within(REMOVE_RULES_COMMANDS_SEND).on(EXCESS_RULES_REMOVED)
                .callMethod("excessRuleRemoved");

        builder.externalTransition().from(REMOVE_RULES_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod(COMMANDS_PROCESSING_FAILED_BY_TIMEOUT_METHOD_NAME);
        builder.externalTransition().from(REMOVE_RULES_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(REMOVE_RULES_COMMANDS_SEND).to(REMOVE_METERS_COMMANDS_SEND)
                .on(NEXT)
                .callMethod("sendExcessMetersCommands");
        builder.internalTransition().within(REMOVE_METERS_COMMANDS_SEND).on(METERS_REMOVED).callMethod("meterRemoved");

        builder.externalTransition().from(REMOVE_METERS_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod(COMMANDS_PROCESSING_FAILED_BY_TIMEOUT_METHOD_NAME);
        builder.externalTransition().from(REMOVE_METERS_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(REMOVE_METERS_COMMANDS_SEND).to(SEND_COMMANDS).on(NEXT)
                .callMethod("sendCommands");
        builder.internalTransition().within(SEND_COMMANDS).on(COMMANDS_REMOVED).callMethod("commandsRemoved");
        builder.internalTransition().within(SEND_COMMANDS).on(COMMANDS_INSTALLED).callMethod("commandsInstalled");

        builder.externalTransition().from(SEND_COMMANDS).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod(COMMANDS_PROCESSING_FAILED_BY_TIMEOUT_METHOD_NAME);
        builder.externalTransition().from(SEND_COMMANDS).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(SEND_COMMANDS).to(FINISHED).on(SWITCH_SYNCHRONIZED)
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
        missingRulesCookies = validationResult.getValidateRulesResult().getMissingRules();
        if (!missingRulesCookies.isEmpty()) {
            log.info("Compute install missing rules (switch={}, key={})", switchId, key);
            try {
                List<FlowSpeakerCommandData> missingRules = validationResult.getRulesByCookies(missingRulesCookies);

                installOfElements.addAll(missingRules);
            } catch (Exception e) {
                sendException(e);
            }
        }
    }

    protected void computeMisconfiguredRules(SwitchSyncState from, SwitchSyncState to,
                                             SwitchSyncEvent event, Object context) {
        Set<Long> reinstallRules = validationResult.getValidateRulesResult().getMisconfiguredRules();
        if (!reinstallRules.isEmpty()) {
            log.info("Compute reinstall misconfigured rules (switch={}, key={})", switchId, key);
            try {
                // todo (rule-manager-fl-integration): use separate modifyOfElements and modify commands
                installOfElements.addAll(validationResult.getRulesByCookies(reinstallRules));
                removeOfElements.addAll(validationResult.getRulesByCookies(reinstallRules));
            } catch (Exception e) {
                sendException(e);
            }
        }
    }

    protected void computeExcessRules(SwitchSyncState from, SwitchSyncState to,
                                      SwitchSyncEvent event, Object context) {
        excessRulesCookies = validationResult.getValidateRulesResult().getExcessRules();

        if (request.isRemoveExcess() && !excessRulesCookies.isEmpty()) {
            log.info("Compute remove excess rules (switch={}, key={})", switchId, key);
            try {
                excessRules = commandBuilder.buildCommandsToRemoveExcessRules(
                        switchId, validationResult.getFlowEntries(), excessRulesCookies);
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
                log.info("Compute remove excess meters (switch={}, key={})", switchId, key);
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

    protected void computeMisconfiguredAndMissingMeters(SwitchSyncState from, SwitchSyncState to,
                                                        SwitchSyncEvent event, Object context) {
        if (!validationResult.isProcessMeters()) {
            return;
        }
        Set<Long> modifyFlowMeterIds = validationResult.getValidateMetersResult().getMisconfiguredMeters()
                .stream()
                .map(MeterInfoEntry::getMeterId)
                .collect(Collectors.toSet());
        if (!modifyFlowMeterIds.isEmpty()) {
            log.info("Compute misconfigured and missing meters (switch={}, key={})", switchId, key);
            try {
                // todo (rule-manager-fl-integration): use separate modifyOfElements and modify commands
                installOfElements.addAll(validationResult.getMeterByMeterId(modifyFlowMeterIds));
            } catch (Exception e) {
                sendException(e);
            }
        }
    }

    protected void sendExcessRulesCommands(SwitchSyncState from, SwitchSyncState to,
                                           SwitchSyncEvent event, Object context) {
        if (excessRules.isEmpty()) {
            log.info("Nothing to do with excess rules (switch={}, key={})", switchId, key);
            fire(NEXT);
            return;
        }
        log.info("Request to remove excess switch rules has been sent (switch={}, key={})", switchId, key);
        excessRulesPendingResponsesCount = excessRules.size();

        for (RemoveFlow command : excessRules) {
            carrier.sendCommandToSpeaker(key, new RemoveFlowForSwitchManagerRequest(switchId, command));
        }

        continueIfExcessRulesSynchronized();
    }

    protected void sendExcessMetersCommands(SwitchSyncState from, SwitchSyncState to,
                                            SwitchSyncEvent event, Object context) {
        if (excessMeters.isEmpty()) {
            log.info("Nothing to do with excess meters (switch={}, key={})", switchId, key);
            fire(NEXT);
            return;
        }
        log.info("Request to remove excess switch meters has been sent (switch={}, key={})", switchId, key);
        excessMetersPendingResponsesCount = excessMeters.size();

        for (Long meterId : excessMeters) {
            carrier.sendCommandToSpeaker(key, new DeleteMeterForSwitchManagerRequest(switchId, meterId));
        }

        continueIfExcessMetersDone();
    }

    protected void sendCommands(SwitchSyncState from, SwitchSyncState to,
                                SwitchSyncEvent event, Object context) {
        if (removeOfElements.isEmpty() && installOfElements.isEmpty()) {
            log.info("Nothing to do with rules/meters/groups (switch={}, key={})", switchId, key);
            fire(SWITCH_SYNCHRONIZED);
            return;
        }

        MessageContext messageContext = new MessageContext();
        if (!removeOfElements.isEmpty()) {
            log.info("Request to remove switch rules/meters/groups has been sent (switch={}, key={})", switchId, key);

            carrier.sendCommandToSpeaker(key, new RemoveSpeakerCommandsRequest(messageContext, switchId,
                    UUID.fromString(messageContext.getCorrelationId()), removeOfElements));
            waitRemoveOfElementsResult = true;
        }

        if (!installOfElements.isEmpty()) {
            log.info("Request to install switch rules/meters/groups has been sent (switch={}, key={})", switchId, key);

            carrier.sendCommandToSpeaker(key, new InstallSpeakerCommandsRequest(messageContext, switchId,
                    UUID.fromString(messageContext.getCorrelationId()), installOfElements));
            waitInstallOfElementsResult = true;
        }

        continueIfCommandsSynchronized();
    }

    protected void commandsRemoved(SwitchSyncState from, SwitchSyncState to,
                                   SwitchSyncEvent event, Object context) {
        log.info("Switch remove rules/meters/groups processed (switch={}, key={})", switchId, key);
        waitRemoveOfElementsResult = false;
        continueIfCommandsSynchronized();
    }

    protected void commandsInstalled(SwitchSyncState from, SwitchSyncState to,
                                     SwitchSyncEvent event, Object context) {
        log.info("Switch install rules/meters/groups processed (switch={}, key={})", switchId, key);
        waitInstallOfElementsResult = false;
        continueIfCommandsSynchronized();
    }

    protected void excessRuleRemoved(SwitchSyncState from, SwitchSyncState to,
                                     SwitchSyncEvent event, Object context) {
        log.info("Switch excess rule removed (switch={}, key={})", switchId, key);
        excessRulesPendingResponsesCount--;
        continueIfExcessRulesSynchronized();
    }

    protected void meterRemoved(SwitchSyncState from, SwitchSyncState to,
                                SwitchSyncEvent event, Object context) {
        log.info("Switch excess meter removed (switch={}, key={})", switchId, key);
        excessMetersPendingResponsesCount--;
        continueIfExcessMetersDone();
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
            return;
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

    protected void computeExcessGroups(SwitchSyncState from, SwitchSyncState to,
                                       SwitchSyncEvent event, Object context) {
        excessGroups = validationResult.getValidateGroupsResult().getExcessGroups().stream()
                .map(GroupInfoEntry::getGroupId)
                .collect(Collectors.toList());
    }

    protected void sendExcessGroupsCommands(SwitchSyncState from, SwitchSyncState to,
                                            SwitchSyncEvent event, Object context) {
        if (excessGroups.isEmpty()) {
            log.info("Nothing to do with excess groups (switch={}, key={})", switchId, key);
            fire(NEXT);
            return;
        }
        log.info("Request to remove switch groups has been sent (switch={}, key={})", switchId, key);
        excessGroupsPendingResponsesCount = excessGroups.size();

        for (Integer groupId : excessGroups) {
            carrier.sendCommandToSpeaker(key, new DeleteGroupRequest(switchId, new GroupId(groupId)));
        }

        continueIfExcessGroupsSynchronized();
    }

    protected void groupsRemoved(SwitchSyncState from, SwitchSyncState to,
                                 SwitchSyncEvent event, Object context) {
        log.info("Switch excess group removed (switch={}, key={})", switchId, key);
        excessGroupsPendingResponsesCount--;
        continueIfExcessGroupsSynchronized();
    }

    private void continueIfExcessRulesSynchronized() {
        if (excessRulesPendingResponsesCount == 0) {
            fire(NEXT);
        }
    }

    private void continueIfExcessMetersDone() {
        if (excessMetersPendingResponsesCount == 0) {
            fire(NEXT);
        }
    }

    private void continueIfExcessGroupsSynchronized() {
        if (excessGroupsPendingResponsesCount == 0) {
            fire(NEXT);
        }
    }

    private void continueIfLogicalPortsSynchronized() {
        if (missingLogicalPortsPendingResponsesCount == 0 && excessLogicalPortsPendingResponsesCount == 0) {
            fire(NEXT);
        }
    }

    private void continueIfCommandsSynchronized() {
        if (!(waitInstallOfElementsResult || waitRemoveOfElementsResult)) {
            fire(SWITCH_SYNCHRONIZED);
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
                new ArrayList<>(missingRulesCookies),
                request.isRemoveExcess() ? new ArrayList<>(excessRulesCookies) : emptyList());

        MetersSyncEntry metersEntry = null;
        if (validationResult.isProcessMeters()) {
            metersEntry = new MetersSyncEntry(validateMetersResult.getMissingMeters(),
                    validateMetersResult.getMisconfiguredMeters(),
                    validateMetersResult.getProperMeters(),
                    validateMetersResult.getExcessMeters(),
                    validateMetersResult.getMissingMeters(),
                    request.isRemoveExcess() ? validateMetersResult.getExcessMeters() : emptyList());
        }

        // todo (rule-manager-fl-integration): fix groups in response. Groups installed and modified using rule-manager.
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
        COMPUTE_EXCESS_GROUPS,
        COMPUTE_LOGICAL_PORTS_COMMANDS,
        REMOVE_RULES_COMMANDS_SEND,
        REMOVE_METERS_COMMANDS_SEND,
        REMOVE_GROUPS_COMMANDS_SEND,
        LOGICAL_PORTS_COMMANDS_SEND,
        SEND_COMMANDS,
        FINISHED_WITH_ERROR,
        FINISHED
    }

    public enum SwitchSyncEvent {
        NEXT,
        EXCESS_RULES_REMOVED,
        METERS_REMOVED,
        GROUPS_REMOVED,
        COMMANDS_REMOVED,
        COMMANDS_INSTALLED,
        SWITCH_SYNCHRONIZED,
        LOGICAL_PORT_INSTALLED,
        LOGICAL_PORT_REMOVED,
        TIMEOUT,
        ERROR
    }
}
