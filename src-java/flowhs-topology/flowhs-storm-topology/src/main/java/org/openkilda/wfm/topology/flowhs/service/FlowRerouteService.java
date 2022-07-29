/* Copyright 2021 Telstra Open Source
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
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.info.reroute.error.RerouteError;
import org.openkilda.messaging.info.reroute.error.RerouteInProgressError;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.exceptions.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Config;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingFsmRegister;
import org.openkilda.wfm.topology.flowhs.service.common.FlowProcessingService;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FlowRerouteService extends FlowProcessingService<FlowRerouteFsm, Event, FlowRerouteContext,
        FlowRerouteHubCarrier, FlowProcessingFsmRegister<FlowRerouteFsm>, FlowRerouteEventListener> {
    private final FlowRerouteFsm.Factory fsmFactory;

    public FlowRerouteService(@NonNull FlowRerouteHubCarrier carrier, @NonNull PersistenceManager persistenceManager,
                              @NonNull PathComputer pathComputer, @NonNull FlowResourcesManager flowResourcesManager,
                              int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
                              int resourceAllocationRetriesLimit, int speakerCommandRetriesLimit) {
        super(new FlowProcessingFsmRegister<>(), new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);

        Config fsmConfig = Config.builder()
                .pathAllocationRetriesLimit(pathAllocationRetriesLimit)
                .pathAllocationRetryDelay(pathAllocationRetryDelay)
                .resourceAllocationRetriesLimit(resourceAllocationRetriesLimit)
                .speakerCommandRetriesLimit(speakerCommandRetriesLimit)
                .build();
        fsmFactory = new FlowRerouteFsm.Factory(carrier, fsmConfig, persistenceManager, pathComputer,
                flowResourcesManager);
    }

    /**
     * Handles request for flow reroute.
     */
    public void handleRequest(@NonNull String key, @NonNull FlowRerouteRequest reroute,
                              @NonNull CommandContext commandContext) {
        String flowId = reroute.getFlowId();
        if (yFlowRepository.isSubFlow(flowId)) {
            sendForbiddenSubFlowOperationToNorthbound(flowId, commandContext);
            carrier.sendRerouteResultStatus(flowId, new RerouteError("Forbidden"),
                    commandContext.getCorrelationId());
            cancelProcessing(key);
            return;
        }

        startFlowRerouting(key, reroute, commandContext, flowId, false);
    }

    /**
     * Handles request for flow reroute.
     */
    public void startFlowRerouting(@NonNull FlowRerouteRequest reroute, @NonNull CommandContext commandContext,
                                   @NonNull String sharedBandwidthGroupId, boolean forceReroute) {
        startFlowRerouting(reroute.getFlowId(), reroute, commandContext, sharedBandwidthGroupId, forceReroute);
    }

    private void startFlowRerouting(String key, FlowRerouteRequest reroute, CommandContext commandContext,
                                    String sharedBandwidthGroupId, boolean forceReroute) {
        String flowId = reroute.getFlowId();
        log.debug("Handling flow reroute request with key {} and flow ID: {}", key, flowId);

        try {
            checkRequestsCollision(key, flowId);
        } catch (Exception e) {
            log.error(e.getMessage());
            fsmRegister.getFsmByKey(key).ifPresent(fsm -> removeIfFinished(fsm, key));
            return;
        }

        if (fsmRegister.hasRegisteredFsmWithFlowId(flowId)) {
            carrier.sendRerouteResultStatus(flowId, new RerouteInProgressError(),
                    commandContext.getCorrelationId());
            cancelProcessing(key);
            return;
        }

        FlowRerouteFsm fsm = fsmFactory.newInstance(flowId, commandContext, eventListeners);
        fsm.setSharedBandwidthGroupId(sharedBandwidthGroupId);
        fsmRegister.registerFsm(key, fsm);

        FlowRerouteContext context = FlowRerouteContext.builder()
                .flowId(flowId)
                .affectedIsl(reroute.getAffectedIsl())
                .forceReroute(forceReroute)
                .ignoreBandwidth(reroute.isIgnoreBandwidth())
                .effectivelyDown(reroute.isEffectivelyDown())
                .rerouteReason(reroute.getReason())
                .build();
        fsmExecutor.fire(fsm, Event.NEXT, context);

        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from worker.
     *
     * @param key command identifier.
     */
    public void handleAsyncResponse(@NonNull String key, @NonNull SpeakerFlowSegmentResponse flowResponse)
            throws UnknownKeyException {
        log.debug("Received flow command response {}", flowResponse);
        FlowRerouteFsm fsm = fsmRegister.getFsmByKey(key)
                .orElseThrow(() -> new UnknownKeyException(key));

        FlowRerouteContext context = FlowRerouteContext.builder()
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
    public void handleAsyncResponseByFlowId(@NonNull String flowId, @NonNull SpeakerFlowSegmentResponse flowResponse)
            throws UnknownKeyException {
        String commandKey = fsmRegister.getKeyByFlowId(flowId)
                .orElseThrow(() -> new UnknownKeyException(flowId));
        handleAsyncResponse(commandKey, flowResponse);
    }

    /**
     * Handles timeout case.
     *
     * @param key command identifier.
     */
    public void handleTimeout(@NonNull String key) throws UnknownKeyException {
        log.debug("Handling timeout for {}", key);
        FlowRerouteFsm fsm = fsmRegister.getFsmByKey(key)
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

    private void checkRequestsCollision(String key, String flowId) {
        if (fsmRegister.hasRegisteredFsmWithKey(key)) {
            throw new IllegalStateException(String.format(
                    "Attempt to create a FSM with key %s, while there's another active FSM with the same key "
                            + "(flowId=\"%s\")", key, flowId));
        }
    }

    private void removeIfFinished(FlowRerouteFsm fsm, String key) {
        if (fsm.isTerminated()) {
            log.debug("FSM with key {} is finished with state {}", key, fsm.getCurrentState());
            fsmRegister.unregisterFsm(key);
            cancelProcessing(key);
        }
    }
}
