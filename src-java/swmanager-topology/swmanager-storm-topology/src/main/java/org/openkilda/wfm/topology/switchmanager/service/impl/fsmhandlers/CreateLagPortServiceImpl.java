/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.switchmanager.service.impl.fsmhandlers;

import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.info.grpc.CreateLogicalPortResponse;
import org.openkilda.messaging.swmanager.request.CreateLagPortRequest;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.switchmanager.error.OperationTimeoutException;
import org.openkilda.wfm.topology.switchmanager.error.SpeakerFailureException;
import org.openkilda.wfm.topology.switchmanager.fsm.CreateLagPortFsm;
import org.openkilda.wfm.topology.switchmanager.fsm.CreateLagPortFsm.CreateLagContext;
import org.openkilda.wfm.topology.switchmanager.fsm.CreateLagPortFsm.CreateLagEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.CreateLagPortFsm.CreateLagState;
import org.openkilda.wfm.topology.switchmanager.service.CreateLagPortService;
import org.openkilda.wfm.topology.switchmanager.service.LagPortOperationConfig;
import org.openkilda.wfm.topology.switchmanager.service.LagPortOperationService;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class CreateLagPortServiceImpl implements CreateLagPortService {

    private final SwitchManagerCarrier carrier;
    private final LagPortOperationService lagPortOperationService;
    private final Map<String, CreateLagPortFsm> fsms = new HashMap<>();
    private final StateMachineBuilder<CreateLagPortFsm, CreateLagState, CreateLagEvent, CreateLagContext> builder;
    private final FsmExecutor<CreateLagPortFsm, CreateLagState, CreateLagEvent, CreateLagContext> fsmExecutor;

    private boolean active = true;

    public CreateLagPortServiceImpl(SwitchManagerCarrier carrier, LagPortOperationConfig config) {
        this.lagPortOperationService = new LagPortOperationService(config);
        this.builder = CreateLagPortFsm.builder();
        this.fsmExecutor = new FsmExecutor<>(CreateLagEvent.NEXT);
        this.carrier = carrier;
    }

    @Override
    public void handleCreateLagRequest(String key, CreateLagPortRequest request) {
        CreateLagPortFsm fsm = builder.newStateMachine(CreateLagState.START, carrier, key, request,
                lagPortOperationService);
        fsms.put(key, fsm);

        fsm.start();
        fireFsmEvent(fsm, CreateLagEvent.NEXT, CreateLagContext.builder().build());
    }

    @Override
    public void handleTaskError(String key, ErrorMessage message) {
        if (!fsms.containsKey(key)) {
            logCreateFsmNotFound(key);
            return;
        }
        CreateLagContext context = CreateLagContext.builder()
                .error(new SpeakerFailureException(message.getData()))
                .build();
        fireFsmEvent(fsms.get(key), CreateLagEvent.ERROR, context);
    }


    @Override
    public void handleTaskTimeout(String key) {
        if (!fsms.containsKey(key)) {
            logCreateFsmNotFound(key);
            return;
        }

        CreateLagPortFsm fsm = fsms.get(key);
        OperationTimeoutException error = new OperationTimeoutException(
                format("LAG create operation timeout. Switch %s", fsm.getSwitchId()));
        fireFsmEvent(fsm, CreateLagEvent.ERROR, CreateLagContext.builder().error(error).build());
    }

    @Override
    public void handleGrpcResponse(String key, CreateLogicalPortResponse response) {
        if (!fsms.containsKey(key)) {
            // CreateLogicalPortResponse could belong to SwitchSyncFsm so log level is Info
            logCreateFsmNotFound(key, true);
            return;
        }
        fireFsmEvent(fsms.get(key), CreateLagEvent.LAG_INSTALLED,
                CreateLagContext.builder().createdLogicalPort(response.getLogicalPort()).build());
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
        return fsms.isEmpty();
    }

    private void logCreateFsmNotFound(String key) {
        logCreateFsmNotFound(key, false);
    }

    private void logCreateFsmNotFound(String key, boolean info) {
        String message = "Create LAG fsm with key {} not found";
        if (info) {
            log.info(message, key);
        } else {
            log.warn(message, key);
        }
    }

    private void fireFsmEvent(CreateLagPortFsm fsm, CreateLagEvent event, CreateLagContext context) {
        fsmExecutor.fire(fsm, event, context);
        removeIfCompleted(fsm);
    }

    private void removeIfCompleted(CreateLagPortFsm fsm) {
        if (fsm.isTerminated()) {
            String requestKey = fsm.getKey();
            log.info("Create LAG {} FSM have reached termination state (key={})", fsm.getRequest(), requestKey);

            fsms.remove(requestKey);
            carrier.cancelTimeoutCallback(requestKey);

            if (isAllOperationsCompleted() && !active) {
                carrier.sendInactive();
            }
        }
    }
}
