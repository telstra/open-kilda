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

package org.openkilda.wfm.topology.flowmonitoring.model;

import org.openkilda.server42.messaging.FlowDirection;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class FlowLatencyRequest {

    @Getter
    private final String requestId;
    @Getter
    private final String flowId;
    @Getter
    private final FlowDirection direction;
    private final Map<Link, Duration> responses;

    @Builder
    public FlowLatencyRequest(String requestId, String flowId, FlowDirection direction, List<Link> flowPath) {
        this.requestId = requestId;
        this.flowId = flowId;
        this.direction = direction;
        this.responses = new HashMap<>();
        flowPath.forEach(link -> responses.put(link, null));
    }

    /**
     * Handle get link latency response.
     */
    public void handleResponse(Link link, Duration latency) {
        log.info("Handle response {} for requestId {}", link, requestId);
        log.info("Waiting for {} for requestId {}", responses.keySet(), requestId);
        responses.put(link, latency);
    }

    public boolean isFulfilled() {
        return responses.values().stream().noneMatch(Objects::isNull);
    }

    public Duration getResult() {
        return responses.values().stream().reduce(Duration.ZERO, Duration::plus);
    }
}
