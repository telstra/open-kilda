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

import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.IslEndpoint;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.metrics.MeterRegistryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.common.YFlowProcessingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.AllocateYFlowResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.RevertYFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action.CompleteYFlowReroutingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action.DeallocateOldYFlowResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action.HandleNotCompletedCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action.HandleNotDeallocatedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action.HandleNotReroutedSubFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action.InstallNewYPointMetersAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action.OnReceivedInstallResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action.OnReceivedRemoveResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action.OnReceivedResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action.OnReceivedValidateResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action.OnSubFlowAllocatedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action.OnSubFlowReroutedAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action.OnTimeoutOperationAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action.RemoveOldYPointMetersAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action.RerouteSubFlowsAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action.ValidateNewYPointMeterAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action.ValidateYFlowAction;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteService;
import org.openkilda.wfm.topology.flowhs.service.YFlowEventListener;
import org.openkilda.wfm.topology.flowhs.service.YFlowRerouteHubCarrier;

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
public final class YFlowRerouteFsm extends YFlowProcessingFsm<YFlowRerouteFsm, State, Event, YFlowRerouteContext,
        YFlowRerouteHubCarrier, YFlowEventListener> {

    private String rerouteReason;
    private Set<IslEndpoint> affectedIsls;
    private boolean forceReroute;
    private boolean ignoreBandwidth;

    private Set<String> targetSubFlowIds;

    private YFlowResources oldResources;
    private YFlowResources reallocatedResources;

    private final Set<String> subFlows = new HashSet<>();
    private final Set<String> reroutingSubFlows = new HashSet<>();
    private final Set<String> failedSubFlows = new HashSet<>();
    private final Set<String> allocatedSubFlows = new HashSet<>();

    private String mainAffinityFlowId;
    private Collection<FlowRerouteRequest> rerouteRequests;

    private String errorReason;

    private YFlowRerouteFsm(@NonNull CommandContext commandContext, @NonNull YFlowRerouteHubCarrier carrier,
                            @NonNull String yFlowId, @NonNull Collection<YFlowEventListener> eventListeners) {
        super(commandContext, carrier, yFlowId, eventListeners);
    }

    @Override
    public void fireNext(YFlowRerouteContext context) {
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

    public void clearReroutingSubFlows() {
        reroutingSubFlows.clear();
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

    public void addAllocatedSubFlow(String flowId) {
        allocatedSubFlows.add(flowId);
    }

    public void clearAllocatedSubFlows() {
        allocatedSubFlows.clear();
    }

    public void setErrorReason(String errorReason) {
        if (this.errorReason != null) {
            log.error("Subsequent error fired: " + errorReason);
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
        return "create";
    }

    public static class Factory {
        private final StateMachineBuilder<YFlowRerouteFsm, State, Event, YFlowRerouteContext> builder;
        private final YFlowRerouteHubCarrier carrier;

        public Factory(@NonNull YFlowRerouteHubCarrier carrier, @NonNull PersistenceManager persistenceManager,
                       @NonNull PathComputer pathComputer, @NonNull FlowResourcesManager resourcesManager,
                       @NonNull FlowRerouteService flowRerouteService,
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

            builder.transitions()
                    .from(State.YFLOW_VALIDATED)
                    .toAmong(State.REROUTING_SUB_FLOWS, State.REVERTING_YFLOW_STATUS, State.REVERTING_YFLOW_STATUS)
                    .onEach(Event.NEXT, Event.TIMEOUT, Event.ERROR);

            builder.defineParallelStatesOn(State.REROUTING_SUB_FLOWS, State.SUB_FLOW_REROUTING_STARTED);
            builder.defineState(State.SUB_FLOW_REROUTING_STARTED)
                    .addEntryAction(new RerouteSubFlowsAction(flowRerouteService));

            builder.internalTransition()
                    .within(State.REROUTING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_ALLOCATED)
                    .perform(new OnSubFlowAllocatedAction(flowRerouteService, persistenceManager));
            builder.internalTransition()
                    .within(State.REROUTING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_REROUTED)
                    .perform(new OnSubFlowReroutedAction());
            builder.internalTransition()
                    .within(State.REROUTING_SUB_FLOWS)
                    .on(Event.SUB_FLOW_FAILED)
                    .perform(new HandleNotReroutedSubFlowAction(persistenceManager));
            builder.transitions()
                    .from(State.REROUTING_SUB_FLOWS)
                    .toAmong(State.SUB_FLOW_REROUTES_FINISHED, State.SUB_FLOW_REROUTES_FINISHED,
                            State.ALL_PENDING_OPERATIONS_COMPLETED)
                    .onEach(Event.ALL_SUB_FLOWS_REROUTED, Event.FAILED_TO_REROUTE_SUB_FLOWS, Event.TIMEOUT);

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
                    .to(State.NEW_YFLOW_RESOURCES_ALLOCATED)
                    .on(Event.NEXT)
                    .perform(new AllocateYFlowResourcesAction<>(persistenceManager, resourceAllocationRetriesLimit,
                            pathComputer, resourcesManager));

            builder.transition()
                    .from(State.NEW_YFLOW_RESOURCES_ALLOCATED)
                    .to(State.INSTALLING_NEW_YFLOW_METERS)
                    .on(Event.NEXT)
                    .perform(new InstallNewYPointMetersAction(persistenceManager));

            builder.internalTransition()
                    .within(State.INSTALLING_NEW_YFLOW_METERS)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(speakerCommandRetriesLimit));
            builder.internalTransition()
                    .within(State.INSTALLING_NEW_YFLOW_METERS)
                    .on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(speakerCommandRetriesLimit));
            builder.transition()
                    .from(State.INSTALLING_NEW_YFLOW_METERS)
                    .to(State.NEW_YFLOW_METERS_INSTALLED)
                    .on(Event.YFLOW_METERS_INSTALLED);
            builder.transition()
                    .from(State.INSTALLING_NEW_YFLOW_METERS)
                    .to(State.NEW_YFLOW_METERS_INSTALLED)
                    .on(Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction("install new meters"));

            builder.transition()
                    .from(State.NEW_YFLOW_METERS_INSTALLED)
                    .to(State.VALIDATING_NEW_YFLOW_METERS)
                    .on(Event.NEXT)
                    .perform(new ValidateNewYPointMeterAction(persistenceManager));

            builder.internalTransition()
                    .within(State.VALIDATING_NEW_YFLOW_METERS)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedValidateResponseAction(speakerCommandRetriesLimit));
            builder.internalTransition()
                    .within(State.VALIDATING_NEW_YFLOW_METERS)
                    .on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedValidateResponseAction(speakerCommandRetriesLimit));
            builder.transition()
                    .from(State.VALIDATING_NEW_YFLOW_METERS)
                    .to(State.NEW_YFLOW_METERS_VALIDATED)
                    .on(Event.YFLOW_METERS_VALIDATED);
            builder.transition()
                    .from(State.VALIDATING_NEW_YFLOW_METERS)
                    .to(State.NEW_YFLOW_METERS_VALIDATED)
                    .on(Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction("validate new meters"));

            builder.transition()
                    .from(State.NEW_YFLOW_METERS_VALIDATED)
                    .to(State.REMOVING_OLD_YFLOW_METERS)
                    .on(Event.NEXT)
                    .perform(new RemoveOldYPointMetersAction(persistenceManager));

            builder.internalTransition()
                    .within(State.REMOVING_OLD_YFLOW_METERS)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRemoveResponseAction(speakerCommandRetriesLimit));
            builder.internalTransition()
                    .within(State.REMOVING_OLD_YFLOW_METERS)
                    .on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedRemoveResponseAction(speakerCommandRetriesLimit));
            builder.transition()
                    .from(State.REMOVING_OLD_YFLOW_METERS)
                    .to(State.OLD_YPOINT_METERS_REMOVED)
                    .on(Event.YPOINT_METER_REMOVED);
            builder.transition()
                    .from(State.REMOVING_OLD_YFLOW_METERS)
                    .to(State.OLD_YPOINT_METERS_REMOVED)
                    .on(Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction("remove old meters"));

            builder.transition()
                    .from(State.OLD_YPOINT_METERS_REMOVED)
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
                    .on(Event.YFLOW_REROUTE_FINISHED);
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
                    log.error("YFlowRerouteFsm, transition to {} on {}", event.getTargetState(), event.getCause()));

            if (!eventListeners.isEmpty()) {
                fsm.addTransitionCompleteListener(event -> {
                    switch (event.getTargetState()) {
                        case FINISHED:
                            fsm.notifyEventListenersOnComplete();
                            break;
                        case FINISHED_WITH_ERROR:
                            fsm.notifyEventListenersOnError(ErrorType.INTERNAL_ERROR, fsm.getErrorReason());
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
        YFLOW_VALIDATED,

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
        OLD_YPOINT_METERS_REMOVED,
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
        ERROR_RECEIVED,

        SUB_FLOW_ALLOCATED,
        SUB_FLOW_REROUTED,
        SUB_FLOW_FAILED,
        ALL_SUB_FLOWS_REROUTED,
        FAILED_TO_REROUTE_SUB_FLOWS,
        YFLOW_METERS_INSTALLED,
        YFLOW_METERS_VALIDATED,
        YPOINT_METER_REMOVED,

        YFLOW_REROUTE_FINISHED,

        TIMEOUT,
        PENDING_OPERATIONS_COMPLETED,
        ERROR
    }
}
