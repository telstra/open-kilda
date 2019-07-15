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

import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.model.PathId;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.history.FlowEventRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
public class FlowRerouteService {
    @VisibleForTesting
    final Map<String, FlowRerouteFsm> fsms = new HashMap<>();

    private final FlowRerouteFsm.Factory fsmFactory;
    private final FsmExecutor<FlowRerouteFsm, State, Event, FlowRerouteContext> fsmExecutor
            = new FsmExecutor<>(Event.NEXT);

    private final FlowRerouteHubCarrier carrier;
    private final PersistenceManager persistenceManager;
    private final FlowEventRepository flowEventRepository;
    private final PathComputer pathComputer;
    private final FlowResourcesManager flowResourcesManager;
    private final int transactionRetriesLimit;
    private final int speakerCommandRetriesLimit;

    public FlowRerouteService(FlowRerouteHubCarrier carrier, PersistenceManager persistenceManager,
                              PathComputer pathComputer, FlowResourcesManager flowResourcesManager,
                              int transactionRetriesLimit, int speakerCommandRetriesLimit) {
        this.carrier = carrier;
        this.persistenceManager = persistenceManager;
        flowEventRepository = persistenceManager.getRepositoryFactory().createFlowEventRepository();
        this.pathComputer = pathComputer;
        this.flowResourcesManager = flowResourcesManager;
        this.transactionRetriesLimit = transactionRetriesLimit;
        this.speakerCommandRetriesLimit = speakerCommandRetriesLimit;
        fsmFactory = new FlowRerouteFsm.Factory(carrier, persistenceManager, pathComputer, flowResourcesManager,
                transactionRetriesLimit, speakerCommandRetriesLimit);
    }

    /**
     * Handles request for flow reroute.
     *
     * @param key command identifier.
     * @param flowId the flow to reroute.
     * @param pathsToReroute the flow paths to reroute.
     * @param forceReroute indicates that reroute must re-install paths even if they're the same.
     * @param rerouteReason the reroute for auto-initiated reroute
     */
    public void handleRequest(String key, CommandContext commandContext, String flowId, Set<PathId> pathsToReroute,
                              boolean forceReroute, String rerouteReason) {
        log.debug("Handling flow reroute request with key {} and flow ID: {}", key, flowId);

        if (fsms.containsKey(key)) {
            log.error("Attempt to create a FSM with key {}, while there's another active FSM with the same key.", key);
            return;
        }

        String eventKey = commandContext.getCorrelationId();
        if (flowEventRepository.existsByTaskId(eventKey)) {
            log.error("Attempt to reuse key {}, but there's a history record(s) for it.", eventKey);
            return;
        }

        FlowRerouteFsm fsm = fsmFactory.newInstance(commandContext, flowId);
        fsms.put(key, fsm);

        FlowRerouteContext context = FlowRerouteContext.builder()
                .flowId(flowId)
                .pathsToReroute(pathsToReroute)
                .forceReroute(forceReroute)
                .rerouteReason(rerouteReason)
                .build();
        fsmExecutor.fire(fsm, Event.NEXT, context);

        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from worker.
     *
     * @param key command identifier.
     */
    public void handleAsyncResponse(String key, SpeakerFlowSegmentResponse flowResponse) {
        log.debug("Received flow command response {}", flowResponse);
        FlowRerouteFsm fsm = fsms.get(key);
        if (fsm == null) {
            log.warn("Failed to find a FSM: received response with key {} for non pending FSM", key);
            return;
        }

        FlowRerouteContext context = FlowRerouteContext.builder()
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
        FlowRerouteFsm fsm = fsms.get(key);
        if (fsm == null) {
            log.warn("Failed to find a FSM: timeout event for non pending FSM with key {}", key);
            return;
        }

        fsmExecutor.fire(fsm, Event.TIMEOUT, null);

        removeIfFinished(fsm, key);
    }

    private void removeIfFinished(FlowRerouteFsm fsm, String key) {
        if (fsm.isTerminated()) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            fsms.remove(key);

            carrier.cancelTimeoutCallback(key);
        }
    }
}
