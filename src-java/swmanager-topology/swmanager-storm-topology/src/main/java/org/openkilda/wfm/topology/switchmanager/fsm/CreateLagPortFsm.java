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
import static org.openkilda.messaging.model.grpc.LogicalPortType.LAG;
import static org.openkilda.wfm.topology.switchmanager.fsm.CreateLagPortFsm.CreateLagEvent.ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.CreateLagPortFsm.CreateLagEvent.LAG_INSTALLED;
import static org.openkilda.wfm.topology.switchmanager.fsm.CreateLagPortFsm.CreateLagEvent.NEXT;
import static org.openkilda.wfm.topology.switchmanager.fsm.CreateLagPortFsm.CreateLagState.CREATE_LAG_IN_DB;
import static org.openkilda.wfm.topology.switchmanager.fsm.CreateLagPortFsm.CreateLagState.FINISHED;
import static org.openkilda.wfm.topology.switchmanager.fsm.CreateLagPortFsm.CreateLagState.FINISHED_WITH_ERROR;
import static org.openkilda.wfm.topology.switchmanager.fsm.CreateLagPortFsm.CreateLagState.GRPC_COMMAND_SEND;
import static org.openkilda.wfm.topology.switchmanager.fsm.CreateLagPortFsm.CreateLagState.START;

import org.openkilda.messaging.command.grpc.CreateOrUpdateLogicalPortRequest;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.model.grpc.LogicalPort;
import org.openkilda.messaging.swmanager.request.CreateLagPortRequest;
import org.openkilda.messaging.swmanager.response.LagPortResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.switchmanager.error.InconsistentDataException;
import org.openkilda.wfm.topology.switchmanager.error.InvalidDataException;
import org.openkilda.wfm.topology.switchmanager.error.SwitchManagerException;
import org.openkilda.wfm.topology.switchmanager.error.SwitchNotFoundException;
import org.openkilda.wfm.topology.switchmanager.fsm.CreateLagPortFsm.CreateLagContext;
import org.openkilda.wfm.topology.switchmanager.fsm.CreateLagPortFsm.CreateLagEvent;
import org.openkilda.wfm.topology.switchmanager.fsm.CreateLagPortFsm.CreateLagState;
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

import java.util.HashSet;

@Slf4j
public class CreateLagPortFsm extends AbstractStateMachine<
        CreateLagPortFsm, CreateLagState, CreateLagEvent, CreateLagContext> {

    private final LagPortOperationService lagPortOperationService;
    @Getter
    private final SwitchId switchId;
    private final String key;
    @Getter
    private final CreateLagPortRequest request;
    private final SwitchManagerCarrier carrier;
    private CreateOrUpdateLogicalPortRequest grpcRequest;
    private Integer lagLogicalPortNumber;

    public CreateLagPortFsm(SwitchManagerCarrier carrier, String key, CreateLagPortRequest request,
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
    public static StateMachineBuilder<CreateLagPortFsm, CreateLagState, CreateLagEvent, CreateLagContext> builder() {
        StateMachineBuilder<CreateLagPortFsm, CreateLagState, CreateLagEvent, CreateLagContext>
                builder = StateMachineBuilderFactory.create(
                CreateLagPortFsm.class,
                CreateLagState.class,
                CreateLagEvent.class,
                CreateLagContext.class,
                SwitchManagerCarrier.class,
                String.class,
                CreateLagPortRequest.class,
                LagPortOperationService.class);

        builder.transition().from(START).to(CREATE_LAG_IN_DB).on(NEXT).callMethod("createLagInDb");
        builder.transition().from(START).to(FINISHED_WITH_ERROR).on(ERROR);

        builder.transition().from(CREATE_LAG_IN_DB).to(GRPC_COMMAND_SEND).on(NEXT).callMethod("sendGrpcRequest");
        builder.transition().from(CREATE_LAG_IN_DB).to(FINISHED_WITH_ERROR).on(ERROR);

        builder.transition().from(GRPC_COMMAND_SEND).to(FINISHED).on(LAG_INSTALLED).callMethod("lagInstalled");
        builder.transition().from(GRPC_COMMAND_SEND).to(FINISHED_WITH_ERROR).on(ERROR);

        builder.onEntry(FINISHED).callMethod("finishedEnter");
        builder.defineFinalState(FINISHED);

        builder.onEntry(FINISHED_WITH_ERROR).callMethod("finishedWithErrorEnter");
        builder.defineFinalState(FINISHED_WITH_ERROR);

        return builder;
    }

    public String getKey() {
        return key;
    }

    void createLagInDb(CreateLagState from, CreateLagState to, CreateLagEvent event, CreateLagContext context) {
        log.info("Creating LAG {} on switch {}. Key={}", request, switchId, key);
        try {
            HashSet<Integer> targetPorts = new HashSet<>(request.getPortNumbers());
            lagLogicalPortNumber = lagPortOperationService.createLagPort(switchId, targetPorts);

            String ipAddress = lagPortOperationService.getSwitchIpAddress(switchId);
            grpcRequest = new CreateOrUpdateLogicalPortRequest(
                    ipAddress, request.getPortNumbers(), lagLogicalPortNumber, LAG);
        } catch (InvalidDataException | InconsistentDataException | SwitchNotFoundException e) {
            log.error(format("Unable to handle %s. Error: %s", request, e.getMessage()), e);
            fire(ERROR, CreateLagContext.builder().error(e).build());
        }
    }

    void sendGrpcRequest(CreateLagState from, CreateLagState to, CreateLagEvent event, CreateLagContext context) {
        log.info("Sending create LAG request {} to switch {}. Key={}", grpcRequest, switchId, key);
        carrier.sendCommandToSpeaker(key, grpcRequest);
    }

    void lagInstalled(CreateLagState from, CreateLagState to, CreateLagEvent event, CreateLagContext context) {
        log.info("LAG {} successfully installed on switch {}. Key={}", context.createdLogicalPort, switchId, key);
    }

    void finishedEnter(CreateLagState from, CreateLagState to, CreateLagEvent event, CreateLagContext context) {
        LagPortResponse response = new LagPortResponse(
                grpcRequest.getLogicalPortNumber(), grpcRequest.getPortNumbers());
        InfoMessage message = new InfoMessage(response, System.currentTimeMillis(), key);
        carrier.response(key, message);
    }

    protected void finishedWithErrorEnter(CreateLagState from, CreateLagState to,
                                          CreateLagEvent event, CreateLagContext context) {
        removePortEntryIgnoreMissing();
        SwitchManagerException error = context.getError();
        log.error(format("Unable to create LAG %s on switch %s. Key: %s. Error: %s",
                request, switchId, key, error.getMessage()), error);

        carrier.errorResponse(key, error.getError(), "Error during LAG create", error.getMessage());
    }

    private void removePortEntryIgnoreMissing() {
        if (lagLogicalPortNumber == null) {
            return;
        }

        log.info("Removing form DB created LAG port {} on switch {}. Key={}", lagLogicalPortNumber, switchId, key);
        try {
            lagPortOperationService.removeLagPort(switchId, lagLogicalPortNumber);
        } catch (SwitchManagerException e) {
            log.debug(
                    "Ignoring error on LAG logical port #{} on {} remove from DB during rollback: {}",
                    lagLogicalPortNumber, switchId, e.getMessage());
        }
    }

    @Override
    protected void afterTransitionCausedException(CreateLagState fromState, CreateLagState toState,
                                                  CreateLagEvent event, CreateLagContext context) {
        Throwable exception = getLastException().getTargetException();
        SwitchManagerException error;

        if (exception instanceof SwitchManagerException) {
            error = (SwitchManagerException) exception;
        } else {
            error = new SwitchManagerException(exception);
        }

        setStatus(StateMachineStatus.IDLE);
        fire(ERROR, CreateLagContext.builder()
                .error(error)
                .build());
    }

    public enum CreateLagState {
        START,
        CREATE_LAG_IN_DB,
        GRPC_COMMAND_SEND,
        FINISHED_WITH_ERROR,
        FINISHED
    }

    public enum CreateLagEvent {
        NEXT,
        LAG_INSTALLED,
        ERROR
    }

    @Value
    @Builder
    public static class CreateLagContext {
        LogicalPort createdLogicalPort;
        SwitchManagerException error;
    }
}
