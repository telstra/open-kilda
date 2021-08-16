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
import org.openkilda.messaging.info.grpc.DeleteLogicalPortResponse;
import org.openkilda.messaging.swmanager.request.DeleteLagPortRequest;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.switchmanager.error.OperationTimeoutException;
import org.openkilda.wfm.topology.switchmanager.error.SpeakerFailureException;
import org.openkilda.wfm.topology.switchmanager.fsm.DeleteLagPortFsm;
import org.openkilda.wfm.topology.switchmanager.fsm.DeleteLagPortFsm.DeleteLagContext;
import org.openkilda.wfm.topology.switchmanager.fsm.DeleteLagPortFsm.DeleteLagEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.DeleteLagPortFsm.DeleteLagState;
import org.openkilda.wfm.topology.switchmanager.service.DeleteLagPortService;
import org.openkilda.wfm.topology.switchmanager.service.LagPortOperationService;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;
import org.openkilda.wfm.topology.switchmanager.service.impl.LagPortOperationServiceImpl;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DeleteLagPortServiceImpl implements DeleteLagPortService {

    private final SwitchManagerCarrier carrier;
    private final LagPortOperationService lagOperationService;
    private final Map<String, DeleteLagPortFsm> fsms = new HashMap<>();
    private final StateMachineBuilder<DeleteLagPortFsm, DeleteLagState, DeleteLagEvent, DeleteLagContext> builder;
    private final FsmExecutor<DeleteLagPortFsm, DeleteLagState, DeleteLagEvent, DeleteLagContext> fsmExecutor;

    private boolean active = true;

    public DeleteLagPortServiceImpl(SwitchManagerCarrier carrier, RepositoryFactory repositoryFactory,
                                    TransactionManager transactionManager, int bfdPortOffset, int bfdPortMaxNumber,
                                    int lagPortOffset) {
        this.lagOperationService = new LagPortOperationServiceImpl(repositoryFactory, transactionManager, bfdPortOffset,
                bfdPortMaxNumber, lagPortOffset);
        this.builder = DeleteLagPortFsm.builder();
        this.fsmExecutor = new FsmExecutor<>(DeleteLagEvent.NEXT);
        this.carrier = carrier;
    }

    @Override
    public void handleDeleteLagRequest(String key, DeleteLagPortRequest request) {
        DeleteLagPortFsm fsm = builder.newStateMachine(
                DeleteLagState.START, carrier, key, request, lagOperationService);
        fsms.put(key, fsm);

        fsm.start();
        fireFsmEvent(fsm, DeleteLagEvent.NEXT, DeleteLagContext.builder().build());
    }

    @Override
    public void handleTaskError(String key, ErrorMessage message) {
        if (!fsms.containsKey(key)) {
            logDeleteFsmNotFound(key);
            return;
        }
        DeleteLagContext context = DeleteLagContext.builder()
                .error(new SpeakerFailureException(message.getData()))
                .build();
        fireFsmEvent(fsms.get(key), DeleteLagEvent.ERROR, context);
    }


    @Override
    public void handleTaskTimeout(String key) {
        if (!fsms.containsKey(key)) {
            logDeleteFsmNotFound(key);
            return;
        }

        DeleteLagPortFsm fsm = fsms.get(key);
        OperationTimeoutException error = new OperationTimeoutException(
                format("LAG delete operation timeout. Switch %s", fsm.getSwitchId()));
        fireFsmEvent(fsm, DeleteLagEvent.ERROR, DeleteLagContext.builder().error(error).build());
    }

    @Override
    public void handleGrpcResponse(String key, DeleteLogicalPortResponse response) {
        if (!fsms.containsKey(key)) {
            // DeleteLogicalPortResponse could belong to SwitchSyncFsm so log level is Info
            logDeleteFsmNotFound(key, true);
            return;
        }
        fireFsmEvent(fsms.get(key), DeleteLagEvent.LAG_REMOVED,
                DeleteLagContext.builder().deletedLogicalPort(response.getLogicalPortNumber()).build());
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

    private void logDeleteFsmNotFound(String key) {
        logDeleteFsmNotFound(key, false);
    }

    private void logDeleteFsmNotFound(String key, boolean info) {
        String message = "Delete LAG fsm with key {} not found";
        if (info) {
            log.info(message, key);
        } else {
            log.warn(message, key);
        }
    }

    private void fireFsmEvent(DeleteLagPortFsm fsm, DeleteLagEvent event, DeleteLagContext context) {
        fsmExecutor.fire(fsm, event, context);
        removeIfCompleted(fsm);
    }

    private void removeIfCompleted(DeleteLagPortFsm fsm) {
        if (fsm.isTerminated()) {
            log.info("Delete LAG {} FSM have reached termination state (key={})", fsm.getRequest(), fsm.getKey());
            fsms.remove(fsm.getKey());
            if (isAllOperationsCompleted() && !active) {
                carrier.sendInactive();
            }
        }
    }
}
