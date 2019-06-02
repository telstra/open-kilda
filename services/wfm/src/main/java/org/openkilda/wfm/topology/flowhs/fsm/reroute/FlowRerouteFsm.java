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

package org.openkilda.wfm.topology.flowhs.fsm.reroute;

import org.openkilda.floodlight.flow.request.InstallIngressRule;
import org.openkilda.floodlight.flow.request.InstallTransitRule;
import org.openkilda.floodlight.flow.request.RemoveRule;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathId;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.NbTrackableStateMachine;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.AllocateResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.CancelPendingCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.CompleteFlowPathInstallationAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.CompleteFlowPathRemovalAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.DeallocateResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.DumpIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.DumpNonIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.HandleNotDeallocatedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.HandleNotRemovedRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.HandleNotRevertedResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.InstallIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.InstallNonIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.MarkFlowDownAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.OnReceivedInstallResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.OnReceivedRemoveResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.RemoveNewRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.RemoveOldRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.RevertPathsSwapAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.RevertResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.SwapPathsAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.ValidateFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.ValidateIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.ValidateNonIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteHubCarrier;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


@Getter
@Setter
@Slf4j
public final class FlowRerouteFsm
        extends NbTrackableStateMachine<FlowRerouteFsm, State, Event, FlowRerouteContext> {

    private final FlowRerouteHubCarrier carrier;

    private String flowId;
    private boolean recreateIfSamePath;
    private boolean reroutePrimary;
    private boolean rerouteProtected;

    private FlowStatus originalFlowStatus;

    private Collection<FlowResources> newResources;
    private PathId newPrimaryForwardPath;
    private PathId newPrimaryReversePath;
    private PathId newProtectedForwardPath;
    private PathId newProtectedReversePath;

    private Collection<FlowResources> oldResources;
    private PathId oldPrimaryForwardPath;
    private FlowPathStatus oldPrimaryForwardPathStatus;
    private PathId oldPrimaryReversePath;
    private FlowPathStatus oldPrimaryReversePathStatus;
    private PathId oldProtectedForwardPath;
    private FlowPathStatus oldProtectedForwardPathStatus;
    private PathId oldProtectedReversePath;
    private FlowPathStatus oldProtectedReversePathStatus;

    private Set<UUID> pendingCommands = Collections.emptySet();

    private Map<UUID, FlowErrorResponse> errorResponses = new HashMap<>();

    private Map<UUID, FlowResponse> failedValidationResponses = new HashMap<>();

    private Map<UUID, InstallIngressRule> ingressCommands = new HashMap<>();
    private Map<UUID, InstallTransitRule> nonIngressCommands = new HashMap<>();
    private Map<UUID, RemoveRule> removeCommands = new HashMap<>();

    public FlowRerouteFsm(CommandContext commandContext, FlowRerouteHubCarrier carrier) {
        super(commandContext);
        this.carrier = carrier;
    }

    private static StateMachineBuilder<FlowRerouteFsm, State, Event, FlowRerouteContext> builder(
            PersistenceManager persistenceManager, PathComputer pathComputer, FlowResourcesManager resourcesManager) {
        StateMachineBuilder<FlowRerouteFsm, State, Event, FlowRerouteContext> builder =
                StateMachineBuilderFactory.create(FlowRerouteFsm.class, State.class, Event.class,
                        FlowRerouteContext.class, CommandContext.class, FlowRerouteHubCarrier.class);

        builder.transition().from(State.INITIALIZED).to(State.FLOW_VALIDATED).on(Event.NEXT)
                .perform(new ValidateFlowAction(persistenceManager));
        builder.transition().from(State.INITIALIZED).to(State.FINISHED_WITH_ERROR).on(Event.TIMEOUT);

        builder.transition().from(State.FLOW_VALIDATED).to(State.RESOURCES_ALLOCATED).on(Event.NEXT)
                .perform(new AllocateResourcesAction(persistenceManager, pathComputer, resourcesManager));
        builder.transitions().from(State.FLOW_VALIDATED)
                .toAmong(State.FINISHED_WITH_ERROR, State.FINISHED_WITH_ERROR)
                .onEach(Event.TIMEOUT, Event.ERROR);

        builder.transition().from(State.RESOURCES_ALLOCATED).to(State.INSTALLING_NON_INGRESS_RULES).on(Event.NEXT)
                .perform(new InstallNonIngressRulesAction(persistenceManager));
        builder.transitions().from(State.RESOURCES_ALLOCATED)
                .toAmong(State.MARKING_FLOW_DOWN, State.FINISHED)
                .onEach(Event.NO_PATH_FOUND, Event.REROUTE_IS_SKIPPED);
        builder.transitions().from(State.RESOURCES_ALLOCATED)
                .toAmong(State.REVERTING_ALLOCATED_RESOURCES, State.REVERTING_ALLOCATED_RESOURCES)
                .onEach(Event.TIMEOUT, Event.ERROR);

        builder.internalTransition().within(State.INSTALLING_NON_INGRESS_RULES).on(Event.COMMAND_EXECUTED)
                .perform(new OnReceivedInstallResponseAction());
        builder.transition().from(State.INSTALLING_NON_INGRESS_RULES).to(State.NON_INGRESS_RULES_INSTALLED)
                .on(Event.RULES_INSTALLED);
        builder.transitions().from(State.INSTALLING_NON_INGRESS_RULES)
                .toAmong(State.ROLLINGBACK_RULE_INSTALLATION, State.ROLLINGBACK_RULE_INSTALLATION)
                .onEach(Event.TIMEOUT, Event.ERROR)
                .perform(new CancelPendingCommandsAction());

        builder.transition().from(State.NON_INGRESS_RULES_INSTALLED).to(State.VALIDATING_NON_INGRESS_RULES)
                .on(Event.NEXT)
                .perform(new DumpNonIngressRulesAction());
        builder.transitions().from(State.NON_INGRESS_RULES_INSTALLED)
                .toAmong(State.ROLLINGBACK_RULE_INSTALLATION, State.ROLLINGBACK_RULE_INSTALLATION)
                .onEach(Event.TIMEOUT, Event.ERROR);

        builder.internalTransition().within(State.VALIDATING_NON_INGRESS_RULES).on(Event.COMMAND_EXECUTED)
                .perform(new ValidateNonIngressRulesAction());
        builder.transition().from(State.VALIDATING_NON_INGRESS_RULES).to(State.NON_INGRESS_RULES_VALIDATED)
                .on(Event.RULES_VALIDATED);
        builder.transitions().from(State.VALIDATING_NON_INGRESS_RULES)
                .toAmong(State.ROLLINGBACK_RULE_INSTALLATION, State.ROLLINGBACK_RULE_INSTALLATION,
                        State.ROLLINGBACK_RULE_INSTALLATION)
                .onEach(Event.TIMEOUT, Event.MISSING_RULE_FOUND, Event.ERROR)
                .perform(new CancelPendingCommandsAction());

        builder.transition().from(State.NON_INGRESS_RULES_VALIDATED).to(State.PATHS_SWAPPED).on(Event.NEXT)
                .perform(new SwapPathsAction(persistenceManager, resourcesManager));
        builder.transitions().from(State.NON_INGRESS_RULES_VALIDATED)
                .toAmong(State.ROLLINGBACK_PATHS_SWAP, State.ROLLINGBACK_PATHS_SWAP)
                .onEach(Event.TIMEOUT, Event.ERROR);

        builder.transition().from(State.PATHS_SWAPPED).to(State.INSTALLING_INGRESS_RULES).on(Event.NEXT)
                .perform(new InstallIngressRulesAction(persistenceManager));
        builder.transitions().from(State.PATHS_SWAPPED)
                .toAmong(State.ROLLINGBACK_PATHS_SWAP, State.ROLLINGBACK_PATHS_SWAP)
                .onEach(Event.TIMEOUT, Event.ERROR);

        builder.internalTransition().within(State.INSTALLING_INGRESS_RULES).on(Event.COMMAND_EXECUTED)
                .perform(new OnReceivedInstallResponseAction());
        builder.transition().from(State.INSTALLING_INGRESS_RULES).to(State.INGRESS_RULES_INSTALLED)
                .on(Event.RULES_INSTALLED);
        builder.transitions().from(State.INSTALLING_INGRESS_RULES)
                .toAmong(State.ROLLINGBACK_PATHS_SWAP, State.ROLLINGBACK_PATHS_SWAP)
                .onEach(Event.TIMEOUT, Event.ERROR)
                .perform(new CancelPendingCommandsAction());

        builder.transition().from(State.INGRESS_RULES_INSTALLED).to(State.VALIDATING_INGRESS_RULES).on(Event.NEXT)
                .perform(new DumpIngressRulesAction());
        builder.transitions().from(State.INGRESS_RULES_INSTALLED)
                .toAmong(State.ROLLINGBACK_PATHS_SWAP, State.ROLLINGBACK_PATHS_SWAP)
                .onEach(Event.TIMEOUT, Event.ERROR);

        builder.internalTransition().within(State.VALIDATING_INGRESS_RULES).on(Event.COMMAND_EXECUTED)
                .perform(new ValidateIngressRulesAction());
        builder.transition().from(State.VALIDATING_INGRESS_RULES).to(State.INGRESS_RULES_VALIDATED)
                .on(Event.RULES_VALIDATED);
        builder.transitions().from(State.VALIDATING_INGRESS_RULES)
                .toAmong(State.ROLLINGBACK_PATHS_SWAP, State.ROLLINGBACK_PATHS_SWAP,
                        State.ROLLINGBACK_PATHS_SWAP)
                .onEach(Event.TIMEOUT, Event.MISSING_RULE_FOUND, Event.ERROR)
                .perform(new CancelPendingCommandsAction());

        builder.transition().from(State.INGRESS_RULES_VALIDATED).to(State.PATHS_INSTALLATION_COMPLETED).on(Event.NEXT)
                .perform(new CompleteFlowPathInstallationAction(persistenceManager));
        builder.transitions().from(State.INGRESS_RULES_VALIDATED)
                .toAmong(State.ROLLINGBACK_PATHS_SWAP, State.ROLLINGBACK_PATHS_SWAP)
                .onEach(Event.TIMEOUT, Event.ERROR);

        builder.transition().from(State.PATHS_INSTALLATION_COMPLETED).to(State.REMOVING_OLD_RULES).on(Event.NEXT)
                .perform(new RemoveOldRulesAction(persistenceManager));
        builder.transitions().from(State.PATHS_INSTALLATION_COMPLETED)
                .toAmong(State.ROLLINGBACK_PATHS_SWAP, State.ROLLINGBACK_PATHS_SWAP)
                .onEach(Event.TIMEOUT, Event.ERROR);

        builder.internalTransition().within(State.REMOVING_OLD_RULES).on(Event.COMMAND_EXECUTED)
                .perform(new OnReceivedRemoveResponseAction());
        builder.transition().from(State.REMOVING_OLD_RULES).to(State.OLD_RULES_REMOVED)
                .on(Event.RULES_REMOVED);
        builder.transition().from(State.REMOVING_OLD_RULES).to(State.OLD_RULES_REMOVED)
                .on(Event.ERROR)
                .perform(new HandleNotRemovedRulesAction());

        builder.transition().from(State.OLD_RULES_REMOVED).to(State.PATHS_REMOVAL_COMPLETED).on(Event.NEXT)
                .perform(new CompleteFlowPathRemovalAction(persistenceManager));

        builder.transition().from(State.PATHS_REMOVAL_COMPLETED).to(State.RESOURCES_DEALLOCATED).on(Event.NEXT)
                .perform(new DeallocateResourcesAction(resourcesManager));

        builder.transition().from(State.RESOURCES_DEALLOCATED).to(State.FINISHED).on(Event.NEXT);
        builder.transition().from(State.RESOURCES_DEALLOCATED).to(State.FINISHED_WITH_ERROR)
                .on(Event.ERROR)
                .perform(new HandleNotDeallocatedResourcesAction());

        builder.transition().from(State.MARKING_FLOW_DOWN).to(State.RESOURCES_ALLOCATION_REVERTED)
                .on(Event.NEXT)
                .perform(new MarkFlowDownAction(persistenceManager));

        builder.transition().from(State.ROLLINGBACK_PATHS_SWAP).to(State.ROLLINGBACK_RULE_INSTALLATION)
                .on(Event.NEXT)
                .perform(new RevertPathsSwapAction(persistenceManager));

        builder.transition().from(State.ROLLINGBACK_RULE_INSTALLATION).to(State.REMOVING_NEW_RULES).on(Event.NEXT)
                .perform(new RemoveNewRulesAction(persistenceManager));

        builder.internalTransition().within(State.REMOVING_NEW_RULES).on(Event.COMMAND_EXECUTED)
                .perform(new OnReceivedRemoveResponseAction());
        builder.transition().from(State.REMOVING_NEW_RULES).to(State.NEW_RULES_REMOVED)
                .on(Event.RULES_REMOVED);
        builder.transition().from(State.REMOVING_NEW_RULES).to(State.NEW_RULES_REMOVED)
                .on(Event.ERROR)
                .perform(new HandleNotRemovedRulesAction());

        builder.transitions().from(State.NEW_RULES_REMOVED)
                .toAmong(State.REVERTING_ALLOCATED_RESOURCES, State.REVERTING_ALLOCATED_RESOURCES)
                .onEach(Event.NEXT, Event.ERROR);

        builder.transition().from(State.REVERTING_ALLOCATED_RESOURCES).to(State.RESOURCES_ALLOCATION_REVERTED)
                .on(Event.NEXT)
                .perform(new RevertResourceAllocationAction(persistenceManager, resourcesManager));
        builder.transition().from(State.RESOURCES_ALLOCATION_REVERTED).to(State.FINISHED_WITH_ERROR).on(Event.NEXT);
        builder.transition().from(State.RESOURCES_ALLOCATION_REVERTED).to(State.FINISHED_WITH_ERROR)
                .on(Event.ERROR)
                .perform(new HandleNotRevertedResourceAllocationAction());

        return builder;
    }

    @Override
    protected void afterTransitionCausedException(State fromState, State toState,
                                                  Event event, FlowRerouteContext context) {
        if (fromState == State.INITIALIZED || fromState == State.FLOW_VALIDATED) {
            ErrorData error = new ErrorData(ErrorType.INTERNAL_ERROR, "Could not create flow",
                    getLastException().getMessage());
            Message message = new ErrorMessage(error, getCommandContext().getCreateTime(),
                    getCommandContext().getCorrelationId());
            carrier.sendNorthboundResponse(message);
        }

        fireError();

        super.afterTransitionCausedException(fromState, toState, event, context);
    }

    @Override
    public void fireNext(FlowRerouteContext context) {
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

    public static FlowRerouteFsm newInstance(CommandContext commandContext, FlowRerouteHubCarrier carrier,
                                             PersistenceManager persistenceManager,
                                             PathComputer pathComputer, FlowResourcesManager resourcesManager) {
        return newInstance(State.INITIALIZED, commandContext, carrier,
                persistenceManager, pathComputer, resourcesManager);
    }

    public static FlowRerouteFsm newInstance(State state, CommandContext commandContext,
                                             FlowRerouteHubCarrier carrier,
                                             PersistenceManager persistenceManager,
                                             PathComputer pathComputer, FlowResourcesManager resourcesManager) {
        return builder(persistenceManager, pathComputer, resourcesManager)
                .newStateMachine(state, commandContext, carrier);
    }

    public enum State {
        INITIALIZED,
        FLOW_VALIDATED,
        RESOURCES_ALLOCATED,

        INSTALLING_NON_INGRESS_RULES,
        NON_INGRESS_RULES_INSTALLED,
        VALIDATING_NON_INGRESS_RULES,
        NON_INGRESS_RULES_VALIDATED,

        PATHS_SWAPPED,

        INSTALLING_INGRESS_RULES,
        INGRESS_RULES_INSTALLED,
        VALIDATING_INGRESS_RULES,
        INGRESS_RULES_VALIDATED,

        PATHS_INSTALLATION_COMPLETED,

        REMOVING_OLD_RULES,
        OLD_RULES_REMOVED,

        PATHS_REMOVAL_COMPLETED,

        RESOURCES_DEALLOCATED,

        ROLLINGBACK_PATHS_SWAP,
        ROLLINGBACK_RULE_INSTALLATION,
        REMOVING_NEW_RULES,
        NEW_RULES_REMOVED,

        MARKING_FLOW_DOWN,
        REVERTING_ALLOCATED_RESOURCES,
        RESOURCES_ALLOCATION_REVERTED,

        FINISHED,
        FINISHED_WITH_ERROR
    }

    public enum Event {
        NEXT,

        NO_PATH_FOUND,
        REROUTE_IS_SKIPPED,

        COMMAND_EXECUTED,

        RULES_INSTALLED,
        RULES_VALIDATED,
        MISSING_RULE_FOUND,

        RULES_REMOVED,

        TIMEOUT,
        ERROR
    }
}
