/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.delete;

import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.messaging.Message;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.NbTrackableFsm;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.delete.actions.CompleteFlowPathRemovalAction;
import org.openkilda.wfm.topology.flowhs.fsm.delete.actions.DeallocateResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.delete.actions.HandleNotCompletedCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.delete.actions.HandleNotDeallocatedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.delete.actions.HandleNotRemovedPathsAction;
import org.openkilda.wfm.topology.flowhs.fsm.delete.actions.OnErrorResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.delete.actions.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.delete.actions.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.delete.actions.OnReceivedResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.delete.actions.RemoveFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.delete.actions.RemoveRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.delete.actions.RevertFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.delete.actions.ValidateFlowAction;
import org.openkilda.wfm.topology.flowhs.service.FlowDeleteHubCarrier;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
@Slf4j
public final class FlowDeleteFsm extends NbTrackableFsm<FlowDeleteFsm, State, Event, FlowDeleteContext> {

    private final FlowDeleteHubCarrier carrier;

    @Setter
    private FlowStatus originalFlowStatus;
    private final Collection<FlowResources> flowResources = new ArrayList<>();

    @NonNull
    private Set<UUID> pendingCommands = new HashSet<>();
    private final Map<UUID, Integer> retriedCommands = new HashMap<>();
    private final Map<UUID, FlowErrorResponse> failedCommands = new HashMap<>();

    @NonNull
    private Map<UUID, FlowSegmentRequestFactory> removeCommands = emptyMap();

    public FlowDeleteFsm(CommandContext commandContext, FlowDeleteHubCarrier carrier, String flowId) {
        super(commandContext, flowId);
        this.carrier = carrier;
    }

    @Override
    public void fireError(String errorReason) {
        fireError(Event.ERROR, errorReason);
    }

    public void setRemoveCommands(@NonNull Map<UUID, FlowSegmentRequestFactory> removeCommands) {
        this.removeCommands = unmodifiableMap(removeCommands);
    }

    public void setPendingCommands(@NonNull Set<UUID> pendingCommands) {
        this.pendingCommands = new HashSet<>(pendingCommands);
    }

    public boolean removePendingCommand(UUID commandId) {
        return pendingCommands.remove(commandId);
    }

    public void resetPendingCommands() {
        pendingCommands.clear();
    }

    public void resetFlowResources() {
        flowResources.clear();
    }

    @Override
    public void sendResponse(Message message) {
        carrier.sendNorthboundResponse(message);
    }

    public static class Factory {
        private final StateMachineBuilder<FlowDeleteFsm, State, Event, FlowDeleteContext> builder;
        private final FlowDeleteHubCarrier carrier;

        public Factory(FlowDeleteHubCarrier carrier, PersistenceManager persistenceManager,
                       FlowResourcesManager resourcesManager,
                       int transactionRetriesLimit, int speakerCommandRetriesLimit) {
            this.carrier = carrier;

            builder = StateMachineBuilderFactory.create(FlowDeleteFsm.class, State.class, Event.class,
                    FlowDeleteContext.class, CommandContext.class, FlowDeleteHubCarrier.class, String.class);

            FlowOperationsDashboardLogger dashboardLogger = new FlowOperationsDashboardLogger(log);

            builder.transition().from(State.INITIALIZED).to(State.FLOW_VALIDATED).on(Event.NEXT)
                    .perform(new ValidateFlowAction(persistenceManager, dashboardLogger));
            builder.transition().from(State.INITIALIZED).to(State.FINISHED_WITH_ERROR).on(Event.TIMEOUT);

            builder.transition().from(State.FLOW_VALIDATED).to(State.REMOVING_RULES).on(Event.NEXT)
                    .perform(new RemoveRulesAction(persistenceManager, resourcesManager));
            builder.transitions().from(State.FLOW_VALIDATED)
                    .toAmong(State.REVERTING_FLOW_STATUS, State.REVERTING_FLOW_STATUS)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.internalTransition().within(State.REMOVING_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedResponseAction());
            builder.internalTransition().within(State.REMOVING_RULES).on(Event.ERROR_RECEIVED)
                    .perform(new OnErrorResponseAction(speakerCommandRetriesLimit));
            builder.transition().from(State.REMOVING_RULES).to(State.RULES_REMOVED)
                    .on(Event.RULES_REMOVED);
            builder.transition().from(State.REMOVING_RULES).to(State.REVERTING_FLOW_STATUS)
                    .on(Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction());

            builder.transition().from(State.RULES_REMOVED).to(State.PATHS_REMOVED).on(Event.NEXT)
                    .perform(new CompleteFlowPathRemovalAction(persistenceManager, transactionRetriesLimit));

            builder.transition().from(State.PATHS_REMOVED).to(State.DEALLOCATING_RESOURCES)
                    .on(Event.NEXT);
            builder.transition().from(State.PATHS_REMOVED).to(State.DEALLOCATING_RESOURCES)
                    .on(Event.ERROR)
                    .perform(new HandleNotRemovedPathsAction(persistenceManager));

            builder.transition().from(State.DEALLOCATING_RESOURCES).to(State.RESOURCES_DEALLOCATED).on(Event.NEXT)
                    .perform(new DeallocateResourcesAction(persistenceManager, resourcesManager));

            builder.transition().from(State.RESOURCES_DEALLOCATED).to(State.REMOVING_FLOW).on(Event.NEXT);
            builder.transition().from(State.RESOURCES_DEALLOCATED).to(State.REMOVING_FLOW)
                    .on(Event.ERROR)
                    .perform(new HandleNotDeallocatedResourcesAction());

            builder.transition().from(State.REMOVING_FLOW).to(State.FLOW_REMOVED).on(Event.NEXT)
                    .perform(new RemoveFlowAction(persistenceManager, transactionRetriesLimit));

            builder.transition().from(State.FLOW_REMOVED).to(State.FINISHED).on(Event.NEXT);
            builder.transition().from(State.FLOW_REMOVED).to(State.FINISHED_WITH_ERROR).on(Event.ERROR);

            builder.transitions().from(State.REVERTING_FLOW_STATUS)
                    .toAmong(State.FINISHED_WITH_ERROR, State.FINISHED_WITH_ERROR)
                    .onEach(Event.NEXT, Event.ERROR)
                    .perform(new RevertFlowStatusAction(persistenceManager));

            builder.defineFinalState(State.FINISHED)
                    .addEntryAction(new OnFinishedAction(dashboardLogger));
            builder.defineFinalState(State.FINISHED_WITH_ERROR)
                    .addEntryAction(new OnFinishedWithErrorAction(dashboardLogger));
        }

        public FlowDeleteFsm newInstance(CommandContext commandContext, String flowId) {
            return builder.newStateMachine(State.INITIALIZED, commandContext, carrier, flowId);
        }
    }

    public enum State {
        INITIALIZED,
        FLOW_VALIDATED,

        REMOVING_RULES,
        RULES_REMOVED,

        PATHS_REMOVED,

        DEALLOCATING_RESOURCES,
        RESOURCES_DEALLOCATED,

        REMOVING_FLOW,
        FLOW_REMOVED,

        FINISHED,

        REVERTING_FLOW_STATUS,

        FINISHED_WITH_ERROR
    }

    public enum Event {
        NEXT,

        RESPONSE_RECEIVED,
        ERROR_RECEIVED,

        RULES_REMOVED,

        TIMEOUT,
        ERROR
    }
}
