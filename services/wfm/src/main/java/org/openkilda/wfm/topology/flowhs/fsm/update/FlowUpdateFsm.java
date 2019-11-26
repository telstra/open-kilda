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

package org.openkilda.wfm.topology.flowhs.fsm.update;

import org.openkilda.messaging.Message;
import org.openkilda.model.FlowStatus;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowPathSwappingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.AbandonPendingCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.AllocatePrimaryResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.AllocateProtectedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.CompleteFlowPathInstallationAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.CompleteFlowPathRemovalAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.DeallocateResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.DumpIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.DumpNonIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.HandleNotCompletedCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.HandleNotDeallocatedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.HandleNotRemovedPathsAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.HandleNotRevertedResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.InstallIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.InstallNonIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.OnReceivedInstallResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.OnReceivedRemoveOrRevertResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.PostResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.RemoveOldRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.RevertFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.RevertFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.RevertNewRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.RevertPathsSwapAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.RevertResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.SwapFlowPathsAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.UpdateFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.UpdateFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.ValidateFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.ValidateIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.ValidateNonIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.service.FlowUpdateHubCarrier;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

@Getter
@Setter
@Slf4j
public final class FlowUpdateFsm extends FlowPathSwappingFsm<FlowUpdateFsm, State, Event, FlowUpdateContext> {

    private final FlowUpdateHubCarrier carrier;

    private RequestedFlow targetFlow;

    private FlowStatus originalFlowStatus;
    private String originalFlowGroup;
    private RequestedFlow originalFlow;

    private FlowStatus newFlowStatus;

    public FlowUpdateFsm(CommandContext commandContext, FlowUpdateHubCarrier carrier, String flowId) {
        super(commandContext, flowId);
        this.carrier = carrier;
    }

    @Override
    public void fireNext(FlowUpdateContext context) {
        fire(Event.NEXT, context);
    }

    @Override
    public void fireError(String errorReason) {
        fireError(Event.ERROR, errorReason);
    }

    private void fireError(Event errorEvent, String errorReason) {
        if (this.errorReason != null) {
            log.error("Subsequent error fired: " + errorReason);
        } else {
            this.errorReason = errorReason;
        }

        fire(errorEvent);
    }

    @Override
    public void fireNoPathFound(String errorReason) {
        fireError(Event.NO_PATH_FOUND, errorReason);
    }

    @Override
    public void sendResponse(Message message) {
        carrier.sendNorthboundResponse(message);
    }

    public static class Factory {
        private final StateMachineBuilder<FlowUpdateFsm, State, Event, FlowUpdateContext> builder;
        private final FlowUpdateHubCarrier carrier;

        public Factory(FlowUpdateHubCarrier carrier, PersistenceManager persistenceManager,
                       PathComputer pathComputer, FlowResourcesManager resourcesManager,
                       int transactionRetriesLimit, int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
                       int speakerCommandRetriesLimit) {
            this.carrier = carrier;


            builder = StateMachineBuilderFactory.create(FlowUpdateFsm.class, State.class, Event.class,
                    FlowUpdateContext.class, CommandContext.class, FlowUpdateHubCarrier.class, String.class);

            FlowOperationsDashboardLogger dashboardLogger = new FlowOperationsDashboardLogger(log);

            builder.transition().from(State.INITIALIZED).to(State.FLOW_VALIDATED).on(Event.NEXT)
                    .perform(new ValidateFlowAction(persistenceManager, dashboardLogger));
            builder.transition().from(State.INITIALIZED).to(State.FINISHED_WITH_ERROR).on(Event.TIMEOUT);

            builder.transition().from(State.FLOW_VALIDATED).to(State.FLOW_UPDATED).on(Event.NEXT)
                    .perform(new UpdateFlowAction(persistenceManager, transactionRetriesLimit));
            builder.transitions().from(State.FLOW_VALIDATED)
                    .toAmong(State.REVERTING_FLOW_STATUS, State.REVERTING_FLOW_STATUS)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.FLOW_UPDATED).to(State.PRIMARY_RESOURCES_ALLOCATED).on(Event.NEXT)
                    .perform(new AllocatePrimaryResourcesAction(persistenceManager, transactionRetriesLimit,
                            pathAllocationRetriesLimit, pathAllocationRetryDelay,
                            pathComputer, resourcesManager, dashboardLogger));
            builder.transitions().from(State.FLOW_UPDATED)
                    .toAmong(State.REVERTING_FLOW, State.REVERTING_FLOW)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.PRIMARY_RESOURCES_ALLOCATED).to(State.PROTECTED_RESOURCES_ALLOCATED)
                    .on(Event.NEXT)
                    .perform(new AllocateProtectedResourcesAction(persistenceManager, transactionRetriesLimit,
                            pathAllocationRetriesLimit, pathAllocationRetryDelay,
                            pathComputer, resourcesManager, dashboardLogger));
            builder.transitions().from(State.PRIMARY_RESOURCES_ALLOCATED)
                    .toAmong(State.REVERTING_ALLOCATED_RESOURCES, State.REVERTING_ALLOCATED_RESOURCES,
                            State.REVERTING_ALLOCATED_RESOURCES)
                    .onEach(Event.TIMEOUT, Event.ERROR, Event.NO_PATH_FOUND);

            builder.transition().from(State.PROTECTED_RESOURCES_ALLOCATED).to(State.RESOURCE_ALLOCATION_COMPLETED)
                    .on(Event.NEXT)
                    .perform(new PostResourceAllocationAction(persistenceManager));
            builder.transitions().from(State.PROTECTED_RESOURCES_ALLOCATED)
                    .toAmong(State.REVERTING_ALLOCATED_RESOURCES, State.REVERTING_ALLOCATED_RESOURCES,
                            State.REVERTING_ALLOCATED_RESOURCES)
                    .onEach(Event.TIMEOUT, Event.ERROR, Event.NO_PATH_FOUND);

            builder.transition().from(State.RESOURCE_ALLOCATION_COMPLETED).to(State.INSTALLING_NON_INGRESS_RULES)
                    .on(Event.NEXT)
                    .perform(new InstallNonIngressRulesAction(persistenceManager, resourcesManager));
            builder.transitions().from(State.RESOURCE_ALLOCATION_COMPLETED)
                    .toAmong(State.REVERTING_ALLOCATED_RESOURCES, State.REVERTING_ALLOCATED_RESOURCES)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.internalTransition().within(State.INSTALLING_NON_INGRESS_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(speakerCommandRetriesLimit));
            builder.internalTransition().within(State.INSTALLING_NON_INGRESS_RULES).on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(speakerCommandRetriesLimit));
            builder.transition().from(State.INSTALLING_NON_INGRESS_RULES).to(State.NON_INGRESS_RULES_INSTALLED)
                    .on(Event.RULES_INSTALLED);
            builder.transitions().from(State.INSTALLING_NON_INGRESS_RULES)
                    .toAmong(State.PATHS_SWAP_REVERTED, State.PATHS_SWAP_REVERTED)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new AbandonPendingCommandsAction());

            builder.transition().from(State.NON_INGRESS_RULES_INSTALLED).to(State.VALIDATING_NON_INGRESS_RULES)
                    .on(Event.NEXT)
                    .perform(new DumpNonIngressRulesAction());
            builder.transitions().from(State.NON_INGRESS_RULES_INSTALLED)
                    .toAmong(State.PATHS_SWAP_REVERTED, State.PATHS_SWAP_REVERTED)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.internalTransition().within(State.VALIDATING_NON_INGRESS_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new ValidateNonIngressRulesAction(speakerCommandRetriesLimit));
            builder.internalTransition().within(State.VALIDATING_NON_INGRESS_RULES).on(Event.ERROR_RECEIVED)
                    .perform(new ValidateNonIngressRulesAction(speakerCommandRetriesLimit));
            builder.transition().from(State.VALIDATING_NON_INGRESS_RULES).to(State.NON_INGRESS_RULES_VALIDATED)
                    .on(Event.RULES_VALIDATED);
            builder.transitions().from(State.VALIDATING_NON_INGRESS_RULES)
                    .toAmong(State.PATHS_SWAP_REVERTED, State.PATHS_SWAP_REVERTED, State.PATHS_SWAP_REVERTED)
                    .onEach(Event.TIMEOUT, Event.MISSING_RULE_FOUND, Event.ERROR)
                    .perform(new AbandonPendingCommandsAction());

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
                    .perform(new OnReceivedInstallResponseAction(speakerCommandRetriesLimit));
            builder.internalTransition().within(State.INSTALLING_INGRESS_RULES).on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(speakerCommandRetriesLimit));
            builder.transition().from(State.INSTALLING_INGRESS_RULES).to(State.INGRESS_RULES_INSTALLED)
                    .on(Event.RULES_INSTALLED);
            builder.transition().from(State.INSTALLING_INGRESS_RULES).to(State.INGRESS_RULES_VALIDATED)
                    .on(Event.INGRESS_IS_SKIPPED);
            builder.transitions().from(State.INSTALLING_INGRESS_RULES)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new AbandonPendingCommandsAction());

            builder.transition().from(State.INGRESS_RULES_INSTALLED).to(State.VALIDATING_INGRESS_RULES).on(Event.NEXT)
                    .perform(new DumpIngressRulesAction());
            builder.transitions().from(State.INGRESS_RULES_INSTALLED)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.internalTransition().within(State.VALIDATING_INGRESS_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new ValidateIngressRulesAction(persistenceManager, speakerCommandRetriesLimit));
            builder.internalTransition().within(State.VALIDATING_INGRESS_RULES).on(Event.ERROR_RECEIVED)
                    .perform(new ValidateIngressRulesAction(persistenceManager, speakerCommandRetriesLimit));
            builder.transition().from(State.VALIDATING_INGRESS_RULES).to(State.INGRESS_RULES_VALIDATED)
                    .on(Event.RULES_VALIDATED);
            builder.transitions().from(State.VALIDATING_INGRESS_RULES)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.MISSING_RULE_FOUND, Event.ERROR)
                    .perform(new AbandonPendingCommandsAction());

            builder.transition().from(State.INGRESS_RULES_VALIDATED).to(State.NEW_PATHS_INSTALLATION_COMPLETED)
                    .on(Event.NEXT)
                    .perform(new CompleteFlowPathInstallationAction(persistenceManager));
            builder.transitions().from(State.INGRESS_RULES_VALIDATED)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.NEW_PATHS_INSTALLATION_COMPLETED)
                    .to(State.REMOVING_OLD_RULES).on(Event.NEXT)
                    .perform(new RemoveOldRulesAction(persistenceManager, resourcesManager));
            builder.transitions().from(State.NEW_PATHS_INSTALLATION_COMPLETED)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.internalTransition().within(State.REMOVING_OLD_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRemoveOrRevertResponseAction(speakerCommandRetriesLimit));
            builder.internalTransition().within(State.REMOVING_OLD_RULES).on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedRemoveOrRevertResponseAction(speakerCommandRetriesLimit));
            builder.transition().from(State.REMOVING_OLD_RULES).to(State.OLD_RULES_REMOVED)
                    .on(Event.RULES_REMOVED);
            builder.transition().from(State.REMOVING_OLD_RULES).to(State.OLD_RULES_REMOVED)
                    .on(Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction());

            builder.transition().from(State.OLD_RULES_REMOVED).to(State.OLD_PATHS_REMOVAL_COMPLETED).on(Event.NEXT)
                    .perform(new CompleteFlowPathRemovalAction(persistenceManager, transactionRetriesLimit));

            builder.transition().from(State.OLD_PATHS_REMOVAL_COMPLETED).to(State.DEALLOCATING_OLD_RESOURCES)
                    .on(Event.NEXT);
            builder.transition().from(State.OLD_PATHS_REMOVAL_COMPLETED).to(State.DEALLOCATING_OLD_RESOURCES)
                    .on(Event.ERROR)
                    .perform(new HandleNotRemovedPathsAction());

            builder.transition().from(State.DEALLOCATING_OLD_RESOURCES)
                    .to(State.OLD_RESOURCES_DEALLOCATED).on(Event.NEXT)
                    .perform(new DeallocateResourcesAction(persistenceManager, resourcesManager));

            builder.transition().from(State.OLD_RESOURCES_DEALLOCATED).to(State.UPDATING_FLOW_STATUS).on(Event.NEXT);
            builder.transition().from(State.OLD_RESOURCES_DEALLOCATED).to(State.UPDATING_FLOW_STATUS)
                    .on(Event.ERROR)
                    .perform(new HandleNotDeallocatedResourcesAction());

            builder.transition().from(State.UPDATING_FLOW_STATUS).to(State.FLOW_STATUS_UPDATED).on(Event.NEXT)
                    .perform(new UpdateFlowStatusAction(persistenceManager, dashboardLogger));

            builder.transition().from(State.FLOW_STATUS_UPDATED).to(State.FINISHED).on(Event.NEXT);
            builder.transition().from(State.FLOW_STATUS_UPDATED).to(State.FINISHED_WITH_ERROR).on(Event.ERROR);

            builder.transition().from(State.REVERTING_PATHS_SWAP).to(State.PATHS_SWAP_REVERTED)
                    .on(Event.NEXT)
                    .perform(new RevertPathsSwapAction(persistenceManager));

            builder.transitions().from(State.PATHS_SWAP_REVERTED)
                    .toAmong(State.REVERTING_NEW_RULES, State.REVERTING_NEW_RULES)
                    .onEach(Event.NEXT, Event.ERROR)
                    .perform(new RevertNewRulesAction(persistenceManager, resourcesManager));

            builder.internalTransition().within(State.REVERTING_NEW_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRemoveOrRevertResponseAction(speakerCommandRetriesLimit));
            builder.internalTransition().within(State.REVERTING_NEW_RULES).on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedRemoveOrRevertResponseAction(speakerCommandRetriesLimit));
            builder.transition().from(State.REVERTING_NEW_RULES).to(State.NEW_RULES_REVERTED)
                    .on(Event.RULES_REMOVED);
            builder.transition().from(State.REVERTING_NEW_RULES).to(State.NEW_RULES_REVERTED)
                    .on(Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction());

            builder.transitions().from(State.NEW_RULES_REVERTED)
                    .toAmong(State.REVERTING_ALLOCATED_RESOURCES, State.REVERTING_ALLOCATED_RESOURCES)
                    .onEach(Event.NEXT, Event.ERROR);

            builder.transitions().from(State.REVERTING_ALLOCATED_RESOURCES)
                    .toAmong(State.RESOURCES_ALLOCATION_REVERTED, State.RESOURCES_ALLOCATION_REVERTED)
                    .onEach(Event.NEXT, Event.ERROR)
                    .perform(new RevertResourceAllocationAction(persistenceManager, resourcesManager));
            builder.transition().from(State.RESOURCES_ALLOCATION_REVERTED).to(State.REVERTING_FLOW).on(Event.NEXT);
            builder.transition().from(State.RESOURCES_ALLOCATION_REVERTED).to(State.REVERTING_FLOW)
                    .on(Event.ERROR)
                    .perform(new HandleNotRevertedResourceAllocationAction());

            builder.transitions().from(State.REVERTING_FLOW)
                    .toAmong(State.REVERTING_FLOW_STATUS, State.REVERTING_FLOW_STATUS)
                    .onEach(Event.NEXT, Event.ERROR)
                    .perform(new RevertFlowAction(persistenceManager));

            builder.transitions().from(State.REVERTING_FLOW_STATUS)
                    .toAmong(State.FINISHED_WITH_ERROR, State.FINISHED_WITH_ERROR)
                    .onEach(Event.NEXT, Event.ERROR)
                    .perform(new RevertFlowStatusAction(persistenceManager));

            builder.defineFinalState(State.FINISHED)
                    .addEntryAction(new OnFinishedAction(dashboardLogger));
            builder.defineFinalState(State.FINISHED_WITH_ERROR)
                    .addEntryAction(new OnFinishedWithErrorAction(dashboardLogger));
        }

        public FlowUpdateFsm newInstance(CommandContext commandContext, String flowId) {
            return builder.newStateMachine(State.INITIALIZED, commandContext, carrier, flowId);
        }
    }

    public enum State {
        INITIALIZED,
        FLOW_VALIDATED,
        FLOW_UPDATED,
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

        REVERTING_ALLOCATED_RESOURCES,
        RESOURCES_ALLOCATION_REVERTED,
        REVERTING_FLOW_STATUS,
        REVERTING_FLOW,

        FINISHED_WITH_ERROR
    }

    public enum Event {
        NEXT,

        NO_PATH_FOUND,

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
