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

import org.openkilda.messaging.AliveResponse;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
public class FloodlightTracker {
    private final RegionMonitorCarrier carrier;
    private final AliveSetup aliveSetup;

    @VisibleForTesting
    protected Map<String, SpeakerStatus> floodlightStatus = new HashMap<>();

    public FloodlightTracker(
            RegionMonitorCarrier carrier, Set<String> floodlights, long aliveTimeout, long aliveInterval) {
        this(carrier, floodlights, new AliveSetup(aliveTimeout, aliveInterval));
    }

    FloodlightTracker(RegionMonitorCarrier carrier, Set<String> floodlights, AliveSetup aliveSetup) {
        this.carrier = carrier;
        this.aliveSetup = aliveSetup;

        for (String region : floodlights) {
            SpeakerStatus fl = new SpeakerStatus(region, aliveSetup.makeZeroMarker());
            floodlightStatus.put(region, fl);
        }
    }

    /**
     * Handle alive response.
     */
    public void handleAliveResponse(String region, AliveResponse response) {
        if (response.getFailedMessages() > 0) {
            log.info(
                    "Receive alive response with not zero(=={}) transport errors count - force network sync",
                    response.getFailedMessages());
            carrier.emitNetworkDumpRequest(region);
        }
    }

    /**
     * Updates floodlight availability status according to last received alive response.
     */
    public void handleAliveExpiration() {
        for (SpeakerStatus status : floodlightStatus.values()) {
            if (aliveSetup.isAlive(status.getAliveMarker())) {
                continue;
            }

            if (status.markInactive()) {
                log.error("Floodlight region {} is marked as inactive no alive responses for {}", status.getRegion(),
                        aliveSetup.timeFromLastSeen(status.getAliveMarker()));
                carrier.emitRegionBecameUnavailableNotification(status.getRegion());
            }
        }
    }

    /**
     * Register alive evidences (any received message from specific region).
     */
    public void handleAliveEvidence(String region, long timestamp) {
        log.debug("Handling alive evidence for region {}", region);
        SpeakerStatus status = floodlightStatus.get(region);

        boolean isActiveNow = status.isActive();
        status.markActive(aliveSetup.makeMarker(timestamp));

        if (!isActiveNow) {
            log.info("Region {} is went online (force network sync)", region);
            carrier.emitNetworkDumpRequest(region);
        }
    }

    /**
     * Emit alive request for regions stays quiet(do not produce any message) for some time.
     */
    public void emitAliveRequests() {
        for (SpeakerStatus status : floodlightStatus.values()) {
            if (aliveSetup.isAliveRequestRequired(status.getAliveMarker())) {
                carrier.emitSpeakerAliveRequest(status.getRegion());
            }
        }
    }
}
