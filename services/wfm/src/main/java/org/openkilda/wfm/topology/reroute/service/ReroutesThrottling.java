/* Copyright 2018 Telstra Open Source
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

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ReroutesThrottling {

    private ExtendableTimeWindow extendableTimeWindow;

    private Map<String, String> reroutes = new HashMap<>();

    public ReroutesThrottling(long minDelay, long maxDelay) {
        extendableTimeWindow = new ExtendableTimeWindow(minDelay, maxDelay);
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
     * @param flowId the flow ID
     * @param correlationId the correlation ID
     */
    public void putRequest(String flowId, String correlationId) {
        log.info("Puts flow {} with correlationId {}", flowId, correlationId);
        String prevCorrelationId = reroutes.put(flowId, correlationId);
        if (prevCorrelationId != null) {
            log.info("Previous flow {} with correlationId {} was dropped.", flowId, prevCorrelationId);
        }
        extendableTimeWindow.registerEvent();
    }

    /**
     * Gets reroutes.
     * Returns a map with unique reroutes if timeWindow is ends or empty map otherwise.
     *
     * @return Map with flowId as key and correlationId as value.
     */
    public Map<String, String> getReroutes() {
        if (extendableTimeWindow.isTimeToFlush()) {
            extendableTimeWindow.flush();
            Map<String, String> result = reroutes;
            reroutes = new HashMap<>();
            return result;
        } else {
            return Collections.emptyMap();
        }
    }
}
