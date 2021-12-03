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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.create;

import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.metrics.MeterRegistryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.common.YFlowProcessingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.AllocateYFlowResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.CompleteYFlowInstallationAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.CreateDraftYFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.CreateSubFlowsAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.DeallocateYFlowResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.HandleNotCompletedCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.HandleNotCreatedSubFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.HandleNotDeallocatedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.HandleNotRemovedSubFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.InstallYFlowResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.OnReceivedInstallResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.OnReceivedRemoveResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.OnReceivedValidateResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.OnSubFlowAllocatedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.OnSubFlowCreatedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.OnSubFlowRemovedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.RemoveSubFlowsAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.RemoveYFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.RemoveYFlowResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.ValidateYFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action.ValidateYFlowResourcesAction;
import org.openkilda.wfm.topology.flowhs.service.FlowCreateService;
import org.openkilda.wfm.topology.flowhs.service.FlowDeleteService;
import org.openkilda.wfm.topology.flowhs.service.YFlowCreateHubCarrier;
import org.openkilda.wfm.topology.flowhs.service.YFlowEventListener;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.LongTaskTimer.Sample;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
@Slf4j
public final class YFlowCreateFsm extends YFlowProcessingFsm<YFlowCreateFsm, State, Event, YFlowCreateContext,
        YFlowCreateHubCarrier, YFlowEventListener> {
    private YFlowRequest targetFlow;

    private final Set<String> subFlows = new HashSet<>();
    private final Set<String> creatingSubFlows = new HashSet<>();
    private final Set<String> deletingSubFlows = new HashSet<>();
    private final Set<String> failedSubFlows = new HashSet<>();
    private final Set<String> allocatedSubFlows = new HashSet<>();

    private String errorReason;

    private YFlowCreateFsm(@NonNull CommandContext commandContext, @NonNull YFlowCreateHubCarrier carrier,
                           @NonNull String yFlowId, @NonNull Collection<YFlowEventListener> eventListeners) {
        super(commandContext, carrier, yFlowId, eventListeners);
    }

    @Override
    public void fireNext(YFlowCreateContext context) {
        fire(Event.NEXT, context);
    }

    @Override
    public void fireError(String errorReason) {
        fireError(Event.ERROR, errorReason);
    }

    private void fireError(Event errorEvent, String errorReason) {
        setErrorReason(errorReason);
        fire(errorEvent);
    }

    public void addSubFlow(String flowId) {
        subFlows.add(flowId);
    }

    public boolean isCreatingSubFlow(String flowId) {
        return creatingSubFlows.contains(flowId);
    }

    public void addCreatingSubFlow(String flowId) {
        creatingSubFlows.add(flowId);
    }

    public void removeCreatingSubFlow(String flowId) {
        creatingSubFlows.remove(flowId);
    }

    public void clearCreatingSubFlows() {
        creatingSubFlows.clear();
    }

    public boolean isDeletingSubFlow(String flowId) {
        return deletingSubFlows.contains(flowId);
    }

    public void addDeletingSubFlow(String flowId) {
        deletingSubFlows.add(flowId);
    }

    public void removeDeletingSubFlow(String flowId) {
        deletingSubFlows.remove(flowId);
    }

    public void clearDeletingSubFlows() {
        deletingSubFlows.clear();
    }

    public void addFailedSubFlow(String flowId) {
        failedSubFlows.add(flowId);
    }

    public boolean isFailedSubFlow(String flowId) {
        return failedSubFlows.contains(flowId);
    }

    public void addAllocatedSubFlow(String flowId) {
        allocatedSubFlows.add(flowId);
    }

    public void setErrorReason(String errorReason) {
        if (this.errorReason != null) {
            log.error("Subsequent error fired: " + errorReason);
        } else {
            this.errorReason = errorReason;
        }
    }

    @Override
    public void reportError(Event event) {
        if (Event.TIMEOUT == event) {
            reportGlobalTimeout();
        }
    }

    @Override
    protected String getCrudActionName() {
        return "create";
    }

    public static class Factory {
        private final StateMachineBuilder<YFlowCreateFsm, State, Event, YFlowCreateContext> builder;
        private final YFlowCreateHubCarrier carrier;

        public Factory(@NonNull YFlowCreateHubCarrier carrier, @NonNull PersistenceManager persistenceManager,
                       @NonNull PathComputer pathComputer, @NonNull FlowResourcesManager resourcesManager,
                       @NonNull FlowCreateService flowCreateService, @NonNull FlowDeleteService flowDeleteService,
                       int resourceAllocationRetriesLimit, int speakerCommandRetriesLimit) {
            this.carrier = carrier;


            builder = StateMachineBuilderFactory.create(YFlowCreateFsm.class, State.class, Event.class,
                    YFlowCreateContext.class, CommandContext.class, YFlowCreateHubCarrier.class, String.class,
                    Collection.class);

            FlowOperationsDashboardLogger dashboardLogger = new FlowOperationsDashboardLogger(log);

            builder.transition()
                    .from(State.INITIALIZED)
                    .to(State.YFLOW_VALIDATED)
                    .on(Event.NEXT)
                    .perform(new ValidateYFlowAction(persistenceManager, dashboardLogger));
            builder.transition()
                    .from(State.INITIALIZED)
                    .to(State.FINISHED_WITH_ERROR)
                    .on(Event.TIMEOUT);

            builder.transition()
                    .from(State.YFLOW_VALIDATED)
                    .to(State.DRAFT_YFLOW_CREATED)
                    .on(Event.NEXT)
                    .perform(new CreateDraftYFlowAction(persistenceManager));
            builder.transitions()
                    .from(State.YFLOW_VALIDATED)
                    .toAmong(State.FINISHED_WITH_ERROR, State.FINISHED_WITH_ERROR)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.transitions()
                    .from(State.DRAFT_YFLOW_CREATED)
                    .toAmong(State.CREATING_SUB_FLOWS, State.REMOVING_YFLOW, State.REMOVING_YFLOW)
                    .onEach(Event.NEXT, Event.ERROR, Event.TIMEOUT);

            builder.defineParallelStatesOn(State.CREATING_SUB_FLOWS, State.SUB_FLOW_CREATION_STARTED);
            builder.defineState(State.SUB_FLOW_CREATION_STARTED)
                    .addEntryAction(new CreateSubFlowsAction(flowCreateService));

            builder.internalTransition()
                    .within(State.CREATING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_ALLOCATED)
                    .perform(new OnSubFlowAllocatedAction(persistenceManager));
            builder.internalTransition()
                    .within(State.CREATING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_CREATED)
                    .perform(new OnSubFlowCreatedAction());
            builder.internalTransition()
                    .within(State.CREATING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_FAILED)
                    .perform(new HandleNotCreatedSubFlowAction(persistenceManager));
            builder.transitions()
                    .from(State.CREATING_SUB_FLOWS)
                    .toAmong(State.REMOVING_SUB_FLOWS, State.REMOVING_SUB_FLOWS, State.REMOVING_SUB_FLOWS)
                    .onEach(Event.FAILED_TO_CREATE_SUB_FLOWS, Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.CREATING_SUB_FLOWS)
                    .to(State.SUB_FLOWS_CREATED)
                    .on(Event.ALL_SUB_FLOWS_CREATED);

            builder.transitions()
                    .from(State.SUB_FLOWS_CREATED)
                    .toAmong(State.REMOVING_SUB_FLOWS, State.REMOVING_SUB_FLOWS)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.SUB_FLOWS_CREATED)
                    .to(State.YFLOW_RESOURCES_ALLOCATED)
                    .on(Event.NEXT)
                    .perform(new AllocateYFlowResourcesAction<>(persistenceManager, resourceAllocationRetriesLimit,
                            pathComputer, resourcesManager));

            builder.transition()
                    .from(State.YFLOW_RESOURCES_ALLOCATED)
                    .to(State.INSTALLING_YFLOW_METERS)
                    .on(Event.NEXT)
                    .perform(new InstallYFlowResourcesAction(persistenceManager));
            builder.transitions()
                    .from(State.YFLOW_RESOURCES_ALLOCATED)
                    .toAmong(State.DEALLOCATING_YFLOW_RESOURCES, State.DEALLOCATING_YFLOW_RESOURCES)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.internalTransition()
                    .within(State.INSTALLING_YFLOW_METERS)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(speakerCommandRetriesLimit));
            builder.internalTransition()
                    .within(State.INSTALLING_YFLOW_METERS)
                    .on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(speakerCommandRetriesLimit));
            builder.transition()
                    .from(State.INSTALLING_YFLOW_METERS)
                    .to(State.YFLOW_METERS_INSTALLED)
                    .on(Event.ALL_YFLOW_METERS_INSTALLED);
            builder.transitions()
                    .from(State.INSTALLING_YFLOW_METERS)
                    .toAmong(State.REVERTING_YFLOW, State.REVERTING_YFLOW)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.YFLOW_METERS_INSTALLED)
                    .to(State.VALIDATING_YFLOW_METERS)
                    .on(Event.NEXT)
                    .perform(new ValidateYFlowResourcesAction(persistenceManager));
            builder.transitions()
                    .from(State.YFLOW_METERS_INSTALLED)
                    .toAmong(State.REVERTING_YFLOW, State.REVERTING_YFLOW)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.internalTransition()
                    .within(State.VALIDATING_YFLOW_METERS)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedValidateResponseAction(speakerCommandRetriesLimit));
            builder.internalTransition()
                    .within(State.VALIDATING_YFLOW_METERS)
                    .on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedValidateResponseAction(speakerCommandRetriesLimit));
            builder.transitions()
                    .from(State.VALIDATING_YFLOW_METERS)
                    .toAmong(State.YFLOW_METERS_VALIDATED, State.REVERTING_YFLOW, State.REVERTING_YFLOW)
                    .onEach(Event.ALL_YFLOW_METERS_VALIDATED, Event.ERROR, Event.TIMEOUT);

            builder.transitions()
                    .from(State.YFLOW_METERS_VALIDATED)
                    .toAmong(State.REVERTING_YFLOW, State.REVERTING_YFLOW)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.YFLOW_METERS_VALIDATED)
                    .to(State.YFLOW_INSTALLATION_COMPLETED)
                    .on(Event.NEXT)
                    .perform(new CompleteYFlowInstallationAction(persistenceManager, dashboardLogger));
            builder.transitions()
                    .from(State.YFLOW_INSTALLATION_COMPLETED)
                    .toAmong(State.FINISHED, State.REVERTING_YFLOW, State.REVERTING_YFLOW)
                    .onEach(Event.NEXT, Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.REVERTING_YFLOW)
                    .to(State.REMOVING_YFLOW_METERS)
                    .on(Event.NEXT)
                    .perform(new RemoveYFlowResourcesAction(persistenceManager));

            builder.internalTransition()
                    .within(State.REMOVING_YFLOW_METERS)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRemoveResponseAction(speakerCommandRetriesLimit));
            builder.internalTransition()
                    .within(State.REMOVING_YFLOW_METERS)
                    .on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedRemoveResponseAction(speakerCommandRetriesLimit));
            builder.transition()
                    .from(State.REMOVING_YFLOW_METERS)
                    .to(State.YFLOW_METERS_REMOVED)
                    .on(Event.ALL_YFLOW_METERS_REMOVED);
            builder.transition()
                    .from(State.REMOVING_YFLOW_METERS)
                    .to(State.YFLOW_METERS_REMOVED)
                    .on(Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction());

            builder.transition()
                    .from(State.YFLOW_METERS_REMOVED)
                    .to(State.DEALLOCATING_YFLOW_RESOURCES)
                    .on(Event.NEXT);

            builder.transition()
                    .from(State.DEALLOCATING_YFLOW_RESOURCES)
                    .to(State.YFLOW_RESOURCES_DEALLOCATED)
                    .on(Event.NEXT)
                    .perform(new DeallocateYFlowResourcesAction(persistenceManager, resourcesManager));

            builder.transition()
                    .from(State.YFLOW_RESOURCES_DEALLOCATED)
                    .to(State.REMOVING_SUB_FLOWS)
                    .on(Event.NEXT);
            builder.transition()
                    .from(State.YFLOW_RESOURCES_DEALLOCATED)
                    .to(State.REMOVING_SUB_FLOWS)
                    .on(Event.ERROR)
                    .perform(new HandleNotDeallocatedResourcesAction());

            builder.defineParallelStatesOn(State.REMOVING_SUB_FLOWS, State.SUB_FLOW_REMOVAL_STARTED);
            builder.defineState(State.SUB_FLOW_REMOVAL_STARTED)
                    .addEntryAction(new RemoveSubFlowsAction(flowDeleteService));

            builder.internalTransition()
                    .within(State.REMOVING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_REMOVED)
                    .perform(new OnSubFlowRemovedAction());
            builder.internalTransition()
                    .within(State.REMOVING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_FAILED)
                    .perform(new HandleNotRemovedSubFlowAction());
            builder.transitions()
                    .from(State.REMOVING_SUB_FLOWS)
                    .toAmong(State.SUB_FLOWS_REMOVED, State.SUB_FLOWS_REMOVED, State.SUB_FLOWS_REMOVED)
                    .onEach(Event.ALL_SUB_FLOWS_REMOVED, Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.SUB_FLOWS_REMOVED)
                    .to(State.REMOVING_YFLOW)
                    .on(Event.NEXT);

            builder.transition()
                    .from(State.REMOVING_YFLOW)
                    .to(State.YFLOW_REMOVED)
                    .on(Event.NEXT)
                    .perform(new RemoveYFlowAction(persistenceManager));

            builder.transition()
                    .from(State.YFLOW_REMOVED)
                    .to(State.FINISHED_WITH_ERROR)
                    .on(Event.NEXT);

            builder.defineFinalState(State.FINISHED)
                    .addEntryAction(new OnFinishedAction(dashboardLogger));
            builder.defineFinalState(State.FINISHED_WITH_ERROR)
                    .addEntryAction(new OnFinishedWithErrorAction(dashboardLogger));
        }

        public YFlowCreateFsm newInstance(CommandContext commandContext, @NonNull String yFlowId,
                                          @NonNull Collection<YFlowEventListener> eventListeners) {
            YFlowCreateFsm fsm = builder.newStateMachine(State.INITIALIZED, commandContext, carrier, yFlowId,
                    eventListeners);

            fsm.addTransitionCompleteListener(event ->
                    log.debug("YFlowCreateFsm, transition to {} on {}", event.getTargetState(), event.getCause()));

            if (!eventListeners.isEmpty()) {
                fsm.addTransitionCompleteListener(event -> {
                    switch (event.getTargetState()) {
                        case FINISHED:
                            fsm.notifyEventListenersOnComplete();
                            break;
                        case FINISHED_WITH_ERROR:
                            fsm.notifyEventListenersOnError(ErrorType.INTERNAL_ERROR, fsm.getErrorReason());
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
        YFLOW_VALIDATED,
        DRAFT_YFLOW_CREATED,

        CREATING_SUB_FLOWS,
        SUB_FLOW_CREATION_STARTED,
        SUB_FLOWS_CREATED,

        YFLOW_RESOURCES_ALLOCATED,
        INSTALLING_YFLOW_METERS,
        YFLOW_METERS_INSTALLED,
        VALIDATING_YFLOW_METERS,
        YFLOW_METERS_VALIDATED,
        YFLOW_INSTALLATION_COMPLETED,
        FINISHED,

        REVERTING_YFLOW,
        REMOVING_YFLOW_METERS,
        YFLOW_METERS_REMOVED,
        DEALLOCATING_YFLOW_RESOURCES,
        YFLOW_RESOURCES_DEALLOCATED,

        REMOVING_SUB_FLOWS,
        SUB_FLOW_REMOVAL_STARTED,
        SUB_FLOWS_REMOVED,

        REMOVING_YFLOW,
        YFLOW_REMOVED,
        FINISHED_WITH_ERROR;
    }

    public enum Event {
        NEXT,

        RESPONSE_RECEIVED,
        ERROR_RECEIVED,

        SUB_FLOW_ALLOCATED,
        SUB_FLOW_CREATED,
        SUB_FLOW_FAILED,
        ALL_SUB_FLOWS_CREATED,
        FAILED_TO_CREATE_SUB_FLOWS,
        ALL_YFLOW_METERS_INSTALLED,
        ALL_YFLOW_METERS_VALIDATED,
        ALL_YFLOW_METERS_REMOVED,

        SUB_FLOW_REMOVED,
        ALL_SUB_FLOWS_REMOVED,

        TIMEOUT,
        ERROR
    }
}
