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
import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.switches.UnmanagedSwitchNotification;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.floodlightrouter.model.RegionMappingRemove;
import org.openkilda.wfm.topology.floodlightrouter.model.RegionMappingSet;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchMonitorCarrier;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.Iterator;
import java.util.Objects;

@Slf4j
public class SwitchReadWriteConnectMonitor extends SwitchConnectMonitor {
    private String activeRegion = null;

    public SwitchReadWriteConnectMonitor(SwitchMonitorCarrier carrier, Clock clock, SwitchId switchId) {
        super(carrier, clock, switchId);
    }

    @Override
    protected void handlePeriodicDump(NetworkDumpSwitchData switchData, String region) {
        super.handlePeriodicDump(switchData, region);
        if (Objects.equals(activeRegion, region)) {
            // proxy network dump for active region only
            carrier.switchStatusUpdateNotification(switchId, switchData);
        }
    }

    @Override
    protected void becomeAvailable(InfoData notification, String region) {
        super.becomeAvailable(notification, region);

        activeRegion = region;
        log.info("Set {} active region for {} to \"{}\"", formatConnectMode(), switchId, activeRegion);

        carrier.regionUpdateNotification(new RegionMappingSet(switchId, activeRegion, isReadWriteMode()));
        carrier.switchStatusUpdateNotification(switchId, notification);
    }

    @Override
    protected void becomeUnavailable(InfoData notification) {
        super.becomeUnavailable(notification);

        activeRegion = null;
        log.info("There is no any {} available regions for {} - switch is unavailable", formatConnectMode(), switchId);

        carrier.regionUpdateNotification(new RegionMappingRemove(switchId, null, isReadWriteMode()));
        carrier.switchStatusUpdateNotification(switchId, notification);
    }

    @Override
    protected void becomeUnavailableDueToRegionOffline() {
        becomeUnavailable(new UnmanagedSwitchNotification(switchId));
    }

    @Override
    protected void handleRegionLose(String region) {
        super.handleRegionLose(region);
        if (Objects.equals(activeRegion, region) && !availableInRegions.isEmpty()) {
            swapActiveRegion();
        }
    }

    @Override
    protected boolean isReadWriteMode() {
        return true;
    }

    @Override
    protected boolean isConnectNotification(SwitchInfoData notification) {
        return Objects.equals(SwitchChangeType.ACTIVATED, notification.getState());
    }

    @Override
    protected boolean isDisconnectNotification(SwitchInfoData notification) {
        return Objects.equals(SwitchChangeType.DEACTIVATED, notification.getState());
    }

    private void swapActiveRegion() {
        Iterator<String> iter = availableInRegions.iterator();
        if (iter.hasNext()) {
            String current = activeRegion;
            activeRegion = iter.next();
            log.info(
                    "Change {} active region for {} from \"{}\" to \"{}\"",
                    formatConnectMode(), switchId, current, activeRegion);
            carrier.regionUpdateNotification(new RegionMappingSet(switchId, activeRegion, isReadWriteMode()));
        } else {
            throw new IllegalStateException(String.format(
                    "Unable to determine \"next\" available region for switch %s, availability regions set is empty",
                    switchId));
        }
    }
}
