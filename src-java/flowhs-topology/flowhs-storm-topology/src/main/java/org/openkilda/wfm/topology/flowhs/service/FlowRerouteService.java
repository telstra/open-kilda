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

import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.info.reroute.error.RerouteInProgressError;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FlowRerouteService extends FsmBasedFlowProcessingService<FlowRerouteFsm, Event, FlowRerouteContext,
        FlowRerouteHubCarrier> {
    private final FlowRerouteFsm.Factory fsmFactory;

    public FlowRerouteService(FlowRerouteHubCarrier carrier, PersistenceManager persistenceManager,
                              PathComputer pathComputer, FlowResourcesManager flowResourcesManager,
                              int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
                              int resourceAllocationRetriesLimit, int speakerCommandRetriesLimit) {
        super(new FsmExecutor<>(Event.NEXT), carrier, persistenceManager);
        fsmFactory = new FlowRerouteFsm.Factory(carrier, persistenceManager, pathComputer, flowResourcesManager,
                pathAllocationRetriesLimit, pathAllocationRetryDelay, resourceAllocationRetriesLimit,
                speakerCommandRetriesLimit);
    }

    /**
     * Handles request for flow reroute.
     */
    public void handleRequest(String key, FlowRerouteRequest reroute, final CommandContext commandContext) {
        String flowId = reroute.getFlowId();
        if (yFlowRepository.isSubFlow(flowId)) {
            sendForbiddenSubFlowOperationToNorthbound(flowId, commandContext);
            return;
        }

        log.debug("Handling flow reroute request with key {} and flow ID: {}", key, flowId);

        try {
            checkRequestsCollision(key, flowId);
        } catch (Exception e) {
            log.error(e.getMessage());
            FlowRerouteFsm fsm = getFsmByKey(key).orElse(null);
            if (fsm != null) {
                removeIfFinished(fsm, key);
            }
            return;
        }

        if (hasRegisteredFsmWithFlowId(flowId)) {
            carrier.sendRerouteResultStatus(flowId, new RerouteInProgressError(),
                    commandContext.getCorrelationId());
            return;
        }

        FlowRerouteFsm fsm = fsmFactory.newInstance(commandContext, flowId);
        fsm.setSharedBandwidthGroupId(flowId);
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
    public void handleAsyncResponse(String key, SpeakerFlowSegmentResponse flowResponse) {
        log.debug("Received flow command response {}", flowResponse);
        FlowRerouteFsm fsm = getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Failed to find a FSM: received response with key {} for non pending FSM", key);
            return;
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
     * Handles timeout case.
     *
     * @param key command identifier.
     */
    public void handleTimeout(String key) {
        log.debug("Handling timeout for {}", key);
        FlowRerouteFsm fsm = getFsmByKey(key).orElse(null);
        if (fsm == null) {
            log.warn("Failed to find a FSM: timeout event for non pending FSM with key {}", key);
            return;
        }

        fsmExecutor.fire(fsm, Event.TIMEOUT, null);

        removeIfFinished(fsm, key);
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
