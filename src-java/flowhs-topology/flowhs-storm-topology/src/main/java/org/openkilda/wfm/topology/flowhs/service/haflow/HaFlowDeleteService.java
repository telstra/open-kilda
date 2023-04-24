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
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowProcessingEventListener;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingFsmRegister;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingService;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HaFlowDeleteService extends FlowProcessingService<HaFlowDeleteFsm, Event, HaFlowDeleteContext,
        FlowGenericCarrier, FlowProcessingFsmRegister<HaFlowDeleteFsm>, FlowProcessingEventListener> {
    private final HaFlowDeleteFsm.Factory fsmFactory;

    public HaFlowDeleteService(
            @NonNull FlowGenericCarrier carrier, @NonNull PersistenceManager persistenceManager,
            @NonNull FlowResourcesManager flowResourcesManager, @NonNull RuleManager ruleManager,
            int speakerCommandRetriesLimit) {
        super(new FlowProcessingFsmRegister<>(), new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);
        fsmFactory = new HaFlowDeleteFsm.Factory(carrier, persistenceManager, flowResourcesManager, ruleManager,
                speakerCommandRetriesLimit);
    }

    /**
     * Handles request for ha-flow delete.
     *
     * @param key command identifier.
     * @param haFlowId the flow to delete.
     */
    public void handleRequest(@NonNull String key, @NonNull CommandContext commandContext, @NonNull String haFlowId)
            throws DuplicateKeyException {
        log.debug("Handling ha-flow deletion request with key {} and ha-flow ID: {}", key, haFlowId);

        if (fsmRegister.hasRegisteredFsmWithKey(key)) {
            throw new DuplicateKeyException(key, "There's another active FSM with the same key");
        }
        if (fsmRegister.hasRegisteredFsmWithFlowId(haFlowId)) {
            sendErrorResponseToNorthbound(ErrorType.REQUEST_INVALID, "Could not delete ha-flow",
                    format("HA-flow %s is already deleting now", haFlowId), commandContext);
            cancelProcessing(key);
            throw new DuplicateKeyException(key, "There's another active FSM for the same ha-flowId " + haFlowId);
        }

        HaFlowDeleteFsm fsm = fsmFactory.newInstance(commandContext, haFlowId, eventListeners);
        fsmRegister.registerFsm(key, fsm);

        fsm.start();
        fsmExecutor.fire(fsm, Event.NEXT, HaFlowDeleteContext.builder().build());

        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from worker.
     *
     * @param key command identifier.
     */
    public void handleAsyncResponse(@NonNull String key, @NonNull SpeakerCommandResponse speakerResponse) {
        log.debug("Received speaker command response {}", speakerResponse);
        HaFlowDeleteFsm fsm = fsmRegister.getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Received a response with unknown key {}.", key);
            return;
        }

        HaFlowDeleteContext context = HaFlowDeleteContext.builder()
                .speakerResponse(speakerResponse)
                .build();
        fsmExecutor.fire(fsm, Event.RESPONSE_RECEIVED, context);
        removeIfFinished(fsm, key);
    }

    /**
     * Handles timeout case.
     *
     * @param key command identifier.
     */
    public void handleTimeout(@NonNull String key) {
        log.debug("Handling timeout for {}", key);
        HaFlowDeleteFsm fsm = fsmRegister.getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Failed to find a FSM: timeout event for non pending FSM with key {}", key);
            return;
        }

        fsmExecutor.fire(fsm, Event.TIMEOUT);
        removeIfFinished(fsm, key);
    }

    private void removeIfFinished(HaFlowDeleteFsm fsm, String key) {
        if (fsm.isTerminated()) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            fsmRegister.unregisterFsm(key);
            cancelProcessing(key);
        }
    }
}
