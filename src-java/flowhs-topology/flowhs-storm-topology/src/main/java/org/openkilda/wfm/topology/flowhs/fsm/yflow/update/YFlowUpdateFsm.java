/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.update;

import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.metrics.MeterRegistryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.common.YFlowProcessingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.AllocateYFlowResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.RevertYFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.CompleteYFlowUpdatingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.DeallocateNewYFlowResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.DeallocateOldYFlowResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.HandleNotCompletedCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.HandleNotDeallocatedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.HandleNotRevertedSubFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.HandleNotUpdatedSubFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.InstallNewMetersAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.InstallReallocatedMetersAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.OnReceivedInstallResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.OnReceivedRemoveResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.OnReceivedResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.OnRevertSubFlowAllocatedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.OnSubFlowAllocatedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.OnSubFlowRevertedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.OnSubFlowUpdatedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.OnTimeoutOperationAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.RemoveNewMetersAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.RemoveOldMetersAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.RevertSubFlowsAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.RevertYFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.UpdateSubFlowsAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.UpdateYFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.ValidateNewMetersAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions.ValidateYFlowAction;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowUpdateService;
import org.openkilda.wfm.topology.flowhs.service.yflow.YFlowEventListener;

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
public final class YFlowUpdateFsm extends YFlowProcessingFsm<YFlowUpdateFsm, State, Event, YFlowUpdateContext,
        FlowGenericCarrier, YFlowEventListener> {
    private YFlowRequest originalFlow;
    private YFlowRequest targetFlow;

    private YFlowResources oldResources;

    private final Set<String> subFlows = new HashSet<>();
    private final Set<String> updatingSubFlows = new HashSet<>();
    private final Set<String> failedSubFlows = new HashSet<>();
    private final Set<String> allocatedSubFlows = new HashSet<>();

    private String mainAffinityFlowId;
    private Collection<RequestedFlow> requestedFlows;

    private String diverseFlowId;

    private Collection<DeleteSpeakerCommandsRequest> deleteOldYFlowCommands;

    private YFlowUpdateFsm(@NonNull CommandContext commandContext, @NonNull FlowGenericCarrier carrier,
                           @NonNull String yFlowId, @NonNull Collection<YFlowEventListener> eventListeners) {
        super(Event.NEXT, Event.ERROR, commandContext, carrier, yFlowId, eventListeners);
    }

    public void addSubFlow(String flowId) {
        subFlows.add(flowId);
    }

    public boolean isUpdatingSubFlow(String flowId) {
        return updatingSubFlows.contains(flowId);
    }

    public void addUpdatingSubFlow(String flowId) {
        updatingSubFlows.add(flowId);
    }

    public void removeUpdatingSubFlow(String flowId) {
        updatingSubFlows.remove(flowId);
    }

    public void clearUpdatingSubFlows() {
        updatingSubFlows.clear();
    }

    public void addFailedSubFlow(String flowId) {
        failedSubFlows.add(flowId);
    }

    public void clearFailedSubFlows() {
        failedSubFlows.clear();
    }

    public boolean isFailedSubFlow(String flowId) {
        return failedSubFlows.contains(flowId);
    }

    public void addAllocatedSubFlow(String flowId) {
        allocatedSubFlows.add(flowId);
    }

    public void clearAllocatedSubFlows() {
        allocatedSubFlows.clear();
    }

    @Override
    protected String getCrudActionName() {
        return "update";
    }

    public static class Factory {
        private final StateMachineBuilder<YFlowUpdateFsm, State, Event, YFlowUpdateContext> builder;
        private final FlowGenericCarrier carrier;

        public Factory(@NonNull FlowGenericCarrier carrier, @NonNull PersistenceManager persistenceManager,
                       @NonNull PathComputer pathComputer, @NonNull FlowResourcesManager resourcesManager,
                       @NonNull RuleManager ruleManager, @NonNull FlowUpdateService flowUpdateService,
                       int resourceAllocationRetriesLimit, int speakerCommandRetriesLimit) {
            this.carrier = carrier;

            builder = StateMachineBuilderFactory.create(YFlowUpdateFsm.class, State.class, Event.class,
                    YFlowUpdateContext.class, CommandContext.class, FlowGenericCarrier.class, String.class,
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
                    .to(State.YFLOW_UPDATED)
                    .on(Event.NEXT)
                    .perform(new UpdateYFlowAction(persistenceManager, ruleManager));
            builder.transitions()
                    .from(State.YFLOW_VALIDATED)
                    .toAmong(State.REVERTING_YFLOW_STATUS, State.REVERTING_YFLOW_STATUS)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transitions()
                    .from(State.YFLOW_UPDATED)
                    .toAmong(State.UPDATING_SUB_FLOWS, State.REVERTING_YFLOW_UPDATE, State.REVERTING_YFLOW_UPDATE)
                    .onEach(Event.NEXT, Event.TIMEOUT, Event.ERROR);

            builder.defineParallelStatesOn(State.UPDATING_SUB_FLOWS, State.SUB_FLOW_UPDATING_STARTED);
            builder.defineState(State.SUB_FLOW_UPDATING_STARTED)
                    .addEntryAction(new UpdateSubFlowsAction(flowUpdateService));

            builder.internalTransition()
                    .within(State.UPDATING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_ALLOCATED)
                    .perform(new OnSubFlowAllocatedAction(persistenceManager));
            builder.internalTransition()
                    .within(State.UPDATING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_UPDATED)
                    .perform(new OnSubFlowUpdatedAction(flowUpdateService));
            builder.internalTransition()
                    .within(State.UPDATING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_FAILED)
                    .perform(new HandleNotUpdatedSubFlowAction(persistenceManager));
            builder.transitions()
                    .from(State.UPDATING_SUB_FLOWS)
                    .toAmong(State.REVERTING_YFLOW, State.REVERTING_YFLOW, State.ALL_PENDING_OPERATIONS_COMPLETED)
                    .onEach(Event.FAILED_TO_UPDATE_SUB_FLOWS, Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.UPDATING_SUB_FLOWS)
                    .to(State.ALL_SUB_FLOWS_UPDATED)
                    .on(Event.ALL_SUB_FLOWS_UPDATED);

            builder.transitions()
                    .from(State.ALL_SUB_FLOWS_UPDATED)
                    .toAmong(State.REVERTING_YFLOW, State.REVERTING_YFLOW)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.ALL_SUB_FLOWS_UPDATED)
                    .to(State.REMOVING_OLD_YFLOW_METERS)
                    .on(Event.NEXT)
                    .perform(new RemoveOldMetersAction(persistenceManager, ruleManager));

            builder.internalTransition()
                    .within(State.REMOVING_OLD_YFLOW_METERS)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRemoveResponseAction(speakerCommandRetriesLimit));
            builder.transition()
                    .from(State.REMOVING_OLD_YFLOW_METERS)
                    .to(State.OLD_YFLOW_METERS_REMOVED)
                    .on(Event.YFLOW_METERS_REMOVED);
            builder.transitions()
                    .from(State.REMOVING_OLD_YFLOW_METERS)
                    .toAmong(State.REVERTING_YFLOW, State.REVERTING_YFLOW)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.OLD_YFLOW_METERS_REMOVED)
                    .to(State.NEW_YFLOW_RESOURCES_ALLOCATED)
                    .on(Event.NEXT)
                    .perform(new AllocateYFlowResourcesAction<>(persistenceManager, resourceAllocationRetriesLimit,
                            pathComputer, resourcesManager));

            builder.transition()
                    .from(State.NEW_YFLOW_RESOURCES_ALLOCATED)
                    .to(State.INSTALLING_NEW_YFLOW_METERS)
                    .on(Event.NEXT)
                    .perform(new InstallNewMetersAction(persistenceManager, ruleManager));
            builder.transitions()
                    .from(State.NEW_YFLOW_RESOURCES_ALLOCATED)
                    .toAmong(State.REVERTING_YFLOW, State.REVERTING_YFLOW)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.internalTransition()
                    .within(State.INSTALLING_NEW_YFLOW_METERS)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(speakerCommandRetriesLimit));
            builder.transition()
                    .from(State.INSTALLING_NEW_YFLOW_METERS)
                    .to(State.NEW_YFLOW_METERS_INSTALLED)
                    .on(Event.ALL_YFLOW_METERS_INSTALLED);
            builder.transitions()
                    .from(State.INSTALLING_NEW_YFLOW_METERS)
                    .toAmong(State.REVERTING_YFLOW, State.REVERTING_YFLOW)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.NEW_YFLOW_METERS_INSTALLED)
                    .to(State.VALIDATING_NEW_YFLOW_METERS)
                    .on(Event.NEXT)
                    .perform(new ValidateNewMetersAction(persistenceManager));
            builder.transitions()
                    .from(State.NEW_YFLOW_METERS_INSTALLED)
                    .toAmong(State.REVERTING_YFLOW, State.REVERTING_YFLOW)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.VALIDATING_NEW_YFLOW_METERS)
                    .to(State.NEW_YFLOW_METERS_VALIDATED)
                    .on(Event.YFLOW_METERS_VALIDATED);
            builder.transitions()
                    .from(State.VALIDATING_NEW_YFLOW_METERS)
                    .toAmong(State.REVERTING_YFLOW, State.REVERTING_YFLOW)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.NEW_YFLOW_METERS_VALIDATED)
                    .to(State.YFLOW_INSTALLATION_COMPLETED)
                    .on(Event.NEXT)
                    .perform(new CompleteYFlowUpdatingAction(persistenceManager, dashboardLogger));
            builder.transitions()
                    .from(State.YFLOW_INSTALLATION_COMPLETED)
                    .toAmong(State.DEALLOCATING_OLD_YFLOW_RESOURCES, State.FINISHED_WITH_ERROR,
                            State.FINISHED_WITH_ERROR)
                    .onEach(Event.NEXT, Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.DEALLOCATING_OLD_YFLOW_RESOURCES)
                    .to(State.OLD_YFLOW_RESOURCES_DEALLOCATED)
                    .on(Event.NEXT)
                    .perform(new DeallocateOldYFlowResourcesAction(persistenceManager, resourcesManager));

            builder.transition()
                    .from(State.OLD_YFLOW_RESOURCES_DEALLOCATED)
                    .to(State.FINISHED)
                    .on(Event.NEXT);
            builder.transition()
                    .from(State.OLD_YFLOW_RESOURCES_DEALLOCATED)
                    .to(State.FINISHED_WITH_ERROR)
                    .on(Event.ERROR)
                    .perform(new HandleNotDeallocatedResourcesAction());

            builder.onEntry(State.ALL_PENDING_OPERATIONS_COMPLETED)
                    .perform(new OnTimeoutOperationAction());
            builder.internalTransition()
                    .within(State.ALL_PENDING_OPERATIONS_COMPLETED)
                    .on(Event.SUB_FLOW_UPDATED)
                    .perform(new OnReceivedResponseAction(persistenceManager));
            builder.internalTransition()
                    .within(State.ALL_PENDING_OPERATIONS_COMPLETED)
                    .on(Event.SUB_FLOW_FAILED)
                    .perform(new OnReceivedResponseAction(persistenceManager));
            builder.transition()
                    .from(State.ALL_PENDING_OPERATIONS_COMPLETED)
                    .to(State.REVERTING_YFLOW)
                    .on(Event.REVERT_YFLOW);

            builder.transition()
                    .from(State.REVERTING_YFLOW)
                    .to(State.REMOVING_YFLOW_METERS)
                    .on(Event.NEXT)
                    .perform(new RemoveNewMetersAction(persistenceManager, ruleManager));

            builder.internalTransition()
                    .within(State.REMOVING_YFLOW_METERS)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRemoveResponseAction(speakerCommandRetriesLimit));
            builder.transition()
                    .from(State.REMOVING_YFLOW_METERS)
                    .to(State.YFLOW_METERS_REMOVED)
                    .on(Event.YFLOW_METERS_REMOVED);
            builder.transition()
                    .from(State.REMOVING_YFLOW_METERS)
                    .to(State.YFLOW_METERS_REMOVED)
                    .on(Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction());

            builder.transition()
                    .from(State.YFLOW_METERS_REMOVED)
                    .to(State.DEALLOCATING_NEW_YFLOW_RESOURCES)
                    .on(Event.NEXT);

            builder.transition()
                    .from(State.DEALLOCATING_NEW_YFLOW_RESOURCES)
                    .to(State.NEW_YFLOW_RESOURCES_DEALLOCATED)
                    .on(Event.NEXT)
                    .perform(new DeallocateNewYFlowResourcesAction(persistenceManager, resourcesManager));

            builder.transition()
                    .from(State.NEW_YFLOW_RESOURCES_DEALLOCATED)
                    .to(State.REVERTING_SUB_FLOWS)
                    .on(Event.NEXT);
            builder.transition()
                    .from(State.NEW_YFLOW_RESOURCES_DEALLOCATED)
                    .to(State.REVERTING_SUB_FLOWS)
                    .on(Event.ERROR)
                    .perform(new HandleNotDeallocatedResourcesAction());

            builder.defineParallelStatesOn(State.REVERTING_SUB_FLOWS, State.SUB_FLOW_REVERTING_STARTED);
            builder.defineState(State.SUB_FLOW_REVERTING_STARTED)
                    .addEntryAction(new RevertSubFlowsAction(flowUpdateService));

            builder.internalTransition()
                    .within(State.REVERTING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_ALLOCATED)
                    .perform(new OnRevertSubFlowAllocatedAction(persistenceManager));
            builder.internalTransition()
                    .within(State.REVERTING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_UPDATED)
                    .perform(new OnSubFlowRevertedAction(flowUpdateService));
            builder.internalTransition()
                    .within(State.REVERTING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_FAILED)
                    .perform(new HandleNotRevertedSubFlowAction());
            builder.transitions()
                    .from(State.REVERTING_SUB_FLOWS)
                    .toAmong(State.ALL_SUB_FLOWS_REVERTED, State.ALL_SUB_FLOWS_REVERTED)
                    .onEach(Event.FAILED_TO_UPDATE_SUB_FLOWS, Event.ERROR);

            builder.transition()
                    .from(State.REVERTING_SUB_FLOWS)
                    .to(State.ALL_SUB_FLOWS_REVERTED)
                    .on(Event.ALL_SUB_FLOWS_REVERTED);

            builder.transition()
                    .from(State.ALL_SUB_FLOWS_REVERTED)
                    .to(State.REVERTING_YFLOW_UPDATE)
                    .on(Event.NEXT);

            builder.transition()
                    .from(State.REVERTING_YFLOW_UPDATE)
                    .to(State.YFLOW_UPDATE_REVERTED)
                    .on(Event.NEXT)
                    .perform(new RevertYFlowAction(persistenceManager));

            builder.transition()
                    .from(State.YFLOW_UPDATE_REVERTED)
                    .to(State.INSTALLING_OLD_YFLOW_METERS)
                    .on(Event.NEXT)
                    .perform(new InstallReallocatedMetersAction(persistenceManager, ruleManager));

            builder.internalTransition()
                    .within(State.INSTALLING_OLD_YFLOW_METERS)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(speakerCommandRetriesLimit));
            builder.transition()
                    .from(State.INSTALLING_OLD_YFLOW_METERS)
                    .to(State.OLD_YFLOW_METERS_INSTALLED)
                    .on(Event.ALL_YFLOW_METERS_INSTALLED);
            builder.transitions()
                    .from(State.INSTALLING_OLD_YFLOW_METERS)
                    .toAmong(State.OLD_YFLOW_METERS_INSTALLED, State.OLD_YFLOW_METERS_INSTALLED)
                    .onEach(Event.ERROR, Event.TIMEOUT)
                    .perform(new HandleNotCompletedCommandsAction());

            builder.transition()
                    .from(State.OLD_YFLOW_METERS_INSTALLED)
                    .to(State.YFLOW_REVERTED)
                    .on(Event.NEXT)
                    .perform(new CompleteYFlowUpdatingAction(persistenceManager, dashboardLogger));

            builder.transition()
                    .from(State.YFLOW_REVERTED)
                    .to(State.FINISHED_WITH_ERROR)
                    .on(Event.NEXT);

            builder.transition()
                    .from(State.REVERTING_YFLOW_STATUS)
                    .to(State.FINISHED_WITH_ERROR)
                    .on(Event.NEXT)
                    .perform(new RevertYFlowStatusAction<>(persistenceManager, dashboardLogger));

            builder.defineFinalState(State.FINISHED)
                    .addEntryAction(new OnFinishedAction(dashboardLogger));
            builder.defineFinalState(State.FINISHED_WITH_ERROR)
                    .addEntryAction(new OnFinishedWithErrorAction(dashboardLogger));
        }

        public YFlowUpdateFsm newInstance(@NonNull CommandContext commandContext, @NonNull String flowId,
                                          @NonNull Collection<YFlowEventListener> eventListeners) {
            YFlowUpdateFsm fsm = builder.newStateMachine(State.INITIALIZED, commandContext, carrier, flowId,
                    eventListeners);

            fsm.addTransitionCompleteListener(event ->
                    log.debug("YFlowUpdateFsm, transition to {} on {}", event.getTargetState(), event.getCause()));

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
        YFLOW_UPDATED,

        UPDATING_SUB_FLOWS,
        SUB_FLOW_UPDATING_STARTED,
        ALL_SUB_FLOWS_UPDATED,

        NEW_YFLOW_RESOURCES_ALLOCATED,
        INSTALLING_NEW_YFLOW_METERS,
        NEW_YFLOW_METERS_INSTALLED,
        VALIDATING_NEW_YFLOW_METERS,
        NEW_YFLOW_METERS_VALIDATED,
        REMOVING_OLD_YFLOW_METERS,
        OLD_YFLOW_METERS_REMOVED,
        DEALLOCATING_OLD_YFLOW_RESOURCES,
        OLD_YFLOW_RESOURCES_DEALLOCATED,
        COMPLETE_YFLOW_INSTALLATION,
        YFLOW_INSTALLATION_COMPLETED,
        FINISHED,

        ALL_PENDING_OPERATIONS_COMPLETED,

        REVERTING_YFLOW,
        YFLOW_REVERTED,
        REMOVING_YFLOW_METERS,
        YFLOW_METERS_REMOVED,
        DEALLOCATING_NEW_YFLOW_RESOURCES,
        NEW_YFLOW_RESOURCES_DEALLOCATED,

        REVERTING_YFLOW_UPDATE,
        YFLOW_UPDATE_REVERTED,

        REVERTING_SUB_FLOWS,
        SUB_FLOW_REVERTING_STARTED,
        ALL_SUB_FLOWS_REVERTED,

        YFLOW_RESOURCES_REALLOCATED,
        INSTALLING_OLD_YFLOW_METERS,
        OLD_YFLOW_METERS_INSTALLED,

        REVERTING_YFLOW_STATUS,
        FINISHED_WITH_ERROR;
    }

    public enum Event {
        NEXT,

        RESPONSE_RECEIVED,

        SUB_FLOW_ALLOCATED,
        SUB_FLOW_UPDATED,
        SUB_FLOW_FAILED,
        ALL_SUB_FLOWS_UPDATED,
        FAILED_TO_UPDATE_SUB_FLOWS,
        ALL_YFLOW_METERS_INSTALLED,
        YFLOW_METERS_VALIDATED,
        YFLOW_METERS_REMOVED,

        SUB_FLOW_REVERTED,
        ALL_SUB_FLOWS_REVERTED,

        TIMEOUT,
        REVERT_YFLOW,
        ERROR
    }
}
