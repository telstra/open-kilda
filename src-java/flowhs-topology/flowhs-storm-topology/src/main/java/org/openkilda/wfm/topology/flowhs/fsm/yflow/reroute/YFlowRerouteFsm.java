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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute;

import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.yflow.SubFlowPathDto;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.reroute.error.RerouteError;
import org.openkilda.model.IslEndpoint;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.metrics.MeterRegistryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.common.YFlowProcessingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.AllocateYFlowResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.RevertYFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions.CompleteYFlowReroutingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions.DeallocateOldYFlowResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions.HandleNotCompletedCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions.HandleNotDeallocatedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions.HandleNotReroutedSubFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions.InstallNewMetersAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions.OnReceivedInstallResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions.OnReceivedRemoveResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions.OnReceivedResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions.OnReceivedValidateResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions.OnSubFlowAllocatedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions.OnSubFlowReroutedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions.OnTimeoutOperationAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions.RemoveOldMetersAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions.RerouteSubFlowsAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions.StartReroutingYFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions.ValidateNewMetersAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions.ValidateYFlowAction;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteService;
import org.openkilda.wfm.topology.flowhs.service.yflow.YFlowEventListener;
import org.openkilda.wfm.topology.flowhs.service.yflow.YFlowRerouteHubCarrier;

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
public final class YFlowRerouteFsm extends YFlowProcessingFsm<YFlowRerouteFsm, State, Event, YFlowRerouteContext,
        YFlowRerouteHubCarrier, YFlowEventListener> {

    private String rerouteReason;
    private Set<IslEndpoint> affectedIsls;
    private boolean forceReroute;
    private boolean ignoreBandwidth;

    private Set<String> targetSubFlowIds;

    private YFlowResources oldResources;

    private final Set<String> subFlows = new HashSet<>();
    private final Set<String> reroutingSubFlows = new HashSet<>();
    private final Set<String> failedSubFlows = new HashSet<>();
    private final Set<String> allocatedSubFlows = new HashSet<>();

    private String mainAffinityFlowId;
    private Collection<FlowRerouteRequest> rerouteRequests;

    private PathInfoData oldSharedPath;
    private List<SubFlowPathDto> oldSubFlowPathDtos;
    private List<Long> oldYFlowPathCookies;

    private Collection<DeleteSpeakerCommandsRequest> deleteOldYFlowCommands;

    private RerouteError rerouteError;

    private YFlowRerouteFsm(@NonNull CommandContext commandContext, @NonNull YFlowRerouteHubCarrier carrier,
                            @NonNull String yFlowId, @NonNull Collection<YFlowEventListener> eventListeners) {
        super(Event.NEXT, Event.ERROR, commandContext, carrier, yFlowId, eventListeners);
    }

    public void addSubFlow(String flowId) {
        subFlows.add(flowId);
    }

    public boolean isReroutingSubFlow(String flowId) {
        return reroutingSubFlows.contains(flowId);
    }

    public void addReroutingSubFlow(String flowId) {
        reroutingSubFlows.add(flowId);
    }

    public void removeReroutingSubFlow(String flowId) {
        reroutingSubFlows.remove(flowId);
    }

    public void addFailedSubFlow(String flowId) {
        failedSubFlows.add(flowId);
    }

    public boolean isFailedSubFlow(String flowId) {
        return failedSubFlows.contains(flowId);
    }

    public void addAllocatedSubFlow(String flowId) {
        allocatedSubFlows.add(flowId);
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
        private final StateMachineBuilder<YFlowRerouteFsm, State, Event, YFlowRerouteContext> builder;
        private final YFlowRerouteHubCarrier carrier;

        public Factory(@NonNull YFlowRerouteHubCarrier carrier, @NonNull PersistenceManager persistenceManager,
                       @NonNull PathComputer pathComputer, @NonNull FlowResourcesManager resourcesManager,
                       @NonNull RuleManager ruleManager, @NonNull FlowRerouteService flowRerouteService,
                       int resourceAllocationRetriesLimit, int speakerCommandRetriesLimit) {
            this.carrier = carrier;

            builder = StateMachineBuilderFactory.create(YFlowRerouteFsm.class, State.class, Event.class,
                    YFlowRerouteContext.class, CommandContext.class, YFlowRerouteHubCarrier.class, String.class,
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
                    .to(State.YFLOW_REROUTE_STARTED)
                    .on(Event.NEXT)
                    .perform(new StartReroutingYFlowAction(persistenceManager, ruleManager));
            builder.transitions()
                    .from(State.YFLOW_VALIDATED)
                    .toAmong(State.FINISHED, State.REVERTING_YFLOW_STATUS, State.REVERTING_YFLOW_STATUS)
                    .onEach(Event.YFLOW_REROUTE_SKIPPED, Event.TIMEOUT, Event.ERROR);

            builder.transitions()
                    .from(State.YFLOW_REROUTE_STARTED)
                    .toAmong(State.REROUTING_SUB_FLOWS, State.REVERTING_YFLOW_STATUS, State.REVERTING_YFLOW_STATUS)
                    .onEach(Event.NEXT, Event.TIMEOUT, Event.ERROR);

            builder.defineParallelStatesOn(State.REROUTING_SUB_FLOWS, State.SUB_FLOW_REROUTING_STARTED);
            builder.defineState(State.SUB_FLOW_REROUTING_STARTED)
                    .addEntryAction(new RerouteSubFlowsAction(flowRerouteService));

            builder.internalTransition()
                    .within(State.REROUTING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_ALLOCATED)
                    .perform(new OnSubFlowAllocatedAction(persistenceManager));
            builder.internalTransition()
                    .within(State.REROUTING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_REROUTED)
                    .perform(new OnSubFlowReroutedAction(flowRerouteService));
            builder.internalTransition()
                    .within(State.REROUTING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_FAILED)
                    .perform(new HandleNotReroutedSubFlowAction(persistenceManager));
            builder.transitions()
                    .from(State.REROUTING_SUB_FLOWS)
                    .toAmong(State.SUB_FLOW_REROUTES_FINISHED, State.SUB_FLOW_REROUTES_FINISHED,
                            State.COMPLETE_YFLOW_INSTALLATION, State.ALL_PENDING_OPERATIONS_COMPLETED)
                    .onEach(Event.ALL_SUB_FLOWS_REROUTED, Event.FAILED_TO_REROUTE_SUB_FLOWS,
                            Event.YFLOW_REROUTE_SKIPPED, Event.TIMEOUT);

            builder.onEntry(State.ALL_PENDING_OPERATIONS_COMPLETED)
                    .perform(new OnTimeoutOperationAction());
            builder.internalTransition()
                    .within(State.ALL_PENDING_OPERATIONS_COMPLETED)
                    .on(Event.SUB_FLOW_REROUTED)
                    .perform(new OnReceivedResponseAction(persistenceManager));
            builder.internalTransition()
                    .within(State.ALL_PENDING_OPERATIONS_COMPLETED)
                    .on(Event.SUB_FLOW_FAILED)
                    .perform(new OnReceivedResponseAction(persistenceManager));
            builder.transition()
                    .from(State.ALL_PENDING_OPERATIONS_COMPLETED)
                    .to(State.SUB_FLOW_REROUTES_FINISHED)
                    .on(Event.PENDING_OPERATIONS_COMPLETED);

            builder.transition()
                    .from(State.SUB_FLOW_REROUTES_FINISHED)
                    .to(State.REMOVING_OLD_YFLOW_METERS)
                    .on(Event.NEXT)
                    .perform(new RemoveOldMetersAction(persistenceManager, ruleManager));

            builder.internalTransition()
                    .within(State.REMOVING_OLD_YFLOW_METERS)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRemoveResponseAction(speakerCommandRetriesLimit));
            builder.transition()
                    .from(State.REMOVING_OLD_YFLOW_METERS)
                    .to(State.OLD_YFLOW_METERS_REMOVED)
                    .on(Event.YFLOW_METERS_REMOVED);
            builder.transition()
                    .from(State.REMOVING_OLD_YFLOW_METERS)
                    .to(State.OLD_YFLOW_METERS_REMOVED)
                    .on(Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction("remove old meters"));

            builder.transition()
                    .from(State.OLD_YFLOW_METERS_REMOVED)
                    .to(State.NEW_YFLOW_RESOURCES_ALLOCATED)
                    .on(Event.NEXT)
                    .perform(new AllocateYFlowResourcesAction<>(persistenceManager, resourceAllocationRetriesLimit,
                            pathComputer, resourcesManager));

            builder.transition()
                    .from(State.NEW_YFLOW_RESOURCES_ALLOCATED)
                    .to(State.INSTALLING_NEW_YFLOW_METERS)
                    .on(Event.NEXT)
                    .perform(new InstallNewMetersAction(persistenceManager, ruleManager));
            /*TODO: need to handle errors and timeout here
            builder.transitions()
                    .from(State.NEW_YFLOW_RESOURCES_ALLOCATED)
                    .toAmong(???)
                    .onEach(Event.ERROR, Event.TIMEOUT);*/

            builder.internalTransition()
                    .within(State.INSTALLING_NEW_YFLOW_METERS)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(speakerCommandRetriesLimit));
            builder.transition()
                    .from(State.INSTALLING_NEW_YFLOW_METERS)
                    .to(State.NEW_YFLOW_METERS_INSTALLED)
                    .on(Event.YFLOW_METERS_INSTALLED);
            builder.transitions()
                    .from(State.INSTALLING_NEW_YFLOW_METERS)
                    .toAmong(State.NEW_YFLOW_METERS_INSTALLED, State.NEW_YFLOW_METERS_INSTALLED)
                    .onEach(Event.ERROR, Event.TIMEOUT)
                    .perform(new HandleNotCompletedCommandsAction("install new meters"));

            builder.transition()
                    .from(State.NEW_YFLOW_METERS_INSTALLED)
                    .to(State.VALIDATING_NEW_YFLOW_METERS)
                    .on(Event.NEXT)
                    .perform(new ValidateNewMetersAction(persistenceManager));

            builder.internalTransition()
                    .within(State.VALIDATING_NEW_YFLOW_METERS)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedValidateResponseAction(speakerCommandRetriesLimit));
            builder.transition()
                    .from(State.VALIDATING_NEW_YFLOW_METERS)
                    .to(State.NEW_YFLOW_METERS_VALIDATED)
                    .on(Event.YFLOW_METERS_VALIDATED);
            builder.transitions()
                    .from(State.VALIDATING_NEW_YFLOW_METERS)
                    .toAmong(State.NEW_YFLOW_METERS_VALIDATED, State.NEW_YFLOW_METERS_VALIDATED)
                    .onEach(Event.ERROR, Event.TIMEOUT)
                    .perform(new HandleNotCompletedCommandsAction("validate new meters"));

            builder.transition()
                    .from(State.NEW_YFLOW_METERS_VALIDATED)
                    .to(State.DEALLOCATING_OLD_YFLOW_RESOURCES)
                    .on(Event.NEXT);

            builder.transition()
                    .from(State.DEALLOCATING_OLD_YFLOW_RESOURCES)
                    .to(State.OLD_YFLOW_RESOURCES_DEALLOCATED)
                    .on(Event.NEXT)
                    .perform(new DeallocateOldYFlowResourcesAction(persistenceManager, resourcesManager));

            builder.transition()
                    .from(State.OLD_YFLOW_RESOURCES_DEALLOCATED)
                    .to(State.COMPLETE_YFLOW_INSTALLATION)
                    .on(Event.NEXT);
            builder.transition()
                    .from(State.OLD_YFLOW_RESOURCES_DEALLOCATED)
                    .to(State.COMPLETE_YFLOW_INSTALLATION)
                    .on(Event.ERROR)
                    .perform(new HandleNotDeallocatedResourcesAction());

            builder.transition()
                    .from(State.COMPLETE_YFLOW_INSTALLATION)
                    .to(State.YFLOW_INSTALLATION_COMPLETED)
                    .on(Event.NEXT)
                    .perform(new CompleteYFlowReroutingAction(persistenceManager, dashboardLogger));

            builder.transition()
                    .from(State.YFLOW_INSTALLATION_COMPLETED)
                    .to(State.FINISHED)
                    .on(Event.NEXT);
            builder.transition()
                    .from(State.YFLOW_INSTALLATION_COMPLETED)
                    .to(State.FINISHED_WITH_ERROR)
                    .on(Event.ERROR);

            builder.transition()
                    .from(State.REVERTING_YFLOW_STATUS)
                    .to(State.FINISHED_WITH_ERROR)
                    .on(Event.NEXT)
                    .perform(new RevertYFlowStatusAction<>(persistenceManager, dashboardLogger));

            builder.defineFinalState(State.FINISHED)
                    .addEntryAction(new OnFinishedAction(dashboardLogger, carrier));
            builder.defineFinalState(State.FINISHED_WITH_ERROR)
                    .addEntryAction(new OnFinishedWithErrorAction(dashboardLogger, carrier));
        }

        public YFlowRerouteFsm newInstance(@NonNull CommandContext commandContext, @NonNull String flowId,
                                           @NonNull Collection<YFlowEventListener> eventListeners) {
            YFlowRerouteFsm fsm = builder.newStateMachine(State.INITIALIZED, commandContext, carrier, flowId,
                    eventListeners);

            fsm.addTransitionCompleteListener(event ->
                    log.debug("YFlowRerouteFsm, transition to {} on {}", event.getTargetState(), event.getCause()));

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
        YFLOW_REROUTE_STARTED,

        REROUTING_SUB_FLOWS,
        SUB_FLOW_REROUTING_STARTED,
        SUB_FLOW_REROUTES_FINISHED,

        ALL_PENDING_OPERATIONS_COMPLETED,

        NEW_YFLOW_RESOURCES_ALLOCATED,
        INSTALLING_NEW_YFLOW_METERS,
        NEW_YFLOW_METERS_INSTALLED,
        VALIDATING_NEW_YFLOW_METERS,
        NEW_YFLOW_METERS_VALIDATED,
        REMOVING_OLD_YFLOW_METERS,
        OLD_YFLOW_METERS_REMOVED,
        DEALLOCATING_OLD_YFLOW_RESOURCES,
        OLD_YFLOW_RESOURCES_DEALLOCATED,
        COMPLETE_YFLOW_INSTALLATION,
        YFLOW_INSTALLATION_COMPLETED,
        FINISHED,

        REVERTING_YFLOW_STATUS,
        FINISHED_WITH_ERROR;
    }

    public enum Event {
        NEXT,

        RESPONSE_RECEIVED,

        SUB_FLOW_ALLOCATED,
        SUB_FLOW_REROUTED,
        SUB_FLOW_FAILED,
        ALL_SUB_FLOWS_REROUTED,
        FAILED_TO_REROUTE_SUB_FLOWS,
        YFLOW_METERS_INSTALLED,
        YFLOW_METERS_VALIDATED,
        YFLOW_METERS_REMOVED,

        YFLOW_REROUTE_SKIPPED,

        TIMEOUT,
        PENDING_OPERATIONS_COMPLETED,
        ERROR
    }
}
