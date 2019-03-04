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
import org.openkilda.wfm.topology.discovery.model.IslReference;
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
    private final Integer taskId;

    private final TickClock clock = new TickClock();

    private long packetNo = 0;
    private Set<Packet> producedPackets = new HashSet<>();
    private Set<Packet> confirmedPackets = new HashSet<>();
    private SortedMap<Long, Set<Packet>> timeouts = new TreeMap<>();

    public DiscoveryWatcherService(long awaitTime, Integer taskId) {
        this.awaitTime = awaitTime;
        this.taskId = taskId;
    }

    /**
     * Add new endpoint for send discovery process.
     */
    public void addWatch(IWatcherCarrier carrier, Endpoint endpoint, long currentTime) {
        Packet packet = Packet.of(endpoint, packetNo);
        log.debug("Watcher service receive ADD-watch request for {} and produce packet id:{} task:{}",
                  endpoint, packet.packetNo, taskId);

        producedPackets.add(packet);
        timeouts.computeIfAbsent(currentTime + awaitTime, key -> new HashSet<>())
                .add(packet);

        DiscoverIslCommandData discoveryRequest = new DiscoverIslCommandData(
                endpoint.getDatapath(), endpoint.getPortNumber(), packetNo);
        carrier.sendDiscovery(discoveryRequest);

        packetNo += 1;
    }

    /**
     * Remove endpoint from discovery process.
     */
    public void removeWatch(IWatcherCarrier carrier, Endpoint endpoint) {
        log.debug("Watcher service receive REMOVE-watch request for {}", endpoint);
        carrier.clearDiscovery(endpoint);
        producedPackets.removeIf(packet -> packet.endpoint.equals(endpoint));
        confirmedPackets.removeIf(packet -> packet.endpoint.equals(endpoint));
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
                    timeoutAction(carrier, ee);
                }
            }
            range.clear();
        }
    }

    /**
     * .
     */
    public void confirmation(Endpoint endpoint, long packetNo) {
        log.debug("Watcher service receive SEND-confirmation for {} id:{} task:{}", endpoint, packetNo, taskId);
        Packet packet = Packet.of(endpoint, packetNo);
        if (producedPackets.remove(packet)) {
            confirmedPackets.add(packet);
        } else if (log.isDebugEnabled()) {
            log.debug("Can't find produced packet for {} id:{} task:{}", endpoint, packetNo, taskId);
        }
    }

    /**
     * Consume discovery event.
     */
    public void discovery(IWatcherCarrier carrier, IslInfoData discoveryEvent) {
        Endpoint source = new Endpoint(discoveryEvent.getSource());
        Long packetId = discoveryEvent.getPacketId();
        if (packetId == null) {
            log.error("Got corrupted discovery packet into {} - packetId field is empty", source);
        } else {
            discovery(carrier, discoveryEvent, Packet.of(source, packetId));
        }
    }

    private void discovery(IWatcherCarrier carrier, IslInfoData discoveryEvent, Packet packet) {
        if (log.isDebugEnabled()) {
            IslReference ref = IslReference.of(discoveryEvent);
            log.debug("Watcher service receive DISCOVERY event for {} id:{} task:{} - {}",
                      packet.endpoint, packet.packetNo, taskId, ref);
        }

        boolean wasProduced = producedPackets.remove(packet);
        boolean wasConfirmed = confirmedPackets.remove(packet);
        if (wasProduced || wasConfirmed) {
            carrier.discoveryReceived(packet.endpoint, discoveryEvent, clock.getCurrentTimeMs());
        } else {
            log.error("Receive invalid or removed discovery packet on {} id:{} task:{}",
                    packet.endpoint, packet.packetNo, taskId);
        }
    }

    private void timeoutAction(IWatcherCarrier carrier, Packet packet) {
        producedPackets.remove(packet);

        if (confirmedPackets.remove(packet)) {
            log.info("Detect discovery packet lost sent via {} id:{} task:{}",
                     packet.endpoint, packet.packetNo, taskId);
            carrier.discoveryFailed(packet.getEndpoint(), clock.getCurrentTimeMs());
        }
    }

    @VisibleForTesting
    Set<Packet> getProducedPackets() {
        return producedPackets;
    }

    @VisibleForTesting
    Set<Packet> getConfirmedPackets() {
        return confirmedPackets;
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
