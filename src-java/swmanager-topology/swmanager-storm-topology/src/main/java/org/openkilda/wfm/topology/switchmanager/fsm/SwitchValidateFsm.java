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

import static org.openkilda.model.SwitchFeature.LAG;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.GROUPS_RECEIVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.LOGICAL_PORTS_RECEIVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.METERS_RECEIVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.METERS_UNSUPPORTED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.NEXT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.READY;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.RULES_RECEIVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState.FINISHED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState.FINISHED_WITH_ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState.VALIDATE;

import org.openkilda.messaging.command.grpc.DumpLogicalPortsRequest;
import org.openkilda.messaging.command.switches.DumpGroupsForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DumpMetersForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DumpRulesForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.switches.SwitchValidationResponse;
import org.openkilda.messaging.model.grpc.LogicalPort;
import org.openkilda.model.FlowPath;
import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.topology.switchmanager.error.SwitchManagerException;
import org.openkilda.wfm.topology.switchmanager.error.SwitchNotFoundException;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateContext;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState;
import org.openkilda.wfm.topology.switchmanager.mappers.FlowEntryConverter;
import org.openkilda.wfm.topology.switchmanager.mappers.ValidationMapper;
import org.openkilda.wfm.topology.switchmanager.model.SwitchValidationContext;
import org.openkilda.wfm.topology.switchmanager.model.ValidateGroupsResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateLogicalPortsResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidationResult;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;
import org.openkilda.wfm.topology.switchmanager.service.ValidationService;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.StateMachineStatus;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class SwitchValidateFsm extends AbstractStateMachine<
        SwitchValidateFsm, SwitchValidateState, SwitchValidateEvent, SwitchValidateContext> {

    private final PersistenceManager persistenceManager;
    private final SwitchRepository switchRepository;
    private final FlowPathRepository flowPathRepository;

    private final String key;
    private final SwitchValidateRequest request;
    private final SwitchManagerCarrier carrier;
    private final ValidationService validationService;
    private final RuleManager ruleManager;

    private SwitchValidationContext validationContext;
    private final Set<ExternalResources> pendingRequests = new HashSet<>();

    public SwitchValidateFsm(
            SwitchManagerCarrier carrier, String key, SwitchValidateRequest request,
            ValidationService validationService, RuleManager ruleManager,
            PersistenceManager persistenceManager) {
        this.carrier = carrier;
        this.key = key;
        this.request = request;
        this.validationService = validationService;
        this.ruleManager = ruleManager;
        this.persistenceManager = persistenceManager;

        SwitchId switchId = request.getSwitchId();
        this.validationContext = SwitchValidationContext.builder(switchId).build();

        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        switchRepository = repositoryFactory.createSwitchRepository();
        flowPathRepository = repositoryFactory.createFlowPathRepository();
    }

    /**
     * FSM builder.
     */
    public static StateMachineBuilder<SwitchValidateFsm, SwitchValidateState,
            SwitchValidateEvent, SwitchValidateContext> builder() {
        StateMachineBuilder<SwitchValidateFsm, SwitchValidateState, SwitchValidateEvent, SwitchValidateContext>
                builder = StateMachineBuilderFactory.create(
                SwitchValidateFsm.class,
                SwitchValidateState.class,
                SwitchValidateEvent.class,
                SwitchValidateContext.class,
                SwitchManagerCarrier.class,
                String.class,
                SwitchValidateRequest.class,
                ValidationService.class,
                RuleManager.class,
                PersistenceManager.class);

        // START
        builder.transition().from(SwitchValidateState.START).to(SwitchValidateState.COLLECT_DATA).on(NEXT);
        builder.transition().from(SwitchValidateState.START).to(FINISHED_WITH_ERROR).on(ERROR);

        // COLLECT_DATA
        builder.onEntry(SwitchValidateState.COLLECT_DATA).callMethod("emitRequests");
        builder.internalTransition().within(SwitchValidateState.COLLECT_DATA)
                .on(RULES_RECEIVED).callMethod("rulesReceived");
        builder.internalTransition().within(SwitchValidateState.COLLECT_DATA)
                .on(GROUPS_RECEIVED).callMethod("groupsReceived");
        builder.internalTransition().within(SwitchValidateState.COLLECT_DATA)
                .on(LOGICAL_PORTS_RECEIVED).callMethod("logicalPortsReceived");
        builder.internalTransition().within(SwitchValidateState.COLLECT_DATA)
                .on(METERS_RECEIVED).callMethod("metersReceived");
        builder.internalTransition().within(SwitchValidateState.COLLECT_DATA)
                .on(METERS_UNSUPPORTED).callMethod("metersUnsupported");
        builder.externalTransition().from(SwitchValidateState.COLLECT_DATA).to(VALIDATE).on(READY);
        builder.externalTransition().from(SwitchValidateState.COLLECT_DATA).to(FINISHED_WITH_ERROR).on(ERROR);

        // VALIDATE
        builder.externalTransition().from(VALIDATE).to(FINISHED).on(NEXT);
        builder.externalTransition().from(VALIDATE).to(FINISHED_WITH_ERROR).on(ERROR);
        builder.onEntry(VALIDATE)
                .callMethod("validateEnter");

        // FINISHED
        builder.onEntry(FINISHED)
                .callMethod("finishedEnter");
        builder.defineFinalState(FINISHED);

        // FINISHED_WITH_ERROR
        builder.onEntry(SwitchValidateState.FINISHED_WITH_ERROR)
                .callMethod("finishedWithErrorEnter");
        builder.defineFinalState(FINISHED_WITH_ERROR);

        return builder;
    }

    public String getKey() {
        return key;
    }

    public void emitRequests(SwitchValidateState from, SwitchValidateState to, SwitchValidateEvent event,
                             SwitchValidateContext context) {
        try {
            SwitchId switchId = getSwitchId();
            log.info("The switch validate process for {} has been started (key={})", switchId, key);

            // FIXME(surabujin): not reliable check - corresponding error from speaker is much more better
            Optional<Switch> sw = switchRepository.findById(switchId);
            if (!sw.isPresent()) {
                throw new SwitchNotFoundException(switchId);
            }

            requestSwitchOfFlows();

            requestSwitchOfGroups();

            if (sw.get().getFeatures().contains(LAG)) {
                // at this moment Kilda validated only LAG logical ports
                Optional<String> ipAddress = sw.map(Switch::getSocketAddress).map(IpSocketAddress::getAddress);
                if (ipAddress.isPresent()) {
                    requestLogicalPorts(ipAddress.get());
                } else {
                    log.warn("Unable to get IP address of switch {} to get LAGs", switchId);
                }
            }

            if (request.isProcessMeters()) {
                requestSwitchMeters();
            }
        } catch (Exception ex) {
            log.error("Failure in emitRequests", ex);
            throw ex;
        }
    }

    protected void rulesReceived(SwitchValidateState from, SwitchValidateState to,
                                 SwitchValidateEvent event, SwitchValidateContext context) {
        log.info("Switch rules received (switch={}, key={})", getSwitchId(), key);

        validationContext = validationContext.toBuilder()
                .actualOfFlows(context.getFlowEntries())
                .build();
        pendingRequests.remove(ExternalResources.ACTUAL_OF_FLOWS);

        fireReadyIfAllResourcesReceived();
    }

    protected void groupsReceived(SwitchValidateState from, SwitchValidateState to,
                                  SwitchValidateEvent event, SwitchValidateContext context) {
        log.info("Switch groups received (switch={}, key={})", getSwitchId(), key);
        validationContext = validationContext.toBuilder()
                .actualGroupEntries(context.getGroupEntries())
                .build();
        pendingRequests.remove(ExternalResources.ACTUAL_OF_GROUPS);

        fireReadyIfAllResourcesReceived();
    }

    protected void logicalPortsReceived(SwitchValidateState from, SwitchValidateState to,
                                        SwitchValidateEvent event, SwitchValidateContext context) {
        log.info("Switch logical ports received (switch={}, key={})", getSwitchId(), key);
        validationContext = validationContext.toBuilder()
                .actualLogicalPortEntries(context.getLogicalPortEntries())
                .build();
        pendingRequests.remove(ExternalResources.ACTUAL_LOGICAL_PORTS);

        fireReadyIfAllResourcesReceived();
    }

    protected void metersReceived(SwitchValidateState from, SwitchValidateState to,
                                  SwitchValidateEvent event, SwitchValidateContext context) {
        log.info("Switch meters received (switch={}, key={})", getSwitchId(), key);

        validationContext = validationContext.toBuilder()
                .actualMeters(context.getMeterEntries())
                .build();
        pendingRequests.remove(ExternalResources.ACTUAL_METERS);

        fireReadyIfAllResourcesReceived();
    }

    protected void metersUnsupported(SwitchValidateState from, SwitchValidateState to,
                                     SwitchValidateEvent event, SwitchValidateContext context) {
        log.info("Switch meters unsupported (switch={}, key={})", getSwitchId(), key);
        pendingRequests.remove(ExternalResources.ACTUAL_METERS);
        fireReadyIfAllResourcesReceived();
    }

    protected void validateEnter(SwitchValidateState from, SwitchValidateState to,
                                 SwitchValidateEvent event, SwitchValidateContext context) {
        SwitchId switchId = getSwitchId();
        Set<PathId> flowPathIds = flowPathRepository.findBySegmentSwitch(switchId).stream()
                .map(FlowPath::getPathId)
                .collect(Collectors.toSet());
        flowPathIds.addAll(flowPathRepository.findByEndpointSwitch(switchId).stream()
                .map(FlowPath::getPathId)
                .collect(Collectors.toSet()));
        PersistenceDataAdapter dataAdapter = PersistenceDataAdapter.builder()
                .persistenceManager(persistenceManager)
                .switchIds(Collections.singleton(switchId))
                .pathIds(flowPathIds)
                .keepMultitableForFlow(true)
                .build();
        List<SpeakerData> expectedEntities = ruleManager.buildRulesForSwitch(switchId, dataAdapter);
        List<FlowSpeakerData> expectedRules = filterSpeakerData(expectedEntities, FlowSpeakerData.class);
        List<MeterSpeakerData> expectedMeters = filterSpeakerData(expectedEntities, MeterSpeakerData.class);
        List<GroupSpeakerData> expectedGroups = filterSpeakerData(expectedEntities, GroupSpeakerData.class);
        validateRules(expectedRules);
        validateMeters(expectedMeters);
        validateGroups(expectedGroups);
        validateLogicalPorts();
    }

    protected void finishedEnter(SwitchValidateState from, SwitchValidateState to,
                                 SwitchValidateEvent event, SwitchValidateContext context) {
        if (request.isPerformSync()) {
            List<FlowEntry> flowEntries = validationContext.getActualOfFlows().stream()
                    .map(FlowEntryConverter.INSTANCE::toFlowEntry)
                    .collect(Collectors.toList());
            ValidationResult results = new ValidationResult(flowEntries,
                    validationContext.getMetersValidationReport() != null,
                    validationContext.getOfFlowsValidationReport(), validationContext.getMetersValidationReport(),
                    validationContext.getValidateGroupsResult(), validationContext.getValidateLogicalPortResult());
            carrier.runSwitchSync(key, request, results);
        } else {
            SwitchValidationResponse response = ValidationMapper.INSTANCE.toSwitchResponse(validationContext);
            InfoMessage message = new InfoMessage(response, System.currentTimeMillis(), key);

            carrier.cancelTimeoutCallback(key);
            carrier.response(key, message);
        }
    }

    protected void finishedWithErrorEnter(SwitchValidateState from, SwitchValidateState to,
                                          SwitchValidateEvent event, SwitchValidateContext context) {
        ErrorType type;
        String description;
        SwitchManagerException error = context.getError();
        if (error != null) {
            log.error("Switch {} (key: {}) validation failed - {}", getSwitchId(), key, error.getMessage());
            type = error.getError();
            description = error.getMessage();
        } else {
            log.error("Switch {} (key: {}) validation failed - timeout", getSwitchId(), key);
            type = ErrorType.OPERATION_TIMED_OUT;
            description = String.format("Switch %s validate/sync operation have timed out", getSwitchId());
        }

        carrier.cancelTimeoutCallback(key);
        carrier.errorResponse(key, type, description, "Error in switch validation");
    }

    // -- private/service methods --

    private void fireReadyIfAllResourcesReceived() {
        if (pendingRequests.isEmpty()) {
            fire(SwitchValidateEvent.READY);
        }
    }

    private void requestSwitchOfFlows() {
        SwitchId switchId = getSwitchId();
        log.info("Sending requests to get switch OF-flows (switch={}, key={})", switchId, key);

        carrier.sendCommandToSpeaker(key, new DumpRulesForSwitchManagerRequest(switchId));
        pendingRequests.add(ExternalResources.ACTUAL_OF_FLOWS);
    }

    private void requestSwitchOfGroups() {
        SwitchId switchId = getSwitchId();
        log.info("Sending requests to get switch OF-groups (switch={}, key={})", switchId, key);

        carrier.sendCommandToSpeaker(key, new DumpGroupsForSwitchManagerRequest(switchId));
        pendingRequests.add(ExternalResources.ACTUAL_OF_GROUPS);
    }

    private void requestLogicalPorts(String ipAddress) {
        SwitchId switchId = getSwitchId();
        log.info("Sending request to get switch logical ports. IP {} (switch={}, key={})", ipAddress, switchId, key);

        carrier.sendCommandToSpeaker(key, new DumpLogicalPortsRequest(ipAddress));
        pendingRequests.add(ExternalResources.ACTUAL_LOGICAL_PORTS);
    }

    private void requestSwitchMeters() {
        SwitchId switchId = getSwitchId();
        log.info("Sending requests to get switch meters (switch={}, key={})", switchId, key);

        carrier.sendCommandToSpeaker(key, new DumpMetersForSwitchManagerRequest(switchId));
        pendingRequests.add(ExternalResources.ACTUAL_METERS);
    }

    private void validateRules(List<FlowSpeakerData> expectedRules) {
        log.info("Validate rules (switch={}, key={})", getSwitchId(), key);
        ValidateRulesResult results = validationService.validateRules(
                getSwitchId(), validationContext.getActualOfFlows(), expectedRules);
        validationContext = validationContext.toBuilder()
                .ofFlowsValidationReport(results)
                .build();
    }

    private void validateMeters(List<MeterSpeakerData> expectedMeters) {
        if (!request.isProcessMeters() || validationContext.getActualMeters() == null) {
            return;
        }

        log.info("Validate meters (switch={}, key={})", getSwitchId(), key);
        ValidateMetersResult results = validationService.validateMeters(
                getSwitchId(), validationContext.getActualMeters(), expectedMeters);
        validationContext = validationContext.toBuilder()
                .metersValidationReport(results)
                .build();
    }

    protected void validateGroups(List<GroupSpeakerData> expectedGroups) {
        log.info("Validate groups (switch={}, key={})", getSwitchId(), key);
        ValidateGroupsResult results = validationService.validateGroups(
                getSwitchId(), validationContext.getActualGroupEntries(), expectedGroups);
        validationContext = validationContext.toBuilder()
                .validateGroupsResult(results)
                .build();
    }

    protected void validateLogicalPorts() {
        if (validationContext.getActualLogicalPortEntries() == null) {
            return;
        }
        log.info("Validate logical ports (switch={}, key={})", getSwitchId(), key);
        ValidateLogicalPortsResult results = validationService.validateLogicalPorts(
                getSwitchId(), validationContext.getActualLogicalPortEntries());
        validationContext = validationContext.toBuilder()
                .validateLogicalPortResult(results)
                .build();
    }

    private <T extends SpeakerData> List<T> filterSpeakerData(List<SpeakerData> speakerData,
                                                              Class<T> clazz) {
        return speakerData.stream()
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .collect(Collectors.toList());
    }

    @Override
    protected void afterTransitionCausedException(
            SwitchValidateState fromState, SwitchValidateState toState, SwitchValidateEvent event,
            SwitchValidateContext context) {
        Throwable exception = getLastException().getTargetException();

        SwitchManagerException error;

        if (exception instanceof SwitchManagerException) {
            error = (SwitchManagerException) exception;
        } else {
            error = new SwitchManagerException(exception);
        }

        setStatus(StateMachineStatus.IDLE);
        fire(ERROR, SwitchValidateContext.builder()
                .error(error)
                .build());
    }

    public SwitchId getSwitchId() {
        return validationContext.getSwitchId();
    }

    public enum SwitchValidateState {
        START,
        COLLECT_DATA,
        VALIDATE,
        FINISHED_WITH_ERROR,
        FINISHED
    }

    public enum SwitchValidateEvent {
        NEXT,
        READY,
        RULES_RECEIVED,
        METERS_RECEIVED,
        GROUPS_RECEIVED,
        LOGICAL_PORTS_RECEIVED,
        METERS_UNSUPPORTED,
        ERROR
    }

    private enum ExternalResources {
        ACTUAL_OF_FLOWS,
        ACTUAL_METERS,
        ACTUAL_OF_GROUPS,
        ACTUAL_LOGICAL_PORTS
    }

    @Value
    @Builder
    public static class SwitchValidateContext {
        List<FlowSpeakerData> flowEntries;
        List<MeterSpeakerData> meterEntries;
        List<GroupSpeakerData> groupEntries;
        List<LogicalPort> logicalPortEntries;
        SwitchManagerException error;
    }
}
