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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.reroute.error.RerouteError;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.IslEndpoint;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.metrics.MeterRegistryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.common.HaFlowPathSwappingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NotifyFlowMonitorAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.ReportErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.DeallocateResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.HandleNotCompletedCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.HandleNotDeallocatedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.HandleNotRemovedPathsAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.HandleNotRevertedResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.NotifyHaFlowStatsOnNewPathsAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.NotifyHaFlowStatsOnRemovedPathsAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.OnReceivedInstallResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.OnReceivedRemoveResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.OnReceivedRevertResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.RevertResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions.AllocatePrimaryResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions.AllocateProtectedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions.BuildNewRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions.CompleteFlowPathInstallationAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions.CompleteFlowPathRemovalAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions.InstallIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions.InstallNonIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions.OnNoPathFoundAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions.PostResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions.RemoveOldRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions.RevertFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions.RevertNewRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions.RevertPathsSwapAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions.SetInstallRuleErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions.SwapFlowPathsAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions.UpdateFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions.ValidateHaFlowAction;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteEventListener;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteHubCarrier;

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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
@Slf4j
public final class HaFlowRerouteFsm extends HaFlowPathSwappingFsm<HaFlowRerouteFsm, State, Event, HaFlowRerouteContext,
        FlowRerouteHubCarrier, FlowRerouteEventListener> {
    private boolean recreateIfSamePath;
    private boolean reroutePrimary;
    private boolean rerouteProtected;
    private boolean effectivelyDown;

    private FlowStatus originalFlowStatus;
    private FlowStatus newHaFlowStatus;
    private FlowEncapsulationType newEncapsulationType;

    private String rerouteReason;
    private Set<IslEndpoint> affectedIsls;
    private boolean ignoreBandwidth;

    private RerouteError rerouteError;

    public HaFlowRerouteFsm(
            @NonNull CommandContext commandContext, @NonNull FlowRerouteHubCarrier carrier, @NonNull String haFlowId,
            @NonNull Collection<FlowRerouteEventListener> eventListeners) {
        super(Event.NEXT, Event.ERROR, commandContext, carrier, haFlowId, eventListeners);
    }

    @Override
    protected void afterTransitionCausedException(State fromState, State toState, Event event,
                                                  HaFlowRerouteContext context) {
        String errorMessage = getLastException().getMessage();
        if (fromState == State.INITIALIZED || fromState == State.FLOW_VALIDATED) {
            ErrorData error = new ErrorData(ErrorType.INTERNAL_ERROR, "Could not reroute HA-flow", errorMessage);
            Message message = new ErrorMessage(error, getCommandContext().getCreateTime(),
                    getCommandContext().getCorrelationId());
            sendNorthboundResponse(message);
        }

        fireError(errorMessage);
        super.afterTransitionCausedException(fromState, toState, event, context);
    }

    @Override
    public void fireNoPathFound(String errorReason) {
        fireError(Event.NO_PATH_FOUND, errorReason);
    }

    public void setRerouteError(RerouteError rerouteError) {
        if (this.rerouteError == null) {
            this.rerouteError = rerouteError;
        }
    }

    @Override
    protected String getCrudActionName() {
        return "reroute";
    }

    public static class Factory {
        private final StateMachineBuilder<HaFlowRerouteFsm, State, Event, HaFlowRerouteContext> builder;
        private final FlowRerouteHubCarrier carrier;

        public Factory(@NonNull FlowRerouteHubCarrier carrier, @NonNull Config config,
                       @NonNull PersistenceManager persistenceManager, @NonNull RuleManager ruleManager,
                       @NonNull PathComputer pathComputer, @NonNull FlowResourcesManager resourcesManager) {
            this.carrier = carrier;

            builder = StateMachineBuilderFactory.create(HaFlowRerouteFsm.class, State.class, Event.class,
                    HaFlowRerouteContext.class, CommandContext.class, FlowRerouteHubCarrier.class, String.class,
                    Collection.class);

            FlowOperationsDashboardLogger dashboardLogger = new FlowOperationsDashboardLogger(log);
            final ReportErrorAction<HaFlowRerouteFsm, State, Event, HaFlowRerouteContext>
                    reportErrorAction = new ReportErrorAction<>(Event.TIMEOUT);

            builder.transition().from(State.INITIALIZED).to(State.FLOW_VALIDATED).on(Event.NEXT)
                    .perform(new ValidateHaFlowAction(persistenceManager, dashboardLogger));
            builder.transition().from(State.INITIALIZED).to(State.FINISHED_WITH_ERROR).on(Event.TIMEOUT);

            builder.transition().from(State.FLOW_VALIDATED).to(State.PRIMARY_RESOURCES_ALLOCATED).on(Event.NEXT)
                    .perform(new AllocatePrimaryResourcesAction(persistenceManager,
                            config.getPathAllocationRetriesLimit(), config.getPathAllocationRetryDelay(),
                            config.getResourceAllocationRetriesLimit(), pathComputer, resourcesManager,
                            dashboardLogger));
            builder.transitions().from(State.FLOW_VALIDATED)
                    .toAmong(State.REVERTING_FLOW_STATUS, State.REVERTING_FLOW_STATUS)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.PRIMARY_RESOURCES_ALLOCATED).to(State.PROTECTED_RESOURCES_ALLOCATED)
                    .on(Event.NEXT)
                    .perform(new AllocateProtectedResourcesAction(persistenceManager,
                            config.getPathAllocationRetriesLimit(), config.getPathAllocationRetryDelay(),
                            config.getResourceAllocationRetriesLimit(), pathComputer, resourcesManager,
                            dashboardLogger));
            builder.transition().from(State.PRIMARY_RESOURCES_ALLOCATED).to(State.REVERTING_ALLOCATED_RESOURCES)
                    .on(Event.NO_PATH_FOUND)
                    .perform(new OnNoPathFoundAction(persistenceManager, dashboardLogger, true));
            builder.transitions().from(State.PRIMARY_RESOURCES_ALLOCATED)
                    .toAmong(State.REVERTING_ALLOCATED_RESOURCES, State.REVERTING_ALLOCATED_RESOURCES)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.PROTECTED_RESOURCES_ALLOCATED).to(State.RESOURCE_ALLOCATION_COMPLETED)
                    .on(Event.NEXT)
                    .perform(new PostResourceAllocationAction(persistenceManager));
            builder.transition().from(State.PROTECTED_RESOURCES_ALLOCATED).to(State.MARKED_FLOW_DOWN_OR_DEGRADED)
                    .on(Event.NO_PATH_FOUND)
                    .perform(new OnNoPathFoundAction(persistenceManager, dashboardLogger, false));
            builder.transition().from(State.MARKED_FLOW_DOWN_OR_DEGRADED).to(State.RESOURCE_ALLOCATION_COMPLETED)
                    .on(Event.NEXT)
                    .perform(new PostResourceAllocationAction(persistenceManager));
            builder.transitions().from(State.PROTECTED_RESOURCES_ALLOCATED)
                    .toAmong(State.REVERTING_ALLOCATED_RESOURCES, State.REVERTING_ALLOCATED_RESOURCES)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.RESOURCE_ALLOCATION_COMPLETED).to(State.BUILDING_RULES)
                    .on(Event.NEXT)
                    .perform(new BuildNewRulesAction(persistenceManager, ruleManager));
            builder.transition().from(State.RESOURCE_ALLOCATION_COMPLETED).to(State.NOTIFY_FLOW_MONITOR_WITH_ERROR)
                    .on(Event.REROUTE_IS_SKIPPED)
                    .perform(new RevertFlowStatusAction(persistenceManager));
            builder.transitions().from(State.RESOURCE_ALLOCATION_COMPLETED)
                    .toAmong(State.REVERTING_ALLOCATED_RESOURCES, State.REVERTING_ALLOCATED_RESOURCES)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.BUILDING_RULES).to(State.INSTALLING_NON_INGRESS_RULES)
                    .on(Event.NEXT)
                    .perform(new InstallNonIngressRulesAction(persistenceManager));
            builder.transitions().from(State.BUILDING_RULES)
                    .toAmong(State.PATHS_SWAP_REVERTED, State.PATHS_SWAP_REVERTED)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new SetInstallRuleErrorAction());

            builder.internalTransition().within(State.INSTALLING_NON_INGRESS_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction<>(
                            config.getSpeakerCommandRetriesLimit(), Event.RULES_INSTALLED, carrier));
            builder.transition().from(State.INSTALLING_NON_INGRESS_RULES).to(State.NON_INGRESS_RULES_INSTALLED)
                    .on(Event.RULES_INSTALLED);
            builder.transitions().from(State.INSTALLING_NON_INGRESS_RULES)
                    .toAmong(State.PATHS_SWAP_REVERTED, State.PATHS_SWAP_REVERTED)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new SetInstallRuleErrorAction());

            builder.transition().from(State.NON_INGRESS_RULES_INSTALLED).to(State.PATHS_SWAPPED).on(Event.NEXT)
                    .perform(new SwapFlowPathsAction(persistenceManager, resourcesManager));
            builder.transitions().from(State.NON_INGRESS_RULES_INSTALLED)
                    .toAmong(State.PATHS_SWAP_REVERTED, State.PATHS_SWAP_REVERTED)
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
                    .perform(new SetInstallRuleErrorAction());

            builder.transition().from(State.INGRESS_RULES_INSTALLED).to(State.NEW_PATHS_INSTALLATION_COMPLETED)
                    .on(Event.NEXT)
                    .perform(new CompleteFlowPathInstallationAction(persistenceManager));
            builder.transitions().from(State.INGRESS_RULES_INSTALLED)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.NEW_PATHS_INSTALLATION_COMPLETED)
                    .to(State.REMOVING_OLD_RULES)
                    .on(Event.NEXT)
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
                    .to(State.NOTIFY_FLOW_STATS_ON_REMOVED_PATHS)
                    .on(Event.NEXT)
                    .perform(new NotifyHaFlowStatsOnRemovedPathsAction<>(persistenceManager, carrier));

            builder.transition().from(State.NOTIFY_FLOW_STATS_ON_REMOVED_PATHS)
                    .to(State.OLD_PATHS_REMOVAL_COMPLETED)
                    .on(Event.NEXT)
                    .perform(new CompleteFlowPathRemovalAction(persistenceManager));

            builder.transition().from(State.OLD_PATHS_REMOVAL_COMPLETED).to(State.DEALLOCATING_OLD_RESOURCES)
                    .on(Event.NEXT);
            builder.transition().from(State.OLD_PATHS_REMOVAL_COMPLETED).to(State.DEALLOCATING_OLD_RESOURCES)
                    .on(Event.ERROR)
                    .perform(new HandleNotRemovedPathsAction<>());

            builder.transition().from(State.DEALLOCATING_OLD_RESOURCES)
                    .to(State.OLD_RESOURCES_DEALLOCATED).on(Event.NEXT)
                    .perform(new DeallocateResourcesAction<>(persistenceManager, resourcesManager));

            builder.transition().from(State.OLD_RESOURCES_DEALLOCATED).to(State.UPDATING_FLOW_STATUS).on(Event.NEXT);
            builder.transition().from(State.OLD_RESOURCES_DEALLOCATED).to(State.UPDATING_FLOW_STATUS)
                    .on(Event.ERROR)
                    .perform(new HandleNotDeallocatedResourcesAction<>());

            builder.transition().from(State.UPDATING_FLOW_STATUS).to(State.FLOW_STATUS_UPDATED).on(Event.NEXT)
                    .perform(new UpdateFlowStatusAction(persistenceManager, dashboardLogger));

            builder.transition().from(State.FLOW_STATUS_UPDATED)
                    .to(State.NOTIFY_FLOW_MONITOR).on(Event.NEXT);
            builder.transition().from(State.FLOW_STATUS_UPDATED)
                    .to(State.NOTIFY_FLOW_MONITOR_WITH_ERROR).on(Event.ERROR);

            builder.onEntry(State.REVERTING_PATHS_SWAP)
                    .perform(reportErrorAction);
            builder.transition().from(State.REVERTING_PATHS_SWAP).to(State.PATHS_SWAP_REVERTED)
                    .on(Event.NEXT)
                    .perform(new RevertPathsSwapAction(persistenceManager));

            builder.onEntry(State.PATHS_SWAP_REVERTED)
                    .perform(reportErrorAction);
            builder.transition().from(State.PATHS_SWAP_REVERTED)
                    .to(State.REVERTING_NEW_RULES).on(Event.NEXT)
                    .perform(new RevertNewRulesAction(persistenceManager, ruleManager));

            builder.internalTransition().within(State.REVERTING_NEW_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRevertResponseAction<>(
                            config.getSpeakerCommandRetriesLimit(), Event.RULES_REMOVED, carrier));
            builder.transition().from(State.REVERTING_NEW_RULES).to(State.NEW_RULES_REVERTED)
                    .on(Event.RULES_REMOVED);
            builder.transitions().from(State.REVERTING_NEW_RULES)
                    .toAmong(State.NEW_RULES_REVERTED, State.NEW_RULES_REVERTED)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction<>());

            builder.transition().from(State.NEW_RULES_REVERTED)
                    .to(State.REVERTING_ALLOCATED_RESOURCES)
                    .on(Event.NEXT);

            builder.onEntry(State.REVERTING_ALLOCATED_RESOURCES)
                    .perform(reportErrorAction);
            builder.transition().from(State.REVERTING_ALLOCATED_RESOURCES)
                    .to(State.RESOURCES_ALLOCATION_REVERTED)
                    .on(Event.NEXT)
                    .perform(new RevertResourceAllocationAction<>(persistenceManager, resourcesManager));
            builder.transition().from(State.RESOURCES_ALLOCATION_REVERTED)
                    .to(State.REVERTING_FLOW_STATUS).on(Event.NEXT);
            builder.transition().from(State.RESOURCES_ALLOCATION_REVERTED).to(State.REVERTING_FLOW_STATUS)
                    .on(Event.ERROR)
                    .perform(new HandleNotRevertedResourceAllocationAction<>());

            builder.onEntry(State.REVERTING_FLOW_STATUS)
                    .perform(reportErrorAction);
            builder.transition().from(State.REVERTING_FLOW_STATUS)
                    .to(State.NOTIFY_FLOW_MONITOR_WITH_ERROR)
                    .on(Event.NEXT)
                    .perform(new RevertFlowStatusAction(persistenceManager));

            builder.transition()
                    .from(State.NOTIFY_FLOW_MONITOR)
                    .to(State.FINISHED)
                    .on(Event.NEXT)
                    .perform(new NotifyFlowMonitorAction<>(persistenceManager, carrier));
            builder.transition()
                    .from(State.NOTIFY_FLOW_MONITOR_WITH_ERROR)
                    .to(State.FINISHED_WITH_ERROR)
                    .on(Event.NEXT)
                    .perform(new NotifyFlowMonitorAction<>(persistenceManager, carrier));

            builder.defineFinalState(State.FINISHED)
                    .addEntryAction(new OnFinishedAction(dashboardLogger, carrier));
            builder.defineFinalState(State.FINISHED_WITH_ERROR)
                    .addEntryAction(new OnFinishedWithErrorAction(dashboardLogger, carrier));
        }

        public HaFlowRerouteFsm newInstance(@NonNull String flowId, @NonNull CommandContext commandContext,
                                            @NonNull Collection<FlowRerouteEventListener> eventListeners) {
            HaFlowRerouteFsm fsm = builder.newStateMachine(State.INITIALIZED, commandContext, carrier, flowId,
                    eventListeners);

            fsm.addTransitionCompleteListener(event ->
                    log.debug("HaFlowRerouteFsm, transition to {} on {}", event.getTargetState(), event.getCause()));

            if (fsm.getEventListeners() != null && !fsm.getEventListeners().isEmpty()) {
                fsm.addTransitionCompleteListener(event -> {
                    switch (event.getTargetState()) {
                        case FINISHED:
                            fsm.getEventListeners().forEach(listener -> listener.onCompleted(fsm.getFlowId()));
                            break;
                        case FINISHED_WITH_ERROR:
                            ErrorType errorType = Optional.ofNullable(fsm.getOperationResultMessage())
                                    .filter(message -> message instanceof ErrorMessage)
                                    .map(message -> ((ErrorMessage) message).getData())
                                    .map(ErrorData::getErrorType).orElse(ErrorType.INTERNAL_ERROR);
                            fsm.getEventListeners().forEach(listener -> listener.onFailed(fsm.getFlowId(),
                                    fsm.getErrorReason(), errorType));
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
        FLOW_VALIDATED,
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

        MARKED_FLOW_DOWN_OR_DEGRADED,
        REVERTING_ALLOCATED_RESOURCES,
        RESOURCES_ALLOCATION_REVERTED,
        REVERTING_FLOW_STATUS,

        FINISHED_WITH_ERROR,

        NOTIFY_FLOW_MONITOR,
        NOTIFY_FLOW_MONITOR_WITH_ERROR,

        NOTIFY_FLOW_STATS_ON_NEW_PATHS,
        NOTIFY_FLOW_STATS_ON_REMOVED_PATHS
    }

    public enum Event {
        NEXT,

        NO_PATH_FOUND,
        REROUTE_IS_SKIPPED,

        RESPONSE_RECEIVED,

        RULES_INSTALLED,
        RULES_REMOVED,

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
