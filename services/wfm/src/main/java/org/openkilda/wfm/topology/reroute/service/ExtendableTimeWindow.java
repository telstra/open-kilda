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

package org.openkilda.wfm.topology.reroute.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class ExtendableTimeWindow {
    private final Clock clock;
    private final long maxDelay;
    private final long minDelay;
    private LocalDateTime firstEventTime;
    private LocalDateTime lastEventTime;

    public ExtendableTimeWindow(long minDelay, long maxDelay) {
        this(minDelay, maxDelay, Clock.systemDefaultZone());
    }

    /**
     * This constructor is used only for testing.
     *
     * @param minDelay the delay between 2 events.
     * @param maxDelay time window can't be increased more than that time.
     * @param clock the clock.
     */
    ExtendableTimeWindow(long minDelay, long maxDelay, Clock clock) {
        this.minDelay = minDelay;
        this.maxDelay = maxDelay;
        this.clock = clock;
    }

    /**
     * Register current event.
     */
    public void registerEvent() {
        LocalDateTime now = LocalDateTime.now(clock);
        if (firstEventTime == null) {
            firstEventTime = now;
        }
        lastEventTime = now;
    }

    /**
     * Clear state and starts new time window.
     */
    public void flush() {
        firstEventTime = null;
        lastEventTime = null;
    }

    /**
     * Return flag about time window is closed and can be flushed.
     *
     * @return true if time period for the last event is longer than minDelay or if time since first event longer
     *     than max time window size.
     */
    public boolean isTimeToFlush() {
        if (firstEventTime == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return lastEventTime.plus(minDelay, ChronoUnit.SECONDS).isBefore(now)
                || firstEventTime.plus(maxDelay, ChronoUnit.SECONDS).isBefore(now);
    }
}
