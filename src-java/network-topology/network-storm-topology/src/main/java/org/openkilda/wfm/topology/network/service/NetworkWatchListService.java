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

package org.openkilda.wfm.topology.network.service;

import org.openkilda.wfm.share.model.Endpoint;

import com.google.common.annotations.VisibleForTesting;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

@Slf4j
public class NetworkWatchListService {
    private final IWatchListCarrier carrier;
    private final long tickPeriod;
    private final long slowDiscoveryTickPeriod;
    private final long bfdSlowDiscoveryTickPeriod;

    private final Map<Endpoint, EndpointContext> endpoints = new HashMap<>();
    private final SortedMap<Long, Set<Endpoint>> timeouts = new TreeMap<>();


    public NetworkWatchListService(IWatchListCarrier carrier, long tickPeriod,
                                   long slowDiscoveryTickPeriod, long bfdSlowDiscoveryTickPeriod) {
        this.carrier = carrier;
        this.tickPeriod = tickPeriod;
        this.slowDiscoveryTickPeriod = slowDiscoveryTickPeriod;
        this.bfdSlowDiscoveryTickPeriod = bfdSlowDiscoveryTickPeriod;
    }

    @VisibleForTesting
    Set<Endpoint> getEndpoints() {
        return endpoints.keySet();
    }

    @VisibleForTesting
    SortedMap<Long, Set<Endpoint>> getTimeouts() {
        return timeouts;
    }

    @VisibleForTesting
    void addWatch(Endpoint endpoint, long currentTime) {
        if (endpoints.put(endpoint, new EndpointContext()) == null) {
            carrier.discoveryRequest(endpoint, currentTime);
            addTimeouts(Collections.singleton(endpoint), currentTime + tickPeriod);
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
     * Handle update slow discovery flag request.
     */
    public void updateSlowDiscoveryFlag(Endpoint endpoint, boolean isEnabled) {
        log.debug("Watch-list service receive update slow discovery flag to {} request for {}", isEnabled, endpoint);
        endpoints.computeIfPresent(endpoint, (e, endpointContext) ->
                endpointContext.toBuilder().slowDiscoveryEnabled(isEnabled).build());
    }

    @VisibleForTesting
    void updateBfdSlowDiscoveryFlag(Endpoint endpoint, boolean isEnabled, long currentTime) {
        log.debug("Watch-list service receive update slow discovery flag to {} for BFD enabled case request for {}",
                isEnabled, endpoint);
        endpoints.computeIfPresent(endpoint, (e, endpointContext) ->
                endpointContext.toBuilder().bfdSlowDiscoveryEnabled(isEnabled).build());

        if (!isEnabled) {
            for (Set<Endpoint> e : timeouts.values()) {
                e.remove(endpoint);
            }

            carrier.discoveryRequest(endpoint, currentTime);
            long delay = Optional.ofNullable(endpoints.get(endpoint))
                    .map(endpointContext ->
                            endpointContext.isSlowDiscoveryEnabled() ? slowDiscoveryTickPeriod : tickPeriod)
                    .orElse(tickPeriod);
            addTimeouts(Collections.singleton(endpoint), currentTime + delay);
        }
    }

    /**
     * Handle update slow discovery flag request for BFD enabled case.
     */
    public void updateBfdSlowDiscoveryFlag(Endpoint endpoint, boolean isEnabled) {
        updateBfdSlowDiscoveryFlag(endpoint, isEnabled, now());
    }

    /**
     * Consume timer tick.
     */
    @VisibleForTesting
    void tick(long tickTime) {
        SortedMap<Long, Set<Endpoint>> range = timeouts.subMap(0L, tickTime + 1);
        if (!range.isEmpty()) {
            Set<Endpoint> renew = new HashSet<>();
            Set<Endpoint> renewSlowDisco = new HashSet<>();
            Set<Endpoint> renewBfdSlowDisco = new HashSet<>();

            for (Set<Endpoint> e : range.values()) {
                for (Endpoint ee : e) {
                    EndpointContext endpointContext = endpoints.get(ee);
                    if (endpointContext != null) {
                        carrier.discoveryRequest(ee, tickTime);

                        if (endpointContext.isBfdSlowDiscoveryEnabled()) {
                            renewBfdSlowDisco.add(ee);
                        } else if (endpointContext.isSlowDiscoveryEnabled()) {
                            renewSlowDisco.add(ee);
                        } else {
                            renew.add(ee);
                        }
                    }
                }
            }
            range.clear();

            addTimeouts(renew, tickTime + tickPeriod);
            addTimeouts(renewSlowDisco, tickTime + slowDiscoveryTickPeriod);
            addTimeouts(renewBfdSlowDisco, tickTime + bfdSlowDiscoveryTickPeriod);
        }
    }

    public void tick() {
        tick(now());
    }

    private long now() {
        return System.nanoTime();
    }

    private void addTimeouts(Set<Endpoint> renew, long key) {
        if (!renew.isEmpty()) {
            timeouts.computeIfAbsent(key, mappingFunction -> new HashSet<>())
                    .addAll(renew);
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder(toBuilder = true)
    private static class EndpointContext {
        boolean slowDiscoveryEnabled;
        boolean bfdSlowDiscoveryEnabled;
    }
}
