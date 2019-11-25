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

import org.openkilda.messaging.Message;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowPathSwappingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.AbandonPendingCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.CompleteFlowPathRemovalAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.DeallocateResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.EmitIngressRulesVerifyRequestsAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.EmitNonIngressRulesVerifyRequestsAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HandleNotCompletedCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HandleNotDeallocatedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HandleNotRemovedPathsAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HandleNotRevertedResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.OnReceivedInstallResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.OnReceivedRemoveOrRevertResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.OnReceivedValidateIngressResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.OnReceivedValidateNonIngressResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.RemoveOldRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.RevertFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.RevertNewRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.RevertPathsSwapAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.RevertResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.UpdateFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.AllocatePrimaryResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.AllocateProtectedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.CompleteFlowPathInstallationAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.InstallIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.InstallNonIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.OnNoPathFoundAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.PostResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.SwapFlowPathsAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.actions.ValidateFlowAction;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteHubCarrier;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

@Getter
@Setter
@Slf4j
public final class FlowRerouteFsm extends FlowPathSwappingFsm<FlowRerouteFsm, State, Event, FlowRerouteContext> {

    private final FlowRerouteHubCarrier carrier;

    private boolean recreateIfSamePath;
    private boolean reroutePrimary;
    private boolean rerouteProtected;

    public FlowRerouteFsm(CommandContext commandContext, FlowRerouteHubCarrier carrier, String flowId) {
        super(commandContext, flowId);
        this.carrier = carrier;
    }

    @Override
    public void fireError(String errorReason) {
        fireError(Event.ERROR, errorReason);
    }

    @Override
    public void fireNoPathFound(String errorReason) {
        fireError(Event.NO_PATH_FOUND, errorReason);
    }

    public void fireRerouteIsSkipped(String errorReason) {
        fireError(Event.REROUTE_IS_SKIPPED, errorReason);
    }

    @Override
    public void sendResponse(Message message) {
        carrier.sendNorthboundResponse(message);
    }

    public static class Factory {
        private final StateMachineBuilder<FlowRerouteFsm, State, Event, FlowRerouteContext> builder;
        private final FlowRerouteHubCarrier carrier;

        public Factory(FlowRerouteHubCarrier carrier, PersistenceManager persistenceManager,
                       PathComputer pathComputer, FlowResourcesManager resourcesManager,
                       int transactionRetriesLimit, int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
                       int speakerCommandRetriesLimit) {
            this.carrier = carrier;

            builder = StateMachineBuilderFactory.create(FlowRerouteFsm.class, State.class, Event.class,
                    FlowRerouteContext.class, CommandContext.class, FlowRerouteHubCarrier.class, String.class);

            FlowOperationsDashboardLogger dashboardLogger = new FlowOperationsDashboardLogger(log);

            builder.transition().from(State.INITIALIZED).to(State.FLOW_VALIDATED).on(Event.NEXT)
                    .perform(new ValidateFlowAction(persistenceManager, dashboardLogger));
            builder.transition().from(State.INITIALIZED).to(State.FINISHED_WITH_ERROR).on(Event.TIMEOUT);

            builder.transition().from(State.FLOW_VALIDATED).to(State.PRIMARY_RESOURCES_ALLOCATED).on(Event.NEXT)
                    .perform(new AllocatePrimaryResourcesAction(persistenceManager, transactionRetriesLimit,
                            pathAllocationRetriesLimit, pathAllocationRetryDelay,
                            pathComputer, resourcesManager, dashboardLogger));
            builder.transitions().from(State.FLOW_VALIDATED)
                    .toAmong(State.REVERTING_FLOW_STATUS, State.REVERTING_FLOW_STATUS)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.PRIMARY_RESOURCES_ALLOCATED).to(State.PROTECTED_RESOURCES_ALLOCATED)
                    .on(Event.NEXT)
                    .perform(new AllocateProtectedResourcesAction(persistenceManager, transactionRetriesLimit,
                            pathAllocationRetriesLimit, pathAllocationRetryDelay,
                            pathComputer, resourcesManager, dashboardLogger));
            builder.transition().from(State.PRIMARY_RESOURCES_ALLOCATED).to(State.NO_PATH_FOUND)
                    .on(Event.NO_PATH_FOUND);
            builder.transitions().from(State.PRIMARY_RESOURCES_ALLOCATED)
                    .toAmong(State.REVERTING_ALLOCATED_RESOURCES, State.REVERTING_ALLOCATED_RESOURCES)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.PROTECTED_RESOURCES_ALLOCATED).to(State.RESOURCE_ALLOCATION_COMPLETED)
                    .on(Event.NEXT)
                    .perform(new PostResourceAllocationAction(persistenceManager, dashboardLogger));
            builder.transition().from(State.PROTECTED_RESOURCES_ALLOCATED).to(State.NO_PATH_FOUND)
                    .on(Event.NO_PATH_FOUND);
            builder.transitions().from(State.PROTECTED_RESOURCES_ALLOCATED)
                    .toAmong(State.REVERTING_ALLOCATED_RESOURCES, State.REVERTING_ALLOCATED_RESOURCES)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.RESOURCE_ALLOCATION_COMPLETED).to(State.INSTALLING_NON_INGRESS_RULES)
                    .on(Event.NEXT)
                    .perform(new InstallNonIngressRulesAction(persistenceManager, resourcesManager));
            builder.transition().from(State.RESOURCE_ALLOCATION_COMPLETED).to(State.FINISHED_WITH_ERROR)
                    .on(Event.REROUTE_IS_SKIPPED)
                    .perform(new RevertFlowStatusAction<>(persistenceManager, dashboardLogger));
            builder.transitions().from(State.RESOURCE_ALLOCATION_COMPLETED)
                    .toAmong(State.REVERTING_ALLOCATED_RESOURCES, State.REVERTING_ALLOCATED_RESOURCES)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.internalTransition().within(State.INSTALLING_NON_INGRESS_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction<>(speakerCommandRetriesLimit, Event.RULES_INSTALLED));
            builder.internalTransition().within(State.INSTALLING_NON_INGRESS_RULES).on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction<>(speakerCommandRetriesLimit, Event.RULES_INSTALLED));
            builder.transition().from(State.INSTALLING_NON_INGRESS_RULES).to(State.NON_INGRESS_RULES_INSTALLED)
                    .on(Event.RULES_INSTALLED);
            builder.transitions().from(State.INSTALLING_NON_INGRESS_RULES)
                    .toAmong(State.PATHS_SWAP_REVERTED, State.PATHS_SWAP_REVERTED)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new AbandonPendingCommandsAction<>());

            builder.transition().from(State.NON_INGRESS_RULES_INSTALLED).to(State.VALIDATING_NON_INGRESS_RULES)
                    .on(Event.NEXT)
                    .perform(new EmitNonIngressRulesVerifyRequestsAction<>(Event.RULES_VALIDATED));
            builder.transitions().from(State.NON_INGRESS_RULES_INSTALLED)
                    .toAmong(State.PATHS_SWAP_REVERTED, State.PATHS_SWAP_REVERTED)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.internalTransition().within(State.VALIDATING_NON_INGRESS_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedValidateNonIngressResponseAction<>(speakerCommandRetriesLimit,
                            Event.RULES_VALIDATED, Event.MISSING_RULE_FOUND));
            builder.internalTransition().within(State.VALIDATING_NON_INGRESS_RULES).on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedValidateNonIngressResponseAction<>(speakerCommandRetriesLimit,
                            Event.RULES_VALIDATED, Event.MISSING_RULE_FOUND));
            builder.transition().from(State.VALIDATING_NON_INGRESS_RULES).to(State.NON_INGRESS_RULES_VALIDATED)
                    .on(Event.RULES_VALIDATED);
            builder.transitions().from(State.VALIDATING_NON_INGRESS_RULES)
                    .toAmong(State.PATHS_SWAP_REVERTED, State.PATHS_SWAP_REVERTED, State.PATHS_SWAP_REVERTED)
                    .onEach(Event.TIMEOUT, Event.MISSING_RULE_FOUND, Event.ERROR)
                    .perform(new AbandonPendingCommandsAction<>());

            builder.transition().from(State.NON_INGRESS_RULES_VALIDATED).to(State.PATHS_SWAPPED).on(Event.NEXT)
                    .perform(new SwapFlowPathsAction(persistenceManager, resourcesManager));
            builder.transitions().from(State.NON_INGRESS_RULES_VALIDATED)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.PATHS_SWAPPED).to(State.INSTALLING_INGRESS_RULES).on(Event.NEXT)
                    .perform(new InstallIngressRulesAction(persistenceManager, resourcesManager));
            builder.transitions().from(State.PATHS_SWAPPED)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.internalTransition().within(State.INSTALLING_INGRESS_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction<>(speakerCommandRetriesLimit, Event.RULES_INSTALLED));
            builder.internalTransition().within(State.INSTALLING_INGRESS_RULES).on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction<>(speakerCommandRetriesLimit, Event.RULES_INSTALLED));
            builder.transition().from(State.INSTALLING_INGRESS_RULES).to(State.INGRESS_RULES_INSTALLED)
                    .on(Event.RULES_INSTALLED);
            builder.transition().from(State.INSTALLING_INGRESS_RULES).to(State.INGRESS_RULES_VALIDATED)
                    .on(Event.INGRESS_IS_SKIPPED);
            builder.transitions().from(State.INSTALLING_INGRESS_RULES)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new AbandonPendingCommandsAction<>());

            builder.transition().from(State.INGRESS_RULES_INSTALLED).to(State.VALIDATING_INGRESS_RULES).on(Event.NEXT)
                    .perform(new EmitIngressRulesVerifyRequestsAction<>());
            builder.transitions().from(State.INGRESS_RULES_INSTALLED)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.internalTransition().within(State.VALIDATING_INGRESS_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedValidateIngressResponseAction<>(speakerCommandRetriesLimit,
                            Event.RULES_VALIDATED, Event.MISSING_RULE_FOUND));
            builder.internalTransition().within(State.VALIDATING_INGRESS_RULES).on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedValidateIngressResponseAction<>(speakerCommandRetriesLimit,
                            Event.RULES_VALIDATED, Event.MISSING_RULE_FOUND));
            builder.transition().from(State.VALIDATING_INGRESS_RULES).to(State.INGRESS_RULES_VALIDATED)
                    .on(Event.RULES_VALIDATED);
            builder.transitions().from(State.VALIDATING_INGRESS_RULES)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.MISSING_RULE_FOUND, Event.ERROR)
                    .perform(new AbandonPendingCommandsAction<>());

            builder.transition().from(State.INGRESS_RULES_VALIDATED).to(State.NEW_PATHS_INSTALLATION_COMPLETED)
                    .on(Event.NEXT)
                    .perform(new CompleteFlowPathInstallationAction(persistenceManager));
            builder.transitions().from(State.INGRESS_RULES_VALIDATED)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.NEW_PATHS_INSTALLATION_COMPLETED)
                    .to(State.REMOVING_OLD_RULES).on(Event.NEXT)
                    .perform(new RemoveOldRulesAction<>(persistenceManager, resourcesManager, Event.RULES_REMOVED));
            builder.transitions().from(State.NEW_PATHS_INSTALLATION_COMPLETED)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.internalTransition().within(State.REMOVING_OLD_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRemoveOrRevertResponseAction<>(speakerCommandRetriesLimit,
                            Event.RULES_REMOVED));
            builder.internalTransition().within(State.REMOVING_OLD_RULES).on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedRemoveOrRevertResponseAction<>(speakerCommandRetriesLimit,
                            Event.RULES_REMOVED));
            builder.transition().from(State.REMOVING_OLD_RULES).to(State.OLD_RULES_REMOVED)
                    .on(Event.RULES_REMOVED);
            builder.transition().from(State.REMOVING_OLD_RULES).to(State.OLD_RULES_REMOVED)
                    .on(Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction<>("reroute"));

            builder.transition().from(State.OLD_RULES_REMOVED).to(State.OLD_PATHS_REMOVAL_COMPLETED).on(Event.NEXT)
                    .perform(new CompleteFlowPathRemovalAction<>(persistenceManager, transactionRetriesLimit));

            builder.transition().from(State.OLD_PATHS_REMOVAL_COMPLETED).to(State.DEALLOCATING_OLD_RESOURCES)
                    .on(Event.NEXT);
            builder.transition().from(State.OLD_PATHS_REMOVAL_COMPLETED).to(State.DEALLOCATING_OLD_RESOURCES)
                    .on(Event.ERROR)
                    .perform(new HandleNotRemovedPathsAction<>());

            builder.transition().from(State.DEALLOCATING_OLD_RESOURCES)
                    .to(State.OLD_RESOURCES_DEALLOCATED).on(Event.NEXT)
                    .perform(new DeallocateResourcesAction<>(persistenceManager, resourcesManager));

            builder.transition().from(State.OLD_RESOURCES_DEALLOCATED).to(State.UPDATING_FLOW_STATUS).on(Event.NEXT);
            builder.transition().from(State.OLD_RESOURCES_DEALLOCATED).to(State.UPDATING_FLOW_STATUS)
                    .on(Event.ERROR)
                    .perform(new HandleNotDeallocatedResourcesAction<>());

            builder.transition().from(State.UPDATING_FLOW_STATUS).to(State.FLOW_STATUS_UPDATED).on(Event.NEXT)
                    .perform(new UpdateFlowStatusAction<>(persistenceManager, dashboardLogger));

            builder.transition().from(State.FLOW_STATUS_UPDATED).to(State.FINISHED).on(Event.NEXT);
            builder.transition().from(State.FLOW_STATUS_UPDATED).to(State.FINISHED_WITH_ERROR).on(Event.ERROR);

            builder.transition().from(State.NO_PATH_FOUND).to(State.REVERTING_ALLOCATED_RESOURCES)
                    .on(Event.NEXT)
                    .perform(new OnNoPathFoundAction(persistenceManager));

            builder.transition().from(State.REVERTING_PATHS_SWAP).to(State.PATHS_SWAP_REVERTED)
                    .on(Event.NEXT)
                    .perform(new RevertPathsSwapAction<>(persistenceManager));

            builder.transitions().from(State.PATHS_SWAP_REVERTED)
                    .toAmong(State.REVERTING_NEW_RULES, State.REVERTING_NEW_RULES)
                    .onEach(Event.NEXT, Event.ERROR)
                    .perform(new RevertNewRulesAction<>(persistenceManager, resourcesManager));

            builder.internalTransition().within(State.REVERTING_NEW_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRemoveOrRevertResponseAction<>(speakerCommandRetriesLimit,
                            Event.RULES_REMOVED));
            builder.internalTransition().within(State.REVERTING_NEW_RULES).on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedRemoveOrRevertResponseAction<>(speakerCommandRetriesLimit,
                            Event.RULES_REMOVED));
            builder.transition().from(State.REVERTING_NEW_RULES).to(State.NEW_RULES_REVERTED)
                    .on(Event.RULES_REMOVED);
            builder.transition().from(State.REVERTING_NEW_RULES).to(State.NEW_RULES_REVERTED)
                    .on(Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction<>("reroute"));

            builder.transitions().from(State.NEW_RULES_REVERTED)
                    .toAmong(State.REVERTING_ALLOCATED_RESOURCES, State.REVERTING_ALLOCATED_RESOURCES)
                    .onEach(Event.NEXT, Event.ERROR);

            builder.transitions().from(State.REVERTING_ALLOCATED_RESOURCES)
                    .toAmong(State.RESOURCES_ALLOCATION_REVERTED, State.RESOURCES_ALLOCATION_REVERTED)
                    .onEach(Event.NEXT, Event.ERROR)
                    .perform(new RevertResourceAllocationAction<>(persistenceManager, resourcesManager));
            builder.transition().from(State.RESOURCES_ALLOCATION_REVERTED)
                    .to(State.REVERTING_FLOW_STATUS).on(Event.NEXT);
            builder.transition().from(State.RESOURCES_ALLOCATION_REVERTED).to(State.REVERTING_FLOW_STATUS)
                    .on(Event.ERROR)
                    .perform(new HandleNotRevertedResourceAllocationAction<>());

            builder.transitions().from(State.REVERTING_FLOW_STATUS)
                    .toAmong(State.FINISHED_WITH_ERROR, State.FINISHED_WITH_ERROR)
                    .onEach(Event.NEXT, Event.ERROR)
                    .perform(new RevertFlowStatusAction<>(persistenceManager, dashboardLogger));

            builder.defineFinalState(State.FINISHED)
                    .addEntryAction(new OnFinishedAction(dashboardLogger));
            builder.defineFinalState(State.FINISHED_WITH_ERROR)
                    .addEntryAction(new OnFinishedWithErrorAction(dashboardLogger));
        }

        public FlowRerouteFsm newInstance(CommandContext commandContext, String flowId) {
            return builder.newStateMachine(State.INITIALIZED, commandContext, carrier, flowId);
        }
    }

    public enum State {
        INITIALIZED,
        FLOW_VALIDATED,
        PRIMARY_RESOURCES_ALLOCATED,
        PROTECTED_RESOURCES_ALLOCATED,
        RESOURCE_ALLOCATION_COMPLETED,

        INSTALLING_NON_INGRESS_RULES,
        NON_INGRESS_RULES_INSTALLED,
        VALIDATING_NON_INGRESS_RULES,
        NON_INGRESS_RULES_VALIDATED,

        PATHS_SWAPPED,

        INSTALLING_INGRESS_RULES,
        INGRESS_RULES_INSTALLED,
        VALIDATING_INGRESS_RULES,
        INGRESS_RULES_VALIDATED,

        NEW_PATHS_INSTALLATION_COMPLETED,

        REMOVING_OLD_RULES,
        OLD_RULES_REMOVED,

        OLD_PATHS_REMOVAL_COMPLETED,

        DEALLOCATING_OLD_RESOURCES,
        OLD_RESOURCES_DEALLOCATED,

        UPDATING_FLOW_STATUS,
        FLOW_STATUS_UPDATED,

        FINISHED,

        REVERTING_PATHS_SWAP,
        PATHS_SWAP_REVERTED,
        REVERTING_NEW_RULES,
        NEW_RULES_REVERTED,

        NO_PATH_FOUND,
        REVERTING_ALLOCATED_RESOURCES,
        RESOURCES_ALLOCATION_REVERTED,
        REVERTING_FLOW_STATUS,

        FINISHED_WITH_ERROR
    }

    public enum Event {
        NEXT,

        NO_PATH_FOUND,
        REROUTE_IS_SKIPPED,

        RESPONSE_RECEIVED,
        ERROR_RECEIVED,

        INGRESS_IS_SKIPPED,

        RULES_INSTALLED,
        RULES_VALIDATED,
        MISSING_RULE_FOUND,

        RULES_REMOVED,

        TIMEOUT,
        ERROR
    }
}
