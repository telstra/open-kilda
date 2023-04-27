/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.validation;

import static java.lang.String.format;
import static org.openkilda.wfm.topology.flowhs.fsm.haflow.validation.HaFlowValidationFsm.Event.DATA_READY;
import static org.openkilda.wfm.topology.flowhs.fsm.haflow.validation.HaFlowValidationFsm.Event.ERROR;
import static org.openkilda.wfm.topology.flowhs.fsm.haflow.validation.HaFlowValidationFsm.Event.GROUPS_RECEIVED;
import static org.openkilda.wfm.topology.flowhs.fsm.haflow.validation.HaFlowValidationFsm.Event.METERS_RECEIVED;
import static org.openkilda.wfm.topology.flowhs.fsm.haflow.validation.HaFlowValidationFsm.Event.NEXT;
import static org.openkilda.wfm.topology.flowhs.fsm.haflow.validation.HaFlowValidationFsm.Event.RULES_RECEIVED;
import static org.openkilda.wfm.topology.flowhs.fsm.haflow.validation.HaFlowValidationFsm.State.FINISHED;
import static org.openkilda.wfm.topology.flowhs.fsm.haflow.validation.HaFlowValidationFsm.State.FINISHED_WITH_ERROR;
import static org.openkilda.wfm.topology.flowhs.fsm.haflow.validation.HaFlowValidationFsm.State.INITIALIZED;
import static org.openkilda.wfm.topology.flowhs.fsm.haflow.validation.HaFlowValidationFsm.State.RECEIVE_DATA;
import static org.openkilda.wfm.topology.flowhs.fsm.haflow.validation.HaFlowValidationFsm.State.VALIDATE_FLOW;

import org.openkilda.messaging.command.haflow.HaFlowValidationResponse;
import org.openkilda.messaging.command.switches.DumpGroupsForFlowHsRequest;
import org.openkilda.messaging.command.switches.DumpMetersForFlowHsRequest;
import org.openkilda.messaging.command.switches.DumpRulesForFlowHsRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowDumpResponse;
import org.openkilda.messaging.info.group.GroupDumpResponse;
import org.openkilda.messaging.info.meter.MeterDumpResponse;
import org.openkilda.model.HaFlow;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.error.IllegalFlowStateException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.share.metrics.MeterRegistryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.common.NbTrackableFlowProcessingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.validation.HaFlowValidationFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.validation.HaFlowValidationFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowValidationEventListener;
import org.openkilda.wfm.topology.flowhs.service.FlowValidationHubCarrier;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.LongTaskTimer.Sample;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class HaFlowValidationFsm extends NbTrackableFlowProcessingFsm<HaFlowValidationFsm, State, Event, Object,
        FlowValidationHubCarrier, FlowValidationEventListener> {

    private static final String VALIDATION_ERROR_DESC = "The HaFlow validation operation in the HaFlowValidationFsm";
    private static final String FINISHED_WITH_ERROR_METHOD_NAME = "finishedWithError";
    private static final String FINISHED_METHOD_NAME = "finished";
    @Getter
    private final String flowId;
    private final HaFlowValidationService service;
    private int awaitingRulesSize;
    private int awaitingMetersSize;
    private int awaitingGroupsSize;
    private final List<FlowDumpResponse> receivedRules = new ArrayList<>();
    private final List<MeterDumpResponse> receivedMeters = new ArrayList<>();
    private final List<GroupDumpResponse> receivedGroups = new ArrayList<>();
    private final Set<SwitchId> switchIdSet = new HashSet<>();
    private HaFlowValidationResponse response;

    public HaFlowValidationFsm(@NonNull CommandContext commandContext, @NonNull FlowValidationHubCarrier carrier,
                               @NonNull String flowId, @NonNull PersistenceManager persistenceManager,
                               @NonNull Collection<FlowValidationEventListener> eventListeners,
                               @NonNull RuleManager ruleManager) {
        super(NEXT, ERROR, commandContext, carrier, eventListeners);
        this.flowId = flowId;

        service = new HaFlowValidationService(persistenceManager, ruleManager);
    }

    public void receiveData(State from, State to,
                            Event event, Object context) {
        HaFlow haFlow;
        try {
            haFlow = service.findValidHaFlowById(flowId);
        } catch (FlowNotFoundException e) {
            log.error("The ha-flow {} has not been found when sending commands to the SpeakerWorkerBolt", flowId, e);
            sendException(e.getMessage(), "Receiving rules operation in HaFlowValidationFsm", ErrorType.NOT_FOUND);
            return;
        } catch (IllegalFlowStateException e) {
            log.error("Could not validate the ha-flow: the ha-flow {} is in DOWN state", flowId, e);
            sendException("Could not validate the ha-flow",
                    format("Could not validate the ha-flow: ha-flow %s is in DOWN state", flowId),
                    ErrorType.UNPROCESSABLE_REQUEST);
            return;
        }

        switchIdSet.addAll(extractSwitchIdSet(haFlow));

        awaitingRulesSize = switchIdSet.size();
        log.debug("Send commands to get the rules on the switches");
        switchIdSet.forEach(switchId ->
                getCarrier().sendSpeakerRequest(flowId, new DumpRulesForFlowHsRequest(switchId)));

        Set<SwitchId> meterSwitchIds = new HashSet<>(haFlow.getEndpointSwitchIds());
        meterSwitchIds.add(haFlow.getForwardPath().getYPointSwitchId());
        Optional.ofNullable(haFlow.getProtectedForwardPath()).ifPresent(e -> meterSwitchIds.add(e.getYPointSwitchId()));

        log.debug("Send commands to get the meters on the termination switches");
        awaitingMetersSize = meterSwitchIds.size();
        meterSwitchIds.forEach(switchId -> {
            getCarrier().sendSpeakerRequest(flowId, new DumpMetersForFlowHsRequest(switchId));
        });

        log.debug("Send commands to get the groups on the termination switches");
        awaitingGroupsSize = haFlow.getProtectedForwardPath() != null ? 2 : 1;

        getCarrier().sendSpeakerRequest(flowId,
                new DumpGroupsForFlowHsRequest(haFlow.getForwardPath().getYPointSwitchId()));
        Optional.ofNullable(haFlow.getProtectedForwardPath()).ifPresent(protectedFlowPath -> {
            getCarrier().sendSpeakerRequest(flowId,
                    new DumpGroupsForFlowHsRequest(protectedFlowPath.getYPointSwitchId()));
        });
    }

    public void receivedRules(State from, State to,
                              Event event, Object context) {
        FlowDumpResponse flowDumpResponse = (FlowDumpResponse) context;
        log.info("The Switch rules received for the switch {}",
                Optional.ofNullable(flowDumpResponse.getSwitchId().toString()).orElse("No switch Id"));
        receivedRules.add(flowDumpResponse);
        awaitingRulesSize--;
        checkOfCompleteDataCollection();
    }

    public void receivedMeters(State from, State to,
                               Event event, Object context) {
        MeterDumpResponse meterDumpResponse = (MeterDumpResponse) context;
        log.info("The Switch meters received for the switch {}",
                Optional.ofNullable(meterDumpResponse.getSwitchId().toString()).orElse("No switch Id"));
        receivedMeters.add(meterDumpResponse);
        awaitingMetersSize--;
        checkOfCompleteDataCollection();
    }

    public void receivedGroups(State from, State to,
                               Event event, Object context) {
        GroupDumpResponse groupDumpResponse = (GroupDumpResponse) context;
        log.info("The Switch groups received for the switch {}",
                Optional.ofNullable(groupDumpResponse.getSwitchId().toString()).orElse("No switch Id"));
        receivedGroups.add(groupDumpResponse);
        awaitingGroupsSize--;
        checkOfCompleteDataCollection();
    }

    private void checkOfCompleteDataCollection() {
        if (awaitingRulesSize == 0 && awaitingMetersSize == 0 && awaitingGroupsSize == 0) {
            fire(DATA_READY);
        }
    }

    public void validateFlow(State from, State to, Event event, Object context) {
        try {
            response = service.validateFlow(flowId, receivedRules, receivedMeters, receivedGroups, switchIdSet);
        } catch (FlowNotFoundException e) {
            log.error("The ha-flow {} not found during the ha-flow validation", flowId, e);
            sendException(e.getMessage(), VALIDATION_ERROR_DESC, ErrorType.NOT_FOUND);
        } catch (SwitchNotFoundException e) {
            log.error(e.getMessage(), e);
            sendException(e.getMessage(), VALIDATION_ERROR_DESC, ErrorType.NOT_FOUND);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            sendException(e.getMessage(), VALIDATION_ERROR_DESC, ErrorType.INTERNAL_ERROR);
        }
    }

    public void finished(State from, State to, Event event, Object context) {
        log.info("FSM finished work");

        CommandContext commandContext = getCommandContext();
        InfoMessage message = new InfoMessage(response, commandContext.getCreateTime(),
                commandContext.getCorrelationId());
        getCarrier().sendNorthboundResponse(message);
    }

    public void finishedWithError(State from, State to, Event event, Object context) {
        ErrorData data = (ErrorData) context;
        log.error("Message: {}", data.getErrorMessage());
        sendNorthboundError(data.getErrorType(),
                format("Could not validate the ha-flow: %s", data.getErrorMessage()), data.getErrorDescription());
    }

    private Set<SwitchId> extractSwitchIdSet(HaFlow haFlow) {
        Set<SwitchId> switchIds = new HashSet<>();
        haFlow.getPaths().forEach(path -> {
            path.getSubPaths().forEach(subPath -> {
                switchIds.add(subPath.getSrcSwitchId());
                switchIds.add(subPath.getDestSwitchId());
                subPath.getSegments().forEach(segment -> {
                    switchIds.add(segment.getSrcSwitchId());
                    switchIds.add(segment.getDestSwitchId());
                });
            });
        });
        return switchIds;
    }

    private void sendException(String message, String description, ErrorType errorType) {
        ErrorData errorData = new ErrorData(errorType, message, description);
        fire(ERROR, errorData);
    }

    public static class Factory {
        private final StateMachineBuilder<HaFlowValidationFsm, State, Event, Object> builder;
        private final FlowValidationHubCarrier carrier;
        private final PersistenceManager persistenceManager;
        private final RuleManager ruleManager;

        public Factory(@NonNull FlowValidationHubCarrier carrier, @NonNull PersistenceManager persistenceManager,
                       RuleManager ruleManager) {
            this.carrier = carrier;
            this.persistenceManager = persistenceManager;
            this.ruleManager = ruleManager;

            builder = StateMachineBuilderFactory.create(
                    HaFlowValidationFsm.class,
                    State.class,
                    Event.class,
                    Object.class,
                    CommandContext.class,
                    FlowValidationHubCarrier.class,
                    String.class,
                    PersistenceManager.class,
                    Collection.class,
                    RuleManager.class);

            builder.transition().from(INITIALIZED).to(RECEIVE_DATA).on(NEXT)
                    .callMethod("receiveData");
            builder.internalTransition().within(RECEIVE_DATA).on(RULES_RECEIVED)
                    .callMethod("receivedRules");
            builder.internalTransition().within(RECEIVE_DATA).on(METERS_RECEIVED)
                    .callMethod("receivedMeters");
            builder.internalTransition().within(RECEIVE_DATA).on(GROUPS_RECEIVED)
                    .callMethod("receivedGroups");

            builder.transition().from(RECEIVE_DATA).to(FINISHED_WITH_ERROR).on(ERROR)
                    .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
            builder.transition().from(RECEIVE_DATA).to(VALIDATE_FLOW).on(DATA_READY)
                    .callMethod("validateFlow");

            builder.transition().from(VALIDATE_FLOW).to(FINISHED_WITH_ERROR).on(ERROR)
                    .callMethod(FINISHED_WITH_ERROR_METHOD_NAME);
            builder.transition().from(VALIDATE_FLOW).to(FINISHED).on(NEXT).callMethod(FINISHED_METHOD_NAME);

            builder.defineFinalState(FINISHED);
            builder.defineFinalState(FINISHED_WITH_ERROR);
        }

        public HaFlowValidationFsm newInstance(@NonNull String flowId, @NonNull CommandContext commandContext) {
            HaFlowValidationFsm fsm = builder.newStateMachine(INITIALIZED, commandContext, carrier, flowId,
                    persistenceManager, ruleManager);

            fsm.addTransitionCompleteListener(event ->
                    log.debug("The HaFlowValidationFsm, transition to {} on {}",
                            event.getTargetState(), event.getCause()));


            MeterRegistryHolder.getRegistry().ifPresent(registry -> {
                Sample sample = LongTaskTimer.builder("fsm.active_execution")
                        .register(registry)
                        .start();
                fsm.addTerminateListener(e -> {
                    long duration = sample.stop();
                    if (fsm.getCurrentState() == FINISHED) {
                        registry.timer("fsm.execution.success")
                                .record(duration, TimeUnit.NANOSECONDS);
                    } else if (fsm.getCurrentState() == FINISHED_WITH_ERROR) {
                        registry.timer("fsm.execution.failed")
                                .record(duration, TimeUnit.NANOSECONDS);
                    }
                });
            });
            return fsm;
        }
    }

    public enum State {
        INITIALIZED,
        RECEIVE_DATA,
        VALIDATE_FLOW,
        FINISHED_WITH_ERROR,
        FINISHED
    }

    public enum Event {
        NEXT,
        RULES_RECEIVED,
        METERS_RECEIVED,
        GROUPS_RECEIVED,
        DATA_READY,
        ERROR
    }
}
