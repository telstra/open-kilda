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

package org.openkilda.wfm.topology.switchmanager.service;

import org.openkilda.messaging.MessageCookie;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.grpc.DeleteLogicalPortResponse;
import org.openkilda.messaging.swmanager.request.DeleteLagPortRequest;
import org.openkilda.wfm.error.MessageDispatchException;
import org.openkilda.wfm.error.UnexpectedInputException;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.switchmanager.error.OperationTimeoutException;
import org.openkilda.wfm.topology.switchmanager.error.SpeakerFailureException;
import org.openkilda.wfm.topology.switchmanager.fsm.DeleteLagPortFsm;
import org.openkilda.wfm.topology.switchmanager.fsm.DeleteLagPortFsm.DeleteLagContext;
import org.openkilda.wfm.topology.switchmanager.fsm.DeleteLagPortFsm.DeleteLagEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.DeleteLagPortFsm.DeleteLagState;
import org.openkilda.wfm.topology.switchmanager.service.configs.LagPortOperationConfig;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DeleteLagPortService implements SwitchManagerHubService {
    @Getter
    private final SwitchManagerCarrier carrier;

    private final LagPortOperationService lagOperationService;

    private final Map<String, DeleteLagPortFsm> handlers = new HashMap<>();
    private final StateMachineBuilder<DeleteLagPortFsm, DeleteLagState, DeleteLagEvent, DeleteLagContext> builder;
    private final FsmExecutor<DeleteLagPortFsm, DeleteLagState, DeleteLagEvent, DeleteLagContext> fsmExecutor;

    private boolean active = true;

    public DeleteLagPortService(SwitchManagerCarrier carrier, LagPortOperationConfig config) {
        this.lagOperationService = new LagPortOperationService(config);
        this.builder = DeleteLagPortFsm.builder();
        this.fsmExecutor = new FsmExecutor<>(DeleteLagEvent.NEXT);
        this.carrier = carrier;
    }

    /**
     * Handle delete LAG port request.
     */
    public void handleDeleteLagRequest(String key, DeleteLagPortRequest request) {
        DeleteLagPortFsm fsm = builder.newStateMachine(
                DeleteLagState.START, carrier, key, request, lagOperationService);
        handlers.put(key, fsm);

        fsm.start();
        fireFsmEvent(fsm, DeleteLagEvent.NEXT, DeleteLagContext.builder().build());
    }

    @Override
    public void timeout(@NonNull MessageCookie cookie) throws MessageDispatchException {
        OperationTimeoutException error = new OperationTimeoutException("LAG create operation timeout");
        fireFsmEvent(cookie, DeleteLagEvent.ERROR, DeleteLagContext.builder().error(error).build());
    }

    @Override
    public void dispatchWorkerMessage(InfoData payload, MessageCookie cookie)
            throws UnexpectedInputException, MessageDispatchException {
        if (payload instanceof DeleteLogicalPortResponse) {
            handleDeleteResponse((DeleteLogicalPortResponse) payload, cookie);
        } else {
            throw new UnexpectedInputException(payload);
        }
    }

    @Override
    public void dispatchErrorMessage(ErrorData payload, MessageCookie cookie) throws MessageDispatchException {
        DeleteLagContext context = DeleteLagContext.builder()
                .error(new SpeakerFailureException(payload))
                .build();
        fireFsmEvent(cookie, DeleteLagEvent.ERROR, context);
    }

    private void handleDeleteResponse(DeleteLogicalPortResponse payload, MessageCookie cookie)
            throws MessageDispatchException {
        fireFsmEvent(cookie, DeleteLagEvent.LAG_REMOVED,
                DeleteLagContext.builder().deletedLogicalPort(payload.getLogicalPortNumber()).build());
    }

    @Override
    public void activate() {
        active = true;
    }

    @Override
    public boolean deactivate() {
        active = false;
        return isAllOperationsCompleted();
    }

    @Override
    public boolean isAllOperationsCompleted() {
        return handlers.isEmpty();
    }

    private void fireFsmEvent(MessageCookie cookie, DeleteLagEvent event, DeleteLagContext context)
            throws MessageDispatchException {
        DeleteLagPortFsm handler = null;
        if (cookie != null) {
            handler = handlers.get(cookie.getValue());
        }
        if (handler == null) {
            throw new MessageDispatchException(cookie);
        }
        fireFsmEvent(handler, event, context);
    }

    private void fireFsmEvent(DeleteLagPortFsm fsm, DeleteLagEvent event, DeleteLagContext context) {
        fsmExecutor.fire(fsm, event, context);
        removeIfCompleted(fsm);
    }

    private void removeIfCompleted(DeleteLagPortFsm fsm) {
        if (fsm.isTerminated()) {
            String requestKey = fsm.getKey();
            log.info("Delete LAG {} FSM have reached termination state (key={})", fsm.getRequest(), requestKey);
            handlers.remove(requestKey);
            carrier.cancelTimeoutCallback(requestKey);

            if (isAllOperationsCompleted() && !active) {
                carrier.sendInactive();
            }
        }
    }
}
