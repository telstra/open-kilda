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

package org.openkilda.wfm.topology.floodlightrouter.service.monitor;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.discovery.NetworkDumpSwitchData;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.messaging.model.SwitchAvailabilityData;
import org.openkilda.messaging.model.SwitchAvailabilityEntry;
import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.SwitchConnectMode;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.floodlightrouter.mapper.SwitchNotificationMapper;
import org.openkilda.wfm.topology.floodlightrouter.model.RegionMappingAdd;
import org.openkilda.wfm.topology.floodlightrouter.model.RegionMappingRemove;
import org.openkilda.wfm.topology.floodlightrouter.model.RegionMappingSet;
import org.openkilda.wfm.topology.floodlightrouter.model.SwitchConnect;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchMonitorCarrier;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class SwitchMonitor {
    private final SwitchMonitorCarrier carrier;
    private final Clock clock;

    private final SwitchId switchId;

    private final ConnectionsCollection readWriteConnects = new ConnectionsCollection(SwitchConnectMode.READ_WRITE);

    private final ConnectionsCollection readOnlyConnects = new ConnectionsCollection(SwitchConnectMode.READ_ONLY);

    private Instant lastUpdateTime;

    public SwitchMonitor(SwitchMonitorCarrier carrier, Clock clock, SwitchId switchId) {
        this.carrier = carrier;
        this.clock = clock;
        this.switchId = switchId;

        lastUpdateTime = clock.instant();
    }

    /**
     * Handle status update notification.
     */
    public void handleStatusUpdateNotification(SwitchInfoData notification, String region) {
        switch (notification.getState()) {
            case ADDED:
                processConnectNotification(notification.getSwitchView(), region, readOnlyConnects);
                break;
            case ACTIVATED:
                processConnectNotification(notification.getSwitchView(), region, readWriteConnects);
                break;
            case DEACTIVATED:
                processDisconnectNotification(notification, region, readWriteConnects);
                break;
            case REMOVED:
                processDisconnectNotification(notification, region, readOnlyConnects);
                break;
            default:
                log.debug("Proxy switch {} connect/disconnect unrelated notification - {}", switchId, notification);
                carrier.sendOtherNotification(switchId, notification);
                return;
        }

        lastUpdateTime = clock.instant();
    }

    /**
     * Handle region offline notification.
     */
    public void handleRegionOfflineNotification(String region) {
        for (ConnectionsCollection connections : new ConnectionsCollection[] {readOnlyConnects, readWriteConnects}) {
            SwitchConnect entry = connections.remove(region);
            if (entry != null) {
                loseRegion(region, entry, connections.getMode(), true);
            }
        }
    }

    /**
     * Handle network dump entry/response.
     */
    public void handleNetworkDumpResponse(NetworkDumpSwitchData networkDumpEntry, String region) {
        if (networkDumpEntry.isWriteMode()) {
            processConnectNotification(networkDumpEntry.getSwitchView(), region, readWriteConnects);
            proxyNotificationIfMasterRegion(networkDumpEntry, region);
        } else {
            processConnectNotification(networkDumpEntry.getSwitchView(), region, readOnlyConnects);
        }
    }

    /**
     * Handle port status update notification.
     */
    public void handlePortStatusUpdateNotification(PortInfoData notification, String region) {
        proxyNotificationIfMasterRegion(notification, region);
    }

    private void processConnectNotification(
            SpeakerSwitchView speakerData, String region, ConnectionsCollection connects) {
        boolean isActive = connects.isEmpty() && SwitchConnectMode.READ_WRITE == connects.getMode();
        SwitchConnect update = makeSwitchConnect(isActive, speakerData);
        SwitchConnect current = connects.put(region, update);
        if (current != null) {
            connects.put(region, mergeAvailabilityEntries(current, update));
        } else {
            acquireRegion(speakerData, region, update, connects.getMode());
        }
    }

    private void processDisconnectNotification(
            SwitchInfoData notification, String region, ConnectionsCollection connects) {
        SwitchConnect entry = connects.remove(region);
        if (entry == null) {
            log.error(
                    "Got disconnect notification for {} in region {}, but there is no data about such connect (know "
                            + "connections: {})",
                    switchId, region, formatConnections());
            reportNotificationDrop(notification, region);
            return;
        }

        loseRegion(region, entry, connects.getMode(), false);
    }

    private void acquireRegion(
            SpeakerSwitchView speakerData, String region, SwitchConnect entry, SwitchConnectMode mode) {
        reportRegionAcquire(region, mode);
        if (mode == SwitchConnectMode.READ_WRITE) {
            acquireReadWriteRegion(speakerData, region, entry);
        } else {
            acquireReadOnlyRegion(region);
        }
    }

    private void loseRegion(String region, SwitchConnect entry, SwitchConnectMode mode, boolean isRegionOffline) {
        reportRegionLose(region, mode);
        if (mode == SwitchConnectMode.READ_WRITE) {
            loseReadWriteRegion(region, entry, isRegionOffline);
        } else {
            loseReadOnlyRegion(region);
        }
    }

    private void acquireReadWriteRegion(SpeakerSwitchView speakerData, String region, SwitchConnect entry) {
        if (entry.isActive()) {
            log.info("Set {} active region for {} to \"{}\"", SwitchConnectMode.READ_WRITE, switchId, region);
            carrier.regionUpdateNotification(new RegionMappingSet(switchId, region, true));
            carrier.sendSwitchConnectNotification(switchId, speakerData, makeDump());
        } else {
            carrier.sendSwitchAvailabilityUpdateNotification(switchId, makeDump());
        }
    }

    private void acquireReadOnlyRegion(String region) {
        carrier.regionUpdateNotification(new RegionMappingAdd(switchId, region, false));
        carrier.sendSwitchAvailabilityUpdateNotification(switchId, makeDump());
    }

    private void loseReadWriteRegion(String region, SwitchConnect connect, boolean isRegionOffline) {
        if (connect.isActive()) {
            swapActiveReadWriteRegion(region, isRegionOffline);
        } else {
            carrier.sendSwitchAvailabilityUpdateNotification(switchId, makeDump());
        }
    }

    private void loseReadOnlyRegion(String region) {
        carrier.regionUpdateNotification(new RegionMappingRemove(switchId, region, false));
        carrier.sendSwitchAvailabilityUpdateNotification(switchId, makeDump());
    }

    private void swapActiveReadWriteRegion(String currentRegion, boolean isRegionOffline) {
        Iterator<String> iter = readWriteConnects.listRegions().iterator();
        if (iter.hasNext()) {
            String targetRegion = iter.next();
            log.info(
                    "Change {} active region for {} from \"{}\" to \"{}\"",
                    SwitchConnectMode.READ_WRITE, switchId, currentRegion, targetRegion);

            SwitchConnect target = readWriteConnects.get(targetRegion);
            if (target == null) {
                // it must never happen, but if it happen better throw something meaningful
                throw new IllegalStateException(String.format(
                        "Switch %s availability data for %s corrupted", SwitchConnectMode.READ_WRITE, switchId));
            }

            readWriteConnects.put(targetRegion, target.buildActiveVariant());

            carrier.regionUpdateNotification(new RegionMappingSet(switchId, targetRegion, true));
            carrier.sendSwitchAvailabilityUpdateNotification(switchId, makeDump());
        } else {
            log.info("All {} connection to the switch {} have lost", SwitchConnectMode.READ_WRITE, switchId);

            carrier.regionUpdateNotification(new RegionMappingRemove(switchId, currentRegion, true));
            carrier.sendSwitchDisconnectNotification(switchId, makeDump(), isRegionOffline);
        }
    }

    private void proxyNotificationIfMasterRegion(InfoData notification, String region) {
        readWriteConnects.lookupActive().ifPresent(active -> {
            if (Objects.equals(active, region)) {
                carrier.sendOtherNotification(switchId, notification);
            }
        });
    }

    /**
     * Evaluate become unavailable time for garbage collection.
     */
    public Optional<Instant> getBecomeUnavailableAt() {
        if (readWriteConnects.isEmpty() && readOnlyConnects.isEmpty()) {
            return Optional.of(lastUpdateTime);
        }
        return Optional.empty();
    }

    private SwitchConnect mergeAvailabilityEntries(
            SwitchConnect current, SwitchConnect update) {
        return new SwitchConnect(
                current.isActive() || update.isActive(), current.getConnectedAt(),
                update.getSwitchAddress(), update.getSpeakerAddress());
    }

    private SwitchAvailabilityData makeDump() {
        SwitchAvailabilityData.SwitchAvailabilityDataBuilder builder = SwitchAvailabilityData.builder();

        Set<String> readWriteRegions = new HashSet<>();
        for (SwitchAvailabilityEntry entry : readWriteConnects.dump()) {
            readWriteRegions.add(entry.getRegionName());
            builder.connection(entry);
        }

        for (SwitchAvailabilityEntry entry : readOnlyConnects.dump()) {
            if (readWriteRegions.contains(entry.getRegionName())) {
                continue;
            }
            builder.connection(entry);
        }

        return builder.build();
    }

    private SwitchConnect makeSwitchConnect(boolean isActive, SpeakerSwitchView speakerData) {
        IpSocketAddress switchAddress = null;
        IpSocketAddress speakerAddress = null;
        if (speakerData != null) {
            switchAddress = speakerData.getSwitchSocketAddress();
            speakerAddress = speakerData.getSpeakerSocketAddress();
        }
        return new SwitchConnect(isActive, clock.instant(), switchAddress, speakerAddress);
    }

    private void reportNotificationDrop(InfoData notification, String region) {
        log.debug("Drop speaker switch {} notification in region {}: {}", switchId, region, notification);
    }

    private void reportRegionAcquire(String region, SwitchConnectMode mode) {
        reportAvailabilityUpdate("available", region, mode);
    }

    private void reportRegionLose(String region, SwitchConnectMode mode) {
        reportAvailabilityUpdate("unavailable", region, mode);
    }

    private void reportAvailabilityUpdate(String become, String region, SwitchConnectMode mode) {
        log.info(
                "Switch {} become {} in region \"{}\" in {} mode (all connections {})",
                switchId, become, region, mode, formatConnections());
    }

    private String formatConnections() {
        return String.format(
                "%s: %s --- %s: %s",
                SwitchConnectMode.READ_WRITE, formatConnectionsSet(readWriteConnects.listRegions()),
                SwitchConnectMode.READ_ONLY, formatConnectionsSet(readOnlyConnects.listRegions()));
    }

    private static String formatConnectionsSet(Set<String> regions) {
        return "{" + regions.stream()
                .sorted()
                .collect(Collectors.joining(", ")) + "}";
    }

    private static class ConnectionsCollection {
        private final Map<String, SwitchConnect> data = new HashMap<>();

        @Getter
        private final SwitchConnectMode mode;

        ConnectionsCollection(SwitchConnectMode mode) {
            this.mode = mode;
        }

        SwitchConnect put(String region, SwitchConnect entry) {
            return data.put(region, entry);
        }

        SwitchConnect get(String region) {
            return data.get(region);
        }

        SwitchConnect remove(String region) {
            return data.remove(region);
        }

        boolean isEmpty() {
            return data.isEmpty();
        }

        Optional<String> lookupActive() {
            for (Map.Entry<String, SwitchConnect> entry : data.entrySet()) {
                if (entry.getValue().isActive()) {
                    return Optional.of(entry.getKey());
                }
            }
            return Optional.empty();
        }

        Set<String> listRegions() {
            return data.keySet();
        }

        List<SwitchAvailabilityEntry> dump() {
            List<SwitchAvailabilityEntry> results = new ArrayList<>();
            for (Map.Entry<String, SwitchConnect> entry : data.entrySet()) {
                results.add(SwitchNotificationMapper.INSTANCE.toMessaging(entry.getValue(), entry.getKey(), mode));
            }
            return results;
        }
    }
}
