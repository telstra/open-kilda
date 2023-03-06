/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.pathswap;

import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.metrics.MeterRegistryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowPathSwappingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NotifyFlowMonitorAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.ReportErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions.AbandonPendingCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions.EmitIngressRulesVerifyRequestsAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions.FlowValidateAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions.HandleNotCompletedCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions.InstallIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions.OnReceivedInstallResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions.OnReceivedRemoveOrRevertResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions.RecalculateFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions.RemoveOldRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions.RevertNewRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions.RevertPathsSwapAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions.UpdateFlowPathsAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions.UpdateFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions.ValidateIngressRulesAction;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
@Slf4j
public final class FlowPathSwapFsm extends FlowPathSwappingFsm<FlowPathSwapFsm, State, Event, FlowPathSwapContext,
        FlowPathSwapHubCarrier, FlowProcessingEventListener> {

    private final Map<UUID, InstallSpeakerCommandsRequest> installSpeakerRequests = new HashMap<>();
    private final Map<UUID, DeleteSpeakerCommandsRequest> deleteSpeakerRequests = new HashMap<>();

    public FlowPathSwapFsm(@NonNull CommandContext commandContext, @NonNull FlowPathSwapHubCarrier carrier,
                           @NonNull String flowId,
                           @NonNull Collection<FlowProcessingEventListener> eventListeners) {
        super(Event.NEXT, Event.ERROR, commandContext, carrier, flowId, eventListeners);
    }

    @Override
    public void fireNoPathFound(String errorReason) {

    }

    public void addInstallSpeakerCommand(UUID key, InstallSpeakerCommandsRequest command) {
        installSpeakerRequests.put(key, command);
    }

    public Optional<InstallSpeakerCommandsRequest> getInstallSpeakerCommand(UUID key) {
        return Optional.ofNullable(installSpeakerRequests.get(key));
    }

    public void addDeleteSpeakerCommand(UUID key, DeleteSpeakerCommandsRequest command) {
        deleteSpeakerRequests.put(key, command);
    }

    public Optional<DeleteSpeakerCommandsRequest> getDeleteSpeakerCommand(UUID key) {
        return Optional.ofNullable(deleteSpeakerRequests.get(key));
    }

    @Override
    protected String getCrudActionName() {
        return "path swap";
    }

    public static class Factory {
        private final StateMachineBuilder<FlowPathSwapFsm, State, Event, FlowPathSwapContext> builder;
        private final FlowPathSwapHubCarrier carrier;

        public Factory(@NonNull FlowPathSwapHubCarrier carrier, @NonNull PersistenceManager persistenceManager,
                       @NonNull FlowResourcesManager resourcesManager, @NonNull RuleManager ruleManager,
                       int speakerCommandRetriesLimit) {
            this.carrier = carrier;

            final ReportErrorAction<FlowPathSwapFsm, State, Event, FlowPathSwapContext>
                    reportErrorAction = new ReportErrorAction<>(Event.TIMEOUT);

            builder = StateMachineBuilderFactory.create(FlowPathSwapFsm.class, State.class, Event.class,
                    FlowPathSwapContext.class, CommandContext.class, FlowPathSwapHubCarrier.class, String.class,
                    Collection.class);

            FlowOperationsDashboardLogger dashboardLogger = new FlowOperationsDashboardLogger(log);

            builder.transition().from(State.INITIALIZED).to(State.FLOW_VALIDATED).on(Event.NEXT)
                    .perform(new FlowValidateAction(persistenceManager, dashboardLogger));
            builder.transition().from(State.INITIALIZED).to(State.FINISHED_WITH_ERROR).on(Event.TIMEOUT);

            builder.transition().from(State.FLOW_VALIDATED).to(State.FLOW_UPDATED).on(Event.NEXT)
                    .perform(new UpdateFlowPathsAction(persistenceManager));
            builder.transitions().from(State.FLOW_VALIDATED)
                    .toAmong(State.REVERTING_FLOW_STATUS, State.REVERTING_FLOW_STATUS)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.FLOW_UPDATED).to(State.INSTALLING_INGRESS_RULES).on(Event.NEXT)
                    .perform(new InstallIngressRulesAction(persistenceManager, resourcesManager));
            builder.transitions().from(State.FLOW_UPDATED)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.internalTransition().within(State.INSTALLING_INGRESS_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(speakerCommandRetriesLimit));
            builder.transition().from(State.INSTALLING_INGRESS_RULES).to(State.INGRESS_RULES_INSTALLED)
                    .on(Event.RULES_INSTALLED);
            builder.transitions().from(State.INSTALLING_INGRESS_RULES)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new AbandonPendingCommandsAction());

            builder.transition().from(State.INGRESS_RULES_INSTALLED).to(State.VALIDATING_INGRESS_RULES)
                    .on(Event.NEXT).perform(new EmitIngressRulesVerifyRequestsAction());
            builder.transitions().from(State.INGRESS_RULES_INSTALLED)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.internalTransition().within(State.VALIDATING_INGRESS_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new ValidateIngressRulesAction(speakerCommandRetriesLimit));
            builder.transition().from(State.VALIDATING_INGRESS_RULES).to(State.INGRESS_RULES_VALIDATED)
                    .on(Event.RULES_VALIDATED);
            builder.transitions().from(State.VALIDATING_INGRESS_RULES)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.MISSING_RULE_FOUND, Event.ERROR);

            builder.transition().from(State.INGRESS_RULES_VALIDATED).to(State.REMOVING_OLD_RULES).on(Event.NEXT)
                    .perform(new RemoveOldRulesAction(persistenceManager, resourcesManager));

            builder.internalTransition().within(State.REMOVING_OLD_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRemoveOrRevertResponseAction(speakerCommandRetriesLimit));
            builder.transitions().from(State.REMOVING_OLD_RULES)
                    .toAmong(State.OLD_RULES_REMOVED, State.OLD_RULES_REMOVED)
                    .onEach(Event.RULES_REMOVED, Event.ERROR);

            builder.onEntry(State.OLD_RULES_REMOVED).perform(reportErrorAction);
            builder.transition().from(State.OLD_RULES_REMOVED).to(State.UPDATING_FLOW_STATUS)
                    .on(Event.NEXT);

            builder.transition().from(State.UPDATING_FLOW_STATUS).to(State.FLOW_STATUS_UPDATED).on(Event.NEXT)
                    .perform(new UpdateFlowStatusAction(persistenceManager, dashboardLogger));

            builder.onEntry(State.REVERTING_PATHS_SWAP).perform(reportErrorAction);
            builder.transition().from(State.REVERTING_PATHS_SWAP).to(State.PATHS_SWAP_REVERTED)
                    .on(Event.NEXT)
                    .perform(new RevertPathsSwapAction(persistenceManager));

            builder.transition().from(State.PATHS_SWAP_REVERTED)
                    .to(State.REVERTING_NEW_RULES)
                    .on(Event.NEXT)
                    .perform(new RevertNewRulesAction(persistenceManager, resourcesManager));

            builder.internalTransition().within(State.REVERTING_NEW_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRemoveOrRevertResponseAction(speakerCommandRetriesLimit));
            builder.transition().from(State.REVERTING_NEW_RULES).to(State.NEW_RULES_REVERTED)
                    .on(Event.RULES_REMOVED);
            builder.transition().from(State.REVERTING_NEW_RULES).to(State.NEW_RULES_REVERTED)
                    .on(Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction());

            builder.transition().from(State.NEW_RULES_REVERTED)
                    .to(State.REVERTING_FLOW_STATUS)
                    .on(Event.NEXT)
                    .perform(new RecalculateFlowStatusAction(persistenceManager,
                            dashboardLogger));

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
                    .perform(new NotifyFlowMonitorAction<>(persistenceManager, carrier));
            builder.transition()
                    .from(State.NOTIFY_FLOW_MONITOR_WITH_ERROR)
                    .to(State.FINISHED_WITH_ERROR)
                    .on(Event.NEXT)
                    .perform(new NotifyFlowMonitorAction<>(persistenceManager, carrier));

            builder.defineFinalState(State.FINISHED)
                    .addEntryAction(new OnFinishedAction(dashboardLogger));
            builder.defineFinalState(State.FINISHED_WITH_ERROR)
                    .addEntryAction(new OnFinishedWithErrorAction(dashboardLogger));
        }

        public FlowPathSwapFsm newInstance(@NonNull CommandContext commandContext, @NonNull String flowId,
                                           @NonNull Collection<FlowProcessingEventListener> eventListeners) {
            FlowPathSwapFsm fsm = builder.newStateMachine(State.INITIALIZED, commandContext, carrier, flowId,
                    eventListeners);

            fsm.addTransitionCompleteListener(event ->
                    log.debug("FlowPathSwapFsm, transition to {} on {}", event.getTargetState(), event.getCause()));

            if (!eventListeners.isEmpty()) {
                fsm.addTransitionCompleteListener(event -> {
                    switch (event.getTargetState()) {
                        case FINISHED:
                            fsm.notifyEventListeners(listener -> listener.onCompleted(flowId));
                            break;
                        case FINISHED_WITH_ERROR:
                            fsm.notifyEventListeners(listener ->
                                    listener.onFailed(flowId, fsm.getErrorReason(), ErrorType.INTERNAL_ERROR));
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
        FLOW_UPDATED,

        INSTALLING_INGRESS_RULES,
        INGRESS_RULES_INSTALLED,
        VALIDATING_INGRESS_RULES,
        INGRESS_RULES_VALIDATED,

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

        REVERTING_FLOW_STATUS,
        REVERTING_FLOW,

        FINISHED_WITH_ERROR,

        NOTIFY_FLOW_MONITOR,
        NOTIFY_FLOW_MONITOR_WITH_ERROR
    }

    public enum Event {
        NEXT,

        RESPONSE_RECEIVED,

        RULES_INSTALLED,
        RULES_VALIDATED,
        MISSING_RULE_FOUND,

        RULES_REMOVED,

        TIMEOUT,
        ERROR
    }
}
