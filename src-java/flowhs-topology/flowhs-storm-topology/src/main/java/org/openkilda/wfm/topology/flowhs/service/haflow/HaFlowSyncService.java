/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.service.haflow;

import static java.lang.String.format;

import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.messaging.command.haflow.HaFlowSyncRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncFsm.Config;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncFsm.Event;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowProcessingEventListener;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingFsmRegister;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingService;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HaFlowSyncService extends FlowProcessingService<HaFlowSyncFsm, Event, HaFlowSyncContext,
        FlowGenericCarrier, FlowProcessingFsmRegister<HaFlowSyncFsm>, FlowProcessingEventListener> {

    private final HaFlowSyncFsm.Factory fsmFactory;

    public HaFlowSyncService(
            @NonNull HaFlowGenericCarrier carrier, @NonNull PersistenceManager persistenceManager,
            @NonNull RuleManager ruleManager, int speakerCommandRetriesLimit) {
        super(new FlowProcessingFsmRegister<>(), new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);

        Config fsmConfig = Config.builder()
                .speakerCommandRetriesLimit(speakerCommandRetriesLimit)
                .build();
        fsmFactory = new HaFlowSyncFsm.Factory(carrier, persistenceManager, ruleManager, fsmConfig);
    }

    /**
     * Handles request for HA-flow sync.
     *
     * @param key command identifier.
     * @param request request data.
     */
    public void handleRequest(
            @NonNull String key, @NonNull CommandContext commandContext, @NonNull HaFlowSyncRequest request)
            throws DuplicateKeyException {
        log.debug("Handling HA-flow sync request with key {} and flow ID: {}", key, request.getHaFlowId());

        if (fsmRegister.hasRegisteredFsmWithKey(key)) {
            throw new DuplicateKeyException(key, "There's another active FSM with the same key");
        }

        String haFlowId = request.getHaFlowId();
        if (fsmRegister.hasRegisteredFsmWithFlowId(haFlowId)) {
            sendErrorResponseToNorthbound(ErrorType.ALREADY_EXISTS, "Could not sync ha-flow",
                    format("HA-flow %s is already syncing now", haFlowId), commandContext);
            return;
        }

        HaFlowSyncFsm fsm = fsmFactory.newInstance(commandContext, haFlowId, eventListeners);
        fsmRegister.registerFsm(key, fsm);

        HaFlowSyncContext context = HaFlowSyncContext.builder().build();
        fsm.start(context);
        fsmExecutor.fire(fsm, Event.NEXT, context);
        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from speaker.
     */
    public void handleAsyncResponse(@NonNull String key, @NonNull SpeakerCommandResponse speakerCommandResponse) {
        log.debug("Received speaker command response {}", speakerCommandResponse);
        HaFlowSyncFsm fsm = fsmRegister.getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Failed to find a FSM: received response with key {} for non pending FSM", key);
            return;
        }

        HaFlowSyncContext context = HaFlowSyncContext.builder()
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
        HaFlowSyncFsm fsm = fsmRegister.getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Failed to find a FSM: timeout event for non pending FSM with key {}", key);
            return;
        }

        fsmExecutor.fire(fsm, Event.TIMEOUT, null);
        removeIfFinished(fsm, key);
    }

    private void removeIfFinished(HaFlowSyncFsm fsm, String key) {
        if (fsm.isTerminated()) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            fsmRegister.unregisterFsm(key);
            cancelProcessing(key);
        }
    }
}
