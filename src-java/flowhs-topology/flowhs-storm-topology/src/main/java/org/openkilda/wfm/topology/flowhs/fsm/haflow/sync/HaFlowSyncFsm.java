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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.sync;

import static org.openkilda.wfm.share.history.model.HaFlowEventData.Event.FLOW_SYNC;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.metrics.MeterRegistryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.common.HaFlowProcessingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.ReportErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.CreateNewHaFlowHistoryEventAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.actions.EmitSyncRuleRequestsAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.actions.HaFlowSyncSetupAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.actions.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.actions.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.actions.OnReceivedResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.actions.UpdateHaFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.service.FlowCreateEventListener;
import org.openkilda.wfm.topology.flowhs.service.FlowProcessingEventListener;
import org.openkilda.wfm.topology.flowhs.service.haflow.HaFlowGenericCarrier;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
@Slf4j
public final class HaFlowSyncFsm extends HaFlowProcessingFsm<HaFlowSyncFsm, State, Event,
        HaFlowSyncContext, HaFlowGenericCarrier, FlowCreateEventListener> {

    private final Map<UUID, Set<PathId>> requestToPathIdsMap;

    private HaFlowSyncFsm(
            @NonNull CommandContext commandContext, @NonNull HaFlowGenericCarrier carrier, @NonNull String haFlowId,
            @NonNull Collection<FlowCreateEventListener> eventListeners) {
        super(Event.NEXT, Event.ERROR, commandContext, carrier, haFlowId, eventListeners);
        this.requestToPathIdsMap = new HashMap<>();
    }

    public void putPathRequest(UUID requestUuid, PathId pathId) {
        requestToPathIdsMap.putIfAbsent(requestUuid, new HashSet<>());
        requestToPathIdsMap.get(requestUuid).add(pathId);
    }

    @Override
    protected void afterTransitionCausedException(State fromState, State toState, Event event,
                                                  HaFlowSyncContext context) {
        super.afterTransitionCausedException(fromState, toState, event, context);
        String errorMessage = getLastException().getMessage();
        if (fromState == State.INITIALIZED || fromState == State.SETUP) {
            ErrorData error = new ErrorData(ErrorType.INTERNAL_ERROR, "Could not sync flow",
                    errorMessage);
            Message message = new ErrorMessage(error, getCommandContext().getCreateTime(),
                    getCommandContext().getCorrelationId());
            sendNorthboundResponse(message);
        }

        fireError(errorMessage);
    }

    @Override
    protected String getCrudActionName() {
        return "sync";
    }

    public static class Factory {
        private final StateMachineBuilder<HaFlowSyncFsm, State, Event, HaFlowSyncContext> builder;
        private final HaFlowGenericCarrier carrier;

        public Factory(@NonNull HaFlowGenericCarrier carrier, @NonNull PersistenceManager persistenceManager,
                       @NonNull RuleManager ruleManager, @NonNull Config config) {
            this.carrier = carrier;
            this.builder = StateMachineBuilderFactory.create(HaFlowSyncFsm.class, State.class, Event.class,
                    HaFlowSyncContext.class, CommandContext.class, HaFlowGenericCarrier.class, String.class,
                    Collection.class);

            FlowOperationsDashboardLogger dashboardLogger = new FlowOperationsDashboardLogger(log);

            final ReportErrorAction<HaFlowSyncFsm, State, Event, HaFlowSyncContext>
                    reportErrorAction = new ReportErrorAction<>(Event.TIMEOUT);

            builder.transition()
                    .from(State.CREATE_NEW_HISTORY_EVENT)
                    .to(State.INITIALIZED)
                    .on(Event.NEXT)
                    .perform(new CreateNewHaFlowHistoryEventAction<>(persistenceManager, FLOW_SYNC));

            builder.transition()
                    .from(State.INITIALIZED)
                    .to(State.SETUP)
                    .on(Event.NEXT)
                    .perform(new HaFlowSyncSetupAction(persistenceManager, dashboardLogger));

            builder.transitions()
                    .from(State.SETUP)
                    .toAmong(State.SYNC_FAIL, State.SYNC_FAIL)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            // install and validate transit and egress rules
            builder.externalTransition()
                    .from(State.SETUP)
                    .to(State.SYNCING_RULES)
                    .on(Event.NEXT)
                    .perform(new EmitSyncRuleRequestsAction(persistenceManager, ruleManager));

            // skip installation on transit and egress rules for one switch flow
            builder.externalTransition()
                    .from(State.SYNCING_RULES)
                    .to(State.SYNC_SUCCESS)
                    .on(Event.SKIP_RULES_SYNC);

            builder.internalTransition()
                    .within(State.SYNCING_RULES)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedResponseAction(
                            config.speakerCommandRetriesLimit, Event.RULES_SYNED, carrier));

            builder.transitions()
                    .from(State.SYNCING_RULES)
                    .toAmong(State.SYNC_FAIL, State.SYNC_FAIL)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition()
                    .from(State.SYNCING_RULES)
                    .to(State.SYNC_SUCCESS)
                    .on(Event.RULES_SYNED);

            builder.onEntry(State.SYNC_SUCCESS)
                    .perform(new UpdateHaFlowStatusAction(persistenceManager, dashboardLogger));

            builder.transition()
                    .from(State.SYNC_SUCCESS)
                    .to(State.FINISHED)
                    .on(Event.NEXT);

            builder.transitions()
                    .from(State.SYNC_SUCCESS)
                    .toAmong(State.FINISHED_WITH_ERROR, State.FINISHED_WITH_ERROR)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.onEntry(State.SYNC_FAIL)
                    .perform(new UpdateHaFlowStatusAction(persistenceManager, dashboardLogger));

            builder.transitions()
                    .from(State.SYNC_FAIL)
                    .toAmong(State.FINISHED_WITH_ERROR, State.FINISHED_WITH_ERROR)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.onEntry(State.FINISHED_WITH_ERROR)
                    .perform(reportErrorAction);

            builder.defineFinalState(State.FINISHED)
                    .addEntryAction(new OnFinishedAction(dashboardLogger));
            builder.defineFinalState(State.FINISHED_WITH_ERROR)
                    .addEntryAction(new OnFinishedWithErrorAction(dashboardLogger));
        }

        public HaFlowSyncFsm newInstance(@NonNull CommandContext commandContext, @NonNull String haFlowId,
                                         @NonNull Collection<FlowProcessingEventListener> eventListeners) {
            HaFlowSyncFsm fsm = builder.newStateMachine(State.CREATE_NEW_HISTORY_EVENT, commandContext, carrier,
                    haFlowId, eventListeners);

            fsm.addTransitionCompleteListener(event ->
                    log.debug("HaFlowSyncFsm, transition to {} on {}", event.getTargetState(), event.getCause()));

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

    @Value
    @Builder
    public static class Config implements Serializable {
        @Builder.Default
        int speakerCommandRetriesLimit = 3;
    }

    public enum State {
        CREATE_NEW_HISTORY_EVENT,
        INITIALIZED,
        SETUP,
        SYNCING_RULES,
        SYNC_SUCCESS,
        SYNC_FAIL,
        FINISHED,
        FINISHED_WITH_ERROR;
    }

    public enum Event {
        NEXT,

        RESPONSE_RECEIVED,
        SKIP_RULES_SYNC,
        RULES_SYNED,

        TIMEOUT,
        ERROR
    }
}
