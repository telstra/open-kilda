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

package org.openkilda.wfm.topology.flowhs.service;

import org.openkilda.messaging.command.flow.FlowSyncRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncContext;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncFsm;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncFsm.Event;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationConfig;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathReference;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathResultCode;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingFsmRegister;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingService;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class FlowSyncService extends FlowProcessingService<FlowSyncFsm, Event, FlowSyncContext, FlowSyncCarrier,
        FlowProcessingFsmRegister<FlowSyncFsm>, FlowProcessingEventListener> {
    private final FlowSyncFsm.Factory handlerFactory;

    public FlowSyncService(
            @NonNull FlowSyncCarrier carrier, @NonNull PersistenceManager persistenceManager,
            @NonNull FlowResourcesManager flowResourcesManager,
            @NonNull FlowPathOperationConfig flowPathOperationConfig) {
        super(new FlowProcessingFsmRegister<>(), FlowSyncFsm.EXECUTOR, carrier, persistenceManager);
        handlerFactory = new FlowSyncFsm.Factory(
                carrier, persistenceManager, flowResourcesManager, flowPathOperationConfig);
    }

    /**
     * Handle flow sync request.
     */
    public void handleRequest(String requestKey, FlowSyncRequest request, CommandContext commandContext) {
        String flowId = request.getFlowId();
        log.debug("Handling flow reroute request with key {} and flow ID: {}", requestKey, flowId);

        // Because of field grouping specific flowId goes into specific bolt instance, so we can do such checks
        if (fsmRegister.hasRegisteredFsmWithFlowId(flowId)) {
            ErrorData payload = new ErrorData(
                    ErrorType.BUSY, "Overlapping flow sync requests",
                    String.format("Flow %s are doing \"sync\" already", flowId));
            carrier.sendNorthboundResponse(new ErrorMessage(
                    payload, commandContext.getCreateTime(), commandContext.getCorrelationId()));
            return;
        }

        FlowSyncFsm handler = handlerFactory.newInstance(request.getFlowId(), commandContext);
        fsmRegister.registerFsm(requestKey, handler);
        handler.getResultFuture()
                .thenAccept(dummy -> onComplete(requestKey));
    }

    /**
     * Handle global operation timeout.
     */
    public void handleTimeout(String requestKey) {
        fsmRegister.getFsmByKey(requestKey)
                .ifPresent(FlowSyncFsm::handleTimeout);
    }

    /**
     * Handle flow path sync(install) operation results.
     */
    public void handlePathSyncResponse(FlowPathReference reference, FlowPathResultCode result) {
        Optional<FlowSyncFsm> handler = fsmRegister.getFsmByFlowId(reference.getFlowId());
        if (handler.isPresent()) {
            handler.get().handlePathOperationResult(reference.getPathId(), result);
        } else {
            log.error("Got path sync result for {} but there is no relative sync handler", reference);
        }
    }

    private void onComplete(String requestKey) {
        fsmRegister.unregisterFsm(requestKey);
        carrier.cancelTimeoutCallback(requestKey);

        if (!isActive() && !fsmRegister.hasAnyRegisteredFsm()) {
            carrier.sendInactive();
        }
    }
}
