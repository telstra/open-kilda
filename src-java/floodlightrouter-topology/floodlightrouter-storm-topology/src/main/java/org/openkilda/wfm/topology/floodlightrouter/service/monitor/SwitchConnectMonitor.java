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
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.floodlightrouter.model.RegionMappingUpdate;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchMonitorCarrier;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public abstract class SwitchConnectMonitor {
    protected final SwitchMonitorCarrier carrier;
    private final Clock clock;
    protected final SwitchId switchId;

    protected final Set<String> availableInRegions = new HashSet<>();
    private String activeRegion = null;

    /**
     * Last time when connection was marked as unavailable. Caller must use {@code isAvailable()} result to detect
     * is connection now available or not.
     */
    @Getter
    private Instant becomeUnavailableAt;

    public SwitchConnectMonitor(SwitchMonitorCarrier carrier, Clock clock, SwitchId switchId) {
        this.carrier = carrier;
        this.clock = clock;
        this.switchId = switchId;

        // connection is not active yet... so it is inactive
        becomeUnavailableAt = clock.instant();
    }

    /**
     * Handle switch status notification.
     */
    public boolean handleSwitchStatusNotification(SwitchInfoData notification, String region) {
        ensureSwitchIdMatch(notification.getSwitchId());

        if (isConnectNotification(notification)) {
            handleConnect(notification, region);
        } else if (isDisconnectNotification(notification)) {
            handleDisconnect(notification, region);
        } else {
            return false;
        }
        return true;
    }

    /**
     * Handle region offline notification.
     */
    public void handleRegionOfflineNotification(String region) {
        if (availableInRegions.remove(region)) {
            handleAvailableRegionsSetUpdate();
            if (availableInRegions.isEmpty()) {
                becomeUnavailableDueToRegionOffline();
            }
        }
    }

    /**
     * Handle network dump response.
     */
    public void handleNetworkDumpResponse(NetworkDumpSwitchData switchData, String region) {
        ensureSwitchIdMatch(switchData.getSwitchId());

        boolean availableNow = isAvailable();
        handleConnect(switchData, region);
        if (availableNow && Objects.equals(activeRegion, region)) {
            // proxy network dump for active region only
            carrier.switchStatusUpdateNotification(switchId, switchData);
        }
    }

    public boolean isAvailable() {
        return ! availableInRegions.isEmpty();
    }

    protected void handleConnect(InfoData notification, String region) {
        if (availableInRegions.add(region)) {
            if (availableInRegions.size() == 1) {
                becomeAvailable(notification, region);
            }
            handleAvailableRegionsSetUpdate();
        }
    }

    protected void handleDisconnect(SwitchInfoData notification, String region) {
        if (availableInRegions.remove(region)) {
            handleAvailableRegionsSetUpdate();
            if (availableInRegions.isEmpty()) {
                becomeUnavailable(notification);
            }
        }
    }

    protected void becomeAvailable(InfoData notification, String region) {
        activeRegion = region;
        log.info("Set {} active region for {} to \"{}\"", formatConnectMode(), switchId, activeRegion);

        carrier.regionUpdateNotification(new RegionMappingUpdate(switchId, activeRegion, isReadWriteMode()));
        carrier.switchStatusUpdateNotification(switchId, notification);
    }

    protected void becomeUnavailable(InfoData notification) {
        becomeUnavailableAt = clock.instant();
        activeRegion = null;
        log.info("There is no any {} available regions for {} - switch is unavailable", formatConnectMode(), switchId);

        carrier.regionUpdateNotification(new RegionMappingUpdate(switchId, null, isReadWriteMode()));
        carrier.switchStatusUpdateNotification(switchId, notification);
    }

    protected abstract void becomeUnavailableDueToRegionOffline();

    protected void handleAvailableRegionsSetUpdate() {
        log.info(
                "List of {} availability zones for {} has changed to: {}",
                formatConnectMode(), switchId, formatAvailableRegionsSet());
        if (!availableInRegions.contains(activeRegion) && !availableInRegions.isEmpty()) {
            swapActiveRegion();
        }
    }

    protected void ensureSwitchIdMatch(SwitchId affectedSwitchId) {
        if (!switchId.equals(affectedSwitchId)) {
            throw new IllegalArgumentException(String.format(
                    "Got status update notification for wrong switch %s, can process notification only for %s",
                    affectedSwitchId, switchId));
        }
    }

    protected abstract boolean isReadWriteMode();

    protected abstract boolean isConnectNotification(SwitchInfoData notification);

    protected abstract boolean isDisconnectNotification(SwitchInfoData notification);

    private void swapActiveRegion() {
        Iterator<String> iter = availableInRegions.iterator();
        if (iter.hasNext()) {
            String current = activeRegion;
            activeRegion = iter.next();
            log.info(
                    "Change {} active region for {} from \"{}\" to \"{}\"",
                    formatConnectMode(), switchId, current, activeRegion);
            carrier.regionUpdateNotification(new RegionMappingUpdate(switchId, activeRegion, isReadWriteMode()));
        } else {
            throw new IllegalStateException(String.format(
                    "Unable to determine \"next\" available region for switch %s, availability regions set is empty",
                    switchId));
        }
    }

    private String formatConnectMode() {
        return isReadWriteMode() ? "RW" : "RO";
    }

    private String formatAvailableRegionsSet() {
        return "{" + availableInRegions.stream()
                .sorted()
                .collect(Collectors.joining(", ")) + "}";
    }
}
