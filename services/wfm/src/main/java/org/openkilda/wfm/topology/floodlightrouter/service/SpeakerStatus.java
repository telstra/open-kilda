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

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@Slf4j
@Data
public class SpeakerStatus {
    private final String region;
    private final Duration remoteOffsetInfo;
    private final Duration remoteOffsetWarn;

    private AliveMarker aliveMarker;
    private boolean active = true;

    public SpeakerStatus(String region, Duration aliveTimeout, AliveMarker aliveMarker) {
        this.region = region;
        this.remoteOffsetWarn = aliveTimeout;
        this.remoteOffsetInfo = Duration.ofMillis((long) (aliveTimeout.toMillis() * .7));
        this.aliveMarker = aliveMarker;
    }

    public void markActive(AliveMarker marker) {
        active = true;
        aliveMarker = marker;
        if (aliveMarker.getRemoteOffsetMillis() != null) {
            reportRemoteOffset(aliveMarker.getRemoteOffsetMillis());
        }
    }

    public boolean markInactive() {
        boolean isActiveChanged = active;
        active = false;
        return isActiveChanged;
    }

    private void reportRemoteOffset(Duration remoteOffset) {
        String message = String.format("Time offset between SPEAKER and router is %s (region %s)", remoteOffset,
                                       region);
        if (remoteOffsetWarn.compareTo(remoteOffset) < 0) {
            log.warn(message);
        } else if (remoteOffsetInfo.compareTo(remoteOffset) < 0) {
            log.info(message);
        } else {
            log.debug(message);
        }
    }
}
