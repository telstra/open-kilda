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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.delete;

import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.metrics.MeterRegistryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.common.YFlowProcessingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.RevertYFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.actions.CompleteYFlowRemovalAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.actions.DeallocateYFlowResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.actions.HandleNotCompletedCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.actions.HandleNotDeallocatedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.actions.HandleNotRemovedSubFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.actions.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.actions.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.actions.OnReceivedRemoveResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.actions.OnSubFlowRemovedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.actions.RemoveSubFlowsAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.actions.RemoveYFlowMetersAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.actions.StartRemovingYFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.actions.ValidateYFlowAction;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources;
import org.openkilda.wfm.topology.flowhs.service.FlowDeleteService;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
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
public final class YFlowDeleteFsm extends YFlowProcessingFsm<YFlowDeleteFsm, State, Event, YFlowDeleteContext,
        FlowGenericCarrier, YFlowEventListener> {
    private FlowStatus originalYFlowStatus;
    private YFlowResources oldResources;

    private final Set<String> subFlows = new HashSet<>();
    private final Set<String> deletingSubFlows = new HashSet<>();
    private final Set<String> failedSubFlows = new HashSet<>();

    private Collection<DeleteSpeakerCommandsRequest> deleteOldYFlowCommands;

    private YFlowDeleteFsm(@NonNull CommandContext commandContext, @NonNull FlowGenericCarrier carrier,
                           @NonNull String yFlowId, @NonNull Collection<YFlowEventListener> eventListeners) {
        super(Event.NEXT, Event.ERROR, commandContext, carrier, yFlowId, eventListeners);
    }

    public void addSubFlow(String flowId) {
        subFlows.add(flowId);
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

    @Override
    protected String getCrudActionName() {
        return "delete";
    }

    public static class Factory {
        private final StateMachineBuilder<YFlowDeleteFsm, State, Event, YFlowDeleteContext> builder;
        private final FlowGenericCarrier carrier;

        public Factory(@NonNull FlowGenericCarrier carrier, @NonNull PersistenceManager persistenceManager,
                       @NonNull FlowResourcesManager resourcesManager, @NonNull RuleManager ruleManager,
                       @NonNull FlowDeleteService flowDeleteService, int speakerCommandRetriesLimit) {
            this.carrier = carrier;

            builder = StateMachineBuilderFactory.create(YFlowDeleteFsm.class, State.class, Event.class,
                    YFlowDeleteContext.class, CommandContext.class, FlowGenericCarrier.class, String.class,
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
                    .to(State.REMOVING_SUB_FLOWS)
                    .on(Event.NEXT)
                    .perform(new StartRemovingYFlowAction(persistenceManager, ruleManager));
            builder.transitions()
                    .from(State.YFLOW_VALIDATED)
                    .toAmong(State.REVERTING_YFLOW_STATUS, State.REVERTING_YFLOW_STATUS)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.defineParallelStatesOn(State.REMOVING_SUB_FLOWS, State.SUB_FLOW_REMOVAL_STARTED);
            builder.defineState(State.SUB_FLOW_REMOVAL_STARTED)
                    .addEntryAction(new RemoveSubFlowsAction(persistenceManager, flowDeleteService));

            builder.internalTransition()
                    .within(State.REMOVING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_REMOVED)
                    .perform(new OnSubFlowRemovedAction(persistenceManager, flowDeleteService));
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
                    .to(State.REMOVING_YFLOW_METERS)
                    .on(Event.NEXT)
                    .perform(new RemoveYFlowMetersAction(persistenceManager, ruleManager));

            builder.internalTransition()
                    .within(State.REMOVING_YFLOW_METERS)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRemoveResponseAction(speakerCommandRetriesLimit));
            builder.transition()
                    .from(State.REMOVING_YFLOW_METERS)
                    .to(State.YFLOW_METERS_REMOVED)
                    .on(Event.ALL_YFLOW_METERS_REMOVED);
            builder.transitions()
                    .from(State.REMOVING_YFLOW_METERS)
                    .toAmong(State.YFLOW_RESOURCES_DEALLOCATED, State.YFLOW_RESOURCES_DEALLOCATED)
                    .onEach(Event.ERROR, Event.TIMEOUT)
                    .perform(new HandleNotCompletedCommandsAction());

            builder.transition()
                    .from(State.YFLOW_METERS_REMOVED)
                    .to(State.YFLOW_RESOURCES_DEALLOCATED)
                    .on(Event.NEXT)
                    .perform(new DeallocateYFlowResourcesAction(persistenceManager, resourcesManager));
            builder.transitions()
                    .from(State.YFLOW_RESOURCES_DEALLOCATED)
                    .toAmong(State.YFLOW_RESOURCES_DEALLOCATED, State.YFLOW_RESOURCES_DEALLOCATED)
                    .onEach(Event.ERROR, Event.TIMEOUT)
                    .perform(new HandleNotDeallocatedResourcesAction());


            builder.transition()
                    .from(State.YFLOW_RESOURCES_DEALLOCATED)
                    .to(State.YFLOW_REMOVED)
                    .on(Event.NEXT)
                    .perform(new CompleteYFlowRemovalAction(persistenceManager));
            builder.transitions()
                    .from(State.YFLOW_REMOVED)
                    .toAmong(State.FINISHED, State.FINISHED, State.FINISHED)
                    .onEach(Event.NEXT, Event.ERROR, Event.TIMEOUT);

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

        public YFlowDeleteFsm newInstance(@NonNull CommandContext commandContext, @NonNull String yFlowId,
                                          @NonNull Collection<YFlowEventListener> eventListeners) {
            YFlowDeleteFsm fsm = builder.newStateMachine(State.INITIALIZED, commandContext, carrier, yFlowId,
                    eventListeners);

            fsm.addTransitionCompleteListener(event ->
                    log.debug("YFlowDeleteFsm, transition to {} on {}", event.getTargetState(), event.getCause()));

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
        YFLOW_DELETE_STARTED,

        REMOVING_SUB_FLOWS,
        SUB_FLOW_REMOVAL_STARTED,
        SUB_FLOWS_REMOVED,

        REMOVING_YFLOW_METERS,
        YFLOW_METERS_REMOVED,
        DEALLOCATING_YFLOW_RESOURCES,
        YFLOW_RESOURCES_DEALLOCATED,
        YFLOW_REMOVED,

        FINISHED,

        REVERTING_YFLOW_STATUS,
        FINISHED_WITH_ERROR;
    }

    public enum Event {
        NEXT,

        RESPONSE_RECEIVED,

        SUB_FLOW_REMOVED,
        SUB_FLOW_FAILED,
        ALL_SUB_FLOWS_REMOVED,

        ALL_YFLOW_METERS_REMOVED,

        TIMEOUT,
        ERROR
    }
}
