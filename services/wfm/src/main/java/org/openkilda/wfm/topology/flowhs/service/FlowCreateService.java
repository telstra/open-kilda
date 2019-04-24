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
import org.openkilda.pce.AvailableNetworkFactory;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.pce.PathComputerFactory;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.topology.flowhs.bolts.FlowCreateHubCarrier;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMapper;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineLogger;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class FlowCreateService {

    private transient Map<String, FlowCreateFsm> fsms = new HashMap<>();

    private final PersistenceManager persistenceManager;
    private final FlowResourcesConfig flowResourcesConfig;
    private final PathComputer pathComputer;

    public FlowCreateService(PersistenceManager persistenceManager, PathComputerConfig pathComputerConfig,
                             FlowResourcesConfig flowResourcesConfig) {
        this.persistenceManager = persistenceManager;
        this.flowResourcesConfig = flowResourcesConfig;

        AvailableNetworkFactory availableNetworkFactory =
                new AvailableNetworkFactory(pathComputerConfig, persistenceManager.getRepositoryFactory());
        this.pathComputer = new PathComputerFactory(pathComputerConfig, availableNetworkFactory).getPathComputer();
    }

    /**
     * Handles request for flow creation.
     * @param key command identifier.
     * @param dto request data.
     */
    public void handleRequest(String key, CommandContext commandContext, FlowDto dto, FlowCreateHubCarrier carrier) {
        log.debug("Handling flow create request with key {}", key);
        FlowCreateFsm fsm = FlowCreateFsm.newInstance(commandContext, carrier,
                persistenceManager, flowResourcesConfig, pathComputer);
        fsms.put(key, fsm);

        StateMachineLogger fsmLogger = new StateMachineLogger(fsm);
        fsmLogger.startLogging();

        RequestedFlow request = RequestedFlowMapper.INSTANCE.toRequestedFlow(dto);
        fsm.fire(Event.NEXT, FlowCreateContext.builder()
                .flowDetails(request)
                .build());

        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from worker.
     * @param key command identifier.
     */
    public void handleAsyncResponse(String key, FlowResponse flowResponse) {
        log.debug("Received command completion message {}", flowResponse);
        FlowCreateFsm fsm = fsms.get(key);
        if (fsm == null) {
            log.info("Failed to find fsm: received response with key {} for non pending fsm", key);
            return;
        }

        if (flowResponse.isSuccess()) {
            fsm.fire(Event.COMMAND_EXECUTED, FlowCreateContext.builder()
                    .flowResponse(flowResponse)
                    .build());
        } else {
            FlowErrorResponse errorResponse = (FlowErrorResponse) flowResponse;
            if (errorResponse.getErrorCode() == ErrorCode.OPERATION_TIMED_OUT) {
                fsm.fire(Event.TIMEOUT);
            } else {
                fsm.fire(Event.ERROR);
            }
        }

        removeIfFinished(fsm, key);
    }

    /**
     * Handles timeout case.
     * @param key command identifier.
     */
    public void handleTimeout(String key) {
        FlowCreateFsm fsm = fsms.get(key);

        if (fsm != null) {
            fsm.fire(Event.TIMEOUT);

            if (fsm.getCurrentState() == State.FINISHED_WITH_ERROR) {
                fsms.remove(key);
            }
        }
    }

    private void removeIfFinished(FlowCreateFsm fsm, String key) {
        if (fsm.getCurrentState() == State.FINISHED || fsm.getCurrentState() == State.FINISHED_WITH_ERROR) {
            fsms.remove(key);
        }
    }
}
