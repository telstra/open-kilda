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

import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.messaging.command.haflow.HaFlowRerouteRequest;
import org.openkilda.messaging.info.reroute.error.RerouteInProgressError;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.Config;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteEventListener;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteHubCarrier;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingFsmRegister;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingService;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HaFlowRerouteService extends FlowProcessingService<HaFlowRerouteFsm, Event, HaFlowRerouteContext,
        FlowRerouteHubCarrier, FlowProcessingFsmRegister<HaFlowRerouteFsm>, FlowRerouteEventListener> {
    private final HaFlowRerouteFsm.Factory fsmFactory;

    public HaFlowRerouteService(
            @NonNull FlowRerouteHubCarrier carrier, @NonNull PersistenceManager persistenceManager,
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
        fsmFactory = new HaFlowRerouteFsm.Factory(carrier, fsmConfig, persistenceManager, ruleManager, pathComputer,
                flowResourcesManager);
    }

    /**
     * Handles request for ha-flow reroute.
     */
    public void handleRerouteRequest(
            @NonNull String key, @NonNull CommandContext commandContext, @NonNull HaFlowRerouteRequest request) {

        String haFlowId = request.getHaFlowId();
        log.debug("Handling ha-flow reroute request with key {} and flow ID: {}", key, haFlowId);

        if (fsmRegister.hasRegisteredFsmWithKey(key)) {
            log.error("Attempt to create a FSM with key {}, while there's another active FSM with the same key. "
                    + "HA-flow id: '{}'", key, haFlowId);
            fsmRegister.getFsmByKey(key).ifPresent(fsm -> removeIfFinished(fsm, key));
        }

        if (fsmRegister.hasRegisteredFsmWithFlowId(haFlowId)) {
            carrier.sendRerouteResultStatus(haFlowId, new RerouteInProgressError(),
                    commandContext.getCorrelationId());
            cancelProcessing(key);
            return;
        }

        HaFlowRerouteFsm fsm = fsmFactory.newInstance(haFlowId, commandContext, eventListeners);
        fsmRegister.registerFsm(key, fsm);

        HaFlowRerouteContext context = HaFlowRerouteContext.builder()
                .haFlowId(haFlowId)
                .affectedIsl(request.getAffectedIsls())
                .ignoreBandwidth(request.isIgnoreBandwidth())
                .effectivelyDown(request.isEffectivelyDown())
                .rerouteReason(request.getReason())
                .build();
        fsmExecutor.fire(fsm, Event.NEXT, context);
        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from worker.
     */
    public void handleAsyncResponse(@NonNull String key, @NonNull SpeakerCommandResponse flowResponse) {
        log.debug("Received flow command response {}", flowResponse);
        HaFlowRerouteFsm fsm = fsmRegister.getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Failed to find a FSM: received response with key {} for non pending FSM", key);
            return;
        }

        HaFlowRerouteContext context = HaFlowRerouteContext.builder()
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
        HaFlowRerouteFsm fsm = fsmRegister.getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Failed to find a FSM: timeout event for non pending FSM with key {}", key);
            return;
        }

        fsmExecutor.fire(fsm, Event.TIMEOUT);
        removeIfFinished(fsm, key);
    }

    private void removeIfFinished(HaFlowRerouteFsm fsm, String key) {
        if (fsm.isTerminated()) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            fsmRegister.unregisterFsm(key);
            cancelProcessing(key);
        }
    }
}
