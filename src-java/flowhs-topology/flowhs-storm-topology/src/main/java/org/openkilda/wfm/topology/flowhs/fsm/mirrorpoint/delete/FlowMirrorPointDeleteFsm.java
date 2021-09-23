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

package org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete;

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.NbTrackableFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.actions.DeallocateFlowMirrorPathResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.actions.EmitCommandRequestsAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.actions.HandleNotCompletedCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.actions.HandleNotDeallocatedFlowMirrorPathResourceAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.actions.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.actions.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.actions.OnReceivedCommandResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.actions.PostFlowMirrorPathDeallocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.actions.ValidateRequestAction;
import org.openkilda.wfm.topology.flowhs.service.FlowMirrorPointDeleteHubCarrier;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Slf4j
public final class FlowMirrorPointDeleteFsm extends NbTrackableFsm<FlowMirrorPointDeleteFsm, State, Event,
        FlowMirrorPointDeleteContext, FlowMirrorPointDeleteHubCarrier> {

    private final String flowId;

    private FlowStatus flowStatus;
    private PathId mirrorPathId;
    private FlowPathStatus originalFlowMirrorPathStatus;

    private PathId flowPathId;
    private SwitchId mirrorSwitchId;

    private final Map<UUID, SwitchId> pendingCommands = new HashMap<>();
    private final Map<UUID, Integer> retriedCommands = new HashMap<>();
    private final Map<UUID, FlowErrorResponse> failedCommands = new HashMap<>();
    private final Map<UUID, FlowSegmentRequestFactory> commands = new HashMap<>();

    private boolean mirrorPathResourcesDeallocated = false;

    private String errorReason;

    public FlowMirrorPointDeleteFsm(CommandContext commandContext, @NonNull FlowMirrorPointDeleteHubCarrier carrier,
                                    String flowId) {
        super(commandContext, carrier);
        this.flowId = flowId;
    }

    @Override
    public void fireNext(FlowMirrorPointDeleteContext context) {
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

    private void setErrorReason(String errorReason) {
        if (this.errorReason != null) {
            log.error("Subsequent error fired: {}", errorReason);
        } else {
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
        return "delete-mirror-point";
    }

    public static class Factory {
        private final StateMachineBuilder<FlowMirrorPointDeleteFsm, State, Event, FlowMirrorPointDeleteContext> builder;
        private final FlowMirrorPointDeleteHubCarrier carrier;

        public Factory(FlowMirrorPointDeleteHubCarrier carrier, PersistenceManager persistenceManager,
                       FlowResourcesManager resourcesManager, int speakerCommandRetriesLimit) {
            this.carrier = carrier;


            builder = StateMachineBuilderFactory.create(FlowMirrorPointDeleteFsm.class, State.class, Event.class,
                    FlowMirrorPointDeleteContext.class, CommandContext.class, FlowMirrorPointDeleteHubCarrier.class,
                    String.class);

            FlowOperationsDashboardLogger dashboardLogger = new FlowOperationsDashboardLogger(log);

            builder.transition().from(State.INITIALIZED).to(State.FLOW_VALIDATED).on(Event.NEXT)
                    .perform(new ValidateRequestAction(persistenceManager, dashboardLogger));
            builder.transition().from(State.INITIALIZED).to(State.FINISHED_WITH_ERROR).on(Event.TIMEOUT);

            builder.transition().from(State.FLOW_VALIDATED).to(State.DEALLOCATING_FLOW_MIRROR_PATH_RESOURCES)
                    .on(Event.NEXT);
            builder.transitions().from(State.FLOW_VALIDATED)
                    .toAmong(State.FINISHED_WITH_ERROR, State.FINISHED_WITH_ERROR)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.onEntry(State.DEALLOCATING_FLOW_MIRROR_PATH_RESOURCES)
                    .perform(new DeallocateFlowMirrorPathResourcesAction(persistenceManager, resourcesManager));
            builder.transitions().from(State.DEALLOCATING_FLOW_MIRROR_PATH_RESOURCES)
                    .toAmong(State.FLOW_MIRROR_PATH_RESOURCES_DEALLOCATED, State.FLOW_MIRROR_PATH_RESOURCES_DEALLOCATED)
                    .onEach(Event.NEXT, Event.ERROR);

            builder.transition().from(State.FLOW_MIRROR_PATH_RESOURCES_DEALLOCATED).to(State.REMOVING_GROUP)
                    .on(Event.NEXT);
            builder.internalTransition().within(State.FLOW_MIRROR_PATH_RESOURCES_DEALLOCATED).on(Event.ERROR)
                    .perform(new HandleNotDeallocatedFlowMirrorPathResourceAction());

            builder.onEntry(State.REMOVING_GROUP)
                    .perform(new EmitCommandRequestsAction(persistenceManager, resourcesManager));
            builder.internalTransition().within(State.REMOVING_GROUP).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedCommandResponseAction(speakerCommandRetriesLimit));
            builder.internalTransition().within(State.REMOVING_GROUP).on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedCommandResponseAction(speakerCommandRetriesLimit));
            builder.transition().from(State.REMOVING_GROUP).to(State.GROUP_REMOVED)
                    .on(Event.GROUP_REMOVED);
            builder.transition().from(State.REMOVING_GROUP).to(State.GROUP_REMOVED)
                    .on(Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction());

            builder.transitions().from(State.GROUP_REMOVED)
                    .toAmong(State.FLOW_MIRROR_POINTS_RECORD_PROCESSED, State.FLOW_MIRROR_POINTS_RECORD_PROCESSED)
                    .onEach(Event.NEXT, Event.ERROR);

            builder.onEntry(State.FLOW_MIRROR_POINTS_RECORD_PROCESSED)
                    .perform(new PostFlowMirrorPathDeallocationAction(persistenceManager, resourcesManager));

            builder.transition().from(State.FLOW_MIRROR_POINTS_RECORD_PROCESSED).to(State.FINISHED).on(Event.NEXT);
            builder.transition().from(State.FLOW_MIRROR_POINTS_RECORD_PROCESSED)
                    .to(State.FINISHED_WITH_ERROR).on(Event.ERROR);

            builder.defineFinalState(State.FINISHED)
                    .addEntryAction(new OnFinishedAction(persistenceManager, dashboardLogger));
            builder.defineFinalState(State.FINISHED_WITH_ERROR)
                    .addEntryAction(new OnFinishedWithErrorAction(persistenceManager, dashboardLogger));
        }

        public FlowMirrorPointDeleteFsm newInstance(CommandContext commandContext, String flowId) {
            return builder.newStateMachine(State.INITIALIZED, commandContext, carrier, flowId);
        }
    }

    public enum State {
        INITIALIZED,
        FLOW_VALIDATED,

        DEALLOCATING_FLOW_MIRROR_PATH_RESOURCES,
        FLOW_MIRROR_PATH_RESOURCES_DEALLOCATED,

        REMOVING_GROUP,
        GROUP_REMOVED,

        FLOW_MIRROR_POINTS_RECORD_PROCESSED,

        FINISHED,
        FINISHED_WITH_ERROR
    }

    public enum Event {
        NEXT,

        RESPONSE_RECEIVED,
        ERROR_RECEIVED,

        GROUP_REMOVED,

        TIMEOUT,
        ERROR
    }
}
