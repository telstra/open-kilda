/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.utils;

import org.openkilda.wfm.topology.flowhs.model.FlowRerouteFact;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class RerouteRetryManager {
    private final Map<String, RerouteRetryQueue> queueByFlowId = new HashMap<>();

    /**
     * Register request in retry "queue" and check is it can be processed now. Return true if this request can be
     * processed now.
     */
    public boolean record(FlowRerouteFact entity) {
        RerouteRetryQueue queue = queueByFlowId.computeIfAbsent(
                entity.getFlowId(), ignore -> new RerouteRetryQueue());
        queue.add(entity);
        log.info("Size of flow reroute queue for {} is {}", entity.getFlowId(), queue.size());
        return queue.size() == 1;
    }

    /**
     * Return "active" request.
     */
    public Optional<FlowRerouteFact> read(String flowId) {
        RerouteRetryQueue queue = queueByFlowId.get(flowId);
        if (queue == null) {
            return Optional.empty();
        }

        // use method raises exception on empty queue access, because queue can't be empty by used design
        return queue.get();
    }

    /**
     * Remove "active" request.
     */
    public Optional<FlowRerouteFact> discard(String flowId) {
        RerouteRetryQueue queue = queueByFlowId.get(flowId);
        if (queue == null) {
            return Optional.empty();
        }

        // use method raises exception on empty queue access, because queue can't be empty by used design
        Optional<FlowRerouteFact> entity = queue.remove();
        if (queue.isEmpty()) {
            queueByFlowId.remove(flowId);
        }
        return entity;
    }
}
