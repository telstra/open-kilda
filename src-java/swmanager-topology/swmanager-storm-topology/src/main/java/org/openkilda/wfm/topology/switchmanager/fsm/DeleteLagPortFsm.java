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

package org.openkilda.wfm.topology.switchmanager.fsm;

import static java.lang.String.format;
import static org.openkilda.wfm.topology.switchmanager.fsm.DeleteLagPortFsm.DeleteLagEvent.ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.DeleteLagPortFsm.DeleteLagEvent.LAG_REMOVED;
import static org.openkilda.wfm.topology.switchmanager.fsm.DeleteLagPortFsm.DeleteLagEvent.NEXT;
import static org.openkilda.wfm.topology.switchmanager.fsm.DeleteLagPortFsm.DeleteLagState.CREATE_GRPC_COMMAND;
import static org.openkilda.wfm.topology.switchmanager.fsm.DeleteLagPortFsm.DeleteLagState.FINISHED;
import static org.openkilda.wfm.topology.switchmanager.fsm.DeleteLagPortFsm.DeleteLagState.FINISHED_WITH_ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.DeleteLagPortFsm.DeleteLagState.GRPC_COMMAND_SEND;
import static org.openkilda.wfm.topology.switchmanager.fsm.DeleteLagPortFsm.DeleteLagState.REMOVE_LAG_FROM_DB;
import static org.openkilda.wfm.topology.switchmanager.fsm.DeleteLagPortFsm.DeleteLagState.START;

import org.openkilda.messaging.command.grpc.DeleteLogicalPortRequest;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.swmanager.request.DeleteLagPortRequest;
import org.openkilda.messaging.swmanager.response.LagPortResponse;
import org.openkilda.model.LagLogicalPort;
import org.openkilda.model.PhysicalPort;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.switchmanager.error.InconsistentDataException;
import org.openkilda.wfm.topology.switchmanager.error.InvalidDataException;
import org.openkilda.wfm.topology.switchmanager.error.LagPortNotFoundException;
import org.openkilda.wfm.topology.switchmanager.error.SwitchManagerException;
import org.openkilda.wfm.topology.switchmanager.error.SwitchNotFoundException;
import org.openkilda.wfm.topology.switchmanager.fsm.DeleteLagPortFsm.DeleteLagContext;
import org.openkilda.wfm.topology.switchmanager.fsm.DeleteLagPortFsm.DeleteLagEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.DeleteLagPortFsm.DeleteLagState;
import org.openkilda.wfm.topology.switchmanager.service.LagPortOperationService;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;

import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.StateMachineStatus;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

import java.util.Collections;
import java.util.stream.Collectors;

@Slf4j
public class DeleteLagPortFsm extends AbstractStateMachine<
        DeleteLagPortFsm, DeleteLagState, DeleteLagEvent, DeleteLagContext> {

    private final LagPortOperationService lagPortOperationService;
    @Getter
    private final SwitchId switchId;
    private final String key;
    @Getter
    private final DeleteLagPortRequest request;
    private final SwitchManagerCarrier carrier;
    private DeleteLogicalPortRequest grpcRequest;
    private LagLogicalPort removedLagPort;

    public DeleteLagPortFsm(SwitchManagerCarrier carrier, String key, DeleteLagPortRequest request,
                            LagPortOperationService lagPortOperationService) {
        this.carrier = carrier;
        this.key = key;
        this.request = request;
        this.switchId = request.getSwitchId();
        this.lagPortOperationService = lagPortOperationService;
    }

    /**
     * FSM builder.
     */
    public static StateMachineBuilder<DeleteLagPortFsm, DeleteLagState, DeleteLagEvent, DeleteLagContext> builder() {
        StateMachineBuilder<DeleteLagPortFsm, DeleteLagState, DeleteLagEvent, DeleteLagContext>
                builder = StateMachineBuilderFactory.create(
                DeleteLagPortFsm.class,
                DeleteLagState.class,
                DeleteLagEvent.class,
                DeleteLagContext.class,
                SwitchManagerCarrier.class,
                String.class,
                DeleteLagPortRequest.class,
                LagPortOperationService.class);

        builder.transition().from(START).to(CREATE_GRPC_COMMAND).on(NEXT).callMethod("createGrpcRequest");
        builder.transition().from(START).to(FINISHED_WITH_ERROR).on(ERROR);

        builder.transition().from(CREATE_GRPC_COMMAND).to(GRPC_COMMAND_SEND).on(NEXT).callMethod("sendGrpcRequest");
        builder.transition().from(CREATE_GRPC_COMMAND).to(FINISHED_WITH_ERROR).on(ERROR);

        builder.transition().from(GRPC_COMMAND_SEND).to(REMOVE_LAG_FROM_DB).on(LAG_REMOVED).callMethod("removeDbLag");
        builder.transition().from(GRPC_COMMAND_SEND).to(FINISHED_WITH_ERROR).on(ERROR);

        builder.transition().from(REMOVE_LAG_FROM_DB).to(FINISHED).on(NEXT);
        builder.transition().from(REMOVE_LAG_FROM_DB).to(FINISHED_WITH_ERROR).on(ERROR);

        builder.onEntry(FINISHED).callMethod("finishedEnter");
        builder.defineFinalState(FINISHED);

        builder.onEntry(FINISHED_WITH_ERROR).callMethod("finishedWithErrorEnter");
        builder.defineFinalState(FINISHED_WITH_ERROR);

        return builder;
    }

    public String getKey() {
        return key;
    }

    void createGrpcRequest(DeleteLagState from, DeleteLagState to, DeleteLagEvent event, DeleteLagContext context) {
        log.info("Removing LAG {} on switch {}. Key={}", request, switchId, key);
        try {
            lagPortOperationService.ensureDeleteIsPossible(switchId, request.getLogicalPortNumber());

            String ipAddress = lagPortOperationService.getSwitchIpAddress(switchId);
            grpcRequest = new DeleteLogicalPortRequest(ipAddress, request.getLogicalPortNumber());
        } catch (LagPortNotFoundException | InvalidDataException | SwitchNotFoundException
                | InconsistentDataException e) {
            log.error(format("Unable to delete LAG logical port %d on switch %s. Error: %s",
                    request.getLogicalPortNumber(), switchId, e.getMessage()), e);
            fire(ERROR, DeleteLagContext.builder().error(e).build());
        }
    }

    void sendGrpcRequest(DeleteLagState from, DeleteLagState to, DeleteLagEvent event, DeleteLagContext context) {
        log.info("Sending delete LAG request {} to switch {}. Key={}", grpcRequest, switchId, key);
        carrier.sendCommandToSpeaker(key, grpcRequest);
    }

    void removeDbLag(DeleteLagState from, DeleteLagState to, DeleteLagEvent event, DeleteLagContext context) {
        log.info("Removing LAG  port {} from database. Switch {}. Key={}",
                request.getLogicalPortNumber(), switchId, key);
        Integer portNumber = request.getLogicalPortNumber();
        try {
            removedLagPort = lagPortOperationService.removeLagPort(switchId, portNumber);
            log.info("Successfully removed LAG logical port {} on switch {} from DB", portNumber, switchId);
        } catch (LagPortNotFoundException | InvalidDataException e) {
            log.error(
                    "Can't remove LAG logical port {} on switch {} from DB: {}", portNumber, switchId, e.getMessage());
        }
    }

    void finishedEnter(DeleteLagState from, DeleteLagState to, DeleteLagEvent event, DeleteLagContext context) {
        LagPortResponse response;
        if (removedLagPort != null) {
            response = new LagPortResponse(
                    removedLagPort.getLogicalPortNumber(), removedLagPort.getPhysicalPorts().stream()
                    .map(PhysicalPort::getPortNumber).collect(Collectors.toSet()));

        } else {
            // dummy response entity
            // TODO(surabujin): weird behaviour, can we be more correct?
            response = new LagPortResponse(request.getLogicalPortNumber(), Collections.emptySet());
        }

        InfoMessage message = new InfoMessage(response, System.currentTimeMillis(), key);

        carrier.cancelTimeoutCallback(key);
        carrier.response(key, message);
    }

    protected void finishedWithErrorEnter(DeleteLagState from, DeleteLagState to,
                                          DeleteLagEvent event, DeleteLagContext context) {
        SwitchManagerException error = context.getError();
        log.error(format("Unable to delete LAG %s from switch %s. Key: %s. Error: %s",
                request, switchId, key, error.getMessage()), error);

        carrier.cancelTimeoutCallback(key);
        carrier.errorResponse(key, error.getError(), "Error during LAG delete", error.getMessage());
    }

    @Override
    protected void afterTransitionCausedException(DeleteLagState fromState, DeleteLagState toState,
                                                  DeleteLagEvent event, DeleteLagContext context) {
        Throwable exception = getLastException().getTargetException();
        SwitchManagerException error;

        if (exception instanceof SwitchManagerException) {
            error = (SwitchManagerException) exception;
        } else {
            error = new SwitchManagerException(exception);
        }

        setStatus(StateMachineStatus.IDLE);
        fire(ERROR, DeleteLagContext.builder()
                .error(error)
                .build());
    }

    public enum DeleteLagState {
        START,
        REMOVE_LAG_FROM_DB,
        CREATE_GRPC_COMMAND,
        GRPC_COMMAND_SEND,
        FINISHED_WITH_ERROR,
        FINISHED
    }

    public enum DeleteLagEvent {
        NEXT,
        LAG_REMOVED,
        ERROR
    }

    @Value
    @Builder
    public static class DeleteLagContext {
        Integer deletedLogicalPort;
        SwitchManagerException error;
    }
}
