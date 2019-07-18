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

import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Config;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMapper;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class FlowCreateService {

    private final Map<String, FlowCreateFsm> fsms = new HashMap<>();

    private final FlowCreateFsm.Factory fsmFactory;
    private final FlowCreateHubCarrier carrier;
    private final KildaConfigurationRepository kildaConfigurationRepository;

    public FlowCreateService(FlowCreateHubCarrier carrier, PersistenceManager persistenceManager,
                             PathComputer pathComputer, FlowResourcesManager flowResourcesManager,
                             int genericRetriesLimit, int speakerCommandRetriesLimit) {
        this.carrier = carrier;
        this.kildaConfigurationRepository = persistenceManager.getRepositoryFactory()
                .createKildaConfigurationRepository();

        Config fsmConfig = Config.builder()
                .flowCreationRetriesLimit(genericRetriesLimit)
                .speakerCommandRetriesLimit(speakerCommandRetriesLimit)
                .build();
        this.fsmFactory =
                FlowCreateFsm.factory(persistenceManager, carrier, fsmConfig, flowResourcesManager, pathComputer);
    }

    /**
     * Handles request for flow creation.
     * @param key command identifier.
     * @param request request data.
     */
    public void handleRequest(String key, CommandContext commandContext, FlowRequest request) {
        log.debug("Handling flow create request with key {}", key);

        FlowCreateFsm fsm = fsmFactory.produce(request.getFlowId(), commandContext);
        fsms.put(key, fsm);

        RequestedFlow requestedFlow = RequestedFlowMapper.INSTANCE.toRequestedFlow(request);
        if (requestedFlow.getFlowEncapsulationType() == null) {
            requestedFlow.setFlowEncapsulationType(kildaConfigurationRepository.get().getFlowEncapsulationType());
        }
        FlowCreateContext context = FlowCreateContext.builder()
                .targetFlow(requestedFlow)
                .build();

        fsm.start();
        processNext(fsm, context);
        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from worker.
     * @param key command identifier.
     */
    public void handleAsyncResponse(String key, FlowResponse flowResponse) {
        log.debug("Received response {}", flowResponse);
        FlowCreateFsm fsm = fsms.get(key);
        if (fsm == null) {
            log.info("Failed to find fsm: received response with key {} for non pending fsm", key);
            return;
        }

        FlowCreateContext context = FlowCreateContext.builder()
                .speakerFlowResponse(flowResponse)
                .build();
        fsm.fire(Event.RESPONSE_RECEIVED, context);

        processNext(fsm, null);
        removeIfFinished(fsm, key);
    }

    /**
     * Handles timeout case.
     * @param key command identifier.
     */
    public void handleTimeout(String key) {
        log.debug("Handling timeout for {}", key);
        FlowCreateFsm fsm = fsms.get(key);

        if (fsm != null) {
            fsm.fire(Event.TIMEOUT);

            removeIfFinished(fsm, key);
        }
    }

    private void processNext(FlowCreateFsm fsm, FlowCreateContext context) {
        while (!fsm.getCurrentState().isBlocked()) {
            fsm.fireNext(context);
        }
    }

    private void removeIfFinished(FlowCreateFsm fsm, String key) {
        if (fsm.getCurrentState() == State.FINISHED || fsm.getCurrentState() == State.FINISHED_WITH_ERROR) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            fsms.remove(key);

            carrier.cancelTimeoutCallback(key);
        }
    }
}
