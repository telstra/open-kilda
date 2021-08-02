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

package org.openkilda.wfm.topology.flowmonitoring.service;

import org.openkilda.server42.messaging.FlowDirection;
import org.openkilda.wfm.topology.flowmonitoring.model.FlowLatencyRequest;
import org.openkilda.wfm.topology.flowmonitoring.model.Link;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class CalculateFlowLatencyService {

    private Map<String, FlowLatencyRequest> requests = new HashMap<>();

    private FlowCacheBoltCarrier carrier;

    public CalculateFlowLatencyService(FlowCacheBoltCarrier carrier) {
        this.carrier = carrier;
    }

    /**
     * Handle calculate flow latency request.
     */
    public void handleCalculateFlowLatencyRequest(String flowId, FlowDirection direction, List<Link> flowPath) {
        requests.values().stream()
                .filter(request -> request.getFlowId().equals(flowId) && request.getDirection() == direction)
                .findAny()
                .ifPresent(flowLatencyRequest -> {
                    FlowLatencyRequest remove = requests.remove(flowLatencyRequest.getRequestId());
                    log.warn("Removing previous calculate flow latency request for {} {} for requestId {}",
                            flowId, direction, remove.getRequestId());
                });

        String requestId = UUID.randomUUID().toString();
        requests.put(requestId, FlowLatencyRequest.builder()
                .requestId(requestId)
                .flowId(flowId)
                .direction(direction)
                .flowPath(flowPath)
                .build());

        flowPath.forEach(link -> carrier.emitGetLinkLatencyRequest(flowId, requestId, link));
    }

    /**
     * Handle get link latency response. Send check flow latency request if ready.
     */
    public void handleGetLinkLatencyResponse(String requestId, Link link, Duration latency) {
        log.debug("Get link latency response for link {} with {} and requestId {}", link, latency, requestId);
        FlowLatencyRequest flowLatencyRequest = requests.get(requestId);
        if (flowLatencyRequest == null) {
            log.warn("Link latency response for unknown request found {}", link);
            return;
        }
        flowLatencyRequest.handleResponse(link, latency);

        if (flowLatencyRequest.isFulfilled()) {
            log.debug("Process calculated latency for requestId {}", requestId);
            String flowId = flowLatencyRequest.getFlowId();
            FlowDirection direction = flowLatencyRequest.getDirection();
            Duration result = flowLatencyRequest.getResult();
            carrier.emitCheckFlowLatencyRequest(flowId, direction, result);
            carrier.emitLatencyStats(flowId, direction, result);
            requests.remove(requestId);
        }
    }
}
