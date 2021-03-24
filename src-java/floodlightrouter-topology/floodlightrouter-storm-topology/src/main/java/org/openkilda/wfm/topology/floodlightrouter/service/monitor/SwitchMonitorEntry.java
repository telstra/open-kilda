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

import org.openkilda.messaging.info.discovery.NetworkDumpSwitchData;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchMonitorCarrier;

import com.google.common.collect.ImmutableList;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class SwitchMonitorEntry {
    private final SwitchMonitorCarrier carrier;

    private final List<SwitchConnectMonitor> basicMonitors;

    public SwitchMonitorEntry(SwitchMonitorCarrier carrier, Clock clock, SwitchId switchId) {
        this.carrier = carrier;
        basicMonitors = ImmutableList.of(
                new SwitchReadOnlyConnectMonitor(carrier, clock, switchId),
                new SwitchReadWriteConnectMonitor(carrier, clock, switchId));
    }

    /**
     * Handle status update notification.
     */
    public void handleStatusUpdateNotification(SwitchInfoData notification, String region) {
        boolean isHandled = false;
        Iterator<SwitchConnectMonitor> iter = basicMonitors.iterator();
        while (! isHandled && iter.hasNext()) {
            isHandled = iter.next().handleSwitchStatusNotification(notification, region);
        }

        if (! isHandled) {
            carrier.networkStatusUpdateNotification(notification.getSwitchId(), notification);
        }
    }

    /**
     * Handle region offline notification.
     */
    public void handleRegionOfflineNotification(String region) {
        for (SwitchConnectMonitor entry : basicMonitors) {
            entry.handleRegionOfflineNotification(region);
        }
    }

    /**
     * Handle network dump entry/response.
     */
    public void handleNetworkDumpResponse(NetworkDumpSwitchData response, String region) {
        for (SwitchConnectMonitor entry : basicMonitors) {
            entry.handleNetworkDumpResponse(response, region);
        }
    }

    /**
     * Handle port status update notification.
     */
    public void handlePortStatusUpdateNotification(PortInfoData notification, String region) {
        for (SwitchConnectMonitor entry : basicMonitors) {
            entry.handlePortStatusUpdateNotification(notification, region);
        }
    }

    /**
     * Merge all basic monitors becomeUnavailableAt values.
     */
    public Optional<Instant> getBecomeUnavailableAt() {
        Instant result = null;
        for (SwitchConnectMonitor entry : basicMonitors) {
            Instant current = entry.getBecomeUnavailableAt();
            if (entry.isAvailable() || current == null) {
                return Optional.empty(); // at least one of connections are active
            }

            if (result == null || result.isBefore(current)) {
                result = current;
            }
        }

        return Optional.ofNullable(result);
    }
}
