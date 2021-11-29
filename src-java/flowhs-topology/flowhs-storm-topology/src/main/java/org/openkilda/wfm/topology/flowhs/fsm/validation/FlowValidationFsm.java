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

package org.openkilda.wfm.topology.flowhs.fsm.validation;

import static java.lang.String.format;
import static org.openkilda.wfm.topology.flowhs.fsm.validation.FlowValidationFsm.Event.DATA_READY;
import static org.openkilda.wfm.topology.flowhs.fsm.validation.FlowValidationFsm.Event.ERROR;
import static org.openkilda.wfm.topology.flowhs.fsm.validation.FlowValidationFsm.Event.GROUPS_RECEIVED;
import static org.openkilda.wfm.topology.flowhs.fsm.validation.FlowValidationFsm.Event.METERS_RECEIVED;
import static org.openkilda.wfm.topology.flowhs.fsm.validation.FlowValidationFsm.Event.NEXT;
import static org.openkilda.wfm.topology.flowhs.fsm.validation.FlowValidationFsm.Event.RULES_RECEIVED;
import static org.openkilda.wfm.topology.flowhs.fsm.validation.FlowValidationFsm.State.FINISHED;
import static org.openkilda.wfm.topology.flowhs.fsm.validation.FlowValidationFsm.State.FINISHED_WITH_ERROR;
import static org.openkilda.wfm.topology.flowhs.fsm.validation.FlowValidationFsm.State.INITIALIZED;
import static org.openkilda.wfm.topology.flowhs.fsm.validation.FlowValidationFsm.State.RECEIVE_DATA;
import static org.openkilda.wfm.topology.flowhs.fsm.validation.FlowValidationFsm.State.VALIDATE_FLOW;

import org.openkilda.messaging.command.switches.DumpGroupsForFlowHsRequest;
import org.openkilda.messaging.command.switches.DumpMetersForFlowHsRequest;
import org.openkilda.messaging.command.switches.DumpRulesForFlowHsRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.flow.FlowValidationResponse;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.rule.SwitchGroupEntries;
import org.openkilda.model.Flow;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.error.IllegalFlowStateException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.metrics.MeterRegistryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.common.NbTrackableFlowProcessingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.validation.FlowValidationFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.validation.FlowValidationFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowValidationEventListener;
import org.openkilda.wfm.topology.flowhs.service.FlowValidationHubCarrier;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.LongTaskTimer.Sample;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class FlowValidationFsm extends NbTrackableFlowProcessingFsm<FlowValidationFsm, State, Event, Object,
        FlowValidationHubCarrier, FlowValidationEventListener> {
    private static final String FINISHED_WITH_ERROR_METHOD_NAME = "finishedWithError";
    private static final String FINISHED_METHOD_NAME = "finished";
    private static final int TERMINATION_SWITCHES_COUNT = 2;

    @Getter
    private final String flowId;
    private final FlowValidationService service;

    private int awaitingRules;
    private int awaitingMeters;
    private int awaitingGroups;
    private final List<SwitchFlowEntries> receivedRules = new ArrayList<>();
    private final List<SwitchMeterEntries> receivedMeters = new ArrayList<>();
    private final List<SwitchGroupEntries> receivedGroups = new ArrayList<>();
    private List<FlowValidationResponse> response;

    public FlowValidationFsm(@NonNull CommandContext commandContext, @NonNull FlowValidationHubCarrier carrier,
                             @NonNull String flowId, @NonNull PersistenceManager persistenceManager,
                             @NonNull FlowResourcesConfig flowResourcesConfig,
                             @NonNull Config config,
                             @NonNull Collection<FlowValidationEventListener> eventListeners) {
        super(Event.NEXT, Event.ERROR, commandContext, carrier, eventListeners);
        this.flowId = flowId;

        service = new FlowValidationService(persistenceManager, flowResourcesConfig,
                config.getFlowMeterMinBurstSizeInKbits(), config.getFlowMeterBurstCoefficient());
    }

    protected void receiveData(State from, State to,
                               Event event, Object context) {
        Flow flow;
        try {
            flow = service.checkFlowStatusAndGetFlow(flowId);
        } catch (FlowNotFoundException e) {
            log.error("Flow {} not found when sending commands to SpeakerWorkerBolt", flowId, e);
            sendException(e.getMessage(), "Receiving rules operation in FlowValidationFsm", ErrorType.NOT_FOUND);
            return;
        } catch (IllegalFlowStateException e) {
            log.error("Could not validate flow: Flow {} is in DOWN state", flowId, e);
            sendException("Could not validate flow",
                    format("Could not validate flow: Flow %s is in DOWN state", flowId),
                    ErrorType.UNPROCESSABLE_REQUEST);
            return;
        }

        List<SwitchId> switchIds = service.getSwitchIdListByFlowId(flowId);

        awaitingRules = switchIds.size();
        log.debug("Send commands to get rules on the switches");
        switchIds.forEach(switchId ->
                getCarrier().sendSpeakerRequest(flowId, new DumpRulesForFlowHsRequest(switchId)));

        log.debug("Send commands to get meters on the termination switches");
        awaitingMeters = TERMINATION_SWITCHES_COUNT;
        getCarrier().sendSpeakerRequest(flowId, new DumpMetersForFlowHsRequest(flow.getSrcSwitchId()));
        getCarrier().sendSpeakerRequest(flowId, new DumpMetersForFlowHsRequest(flow.getDestSwitchId()));

        log.debug("Send commands to get groups on the termination switches");
        awaitingGroups = TERMINATION_SWITCHES_COUNT;
        getCarrier().sendSpeakerRequest(flowId, new DumpGroupsForFlowHsRequest(flow.getSrcSwitchId()));
        getCarrier().sendSpeakerRequest(flowId, new DumpGroupsForFlowHsRequest(flow.getDestSwitchId()));
    }

    protected void receivedRules(State from, State to,
                                 Event event, Object context) {
        SwitchFlowEntries switchFlowEntries = (SwitchFlowEntries) context;
        log.info("Switch rules received for switch {}", switchFlowEntries.getSwitchId());
        receivedRules.add(switchFlowEntries);
        awaitingRules--;
        checkOfCompleteDataCollection();
    }

    protected void receivedMeters(State from, State to,
                                  Event event, Object context) {
        SwitchMeterEntries switchMeterEntries = (SwitchMeterEntries) context;
        log.info("Switch meters received for switch {}", switchMeterEntries.getSwitchId());
        receivedMeters.add(switchMeterEntries);
        awaitingMeters--;
        checkOfCompleteDataCollection();
    }

    protected void receivedGroups(State from, State to,
                                  Event event, Object context) {
        SwitchGroupEntries switchGroupEntries = (SwitchGroupEntries) context;
        log.info("Switch meters received for switch {}", switchGroupEntries.getSwitchId());
        receivedGroups.add(switchGroupEntries);
        awaitingGroups--;
        checkOfCompleteDataCollection();
    }

    private void checkOfCompleteDataCollection() {
        if (awaitingRules == 0 && awaitingMeters == 0 && awaitingGroups == 0) {
            fire(DATA_READY);
        }
    }

    protected void validateFlow(State from, State to, Event event, Object context) {
        try {
            response = service.validateFlow(flowId, receivedRules, receivedMeters, receivedGroups);
        } catch (FlowNotFoundException e) {
            log.error("Flow {} not found during flow validation", flowId, e);
            sendException(e.getMessage(), "Flow validation operation in FlowValidationFsm", ErrorType.NOT_FOUND);
        } catch (SwitchNotFoundException e) {
            log.error(e.getMessage(), e);
            sendException(e.getMessage(), "Flow validation operation in FlowValidationFsm", ErrorType.NOT_FOUND);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            sendException(e.getMessage(), "Flow validation operation in FlowValidationFsm", ErrorType.INTERNAL_ERROR);
        }
    }

    protected void finished(State from, State to, Event event, Object context) {
        log.info("FSM finished work");
        notifyEventListeners(listener -> listener.onValidationResult(flowId, response));
        getCarrier().sendNorthboundResponse(response);
    }

    protected void finishedWithError(State from, State to, Event event, Object context) {
        ErrorData data = (ErrorData) context;
        log.error("Message: {}", data.getErrorMessage());
        sendNorthboundError(data.getErrorType(),
                format("Could not validate flow: %s", data.getErrorMessage()), data.getErrorDescription());
    }

    private void sendException(String message, String description, ErrorType errorType) {
        ErrorData errorData = new ErrorData(errorType, message, description);
        fire(ERROR, errorData);
    }

    public static class Factory {
        private final StateMachineBuilder<FlowValidationFsm, State, Event, Object> builder;
        private final FlowValidationHubCarrier carrier;
        private final PersistenceManager persistenceManager;
        private final FlowResourcesConfig flowResourcesConfig;
        private final Config config;

        public Factory(@NonNull FlowValidationHubCarrier carrier, @NonNull PersistenceManager persistenceManager,
                       @NonNull FlowResourcesConfig flowResourcesConfig, @NonNull Config config) {
            this.carrier = carrier;
            this.persistenceManager = persistenceManager;
            this.flowResourcesConfig = flowResourcesConfig;
            this.config = config;

            builder = StateMachineBuilderFactory.create(
                    FlowValidationFsm.class,
                    State.class,
                    Event.class,
                    Object.class,
                    CommandContext.class,
                    FlowValidationHubCarrier.class,
                    String.class,
                    PersistenceManager.class,
                    FlowResourcesConfig.class,
                    Config.class,
                    Collection.class);

            builder.transition().from(INITIALIZED).to(RECEIVE_DATA).on(NEXT)
                    .callMethod("receiveData");
            builder.internalTransition().within(RECEIVE_DATA).on(RULES_RECEIVED).callMethod("receivedRules");
            builder.internalTransition().within(RECEIVE_DATA).on(METERS_RECEIVED).callMethod("receivedMeters");
            builder.internalTransition().within(RECEIVE_DATA).on(GROUPS_RECEIVED).callMethod("receivedGroups");

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

        public FlowValidationFsm newInstance(@NonNull String flowId, @NonNull CommandContext commandContext,
                                             @NonNull Collection<FlowValidationEventListener> eventListeners) {
            FlowValidationFsm fsm = builder.newStateMachine(INITIALIZED, commandContext, carrier, flowId,
                    persistenceManager, flowResourcesConfig, config, eventListeners);

            fsm.addTransitionCompleteListener(event ->
                    log.debug("FlowValidationFsm, transition to {} on {}", event.getTargetState(), event.getCause()));

            if (!eventListeners.isEmpty()) {
                fsm.addTransitionCompleteListener(event -> {
                    switch (event.getTargetState()) {
                        case FINISHED_WITH_ERROR:
                            fsm.notifyEventListeners(listener -> listener.onFailedValidation(flowId));
                            break;
                        default:
                            // ignore
                    }
                });
            }

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

    @Value
    @Builder
    public static class Config implements Serializable {
        long flowMeterMinBurstSizeInKbits;
        double flowMeterBurstCoefficient;
    }
}
