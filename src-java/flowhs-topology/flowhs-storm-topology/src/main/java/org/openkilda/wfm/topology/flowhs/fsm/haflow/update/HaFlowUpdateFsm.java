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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.update;

import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.model.FlowStatus;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.metrics.MeterRegistryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.common.HaFlowPathSwappingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.ReportErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.DeallocateResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.HandleNotCompletedCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.HandleNotDeallocatedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.HandleNotRemovedPathsAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.HandleNotRevertedResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.NotifyHaFlowMonitorAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.NotifyHaFlowStatsOnNewPathsAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.NotifyHaFlowStatsOnRemovedPathsAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.OnReceivedInstallResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.OnReceivedRemoveResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.OnReceivedRevertResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.RevertResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions.AbandonPendingCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions.AllocatePrimaryResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions.AllocateProtectedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions.BuildNewRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions.CompleteFlowPathInstallationAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions.CompleteFlowPathRemovalAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions.InstallIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions.InstallNonIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions.PostResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions.RemoveOldRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions.RevertFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions.RevertFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions.RevertNewRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions.RevertPathsSwapAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions.SwapFlowPathsAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions.UpdateFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions.UpdateHaFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions.ValidateFlowAction;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowProcessingEventListener;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.LongTaskTimer.Sample;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.io.Serializable;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
@Slf4j
public final class HaFlowUpdateFsm extends HaFlowPathSwappingFsm<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext,
        FlowGenericCarrier, FlowProcessingEventListener> {
    private HaFlowRequest targetHaFlow;
    private FlowStatus newFlowStatus;

    public HaFlowUpdateFsm(@NonNull CommandContext commandContext, @NonNull FlowGenericCarrier carrier,
                           @NonNull String flowId,
                           @NonNull Collection<FlowProcessingEventListener> eventListeners) {
        super(Event.NEXT, Event.ERROR, commandContext, carrier, flowId, eventListeners);
    }

    @Override
    public void fireNoPathFound(String errorReason) {
        fireError(Event.NO_PATH_FOUND, errorReason);
    }

    @Override
    protected String getCrudActionName() {
        return "update";
    }

    public static class Factory {
        private final StateMachineBuilder<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext> builder;
        private final FlowGenericCarrier carrier;

        public Factory(@NonNull FlowGenericCarrier carrier, @NonNull Config config,
                       @NonNull PersistenceManager persistenceManager, @NonNull RuleManager ruleManager,
                       @NonNull PathComputer pathComputer, @NonNull FlowResourcesManager resourcesManager) {

            this.carrier = carrier;

            builder = StateMachineBuilderFactory.create(HaFlowUpdateFsm.class, State.class, Event.class,
                    HaFlowUpdateContext.class, CommandContext.class, FlowGenericCarrier.class, String.class,
                    Collection.class);

            FlowOperationsDashboardLogger dashboardLogger = new FlowOperationsDashboardLogger(log);
            final ReportErrorAction<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext>
                    reportErrorAction = new ReportErrorAction<>(Event.TIMEOUT);

            builder.transition().from(State.INITIALIZED).to(State.FLOW_VALIDATED).on(Event.NEXT)
                    .perform(new ValidateFlowAction(persistenceManager, dashboardLogger));
            builder.transition().from(State.INITIALIZED).to(State.FINISHED_WITH_ERROR).on(Event.TIMEOUT);

            builder.transition().from(State.FLOW_VALIDATED).to(State.FLOW_UPDATED).on(Event.NEXT)
                    .perform(new UpdateHaFlowAction(persistenceManager));
            builder.transitions().from(State.FLOW_VALIDATED)
                    .toAmong(State.REVERTING_FLOW_STATUS, State.REVERTING_FLOW_STATUS)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.FLOW_UPDATED).to(State.PRIMARY_RESOURCES_ALLOCATED).on(Event.NEXT)
                    .perform(new AllocatePrimaryResourcesAction(persistenceManager,
                            config.getPathAllocationRetriesLimit(), config.getPathAllocationRetryDelay(),
                            config.getResourceAllocationRetriesLimit(), pathComputer, resourcesManager,
                            dashboardLogger));
            builder.transitions().from(State.FLOW_UPDATED)
                    .toAmong(State.REVERTING_FLOW, State.REVERTING_FLOW)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.PRIMARY_RESOURCES_ALLOCATED).to(State.PROTECTED_RESOURCES_ALLOCATED)
                    .on(Event.NEXT)
                    .perform(new AllocateProtectedResourcesAction(persistenceManager,
                            config.getPathAllocationRetriesLimit(), config.getPathAllocationRetryDelay(),
                            config.getResourceAllocationRetriesLimit(), pathComputer, resourcesManager,
                            dashboardLogger));
            builder.transitions().from(State.PRIMARY_RESOURCES_ALLOCATED)
                    .toAmong(State.NEW_RULES_REVERTED, State.NEW_RULES_REVERTED, State.REVERTING_ALLOCATED_RESOURCES)
                    .onEach(Event.TIMEOUT, Event.ERROR, Event.NO_PATH_FOUND);

            builder.transition().from(State.PROTECTED_RESOURCES_ALLOCATED).to(State.RESOURCE_ALLOCATION_COMPLETED)
                    .on(Event.NEXT)
                    .perform(new PostResourceAllocationAction(persistenceManager));
            builder.transitions().from(State.PROTECTED_RESOURCES_ALLOCATED)
                    .toAmong(State.NEW_RULES_REVERTED, State.NEW_RULES_REVERTED, State.REVERTING_ALLOCATED_RESOURCES)
                    .onEach(Event.TIMEOUT, Event.ERROR, Event.NO_PATH_FOUND);

            builder.transition().from(State.RESOURCE_ALLOCATION_COMPLETED).to(State.BUILDING_RULES)
                    .on(Event.NEXT)
                    .perform(new BuildNewRulesAction(persistenceManager, ruleManager));
            builder.transitions().from(State.RESOURCE_ALLOCATION_COMPLETED)
                    .toAmong(State.NEW_RULES_REVERTED, State.NEW_RULES_REVERTED)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.BUILDING_RULES).to(State.INSTALLING_NON_INGRESS_RULES)
                    .on(Event.NEXT)
                    .perform(new InstallNonIngressRulesAction(persistenceManager, ruleManager));
            builder.transitions().from(State.BUILDING_RULES)
                    .toAmong(State.NEW_RULES_REVERTED, State.NEW_RULES_REVERTED)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.internalTransition().within(State.INSTALLING_NON_INGRESS_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction<>(
                            config.getSpeakerCommandRetriesLimit(), Event.RULES_INSTALLED, carrier));
            builder.transition().from(State.INSTALLING_NON_INGRESS_RULES).to(State.NON_INGRESS_RULES_INSTALLED)
                    .on(Event.RULES_INSTALLED);
            builder.transitions().from(State.INSTALLING_NON_INGRESS_RULES)
                    .toAmong(State.PATHS_SWAP_REVERTED, State.PATHS_SWAP_REVERTED)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new AbandonPendingCommandsAction());

            builder.transition().from(State.NON_INGRESS_RULES_INSTALLED).to(State.PATHS_SWAPPED)
                    .on(Event.NEXT)
                    .perform(new SwapFlowPathsAction(persistenceManager, resourcesManager));
            builder.transitions().from(State.NON_INGRESS_RULES_INSTALLED)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition()
                    .from(State.PATHS_SWAPPED)
                    .to(State.NOTIFY_FLOW_STATS_ON_NEW_PATHS)
                    .on(Event.NEXT)
                    .perform(new NotifyHaFlowStatsOnNewPathsAction<>(persistenceManager, carrier));
            builder.transitions().from(State.PATHS_SWAPPED)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.NOTIFY_FLOW_STATS_ON_NEW_PATHS)
                    .to(State.INSTALLING_INGRESS_RULES)
                    .on(Event.NEXT)
                    .perform(new InstallIngressRulesAction(persistenceManager));

            builder.internalTransition().within(State.INSTALLING_INGRESS_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction<>(
                            config.getSpeakerCommandRetriesLimit(), Event.RULES_INSTALLED, carrier));
            builder.transition().from(State.INSTALLING_INGRESS_RULES).to(State.INGRESS_RULES_INSTALLED)
                    .on(Event.RULES_INSTALLED);
            builder.transitions().from(State.INSTALLING_INGRESS_RULES)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new AbandonPendingCommandsAction());

            builder.transition().from(State.INGRESS_RULES_INSTALLED).to(State.NEW_PATHS_INSTALLATION_COMPLETED)
                    .on(Event.NEXT)
                    .perform(new CompleteFlowPathInstallationAction(persistenceManager));
            builder.transitions().from(State.INGRESS_RULES_INSTALLED)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.NEW_PATHS_INSTALLATION_COMPLETED)
                    .to(State.REMOVING_OLD_RULES).on(Event.NEXT)
                    .perform(new RemoveOldRulesAction(persistenceManager, ruleManager));
            builder.transitions().from(State.NEW_PATHS_INSTALLATION_COMPLETED)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.internalTransition().within(State.REMOVING_OLD_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRemoveResponseAction<>(
                            config.getSpeakerCommandRetriesLimit(), Event.RULES_REMOVED, carrier));
            builder.transition().from(State.REMOVING_OLD_RULES).to(State.OLD_RULES_REMOVED)
                    .on(Event.RULES_REMOVED);
            builder.transitions().from(State.REMOVING_OLD_RULES)
                    .toAmong(State.OLD_RULES_REMOVED, State.OLD_RULES_REMOVED)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction<>());

            builder.transition().from(State.OLD_RULES_REMOVED)
                    .to(State.NOTIFY_FLOW_STATS_ON_REMOVED_PATHS).on(Event.NEXT)
                    .perform(new NotifyHaFlowStatsOnRemovedPathsAction<>(persistenceManager, carrier));

            builder.transition().from(State.NOTIFY_FLOW_STATS_ON_REMOVED_PATHS)
                    .to(State.OLD_PATHS_REMOVAL_COMPLETED)
                    .on(Event.NEXT)
                    .perform(new CompleteFlowPathRemovalAction(persistenceManager));

            builder.transition().from(State.OLD_PATHS_REMOVAL_COMPLETED).to(State.DEALLOCATING_OLD_RESOURCES)
                    .on(Event.NEXT);
            builder.transitions().from(State.OLD_PATHS_REMOVAL_COMPLETED)
                    .toAmong(State.DEALLOCATING_OLD_RESOURCES, State.DEALLOCATING_OLD_RESOURCES)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new HandleNotRemovedPathsAction<>());

            builder.transition().from(State.DEALLOCATING_OLD_RESOURCES)
                    .to(State.OLD_RESOURCES_DEALLOCATED).on(Event.NEXT)
                    .perform(new DeallocateResourcesAction<>(persistenceManager, resourcesManager));

            builder.transition().from(State.OLD_RESOURCES_DEALLOCATED).to(State.UPDATING_FLOW_STATUS).on(Event.NEXT);
            builder.transitions().from(State.OLD_RESOURCES_DEALLOCATED)
                    .toAmong(State.UPDATING_FLOW_STATUS, State.UPDATING_FLOW_STATUS)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new HandleNotDeallocatedResourcesAction<>());

            builder.transition().from(State.UPDATING_FLOW_STATUS).to(State.FLOW_STATUS_UPDATED).on(Event.NEXT)
                    .perform(new UpdateFlowStatusAction(persistenceManager, dashboardLogger));

            builder.transition().from(State.FLOW_STATUS_UPDATED).to(State.NOTIFY_FLOW_MONITOR).on(Event.NEXT);
            builder.transitions().from(State.FLOW_STATUS_UPDATED)
                    .toAmong(State.NOTIFY_FLOW_MONITOR_WITH_ERROR, State.NOTIFY_FLOW_MONITOR_WITH_ERROR)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.onEntry(State.REVERTING_PATHS_SWAP)
                    .perform(reportErrorAction);
            builder.transition().from(State.REVERTING_PATHS_SWAP).to(State.PATHS_SWAP_REVERTED)
                    .on(Event.NEXT)
                    .perform(new RevertPathsSwapAction(persistenceManager));

            builder.onEntry(State.PATHS_SWAP_REVERTED)
                    .perform(reportErrorAction);
            builder.transition().from(State.PATHS_SWAP_REVERTED)
                    .to(State.REVERTING_NEW_RULES)
                    .on(Event.NEXT)
                    .perform(new RevertNewRulesAction(persistenceManager, ruleManager));

            builder.internalTransition().within(State.REVERTING_NEW_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRevertResponseAction<>(
                            config.getSpeakerCommandRetriesLimit(), Event.RULES_REVERTED, carrier));
            builder.transition().from(State.REVERTING_NEW_RULES).to(State.NEW_RULES_REVERTED)
                    .on(Event.RULES_REVERTED);
            builder.transitions().from(State.REVERTING_NEW_RULES)
                    .toAmong(State.NEW_RULES_REVERTED, State.NEW_RULES_REVERTED)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction<>());

            builder.transition().from(State.NEW_RULES_REVERTED)
                    .to(State.REVERTING_ALLOCATED_RESOURCES)
                    .on(Event.NEXT);

            builder.onEntry(State.REVERTING_ALLOCATED_RESOURCES)
                    .perform(reportErrorAction);
            builder.transitions().from(State.REVERTING_ALLOCATED_RESOURCES)
                    .toAmong(State.RESOURCES_ALLOCATION_REVERTED)
                    .onEach(Event.NEXT)
                    .perform(new RevertResourceAllocationAction<>(persistenceManager, resourcesManager));
            builder.transition().from(State.RESOURCES_ALLOCATION_REVERTED).to(State.REVERTING_FLOW).on(Event.NEXT);
            builder.transition().from(State.RESOURCES_ALLOCATION_REVERTED).to(State.REVERTING_FLOW)
                    .on(Event.ERROR)
                    .perform(new HandleNotRevertedResourceAllocationAction<>());

            builder.onEntry(State.REVERTING_FLOW)
                    .perform(reportErrorAction);
            builder.transition().from(State.REVERTING_FLOW)
                    .to(State.REVERTING_FLOW_STATUS)
                    .on(Event.NEXT)
                    .perform(new RevertFlowAction(persistenceManager));

            builder.onEntry(State.REVERTING_FLOW_STATUS)
                    .perform(reportErrorAction);
            builder.transition().from(State.REVERTING_FLOW_STATUS)
                    .to(State.NOTIFY_FLOW_MONITOR_WITH_ERROR)
                    .on(Event.NEXT)
                    .perform(new RevertFlowStatusAction(persistenceManager));

            builder.onEntry(State.FINISHED_WITH_ERROR)
                    .perform(reportErrorAction);

            builder.transition()
                    .from(State.NOTIFY_FLOW_MONITOR)
                    .to(State.FINISHED)
                    .on(Event.NEXT)
                    .perform(new NotifyHaFlowMonitorAction<>(persistenceManager, carrier));
            builder.transition()
                    .from(State.NOTIFY_FLOW_MONITOR_WITH_ERROR)
                    .to(State.FINISHED_WITH_ERROR)
                    .on(Event.NEXT)
                    .perform(new NotifyHaFlowMonitorAction<>(persistenceManager, carrier));

            builder.defineFinalState(State.FINISHED)
                    .addEntryAction(new OnFinishedAction(dashboardLogger));
            builder.defineFinalState(State.FINISHED_WITH_ERROR)
                    .addEntryAction(new OnFinishedWithErrorAction(dashboardLogger));
        }

        public HaFlowUpdateFsm newInstance(
                @NonNull String flowId, @NonNull CommandContext commandContext,
                @NonNull Collection<FlowProcessingEventListener> eventListeners) {
            HaFlowUpdateFsm fsm = builder.newStateMachine(State.INITIALIZED, commandContext, carrier, flowId,
                    eventListeners);

            fsm.addTransitionCompleteListener(event ->
                    log.debug("HaFlowUpdateFsm, transition to {} on {}", event.getTargetState(), event.getCause()));

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
        FLOW_VALIDATED,
        FLOW_UPDATED,
        PRIMARY_RESOURCES_ALLOCATED,
        PROTECTED_RESOURCES_ALLOCATED,
        RESOURCE_ALLOCATION_COMPLETED,

        BUILDING_RULES,
        INSTALLING_NON_INGRESS_RULES,
        NON_INGRESS_RULES_INSTALLED,

        PATHS_SWAPPED,

        INSTALLING_INGRESS_RULES,
        INGRESS_RULES_INSTALLED,

        NEW_PATHS_INSTALLATION_COMPLETED,

        REMOVING_OLD_RULES,
        OLD_RULES_REMOVED,

        OLD_PATHS_REMOVAL_COMPLETED,

        DEALLOCATING_OLD_RESOURCES,
        OLD_RESOURCES_DEALLOCATED,

        UPDATING_FLOW_STATUS,
        FLOW_STATUS_UPDATED,

        FINISHED,

        REVERTING_PATHS_SWAP,
        PATHS_SWAP_REVERTED,
        REVERTING_NEW_RULES,
        NEW_RULES_REVERTED,

        REVERTING_ALLOCATED_RESOURCES,
        RESOURCES_ALLOCATION_REVERTED,
        REVERTING_FLOW_STATUS,
        REVERTING_FLOW,

        FINISHED_WITH_ERROR,

        NOTIFY_FLOW_MONITOR,
        NOTIFY_FLOW_MONITOR_WITH_ERROR,

        NOTIFY_FLOW_STATS_ON_NEW_PATHS,
        NOTIFY_FLOW_STATS_ON_REMOVED_PATHS
    }

    public enum Event {
        NEXT,

        NO_PATH_FOUND,

        RESPONSE_RECEIVED,

        RULES_INSTALLED,
        RULES_REMOVED,
        RULES_REVERTED,

        TIMEOUT,
        ERROR
    }

    @Value
    @Builder
    public static class Config implements Serializable {
        @Builder.Default
        int speakerCommandRetriesLimit = 3;
        @Builder.Default
        int pathAllocationRetriesLimit = 10;
        @Builder.Default
        int pathAllocationRetryDelay = 50;
        @Builder.Default
        int resourceAllocationRetriesLimit = 10;
    }
}
