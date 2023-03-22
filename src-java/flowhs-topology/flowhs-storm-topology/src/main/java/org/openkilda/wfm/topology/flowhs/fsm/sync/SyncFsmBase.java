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
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.FsmUtil;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingWithHistorySupportFsm;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationDescriptor;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathReference;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathResultCode;
import org.openkilda.wfm.topology.flowhs.service.FlowProcessingEventListener;
import org.openkilda.wfm.topology.flowhs.service.FlowSyncCarrier;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public abstract class SyncFsmBase<F extends AbstractStateMachine<F, S, E, FlowSyncContext>, S, E>
        extends FlowProcessingWithHistorySupportFsm<F, S, E, FlowSyncContext, FlowSyncCarrier,
        FlowProcessingEventListener> {

    @Getter
    private final Set<String> targets = new HashSet<>();

    @Getter
    private final CompletableFuture<Void> resultFuture = new CompletableFuture<>();

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

    @Getter
    private final List<FlowPathOperationDescriptor> pathOperationSuccess = new ArrayList<>();
    @Getter
    private final List<FlowPathOperationDescriptor> pathOperationFail = new ArrayList<>();

    private final Map<PathId, FlowPathOperationDescriptor> pendingPathOperations = new HashMap<>();

    public SyncFsmBase(
            @NonNull CommandContext commandContext, @NonNull FlowSyncCarrier carrier, E nextEvent, E errorEvent) {
        super(nextEvent, errorEvent, commandContext, carrier);

        FsmUtil.addExecutionTimeMeter(this, this::isSuccessfullyCompleted);
        addTerminateListener(dummy -> onComplete());
    }

    /**
     * Handle path operation result.
     */
    public void handlePathOperationResult(PathId pathId, FlowPathResultCode resultCode) {
        injectPathOperationResultEvent(FlowSyncContext.builder()
                .pathId(pathId)
                .pathResultCode(resultCode)
                .build());
    }

    public void handleTimeout() {
        injectTimeoutEvent(FlowSyncContext.builder().build());
    }

    void continueIfNoPendingPathOperations(FlowSyncContext context) {
        if (! isPendingPathOperationsExists()) {
            allPathOperationsIsOverProceedToTheNextStep(context);
        }
    }

    protected abstract void injectPathOperationResultEvent(FlowSyncContext context);

    protected abstract void injectTimeoutEvent(FlowSyncContext context);

    abstract void handlePathSyncFailure(FlowSyncContext context);

    protected void cancelPendingPathOperations(FlowSyncContext context) {
        // must create independent list, because pendingPathOperations can be modified inside loop body
        Collection<FlowPathOperationDescriptor> pendingOperations = new ArrayList<>(pendingPathOperations.values());
        for (FlowPathOperationDescriptor entry : pendingOperations) {
            FlowPathReference reference = entry.getRequest().getReference();
            PathId pathId = reference.getPathId();
            String flowId = reference.getFlowId();

            log.info("Cancel path sync(install) operation for {} (flowId={})", pathId, flowId);
            try {
                getCarrier().cancelFlowPathOperation(pathId);
            } catch (UnknownKeyException e) {
                log.error("Path {} sync operation is missing (flowId={})", pathId, flowId);
                addFailedPathOperation(pathId);
            }
        }

        continueIfNoPendingPathOperations(context);
    }

    public boolean isPendingPathOperationsExists() {
        return !pendingPathOperations.isEmpty();
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

    protected void onComplete() {
        resultFuture.complete(null);
    }

    protected abstract void injectEvent(E event, FlowSyncContext context);

    private Optional<FlowPathOperationDescriptor> commitFlowPathOperation(
            List<FlowPathOperationDescriptor> target, PathId pathId) {
        FlowPathOperationDescriptor descriptor = pendingPathOperations.remove(pathId);
        if (descriptor == null) {
            return Optional.empty();
        }

        target.add(descriptor);
        return Optional.of(descriptor);
    }

    protected Boolean isSuccessfullyCompleted() {
        if (isTerminated()) {
            return getCurrentState() == getFinishedState();
        }

        return null;
    }

    protected abstract void allPathOperationsIsOverProceedToTheNextStep(FlowSyncContext context);

    protected abstract S getFinishedState();
}
