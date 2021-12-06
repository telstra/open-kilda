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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.update;

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
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.RevertYFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.CompleteYFlowUpdatingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.DeallocateNewYFlowResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.DeallocateOldYFlowResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.HandleNotCompletedCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.HandleNotDeallocatedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.HandleNotRevertedSubFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.HandleNotUpdatedSubFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.InstallNewYPointMeterAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.InstallReallocatedYPointMeterAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.OnReceivedInstallResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.OnReceivedRemoveResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.OnReceivedResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.OnReceivedValidateResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.OnRevertSubFlowAllocatedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.OnSubFlowAllocatedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.OnSubFlowRevertedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.OnSubFlowUpdatedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.OnTimeoutOperationAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.ReallocateYFlowResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.RemoveOldYPointMeterAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.RemoveYPointMetersAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.RevertSubFlowsAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.RevertYFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.UpdateSubFlowsAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.UpdateYFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.ValidateNewYPointMeterAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action.ValidateYFlowAction;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources;
import org.openkilda.wfm.topology.flowhs.service.FlowUpdateService;
import org.openkilda.wfm.topology.flowhs.service.YFlowEventListener;
import org.openkilda.wfm.topology.flowhs.service.YFlowUpdateHubCarrier;

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
        YFlowUpdateHubCarrier, YFlowEventListener> {
    private YFlowRequest originalFlow;
    private YFlowRequest targetFlow;

    private YFlowResources oldResources;
    private YFlowResources reallocatedResources;

    private final Set<String> subFlows = new HashSet<>();
    private final Set<String> updatingSubFlows = new HashSet<>();
    private final Set<String> failedSubFlows = new HashSet<>();
    private final Set<String> allocatedSubFlows = new HashSet<>();

    private String mainAffinityFlowId;
    private Collection<RequestedFlow> requestedFlows;

    private String errorReason;

    private YFlowUpdateFsm(@NonNull CommandContext commandContext, @NonNull YFlowUpdateHubCarrier carrier,
                           @NonNull String yFlowId, @NonNull Collection<YFlowEventListener> eventListeners) {
        super(commandContext, carrier, yFlowId, eventListeners);
    }

    @Override
    public void fireNext(YFlowUpdateContext context) {
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

    public void setErrorReason(String errorReason) {
        if (this.errorReason != null) {
            log.error("Subsequent error fired: " + errorReason);
        } else {
            log.error("Error fired: " + errorReason);
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
        return "update";
    }

    public static class Factory {
        private final StateMachineBuilder<YFlowUpdateFsm, State, Event, YFlowUpdateContext> builder;
        private final YFlowUpdateHubCarrier carrier;

        public Factory(@NonNull YFlowUpdateHubCarrier carrier, @NonNull PersistenceManager persistenceManager,
                       @NonNull PathComputer pathComputer, @NonNull FlowResourcesManager resourcesManager,
                       @NonNull FlowUpdateService flowUpdateService,
                       int resourceAllocationRetriesLimit, int speakerCommandRetriesLimit) {
            this.carrier = carrier;


            builder = StateMachineBuilderFactory.create(YFlowUpdateFsm.class, State.class, Event.class,
                    YFlowUpdateContext.class, CommandContext.class, YFlowUpdateHubCarrier.class, String.class,
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
                    .perform(new UpdateYFlowAction(persistenceManager));
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
                    .perform(new OnSubFlowAllocatedAction(flowUpdateService, persistenceManager));
            builder.internalTransition()
                    .within(State.UPDATING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_UPDATED)
                    .perform(new OnSubFlowUpdatedAction());
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
                    .toAmong(State.REVERTING_YFLOW, State.ALL_PENDING_OPERATIONS_COMPLETED)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.ALL_SUB_FLOWS_UPDATED)
                    .to(State.NEW_YFLOW_RESOURCES_ALLOCATED)
                    .on(Event.NEXT)
                    .perform(new AllocateYFlowResourcesAction<>(persistenceManager, resourceAllocationRetriesLimit,
                            pathComputer, resourcesManager));

            builder.transition()
                    .from(State.NEW_YFLOW_RESOURCES_ALLOCATED)
                    .to(State.INSTALLING_NEW_YPOINT_METERS)
                    .on(Event.NEXT)
                    .perform(new InstallNewYPointMeterAction(persistenceManager));
            builder.transitions()
                    .from(State.NEW_YFLOW_RESOURCES_ALLOCATED)
                    .toAmong(State.REVERTING_YFLOW, State.ALL_PENDING_OPERATIONS_COMPLETED)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.internalTransition()
                    .within(State.INSTALLING_NEW_YPOINT_METERS)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(speakerCommandRetriesLimit));
            builder.internalTransition()
                    .within(State.INSTALLING_NEW_YPOINT_METERS)
                    .on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(speakerCommandRetriesLimit));
            builder.transition()
                    .from(State.INSTALLING_NEW_YPOINT_METERS)
                    .to(State.NEW_YPOINT_METERS_INSTALLED)
                    .on(Event.ALL_YFLOW_METERS_INSTALLED);
            builder.transitions()
                    .from(State.INSTALLING_NEW_YPOINT_METERS)
                    .toAmong(State.REVERTING_YFLOW, State.ALL_PENDING_OPERATIONS_COMPLETED)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.NEW_YPOINT_METERS_INSTALLED)
                    .to(State.VALIDATING_NEW_YPOINT_METERS)
                    .on(Event.NEXT)
                    .perform(new ValidateNewYPointMeterAction(persistenceManager));
            builder.transitions()
                    .from(State.NEW_YPOINT_METERS_INSTALLED)
                    .toAmong(State.REVERTING_YFLOW, State.ALL_PENDING_OPERATIONS_COMPLETED)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.internalTransition()
                    .within(State.VALIDATING_NEW_YPOINT_METERS)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedValidateResponseAction(speakerCommandRetriesLimit));
            builder.internalTransition()
                    .within(State.VALIDATING_NEW_YPOINT_METERS)
                    .on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedValidateResponseAction(speakerCommandRetriesLimit));
            builder.transition()
                    .from(State.VALIDATING_NEW_YPOINT_METERS)
                    .to(State.NEW_YPOINT_METERS_VALIDATED)
                    .on(Event.YPOINT_METER_VALIDATED);

            builder.transitions()
                    .from(State.VALIDATING_NEW_YPOINT_METERS)
                    .toAmong(State.REVERTING_YFLOW, State.ALL_PENDING_OPERATIONS_COMPLETED)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.NEW_YPOINT_METERS_VALIDATED)
                    .to(State.REMOVING_OLD_YPOINT_METERS)
                    .on(Event.NEXT)
                    .perform(new RemoveOldYPointMeterAction(persistenceManager));

            builder.transitions()
                    .from(State.NEW_YPOINT_METERS_VALIDATED)
                    .toAmong(State.REVERTING_YFLOW, State.ALL_PENDING_OPERATIONS_COMPLETED)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.internalTransition()
                    .within(State.REMOVING_OLD_YPOINT_METERS)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRemoveResponseAction(speakerCommandRetriesLimit));
            builder.internalTransition()
                    .within(State.REMOVING_OLD_YPOINT_METERS)
                    .on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedRemoveResponseAction(speakerCommandRetriesLimit));
            builder.transition()
                    .from(State.REMOVING_OLD_YPOINT_METERS)
                    .to(State.OLD_YPOINT_METER_REMOVED)
                    .on(Event.YPOINT_METER_REMOVED);
            builder.transition()
                    .from(State.REMOVING_OLD_YPOINT_METERS)
                    .to(State.OLD_YPOINT_METER_REMOVED)
                    .on(Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction());

            builder.transition()
                    .from(State.OLD_YPOINT_METER_REMOVED)
                    .to(State.DEALLOCATING_OLD_YFLOW_RESOURCES)
                    .on(Event.NEXT);

            builder.transition()
                    .from(State.DEALLOCATING_OLD_YFLOW_RESOURCES)
                    .to(State.OLD_YFLOW_RESOURCES_DEALLOCATED)
                    .on(Event.NEXT)
                    .perform(new DeallocateOldYFlowResourcesAction(persistenceManager, resourcesManager));

            builder.transition()
                    .from(State.OLD_YFLOW_RESOURCES_DEALLOCATED)
                    .to(State.COMPLETE_YFLOW_INSTALLATION)
                    .on(Event.NEXT);
            builder.transition()
                    .from(State.OLD_YFLOW_RESOURCES_DEALLOCATED)
                    .to(State.COMPLETE_YFLOW_INSTALLATION)
                    .on(Event.ERROR)
                    .perform(new HandleNotDeallocatedResourcesAction());

            builder.transition()
                    .from(State.COMPLETE_YFLOW_INSTALLATION)
                    .to(State.YFLOW_INSTALLATION_COMPLETED)
                    .on(Event.NEXT)
                    .perform(new CompleteYFlowUpdatingAction(persistenceManager, dashboardLogger));

            builder.transition()
                    .from(State.YFLOW_INSTALLATION_COMPLETED)
                    .to(State.FINISHED)
                    .on(Event.NEXT);
            builder.transition()
                    .from(State.YFLOW_INSTALLATION_COMPLETED)
                    .to(State.FINISHED_WITH_ERROR)
                    .on(Event.ERROR);

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
                    .to(State.REMOVING_YPOINT_METERS)
                    .on(Event.NEXT)
                    .perform(new RemoveYPointMetersAction(persistenceManager));

            builder.internalTransition()
                    .within(State.REMOVING_YPOINT_METERS)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRemoveResponseAction(speakerCommandRetriesLimit));
            builder.internalTransition()
                    .within(State.REMOVING_YPOINT_METERS)
                    .on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedRemoveResponseAction(speakerCommandRetriesLimit));
            builder.transition()
                    .from(State.REMOVING_YPOINT_METERS)
                    .to(State.YPOINT_METERS_REMOVED)
                    .on(Event.YPOINT_METER_REMOVED);
            builder.transition()
                    .from(State.REMOVING_YPOINT_METERS)
                    .to(State.YPOINT_METERS_REMOVED)
                    .on(Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction());

            builder.transition()
                    .from(State.YPOINT_METERS_REMOVED)
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
                    .perform(new OnRevertSubFlowAllocatedAction(flowUpdateService, persistenceManager));
            builder.internalTransition()
                    .within(State.REVERTING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_UPDATED)
                    .perform(new OnSubFlowRevertedAction());
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
                    .to(State.YFLOW_RESOURCES_REALLOCATED)
                    .on(Event.NEXT)
                    .perform(new ReallocateYFlowResourcesAction(persistenceManager, pathComputer));

            builder.transition()
                    .from(State.YFLOW_RESOURCES_REALLOCATED)
                    .to(State.INSTALLING_OLD_YPOINT_METER)
                    .on(Event.NEXT)
                    .perform(new InstallReallocatedYPointMeterAction(persistenceManager));

            builder.internalTransition()
                    .within(State.INSTALLING_OLD_YPOINT_METER)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(speakerCommandRetriesLimit));
            builder.internalTransition()
                    .within(State.INSTALLING_OLD_YPOINT_METER)
                    .on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(speakerCommandRetriesLimit));
            builder.transition()
                    .from(State.INSTALLING_OLD_YPOINT_METER)
                    .to(State.OLD_YPOINT_METER_INSTALLED)
                    .on(Event.ALL_YFLOW_METERS_INSTALLED);
            builder.transition()
                    .from(State.INSTALLING_OLD_YPOINT_METER)
                    .to(State.OLD_YPOINT_METER_INSTALLED)
                    .on(Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction());

            builder.transition()
                    .from(State.OLD_YPOINT_METER_INSTALLED)
                    .to(State.REVERTING_YFLOW_UPDATE)
                    .on(Event.NEXT);

            builder.transition()
                    .from(State.REVERTING_YFLOW_UPDATE)
                    .to(State.YFLOW_UPDATE_REVERTED)
                    .on(Event.NEXT)
                    .perform(new RevertYFlowAction(persistenceManager));

            builder.transition()
                    .from(State.YFLOW_UPDATE_REVERTED)
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

        public YFlowUpdateFsm newInstance(CommandContext commandContext, @NonNull String flowId,
                                          @NonNull Collection<YFlowEventListener> eventListeners) {
            YFlowUpdateFsm fsm = builder.newStateMachine(State.INITIALIZED, commandContext, carrier, flowId,
                    eventListeners);

            fsm.addTransitionCompleteListener(event ->
                    log.debug("YFlowUpdateFsm, transition to {} on {}", event.getTargetState(), event.getCause()));

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
        YFLOW_UPDATED,

        UPDATING_SUB_FLOWS,
        SUB_FLOW_UPDATING_STARTED,
        ALL_SUB_FLOWS_UPDATED,

        NEW_YFLOW_RESOURCES_ALLOCATED,
        INSTALLING_NEW_YPOINT_METERS,
        NEW_YPOINT_METERS_INSTALLED,
        VALIDATING_NEW_YPOINT_METERS,
        NEW_YPOINT_METERS_VALIDATED,
        REMOVING_OLD_YPOINT_METERS,
        OLD_YPOINT_METER_REMOVED,
        DEALLOCATING_OLD_YFLOW_RESOURCES,
        OLD_YFLOW_RESOURCES_DEALLOCATED,
        COMPLETE_YFLOW_INSTALLATION,
        YFLOW_INSTALLATION_COMPLETED,
        FINISHED,

        ALL_PENDING_OPERATIONS_COMPLETED,

        REVERTING_YFLOW,
        YFLOW_REVERTED,
        REMOVING_YPOINT_METERS,
        YPOINT_METERS_REMOVED,
        DEALLOCATING_NEW_YFLOW_RESOURCES,
        NEW_YFLOW_RESOURCES_DEALLOCATED,

        REVERTING_YFLOW_UPDATE,
        YFLOW_UPDATE_REVERTED,

        REVERTING_SUB_FLOWS,
        SUB_FLOW_REVERTING_STARTED,
        ALL_SUB_FLOWS_REVERTED,

        YFLOW_RESOURCES_REALLOCATED,
        INSTALLING_OLD_YPOINT_METER,
        OLD_YPOINT_METER_INSTALLED,

        REVERTING_YFLOW_STATUS,
        FINISHED_WITH_ERROR;
    }

    public enum Event {
        NEXT,

        RESPONSE_RECEIVED,
        ERROR_RECEIVED,

        SUB_FLOW_ALLOCATED,
        SUB_FLOW_UPDATED,
        SUB_FLOW_FAILED,
        ALL_SUB_FLOWS_UPDATED,
        FAILED_TO_UPDATE_SUB_FLOWS,
        ALL_YFLOW_METERS_INSTALLED,
        YPOINT_METER_VALIDATED,
        YPOINT_METER_REMOVED,

        SUB_FLOW_REVERTED,
        ALL_SUB_FLOWS_REVERTED,

        TIMEOUT,
        REVERT_YFLOW,
        ERROR
    }
}
