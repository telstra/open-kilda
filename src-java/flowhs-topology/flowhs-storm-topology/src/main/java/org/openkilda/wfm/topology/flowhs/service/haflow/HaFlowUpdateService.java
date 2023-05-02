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
import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Config;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowProcessingEventListener;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingFsmRegister;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingService;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HaFlowUpdateService extends FlowProcessingService<HaFlowUpdateFsm, Event, HaFlowUpdateContext,
        FlowGenericCarrier, FlowProcessingFsmRegister<HaFlowUpdateFsm>, FlowProcessingEventListener> {
    private final HaFlowUpdateFsm.Factory fsmFactory;

    public HaFlowUpdateService(
            @NonNull FlowGenericCarrier carrier, @NonNull PersistenceManager persistenceManager,
            @NonNull PathComputer pathComputer, @NonNull FlowResourcesManager flowResourcesManager,
            @NonNull RuleManager ruleManager, int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
            int resourceAllocationRetriesLimit, int speakerCommandRetriesLimit) {
        super(new FlowProcessingFsmRegister<>(), new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);

        Config fsmConfig = Config.builder()
                .pathAllocationRetriesLimit(pathAllocationRetriesLimit)
                .pathAllocationRetryDelay(pathAllocationRetryDelay)
                .resourceAllocationRetriesLimit(resourceAllocationRetriesLimit)
                .speakerCommandRetriesLimit(speakerCommandRetriesLimit)
                .build();
        fsmFactory = new HaFlowUpdateFsm.Factory(carrier, fsmConfig, persistenceManager, ruleManager, pathComputer,
                flowResourcesManager);
    }

    /**
     * Handles request for ha-flow update.
     *
     * @param key command identifier.
     * @param request request data.
     */
    public void handleUpdateRequest(
            @NonNull String key, @NonNull CommandContext commandContext, @NonNull HaFlowRequest request)
            throws DuplicateKeyException {

        String haFlowId = request.getHaFlowId();
        log.debug("Handling ha-flow update request with key {} and flow ID: {}", key, haFlowId);

        if (fsmRegister.hasRegisteredFsmWithKey(key)) {
            throw new DuplicateKeyException(key, "There's another active FSM with the same key");
        }

        if (fsmRegister.hasRegisteredFsmWithFlowId(haFlowId)) {
            sendErrorResponseToNorthbound(ErrorType.REQUEST_INVALID, "Could not update ha-flow",
                    format("Ha-flow %s is updating now", haFlowId), commandContext);
            log.error("Attempt to create a FSM with key {}, while there's another active FSM for the same haFlowId {}.",
                    key, haFlowId);
            cancelProcessing(key);
            return;
        }

        HaFlowUpdateFsm fsm = fsmFactory.newInstance(haFlowId, commandContext, eventListeners);
        fsm.setSharedBandwidthGroupId(request.getHaFlowId());
        fsmRegister.registerFsm(key, fsm);

        HaFlowUpdateContext context = HaFlowUpdateContext.builder().targetFlow(request).build();
        fsmExecutor.fire(fsm, Event.NEXT, context);
        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from worker.
     *
     * @param key command identifier.
     */
    public void handleAsyncResponse(@NonNull String key, @NonNull SpeakerCommandResponse flowResponse) {
        log.debug("Received flow command response {}", flowResponse);
        HaFlowUpdateFsm fsm = fsmRegister.getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Failed to find a FSM: received response with key {} for non pending FSM", key);
            return;
        }

        HaFlowUpdateContext context = HaFlowUpdateContext.builder()
                .speakerResponse(flowResponse)
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
        HaFlowUpdateFsm fsm = fsmRegister.getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Failed to find a FSM: timeout event for non pending FSM with key {}", key);
            return;
        }

        fsmExecutor.fire(fsm, Event.TIMEOUT);
        removeIfFinished(fsm, key);
    }

    private void removeIfFinished(HaFlowUpdateFsm fsm, String key) {
        if (fsm.isTerminated()) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            fsmRegister.unregisterFsm(key);
            cancelProcessing(key);
        }
    }
}
