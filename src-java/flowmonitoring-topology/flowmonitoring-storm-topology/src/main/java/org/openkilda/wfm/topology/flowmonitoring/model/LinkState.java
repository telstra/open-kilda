/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.flowmonitoring.model;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.Instant;

@Data
public class LinkState {

    private Duration rttLatency;
    private Instant rttTimestamp;
    private Duration oneWayLatency;

    @Builder
    public LinkState(long rttLatency, Instant rttTimestamp, long oneWayLatency) {
        this.rttLatency = Duration.ofNanos(rttLatency);
        this.rttTimestamp = rttTimestamp;
        this.oneWayLatency = Duration.ofNanos(oneWayLatency);
    }

    /**
     * Get most recent latency for the link.
     */
    public Duration getLatency(Instant now, Duration rttLatencyExpiration) {
        if (rttTimestamp == null || now.isAfter(rttTimestamp.plus(rttLatencyExpiration))) {
            return oneWayLatency;
        }
        return rttLatency;
    }

    public void setRttLatency(long rttLatency) {
        this.rttLatency = Duration.ofNanos(rttLatency);
    }

    public void setOneWayLatency(long oneWayLatency) {
        this.oneWayLatency = Duration.ofNanos(oneWayLatency);
    }
}
