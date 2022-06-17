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

package org.openkilda.wfm.topology.flowhs.fsm.sync.actions;

import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncContext;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncFsm;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncFsm.State;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationDescriptor;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathResultCode;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PathOperationResponseAction
        extends FlowProcessingWithHistorySupportAction<FlowSyncFsm, State, Event, FlowSyncContext> {
    public PathOperationResponseAction(@NonNull PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowSyncContext context, FlowSyncFsm stateMachine) {
        FlowPathResultCode resultCode = context.getPathResultCode();
        PathId pathId = context.getPathId();
        if (resultCode == FlowPathResultCode.SUCCESS) {
            handleSuccess(
                    stateMachine, stateMachine.addSuccessPathOperation(pathId)
                            .orElseThrow(() -> newMissingPendingOperationError(pathId, resultCode)));
        } else {
            FlowPathOperationDescriptor descriptor = stateMachine.addFailedPathOperation(pathId)
                    .orElseThrow(() -> newMissingPendingOperationError(pathId, resultCode));
            handleFailure(stateMachine, descriptor);
            stateMachine.fire(Event.SYNC_FAIL, context);
        }

        if (! stateMachine.isPendingPathOperationsExists()) {
            stateMachine.fire(Event.GUARD_PASSED, context);
        }
    }

    private void handleSuccess(FlowSyncFsm stateMachine, FlowPathOperationDescriptor descriptor) {
        log.info("Flow path {} have been synced (flowId={})", descriptor.getPathId(), stateMachine.getFlowId());
        flowPathRepository.updateStatus(descriptor.getPathId(), FlowPathStatus.ACTIVE);
    }

    private void handleFailure(FlowSyncFsm stateMachine, FlowPathOperationDescriptor descriptor) {
        log.error(
                "Failed to sync flow path {}, restore it's status to initial value {} (flowId={})",
                descriptor.getPathId(), descriptor.getInitialStatus(), stateMachine.getFlowId());
        flowPathRepository.updateStatus(descriptor.getPathId(), descriptor.getInitialStatus());
    }

    private IllegalStateException newMissingPendingOperationError(PathId pathId, FlowPathResultCode resultCode) {
        return new IllegalStateException(String.format(
                "Got unexpected path operation(sync) result pathId=%s, resultCode=%s", pathId, resultCode));
    }
}
