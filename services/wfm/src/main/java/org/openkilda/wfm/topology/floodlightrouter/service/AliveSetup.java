/* Copyright 2019 Telstra Open Source
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class AliveSetup {
    private final Clock localClock;
    private final Clock aliveExpireClock;
    private final Clock aliveIntervalClock;

    public AliveSetup(long aliveTimeout, long aliveInterval) {
        this(Clock.systemUTC(), aliveTimeout, aliveInterval);
    }

    AliveSetup(Clock systemClock, long aliveTimeout, long aliveInterval) {
        localClock = systemClock;
        aliveExpireClock = Clock.offset(localClock, Duration.ofSeconds(aliveTimeout));
        aliveIntervalClock = Clock.offset(localClock, Duration.ofSeconds(aliveInterval));
    }

    public boolean isAlive(AliveMarker marker) {
        return marker.getAliveExpireAt().isAfter(localClock.instant());
    }

    public boolean isAliveRequestRequired(AliveMarker marker) {
        return marker.getNextAliveRequestAt().isBefore(localClock.instant());
    }

    public Duration timeFromLastSeen(AliveMarker marker) {
        return Duration.between(marker.getLastSeenActivity(), localClock.instant());
    }

    public AliveMarker makeZeroMarker() {
        Instant timeNow = localClock.instant();
        return new AliveMarker(timeNow, timeNow, aliveExpireClock.instant(), null);
    }

    public AliveMarker makeMarker(Long remoteTimeMillis) {
        return new AliveMarker(localClock.instant(), aliveIntervalClock.instant(), aliveExpireClock.instant(),
                               measureRemoteOffset(remoteTimeMillis));
    }

    private Duration measureRemoteOffset(Long remoteTimeMillis) {
        if (remoteTimeMillis != null) {
            return Duration.ofMillis(localClock.instant().toEpochMilli() - remoteTimeMillis);
        }
        return null;
    }
}
