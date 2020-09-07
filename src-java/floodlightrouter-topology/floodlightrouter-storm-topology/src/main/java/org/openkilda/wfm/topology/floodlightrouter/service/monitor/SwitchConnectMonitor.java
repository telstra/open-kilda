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
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchMonitorCarrier;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public abstract class SwitchConnectMonitor {
    protected final SwitchMonitorCarrier carrier;
    private final Clock clock;
    protected final SwitchId switchId;

    protected final Set<String> availableInRegions = new HashSet<>();

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
            handleRegionLose(region);
            if (availableInRegions.isEmpty()) {
                becomeUnavailableDueToRegionOffline();
                becomeUnavailableAt = clock.instant();
            }
        }
    }

    /**
     * Handle network dump response.
     */
    public void handleNetworkDumpResponse(NetworkDumpSwitchData switchData, String region) {
        ensureSwitchIdMatch(switchData.getSwitchId());
        if (isReadWriteMode() == switchData.isWriteMode()) {
            handlePeriodicDump(switchData, region);
        }
    }

    public boolean isAvailable() {
        return ! availableInRegions.isEmpty();
    }

    protected void handlePeriodicDump(NetworkDumpSwitchData switchData, String region) {
        handleConnect(switchData, region);
    }

    protected void handleConnect(InfoData notification, String region) {
        if (availableInRegions.add(region)) {
            if (availableInRegions.size() == 1) {
                becomeAvailable(notification, region);
            }
            handleRegionAcquire(region);
        }
    }

    protected void handleDisconnect(SwitchInfoData notification, String region) {
        if (availableInRegions.remove(region)) {
            handleRegionLose(region);
            if (availableInRegions.isEmpty()) {
                becomeUnavailable(notification);
                becomeUnavailableAt = clock.instant();
            }
        }
    }

    protected void becomeAvailable(InfoData notification, String region) {
        // noop
    }

    protected void becomeUnavailable(InfoData notification) {
        // noop
    }

    protected void becomeUnavailableDueToRegionOffline() {
        // noop
    }

    protected void handleRegionAcquire(String region) {
        handleAvailableRegionsSetUpdate();
    }

    protected void handleRegionLose(String region) {
        handleAvailableRegionsSetUpdate();
    }

    protected void handleAvailableRegionsSetUpdate() {
        log.info(
                "List of {} availability zones for {} has changed to: {}",
                formatConnectMode(), switchId, formatAvailableRegionsSet());
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

    protected void reportNotificationDrop(InfoData notification) {
        log.debug("Drop speaker switch {} notification: {}", switchId, notification);
    }

    protected String formatConnectMode() {
        return isReadWriteMode() ? "RW" : "RO";
    }

    protected String formatAvailableRegionsSet() {
        return "{" + availableInRegions.stream()
                .sorted()
                .collect(Collectors.joining(", ")) + "}";
    }
}
