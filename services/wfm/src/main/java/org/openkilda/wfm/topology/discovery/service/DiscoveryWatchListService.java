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

import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.TickClock;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

@Slf4j
public class DiscoveryWatchListService {
    private final long tickPeriodMs;

    private Set<Endpoint> endpoints = new HashSet<>();
    private SortedMap<Long, Set<Endpoint>> timeouts = new TreeMap<>();

    private final TickClock clock = new TickClock();

    public DiscoveryWatchListService(long tickPeriodMs) {
        this.tickPeriodMs = tickPeriodMs;
    }

    @VisibleForTesting
    Set<Endpoint> getEndpoints() {
        return endpoints;
    }

    @VisibleForTesting
    SortedMap<Long, Set<Endpoint>> getTimeouts() {
        return timeouts;
    }

    /**
     * Add endpoint into "watch list".
     */
    @VisibleForTesting
    void addWatch(IWatchListCarrier carrier, Endpoint endpoint, long currentTime) {
        if (endpoints.add(endpoint)) {
            carrier.discoveryRequest(endpoint, currentTime);
            long key = currentTime + tickPeriodMs;
            timeouts.computeIfAbsent(key, mappingFunction -> new HashSet<>())
                    .add(endpoint);
        }
    }

    public void addWatch(IWatchListCarrier carrier, Endpoint endpoint) {
        log.debug("Watch-list service receive ADD-WATCH request for {}", endpoint);
        addWatch(carrier, endpoint, clock.getCurrentTimeMs());
    }

    /**
     * Handle remove watch request.
     */
    public void removeWatch(IWatchListCarrier carrier, Endpoint endpoint) {
        log.debug("Watch-list service receive REMOVE-WATCH request for {}", endpoint);
        carrier.watchRemoved(endpoint);
        endpoints.remove(endpoint);
    }

    /**
     * Consume timer tick.
     */
    public void tick(IWatchListCarrier carrier, long tickTime) {
        clock.tick(tickTime);

        SortedMap<Long, Set<Endpoint>> range = timeouts.subMap(0L, tickTime + 1);
        if (!range.isEmpty()) {
            HashSet<Endpoint> renew = new HashSet<>();
            for (Set<Endpoint> e : range.values()) {
                for (Endpoint ee : e) {
                    if (endpoints.contains(ee)) {
                        carrier.discoveryRequest(ee, tickTime);
                        renew.add(ee);
                    }
                }
            }
            range.clear();
            if (!renew.isEmpty()) {
                long key = tickTime + tickPeriodMs;
                timeouts.computeIfAbsent(key, mappingFunction -> new HashSet<>())
                        .addAll(renew);
            }
        }
    }
}
