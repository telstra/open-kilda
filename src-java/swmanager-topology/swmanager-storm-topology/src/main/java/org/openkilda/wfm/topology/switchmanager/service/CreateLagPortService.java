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
import org.openkilda.messaging.info.grpc.CreateOrUpdateLogicalPortResponse;
import org.openkilda.messaging.swmanager.request.CreateLagPortRequest;
import org.openkilda.wfm.error.MessageDispatchException;
import org.openkilda.wfm.error.UnexpectedInputException;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.switchmanager.error.OperationTimeoutException;
import org.openkilda.wfm.topology.switchmanager.error.SpeakerFailureException;
import org.openkilda.wfm.topology.switchmanager.fsm.CreateLagPortFsm;
import org.openkilda.wfm.topology.switchmanager.fsm.CreateLagPortFsm.CreateLagContext;
import org.openkilda.wfm.topology.switchmanager.fsm.CreateLagPortFsm.CreateLagEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.CreateLagPortFsm.CreateLagState;
import org.openkilda.wfm.topology.switchmanager.service.configs.LagPortOperationConfig;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class CreateLagPortService implements SwitchManagerHubService {
    @Getter
    private final SwitchManagerCarrier carrier;

    private final LagPortOperationService lagPortOperationService;

    private final Map<String, CreateLagPortFsm> handlers = new HashMap<>();
    private final StateMachineBuilder<CreateLagPortFsm, CreateLagState, CreateLagEvent, CreateLagContext> builder;
    private final FsmExecutor<CreateLagPortFsm, CreateLagState, CreateLagEvent, CreateLagContext> fsmExecutor;

    private boolean active = true;

    public CreateLagPortService(SwitchManagerCarrier carrier, LagPortOperationConfig config) {
        this.lagPortOperationService = new LagPortOperationService(config);
        this.builder = CreateLagPortFsm.builder();
        this.fsmExecutor = new FsmExecutor<>(CreateLagEvent.NEXT);
        this.carrier = carrier;
    }

    /**
     * Handle LAG port request.
     */
    public void handleCreateLagRequest(String key, CreateLagPortRequest request) {
        CreateLagPortFsm fsm = builder.newStateMachine(CreateLagState.START, carrier, key, request,
                lagPortOperationService);
        handlers.put(key, fsm);

        fsm.start();
        fireFsmEvent(fsm, CreateLagEvent.NEXT, CreateLagContext.builder().build());
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

    @Override
    public void timeout(@NonNull MessageCookie cookie) throws MessageDispatchException {
        OperationTimeoutException error = new OperationTimeoutException("LAG create operation timeout");
        fireFsmEvent(cookie, CreateLagEvent.ERROR, CreateLagContext.builder().error(error).build());
    }

    @Override
    public void dispatchWorkerMessage(InfoData payload, MessageCookie cookie)
            throws UnexpectedInputException, MessageDispatchException {
        if (payload instanceof CreateOrUpdateLogicalPortResponse) {
            handleCreateOrUpdateResponse((CreateOrUpdateLogicalPortResponse) payload, cookie);
        } else {
            throw new UnexpectedInputException(payload);
        }
    }

    @Override
    public void dispatchErrorMessage(ErrorData payload, MessageCookie cookie)
            throws MessageDispatchException {
        CreateLagContext context = CreateLagContext.builder()
                .error(new SpeakerFailureException(payload))
                .build();
        fireFsmEvent(cookie, CreateLagEvent.ERROR, context);
    }

    private void handleCreateOrUpdateResponse(CreateOrUpdateLogicalPortResponse payload, MessageCookie cookie)
            throws MessageDispatchException {
        fireFsmEvent(cookie, CreateLagEvent.LAG_INSTALLED,
                CreateLagContext.builder().createdLogicalPort(payload.getLogicalPort()).build());
    }

    private void fireFsmEvent(MessageCookie cookie, CreateLagEvent event, CreateLagContext context)
            throws MessageDispatchException {
        CreateLagPortFsm handler = null;
        if (cookie != null) {
            handler = handlers.get(cookie.getValue());
        }
        if (handler == null) {
            throw new MessageDispatchException(cookie);
        }
        fireFsmEvent(handler, event, context);
    }

    private void fireFsmEvent(CreateLagPortFsm fsm, CreateLagEvent event, CreateLagContext context) {
        fsmExecutor.fire(fsm, event, context);
        removeIfCompleted(fsm);
    }

    private void removeIfCompleted(CreateLagPortFsm fsm) {
        if (fsm.isTerminated()) {
            String requestKey = fsm.getKey();
            log.info("Create LAG {} FSM have reached termination state (key={})", fsm.getRequest(), requestKey);

            handlers.remove(requestKey);
            carrier.cancelTimeoutCallback(requestKey);

            if (isAllOperationsCompleted() && !active) {
                carrier.sendInactive();
            }
        }
    }
}
