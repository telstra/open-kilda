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

package org.openkilda.wfm.topology.flowhs.fsm.create;

import org.openkilda.floodlight.flow.request.InstallIngressRule;
import org.openkilda.floodlight.flow.request.InstallTransitRule;
import org.openkilda.floodlight.flow.request.RemoveRule;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.IslConfig;
import org.openkilda.model.PathId;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.config.IslCostConfig;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.NbTrackableStateMachine;
import org.openkilda.wfm.topology.flowhs.fsm.common.SpeakerCommandFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.CompleteFlowCreateAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.DumpIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.DumpNonIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.FlowValidateAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.HandleNotCreatedFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.InstallIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.InstallNonIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.OnReceivedDeleteResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.OnReceivedInstallResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.OnReceivedResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.ProcessNotRevertedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.ResourcesAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.ResourcesDeallocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.RollbackInstalledRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.ValidateIngressRuleAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.ValidateNonIngressRuleAction;
import org.openkilda.wfm.topology.flowhs.service.FlowCreateHubCarrier;
import org.openkilda.wfm.topology.flowhs.service.SpeakerCommandObserver;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Slf4j
public final class FlowCreateFsm extends NbTrackableStateMachine<FlowCreateFsm, State, Event, FlowCreateContext> {

    private final String flowId;
    private final FlowCreateHubCarrier carrier;
    private final IslConfig islConfig;

    private Map<UUID, SpeakerCommandObserver> pendingCommands = new HashMap<>();
    private Set<UUID> failedCommands = new HashSet<>();

    private List<FlowResources> flowResources = new ArrayList<>();
    private PathId forwardPathId;
    private PathId reversePathId;
    private PathId protectedForwardPathId;
    private PathId protectedReversePathId;

    private Map<UUID, InstallIngressRule> ingressCommands = new HashMap<>();
    private Map<UUID, InstallTransitRule> nonIngressCommands = new HashMap<>();
    private Map<UUID, RemoveRule> removeCommands = new HashMap<>();

    // The amount of flow create operation retries left: that means how many retries may be executed.
    // NB: it differs from command execution retries amount.
    private int remainRetries;
    private boolean timedOut;

    private FlowCreateFsm(String flowId, CommandContext commandContext, FlowCreateHubCarrier carrier, Config config,
                          IslCostConfig islCostConfig) {
        super(commandContext);
        this.flowId = flowId;
        this.carrier = carrier;
        this.remainRetries = config.getFlowCreationRetriesLimit();
        this.islConfig = IslConfig.builder()
                .unstableIslTimeoutSec(islCostConfig.getIslUnstableTimeoutSec())
                .unstableCostRaise(islCostConfig.getIslCostWhenPortDown())
                .underMaintenanceCostRaise(islCostConfig.getIslCostWhenUnderMaintenance())
                .build();
    }

    public boolean isPendingCommand(UUID commandId) {
        return pendingCommands.containsKey(commandId);
    }

    /**
     * Initiates a retry if limit is not exceeded.
     * @return true if retry was triggered.
     */
    public boolean retryIfAllowed() {
        if (!timedOut && remainRetries-- > 0) {
            log.info("About to retry flow create. Retries left: {}", remainRetries);
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

    protected void retryIfAllowed(State from, State to, Event event, FlowCreateContext context) {
        if (!retryIfAllowed()) {
            fire(Event.NEXT);
        }
    }

    public void fireTimeout() {
        timedOut = true;
        fire(Event.TIMEOUT);
    }

    @Override
    protected void afterTransitionCausedException(State fromState, State toState, Event event,
                                                  FlowCreateContext context) {
        super.afterTransitionCausedException(fromState, toState, event, context);
        if (fromState == State.INITIALIZED || fromState == State.FLOW_VALIDATED) {
            ErrorData error = new ErrorData(ErrorType.INTERNAL_ERROR, "Could not create flow",
                    getLastException().getMessage());
            Message message = new ErrorMessage(error, getCommandContext().getCreateTime(),
                    getCommandContext().getCorrelationId());
            carrier.sendNorthboundResponse(message);
        }

        fireError();
    }

    @Override
    public void fireNext(FlowCreateContext context) {
        fire(Event.NEXT, context);
    }

    @Override
    public void fireError() {
        fire(Event.ERROR);
    }

    @Override
    public void sendResponse(Message message) {
        carrier.sendNorthboundResponse(message);
    }

    public static FlowCreateFsm.Factory factory(PersistenceManager persistenceManager, FlowCreateHubCarrier carrier,
                                                Config config, FlowResourcesManager resourcesManager,
                                                PathComputer pathComputer, IslCostConfig islCostConfig) {
        return new Factory(persistenceManager, carrier, config, resourcesManager, pathComputer, islCostConfig);
    }

    @Getter
    public enum State {
        INITIALIZED(false),
        FLOW_VALIDATED(false),
        RESOURCES_ALLOCATED(false),
        INSTALLING_NON_INGRESS_RULES(true),
        VALIDATING_NON_INGRESS_RULES(true),
        INSTALLING_INGRESS_RULES(true),
        VALIDATING_INGRESS_RULES(true),
        FINISHED(true),

        REMOVING_RULES(true),
        VALIDATING_REMOVED_RULES(true),
        REVERTING(false),
        RESOURCES_DE_ALLOCATED(false),

        _FAILED(false),
        FINISHED_WITH_ERROR(true);

        boolean blocked;

        State(boolean blocked) {
            this.blocked = blocked;
        }
    }

    public enum Event {
        NEXT,

        RESPONSE_RECEIVED,
        RULE_RECEIVED,
        SKIP_NON_INGRESS_RULES_INSTALL,

        TIMEOUT,
        PATH_NOT_FOUND,
        RETRY,
        ERROR
    }

    public static class Factory {
        private final StateMachineBuilder<FlowCreateFsm, State, Event, FlowCreateContext> builder;
        private final FlowCreateHubCarrier carrier;
        private final Config config;
        private final IslCostConfig islCostConfig;

        Factory(PersistenceManager persistenceManager, FlowCreateHubCarrier carrier, Config config,
                       FlowResourcesManager resourcesManager, PathComputer pathComputer, IslCostConfig islCostConfig) {
            this.builder = StateMachineBuilderFactory.create(
                    FlowCreateFsm.class, State.class, Event.class, FlowCreateContext.class,
                    String.class, CommandContext.class, FlowCreateHubCarrier.class, Config.class, IslCostConfig.class);
            this.carrier = carrier;
            this.config = config;
            this.islCostConfig = islCostConfig;

            SpeakerCommandFsm.Builder commandExecutorFsmBuilder =
                    SpeakerCommandFsm.getBuilder(carrier, config.getSpeakerCommandRetriesLimit());

            FlowOperationsDashboardLogger dashboardLogger = new FlowOperationsDashboardLogger(log);

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
                    .perform(new ResourcesAllocationAction(pathComputer, persistenceManager, resourcesManager));

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
                    .to(State.INSTALLING_INGRESS_RULES)
                    .on(Event.SKIP_NON_INGRESS_RULES_INSTALL)
                    .perform(new InstallIngressRulesAction(commandExecutorFsmBuilder, persistenceManager,
                            resourcesManager));

            // install and validate transit and egress rules
            builder.externalTransition()
                    .from(State.RESOURCES_ALLOCATED)
                    .to(State.INSTALLING_NON_INGRESS_RULES)
                    .on(Event.NEXT)
                    .perform(new InstallNonIngressRulesAction(commandExecutorFsmBuilder, persistenceManager,
                            resourcesManager));

            builder.internalTransition()
                    .within(State.INSTALLING_NON_INGRESS_RULES)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(persistenceManager));
            builder.transition()
                    .from(State.INSTALLING_NON_INGRESS_RULES)
                    .to(State.VALIDATING_NON_INGRESS_RULES)
                    .on(Event.NEXT)
                    .perform(new DumpNonIngressRulesAction(commandExecutorFsmBuilder));

            builder.internalTransition()
                    .within(State.VALIDATING_NON_INGRESS_RULES)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedResponseAction(persistenceManager));
            builder.internalTransition()
                    .within(State.VALIDATING_NON_INGRESS_RULES)
                    .on(Event.RULE_RECEIVED)
                    .perform(new ValidateNonIngressRuleAction(persistenceManager));

            // install and validate ingress rules
            builder.transitions()
                    .from(State.VALIDATING_NON_INGRESS_RULES)
                    .toAmong(State.INSTALLING_INGRESS_RULES)
                    .onEach(Event.NEXT)
                    .perform(new InstallIngressRulesAction(commandExecutorFsmBuilder, persistenceManager,
                            resourcesManager));

            builder.internalTransition()
                    .within(State.INSTALLING_INGRESS_RULES)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(persistenceManager));
            builder.transition()
                    .from(State.INSTALLING_INGRESS_RULES)
                    .to(State.VALIDATING_INGRESS_RULES)
                    .on(Event.NEXT)
                    .perform(new DumpIngressRulesAction(commandExecutorFsmBuilder));

            builder.internalTransition()
                    .within(State.VALIDATING_INGRESS_RULES)
                    .on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedResponseAction(persistenceManager));
            builder.internalTransition()
                    .within(State.VALIDATING_INGRESS_RULES)
                    .on(Event.RULE_RECEIVED)
                    .perform(new ValidateIngressRuleAction(persistenceManager));

            builder.transition()
                    .from(State.VALIDATING_INGRESS_RULES)
                    .to(State.FINISHED)
                    .on(Event.NEXT)
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
                    .perform(new RollbackInstalledRulesAction(commandExecutorFsmBuilder, persistenceManager,
                            resourcesManager));

            builder.transitions()
                    .from(State.VALIDATING_NON_INGRESS_RULES)
                    .toAmong(State.REMOVING_RULES, State.REMOVING_RULES)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new RollbackInstalledRulesAction(commandExecutorFsmBuilder, persistenceManager,
                            resourcesManager));

            builder.transitions()
                    .from(State.INSTALLING_INGRESS_RULES)
                    .toAmong(State.REMOVING_RULES, State.REMOVING_RULES)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new RollbackInstalledRulesAction(commandExecutorFsmBuilder, persistenceManager,
                            resourcesManager));

            builder.transitions()
                    .from(State.VALIDATING_INGRESS_RULES)
                    .toAmong(State.REMOVING_RULES, State.REMOVING_RULES)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new RollbackInstalledRulesAction(commandExecutorFsmBuilder, persistenceManager,
                            resourcesManager));

            // rules deletion
            builder.transitions()
                    .from(State.REMOVING_RULES)
                    .toAmong(State.REMOVING_RULES)
                    .onEach(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedDeleteResponseAction(persistenceManager));
            builder.transitions()
                    .from(State.REMOVING_RULES)
                    .toAmong(State.REVERTING, State.REVERTING)
                    .onEach(Event.TIMEOUT, Event.ERROR);
            builder.transition()
                    .from(State.REMOVING_RULES)
                    .to(State.REVERTING)
                    .on(Event.NEXT);

            // resources deallocation
            builder.transition()
                    .from(State.REVERTING)
                    .to(State.RESOURCES_DE_ALLOCATED)
                    .on(Event.NEXT)
                    .perform(new ResourcesDeallocationAction(resourcesManager, persistenceManager));

            builder.transitions()
                    .from(State.RESOURCES_DE_ALLOCATED)
                    .toAmong(State._FAILED, State._FAILED)
                    .onEach(Event.ERROR, Event.TIMEOUT)
                    .perform(new ProcessNotRevertedResourcesAction());

            builder.transition()
                    .from(State.RESOURCES_DE_ALLOCATED)
                    .to(State._FAILED)
                    .on(Event.NEXT);

            builder.onEntry(State._FAILED)
                    .callMethod("retryIfAllowed");

            builder.transition()
                    .from(State._FAILED)
                    .toFinal(State.FINISHED_WITH_ERROR)
                    .on(Event.NEXT)
                    .perform(new HandleNotCreatedFlowAction(persistenceManager, dashboardLogger));

            builder.transition()
                    .from(State._FAILED)
                    .to(State.RESOURCES_ALLOCATED)
                    .on(Event.RETRY)
                    .perform(new ResourcesAllocationAction(pathComputer, persistenceManager, resourcesManager));

            builder.transitions()
                    .from(State._FAILED)
                    .toAmong(State.FINISHED_WITH_ERROR, State.FINISHED_WITH_ERROR)
                    .onEach(Event.ERROR, Event.TIMEOUT);

            builder.defineFinalState(State.FINISHED);
            builder.defineFinalState(State.FINISHED_WITH_ERROR);
        }

        public FlowCreateFsm produce(String flowId, CommandContext commandContext) {
            return builder.newStateMachine(State.INITIALIZED, flowId, commandContext, carrier, config, islCostConfig);
        }
    }

    @Value
    @Builder
    public static class Config implements Serializable {
        @Builder.Default
        int flowCreationRetriesLimit = 10;
        @Builder.Default
        int speakerCommandRetriesLimit = 3;
    }
}
