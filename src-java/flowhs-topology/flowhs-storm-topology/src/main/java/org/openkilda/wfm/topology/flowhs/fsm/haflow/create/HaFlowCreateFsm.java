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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.create;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.HaFlowResources;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.metrics.MeterRegistryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.common.HaFlowProcessingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NotifyHaFlowMonitorAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.ReportErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.actions.CompleteFlowCreateAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.actions.EmitInstallRulesRequestsAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.actions.FlowValidateAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.actions.HandleNotCreatedFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.actions.HandleNotDeallocatedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.actions.NotifyFlowStatsAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.actions.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.actions.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.actions.OnReceivedResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.actions.ResourcesAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.actions.ResourcesDeallocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.actions.RollbackInstalledRulesAction;
import org.openkilda.wfm.topology.flowhs.service.FlowCreateEventListener;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowProcessingEventListener;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
@Slf4j
public final class HaFlowCreateFsm extends HaFlowProcessingFsm<HaFlowCreateFsm, State, Event,
        HaFlowCreateContext, FlowGenericCarrier, FlowCreateEventListener> {

    public static final String INSTALL_ACTION_NAME = "install";
    public static final String REMOVE_ACTION_NAME = "delete";
    private HaFlowRequest targetFlow;
    private HaFlowResources primaryResources;
    private HaFlowResources protectedResources;
    private Map<PathId, SwitchId> yPointMap;
    private boolean pathsBeenAllocated;
    private boolean backUpPrimaryPathComputationWayUsed;
    private boolean backUpProtectedPathComputationWayUsed;
    private Map<PathId, Boolean> backUpComputationWayUsedMap;
    private final List<SpeakerData> sentCommands;

    // The amount of flow create operation retries left: that means how many retries may be executed.
    // NB: it differs from command execution retries amount.
    private int remainRetries;
    private boolean timedOut;

    private HaFlowCreateFsm(@NonNull CommandContext commandContext, @NonNull FlowGenericCarrier carrier,
                            @NonNull String haFlowId,
                            @NonNull Collection<FlowCreateEventListener> eventListeners, @NonNull Config config) {
        super(Event.NEXT, Event.ERROR, commandContext, carrier, haFlowId, eventListeners);
        this.remainRetries = config.getHaFlowCreationRetriesLimit();
        backUpComputationWayUsedMap = new HashMap<>();
        sentCommands = new ArrayList<>();
        yPointMap = new HashMap<>();
    }

    /**
     * Initiates a retry if limit is not exceeded.
     *
     * @return true if retry was triggered.
     */
    public boolean retryIfAllowed() {
        if (!timedOut && remainRetries-- > 0) {
            log.info("About to retry flow create. Retries left: {}", remainRetries);
            resetState();
            fire(Event.RETRY);
            return true;
        } else {
            if (timedOut) {
                log.warn("Failed to create flow: operation timed out");
            } else {
                log.debug("Retry of flow creation is not possible: limit is exceeded");
            }
            return false;
        }
    }

    public List<HaFlowResources> getHaFlowResources() {
        List<HaFlowResources> result = new ArrayList<>();
        for (HaFlowResources resources : new HaFlowResources[]{primaryResources, protectedResources}) {
            if (resources != null) {
                result.add(resources);
            }
        }
        return result;
    }

    @Override
    protected void afterTransitionCausedException(State fromState, State toState, Event event,
                                                  HaFlowCreateContext context) {
        super.afterTransitionCausedException(fromState, toState, event, context);
        String errorMessage = getLastException().getMessage();
        if (fromState == State.INITIALIZED || fromState == State.FLOW_VALIDATED) {
            ErrorData error = new ErrorData(ErrorType.INTERNAL_ERROR, "Could not create flow",
                    errorMessage);
            Message message = new ErrorMessage(error, getCommandContext().getCreateTime(),
                    getCommandContext().getCorrelationId());
            sendNorthboundResponse(message);
        }

        fireError(errorMessage);
    }

    private void resetState() {
        clearPendingCommands();
        clearSpeakerCommands();
        clearFailedCommands();
        clearRetriedCommands();
        backUpComputationWayUsedMap.clear();
        primaryResources = null;
        protectedResources = null;
        pathsBeenAllocated = false;
        backUpPrimaryPathComputationWayUsed = false;
        backUpProtectedPathComputationWayUsed = false;
    }

    @Override
    protected String getCrudActionName() {
        return "create";
    }

    public static class Factory {
        private final StateMachineBuilder<HaFlowCreateFsm, State, Event, HaFlowCreateContext> builder;
        private final FlowGenericCarrier carrier;
        private final Config config;

        public Factory(@NonNull FlowGenericCarrier carrier, @NonNull PersistenceManager persistenceManager,
                       @NonNull FlowResourcesManager resourcesManager, @NonNull PathComputer pathComputer,
                       @NonNull RuleManager ruleManager, @NonNull Config config) {
            this.carrier = carrier;
            this.config = config;
            this.builder = StateMachineBuilderFactory.create(HaFlowCreateFsm.class, State.class, Event.class,
                    HaFlowCreateContext.class, CommandContext.class, FlowGenericCarrier.class, String.class,
                    Collection.class, Config.class);

            FlowOperationsDashboardLogger dashboardLogger = new FlowOperationsDashboardLogger(log);

            final ReportErrorAction<HaFlowCreateFsm, State, Event, HaFlowCreateContext>
                    reportErrorAction = new ReportErrorAction<>(Event.TIMEOUT);

            // validate the ha-flow
            builder.transition()
                    .from(State.INITIALIZED)
                    .to(State.FLOW_VALIDATED)
                    .on(Event.NEXT)
                    .perform(new FlowValidateAction(persistenceManager, dashboardLogger));

            // allocate flow resources
            builder.transition()
                    .from(State.FLOW_VALIDATED)
                    .to(State.RESOURCES_ALLOCATED)
                    .on(Event.NEXT)
                    .perform(new ResourcesAllocationAction(pathComputer, persistenceManager,
                            config.getPathAllocationRetriesLimit(), config.getPathAllocationRetryDelay(),
                            resourcesManager));

            builder.transitions()
                    .from(State.FLOW_VALIDATED)
                    .toAmong(State.FINISHED_WITH_ERROR, State.FINISHED_WITH_ERROR)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            // there is possibility that during resources allocation we have to revalidate flow again.
            // e.g. if we try to simultaneously create two flows with the same flow id then both threads can go
            // to resource allocation state at the same time
            builder.transition()
                    .from(State.RESOURCES_ALLOCATED)
                    .to(State.INITIALIZED)
                    .on(Event.RETRY);

            builder.transitions()
                    .from(State.RESOURCES_ALLOCATED)
                    .toAmong(State.REVERTING, State.REVERTING)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            // install and validate transit and egress rules
            builder.externalTransition()
                    .from(State.RESOURCES_ALLOCATED)
                    .to(State.INSTALLING_RULES)
                    .on(Event.NEXT)
                    .perform(new EmitInstallRulesRequestsAction(persistenceManager, ruleManager));

            // skip installation on transit and egress rules for one switch flow
            builder.externalTransition()
                    .from(State.INSTALLING_RULES)
                    .to(State.NOTIFY_FLOW_STATS)
                    .on(Event.SKIP_RULES_INSTALL);

            builder.internalTransition()
                    .within(State.INSTALLING_RULES)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedResponseAction(
                            config.speakerCommandRetriesLimit, REMOVE_ACTION_NAME, Event.RULES_INSTALLED));

            // rollback in case of error
            builder.transitions()
                    .from(State.INSTALLING_RULES)
                    .toAmong(State.REMOVING_RULES, State.REMOVING_RULES)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new RollbackInstalledRulesAction(persistenceManager, ruleManager));

            builder.transition()
                    .from(State.INSTALLING_RULES)
                    .to(State.NOTIFY_FLOW_STATS)
                    .on(Event.RULES_INSTALLED);

            builder.onEntry(State.NOTIFY_FLOW_STATS)
                    .perform(new NotifyFlowStatsAction(persistenceManager, carrier));

            // install and validate ingress rules
            builder.transitions()
                    .from(State.NOTIFY_FLOW_STATS)
                    .toAmong(State.NOTIFY_FLOW_MONITOR)
                    .onEach(Event.NEXT)
                    .perform(new NotifyHaFlowMonitorAction<>(persistenceManager, carrier));

            builder.transitions()
                    .from(State.NOTIFY_FLOW_MONITOR)
                    .toAmong(State.FINISHED)
                    .onEach(Event.NEXT)
                    .perform(new CompleteFlowCreateAction(persistenceManager, dashboardLogger));

            // rules deletion
            builder.transition()
                    .from(State.REMOVING_RULES)
                    .to(State.REMOVING_RULES)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedResponseAction(
                            config.speakerCommandRetriesLimit, REMOVE_ACTION_NAME, Event.RULES_REMOVED));
            builder.transitions()
                    .from(State.REMOVING_RULES)
                    .toAmong(State.REVERTING, State.REVERTING)
                    .onEach(Event.TIMEOUT, Event.ERROR);
            builder.transition()
                    .from(State.REMOVING_RULES)
                    .to(State.REVERTING)
                    .on(Event.RULES_REMOVED);

            // resources deallocation
            builder.onEntry(State.REVERTING)
                    .perform(reportErrorAction);
            builder.transition()
                    .from(State.REVERTING)
                    .to(State.RESOURCES_DEALLOCATED)
                    .on(Event.NEXT)
                    .perform(new ResourcesDeallocationAction(resourcesManager, persistenceManager));

            builder.transitions()
                    .from(State.RESOURCES_DEALLOCATED)
                    .toAmong(State.FAILED, State.FAILED)
                    .onEach(Event.ERROR, Event.TIMEOUT)
                    .perform(new HandleNotDeallocatedResourcesAction());

            builder.transition()
                    .from(State.RESOURCES_DEALLOCATED)
                    .to(State.FAILED)
                    .on(Event.NEXT);

            builder.transition()
                    .from(State.FAILED)
                    .toFinal(State.NOTIFY_FLOW_MONITOR_WITH_ERROR)
                    .on(Event.NEXT)
                    .perform(new HandleNotCreatedFlowAction(persistenceManager, dashboardLogger));

            builder.transition()
                    .from(State.FAILED)
                    .to(State.RESOURCES_ALLOCATED)
                    .on(Event.RETRY)
                    .perform(new ResourcesAllocationAction(pathComputer, persistenceManager,
                            config.getPathAllocationRetriesLimit(), config.getPathAllocationRetryDelay(),
                            resourcesManager));

            builder.onEntry(State.FAILED)
                    .perform(reportErrorAction);
            builder.transitions()
                    .from(State.FAILED)
                    .toAmong(State.NOTIFY_FLOW_MONITOR_WITH_ERROR, State.NOTIFY_FLOW_MONITOR_WITH_ERROR)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.onEntry(State.FINISHED_WITH_ERROR)
                    .perform(reportErrorAction);

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

        public HaFlowCreateFsm newInstance(@NonNull CommandContext commandContext, @NonNull String flowId,
                                           @NonNull Collection<FlowProcessingEventListener> eventListeners) {
            HaFlowCreateFsm fsm = builder.newStateMachine(State.INITIALIZED, commandContext, carrier, flowId,
                    eventListeners, config);

            fsm.addTransitionCompleteListener(event ->
                    log.debug("FlowCreateFsm, transition to {} on {}", event.getTargetState(), event.getCause()));

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

    @Value
    @Builder
    public static class Config implements Serializable {
        @Builder.Default
        int haFlowCreationRetriesLimit = 10;
        @Builder.Default
        int speakerCommandRetriesLimit = 3;
        @Builder.Default
        int pathAllocationRetriesLimit = 10;
        @Builder.Default
        int pathAllocationRetryDelay = 50;
    }

    public enum State {
        INITIALIZED,
        FLOW_VALIDATED,
        RESOURCES_ALLOCATED,
        INSTALLING_RULES,
        FINISHED,

        REMOVING_RULES,
        REVERTING,
        RESOURCES_DEALLOCATED,

        NOTIFY_FLOW_MONITOR,
        NOTIFY_FLOW_MONITOR_WITH_ERROR,

        NOTIFY_FLOW_STATS,

        FAILED,
        FINISHED_WITH_ERROR;
    }

    public enum Event {
        NEXT,

        RESPONSE_RECEIVED,
        SKIP_RULES_INSTALL,
        RULES_INSTALLED,
        RULES_REMOVED,

        TIMEOUT,
        RETRY,
        ERROR
    }
}
