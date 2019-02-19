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

import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.TickClock;

import com.google.common.annotations.VisibleForTesting;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

@Slf4j
public class DiscoveryWatcherService {
    private final long awaitTime;

    private final TickClock clock = new TickClock();

    private long packetNo = 0;
    private Set<Packet> confirmations = new HashSet<>();
    private SortedMap<Long, Set<Packet>> timeouts = new TreeMap<>();

    public DiscoveryWatcherService(long awaitTime) {
        this.awaitTime = awaitTime;
    }

    /**
     * Add new endpoint for send discovery process.
     */
    public void addWatch(IWatcherCarrier carrier, Endpoint endpoint, long currentTime) {
        log.debug("Add discovery poll endpoint {}", endpoint);
        Packet packet = Packet.of(endpoint, packetNo);
        timeouts.computeIfAbsent(currentTime + awaitTime, mappingFunction -> new HashSet<>())
                .add(packet);

        DiscoverIslCommandData discoveryRequest = new DiscoverIslCommandData(
                endpoint.getDatapath(), endpoint.getPortNumber(), packetNo);
        carrier.sendDiscovery(discoveryRequest);
        packetNo += 1;
    }

    public void removeWatch(Endpoint endpoint) {
        log.debug("Remove (dummy) discovery poll endpoint {}", endpoint);
        // No action required (at least we can't imagine them now).
    }

    /**
     * Consume timer tick.
     */
    public void tick(IWatcherCarrier carrier, long tickTime) {
        clock.tick(tickTime);

        SortedMap<Long, Set<Packet>> range = timeouts.subMap(0L, tickTime + 1);
        if (!range.isEmpty()) {
            for (Set<Packet> e : range.values()) {
                for (Packet ee : e) {
                    if (confirmations.remove(ee)) {
                        carrier.discoveryFailed(ee.getEndpoint(), tickTime);
                    }
                }
            }
            range.clear();
        }
    }

    /**
     * .
     */
    public void confirmation(Endpoint endpoint, long packetNo) {
        log.debug("Receive discovery send confirmation for {} (packet #{})", endpoint, packetNo);
        Packet packet = Packet.of(endpoint, packetNo);
        confirmations.add(packet);
    }

    /**
     * Consume discovery event.
     */
    public void discovery(IWatcherCarrier carrier, IslInfoData discoveryEvent) {
        Endpoint destination = new Endpoint(discoveryEvent.getDestination());
        Long packetId = discoveryEvent.getPacketId();
        if (packetId == null) {
            log.error("Got corrupted discovery packet into {} - packetId field is empty", destination);
        } else {
            discovery(carrier, discoveryEvent, Packet.of(destination, packetId));
        }
    }

    private void discovery(IWatcherCarrier carrier, IslInfoData discoveryEvent, Packet packet) {
        log.debug("Receive ISL discovery event for {} (packet #{})", packet.endpoint, packet.packetNo);

        confirmations.remove(packet);
        carrier.discoveryReceived(packet.endpoint, discoveryEvent, clock.getCurrentTimeMs());
    }

    @VisibleForTesting
    Set<Packet> getConfirmations() {
        return confirmations;
    }

    @VisibleForTesting
    SortedMap<Long, Set<Packet>> getTimeouts() {
        return timeouts;
    }

    @Value(staticConstructor = "of")
    public static class Packet {
        private final Endpoint endpoint;
        private final long packetNo;
    }
}
