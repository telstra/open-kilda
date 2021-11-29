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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.validation;

import org.openkilda.messaging.info.flow.FlowValidationResponse;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.metrics.MeterRegistryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.common.NbTrackableFlowProcessingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.actions.CompleteYFlowValidationAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.actions.DumpYFlowResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.actions.HandleNotValidatedSubFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.actions.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.actions.OnReceivedYFlowResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.actions.OnSubFlowValidatedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.actions.PreValidateYFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.actions.ValidateSubFlowsAction;
import org.openkilda.wfm.topology.flowhs.service.FlowValidationHubService;
import org.openkilda.wfm.topology.flowhs.service.yflow.YFlowEventListener;
import org.openkilda.wfm.topology.flowhs.service.yflow.YFlowValidationHubCarrier;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.LongTaskTimer.Sample;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
@Slf4j
public final class YFlowValidationFsm extends NbTrackableFlowProcessingFsm<YFlowValidationFsm, State, Event,
        YFlowValidationContext, YFlowValidationHubCarrier, YFlowEventListener> {
    private final String yFlowId;

    private final Set<String> subFlows = new HashSet<>();
    private final Set<String> validatingSubFlows = new HashSet<>();
    private final Set<String> failedSubFlows = new HashSet<>();
    private final List<FlowValidationResponse> subFlowValidationResults = new ArrayList<>();

    private int awaitingRules;
    private int awaitingMeters;
    private final List<SwitchFlowEntries> receivedRules = new ArrayList<>();
    private final List<SwitchMeterEntries> receivedMeters = new ArrayList<>();

    private YFlowValidationFsm(@NonNull CommandContext commandContext, @NonNull YFlowValidationHubCarrier carrier,
                               @NonNull String yFlowId, @NonNull Collection<YFlowEventListener> eventListeners) {
        super(Event.NEXT, Event.ERROR, commandContext, carrier, eventListeners);
        this.yFlowId = yFlowId;
    }

    @Override
    public String getFlowId() {
        return getYFlowId();
    }

    public void addSubFlow(String flowId) {
        subFlows.add(flowId);
    }

    public boolean isValidatingSubFlow(String flowId) {
        return validatingSubFlows.contains(flowId);
    }

    public void addValidatingSubFlow(String flowId) {
        validatingSubFlows.add(flowId);
    }

    public void removeValidatingSubFlow(String flowId) {
        validatingSubFlows.remove(flowId);
    }

    public void clearValidatingSubFlows() {
        validatingSubFlows.clear();
    }

    public void addFailedSubFlow(String flowId) {
        failedSubFlows.add(flowId);
    }

    public boolean isFailedSubFlow(String flowId) {
        return failedSubFlows.contains(flowId);
    }

    public void addSubFlowValidationResults(Collection<FlowValidationResponse> validationResults) {
        subFlowValidationResults.addAll(validationResults);
    }

    public static class Factory {
        private final StateMachineBuilder<YFlowValidationFsm, State, Event, YFlowValidationContext> builder;
        private final YFlowValidationHubCarrier carrier;

        public Factory(@NonNull YFlowValidationHubCarrier carrier, @NonNull PersistenceManager persistenceManager,
                       @NonNull FlowValidationHubService flowValidationHubService,
                       @NonNull YFlowValidationService yFlowValidationService) {
            this.carrier = carrier;


            builder = StateMachineBuilderFactory.create(YFlowValidationFsm.class, State.class, Event.class,
                    YFlowValidationContext.class, CommandContext.class, YFlowValidationHubCarrier.class, String.class,
                    Collection.class);

            builder.transition()
                    .from(State.INITIALIZED)
                    .to(State.YFLOW_PREVALIDATED)
                    .on(Event.NEXT)
                    .perform(new PreValidateYFlowAction(persistenceManager));
            builder.transition()
                    .from(State.INITIALIZED)
                    .to(State.FINISHED_WITH_ERROR)
                    .on(Event.TIMEOUT);

            builder.transition()
                    .from(State.YFLOW_PREVALIDATED)
                    .to(State.VALIDATING_SUB_FLOWS)
                    .on(Event.NEXT);
            builder.transitions()
                    .from(State.YFLOW_PREVALIDATED)
                    .toAmong(State.FINISHED_WITH_ERROR, State.FINISHED)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.defineParallelStatesOn(State.VALIDATING_SUB_FLOWS, State.SUB_FLOW_VALIDATION_STARTED);
            builder.defineState(State.SUB_FLOW_VALIDATION_STARTED)
                    .addEntryAction(new ValidateSubFlowsAction(persistenceManager, flowValidationHubService));

            builder.internalTransition()
                    .within(State.VALIDATING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_VALIDATED)
                    .perform(new OnSubFlowValidatedAction());
            builder.internalTransition()
                    .within(State.VALIDATING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_FAILED)
                    .perform(new HandleNotValidatedSubFlowAction());
            builder.transitions()
                    .from(State.VALIDATING_SUB_FLOWS)
                    .toAmong(State.FINISHED_WITH_ERROR, State.FINISHED_WITH_ERROR)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.VALIDATING_SUB_FLOWS)
                    .to(State.SUB_FLOWS_VALIDATED)
                    .on(Event.ALL_SUB_FLOWS_VALIDATED);

            builder.transition()
                    .from(State.SUB_FLOWS_VALIDATED)
                    .to(State.DUMPING_YFLOW_RESOURCES)
                    .on(Event.NEXT)
                    .perform(new DumpYFlowResourcesAction(persistenceManager));
            builder.transitions()
                    .from(State.SUB_FLOWS_VALIDATED)
                    .toAmong(State.FINISHED_WITH_ERROR, State.FINISHED_WITH_ERROR)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.internalTransition()
                    .within(State.DUMPING_YFLOW_RESOURCES)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedYFlowResourcesAction());
            builder.internalTransition()
                    .within(State.DUMPING_YFLOW_RESOURCES)
                    .on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedYFlowResourcesAction());
            builder.transitions()
                    .from(State.DUMPING_YFLOW_RESOURCES)
                    .toAmong(State.YFLOW_RESOURCES_RECEIVED, State.FINISHED_WITH_ERROR, State.FINISHED_WITH_ERROR)
                    .onEach(Event.ALL_YFLOW_RESOURCES_RECEIVED, Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.YFLOW_RESOURCES_RECEIVED)
                    .to(State.YFLOW_RESOURCES_VALIDATED)
                    .on(Event.NEXT)
                    .perform(new CompleteYFlowValidationAction(yFlowValidationService));

            builder.transitions()
                    .from(State.YFLOW_RESOURCES_VALIDATED)
                    .toAmong(State.FINISHED, State.FINISHED_WITH_ERROR, State.FINISHED_WITH_ERROR)
                    .onEach(Event.NEXT, Event.ERROR, Event.TIMEOUT);

            builder.defineFinalState(State.FINISHED);
            builder.defineFinalState(State.FINISHED_WITH_ERROR)
                    .addEntryAction(new OnFinishedWithErrorAction());
        }

        public YFlowValidationFsm newInstance(@NonNull CommandContext commandContext, @NonNull String yFlowId,
                                              @NonNull Collection<YFlowEventListener> eventListeners) {
            YFlowValidationFsm fsm = builder.newStateMachine(State.INITIALIZED, commandContext, carrier, yFlowId,
                    eventListeners);

            fsm.addTransitionCompleteListener(event ->
                    log.debug("YFlowValidationFsm, transition to {} on {}", event.getTargetState(), event.getCause()));

            MeterRegistryHolder.getRegistry().ifPresent(registry -> {
                Sample sample = LongTaskTimer.builder("fsm.active_execution")
                        .register(registry)
                        .start();
                fsm.addTerminateListener(e -> {
                    long duration = sample.stop();
                    if (fsm.getCurrentState() == State.FINISHED) {
                        registry.timer("fsm.execution.success")
                                .record(duration, TimeUnit.NANOSECONDS);
                    } else if (fsm.getCurrentState() == State.FINISHED_WITH_ERROR) {
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
        YFLOW_PREVALIDATED,

        VALIDATING_SUB_FLOWS,
        SUB_FLOW_VALIDATION_STARTED,
        SUB_FLOWS_VALIDATED,

        DUMPING_YFLOW_RESOURCES,
        YFLOW_RESOURCES_RECEIVED,
        YFLOW_RESOURCES_VALIDATED,

        FINISHED,
        FINISHED_WITH_ERROR;
    }

    public enum Event {
        NEXT,

        SUB_FLOW_VALIDATED,
        SUB_FLOW_FAILED,
        ALL_SUB_FLOWS_VALIDATED,
        FAILED_TO_VALIDATE_SUB_FLOWS,

        ALL_YFLOW_RESOURCES_RECEIVED,
        RESPONSE_RECEIVED,
        ERROR_RECEIVED,

        TIMEOUT,
        ERROR
    }
}
