/* Copyright 2019 Telstra Open Source
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

import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse.ErrorCode;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.bolts.FlowCreateHubCarrier;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMapper;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineLogger;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class FlowCreateService {

    private final Map<String, FlowCreateFsm> fsms = new HashMap<>();

    private final PersistenceManager persistenceManager;
    private final FlowResourcesManager flowResourcesManager;
    private final PathComputer pathComputer;

    public FlowCreateService(PersistenceManager persistenceManager, PathComputer pathComputer,
                             FlowResourcesManager flowResourcesManager) {
        this.persistenceManager = persistenceManager;
        this.flowResourcesManager = flowResourcesManager;

        this.pathComputer = pathComputer;
    }

    /**
     * Handles request for flow creation.
     * @param key command identifier.
     * @param dto request data.
     */
    public void handleRequest(String key, CommandContext commandContext, FlowDto dto, FlowCreateHubCarrier carrier) {
        log.debug("Handling flow create request with key {}", key);
        FlowCreateFsm fsm = FlowCreateFsm.newInstance(commandContext, carrier,
                persistenceManager, flowResourcesManager, pathComputer);
        fsms.put(key, fsm);

        StateMachineLogger fsmLogger = new StateMachineLogger(fsm);
        fsmLogger.startLogging();

        FlowCreateContext context = FlowCreateContext.builder()
                .flowDetails(RequestedFlowMapper.INSTANCE.toRequestedFlow(dto))
                .build();
        fsm.fire(Event.NEXT, context);

        processNext(fsm, context);
        removeIfFinished(fsm, key, carrier);
    }

    /**
     * Handles async response from worker.
     * @param key command identifier.
     */
    public void handleAsyncResponse(String key, FlowResponse flowResponse, FlowCreateHubCarrier carrier) {
        log.debug("Received command completion message {}", flowResponse);
        FlowCreateFsm fsm = fsms.get(key);
        if (fsm == null) {
            log.info("Failed to find fsm: received response with key {} for non pending fsm", key);
            return;
        }

        FlowCreateContext context = FlowCreateContext.builder()
                .flowResponse(flowResponse)
                .build();

        if (flowResponse.isSuccess()) {
            fsm.fire(Event.COMMAND_EXECUTED, context);
        } else {
            FlowErrorResponse errorResponse = (FlowErrorResponse) flowResponse;
            if (errorResponse.getErrorCode() == ErrorCode.OPERATION_TIMED_OUT) {
                fsm.fire(Event.TIMEOUT);
            } else {
                fsm.fire(Event.ERROR, context);
            }
        }

        processNext(fsm, null);
        removeIfFinished(fsm, key, carrier);
    }

    /**
     * Handles timeout case.
     * @param key command identifier.
     */
    public void handleTimeout(String key) {
        log.debug("Handling timeout for {}", key);
        FlowCreateFsm fsm = fsms.get(key);

        if (fsm != null) {
            fsm.fire(Event.TIMEOUT);

            removeIfFinished(fsm, key, null);
        }
    }

    private void processNext(FlowCreateFsm fsm, FlowCreateContext context) {
        while (!fsm.getCurrentState().isBlocked()) {
            fsm.fireNext(context);
        }
    }

    private void removeIfFinished(FlowCreateFsm fsm, String key, FlowCreateHubCarrier carrier) {
        if (fsm.getCurrentState() == State.FINISHED || fsm.getCurrentState() == State.FINISHED_WITH_ERROR) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            fsms.remove(key);

            if (carrier != null) {
                carrier.cancelTimeoutCallback(key);
            }
        }
    }
}
