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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap;

import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.metrics.MeterRegistryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.common.YFlowProcessingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions.CompleteYFlowSwappingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions.CompleteYFlowSwappingWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions.HandleNotCompletedCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions.HandleNotRevertedSubFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions.HandleNotSwappedSubFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions.HandleNotSwappedYFlowResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions.OnReceivedRevertResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions.OnReceivedUpdateResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions.OnSubFlowPathsRevertedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions.OnSubFlowPathsSwappedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions.PathSwapOnSubFlowsAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions.PrepareUpdateSharedEndpointRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions.RevertSharedEndpointRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions.RevertSubFlowsAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions.RevertYFlowResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions.RevertYFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions.SwapYFlowResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions.UpdateSharedEndpointRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions.ValidateYFlowAction;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowPathSwapService;
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
@Slf4j
public final class YFlowPathSwapFsm extends YFlowProcessingFsm<YFlowPathSwapFsm, State, Event, YFlowPathSwapContext,
        FlowGenericCarrier, YFlowEventListener> {
    private final Set<String> subFlows = new HashSet<>();
    private final Set<String> swappingSubFlows = new HashSet<>();
    private final Set<String> failedSubFlows = new HashSet<>();

    private final Set<PathId> newPrimaryPaths = new HashSet<>();
    private final Set<PathId> oldPrimaryPaths = new HashSet<>();

    private List<OfCommand> deleteOldYFlowOfCommands;
    private List<OfCommand> installNewYFlowOfCommands;

    private SwitchId oldYPoint;
    private MeterId oldMeterId;
    private SwitchId oldProtectedPathYPoint;
    private MeterId oldProtectedPathMeterId;

    private YFlowPathSwapFsm(@NonNull CommandContext commandContext, @NonNull FlowGenericCarrier carrier,
                             @NonNull String yFlowId, @NonNull Collection<YFlowEventListener> eventListeners) {
        super(Event.NEXT, Event.ERROR, commandContext, carrier, yFlowId, eventListeners);
    }

    public void addSubFlow(String flowId) {
        subFlows.add(flowId);
    }

    public boolean isSwappingSubFlow(String flowId) {
        return swappingSubFlows.contains(flowId);
    }

    public void addSwappingSubFlow(String flowId) {
        swappingSubFlows.add(flowId);
    }

    public void removeSwappingSubFlow(String flowId) {
        swappingSubFlows.remove(flowId);
    }

    public void clearSwappingSubFlows() {
        swappingSubFlows.clear();
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

    public void addNewPrimaryPath(PathId pathId) {
        newPrimaryPaths.add(pathId);
    }

    public void clearNewPrimaryPaths() {
        newPrimaryPaths.clear();
    }

    public void addOldPrimaryPath(PathId pathId) {
        oldPrimaryPaths.add(pathId);
    }

    public void clearOldPrimaryPaths() {
        oldPrimaryPaths.clear();
    }

    @Override
    protected String getCrudActionName() {
        return "path swap";
    }

    public static class Factory {
        private final StateMachineBuilder<YFlowPathSwapFsm, State, Event, YFlowPathSwapContext> builder;
        private final FlowGenericCarrier carrier;

        public Factory(@NonNull FlowGenericCarrier carrier, @NonNull PersistenceManager persistenceManager,
                       @NonNull RuleManager ruleManager, @NonNull FlowPathSwapService flowPathSwapService,
                       int speakerCommandRetriesLimit) {
            this.carrier = carrier;

            builder = StateMachineBuilderFactory.create(YFlowPathSwapFsm.class, State.class, Event.class,
                    YFlowPathSwapContext.class, CommandContext.class, FlowGenericCarrier.class, String.class,
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

            builder.transitions()
                    .from(State.YFLOW_VALIDATED)
                    .toAmong(State.FINISHED_WITH_ERROR, State.FINISHED_WITH_ERROR)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.YFLOW_VALIDATED)
                    .to(State.SWAPPING_SUB_FLOWS)
                    .on(Event.NEXT)
                    .perform(new PrepareUpdateSharedEndpointRulesAction(persistenceManager, ruleManager));

            builder.defineParallelStatesOn(State.SWAPPING_SUB_FLOWS, State.SUB_FLOW_SWAPPING_STARTED);
            builder.defineState(State.SUB_FLOW_SWAPPING_STARTED)
                    .addEntryAction(new PathSwapOnSubFlowsAction(persistenceManager, flowPathSwapService));

            builder.internalTransition()
                    .within(State.SWAPPING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_SWAPPED)
                    .perform(new OnSubFlowPathsSwappedAction());
            builder.internalTransition()
                    .within(State.SWAPPING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_FAILED)
                    .perform(new HandleNotSwappedSubFlowAction(persistenceManager));
            builder.transitions()
                    .from(State.SWAPPING_SUB_FLOWS)
                    .toAmong(State.REVERT_YFLOW, State.REVERT_YFLOW, State.REVERT_YFLOW)
                    .onEach(Event.FAILED_TO_SWAP_SUB_FLOWS, Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.SWAPPING_SUB_FLOWS)
                    .to(State.ALL_SUB_FLOWS_SWAPPED)
                    .on(Event.ALL_SUB_FLOWS_SWAPPED);

            builder.transition()
                    .from(State.ALL_SUB_FLOWS_SWAPPED)
                    .to(State.YFLOW_RESOURCES_SWAPPED)
                    .on(Event.NEXT)
                    .perform(new SwapYFlowResourcesAction(persistenceManager));

            builder.transitions()
                    .from(State.YFLOW_RESOURCES_SWAPPED)
                    .toAmong(State.REVERT_YFLOW, State.REVERT_YFLOW)
                    .onEach(Event.ERROR, Event.TIMEOUT)
                    .perform(new HandleNotSwappedYFlowResourcesAction(persistenceManager));

            builder.transition()
                    .from(State.YFLOW_RESOURCES_SWAPPED)
                    .to(State.UPDATING_SHAREDPOINT_RULES)
                    .on(Event.NEXT)
                    .perform(new UpdateSharedEndpointRulesAction(persistenceManager, ruleManager));

            builder.internalTransition()
                    .within(State.UPDATING_SHAREDPOINT_RULES)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedUpdateResponseAction(speakerCommandRetriesLimit));
            builder.transition()
                    .from(State.UPDATING_SHAREDPOINT_RULES)
                    .to(State.SHAREDPOINT_RULES_UPDATED)
                    .on(Event.ALL_YFLOW_RULES_UPDATED);
            builder.transitions()
                    .from(State.UPDATING_SHAREDPOINT_RULES)
                    .toAmong(State.REVERT_YFLOW, State.REVERT_YFLOW)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.SHAREDPOINT_RULES_UPDATED)
                    .to(State.YFLOW_SWAP_COMPLETED)
                    .on(Event.NEXT)
                    .perform(new CompleteYFlowSwappingAction(persistenceManager, dashboardLogger));
            builder.transitions()
                    .from(State.YFLOW_SWAP_COMPLETED)
                    .toAmong(State.FINISHED, State.FINISHED_WITH_ERROR,
                            State.FINISHED_WITH_ERROR)
                    .onEach(Event.NEXT, Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.REVERT_YFLOW)
                    .to(State.REVERTING_SUB_FLOWS)
                    .on(Event.NEXT)
                    .perform(new RevertYFlowResourcesAction(persistenceManager, ruleManager));

            builder.defineParallelStatesOn(State.REVERTING_SUB_FLOWS, State.SUB_FLOW_REVERTING_STARTED);
            builder.defineState(State.SUB_FLOW_REVERTING_STARTED)
                    .addEntryAction(new RevertSubFlowsAction(persistenceManager, flowPathSwapService));

            builder.internalTransition()
                    .within(State.REVERTING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_SWAPPED)
                    .perform(new OnSubFlowPathsRevertedAction());
            builder.internalTransition()
                    .within(State.REVERTING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_FAILED)
                    .perform(new HandleNotRevertedSubFlowAction());
            builder.transitions()
                    .from(State.REVERTING_SUB_FLOWS)
                    .toAmong(State.ALL_SUB_FLOWS_REVERTED, State.ALL_SUB_FLOWS_REVERTED, State.ALL_SUB_FLOWS_REVERTED)
                    .onEach(Event.FAILED_TO_REVERT_SUB_FLOWS, Event.ERROR, Event.TIMEOUT);

            builder.transition()
                    .from(State.REVERTING_SUB_FLOWS)
                    .to(State.ALL_SUB_FLOWS_REVERTED)
                    .on(Event.ALL_SUB_FLOWS_REVERTED);

            builder.transition()
                    .from(State.ALL_SUB_FLOWS_REVERTED)
                    .to(State.REVERTING_SHAREDPOINT_RULES)
                    .on(Event.NEXT)
                    .perform(new RevertSharedEndpointRulesAction(persistenceManager, ruleManager));

            builder.internalTransition()
                    .within(State.REVERTING_SHAREDPOINT_RULES)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRevertResponseAction(speakerCommandRetriesLimit));
            builder.transition()
                    .from(State.REVERTING_SHAREDPOINT_RULES)
                    .to(State.SHAREDPOINT_RULES_REVERTED)
                    .on(Event.ALL_YFLOW_RULES_REVERTED);
            builder.transitions()
                    .from(State.REVERTING_SHAREDPOINT_RULES)
                    .toAmong(State.REVERT_YFLOW_STATUS, State.REVERT_YFLOW_STATUS)
                    .onEach(Event.ERROR, Event.TIMEOUT)
                    .perform(new HandleNotCompletedCommandsAction());

            builder.transition()
                    .from(State.SHAREDPOINT_RULES_REVERTED)
                    .to(State.REVERT_YFLOW_STATUS)
                    .on(Event.NEXT);

            builder.transition()
                    .from(State.REVERT_YFLOW_STATUS)
                    .to(State.YFLOW_STATUS_REVERTED)
                    .on(Event.NEXT)
                    .perform(new RevertYFlowStatusAction(persistenceManager));

            builder.transition()
                    .from(State.YFLOW_STATUS_REVERTED)
                    .to(State.FINISHED_WITH_ERROR)
                    .on(Event.NEXT)
                    .perform(new CompleteYFlowSwappingWithErrorAction(persistenceManager));

            builder.defineFinalState(State.FINISHED)
                    .addEntryAction(new OnFinishedAction(dashboardLogger));
            builder.defineFinalState(State.FINISHED_WITH_ERROR)
                    .addEntryAction(new OnFinishedWithErrorAction(dashboardLogger));
        }

        public YFlowPathSwapFsm newInstance(@NonNull CommandContext commandContext, @NonNull String flowId,
                                            @NonNull Collection<YFlowEventListener> eventListeners) {
            YFlowPathSwapFsm fsm = builder.newStateMachine(State.INITIALIZED, commandContext, carrier, flowId,
                    eventListeners);

            fsm.addTransitionCompleteListener(event ->
                    log.debug("YFlowPathSwapFsm, transition to {} on {}", event.getTargetState(), event.getCause()));

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

        SWAPPING_SUB_FLOWS,
        SUB_FLOW_SWAPPING_STARTED,
        ALL_SUB_FLOWS_SWAPPED,

        UPDATING_SHAREDPOINT_RULES,
        SHAREDPOINT_RULES_UPDATED,

        YFLOW_RESOURCES_SWAPPED,
        COMPLETING_YFLOW_SWAP,
        YFLOW_SWAP_COMPLETED,
        FINISHED,

        REVERT_YFLOW,
        REVERTING_SUB_FLOWS,
        SUB_FLOW_REVERTING_STARTED,
        ALL_SUB_FLOWS_REVERTED,

        REVERTING_SHAREDPOINT_RULES,
        SHAREDPOINT_RULES_REVERTED,

        REVERT_YFLOW_STATUS,
        YFLOW_STATUS_REVERTED,
        FINISHED_WITH_ERROR;
    }

    public enum Event {
        NEXT,

        RESPONSE_RECEIVED,

        SUB_FLOW_SWAPPED,
        SUB_FLOW_FAILED,
        ALL_SUB_FLOWS_SWAPPED,
        FAILED_TO_SWAP_SUB_FLOWS,

        ALL_YFLOW_RULES_UPDATED,

        ALL_SUB_FLOWS_REVERTED,
        FAILED_TO_REVERT_SUB_FLOWS,

        ALL_YFLOW_RULES_REVERTED,

        TIMEOUT,
        ERROR
    }
}
