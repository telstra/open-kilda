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
import org.openkilda.messaging.info.reroute.error.RerouteInProgressError;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Config;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FlowRerouteService extends FlowProcessingWithEventSupportService<FlowRerouteFsm, Event, FlowRerouteContext,
        FlowRerouteHubCarrier, FlowRerouteEventListener> {
    private final FlowRerouteFsm.Factory fsmFactory;

    public FlowRerouteService(FlowRerouteHubCarrier carrier, PersistenceManager persistenceManager,
                              PathComputer pathComputer, FlowResourcesManager flowResourcesManager,
                              int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
                              int resourceAllocationRetriesLimit, int speakerCommandRetriesLimit) {
        super(new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);

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
    public void handleRequest(String key, FlowRerouteRequest reroute, final CommandContext commandContext) {
        if (yFlowRepository.isSubFlow(reroute.getFlowId())) {
            sendForbiddenSubFlowOperationToNorthbound(reroute.getFlowId(), commandContext);
            return;
        }

        startFlowRerouting(key, reroute, commandContext, reroute.getFlowId());
    }

    /**
     * Handles request for flow reroute.
     */
    public void startFlowRerouting(FlowRerouteRequest reroute, CommandContext commandContext,
                                   String sharedBandwidthGroupId) {
        startFlowRerouting(reroute.getFlowId(), reroute, commandContext, sharedBandwidthGroupId);
    }

    private void startFlowRerouting(String key, FlowRerouteRequest reroute, CommandContext commandContext,
                                   String sharedBandwidthGroupId) {
        String flowId = reroute.getFlowId();
        log.debug("Handling flow reroute request with key {} and flow ID: {}", key, flowId);

        try {
            checkRequestsCollision(key, flowId);
        } catch (Exception e) {
            log.error(e.getMessage());
            getFsmByKey(key).ifPresent(fsm -> removeIfFinished(fsm, key));
            return;
        }

        if (hasRegisteredFsmWithFlowId(flowId)) {
            carrier.sendRerouteResultStatus(flowId, new RerouteInProgressError(),
                    commandContext.getCorrelationId());
            return;
        }

        FlowRerouteFsm fsm = fsmFactory.newInstance(flowId, commandContext, eventListeners);
        fsm.setSharedBandwidthGroupId(sharedBandwidthGroupId);
        registerFsm(key, fsm);

        FlowRerouteContext context = FlowRerouteContext.builder()
                .flowId(flowId)
                .affectedIsl(reroute.getAffectedIsl())
                .forceReroute(reroute.isForce())
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
    public void handleAsyncResponse(String key, SpeakerFlowSegmentResponse flowResponse) throws UnknownKeyException {
        log.debug("Received flow command response {}", flowResponse);
        FlowRerouteFsm fsm = getFsmByKey(key).orElse(null);
        if (fsm == null) {
            throw new UnknownKeyException(key);
        }

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
        FlowRerouteFsm fsm = getFsmByKey(key).orElse(null);
        if (fsm == null) {
            throw new UnknownKeyException(key);
        }

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

    private void checkRequestsCollision(String key, String flowId) {
        if (hasRegisteredFsmWithKey(key)) {
            throw new IllegalStateException(String.format(
                    "Attempt to create a FSM with key %s, while there's another active FSM with the same key "
                            + "(flowId=\"%s\")", key, flowId));
        }
    }

    private void removeIfFinished(FlowRerouteFsm fsm, String key) {
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
