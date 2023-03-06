/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.sync;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncFsm.State;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationConfig;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowSyncCarrier;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

@Slf4j
public class FlowSyncFsm extends SyncFsmBase<FlowSyncFsm, State, Event> {
    public static final FsmExecutor<FlowSyncFsm, State, Event, FlowSyncContext> EXECUTOR = new FsmExecutor<>(
            Event.NEXT);

    @Getter
    private final String flowId;

    public FlowSyncFsm(
            @NonNull CommandContext commandContext, @NonNull FlowSyncCarrier carrier, @NonNull String flowId) {
        super(commandContext, carrier, Event.NEXT, Event.ERROR);
        this.flowId = flowId;
    }

    // -- FSM actions --

    public void enterCancelAction(State from, State to, Event event, FlowSyncContext context) {
        cancelPendingPathOperations(context);
    }

    public void checkPendingOperationsAction(State from, State to, Event event, FlowSyncContext context) {
        continueIfNoPendingPathOperations(context);
    }

    public void reportGlobalTimeoutAction(State from, State to, Event event, FlowSyncContext context) {
        saveGlobalTimeoutToHistory();
    }

    // -- private/service methods --

    @Override
    protected void injectPathOperationResultEvent(FlowSyncContext context) {
        injectEvent(Event.PATH_OPERATION_RESPONSE, context);
    }

    @Override
    void handlePathSyncFailure(FlowSyncContext context) {
        fire(Event.SYNC_FAIL, context);
    }

    @Override
    public void injectTimeoutEvent(FlowSyncContext context) {
        injectEvent(Event.TIMEOUT, context);
    }

    @Override
    protected void allPathOperationsIsOverProceedToTheNextStep(FlowSyncContext context) {
        fire(Event.GUARD_PASSED, context);
    }

    @Override
    protected void injectEvent(Event event, FlowSyncContext context) {
        EXECUTOR.fire(this, event, context);
    }

    @Override
    protected String getCrudActionName() {
        return "sync";
    }

    @Override
    protected State getFinishedState() {
        return State.FINISHED;
    }

    // -- FSM definition --

    public static class Factory {
        private final StateMachineBuilder<FlowSyncFsm, State, Event, FlowSyncContext> builder;
        private final FlowGenericCarrier carrier;

        public Factory(
                @NonNull FlowSyncCarrier carrier, @NonNull PersistenceManager persistenceManager,
                @NonNull FlowResourcesManager resourcesManager,
                @NonNull FlowPathOperationConfig flowPathOperationConfig) {
            this.carrier = carrier;

            builder = StateMachineBuilderFactory.create(
                    FlowSyncFsm.class, State.class, Event.class, FlowSyncContext.class,
                    CommandContext.class, FlowSyncCarrier.class, String.class);

            final FlowOperationsDashboardLogger dashboardLogger = new FlowOperationsDashboardLogger(log);

            final PathOperationResponseAction<FlowSyncFsm, State, Event> pathOperationResponseAction =
                    new PathOperationResponseAction<>(persistenceManager);
            final String reportGlobalTimeoutActionMethod = "reportGlobalTimeoutAction";
            final String checkPendingOperationsActionMethod = "checkPendingOperationsAction";

            // SETUP
            builder.onEntry(State.SETUP)
                    .perform(new FlowSyncSetupAction(persistenceManager, dashboardLogger));
            builder.transition().from(State.SETUP).to(State.SYNC).on(Event.NEXT);
            builder.transition().from(State.SETUP).to(State.COMMIT_ERROR).on(Event.ERROR);

            // SYNC
            builder.onEntry(State.SYNC)
                    .perform(new CreateSyncHandlersAction<>(
                            persistenceManager, resourcesManager, flowPathOperationConfig));
            builder.transition().from(State.SYNC).to(State.SYNC_FAIL).on(Event.SYNC_FAIL);
            builder.transition().from(State.SYNC).to(State.COMMIT_SUCCESS).on(Event.GUARD_PASSED);
            builder.transition().from(State.SYNC).to(State.CANCEL).on(Event.ERROR);
            builder.transition().from(State.SYNC).to(State.CANCEL).on(Event.TIMEOUT)
                    .callMethod(reportGlobalTimeoutActionMethod);
            builder.internalTransition().within(State.SYNC).on(Event.PATH_OPERATION_RESPONSE)
                    .perform(pathOperationResponseAction);

            // CANCEL
            builder.onEntry(State.CANCEL)
                    .callMethod("enterCancelAction");
            builder.transition().from(State.CANCEL).to(State.COMMIT_ERROR).on(Event.GUARD_PASSED);
            builder.internalTransition().within(State.CANCEL).on(Event.PATH_OPERATION_RESPONSE)
                    .perform(pathOperationResponseAction);
            builder.internalTransition().within(State.CANCEL).on(Event.ERROR)
                    .callMethod(checkPendingOperationsActionMethod);
            builder.internalTransition().within(State.CANCEL).on(Event.TIMEOUT)
                    .callMethod(reportGlobalTimeoutActionMethod);

            // SYNC_FAIL
            builder.onEntry(State.SYNC_FAIL)
                    .callMethod(checkPendingOperationsActionMethod);
            builder.transition().from(State.SYNC_FAIL).to(State.COMMIT_ERROR).on(Event.GUARD_PASSED);
            builder.transition().from(State.SYNC_FAIL).to(State.CANCEL).on(Event.ERROR);
            builder.transition().from(State.SYNC_FAIL).to(State.CANCEL).on(Event.TIMEOUT)
                    .callMethod(reportGlobalTimeoutActionMethod);
            builder.internalTransition().within(State.SYNC_FAIL).on(Event.PATH_OPERATION_RESPONSE)
                    .perform(pathOperationResponseAction);

            // COMMIT_SUCCESS
            builder.onEntry(State.COMMIT_SUCCESS)
                    .perform(new FlowSyncSuccessCompleteAction(persistenceManager, dashboardLogger));
            builder.transition().from(State.COMMIT_SUCCESS).to(State.FINISHED).on(Event.NEXT);
            builder.transition().from(State.COMMIT_SUCCESS).to(State.COMMIT_ERROR).on(Event.ERROR);

            // COMMIT_ERROR
            builder.onEntry(State.COMMIT_ERROR)
                    .perform(new FlowFailedCompleteAction(persistenceManager, dashboardLogger));
            builder.transition().from(State.COMMIT_ERROR).to(State.FINISHED_WITH_ERROR).on(Event.NEXT);

            // final
            builder.defineFinalState(State.FINISHED);
            builder.defineFinalState(State.FINISHED_WITH_ERROR);
        }

        public FlowSyncFsm newInstance(@NonNull String flowId, @NonNull CommandContext commandContext) {
            FlowSyncFsm instance = builder.newStateMachine(State.SETUP, commandContext, carrier, flowId);
            instance.start(FlowSyncContext.builder().build());
            return instance;
        }
    }

    public enum State {
        SETUP,
        SYNC, SYNC_FAIL, CANCEL,
        COMMIT_SUCCESS, COMMIT_ERROR,
        FINISHED, FINISHED_WITH_ERROR
    }

    public enum Event {
        NEXT, GUARD_PASSED, ERROR, TIMEOUT, SYNC_FAIL,
        PATH_OPERATION_RESPONSE
    }
}
