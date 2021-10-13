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

package org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create;

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowPathSwappingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions.EmitUpdateRulesRequestsAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions.EmitVerifyRulesRequestsAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions.HandleNotCompletedCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions.HandleNotDeallocatedFlowMirrorPathResourceAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions.OnReceivedInstallResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions.OnReceivedValidateResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions.PostFlowMirrorPathDeallocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions.PostFlowMirrorPathInstallationAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions.PostResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions.ResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions.RevertFlowMirrorPathAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions.ValidateRequestAction;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlowMirrorPoint;
import org.openkilda.wfm.topology.flowhs.service.FlowMirrorPointCreateHubCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowProcessingEventListener;

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
public final class FlowMirrorPointCreateFsm extends FlowPathSwappingFsm<FlowMirrorPointCreateFsm, State, Event,
        FlowMirrorPointCreateContext, FlowMirrorPointCreateHubCarrier, FlowProcessingEventListener> {

    private RequestedFlowMirrorPoint requestedFlowMirrorPoint;

    private FlowStatus flowStatus;
    private PathId flowPathId;
    private SwitchId mirrorSwitchId;
    private PathId mirrorPathId;
    private long unmaskedCookie;

    private boolean rulesInstalled = false;
    private boolean addNewGroup = false;

    private final Map<UUID, FlowSegmentRequestFactory> commands = new HashMap<>();

    public FlowMirrorPointCreateFsm(CommandContext commandContext, @NonNull FlowMirrorPointCreateHubCarrier carrier,
                                    String flowId) {
        super(commandContext, carrier, flowId);
    }

    @Override
    public void fireNext(FlowMirrorPointCreateContext context) {
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

    @Override
    public void setErrorReason(String errorReason) {
        if (this.errorReason != null) {
            log.error("Subsequent error fired: " + errorReason);
        } else {
            this.errorReason = errorReason;
        }
    }

    @Override
    public void fireNoPathFound(String errorReason) {
        fireError(Event.NO_PATH_FOUND, errorReason);
    }

    @Override
    public void reportError(Event event) {
        if (Event.TIMEOUT == event) {
            reportGlobalTimeout();
        }
    }

    @Override
    protected String getCrudActionName() {
        return "create-mirror-point";
    }

    public static class Factory {
        private final StateMachineBuilder<FlowMirrorPointCreateFsm, State, Event, FlowMirrorPointCreateContext> builder;
        private final FlowMirrorPointCreateHubCarrier carrier;

        public Factory(FlowMirrorPointCreateHubCarrier carrier, PersistenceManager persistenceManager,
                       PathComputer pathComputer, FlowResourcesManager resourcesManager,
                       int pathAllocationRetriesLimit, int pathAllocationRetryDelay, int resourceAllocationRetriesLimit,
                       int speakerCommandRetriesLimit) {
            this.carrier = carrier;

            builder = StateMachineBuilderFactory.create(FlowMirrorPointCreateFsm.class, State.class, Event.class,
                    FlowMirrorPointCreateContext.class, CommandContext.class, FlowMirrorPointCreateHubCarrier.class,
                    String.class);

            FlowOperationsDashboardLogger dashboardLogger = new FlowOperationsDashboardLogger(log);

            builder.transition().from(State.INITIALIZED).to(State.FLOW_VALIDATED).on(Event.NEXT)
                    .perform(new ValidateRequestAction(persistenceManager, dashboardLogger));
            builder.transition().from(State.INITIALIZED).to(State.FINISHED_WITH_ERROR).on(Event.TIMEOUT);

            builder.transition().from(State.FLOW_VALIDATED).to(State.ALLOCATING_RESOURCES).on(Event.NEXT)
                    .perform(new ResourceAllocationAction(persistenceManager,
                            pathAllocationRetriesLimit, pathAllocationRetryDelay, resourceAllocationRetriesLimit,
                            pathComputer, resourcesManager, dashboardLogger));
            builder.transitions().from(State.FLOW_VALIDATED)
                    .toAmong(State.FINISHED_WITH_ERROR, State.FINISHED_WITH_ERROR)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.ALLOCATING_RESOURCES).to(State.RESOURCE_ALLOCATION_COMPLETED)
                    .on(Event.NEXT)
                    .perform(new PostResourceAllocationAction(persistenceManager));
            builder.transitions().from(State.ALLOCATING_RESOURCES)
                    .toAmong(State.FLOW_MIRROR_PATH_ALLOCATION_REVERTED, State.FLOW_MIRROR_PATH_ALLOCATION_REVERTED)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.RESOURCE_ALLOCATION_COMPLETED).to(State.INSTALLING_RULES)
                    .on(Event.NEXT)
                    .perform(new EmitUpdateRulesRequestsAction(persistenceManager, resourcesManager));
            builder.transitions().from(State.RESOURCE_ALLOCATION_COMPLETED)
                    .toAmong(State.FLOW_MIRROR_PATH_ALLOCATION_REVERTED, State.FLOW_MIRROR_PATH_ALLOCATION_REVERTED)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.internalTransition().within(State.INSTALLING_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(speakerCommandRetriesLimit));
            builder.internalTransition().within(State.INSTALLING_RULES).on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(speakerCommandRetriesLimit));
            builder.transition().from(State.INSTALLING_RULES).to(State.VALIDATING_RULES)
                    .on(Event.RULES_UPDATED)
                    .perform(new EmitVerifyRulesRequestsAction());
            builder.transitions().from(State.INSTALLING_RULES)
                    .toAmong(State.REVERTING_FLOW_MIRROR_PATH_RESOURCES, State.REVERTING_FLOW_MIRROR_PATH_RESOURCES)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.internalTransition().within(State.VALIDATING_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedValidateResponseAction(speakerCommandRetriesLimit));
            builder.internalTransition().within(State.VALIDATING_RULES).on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedValidateResponseAction(speakerCommandRetriesLimit));
            builder.transition().from(State.VALIDATING_RULES).to(State.RULES_VALIDATED)
                    .on(Event.RULES_VALIDATED);
            builder.transitions().from(State.VALIDATING_RULES)
                    .toAmong(State.REVERTING_FLOW_MIRROR_PATH_RESOURCES, State.REVERTING_FLOW_MIRROR_PATH_RESOURCES,
                             State.REVERTING_FLOW_MIRROR_PATH_RESOURCES)
                    .onEach(Event.TIMEOUT, Event.MISSING_RULE_FOUND, Event.ERROR);

            builder.transitions().from(State.RULES_VALIDATED)
                    .toAmong(State.MIRROR_PATH_INSTALLATION_COMPLETED, State.MIRROR_PATH_INSTALLATION_COMPLETED)
                    .onEach(Event.NEXT, Event.ERROR)
                    .perform(new PostFlowMirrorPathInstallationAction(persistenceManager));

            builder.transition().from(State.MIRROR_PATH_INSTALLATION_COMPLETED).to(State.FINISHED).on(Event.NEXT);
            builder.transition().from(State.MIRROR_PATH_INSTALLATION_COMPLETED)
                    .to(State.FINISHED_WITH_ERROR).on(Event.ERROR);

            builder.onEntry(State.REVERTING_FLOW_MIRROR_PATH_RESOURCES)
                    .perform(new RevertFlowMirrorPathAllocationAction(persistenceManager, resourcesManager));
            builder.transitions().from(State.REVERTING_FLOW_MIRROR_PATH_RESOURCES)
                    .toAmong(State.FLOW_MIRROR_PATH_ALLOCATION_REVERTED, State.FLOW_MIRROR_PATH_ALLOCATION_REVERTED)
                    .onEach(Event.NEXT, Event.ERROR);

            builder.transition().from(State.FLOW_MIRROR_PATH_ALLOCATION_REVERTED).to(State.REMOVE_GROUP)
                    .on(Event.NEXT);
            builder.transition().from(State.FLOW_MIRROR_PATH_ALLOCATION_REVERTED)
                    .to(State.FLOW_MIRROR_POINTS_RECORD_PROCESSED)
                    .on(Event.SKIP_INSTALLING_RULES);
            builder.internalTransition().within(State.FLOW_MIRROR_PATH_ALLOCATION_REVERTED).on(Event.ERROR)
                    .perform(new HandleNotDeallocatedFlowMirrorPathResourceAction());

            builder.onEntry(State.REMOVE_GROUP)
                    .perform(new EmitUpdateRulesRequestsAction(persistenceManager, resourcesManager));
            builder.internalTransition().within(State.REMOVE_GROUP).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(speakerCommandRetriesLimit));
            builder.internalTransition().within(State.REMOVE_GROUP).on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(speakerCommandRetriesLimit));
            builder.transition().from(State.REMOVE_GROUP).to(State.GROUP_REMOVED)
                    .on(Event.RULES_UPDATED);
            builder.transition().from(State.REMOVE_GROUP).to(State.GROUP_REMOVED)
                    .on(Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction());

            builder.transitions().from(State.GROUP_REMOVED)
                    .toAmong(State.FLOW_MIRROR_POINTS_RECORD_PROCESSED, State.FLOW_MIRROR_POINTS_RECORD_PROCESSED)
                    .onEach(Event.NEXT, Event.ERROR);

            builder.onEntry(State.FLOW_MIRROR_POINTS_RECORD_PROCESSED)
                    .perform(new PostFlowMirrorPathDeallocationAction(persistenceManager, resourcesManager));
            builder.transitions().from(State.FLOW_MIRROR_POINTS_RECORD_PROCESSED)
                    .toAmong(State.FINISHED_WITH_ERROR, State.FINISHED_WITH_ERROR)
                    .onEach(Event.NEXT, Event.ERROR);

            builder.defineFinalState(State.FINISHED)
                    .addEntryAction(new OnFinishedAction(persistenceManager, dashboardLogger));
            builder.defineFinalState(State.FINISHED_WITH_ERROR)
                    .addEntryAction(new OnFinishedWithErrorAction(persistenceManager, dashboardLogger));
        }

        public FlowMirrorPointCreateFsm newInstance(CommandContext commandContext, String flowId) {
            return builder.newStateMachine(State.INITIALIZED, commandContext, carrier, flowId);
        }
    }

    public enum State {
        INITIALIZED,
        FLOW_VALIDATED,
        ALLOCATING_RESOURCES,
        RESOURCE_ALLOCATION_COMPLETED,

        INSTALLING_RULES,
        VALIDATING_RULES,
        RULES_VALIDATED,

        MIRROR_PATH_INSTALLATION_COMPLETED,

        FINISHED,

        REVERTING_FLOW_MIRROR_PATH_RESOURCES,
        FLOW_MIRROR_PATH_ALLOCATION_REVERTED,

        REMOVE_GROUP,
        GROUP_REMOVED,

        FLOW_MIRROR_POINTS_RECORD_PROCESSED,

        FINISHED_WITH_ERROR
    }

    public enum Event {
        NEXT,

        NO_PATH_FOUND,

        RESPONSE_RECEIVED,
        ERROR_RECEIVED,

        RULES_UPDATED,
        RULES_VALIDATED,
        MISSING_RULE_FOUND,

        TIMEOUT,
        ERROR,

        SKIP_INSTALLING_RULES
    }
}
