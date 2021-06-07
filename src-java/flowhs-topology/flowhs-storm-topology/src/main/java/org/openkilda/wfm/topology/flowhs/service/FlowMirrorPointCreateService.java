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
import org.openkilda.messaging.command.flow.FlowMirrorPointCreateRequest;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.history.FlowEventRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMirrorPointMapper;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class FlowMirrorPointCreateService {
    @VisibleForTesting
    final Map<String, FlowMirrorPointCreateFsm> fsms = new HashMap<>();

    private final FlowMirrorPointCreateFsm.Factory fsmFactory;
    private final FsmExecutor<FlowMirrorPointCreateFsm, State, Event, FlowMirrorPointCreateContext> fsmExecutor
            = new FsmExecutor<>(Event.NEXT);

    private final FlowMirrorPointCreateHubCarrier carrier;
    private final FlowEventRepository flowEventRepository;

    private boolean active;

    public FlowMirrorPointCreateService(FlowMirrorPointCreateHubCarrier carrier, PersistenceManager persistenceManager,
                                        PathComputer pathComputer, FlowResourcesManager flowResourcesManager,
                                        int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
                                        int resourceAllocationRetriesLimit, int speakerCommandRetriesLimit) {
        this.carrier = carrier;
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowEventRepository = repositoryFactory.createFlowEventRepository();
        fsmFactory = new FlowMirrorPointCreateFsm.Factory(carrier, persistenceManager, pathComputer,
                flowResourcesManager, pathAllocationRetriesLimit, pathAllocationRetryDelay,
                resourceAllocationRetriesLimit, speakerCommandRetriesLimit);
    }

    /**
     * Handles request for create flow mirror point.
     *
     * @param key command identifier.
     * @param request request data.
     */

    public void handleCreateMirrorPointRequest(String key, CommandContext commandContext,
                                               FlowMirrorPointCreateRequest request) {
        handleRequest(key, commandContext, request);
    }

    /**
     * Handles async response from worker.
     *
     * @param key command identifier.
     */
    public void handleAsyncResponse(String key, SpeakerFlowSegmentResponse flowResponse) {
        log.debug("Received flow command response {}", flowResponse);
        FlowMirrorPointCreateFsm fsm = fsms.get(key);
        if (fsm == null) {
            log.warn("Failed to find a FSM: received response with key {} for non pending FSM", key);
            return;
        }

        FlowMirrorPointCreateContext context = FlowMirrorPointCreateContext.builder()
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
        FlowMirrorPointCreateFsm fsm = fsms.get(key);
        if (fsm == null) {
            log.warn("Failed to find a FSM: timeout event for non pending FSM with key {}", key);
            return;
        }

        fsmExecutor.fire(fsm, Event.TIMEOUT, null);

        removeIfFinished(fsm, key);
    }

    private void handleRequest(String key, CommandContext commandContext, FlowMirrorPointCreateRequest request) {
        log.debug("Handling flow create mirror point request with key {}, flow ID: {}, and flow mirror ID: {}",
                key, request.getFlowId(), request.getMirrorPointId());

        if (fsms.containsKey(key)) {
            log.error("Attempt to create a FSM with key {}, while there's another active FSM with the same key.", key);
            return;
        }

        String eventKey = commandContext.getCorrelationId();
        if (flowEventRepository.existsByTaskId(eventKey)) {
            log.error("Attempt to reuse key {}, but there's a history record(s) for it.", eventKey);
            return;
        }

        FlowMirrorPointCreateFsm fsm = fsmFactory.newInstance(commandContext, request.getFlowId());
        fsms.put(key, fsm);

        FlowMirrorPointCreateContext context = FlowMirrorPointCreateContext.builder()
                .mirrorPoint(RequestedFlowMirrorPointMapper.INSTANCE.map(request))
                .build();
        fsmExecutor.fire(fsm, Event.NEXT, context);

        removeIfFinished(fsm, key);
    }

    private void removeIfFinished(FlowMirrorPointCreateFsm fsm, String key) {
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
        return fsms.isEmpty();
    }

    /**
     * Handles activate command.
     */
    public void activate() {
        active = true;
    }
}
