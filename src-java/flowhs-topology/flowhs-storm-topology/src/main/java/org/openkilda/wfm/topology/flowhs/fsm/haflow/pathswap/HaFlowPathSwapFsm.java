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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.metrics.MeterRegistryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.common.HaFlowPathSwappingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.ReportErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.HandleNotCompletedCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.NotifyHaFlowMonitorAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.OnReceivedInstallResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.OnReceivedRemoveResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.OnReceivedRevertResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.actions.AbandonPendingCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.actions.HaFlowValidateAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.actions.InstallIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.actions.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.actions.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.actions.RecalculateFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.actions.RemoveOldRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.actions.RevertNewRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.actions.RevertPathsSwapAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.actions.UpdateHaFlowPathsAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.actions.UpdateHaFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.service.FlowPathSwapHubCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowProcessingEventListener;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.LongTaskTimer.Sample;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
@Slf4j
public final class HaFlowPathSwapFsm extends HaFlowPathSwappingFsm<HaFlowPathSwapFsm, State, Event,
        HaFlowPathSwapContext, FlowPathSwapHubCarrier, FlowProcessingEventListener> {

    public HaFlowPathSwapFsm(
            @NonNull CommandContext commandContext, @NonNull FlowPathSwapHubCarrier carrier, @NonNull String haFlow,
            @NonNull Collection<FlowProcessingEventListener> eventListeners) {
        super(Event.NEXT, Event.ERROR, commandContext, carrier, haFlow, eventListeners);
    }

    @Override
    public void fireNoPathFound(String errorReason) {
        // no action required
    }

    @Override
    protected String getCrudActionName() {
        return "HA-path swap";
    }

    public static class Factory {
        private final StateMachineBuilder<HaFlowPathSwapFsm, State, Event, HaFlowPathSwapContext> builder;
        private final FlowPathSwapHubCarrier carrier;

        public Factory(@NonNull FlowPathSwapHubCarrier carrier, @NonNull PersistenceManager persistenceManager,
                       @NonNull RuleManager ruleManager, int speakerCommandRetriesLimit) {
            this.carrier = carrier;

            final ReportErrorAction<HaFlowPathSwapFsm, State, Event, HaFlowPathSwapContext>
                    reportErrorAction = new ReportErrorAction<>(Event.TIMEOUT);

            builder = StateMachineBuilderFactory.create(HaFlowPathSwapFsm.class, State.class, Event.class,
                    HaFlowPathSwapContext.class, CommandContext.class, FlowPathSwapHubCarrier.class, String.class,
                    Collection.class);

            FlowOperationsDashboardLogger dashboardLogger = new FlowOperationsDashboardLogger(log);

            builder.transition().from(State.INITIALIZED).to(State.FLOW_VALIDATED).on(Event.NEXT)
                    .perform(new HaFlowValidateAction(persistenceManager, dashboardLogger));
            builder.transition().from(State.INITIALIZED).to(State.FINISHED_WITH_ERROR).on(Event.TIMEOUT);

            builder.transition().from(State.FLOW_VALIDATED).to(State.FLOW_UPDATED).on(Event.NEXT)
                    .perform(new UpdateHaFlowPathsAction(persistenceManager));
            builder.transitions().from(State.FLOW_VALIDATED)
                    .toAmong(State.REVERTING_FLOW_STATUS, State.REVERTING_FLOW_STATUS)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.FLOW_UPDATED).to(State.INSTALLING_INGRESS_RULES).on(Event.NEXT)
                    .perform(new InstallIngressRulesAction(persistenceManager, ruleManager));
            builder.transitions().from(State.FLOW_UPDATED)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.internalTransition().within(State.INSTALLING_INGRESS_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction<>(
                            speakerCommandRetriesLimit, Event.RULES_INSTALLED, carrier));
            builder.transition().from(State.INSTALLING_INGRESS_RULES).to(State.INGRESS_RULES_INSTALLED)
                    .on(Event.RULES_INSTALLED);
            builder.transitions().from(State.INSTALLING_INGRESS_RULES)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new AbandonPendingCommandsAction());

            builder.transition().from(State.INGRESS_RULES_INSTALLED).to(State.REMOVING_OLD_RULES).on(Event.NEXT)
                    .perform(new RemoveOldRulesAction(persistenceManager, ruleManager));

            builder.internalTransition().within(State.REMOVING_OLD_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRemoveResponseAction<>(
                            speakerCommandRetriesLimit, Event.RULES_REMOVED, carrier));
            builder.transitions().from(State.REMOVING_OLD_RULES)
                    .toAmong(State.OLD_RULES_REMOVED, State.OLD_RULES_REMOVED)
                    .onEach(Event.RULES_REMOVED, Event.ERROR);

            builder.onEntry(State.OLD_RULES_REMOVED).perform(reportErrorAction);
            builder.transition().from(State.OLD_RULES_REMOVED).to(State.UPDATING_FLOW_STATUS)
                    .on(Event.NEXT);

            builder.transition().from(State.UPDATING_FLOW_STATUS).to(State.FLOW_STATUS_UPDATED).on(Event.NEXT)
                    .perform(new UpdateHaFlowStatusAction(persistenceManager, dashboardLogger));

            builder.onEntry(State.REVERTING_PATHS_SWAP).perform(reportErrorAction);
            builder.transition().from(State.REVERTING_PATHS_SWAP).to(State.PATHS_SWAP_REVERTED)
                    .on(Event.NEXT)
                    .perform(new RevertPathsSwapAction(persistenceManager));

            builder.transition().from(State.PATHS_SWAP_REVERTED)
                    .to(State.REVERTING_NEW_RULES)
                    .on(Event.NEXT)
                    .perform(new RevertNewRulesAction(persistenceManager, ruleManager));

            builder.internalTransition().within(State.REVERTING_NEW_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRevertResponseAction<>(
                            speakerCommandRetriesLimit, Event.RULES_REVERTED, carrier));
            builder.transition().from(State.REVERTING_NEW_RULES).to(State.NEW_RULES_REVERTED)
                    .on(Event.RULES_REVERTED);
            builder.transition().from(State.REVERTING_NEW_RULES).to(State.NEW_RULES_REVERTED)
                    .on(Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction<>());

            builder.transition().from(State.NEW_RULES_REVERTED)
                    .to(State.REVERTING_FLOW_STATUS)
                    .on(Event.NEXT)
                    .perform(new RecalculateFlowStatusAction(persistenceManager, dashboardLogger));

            builder.transition().from(State.REVERTING_FLOW_STATUS)
                    .to(State.NOTIFY_FLOW_MONITOR_WITH_ERROR)
                    .on(Event.NEXT);

            builder.transition().from(State.FLOW_STATUS_UPDATED).to(State.NOTIFY_FLOW_MONITOR).on(Event.NEXT);
            builder.transition().from(State.FLOW_STATUS_UPDATED)
                    .to(State.NOTIFY_FLOW_MONITOR_WITH_ERROR).on(Event.ERROR);

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

        public HaFlowPathSwapFsm newInstance(
                @NonNull CommandContext commandContext, @NonNull String haFlowId,
                @NonNull Collection<FlowProcessingEventListener> eventListeners) {
            HaFlowPathSwapFsm fsm = builder.newStateMachine(State.INITIALIZED, commandContext, carrier, haFlowId,
                    eventListeners);

            fsm.addTransitionCompleteListener(event ->
                    log.debug("HaFlowPathSwapFsm, transition to {} on {}", event.getTargetState(), event.getCause()));

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

        INSTALLING_INGRESS_RULES,
        INGRESS_RULES_INSTALLED,

        REMOVING_OLD_RULES,
        OLD_RULES_REMOVED,

        UPDATING_FLOW_STATUS,
        FLOW_STATUS_UPDATED,

        FINISHED,

        REVERTING_PATHS_SWAP,
        PATHS_SWAP_REVERTED,
        REVERTING_NEW_RULES,
        NEW_RULES_REVERTED,

        REVERTING_FLOW_STATUS,

        FINISHED_WITH_ERROR,

        NOTIFY_FLOW_MONITOR,
        NOTIFY_FLOW_MONITOR_WITH_ERROR
    }

    public enum Event {
        NEXT,

        RESPONSE_RECEIVED,

        RULES_INSTALLED,

        RULES_REMOVED,
        RULES_REVERTED,

        TIMEOUT,
        ERROR
    }
}
