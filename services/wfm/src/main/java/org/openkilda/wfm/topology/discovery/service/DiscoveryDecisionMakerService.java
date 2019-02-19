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

package org.openkilda.wfm.topology.discovery.service;

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.wfm.topology.discovery.model.Endpoint;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

@Slf4j
public class DiscoveryDecisionMakerService {

    private final long failTimeout;
    private final long awaitTime;
    private HashMap<Endpoint, Long> lastDiscovery = new HashMap<>();

    public DiscoveryDecisionMakerService(long failTimeout, long awaitTime) {
        this.failTimeout = failTimeout;
        this.awaitTime = awaitTime;
    }

    /**
     * .
     */
    public void discovered(IDecisionMakerCarrier carrier, Endpoint endpoint, IslInfoData discoveryEvent,
                           long currentTime) {
        log.debug("Discovery poll DISCOVERED notification on {}", endpoint);
        carrier.linkDiscovered(discoveryEvent);
        lastDiscovery.put(endpoint, currentTime);
    }

    /**
     * Process "failed" event from {@link DiscoveryWatcherService}.
     */
    public void failed(IDecisionMakerCarrier carrier, Endpoint endpoint, long currentTime) {
        log.debug("Discovery poll FAIL notification on {}", endpoint);
        if (!lastDiscovery.containsKey(endpoint)) {
            lastDiscovery.put(endpoint, currentTime - awaitTime);
        }

        long timeWindow = lastDiscovery.get(endpoint) + failTimeout;

        if (currentTime >= timeWindow) {
            carrier.linkDestroyed(endpoint);
        }
    }

    public HashMap<Endpoint, Long> getLastDiscovery() {
        return lastDiscovery;
    }
}
