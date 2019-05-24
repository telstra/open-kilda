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

package org.openkilda.wfm.topology.reroute.service;

import org.openkilda.wfm.topology.reroute.model.FlowThrottlingData;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ReroutesThrottling {

    private ExtendableTimeWindow extendableTimeWindow;

    private Map<String, FlowThrottlingData> reroutes = new HashMap<>();

    private int defaultFlowPriority;

    public ReroutesThrottling(long minDelay, long maxDelay, int defaultFlowPriority) {
        extendableTimeWindow = new ExtendableTimeWindow(minDelay, maxDelay);
        this.defaultFlowPriority = defaultFlowPriority;
    }

    /**
     * This constructor is used only for testing.
     *
     * @param extendableTimeWindow the extendable time window.
     */
    ReroutesThrottling(ExtendableTimeWindow extendableTimeWindow) {
        this.extendableTimeWindow = extendableTimeWindow;
    }

    /**
     * Keeps current reroute request. Only last correlationId will be saved.
     *
     * @param flowId the flow ID.
     * @param throttlingData the correlation ID and flow priority.
     */
    public void putRequest(String flowId, FlowThrottlingData throttlingData) {
        log.info("Puts flow {} with correlationId {}", flowId, throttlingData.getCorrelationId());
        FlowThrottlingData prevThrottlingData = reroutes.put(flowId, throttlingData);
        if (prevThrottlingData != null) {
            throttlingData.getPathIdSet().addAll(prevThrottlingData.getPathIdSet());

            log.info("Previous flow {} with correlationId {} was dropped.",
                    flowId, prevThrottlingData.getCorrelationId());
        }
        extendableTimeWindow.registerEvent();
    }

    /**
     * Gets reroutes.
     * Returns a sorted list with unique reroutes if timeWindow is ends or empty map otherwise.
     *
     * @return sorted list with flowId as key and throttling data as value.
     */
    public List<Map.Entry<String, FlowThrottlingData>> getReroutes() {
        if (extendableTimeWindow.isTimeToFlush()) {
            extendableTimeWindow.flush();
            List<Map.Entry<String, FlowThrottlingData>> result = new ArrayList<>(reroutes.entrySet());
            result.sort(Map.Entry.comparingByValue(new FlowPriorityComparator()));
            reroutes = new HashMap<>();
            return result;
        } else {
            return Collections.emptyList();
        }
    }

    private class FlowPriorityComparator implements Comparator<FlowThrottlingData> {
        @Override
        public int compare(FlowThrottlingData throttlingDataA, FlowThrottlingData throttlingDataB) {
            int priorityA = throttlingDataA.getPriority() == null ? defaultFlowPriority : throttlingDataA.getPriority();
            int priorityB = throttlingDataB.getPriority() == null ? defaultFlowPriority : throttlingDataB.getPriority();
            Instant timeCreateA = throttlingDataA.getTimeCreate();
            Instant timeCreateB = throttlingDataB.getTimeCreate();

            if (priorityA == priorityB && (timeCreateA != null || timeCreateB != null)) {
                if (timeCreateA == null) {
                    return -1;
                }
                if (timeCreateB == null) {
                    return 1;
                }
                return timeCreateA.compareTo(timeCreateB);
            }

            return Integer.compare(priorityA, priorityB);
        }
    }
}
