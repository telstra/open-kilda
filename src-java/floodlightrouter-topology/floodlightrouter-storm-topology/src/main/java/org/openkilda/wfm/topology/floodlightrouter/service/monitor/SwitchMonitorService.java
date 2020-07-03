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
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchMonitorCarrier;

import com.google.common.annotations.VisibleForTesting;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

@Slf4j
public class SwitchMonitorService {
    private final Clock clock;

    @Getter(AccessLevel.PACKAGE)
    private final Duration garbageDelay = Duration.ofSeconds(300);

    private final SwitchMonitorCarrier carrier;

    private final Map<SwitchId, SwitchMonitorEntry> monitors = new HashMap<>();

    public SwitchMonitorService(Clock clock, SwitchMonitorCarrier carrier) {
        this.clock = clock;
        this.carrier = carrier;
    }

    /**
     * Handle timer tick.
     */
    public void handleTimerTick() {
        Iterator<Entry<SwitchId, SwitchMonitorEntry>> iter;
        Instant now = clock.instant();
        for (iter = monitors.entrySet().iterator(); iter.hasNext(); ) {
            Entry<SwitchId, SwitchMonitorEntry> entry = iter.next();
            Optional<Instant> becomeUnavailable = entry.getValue().getBecomeUnavailableAt();
            if (becomeUnavailable.isPresent()) {
                Duration delay = Duration.between(becomeUnavailable.get(), now);
                if (garbageDelay.compareTo(delay) < 0) {
                    log.debug("Monitor for {} become a garbage", entry.getKey());
                    iter.remove();
                }
            }
        }
    }

    /**
     * Handle region offline notification.
     */
    public void handleRegionOfflineNotification(String region) {
        log.debug("Got region \"{}\" OFFLINE notification", region);
        for (SwitchMonitorEntry entry : monitors.values()) {
            entry.handleRegionOfflineNotification(region);
        }
    }

    public void handleStatusUpdateNotification(SwitchInfoData notification, String region) {
        SwitchMonitorEntry entry = lookupOrCreateSwitchMonitor(notification.getSwitchId());
        entry.handleStatusUpdateNotification(notification, region);
    }

    public void handleNetworkDumpResponse(NetworkDumpSwitchData response, String region) {
        SwitchMonitorEntry entry = lookupOrCreateSwitchMonitor(response.getSwitchId());
        entry.handleNetworkDumpResponse(response, region);
    }

    @VisibleForTesting
    boolean isMonitorExists(SwitchId switchId) {
        return monitors.containsKey(switchId);
    }

    private SwitchMonitorEntry lookupOrCreateSwitchMonitor(SwitchId switchId) {
        return monitors.computeIfAbsent(switchId, key -> new SwitchMonitorEntry(carrier, clock, switchId));
    }
}
