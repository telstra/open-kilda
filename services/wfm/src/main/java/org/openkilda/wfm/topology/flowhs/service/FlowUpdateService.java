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
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.history.FlowEventRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMapper;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class FlowUpdateService {
    @VisibleForTesting
    final Map<String, FlowUpdateFsm> fsms = new HashMap<>();

    private final FlowUpdateFsm.Factory fsmFactory;
    private final FsmExecutor<FlowUpdateFsm, State, Event, FlowUpdateContext> fsmExecutor
            = new FsmExecutor<>(Event.NEXT);

    private final FlowUpdateHubCarrier carrier;
    private final PersistenceManager persistenceManager;
    private final FlowEventRepository flowEventRepository;
    private final PathComputer pathComputer;
    private final FlowResourcesManager flowResourcesManager;
    private final int transactionRetriesLimit;
    private final int speakerCommandRetriesLimit;

    public FlowUpdateService(FlowUpdateHubCarrier carrier, PersistenceManager persistenceManager,
                             PathComputer pathComputer, FlowResourcesManager flowResourcesManager,
                             int transactionRetriesLimit, int speakerCommandRetriesLimit) {
        this.carrier = carrier;
        this.persistenceManager = persistenceManager;
        flowEventRepository = persistenceManager.getRepositoryFactory().createFlowEventRepository();
        this.pathComputer = pathComputer;
        this.flowResourcesManager = flowResourcesManager;
        this.transactionRetriesLimit = transactionRetriesLimit;
        this.speakerCommandRetriesLimit = speakerCommandRetriesLimit;
        fsmFactory = new FlowUpdateFsm.Factory(carrier, persistenceManager, pathComputer, flowResourcesManager,
                transactionRetriesLimit, speakerCommandRetriesLimit);
    }

    /**
     * Handles request for flow update.
     *
     * @param key     command identifier.
     * @param request request data.
     */
    public void handleRequest(String key, CommandContext commandContext, FlowRequest request) {
        log.debug("Handling flow update request with key {} and flow ID: {}", key, request.getFlowId());

        if (fsms.containsKey(key)) {
            log.error("Attempt to create a FSM with key {}, while there's another active FSM with the same key.", key);
            return;
        }

        String eventKey = commandContext.getCorrelationId();
        if (flowEventRepository.existsByTaskId(eventKey)) {
            log.error("Attempt to reuse key %s, but there's a history record(s) for it.", eventKey);
            return;
        }

        FlowUpdateFsm fsm = fsmFactory.newInstance(commandContext, request.getFlowId());
        fsms.put(key, fsm);

        RequestedFlow requestedFlow = RequestedFlowMapper.INSTANCE.toRequestedFlow(request);
        FlowUpdateContext context = FlowUpdateContext.builder()
                .targetFlow(requestedFlow)
                .build();
        fsmExecutor.fire(fsm, Event.NEXT, context);

        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from worker.
     *
     * @param key command identifier.
     */
    public void handleAsyncResponse(String key, FlowResponse flowResponse) {
        log.debug("Received flow command response {}", flowResponse);
        FlowUpdateFsm fsm = fsms.get(key);
        if (fsm == null) {
            log.warn("Failed to find a FSM: received response with key {} for non pending FSM", key);
            return;
        }

        FlowUpdateContext context = FlowUpdateContext.builder()
                .speakerFlowResponse(flowResponse)
                .build();

        if (flowResponse instanceof FlowErrorResponse) {
            fsmExecutor.fire(fsm, Event.ERROR_RECEIVED, context);
        } else {
            fsmExecutor.fire(fsm, Event.RESPONSE_RECEIVED, context);
        }

        removeIfFinished(fsm, key);
    }

    /**
     * Handles timeout case.
     *
     * @param key command identifier.
     */
    public void handleTimeout(String key) {
        log.debug("Handling timeout for {}", key);
        FlowUpdateFsm fsm = fsms.get(key);
        if (fsm == null) {
            log.warn("Failed to find a FSM: timeout event for non pending FSM with key {}", key);
            return;
        }

        fsmExecutor.fire(fsm, Event.TIMEOUT, null);

        removeIfFinished(fsm, key);
    }

    private void removeIfFinished(FlowUpdateFsm fsm, String key) {
        if (fsm.isTerminated()) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            fsms.remove(key);

            carrier.cancelTimeoutCallback(key);
        }
    }
}
