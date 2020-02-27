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

import static java.lang.String.format;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.EXPECTED_DEFAULT_METERS_RECEIVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.EXPECTED_DEFAULT_RULES_RECEIVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.METERS_RECEIVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.METERS_UNSUPPORTED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.NEXT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.RULES_RECEIVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent.TIMEOUT;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState.FINISHED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState.FINISHED_WITH_ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState.INITIALIZED;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState.RECEIVE_DATA;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState.VALIDATE_METERS;
import static org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState.VALIDATE_RULES;

import org.openkilda.messaging.command.switches.DumpMetersForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.DumpRulesForSwitchManagerRequest;
import org.openkilda.messaging.command.switches.GetExpectedDefaultMetersRequest;
import org.openkilda.messaging.command.switches.GetExpectedDefaultRulesRequest;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.meter.MeterEntry;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.switches.SwitchValidationResponse;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Isl;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.SwitchValidateFsm.SwitchValidateState;
import org.openkilda.wfm.topology.switchmanager.mappers.ValidationMapper;
import org.openkilda.wfm.topology.switchmanager.model.SwitchValidationContext;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;
import org.openkilda.wfm.topology.switchmanager.service.ValidationService;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// FIXME - connected-devices
@Slf4j
public class SwitchValidateFsm
        extends AbstractBaseFsm<SwitchValidateFsm, SwitchValidateState, SwitchValidateEvent, Object> {

    private static final String FINISHED_WITH_ERROR_METHOD_NAME = "finishedWithError";
    private static final String FINISHED_METHOD_NAME = "finished";
    private static final String ERROR_LOG_MESSAGE = "Key: {}, message: {}";

    private final CommandContext commandContext;
    private final String key;
    private final SwitchValidateRequest request;
    private final SwitchManagerCarrier carrier;
    private final ValidationService validationService;

    private boolean switchExists;

    private SwitchProperties switchProperties;
    private List<Integer> islPorts;
    private List<Integer> flowPorts;
    private Set<Integer> flowLldpPorts;
    private Set<Integer> flowArpPorts;
    private boolean hasMultiTableFlows;

    private SwitchValidationContext validationContext;
    private Set<ExternalResources> pendingRequests = new HashSet<>();

    public SwitchValidateFsm(
            CommandContext commandContext, SwitchManagerCarrier carrier, String key, SwitchValidateRequest request,
            ValidationService validationService, RepositoryFactory repositoryFactory) {
        this.commandContext = commandContext;
        this.carrier = carrier;
        this.key = key;
        this.request = request;
        this.validationService = validationService;
        this.flowPorts = new ArrayList<>();
        this.flowLldpPorts = new HashSet<>();
        this.flowArpPorts = new HashSet<>();

        SwitchId switchId = request.getSwitchId();
        this.validationContext = SwitchValidationContext.builder(switchId).build();

        // FIXME
        SwitchRepository switchRepository = repositoryFactory.createSwitchRepository();
        this.switchExists = switchRepository.exists(switchId);

        SwitchPropertiesRepository switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
        this.switchProperties = switchPropertiesRepository.findBySwitchId(switchId).orElse(null);
        boolean switchLldp = switchProperties != null && switchProperties.isSwitchLldp();
        boolean switchArp = switchProperties != null && switchProperties.isSwitchArp();

        FlowPathRepository flowPathRepository = repositoryFactory.createFlowPathRepository();
        FlowRepository flowRepository = repositoryFactory.createFlowRepository();
        Collection<FlowPath> flowPaths = flowPathRepository.findBySrcSwitch(switchId);
        for (FlowPath flowPath : flowPaths) {
            if (flowPath.isForward()) {
                if (flowPath.getFlow().isSrcWithMultiTable()) {
                    flowPorts.add(flowPath.getFlow().getSrcPort());
                }
                if (flowPath.getFlow().getDetectConnectedDevices().isSrcLldp() || switchLldp) {
                    flowLldpPorts.add(flowPath.getFlow().getSrcPort());
                }
                if (flowPath.getFlow().getDetectConnectedDevices().isSrcArp() || switchArp) {
                    flowArpPorts.add(flowPath.getFlow().getSrcPort());
                }
            } else {
                if (flowPath.getFlow().isDestWithMultiTable()) {
                    flowPorts.add(flowPath.getFlow().getDestPort());
                }
                if (flowPath.getFlow().getDetectConnectedDevices().isDstLldp() || switchLldp) {
                    flowLldpPorts.add(flowPath.getFlow().getDestPort());
                }
                if (flowPath.getFlow().getDetectConnectedDevices().isDstArp() || switchArp) {
                    flowArpPorts.add(flowPath.getFlow().getDestPort());
                }
            }
        }

        hasMultiTableFlows = !flowPathRepository.findBySegmentSwitchWithMultiTable(switchId, true).isEmpty()
                || !flowRepository.findByEndpointSwitchWithMultiTableSupport(switchId).isEmpty();

        IslRepository islRepository = repositoryFactory.createIslRepository();
        this.islPorts = islRepository.findBySrcSwitch(switchId).stream()
                .map(Isl::getSrcPort)
                .collect(Collectors.toList());
    }

    /**
     * FSM builder.
     */
    public static StateMachineBuilder<SwitchValidateFsm, SwitchValidateState,
            SwitchValidateEvent, Object> builder() {
        StateMachineBuilder<SwitchValidateFsm, SwitchValidateState, SwitchValidateEvent, Object> builder =
                StateMachineBuilderFactory.create(
                        SwitchValidateFsm.class,
                        SwitchValidateState.class,
                        SwitchValidateEvent.class,
                        Object.class,
                        CommandContext.class,
                        SwitchManagerCarrier.class,
                        String.class,
                        SwitchValidateRequest.class,
                        ValidationService.class,
                        RepositoryFactory.class);

        builder.onEntry(INITIALIZED).callMethod("initialized");
        builder.externalTransition().from(INITIALIZED).to(RECEIVE_DATA).on(NEXT)
                .callMethod("receiveData");
        builder.externalTransition().from(INITIALIZED).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);

        builder.internalTransition().within(RECEIVE_DATA).on(RULES_RECEIVED).callMethod("rulesReceived");
        builder.internalTransition().within(RECEIVE_DATA).on(METERS_RECEIVED).callMethod("metersReceived");
        builder.internalTransition().within(RECEIVE_DATA).on(EXPECTED_DEFAULT_RULES_RECEIVED)
                .callMethod("expectedDefaultRulesReceived");
        builder.internalTransition().within(RECEIVE_DATA).on(EXPECTED_DEFAULT_METERS_RECEIVED)
                .callMethod("expectedDefaultMetersReceived");
        builder.internalTransition().within(RECEIVE_DATA).on(METERS_UNSUPPORTED)
                .callMethod("metersUnsupported");

        builder.externalTransition().from(RECEIVE_DATA).to(FINISHED_WITH_ERROR).on(TIMEOUT)
                .callMethod("receivingDataFailedByTimeout");
        builder.externalTransition().from(RECEIVE_DATA).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
        builder.externalTransition().from(RECEIVE_DATA).to(VALIDATE_RULES).on(NEXT)
                .callMethod("validateRules");

        builder.externalTransition().from(VALIDATE_RULES).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
        builder.externalTransition().from(VALIDATE_RULES).to(VALIDATE_METERS).on(NEXT)
                .callMethod("validateMeters");

        builder.externalTransition().from(VALIDATE_METERS).to(FINISHED_WITH_ERROR).on(ERROR)
                .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
        builder.externalTransition().from(VALIDATE_METERS).to(FINISHED).on(NEXT)
                .callMethod(FINISHED_METHOD_NAME);

        return builder;
    }

    public String getKey() {
        return key;
    }

    protected void initialized(SwitchValidateState from, SwitchValidateState to,
                               SwitchValidateEvent event, Object context) {
        SwitchId switchId = validationContext.getSwitchId();
        if (!switchExists) {
            sendException(format("Switch '%s' not found", switchId), ErrorType.NOT_FOUND);
            return;
        }
        if (switchProperties == null) {
            sendException(format("Switch properties not found for switch '%s'", switchId), ErrorType.NOT_FOUND);
            return; // TODO(surabujin): em???... and what?..
        }
        log.info("The switch validate process for {} has been started (key={})", switchId, key);
    }

    protected void receiveData(SwitchValidateState from, SwitchValidateState to,
                               SwitchValidateEvent event, Object context) {
        SwitchId switchId = validationContext.getSwitchId();
        log.info("Sending requests to get switch rules and meters (switch={}, key={})", switchId, key);

        carrier.sendCommandToSpeaker(key, new DumpRulesForSwitchManagerRequest(switchId));
        pendingRequests.add(ExternalResources.ACTUAL_OF_FLOWS);

        boolean multiTable = switchProperties.isMultiTable() || hasMultiTableFlows;
        boolean switchLldp = switchProperties.isSwitchLldp();
        boolean switchArp = switchProperties.isSwitchArp();

        carrier.sendCommandToSpeaker(key, new GetExpectedDefaultRulesRequest(switchId, multiTable, switchLldp,
                switchArp, islPorts, flowPorts, flowLldpPorts, flowArpPorts));
        pendingRequests.add(ExternalResources.EXPECTED_DEFAULT_OF_FLOWS);

        if (request.isProcessMeters()) {
            carrier.sendCommandToSpeaker(key, new DumpMetersForSwitchManagerRequest(switchId));
            carrier.sendCommandToSpeaker(key, new GetExpectedDefaultMetersRequest(
                    switchId, multiTable, switchLldp, switchArp));
        } else {
            // FIXME
            expectedDefaultMetersEntries = emptyList();
            pendingRequests.add(ExternalResources.ACTUAL_METERS);
        }
    }

    protected void rulesReceived(SwitchValidateState from, SwitchValidateState to,
                                 SwitchValidateEvent event, Object context) {
        log.info("Switch rules received (switch={}, key={})", validationContext.getSwitchId(), key);

        @SuppressWarnings("unchecked")
        List<FlowEntry> flowEntries = (List<FlowEntry>) context;
        validationContext = validationContext.toBuilder()
                .actualOfFlows(flowEntries)
                .build();
        pendingRequests.remove(ExternalResources.ACTUAL_OF_FLOWS);

        fireNextIfAllResourcesReceived();
    }

    protected void expectedDefaultRulesReceived(SwitchValidateState from, SwitchValidateState to,
                                                SwitchValidateEvent event, Object context) {
        log.info("Switch expected default rules received (switch={}, key={})", validationContext.getSwitchId(), key);

        @SuppressWarnings("unchecked")
        List<FlowEntry> flowEntries = (List<FlowEntry>) context;
        validationContext = validationContext.toBuilder()
                .expectedDefaultOfFlows(flowEntries)
                .build();
        pendingRequests.remove(ExternalResources.EXPECTED_DEFAULT_OF_FLOWS);

        fireNextIfAllResourcesReceived();
    }

    protected void metersReceived(SwitchValidateState from, SwitchValidateState to,
                                  SwitchValidateEvent event, Object context) {
        log.info("Switch meters received (switch={}, key={})", validationContext.getSwitchId(), key);

        @SuppressWarnings("unchecked")
        List<MeterEntry> meterEntries = (List<MeterEntry>) context;
        validationContext = validationContext.toBuilder()
                .actualMeters(meterEntries)
                .build();
        pendingRequests.remove(ExternalResources.ACTUAL_METERS);

        fireNextIfAllResourcesReceived();
    }

    // FIXME
    protected void expectedDefaultMetersReceived(SwitchValidateState from, SwitchValidateState to,
                                                 SwitchValidateEvent event, Object context) {
        log.info("Key: {}, switch expected default meters received", key);
        this.expectedDefaultMetersEntries = (List<MeterEntry>) context;
        checkAllDataReceived();
    }

    protected void metersUnsupported(SwitchValidateState from, SwitchValidateState to,
                                     SwitchValidateEvent event, Object context) {
        log.info("Switch meters unsupported (switch={}, key={})", validationContext.getSwitchId(), key);
        pendingRequests.remove(ExternalResources.ACTUAL_METERS);
        fireNextIfAllResourcesReceived();
    }

    private void fireNextIfAllResourcesReceived() {
        if (pendingRequests.isEmpty()) {
            fire(NEXT);
        }
    }

    protected void receivingDataFailedByTimeout(SwitchValidateState from, SwitchValidateState to,
                                                SwitchValidateEvent event, Object context) {
        ErrorData errorData = new ErrorData(ErrorType.OPERATION_TIMED_OUT, "Receiving data failed by timeout",
                "Error when receive switch data");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);

        log.warn(ERROR_LOG_MESSAGE, key, errorData.getErrorMessage());
        carrier.response(key, errorMessage);
    }

    protected void validateRules(SwitchValidateState from, SwitchValidateState to,
                                 SwitchValidateEvent event, Object context) {
        log.info("Validate rules (switch={}, key={})", validationContext.getSwitchId(), key);
        try {
            validationContext = validationService.validateRules(commandContext, validationContext);
        } catch (Exception e) {
            sendException(e.getMessage());
        }
    }

    protected void validateMeters(SwitchValidateState from, SwitchValidateState to,
                                  SwitchValidateEvent event, Object context) {
        if (! request.isProcessMeters() || validationContext.getActualMeters() == null) {
            return;
        }

        try {
            log.info("Key: {}, validate meters", key);
            validationContext = validationService.validateMeters(validationContext);
        } catch (Exception e) {
            sendException(e.getMessage());
        }
    }

    protected void finished(SwitchValidateState from, SwitchValidateState to,
                            SwitchValidateEvent event, Object context) {
        if (request.isPerformSync()) {
            carrier.runSwitchSync(key, request, validationContext);
        } else {
            SwitchValidationResponse response = ValidationMapper.INSTANCE.toSwitchResponse(validationContext);
            InfoMessage message = new InfoMessage(response, System.currentTimeMillis(), key);

            carrier.cancelTimeoutCallback(key);
            carrier.response(key, message);
        }
    }

    protected void finishedWithError(SwitchValidateState from, SwitchValidateState to,
                                     SwitchValidateEvent event, Object context) {
        ErrorMessage sourceError = (ErrorMessage) context;
        ErrorMessage message = new ErrorMessage(sourceError.getData(), System.currentTimeMillis(), key);

        log.error(ERROR_LOG_MESSAGE, key, message.getData().getErrorMessage());

        carrier.cancelTimeoutCallback(key);
        carrier.response(key, message);
    }

    private void sendException(String exceptionMessage, ErrorType errorType) {
        ErrorData errorData = new ErrorData(errorType, exceptionMessage,
                "Error in SwitchValidateFsm");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);
        fire(ERROR, errorMessage);
    }

    private void sendException(String exceptionMessage) {
        sendException(exceptionMessage, ErrorType.INTERNAL_ERROR);
    }

    public enum SwitchValidateState {
        INITIALIZED,
        RECEIVE_DATA,
        VALIDATE_RULES,
        VALIDATE_METERS,
        FINISHED_WITH_ERROR,
        FINISHED
    }

    public enum SwitchValidateEvent {
        NEXT,
        RULES_RECEIVED,
        METERS_RECEIVED,
        EXPECTED_DEFAULT_RULES_RECEIVED,
        EXPECTED_DEFAULT_METERS_RECEIVED,
        METERS_UNSUPPORTED,
        TIMEOUT,
        ERROR
    }

    private enum ExternalResources {
        ACTUAL_OF_FLOWS,
        ACTUAL_METERS,
        EXPECTED_DEFAULT_OF_FLOWS
    }
}
