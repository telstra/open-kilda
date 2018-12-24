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

import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.switches.UnmanagedSwitchNotification;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.floodlightrouter.bolts.RouterBolt.RouterMessageSender;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class FloodlightTracker {
    @VisibleForTesting
    protected ConcurrentMap<SwitchId, String> switchRegionMap = new ConcurrentHashMap<>();
    @VisibleForTesting
    protected ConcurrentMap<String, FloodlightInstance> floodlightStatus = new ConcurrentHashMap<>();
    private long aliveTimeout;

    public FloodlightTracker(Set<String> floodlights, long aliveTimeout) {
        this.aliveTimeout = TimeUnit.SECONDS.toMillis(aliveTimeout);
        for (String region : floodlights) {
            FloodlightInstance fl = new FloodlightInstance(region);
            floodlightStatus.put(region, fl);
        }
    }

    /**
     * Update switch region mapping.
     * @param switchId target switch
     * @param region target region
     */
    public void updateSwitchRegion(SwitchId switchId, String region) {
        switchRegionMap.put(switchId, region);
    }

    /**
     * Return region for switch.
     * @param switchId target switch
     * @return region of the switch
     */
    public String lookupRegion(SwitchId switchId) {
        return switchRegionMap.get(switchId);
    }

    /**
     * Return active available regions.
     * @return set of active regions
     */
    public Set<String> getActiveRegions() {
        return getRegions(true);
    }

    /**
     * Return inactive available regions.
     * @return set of active regions
     */
    public Set<String> getInActiveRegions() {
        return getRegions(false);
    }

    /**
     * Updates floodlight availability status according to last received alive response.
     */
    public void checkTimeouts() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, FloodlightInstance> flEntry : floodlightStatus.entrySet()) {
            FloodlightInstance instance =  flEntry.getValue();
            if (instance.getLastAliveResponse() + aliveTimeout < now) {
                log.warn("Floodlight region {} is marked as inactive no alive responses for {}", instance.getRegion(),
                        TimeUnit.MILLISECONDS.toSeconds(now - instance.getLastAliveResponse()));
                instance.setAlive(false);
            }
        }
    }

    /**
     * Get switches under offline speaker.
     * @return list of switches
     */
    public List<SwitchId> getUnmanageableSwitches() {
        Set<String> inactiveRegions = getInActiveRegions();
        return switchRegionMap.entrySet().stream()
                                  .filter(switchIdStringEntry -> inactiveRegions.contains(
                                          switchIdStringEntry.getValue()))
                                  .map(Entry::getKey)
                                  .collect(Collectors.toList());
    }

    private Set<String> getRegions(boolean live) {
        Set<String> activeRegions = new HashSet<>();
        for (Map.Entry<String, FloodlightInstance> entry: floodlightStatus.entrySet()) {
            if (entry.getValue().isAlive() == live) {
                activeRegions.add(entry.getKey());
            }
        }
        return activeRegions;
    }

    /**
     * Handles alive response.
     * @return flag whether discovery needed or not
     */
    public boolean handleAliveResponse(String region, long timestamp) {
        log.debug("Handling alive response for region {}", region);
        FloodlightInstance instance = floodlightStatus.get(region);
        instance.setLastAliveResponse(timestamp);
        boolean needDiscovery = false;
        if (timestamp + aliveTimeout > System.currentTimeMillis()) {
            if (!instance.isAlive()) {
                log.info("Region {} is went online", region);
                needDiscovery = true;
            }
            instance.setAlive(true);
        } else {
            log.debug("Outdated alive response for region {}", region);
            instance.setAlive(false);
        }
        return needDiscovery;
    }

    /**
     * Notify consumers about unmanaged switches.
     * @param messageSender storm topology callback to handle transport.
     */
    public void handleUnmanagedSwitches(RouterMessageSender messageSender) {
        List<SwitchId> unmanagedSwitches = getUnmanageableSwitches();
        for (SwitchId sw : unmanagedSwitches) {
            log.debug("Sending unmanaged switch notification for {}", sw.getId());
            UnmanagedSwitchNotification notification = new UnmanagedSwitchNotification(sw);
            InfoMessage message = new InfoMessage(notification, System.currentTimeMillis(), UUID.randomUUID()
                    .toString());
            messageSender.send(message);
        }
    }
}
