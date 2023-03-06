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
import org.openkilda.messaging.command.flow.FlowMirrorPointDeleteRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingFsmRegister;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FlowMirrorPointDeleteService extends FlowProcessingService<FlowMirrorPointDeleteFsm, Event,
        FlowMirrorPointDeleteContext, FlowGenericCarrier,
        FlowProcessingFsmRegister<FlowMirrorPointDeleteFsm>, FlowProcessingEventListener> {
    private final FlowMirrorPointDeleteFsm.Factory fsmFactory;

    public FlowMirrorPointDeleteService(
            FlowGenericCarrier carrier, PersistenceManager persistenceManager,
            FlowResourcesManager flowResourcesManager, RuleManager ruleManager, int speakerCommandRetriesLimit) {
        super(new FlowProcessingFsmRegister<>(), new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);
        fsmFactory = new FlowMirrorPointDeleteFsm.Factory(carrier, persistenceManager, flowResourcesManager,
                ruleManager, speakerCommandRetriesLimit);
    }

    /**
     * Handles request for delete flow mirror point.
     *
     * @param key command identifier.
     * @param request request data.
     */

    public void handleDeleteMirrorPointRequest(String key, CommandContext commandContext,
                                               FlowMirrorPointDeleteRequest request) {
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
    public void handleAsyncResponse(String key, SpeakerCommandResponse commandResponse) {
        log.debug("Received speaker command response {}", commandResponse);
        FlowMirrorPointDeleteFsm fsm = fsmRegister.getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Failed to find a FSM: received response with key {} for non pending FSM", key);
            return;
        }

        FlowMirrorPointDeleteContext context = FlowMirrorPointDeleteContext.builder()
                .speakerResponse(commandResponse)
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
        FlowMirrorPointDeleteFsm fsm = fsmRegister.getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Failed to find a FSM: timeout event for non pending FSM with key {}", key);
            return;
        }

        fsmExecutor.fire(fsm, Event.TIMEOUT, null);

        removeIfFinished(fsm, key);
    }

    private void handleRequest(String key, CommandContext commandContext, FlowMirrorPointDeleteRequest request) {
        String flowId = request.getFlowId();
        log.debug("Handling flow delete mirror point request with key {}, flow ID: {}, and flow mirror ID: {}",
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

        FlowMirrorPointDeleteFsm fsm = fsmFactory.newInstance(commandContext, flowId);
        fsmRegister.registerFsm(key, fsm);

        FlowMirrorPointDeleteContext context = FlowMirrorPointDeleteContext.builder()
                .flowMirrorPointId(request.getMirrorPointId())
                .build();
        fsmExecutor.fire(fsm, Event.NEXT, context);

        removeIfFinished(fsm, key);
    }

    private void removeIfFinished(FlowMirrorPointDeleteFsm fsm, String key) {
        if (fsm.isTerminated()) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            fsmRegister.unregisterFsm(key);
            cancelProcessing(key);
        }
    }
}
