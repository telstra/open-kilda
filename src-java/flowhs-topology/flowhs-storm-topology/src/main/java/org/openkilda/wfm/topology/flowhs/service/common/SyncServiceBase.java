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

package org.openkilda.wfm.topology.flowhs.service.common;

import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncContext;
import org.openkilda.wfm.topology.flowhs.fsm.sync.SyncFsmBase;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathReference;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathResultCode;
import org.openkilda.wfm.topology.flowhs.service.FlowProcessingEventListener;
import org.openkilda.wfm.topology.flowhs.service.FlowSyncCarrier;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public abstract class SyncServiceBase<F extends SyncFsmBase<F, ?, E>, E>
        extends FlowProcessingService<F, E, FlowSyncContext,
        FlowSyncCarrier, FlowProcessingFsmRegister<F>, FlowProcessingEventListener> {
    protected Logger log;

    public SyncServiceBase(
            @NonNull FsmExecutor<F, ?, E, FlowSyncContext> fsmExecutor,
            @NonNull FlowSyncCarrier carrier,
            @NonNull PersistenceManager persistenceManager) {
        super(new FlowProcessingFsmRegister<>(), fsmExecutor, carrier, persistenceManager);
        log = LoggerFactory.getLogger(getClass());
    }

    /**
     * Handle sync request.
     */
    protected void handleRequest(String requestKey, String targetId, CommandContext commandContext) {
        // Because of field grouping specific flowId goes into specific bolt instance, so we can do such checks
        if (fsmRegister.hasRegisteredFsmWithFlowId(targetId)) {
            ErrorData payload = new ErrorData(
                    ErrorType.BUSY, "Overlapping flow sync requests",
                    String.format("Flow %s are doing \"sync\" already", targetId));
            carrier.sendNorthboundResponse(new ErrorMessage(
                    payload, commandContext.getCreateTime(), commandContext.getCorrelationId()));
            return;
        }

        F handler = newHandler(targetId, commandContext);
        fsmRegister.registerFsm(requestKey, handler);
        handler.getResultFuture()
                .thenAccept(dummy -> onComplete(handler, requestKey));
    }

    /**
     * Handle flow path sync(install) operation results.
     */
    public void handlePathSyncResponse(FlowPathReference reference, FlowPathResultCode result) {
        Optional<F> handler = lookupHandler(reference);
        if (handler.isPresent()) {
            handler.get().handlePathOperationResult(reference.getPathId(), result);
        } else {
            log.error("Got path sync result for {} but there is no relative sync handler", reference);
        }
    }

    /**
     * Handle global operation timeout.
     */
    public void handleTimeout(String requestKey) {
        fsmRegister.getFsmByKey(requestKey)
                .ifPresent(handler -> handler.handleTimeout());
    }

    protected void onComplete(F handler, String requestKey) {
        fsmRegister.unregisterFsm(requestKey);
        carrier.cancelTimeoutCallback(requestKey);

        if (!isActive() && !fsmRegister.hasAnyRegisteredFsm()) {
            carrier.sendInactive();
        }
    }

    protected abstract Optional<F> lookupHandler(FlowPathReference reference);

    protected abstract F newHandler(String flowId, CommandContext commandContext);
}
