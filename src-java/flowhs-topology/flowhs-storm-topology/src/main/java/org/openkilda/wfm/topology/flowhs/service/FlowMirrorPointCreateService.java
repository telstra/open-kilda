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

import static java.lang.String.format;

import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.messaging.command.flow.FlowMirrorPointCreateRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMirrorPointMapper;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingFsmRegister;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FlowMirrorPointCreateService extends FlowProcessingService<FlowMirrorPointCreateFsm, Event,
        FlowMirrorPointCreateContext, FlowGenericCarrier,
        FlowProcessingFsmRegister<FlowMirrorPointCreateFsm>, FlowProcessingEventListener> {
    private final FlowMirrorPointCreateFsm.Factory fsmFactory;

    public FlowMirrorPointCreateService(FlowGenericCarrier carrier, PersistenceManager persistenceManager,
                                        PathComputer pathComputer, FlowResourcesManager flowResourcesManager,
                                        RuleManager ruleManager,
                                        int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
                                        int resourceAllocationRetriesLimit, int speakerCommandRetriesLimit) {
        super(new FlowProcessingFsmRegister<>(), new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);
        fsmFactory = new FlowMirrorPointCreateFsm.Factory(carrier, persistenceManager, pathComputer,
                flowResourcesManager, ruleManager, pathAllocationRetriesLimit, pathAllocationRetryDelay,
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
        if (yFlowRepository.isSubFlow(request.getFlowId())) {
            sendForbiddenSubFlowOperationToNorthbound(request.getFlowId(), commandContext);
            cancelProcessing(key);
            return;
        }

        handleRequest(key, commandContext, request);
    }

    /**
     * Handles async response from worker.
     *
     * @param key command identifier.
     */
    public void handleAsyncResponse(String key, SpeakerCommandResponse speakerCommandResponse) {
        log.debug("Received speaker command response {}", speakerCommandResponse);
        FlowMirrorPointCreateFsm fsm = fsmRegister.getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Failed to find a FSM: received response with key {} for non pending FSM", key);
            return;
        }

        FlowMirrorPointCreateContext context = FlowMirrorPointCreateContext.builder()
                .speakerResponse(speakerCommandResponse)
                .build();

        fsmExecutor.fire(fsm, Event.RESPONSE_RECEIVED, context);

        removeIfFinished(fsm, key);
    }

    /**
     * Handles timeout case.
     *
     * @param key command identifier.
     */
    public void handleTimeout(String key) {
        log.debug("Handling timeout for {}", key);
        FlowMirrorPointCreateFsm fsm = fsmRegister.getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Failed to find a FSM: timeout event for non pending FSM with key {}", key);
            return;
        }

        fsmExecutor.fire(fsm, Event.TIMEOUT, null);

        removeIfFinished(fsm, key);
    }

    private void handleRequest(String key, CommandContext commandContext, FlowMirrorPointCreateRequest request) {
        String flowId = request.getFlowId();
        log.debug("Handling flow create mirror point request with key {}, flow ID: {}, and flow mirror ID: {}",
                key, flowId, request.getMirrorPointId());

        if (fsmRegister.hasRegisteredFsmWithKey(key)) {
            log.error("Attempt to create a FSM with key {}, while there's another active FSM with the same key.", key);
            return;
        }
        if (fsmRegister.hasRegisteredFsmWithFlowId(flowId)) {
            sendErrorResponseToNorthbound(ErrorType.REQUEST_INVALID, "Could not update flow",
                    format("Flow %s is updating now", flowId), commandContext);
            log.error("Attempt to create a FSM with key {}, while there's another active FSM for the same flowId {}.",
                    key, flowId);
            cancelProcessing(key);
            return;
        }

        FlowMirrorPointCreateFsm fsm = fsmFactory.newInstance(commandContext, flowId);
        fsmRegister.registerFsm(key, fsm);

        FlowMirrorPointCreateContext context = FlowMirrorPointCreateContext.builder()
                .mirrorPoint(RequestedFlowMirrorPointMapper.INSTANCE.map(request))
                .build();
        fsmExecutor.fire(fsm, Event.NEXT, context);

        removeIfFinished(fsm, key);
    }

    private void removeIfFinished(FlowMirrorPointCreateFsm fsm, String key) {
        if (fsm.isTerminated()) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            fsmRegister.unregisterFsm(key);
            cancelProcessing(key);
        }
    }
}
