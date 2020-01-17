/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.floodlightrouter.service;

import org.openkilda.model.SwitchId;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
public class FloodlightTracker {
    private final AliveSetup aliveSetup;

    @VisibleForTesting
    protected Map<SwitchId, String> switchRegionMap = new HashMap<>();

    @VisibleForTesting
    protected Map<String, SpeakerStatus> floodlightStatus = new HashMap<>();

    public FloodlightTracker(Set<String> floodlights, long aliveTimeout, long aliveInterval) {
        this(floodlights, new AliveSetup(aliveTimeout, aliveInterval), Duration.ofSeconds(aliveTimeout));
    }

    FloodlightTracker(Set<String> floodlights, AliveSetup aliveSetup, Duration aliveTimeout) {
        this.aliveSetup = aliveSetup;

        for (String region : floodlights) {
            SpeakerStatus fl = new SpeakerStatus(region, aliveTimeout, aliveSetup.makeZeroMarker());
            floodlightStatus.put(region, fl);
        }
    }

    /**
     * Update switch region mapping.
     *
     * @param switchId target switch
     * @param region target region
     */
    public boolean updateSwitchRegion(SwitchId switchId, String region) {
        String previous = switchRegionMap.put(switchId, region);
        return !Objects.equals(region, previous);
    }

    /**
     * Return region for switch.
     *
     * @param switchId target switch
     * @return region of the switch
     */
    public String lookupRegion(SwitchId switchId) {
        return switchRegionMap.get(switchId);
    }

    /**
     * Updates floodlight availability status according to last received alive response.
     */
    public void handleAliveExpiration(MessageSender messageSender) {
        Set<String> becomeInactive = new HashSet<>();
        for (SpeakerStatus status : floodlightStatus.values()) {
            if (aliveSetup.isAlive(status.getAliveMarker())) {
                continue;
            }

            log.warn("Floodlight region {} is marked as inactive no alive responses for {}", status.getRegion(),
                     aliveSetup.timeFromLastSeen(status.getAliveMarker()));
            if (status.markInactive()) {
                becomeInactive.add(status.getRegion());
            }
        }

        emmitUnmanagedNotifications(messageSender, becomeInactive);
    }

    /**
     * Handles alive response.
     *
     * @return flag whether discovery needed or not
     */
    public boolean handleAliveResponse(String region, long timestamp) {
        log.debug("Handling alive response for region {}", region);
        SpeakerStatus status = floodlightStatus.get(region);

        boolean isActiveNow = status.isActive();
        status.markActive(aliveSetup.makeMarker(timestamp));

        if (!isActiveNow) {
            log.info("Region {} is went online", region);
            return true;
        }
        return false;
    }

    /**
     * Notify consumers about unmanaged switches.
     *
     * @param messageSender storm topology callback to handle transport.
     */
    private void emmitUnmanagedNotifications(MessageSender messageSender, Set<String> inactiveRegions) {
        for (Map.Entry<SwitchId, String> entry : switchRegionMap.entrySet()) {
            String region = entry.getValue();
            if (!inactiveRegions.contains(region)) {
                continue;
            }

            SwitchId sw = entry.getKey();
            log.debug("Sending unmanaged switch notification for {}", sw.getId());
            messageSender.emitSwitchUnmanagedNotification(sw);
        }
    }

    /**
     * Get regions that requires alive request.
     *
     * @return set of regions
     */
    public Set<String> getRegionsForAliveRequest() {
        Set<String> regions = new HashSet<>();
        for (SpeakerStatus status : floodlightStatus.values()) {
            if (aliveSetup.isAliveRequestRequired(status.getAliveMarker())) {
                regions.add(status.getRegion());
            }
        }
        return regions;
    }
}
