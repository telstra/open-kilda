/* Copyright 2020 Telstra Open Source
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

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.flow.SwapFlowEndpointRequest;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.FlowSwapEndpointsContext;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.FlowSwapEndpointsFsm;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.FlowSwapEndpointsFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.FlowSwapEndpointsFsm.Factory;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMapper;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class FlowSwapEndpointsHubService extends FlowProcessingService<FlowSwapEndpointsHubCarrier> {
    private Map<String, FlowSwapEndpointsFsm> fsms = new HashMap<>();

    private final FlowSwapEndpointsFsm.Factory fsmFactory;

    private boolean active;

    public FlowSwapEndpointsHubService(FlowSwapEndpointsHubCarrier carrier, PersistenceManager persistenceManager) {
        super(carrier, persistenceManager);
        this.fsmFactory = new Factory(carrier, persistenceManager);
    }

    /**
     * Handles request for swap flow endpoints.
     */
    public void handleRequest(String key, CommandContext commandContext, SwapFlowEndpointRequest request) {
        if (yFlowRepository.isSubFlow(request.getFirstFlow().getFlowId())) {
            sendForbiddenSubFlowOperationToNorthbound(request.getFirstFlow().getFlowId(), commandContext);
            return;
        }
        if (yFlowRepository.isSubFlow(request.getSecondFlow().getFlowId())) {
            sendForbiddenSubFlowOperationToNorthbound(request.getSecondFlow().getFlowId(), commandContext);
            return;
        }

        log.debug("Handling swap flow endpoints request with key {} and flow IDs: {}, {}", key,
                request.getFirstFlow().getFlowId(), request.getSecondFlow().getFlowId());

        if (fsms.containsKey(key)) {
            log.error("Attempt to create a FSM with key {}, while there's another active FSM with the same key.", key);
            return;
        }

        RequestedFlow firstFlow = RequestedFlowMapper.INSTANCE.toRequestedFlow(request.getFirstFlow());
        RequestedFlow secondFlow = RequestedFlowMapper.INSTANCE.toRequestedFlow(request.getSecondFlow());
        FlowSwapEndpointsFsm fsm = fsmFactory.newInstance(commandContext, firstFlow, secondFlow);
        fsms.put(key, fsm);
        fsm.fire(Event.NEXT);

        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response.
     */
    public void handleAsyncResponse(String key, Message message) {
        log.debug("Received response {}", message);
        FlowSwapEndpointsFsm fsm = fsms.get(key);
        if (fsm == null) {
            log.warn("Failed to find a FSM: received response with key {} for non pending FSM", key);
            return;
        }

        if (message instanceof InfoMessage && ((InfoMessage) message).getData() instanceof FlowResponse) {
            fsm.fire(Event.RESPONSE_RECEIVED, new FlowSwapEndpointsContext(((InfoMessage) message).getData()));
        } else if (message instanceof ErrorMessage) {
            fsm.fire(Event.ERROR_RECEIVED, new FlowSwapEndpointsContext(((ErrorMessage) message).getData()));
        } else {
            log.warn("Key: {}; Unhandled message {}", key, message);
        }

        removeIfFinished(fsm, key);
    }

    /**
     * Handles timeout case.
     */
    public void handleTaskTimeout(String key) {
        log.debug("Handling timeout for {}", key);
        FlowSwapEndpointsFsm fsm = fsms.get(key);
        if (fsm == null) {
            log.warn("Failed to find a FSM: timeout event for non pending FSM with key {}", key);
            return;
        }

        fsm.fire(Event.TIMEOUT);

        removeIfFinished(fsm, key);
    }

    private void removeIfFinished(FlowSwapEndpointsFsm fsm, String key) {
        if (fsm.isTerminated()) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            fsms.remove(key);

            carrier.cancelTimeoutCallback(key);

            if (!active && fsms.isEmpty()) {
                carrier.sendInactive();
            }
        }
    }

    /**
     * Handles deactivate command.
     */
    public boolean deactivate() {
        active = false;
        if (fsms.isEmpty()) {
            return true;
        }
        return false;
    }

    /**
     * Handles activate command.
     */
    public void activate() {
        active = true;
    }
}
