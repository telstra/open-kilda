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

import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.Event;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FlowDeleteService extends FlowProcessingWithEventSupportService<FlowDeleteFsm, Event, FlowDeleteContext,
        FlowDeleteHubCarrier, FlowDeleteEventListener> {
    private final FlowDeleteFsm.Factory fsmFactory;

    public FlowDeleteService(FlowDeleteHubCarrier carrier, PersistenceManager persistenceManager,
                             FlowResourcesManager flowResourcesManager,
                             int speakerCommandRetriesLimit) {
        super(new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);
        fsmFactory = new FlowDeleteFsm.Factory(carrier, persistenceManager, flowResourcesManager,
                speakerCommandRetriesLimit);
    }

    /**
     * Handles request for flow delete.
     *
     * @param key command identifier.
     * @param flowId the flow to delete.
     */
    public void handleRequest(String key, CommandContext commandContext, String flowId) throws DuplicateKeyException {
        startFlowDeletion(key, commandContext, flowId, true);
    }

    /**
     * Start flow deletion of the flow.
     */
    public void startFlowDeletion(CommandContext commandContext, String flowId, boolean allowNorthboundResponse) {
        try {
            startFlowDeletion(flowId, commandContext, flowId, allowNorthboundResponse);
        } catch (DuplicateKeyException e) {
            throw new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                    format("Failed to initiate flow deletion for %s / %s: %s", flowId, e.getKey(),
                            e.getMessage()));
        }
    }

    private void startFlowDeletion(String key, CommandContext commandContext, String flowId,
                                   boolean allowNorthboundResponse) throws DuplicateKeyException {
        log.debug("Handling flow deletion request with key {} and flow ID: {}", key, flowId);

        if (hasRegisteredFsmWithKey(key)) {
            throw new DuplicateKeyException(key, "There's another active FSM with the same key");
        }
        if (hasRegisteredFsmWithFlowId(flowId)) {
            if (allowNorthboundResponse) {
                sendErrorResponseToNorthbound(ErrorType.REQUEST_INVALID, "Could not delete flow",
                        format("Flow %s is already deleting now", flowId), commandContext);
            }
            throw new DuplicateKeyException(key, "There's another active FSM for the same flowId " + flowId);
        }

        FlowDeleteFsm fsm = fsmFactory.newInstance(commandContext, flowId, allowNorthboundResponse,
                eventListeners);
        registerFsm(key, fsm);

        fsm.start();
        fsmExecutor.fire(fsm, Event.NEXT, FlowDeleteContext.builder().build());

        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from worker.
     *
     * @param key command identifier.
     */
    public void handleAsyncResponse(String key, SpeakerFlowSegmentResponse flowResponse) throws UnknownKeyException {
        log.debug("Received flow command response {}", flowResponse);
        FlowDeleteFsm fsm = getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        FlowDeleteContext context = FlowDeleteContext.builder()
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
     * Handles async response from worker.
     * Used if the command identifier is unknown, so FSM is identified by the flow Id.
     */
    public void handleAsyncResponseByFlowId(String flowId, SpeakerFlowSegmentResponse flowResponse)
            throws UnknownKeyException {
        String commandKey = getKeyByFlowId(flowId)
                .orElseThrow(() -> new UnknownKeyException(flowId));
        handleAsyncResponse(commandKey, flowResponse);
    }

    /**
     * Handles timeout case.
     *
     * @param key command identifier.
     */
    public void handleTimeout(String key) throws UnknownKeyException {
        log.debug("Handling timeout for {}", key);
        FlowDeleteFsm fsm = getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        fsmExecutor.fire(fsm, Event.TIMEOUT);

        removeIfFinished(fsm, key);
    }

    /**
     * Handles timeout case.
     * Used if the command identifier is unknown, so FSM is identified by the flow Id.
     */
    public void handleTimeoutByFlowId(String flowId) throws UnknownKeyException {
        String commandKey = getKeyByFlowId(flowId)
                .orElseThrow(() -> new UnknownKeyException(flowId));
        handleTimeout(commandKey);
    }

    private void removeIfFinished(FlowDeleteFsm fsm, String key) {
        if (fsm.isTerminated()) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            unregisterFsm(key);

            carrier.cancelTimeoutCallback(key);
            if (!isActive() && !hasAnyRegisteredFsm()) {
                carrier.sendInactive();
            }
        }
    }
}
