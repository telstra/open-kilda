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

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

@Slf4j
public class DiscoveryWatchListService {
    private final IWatchListCarrier carrier;
    private final long tickPeriod;

    private Set<Endpoint> endpoints = new HashSet<>();
    private SortedMap<Long, Set<Endpoint>> timeouts = new TreeMap<>();


    public DiscoveryWatchListService(IWatchListCarrier carrier, long tickPeriod) {
        this.carrier = carrier;
        this.tickPeriod = tickPeriod;
    }

    @VisibleForTesting
    Set<Endpoint> getEndpoints() {
        return endpoints;
    }

    @VisibleForTesting
    SortedMap<Long, Set<Endpoint>> getTimeouts() {
        return timeouts;
    }

    @VisibleForTesting
    void addWatch(Endpoint endpoint, long currentTime) {
        if (endpoints.add(endpoint)) {
            carrier.discoveryRequest(endpoint, currentTime);
            long key = currentTime + tickPeriod;
            timeouts.computeIfAbsent(key, mappingFunction -> new HashSet<>())
                    .add(endpoint);
        }
    }

    public void addWatch(Endpoint endpoint) {
        log.debug("Watch-list service receive ADD-WATCH request for {}", endpoint);
        addWatch(endpoint, now());
    }

    /**
     * Handle remove watch request.
     */
    public void removeWatch(Endpoint endpoint) {
        log.debug("Watch-list service receive REMOVE-WATCH request for {}", endpoint);
        carrier.watchRemoved(endpoint);
        endpoints.remove(endpoint);
    }

    /**
     * Consume timer tick.
     */
    public void tick(long tickTime) {
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
                long key = tickTime + tickPeriod;
                timeouts.computeIfAbsent(key, mappingFunction -> new HashSet<>())
                        .addAll(renew);
            }
        }
    }

    public void tick() {
        tick(now());
    }

    private long now() {
        return System.nanoTime();
    }
}
