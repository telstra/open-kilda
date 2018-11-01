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

package org.openkilda.messaging.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class NoviBfdSession extends NoviBfdEndpoint {
    @JsonProperty("logical-port-number")
    private int logicalPortNumber;

    @JsonProperty("interval-ms")
    private int intervalMs;

    @JsonProperty("multiplier")
    private short multiplier;

    @JsonProperty("keep-over-disconnect")
    private boolean keepOverDisconnect;

    @JsonCreator
    @Builder(toBuilder = true)
    public NoviBfdSession(
            @JsonProperty("target") Switch target,
            @JsonProperty("remote") Switch remote,
            @JsonProperty("physical-port-number") int physicalPortNumber,
            @JsonProperty("udp-port-number") int udpPortNumber,
            @JsonProperty("discriminator") int discriminator,
            @JsonProperty("logical-port-number") int logicalPortNumber,
            @JsonProperty("interval-ms") int intervalMs,
            @JsonProperty("multiplier") short multiplier,
            @JsonProperty("keep-over-disconnect") boolean keepOverDisconnect) {
        super(target, remote, physicalPortNumber, udpPortNumber, discriminator);
        this.logicalPortNumber = logicalPortNumber;
        this.intervalMs = intervalMs;
        this.multiplier = multiplier;
        this.keepOverDisconnect = keepOverDisconnect;
    }

    public enum Errors {
        SWITCH_RESPONSE_ERROR,
        NOVI_BFD_BAD_PORT_ERROR,
        NOVI_BFD_BAD_DISCRIMINATOR_ERROR,
        NOVI_BFD_BAD_INTERVAL_ERROR,
        NOVI_BFD_BAD_MULTIPLIER_ERROR,
        NOVI_BFD_DISCRIMINATOR_NOT_FOUND_ERROR,
        NOVI_BFD_INCOMPATIBLE_PKT_ERROR,
        NOVI_BFD_TOO_MANY_ERROR,
        NOVI_BFD_UNKNOWN_ERROR
    }
}
