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

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Optional.ofNullable;
import static org.apache.storm.shade.org.apache.commons.collections.ListUtils.union;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.COMMANDS_PROCESSED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.LOGICAL_PORT_INSTALLED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.LOGICAL_PORT_REMOVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.NEXT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent.TIMEOUT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_EXCESS_METERS;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_EXCESS_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_GROUP_MIRROR_CONFIGS;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_LOGICAL_PORTS_COMMANDS;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_MISCONFIGURED_METERS;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_MISCONFIGURED_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_MISSING_METERS;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.COMPUTE_MISSING_RULES;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.FINISHED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.FINISHED_WITH_ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.INITIALIZED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.LOGICAL_PORTS_COMMANDS_SEND;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.SEND_INSTALL_COMMANDS;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.SEND_MODIFY_COMMANDS;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState.SEND_REMOVE_COMMANDS;

import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.messaging.command.grpc.CreateOrUpdateLogicalPortRequest;
import org.openkilda.messaging.command.grpc.DeleteLogicalPortRequest;
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
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.utils.RuleManagerHelper;
import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.topology.switchmanager.bolt.SwitchManagerHub.OfCommandAction;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchSyncFsm.SwitchSyncState;
import org.openkilda.wfm.topology.switchmanager.model.ValidateGroupsResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateLogicalPortsResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidationResult;
import org.openkilda.wfm.topology.switchmanager.service.CommandBuilder;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;
import org.openkilda.wfm.topology.switchmanager.service.configs.SwitchSyncConfig;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final SwitchSyncConfig syncConfig;
    private List<Long> installedRulesCookies;
    private List<Long> reinstalledRulesCookies;
    private List<Long> removedFlowRulesCookies;

    private final List<SpeakerData> toInstall = new ArrayList<>();
    private final List<SpeakerData> toModify = new ArrayList<>();
    private final List<SpeakerData> toRemove = new ArrayList<>();
    private List<CreateOrUpdateLogicalPortRequest> missingLogicalPorts = emptyList();
    private List<DeleteLogicalPortRequest> excessLogicalPorts = emptyList();

    private int missingLogicalPortsPendingResponsesCount = 0;
    private int excessLogicalPortsPendingResponsesCount = 0;
    private int ofCommandsResponsesCount = 0;

    public SwitchSyncFsm(SwitchManagerCarrier carrier, String key, CommandBuilder commandBuilder,
                         SwitchValidateRequest request, ValidationResult validationResult,
                         SwitchSyncConfig syncConfig) {
        this.carrier = carrier;
        this.key = key;
        this.commandBuilder = commandBuilder;
        this.request = request;
        this.validationResult = fillValidationResult(validationResult);
        this.switchId = request.getSwitchId();
        this.syncConfig = syncConfig;
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
                        ValidationResult.class,
                        SwitchSyncConfig.class);

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

        builder.externalTransition().from(COMPUTE_MISCONFIGURED_METERS).to(COMPUTE_MISSING_METERS).on(NEXT)
                .callMethod("computeMissingMeters");
        builder.externalTransition().from(COMPUTE_MISSING_METERS).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(COMPUTE_MISSING_METERS).to(COMPUTE_GROUP_MIRROR_CONFIGS).on(NEXT)
                .callMethod("computeGroupMirrorConfigs");
        builder.externalTransition().from(COMPUTE_GROUP_MIRROR_CONFIGS).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(COMPUTE_GROUP_MIRROR_CONFIGS).to(COMPUTE_LOGICAL_PORTS_COMMANDS).on(NEXT)
                .callMethod("computeLogicalPortsCommands");
        builder.externalTransition().from(COMPUTE_LOGICAL_PORTS_COMMANDS).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(COMPUTE_LOGICAL_PORTS_COMMANDS).to(LOGICAL_PORTS_COMMANDS_SEND).on(NEXT)
                .callMethod("sendLogicalPortsCommands");
        builder.internalTransition().within(LOGICAL_PORTS_COMMANDS_SEND).on(LOGICAL_PORT_INSTALLED)
                .callMethod("logicalPortInstalled");
        builder.internalTransition().within(LOGICAL_PORTS_COMMANDS_SEND).on(LOGICAL_PORT_REMOVED)
                .callMethod("logicalPortRemoved");
        builder.externalTransition().from(LOGICAL_PORTS_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod(COMMANDS_PROCESSING_FAILED_BY_TIMEOUT_METHOD_NAME);
        builder.externalTransition().from(LOGICAL_PORTS_COMMANDS_SEND).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.externalTransition().from(LOGICAL_PORTS_COMMANDS_SEND).to(SEND_REMOVE_COMMANDS).on(NEXT)
                .callMethod("sendRemoveCommands");

        builder.externalTransition().from(SEND_REMOVE_COMMANDS).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod(COMMANDS_PROCESSING_FAILED_BY_TIMEOUT_METHOD_NAME);
        builder.externalTransition().from(SEND_REMOVE_COMMANDS).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.internalTransition().within(SEND_REMOVE_COMMANDS).on(COMMANDS_PROCESSED)
                .callMethod("ofCommandsProcessed");
        builder.externalTransition().from(SEND_REMOVE_COMMANDS).to(SEND_MODIFY_COMMANDS).on(NEXT)
                .callMethod("sendModifyCommands");

        builder.externalTransition().from(SEND_MODIFY_COMMANDS).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod(COMMANDS_PROCESSING_FAILED_BY_TIMEOUT_METHOD_NAME);
        builder.externalTransition().from(SEND_MODIFY_COMMANDS).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.internalTransition().within(SEND_MODIFY_COMMANDS).on(COMMANDS_PROCESSED)
                .callMethod("ofCommandsProcessed");
        builder.externalTransition().from(SEND_MODIFY_COMMANDS).to(SEND_INSTALL_COMMANDS).on(NEXT)
                .callMethod("sendInstallCommands");

        builder.externalTransition().from(SEND_INSTALL_COMMANDS).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod(COMMANDS_PROCESSING_FAILED_BY_TIMEOUT_METHOD_NAME);
        builder.externalTransition().from(SEND_INSTALL_COMMANDS).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.internalTransition().within(SEND_INSTALL_COMMANDS).on(COMMANDS_PROCESSED)
                .callMethod("ofCommandsProcessed");
        builder.externalTransition().from(SEND_INSTALL_COMMANDS).to(FINISHED).on(NEXT)
                .callMethod(FINISHED_METHOD_NAME);

        builder.defineFinalState(FINISHED);
        builder.defineFinalState(FINISHED_WITH_ERROR);

        return builder;
    }

    public String getKey() {
        return key;
    }

    private static ValidationResult fillValidationResult(ValidationResult result) {
        return new ValidationResult(
                ofNullable(result.getFlowEntries()).orElse(emptyList()),
                result.isProcessMeters(),
                ofNullable(result.getExpectedEntries()).orElse(emptyList()),
                ofNullable(result.getActualFlows()).orElse(emptyList()),
                ofNullable(result.getActualMeters()).orElse(emptyList()),
                ofNullable(result.getActualGroups()).orElse(emptyList()),
                ofNullable(result.getValidateRulesResult())
                        .orElse(new ValidateRulesResult(emptySet(), emptySet(), emptySet(), emptySet())),
                ofNullable(result.getValidateMetersResult())
                        .orElse(new ValidateMetersResult(emptyList(), emptyList(), emptyList(), emptyList())),
                ofNullable(result.getValidateGroupsResult())
                        .orElse(new ValidateGroupsResult(emptyList(), emptyList(), emptyList(), emptyList())),
                ofNullable(result.getValidateLogicalPortsResult()).orElse(
                        new ValidateLogicalPortsResult(emptyList(), emptyList(), emptyList(), emptyList(), null)));
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
                List<FlowSpeakerData> missingRules = validationResult.getExpectedEntries().stream()
                        .filter(entry -> entry instanceof FlowSpeakerData)
                        .map(entry -> (FlowSpeakerData) entry)
                        .filter(flowEntry -> installedRulesCookies.contains(flowEntry.getCookie().getValue()))
                        .collect(Collectors.toList());
                toInstall.addAll(missingRules);
            } catch (Exception e) {
                sendException(e);
            }
        }
    }

    protected void computeMisconfiguredRules(SwitchSyncState from, SwitchSyncState to,
                                             SwitchSyncEvent event, Object context) {
        reinstalledRulesCookies = new ArrayList<>(validationResult.getValidateRulesResult().getMisconfiguredRules());
        if (!reinstalledRulesCookies.isEmpty()) {
            log.info("Compute reinstall rules (switch={}, key={})", switchId, key);
            try {
                List<FlowSpeakerData> misconfiguredRulesToRemove = reinstalledRulesCookies.stream()
                        .flatMap(this::findActualFlowsByCookie)
                        .collect(Collectors.toList());
                toRemove.addAll(misconfiguredRulesToRemove);
                List<FlowSpeakerData> misconfiguredRulesToInstall = validationResult.getExpectedEntries().stream()
                        .filter(entry -> entry instanceof FlowSpeakerData)
                        .map(entry -> (FlowSpeakerData) entry)
                        .filter(flowEntry -> reinstalledRulesCookies.contains(flowEntry.getCookie().getValue()))
                        .collect(Collectors.toList());
                toInstall.addAll(misconfiguredRulesToInstall);
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
                List<FlowSpeakerData> excessRules = removedFlowRulesCookies.stream()
                        .flatMap(this::findActualFlowsByCookie)
                        .collect(Collectors.toList());
                toRemove.addAll(excessRules);
            } catch (Exception e) {
                sendException(e);
            }
        }
    }

    protected void computeExcessMeters(SwitchSyncState from, SwitchSyncState to,
                                       SwitchSyncEvent event, Object context) {
        if (request.isRemoveExcess() && validationResult.isProcessMeters()) {
            ValidateMetersResult validateMetersResult = validationResult.getValidateMetersResult();

            Set<Long> excessMeters = validateMetersResult.getExcessMeters().stream()
                    .map(MeterInfoEntry::getMeterId)
                    .collect(Collectors.toSet());
            if (!excessMeters.isEmpty()) {
                log.info("Compute remove meters (switch={}, key={})", switchId, key);
                try {
                    List<MeterSpeakerData> excessMeterCommands = excessMeters.stream()
                            .map(this::findActualMeterById)
                            .collect(Collectors.toList());
                    toRemove.addAll(excessMeterCommands);
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
        Set<Long> misconfiguredMeters = validationResult.getValidateMetersResult().getMisconfiguredMeters().stream()
                .map(MeterInfoEntry::getMeterId)
                .collect(Collectors.toSet());
        if (!misconfiguredMeters.isEmpty()) {
            log.info("Compute modify meters (switch={}, key={})", switchId, key);
            try {
                List<MeterSpeakerData> misconfiguredMeterCommands = misconfiguredMeters.stream()
                        .map(this::findExpectedMeterById)
                        .collect(Collectors.toList());
                toModify.addAll(misconfiguredMeterCommands);
            } catch (Exception e) {
                sendException(e);
            }
        }
    }

    protected void computeMissingMeters(SwitchSyncState from, SwitchSyncState to,
                                        SwitchSyncEvent event, Object context) {
        if (!validationResult.isProcessMeters()) {
            return;
        }
        Set<Long> missingMeters = validationResult.getValidateMetersResult().getMissingMeters().stream()
                .map(MeterInfoEntry::getMeterId)
                .collect(Collectors.toSet());
        if (!missingMeters.isEmpty()) {
            log.info("Compute missing meters (switch={}, key={})", switchId, key);
            try {
                List<MeterSpeakerData> missingMeterCommands = missingMeters.stream()
                        .map(this::findExpectedMeterById)
                        .collect(Collectors.toList());
                toInstall.addAll(missingMeterCommands);
            } catch (Exception e) {
                sendException(e);
            }
        }
    }

    private Stream<FlowSpeakerData> findActualFlowsByCookie(long cookie) {
        return validationResult.getActualFlows().stream()
                .filter(flowSpeakerData -> flowSpeakerData.getCookie().getValue() == cookie)
                .collect(Collectors.toList())
                .stream();
    }

    private MeterSpeakerData findActualMeterById(long meterId) {
        return validationResult.getActualMeters().stream()
                .filter(meterSpeakerData -> meterSpeakerData.getMeterId().getValue() == meterId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        format("Actual meter with id %s not found", meterId)));
    }

    private MeterSpeakerData findExpectedMeterById(long meterId) {
        return validationResult.getExpectedEntries().stream()
                .filter(entry -> entry instanceof MeterSpeakerData)
                .map(entry -> (MeterSpeakerData) entry)
                .filter(meterSpeakerData -> meterSpeakerData.getMeterId().getValue() == meterId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        format("Expected meter with id %s not found", meterId)));
    }

    private GroupSpeakerData findActualGroupById(long groupId) {
        return validationResult.getActualGroups().stream()
                .filter(groupSpeakerData -> groupSpeakerData.getGroupId().getValue() == groupId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        format("Actual group with id %s not found", groupId)));
    }

    private GroupSpeakerData findExpectedGroupById(long groupId) {
        return validationResult.getExpectedEntries().stream()
                .filter(entry -> entry instanceof GroupSpeakerData)
                .map(entry -> (GroupSpeakerData) entry)
                .filter(groupSpeakerData -> groupSpeakerData.getGroupId().getValue() == groupId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        format("Expected group with id %s not found", groupId)));
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

    protected void sendLogicalPortsCommands(SwitchSyncState from, SwitchSyncState to,
                                            SwitchSyncEvent event, Object context) {
        if (missingLogicalPorts.isEmpty() && excessLogicalPorts.isEmpty()) {
            log.info("Nothing to do with logical ports (switch={}, key={})", switchId, key);
            fire(NEXT);
            return;
        }

        if (!missingLogicalPorts.isEmpty()) {
            log.info("Request to install logical ports has been sent (switch={}, key={})", switchId, key);
            missingLogicalPortsPendingResponsesCount = missingLogicalPorts.size();

            for (CreateOrUpdateLogicalPortRequest createRequest : missingLogicalPorts) {
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
            log.info("Compute missing groups (switch={}, key={})", switchId, key);
            try {
                List<GroupSpeakerData> missingGroups = missingGroupIds.stream()
                        .map(this::findExpectedGroupById)
                        .collect(Collectors.toList());
                toInstall.addAll(missingGroups);
            } catch (Exception e) {
                sendException(e);
            }
        }

        List<Integer> misconfiguredGroupIds = validationResult.getValidateGroupsResult().getMisconfiguredGroups()
                .stream()
                .map(GroupInfoEntry::getGroupId)
                .collect(Collectors.toList());
        if (!misconfiguredGroupIds.isEmpty()) {
            log.info("Compute misconfigured groups (switch={}, key={})", switchId, key);
            try {
                List<GroupSpeakerData> misconfiguredGroups = misconfiguredGroupIds.stream()
                        .map(this::findExpectedGroupById)
                        .collect(Collectors.toList());
                toModify.addAll(misconfiguredGroups);
            } catch (Exception e) {
                sendException(e);
            }
        }

        Set<Integer> excessGroups = validationResult.getValidateGroupsResult().getExcessGroups().stream()
                .map(GroupInfoEntry::getGroupId)
                .collect(Collectors.toSet());
        if (!excessGroups.isEmpty()) {
            log.info("Compute excess groups (switch={}, key={})", switchId, key);
            try {
                List<GroupSpeakerData> excessGroupCommands = excessGroups.stream()
                        .map(this::findActualGroupById)
                        .collect(Collectors.toList());
                toRemove.addAll(excessGroupCommands);
            } catch (Exception e) {
                sendException(e);
            }
        }
    }

    protected void sendRemoveCommands(SwitchSyncState from, SwitchSyncState to,
                                      SwitchSyncEvent event, Object context) {
        if (toRemove.isEmpty()) {
            log.info("No need to process remove commands (switch={}, key={})", switchId, key);
            fire(NEXT);
            return;
        }
        List<List<OfCommand>> commandBatches = cleanupDependenciesAndBuildCommandBatches(toRemove);
        ofCommandsResponsesCount = commandBatches.size();

        log.info("Remove commands has been sent (switch={}, key={})", switchId, key);
        sendOfCommandsToSpeaker(commandBatches, OfCommandAction.DELETE);
    }

    protected void sendModifyCommands(SwitchSyncState from, SwitchSyncState to,
                                      SwitchSyncEvent event, Object context) {
        if (toModify.isEmpty()) {
            log.info("No need to process modify commands (switch={}, key={})", switchId, key);
            fire(NEXT);
            return;
        }
        List<List<OfCommand>> commandBatches = cleanupDependenciesAndBuildCommandBatches(toModify);
        ofCommandsResponsesCount = commandBatches.size();

        log.info("Modify commands has been sent (switch={}, key={})", switchId, key);
        sendOfCommandsToSpeaker(commandBatches, OfCommandAction.MODIFY);
    }

    protected void sendInstallCommands(SwitchSyncState from, SwitchSyncState to,
                                       SwitchSyncEvent event, Object context) {
        if (toInstall.isEmpty()) {
            log.info("No need to process install commands (switch={}, key={})", switchId, key);
            fire(NEXT);
            return;
        }
        List<List<OfCommand>> commandBatches = cleanupDependenciesAndBuildCommandBatches(toInstall);
        ofCommandsResponsesCount = commandBatches.size();

        log.info("Install commands has been sent (switch={}, key={})", switchId, key);
        sendOfCommandsToSpeaker(commandBatches, OfCommandAction.INSTALL);
    }

    private void sendOfCommandsToSpeaker(List<List<OfCommand>> commandBatches, OfCommandAction action) {
        for (List<OfCommand> commands : commandBatches) {
            carrier.sendOfCommandsToSpeaker(key, commands, action, switchId);
        }
    }

    protected void ofCommandsProcessed(SwitchSyncState from, SwitchSyncState to,
                                       SwitchSyncEvent event, Object context) {
        log.info("OF commands processed (switch={}, key={})", switchId, key);
        ofCommandsResponsesCount--;
        if (ofCommandsResponsesCount <= 0) {
            fire(NEXT);
        }
    }

    @VisibleForTesting
    List<List<OfCommand>> cleanupDependenciesAndBuildCommandBatches(List<SpeakerData> source) {
        Set<UUID> ids = source.stream()
                .map(SpeakerData::getUuid)
                .collect(Collectors.toSet());
        source.forEach(speakerData -> speakerData.getDependsOn().retainAll(ids));

        // As we send commands by several batches we need to put command and its dependent commands into same batch
        List<List<SpeakerData>> groupedCommands = RuleManagerHelper.groupCommandsByDependenciesAndSort(source);
        List<List<OfCommand>> batches = new ArrayList<>();

        List<OfCommand> currentBatch = new ArrayList<>();
        for (List<SpeakerData> group : groupedCommands) {
            if (!currentBatch.isEmpty() && currentBatch.size() + group.size() > syncConfig.getOfCommandsBatchSize()) {
                batches.add(currentBatch);
                currentBatch = new ArrayList<>();
            }
            currentBatch.addAll(OfCommand.toOfCommands(group));
        }
        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }

        return batches;
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
        ValidateLogicalPortsResult validateLogicalPortsResult =  validationResult.getValidateLogicalPortsResult();

        RulesSyncEntry rulesEntry = new RulesSyncEntry(
                new ArrayList<>(validateRulesResult.getMissingRules()),
                new ArrayList<>(validateRulesResult.getMisconfiguredRules()),
                new ArrayList<>(validateRulesResult.getProperRules()),
                new ArrayList<>(validateRulesResult.getExcessRules()),
                union(installedRulesCookies, reinstalledRulesCookies),
                request.isRemoveExcess() ? union(removedFlowRulesCookies, reinstalledRulesCookies)
                        : reinstalledRulesCookies);

        MetersSyncEntry metersEntry = null;
        if (validationResult.isProcessMeters()) {
            metersEntry = new MetersSyncEntry(validateMetersResult.getMissingMeters(),
                    validateMetersResult.getMisconfiguredMeters(),
                    validateMetersResult.getProperMeters(),
                    validateMetersResult.getExcessMeters(),
                    validateMetersResult.getMissingMeters(),
                    request.isRemoveExcess() ? validateMetersResult.getExcessMeters() : emptyList());
        }

        List<Integer> installedGroupsIds = extractGroupIds(toInstall);
        List<Integer> modifiedGroupsIds = extractGroupIds(toModify);
        List<Integer> removedGroupsIds = extractGroupIds(toRemove);

        GroupSyncEntry groupEntry = GroupSyncEntry.builder()
                .proper(validateGroupsResult.getProperGroups())
                .misconfigured(validateGroupsResult.getMisconfiguredGroups())
                .missing(validateGroupsResult.getMissingGroups())
                .excess(validateGroupsResult.getExcessGroups())
                .installed(mapToGroupEntryList(installedGroupsIds, validateGroupsResult.getMissingGroups()))
                .modified(mapToGroupEntryList(modifiedGroupsIds, validateGroupsResult.getMisconfiguredGroups()))
                .removed(mapToGroupEntryList(removedGroupsIds, validateGroupsResult.getExcessGroups()))
                .build();

        LogicalPortsSyncEntry logicalPortsEntry = LogicalPortsSyncEntry.builder()
                .proper(validateLogicalPortsResult.getProperLogicalPorts())
                .misconfigured(validateLogicalPortsResult.getMisconfiguredLogicalPorts())
                .excess(validateLogicalPortsResult.getExcessLogicalPorts())
                .missing(validateLogicalPortsResult.getMissingLogicalPorts())
                .installed(validateLogicalPortsResult.getMissingLogicalPorts())
                .removed(validateLogicalPortsResult.getExcessLogicalPorts())
                .error(validateLogicalPortsResult.getErrorMessage())
                .build();

        SwitchSyncResponse response = new SwitchSyncResponse(switchId, rulesEntry, metersEntry, groupEntry,
                logicalPortsEntry);
        InfoMessage message = new InfoMessage(response, System.currentTimeMillis(), key);

        carrier.cancelTimeoutCallback(key);
        carrier.response(key, message);
    }

    private List<Integer> extractGroupIds(List<SpeakerData> source) {
        return source.stream()
                .filter(speakerData -> speakerData instanceof GroupSpeakerData)
                .map(speakerData -> (GroupSpeakerData) speakerData)
                .map(groupSpeakerData -> (int) groupSpeakerData.getGroupId().getValue())
                .collect(Collectors.toList());
    }

    private List<GroupInfoEntry> mapToGroupEntryList(List<Integer> groupIds,
                                                     List<GroupInfoEntry> groupEntries) {
        return groupEntries.stream()
                .filter(entry -> groupIds.contains(entry.getGroupId()))
                .collect(Collectors.toList());
    }

    protected void finishedWithError(SwitchSyncState from, SwitchSyncState to,
                                     SwitchSyncEvent event, Object context) {
        ErrorData payload = (ErrorData) context;
        ErrorMessage message = new ErrorMessage(payload, System.currentTimeMillis(), key);
        log.error(ERROR_LOG_MESSAGE, key, message.getData().getErrorMessage());

        carrier.cancelTimeoutCallback(key);
        carrier.response(key, message);
    }

    private void sendException(Exception e) {
        log.error("Error in switch {} sync", switchId, e);
        ErrorData errorData = new SwitchSyncErrorData(switchId, ErrorType.INTERNAL_ERROR, e.getMessage(),
                "Error in SwitchSyncFsm");
        fire(ERROR, errorData);
    }

    public enum SwitchSyncState {
        INITIALIZED,
        COMPUTE_MISSING_RULES,
        COMPUTE_MISCONFIGURED_RULES,
        COMPUTE_EXCESS_RULES,
        COMPUTE_EXCESS_METERS,
        COMPUTE_MISCONFIGURED_METERS,
        COMPUTE_MISSING_METERS,
        COMPUTE_GROUP_MIRROR_CONFIGS,
        COMPUTE_LOGICAL_PORTS_COMMANDS,
        SEND_REMOVE_COMMANDS,
        SEND_MODIFY_COMMANDS,
        SEND_INSTALL_COMMANDS,
        LOGICAL_PORTS_COMMANDS_SEND,
        FINISHED_WITH_ERROR,
        FINISHED
    }

    public enum SwitchSyncEvent {
        NEXT,
        COMMANDS_PROCESSED,
        LOGICAL_PORT_INSTALLED,
        LOGICAL_PORT_REMOVED,
        TIMEOUT,
        ERROR
    }
}
