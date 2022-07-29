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

import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.exceptions.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.FsmUtil;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingWithHistorySupportFsm;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.sync.actions.CreateSyncHandlersAction;
import org.openkilda.wfm.topology.flowhs.fsm.sync.actions.FailedCompleteAction;
import org.openkilda.wfm.topology.flowhs.fsm.sync.actions.FlowSyncSetupAction;
import org.openkilda.wfm.topology.flowhs.fsm.sync.actions.PathOperationResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.sync.actions.SuccessCompleteAction;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationConfig;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationDescriptor;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathResultCode;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowProcessingEventListener;
import org.openkilda.wfm.topology.flowhs.service.FlowSyncCarrier;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class FlowSyncFsm
        extends FlowProcessingWithHistorySupportFsm<
                FlowSyncFsm, State, Event, FlowSyncContext, FlowSyncCarrier, FlowProcessingEventListener> {
    public static final FsmExecutor<FlowSyncFsm, State, Event, FlowSyncContext> EXECUTOR = new FsmExecutor<>(
            Event.NEXT);

    @Getter
    private final CompletableFuture<Void> resultFuture = new CompletableFuture<>();

    private final Map<PathId, FlowPathOperationDescriptor> pendingPathOperations = new HashMap<>();

    @Getter
    private final List<FlowPathOperationDescriptor> pathOperationSuccess = new ArrayList<>();
    @Getter
    private final List<FlowPathOperationDescriptor> pathOperationFail = new ArrayList<>();

    @Getter
    private final String flowId;

    /**
     * Indicator of dangerous sync operation.
     *
     * <p>If sync operation is dangerous customer traffic can be interrupted during installation of flow segments.
     * Also, if path operation fails to install all path segments, target flow most probably will become corrupted -
     * can't carry customer traffic. And one more side effect of such dangerous operation - in some cases extra rules on
     * the switches used by target flow can appear after dangerous sync.</p>
     */
    @Getter @Setter
    private boolean dangerousSync = false;

    public FlowSyncFsm(
            @NonNull CommandContext commandContext, @NonNull FlowSyncCarrier carrier, @NonNull String flowId) {
        super(Event.NEXT, Event.ERROR, commandContext, carrier);
        this.flowId = flowId;
    }

    // -- public API --

    public void handlePathOperationResult(PathId pathId, FlowPathResultCode resultCode) {
        handleEvent(Event.PATH_OPERATION_RESPONSE, FlowSyncContext.builder()
                .pathId(pathId)
                .pathResultCode(resultCode)
                .build());
    }

    public void handleTimeout() {
        handleEvent(Event.TIMEOUT, FlowSyncContext.builder().build());
    }

    public boolean isPendingPathOperationsExists() {
        return ! pendingPathOperations.isEmpty();
    }

    public void addPendingPathOperation(FlowPathOperationDescriptor descriptor) {
        pendingPathOperations.put(descriptor.getPathId(), descriptor);
    }

    public Optional<FlowPathOperationDescriptor> addSuccessPathOperation(PathId pathId) {
        return commitFlowPathOperation(pathOperationSuccess, pathId);
    }

    public Optional<FlowPathOperationDescriptor> addFailedPathOperation(PathId pathId) {
        return commitFlowPathOperation(pathOperationFail, pathId);
    }

    // -- FSM actions --

    public void enterCancelAction(State from, State to, Event event, FlowSyncContext context) {
        for (PathId entry : pendingPathOperations.keySet()) {
            log.info("Cancel path sync(install) operation for {} (flowId={})", entry, flowId);
            try {
                getCarrier().cancelFlowPathOperation(entry);
            } catch (UnknownKeyException e) {
                log.error("Path {} sync operation is missing (flowId={})", entry, flowId);
                addFailedPathOperation(entry);
            }
        }

        checkPendingOperations(context);
    }

    public void checkPendingOperationsAction(State from, State to, Event event, FlowSyncContext context) {
        checkPendingOperations(context);
    }

    public void reportGlobalTimeoutAction(State from, State to, Event event, FlowSyncContext context) {
        saveGlobalTimeoutToHistory();
    }

    // -- private/service methods --

    private void checkPendingOperations(FlowSyncContext context) {
        if (! isPendingPathOperationsExists()) {
            handleEvent(Event.GUARD_PASSED, context);
        }
    }

    protected void handleEvent(Event event, FlowSyncContext context) {
        EXECUTOR.fire(this, event, context);
    }

    private void onComplete() {
        resultFuture.complete(null);
    }

    private Optional<FlowPathOperationDescriptor> commitFlowPathOperation(
            List<FlowPathOperationDescriptor> target, PathId pathId) {
        FlowPathOperationDescriptor descriptor = pendingPathOperations.remove(pathId);
        if (descriptor == null) {
            return Optional.empty();
        }

        target.add(descriptor);
        return Optional.of(descriptor);
    }

    @Override
    protected String getCrudActionName() {
        return "sync";
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

            final PathOperationResponseAction pathOperationResponseAction = new PathOperationResponseAction(
                    persistenceManager);
            final String reportGlobalTimeoutActionMethod = "reportGlobalTimeoutAction";
            final String checkPendingOperationsActionMethod = "checkPendingOperationsAction";

            // SETUP
            builder.onEntry(State.SETUP)
                    .perform(new FlowSyncSetupAction(persistenceManager, dashboardLogger));
            builder.transition().from(State.SETUP).to(State.SYNC).on(Event.NEXT);
            builder.transition().from(State.SETUP).to(State.COMMIT_ERROR).on(Event.ERROR);

            // SYNC
            builder.onEntry(State.SYNC)
                    .perform(new CreateSyncHandlersAction(
                            carrier, persistenceManager, resourcesManager, flowPathOperationConfig));
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
                    .perform(new SuccessCompleteAction(carrier, persistenceManager, dashboardLogger));
            builder.transition().from(State.COMMIT_SUCCESS).to(State.FINISHED).on(Event.NEXT);
            builder.transition().from(State.COMMIT_SUCCESS).to(State.COMMIT_ERROR).on(Event.ERROR);

            // COMMIT_ERROR
            builder.onEntry(State.COMMIT_ERROR)
                    .perform(new FailedCompleteAction(carrier, persistenceManager, dashboardLogger));
            builder.transition().from(State.COMMIT_ERROR).to(State.FINISHED_WITH_ERROR).on(Event.NEXT);

            // final
            builder.defineFinalState(State.FINISHED);
            builder.defineFinalState(State.FINISHED_WITH_ERROR);
        }

        public FlowSyncFsm newInstance(@NonNull String flowId, @NonNull CommandContext commandContext) {
            FlowSyncFsm instance = builder.newStateMachine(State.SETUP, commandContext, carrier, flowId);

            addExecutionTimeMeter(instance);
            addCompleteNotification(instance);

            instance.start(FlowSyncContext.builder().build());

            return instance;
        }
    }

    public enum State {
        SETUP,
        SYNC, SYNC_FAIL, CANCEL,
        COMMIT_SUCCESS, COMMIT_ERROR,
        FINISHED, FINISHED_WITH_ERROR,
    }

    public enum Event {
        NEXT, GUARD_PASSED, ERROR, TIMEOUT, SYNC_FAIL,
        PATH_OPERATION_RESPONSE
    }

    private static void addExecutionTimeMeter(FlowSyncFsm fsm) {
        FsmUtil.addExecutionTimeMeter(fsm, () -> successResultAdapter(fsm));
    }

    private static void addCompleteNotification(FlowSyncFsm fsm) {
        fsm.addTerminateListener(dummy -> fsm.onComplete());
    }

    private static Boolean successResultAdapter(FlowSyncFsm fsm) {
        if (fsm.isTerminated()) {
            return fsm.getCurrentState() == State.FINISHED;
        }

        return null;
    }
}
