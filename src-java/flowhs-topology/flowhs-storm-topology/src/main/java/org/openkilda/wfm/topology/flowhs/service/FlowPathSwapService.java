/* Copyright 2022 Telstra Open Source
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

import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingFsmRegister;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingService;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FlowPathSwapService extends FlowProcessingService<FlowPathSwapFsm, Event, FlowPathSwapContext,
        FlowPathSwapHubCarrier, FlowProcessingFsmRegister<FlowPathSwapFsm>, FlowProcessingEventListener> {
    private final FlowPathSwapFsm.Factory fsmFactory;

    public FlowPathSwapService(@NonNull FlowPathSwapHubCarrier carrier, @NonNull PersistenceManager persistenceManager,
                               @NonNull RuleManager ruleManager, @NonNull FlowResourcesManager flowResourcesManager,
                               int speakerCommandRetriesLimit) {
        super(new FlowProcessingFsmRegister<>(), new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);
        fsmFactory = new FlowPathSwapFsm.Factory(carrier,
                persistenceManager, flowResourcesManager, ruleManager, speakerCommandRetriesLimit);
    }

    /**
     * Handles request for flow path swap.
     *
     * @param key command identifier.
     * @param flowId the flow to swap.
     */
    public void handleRequest(@NonNull String key, @NonNull CommandContext commandContext, @NonNull String flowId)
            throws DuplicateKeyException {
        if (yFlowRepository.isSubFlow(flowId)) {
            sendForbiddenSubFlowOperationToNorthbound(flowId, commandContext);
            return;
        }
        startFlowPathSwapping(key, commandContext, flowId);
    }

    /**
     * Start flow path swapping for the flow.
     */
    public void startFlowPathSwapping(@NonNull CommandContext commandContext, @NonNull String flowId) {
        try {
            startFlowPathSwapping(flowId, commandContext, flowId);
        } catch (DuplicateKeyException e) {
            throw new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                    format("Failed to initiate flow path swapping for %s / %s: %s", flowId, e.getKey(),
                            e.getMessage()));
        }
    }

    private void startFlowPathSwapping(String key, CommandContext commandContext, String flowId)
            throws DuplicateKeyException {
        log.debug("Handling flow path swap request with key {} and flow ID: {}", key, flowId);

        if (fsmRegister.hasRegisteredFsmWithKey(key)) {
            throw new DuplicateKeyException(key, "There's another active FSM with the same key");
        }
        if (fsmRegister.hasRegisteredFsmWithFlowId(flowId)) {
            sendErrorResponseToNorthbound(ErrorType.REQUEST_INVALID, "Could not swap flow paths",
                    format("Flow %s is in progress now", flowId), commandContext);
            throw new DuplicateKeyException(key, "There's another active FSM for the same flowId " + flowId);
        }

        FlowPathSwapFsm fsm = fsmFactory.newInstance(commandContext, flowId, eventListeners);
        fsmRegister.registerFsm(key, fsm);

        fsm.start();
        fsmExecutor.fire(fsm, Event.NEXT, FlowPathSwapContext.builder().build());

        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from worker.
     *
     * @param key command identifier.
     */
    public void handleAsyncResponse(@NonNull String key, @NonNull SpeakerResponse speakerResponse)
            throws UnknownKeyException {
        log.debug("Received speaker command response {}", speakerResponse);
        FlowPathSwapFsm fsm = fsmRegister.getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        FlowPathSwapContext context = FlowPathSwapContext.builder()
                .speakerResponse(speakerResponse)
                .build();
        fsmExecutor.fire(fsm, Event.RESPONSE_RECEIVED, context);

        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from worker.
     * Used if the command identifier is unknown, so FSM is identified by the flow Id.
     */
    public void handleAsyncResponseByFlowId(@NonNull String flowId, @NonNull SpeakerResponse speakerResponse)
            throws UnknownKeyException {
        String commandKey = fsmRegister.getKeyByFlowId(flowId)
                .orElseThrow(() -> new UnknownKeyException(flowId));
        handleAsyncResponse(commandKey, speakerResponse);
    }

    /**
     * Handles timeout case.
     *
     * @param key command identifier.
     */
    public void handleTimeout(@NonNull String key) throws UnknownKeyException {
        log.debug("Handling timeout for {}", key);
        FlowPathSwapFsm fsm = fsmRegister.getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        fsmExecutor.fire(fsm, Event.TIMEOUT);

        removeIfFinished(fsm, key);
    }

    /**
     * Handles timeout case.
     * Used if the command identifier is unknown, so FSM is identified by the flow Id.
     */
    public void handleTimeoutByFlowId(@NonNull String flowId) throws UnknownKeyException {
        String commandKey = fsmRegister.getKeyByFlowId(flowId)
                .orElseThrow(() -> new UnknownKeyException(flowId));
        handleTimeout(commandKey);
    }

    private void removeIfFinished(FlowPathSwapFsm fsm, String key) {
        if (fsm.isTerminated()) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            fsmRegister.unregisterFsm(key);

            carrier.cancelTimeoutCallback(key);
            if (!isActive() && !fsmRegister.hasAnyRegisteredFsm()) {
                carrier.sendInactive();
            }
        }
    }
}
