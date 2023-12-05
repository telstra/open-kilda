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

package org.openkilda.wfm.topology.flowhs.fsm.create;

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.PathId;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.metrics.MeterRegistryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingWithHistorySupportFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.SpeakerCommandFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.CreateNewHistoryEventAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NotifyFlowMonitorAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.ReportErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.create.actions.CompleteFlowCreateAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.actions.EmitIngressRulesVerifyRequestsAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.actions.EmitNonIngressRulesVerifyRequestsAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.actions.FlowValidateAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.actions.HandleNotCreatedFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.actions.HandleNotDeallocatedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.actions.InstallIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.actions.InstallNonIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.actions.NotifyFlowStatsAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.actions.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.actions.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.actions.OnReceivedDeleteResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.actions.OnReceivedInstallResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.actions.ResourcesAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.actions.ResourcesDeallocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.actions.RollbackInstalledRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.actions.ValidateIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.actions.ValidateNonIngressRuleAction;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.service.FlowCreateEventListener;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.SpeakerCommandObserver;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
@Slf4j
public final class FlowCreateFsm extends FlowProcessingWithHistorySupportFsm<FlowCreateFsm, State, Event,
        FlowCreateContext, FlowGenericCarrier, FlowCreateEventListener> {

    private final String flowId;

    private RequestedFlow targetFlow;
    private String sharedBandwidthGroupId;
    private List<FlowResources> flowResources = new ArrayList<>();
    private PathId forwardPathId;
    private PathId reversePathId;
    private PathId protectedForwardPathId;
    private PathId protectedReversePathId;
    private boolean pathsBeenAllocated;
    private boolean backUpPrimaryPathComputationWayUsed;
    private boolean backUpProtectedPathComputationWayUsed;

    private List<FlowSegmentRequestFactory> sentCommands = new ArrayList<>();
    private Map<UUID, SpeakerCommandObserver> pendingCommands = new HashMap<>();
    private Set<UUID> failedCommands = new HashSet<>();

    private List<FlowSegmentRequestFactory> ingressCommands = new ArrayList<>();
    private List<FlowSegmentRequestFactory> nonIngressCommands = new ArrayList<>();

    // The amount of flow create operation retries left: that means how many retries may be executed.
    // NB: it differs from command execution retries amount.
    private int remainRetries;
    private boolean timedOut;

    private FlowCreateFsm(@NonNull CommandContext commandContext, @NonNull FlowGenericCarrier carrier,
                          @NonNull String flowId,
                          @NonNull Collection<FlowCreateEventListener> eventListeners, @NonNull Config config) {
        super(Event.NEXT, Event.ERROR, commandContext, carrier, eventListeners);
        this.flowId = flowId;
        this.remainRetries = config.getFlowCreationRetriesLimit();
    }

    public boolean isPendingCommand(UUID commandId) {
        return pendingCommands.containsKey(commandId);
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

    @Override
    protected void afterTransitionCausedException(State fromState, State toState, Event event,
                                                  FlowCreateContext context) {
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
        ingressCommands.clear();
        nonIngressCommands.clear();
        failedCommands.clear();
        flowResources.clear();

        pendingCommands.clear();
        sentCommands.clear();

        forwardPathId = null;
        reversePathId = null;
        protectedForwardPathId = null;
        protectedReversePathId = null;
    }

    @Override
    protected String getCrudActionName() {
        return "create";
    }

    public static class Factory {
        private final StateMachineBuilder<FlowCreateFsm, State, Event, FlowCreateContext> builder;
        private final FlowGenericCarrier carrier;
        private final Config config;

        public Factory(@NonNull FlowGenericCarrier carrier, @NonNull PersistenceManager persistenceManager,
                       @NonNull FlowResourcesManager resourcesManager, @NonNull PathComputer pathComputer,
                       @NonNull Config config) {
            this.carrier = carrier;
            this.config = config;

            this.builder = StateMachineBuilderFactory.create(FlowCreateFsm.class, State.class, Event.class,
                    FlowCreateContext.class, CommandContext.class, FlowGenericCarrier.class, String.class,
                    Collection.class, Config.class);

            SpeakerCommandFsm.Builder commandExecutorFsmBuilder =
                    SpeakerCommandFsm.getBuilder(carrier, config.getSpeakerCommandRetriesLimit());

            FlowOperationsDashboardLogger dashboardLogger = new FlowOperationsDashboardLogger(log);

            final OnReceivedInstallResponseAction onReceiveInstallResponse = new OnReceivedInstallResponseAction(
                    persistenceManager);
            final RollbackInstalledRulesAction rollbackInstalledRules = new RollbackInstalledRulesAction(
                    commandExecutorFsmBuilder, persistenceManager);
            final ReportErrorAction<FlowCreateFsm, State, Event, FlowCreateContext>
                    reportErrorAction = new ReportErrorAction<>(Event.TIMEOUT);

            builder.transition()
                    .from(State.CREATE_NEW_HISTORY_EVENT)
                    .to(State.INITIALIZED)
                    .on(Event.NEXT)
                    .perform(new CreateNewHistoryEventAction<>(persistenceManager, FlowEventData.Event.CREATE));

            // validate the flow
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

            // there is possibility that during resources allocation we have to revalidate flow again.
            // e.g. if we try to simultaneously create two flows with the same flow id then both threads can go
            // to resource allocation state at the same time
            builder.transition()
                    .from(State.RESOURCES_ALLOCATED)
                    .to(State.INITIALIZED)
                    .on(Event.RETRY);

            // skip installation on transit and egress rules for one switch flow
            builder.externalTransition()
                    .from(State.RESOURCES_ALLOCATED)
                    .to(State.NOTIFY_FLOW_STATS)
                    .on(Event.SKIP_NON_INGRESS_RULES_INSTALL);

            // install and validate transit and egress rules
            builder.externalTransition()
                    .from(State.RESOURCES_ALLOCATED)
                    .to(State.INSTALLING_NON_INGRESS_RULES)
                    .on(Event.NEXT)
                    .perform(new InstallNonIngressRulesAction(commandExecutorFsmBuilder, persistenceManager
                    ));

            builder.internalTransition()
                    .within(State.INSTALLING_NON_INGRESS_RULES)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(onReceiveInstallResponse);
            builder.transition()
                    .from(State.INSTALLING_NON_INGRESS_RULES)
                    .to(State.VALIDATING_NON_INGRESS_RULES)
                    .on(Event.RULES_INSTALLED)
                    .perform(new EmitNonIngressRulesVerifyRequestsAction(commandExecutorFsmBuilder));

            builder.internalTransition()
                    .within(State.VALIDATING_NON_INGRESS_RULES)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new ValidateNonIngressRuleAction(persistenceManager));

            builder.transition()
                    .from(State.VALIDATING_NON_INGRESS_RULES)
                    .to(State.NOTIFY_FLOW_STATS)
                    .on(Event.RULES_VALIDATED);

            builder.onEntry(State.NOTIFY_FLOW_STATS)
                    .perform(new NotifyFlowStatsAction(persistenceManager, carrier));

            // install and validate ingress rules
            builder.transitions()
                    .from(State.NOTIFY_FLOW_STATS)
                    .toAmong(State.INSTALLING_INGRESS_RULES)
                    .onEach(Event.NEXT)
                    .perform(new InstallIngressRulesAction(commandExecutorFsmBuilder, persistenceManager));

            builder.internalTransition()
                    .within(State.INSTALLING_INGRESS_RULES)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(onReceiveInstallResponse);
            builder.transition()
                    .from(State.INSTALLING_INGRESS_RULES)
                    .to(State.VALIDATING_INGRESS_RULES)
                    .on(Event.RULES_INSTALLED)
                    .perform(new EmitIngressRulesVerifyRequestsAction(commandExecutorFsmBuilder));

            builder.internalTransition()
                    .within(State.VALIDATING_INGRESS_RULES)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new ValidateIngressRulesAction(persistenceManager));

            builder.transition()
                    .from(State.VALIDATING_INGRESS_RULES)
                    .to(State.NOTIFY_FLOW_MONITOR)
                    .on(Event.RULES_VALIDATED)
                    .perform(new CompleteFlowCreateAction(persistenceManager, dashboardLogger));

            // error during validation or resource allocation
            builder.transitions()
                    .from(State.FLOW_VALIDATED)
                    .toAmong(State.FINISHED_WITH_ERROR, State.FINISHED_WITH_ERROR)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transitions()
                    .from(State.RESOURCES_ALLOCATED)
                    .toAmong(State.REVERTING, State.REVERTING)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            // rollback in case of error
            builder.transitions()
                    .from(State.INSTALLING_NON_INGRESS_RULES)
                    .toAmong(State.REMOVING_RULES, State.REMOVING_RULES)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(rollbackInstalledRules);

            builder.transitions()
                    .from(State.VALIDATING_NON_INGRESS_RULES)
                    .toAmong(State.REMOVING_RULES, State.REMOVING_RULES)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(rollbackInstalledRules);

            builder.transitions()
                    .from(State.INSTALLING_INGRESS_RULES)
                    .toAmong(State.REMOVING_RULES, State.REMOVING_RULES)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(rollbackInstalledRules);

            builder.transitions()
                    .from(State.VALIDATING_INGRESS_RULES)
                    .toAmong(State.REMOVING_RULES, State.REMOVING_RULES)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(rollbackInstalledRules);

            // rules deletion
            builder.onEntry(State.REMOVING_RULES)
                    .perform(reportErrorAction);
            builder.transition()
                    .from(State.REMOVING_RULES)
                    .to(State.REMOVING_RULES)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedDeleteResponseAction(persistenceManager));
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
                    .to(State.RESOURCES_DE_ALLOCATED)
                    .on(Event.NEXT)
                    .perform(new ResourcesDeallocationAction(resourcesManager, persistenceManager));

            builder.transitions()
                    .from(State.RESOURCES_DE_ALLOCATED)
                    .toAmong(State._FAILED, State._FAILED)
                    .onEach(Event.ERROR, Event.TIMEOUT)
                    .perform(new HandleNotDeallocatedResourcesAction());

            builder.transition()
                    .from(State.RESOURCES_DE_ALLOCATED)
                    .to(State._FAILED)
                    .on(Event.NEXT);

            builder.transition()
                    .from(State._FAILED)
                    .toFinal(State.NOTIFY_FLOW_MONITOR_WITH_ERROR)
                    .on(Event.NEXT)
                    .perform(new HandleNotCreatedFlowAction(persistenceManager, dashboardLogger));

            builder.transition()
                    .from(State._FAILED)
                    .to(State.RESOURCES_ALLOCATED)
                    .on(Event.RETRY)
                    .perform(new ResourcesAllocationAction(pathComputer, persistenceManager,
                            config.getPathAllocationRetriesLimit(), config.getPathAllocationRetryDelay(),
                            resourcesManager));

            builder.onEntry(State._FAILED)
                    .perform(reportErrorAction);
            builder.transitions()
                    .from(State._FAILED)
                    .toAmong(State.NOTIFY_FLOW_MONITOR_WITH_ERROR, State.NOTIFY_FLOW_MONITOR_WITH_ERROR)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.onEntry(State.FINISHED_WITH_ERROR)
                    .perform(reportErrorAction);

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

        public FlowCreateFsm newInstance(@NonNull CommandContext commandContext, @NonNull String flowId,
                                         @NonNull Collection<FlowCreateEventListener> eventListeners) {
            FlowCreateFsm fsm = builder.newStateMachine(State.CREATE_NEW_HISTORY_EVENT, commandContext, carrier, flowId,
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
        int flowCreationRetriesLimit = 10;
        @Builder.Default
        int speakerCommandRetriesLimit = 3;
        @Builder.Default
        int pathAllocationRetriesLimit = 10;
        @Builder.Default
        int pathAllocationRetryDelay = 50;
    }

    public enum State {
        CREATE_NEW_HISTORY_EVENT,
        INITIALIZED,
        FLOW_VALIDATED,
        RESOURCES_ALLOCATED,
        INSTALLING_NON_INGRESS_RULES,
        VALIDATING_NON_INGRESS_RULES,
        INSTALLING_INGRESS_RULES,
        VALIDATING_INGRESS_RULES,
        FINISHED,

        REMOVING_RULES,
        REVERTING,
        RESOURCES_DE_ALLOCATED,

        NOTIFY_FLOW_MONITOR,
        NOTIFY_FLOW_MONITOR_WITH_ERROR,

        NOTIFY_FLOW_STATS,

        _FAILED,
        FINISHED_WITH_ERROR;
    }

    public enum Event {
        NEXT,

        RESPONSE_RECEIVED,
        SKIP_NON_INGRESS_RULES_INSTALL,

        RULES_INSTALLED,
        RULES_VALIDATED,
        RULES_REMOVED,

        TIMEOUT,
        RETRY,
        ERROR
    }
}
