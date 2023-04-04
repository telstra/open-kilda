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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.delete;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.HaFlowResources;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.metrics.MeterRegistryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.common.HaFlowProcessingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NotifyHaFlowMonitorAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.ReportErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.actions.CompleteHaFlowPathRemovalAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.actions.DeallocateResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.actions.HandleNotCompletedCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.actions.HandleNotDeallocatedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.actions.HandleNotRemovedPathsAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.actions.NotifyHaFlowStatsAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.actions.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.actions.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.actions.OnReceivedResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.actions.RemoveHaFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.actions.RemoveRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.actions.RevertHaFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.actions.ValidateHaFlowAction;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowProcessingEventListener;

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
import java.util.List;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
@Slf4j
public final class HaFlowDeleteFsm extends HaFlowProcessingFsm<HaFlowDeleteFsm, State, Event,
        HaFlowDeleteContext, FlowGenericCarrier, FlowProcessingEventListener> {
    private FlowStatus originalFlowStatus;

    private final List<HaFlowResources> haFlowResources = new ArrayList<>();

    public HaFlowDeleteFsm(
            @NonNull CommandContext commandContext, @NonNull FlowGenericCarrier carrier, @NonNull String haFlowId,
            @NonNull Collection<FlowProcessingEventListener> eventListeners) {
        super(Event.NEXT, Event.ERROR, commandContext, carrier, haFlowId, eventListeners);
    }

    @Override
    protected String getCrudActionName() {
        return "delete";
    }

    public static class Factory {
        private final StateMachineBuilder<HaFlowDeleteFsm, State, Event, HaFlowDeleteContext> builder;
        private final FlowGenericCarrier carrier;

        public Factory(@NonNull FlowGenericCarrier carrier, @NonNull PersistenceManager persistenceManager,
                       @NonNull FlowResourcesManager resourcesManager, @NonNull RuleManager ruleManager,
                       int speakerCommandRetriesLimit) {
            this.carrier = carrier;

            builder = StateMachineBuilderFactory.create(HaFlowDeleteFsm.class, State.class, Event.class,
                    HaFlowDeleteContext.class, CommandContext.class, FlowGenericCarrier.class, String.class,
                    Collection.class);

            final FlowOperationsDashboardLogger dashboardLogger = new FlowOperationsDashboardLogger(log);
            final ReportErrorAction<HaFlowDeleteFsm, State, Event, HaFlowDeleteContext>
                    reportErrorAction = new ReportErrorAction<>(Event.TIMEOUT);

            builder.transition().from(State.INITIALIZED).to(State.HA_FLOW_VALIDATED).on(Event.NEXT)
                    .perform(new ValidateHaFlowAction(persistenceManager, dashboardLogger));
            builder.transition().from(State.INITIALIZED).to(State.FINISHED_WITH_ERROR).on(Event.TIMEOUT);

            builder.transition().from(State.HA_FLOW_VALIDATED).to(State.REMOVING_RULES).on(Event.NEXT)
                    .perform(new RemoveRulesAction(persistenceManager, resourcesManager, ruleManager));
            builder.transitions().from(State.HA_FLOW_VALIDATED)
                    .toAmong(State.REVERTING_HA_FLOW_STATUS, State.REVERTING_HA_FLOW_STATUS)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.internalTransition().within(State.REMOVING_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedResponseAction(speakerCommandRetriesLimit));
            builder.transition().from(State.REMOVING_RULES).to(State.RULES_REMOVED)
                    .on(Event.RULES_REMOVED);
            builder.transition().from(State.REMOVING_RULES).to(State.REVERTING_HA_FLOW_STATUS)
                    .on(Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction());

            builder.transition()
                    .from(State.RULES_REMOVED)
                    .to(State.NOTIFY_HA_FLOW_STATS)
                    .on(Event.NEXT)
                    .perform(new NotifyHaFlowStatsAction(persistenceManager, carrier));

            builder.transition().from(State.NOTIFY_HA_FLOW_STATS).to(State.PATHS_REMOVED).on(Event.NEXT)
                    .perform(new CompleteHaFlowPathRemovalAction(persistenceManager));

            builder.transition().from(State.PATHS_REMOVED).to(State.DEALLOCATING_RESOURCES)
                    .on(Event.NEXT);
            builder.transition().from(State.PATHS_REMOVED).to(State.DEALLOCATING_RESOURCES)
                    .on(Event.ERROR)
                    .perform(new HandleNotRemovedPathsAction(persistenceManager));

            builder.transition().from(State.DEALLOCATING_RESOURCES).to(State.RESOURCES_DEALLOCATED).on(Event.NEXT)
                    .perform(new DeallocateResourcesAction(persistenceManager, resourcesManager));

            builder.transition().from(State.RESOURCES_DEALLOCATED).to(State.REMOVING_HA_FLOW).on(Event.NEXT);
            builder.transition().from(State.RESOURCES_DEALLOCATED).to(State.REMOVING_HA_FLOW)
                    .on(Event.ERROR)
                    .perform(new HandleNotDeallocatedResourcesAction());

            builder.transition().from(State.REMOVING_HA_FLOW).to(State.HA_FLOW_REMOVED).on(Event.NEXT)
                    .perform(new RemoveHaFlowAction(persistenceManager));

            builder.transition().from(State.HA_FLOW_REMOVED).to(State.NOTIFY_FLOW_MONITOR).on(Event.NEXT);
            builder.transition().from(State.HA_FLOW_REMOVED).to(State.NOTIFY_FLOW_MONITOR_WITH_ERROR).on(Event.ERROR);

            builder.onEntry(State.REVERTING_HA_FLOW_STATUS)
                    .perform(reportErrorAction);
            builder.transition().from(State.REVERTING_HA_FLOW_STATUS)
                    .to(State.NOTIFY_FLOW_MONITOR_WITH_ERROR)
                    .on(Event.NEXT)
                    .perform(new RevertHaFlowStatusAction(persistenceManager));

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

        public HaFlowDeleteFsm newInstance(@NonNull CommandContext commandContext, @NonNull String haFlowId,
                                           @NonNull Collection<FlowProcessingEventListener> eventListeners) {
            HaFlowDeleteFsm fsm = builder.newStateMachine(State.INITIALIZED, commandContext, carrier, haFlowId,
                    eventListeners);

            fsm.addTransitionCompleteListener(event ->
                    log.debug("HaFlowDeleteFsm, transition to {} on {}", event.getTargetState(), event.getCause()));

            if (!eventListeners.isEmpty()) {
                fsm.addTransitionCompleteListener(event -> {
                    switch (event.getTargetState()) {
                        case FINISHED:
                            fsm.notifyEventListeners(listener -> listener.onCompleted(haFlowId));
                            break;
                        case FINISHED_WITH_ERROR:
                            fsm.notifyEventListeners(listener ->
                                    listener.onFailed(haFlowId, fsm.getErrorReason(), ErrorType.INTERNAL_ERROR));
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
        HA_FLOW_VALIDATED,

        REMOVING_RULES,
        RULES_REMOVED,

        PATHS_REMOVED,

        DEALLOCATING_RESOURCES,
        RESOURCES_DEALLOCATED,

        REMOVING_HA_FLOW,
        HA_FLOW_REMOVED,

        FINISHED,

        REVERTING_HA_FLOW_STATUS,

        FINISHED_WITH_ERROR,

        NOTIFY_FLOW_MONITOR,
        NOTIFY_FLOW_MONITOR_WITH_ERROR,

        NOTIFY_HA_FLOW_STATS
    }

    public enum Event {
        NEXT,

        RESPONSE_RECEIVED,
        RULES_REMOVED,

        TIMEOUT,
        ERROR
    }
}
