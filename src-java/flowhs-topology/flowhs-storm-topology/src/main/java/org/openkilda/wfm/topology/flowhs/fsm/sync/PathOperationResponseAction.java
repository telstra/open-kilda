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

import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationDescriptor;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathReference;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathResultCode;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PathOperationResponseAction<F extends SyncFsmBase<F, S, E>, S, E>
        extends FlowProcessingWithHistorySupportAction<F, S, E, FlowSyncContext> {
    public PathOperationResponseAction(@NonNull PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(S from, S to, E event, FlowSyncContext context, F stateMachine) {
        FlowPathResultCode resultCode = context.getPathResultCode();
        PathId pathId = context.getPathId();
        if (resultCode == FlowPathResultCode.SUCCESS) {
            handleSuccess(
                    stateMachine.addSuccessPathOperation(pathId)
                            .orElseThrow(() -> newMissingPendingOperationError(pathId, resultCode)));
        } else {
            FlowPathOperationDescriptor descriptor = stateMachine.addFailedPathOperation(pathId)
                    .orElseThrow(() -> newMissingPendingOperationError(pathId, resultCode));
            handleFailure(descriptor);
            stateMachine.handlePathSyncFailure(context);
        }

        stateMachine.continueIfNoPendingPathOperations(context);
    }

    private void handleSuccess(FlowPathOperationDescriptor descriptor) {
        FlowPathReference reference = descriptor.getRequest().getReference();
        log.info("Flow path {} have been synced (flowId={})", reference.getPathId(), reference.getFlowId());
        flowPathRepository.updateStatus(reference.getPathId(), FlowPathStatus.ACTIVE);
    }

    private void handleFailure(FlowPathOperationDescriptor descriptor) {
        FlowPathReference reference = descriptor.getRequest().getReference();
        log.error(
                "Failed to sync flow path {}, restore it's status to initial value {} (flowId={})",
                reference.getPathId(), descriptor.getInitialStatus(), reference.getFlowId());
        flowPathRepository.updateStatus(reference.getPathId(), descriptor.getInitialStatus());
    }

    private IllegalStateException newMissingPendingOperationError(PathId pathId, FlowPathResultCode resultCode) {
        return new IllegalStateException(String.format(
                "Got unexpected path operation(sync) result pathId=%s, resultCode=%s", pathId, resultCode));
    }
}
