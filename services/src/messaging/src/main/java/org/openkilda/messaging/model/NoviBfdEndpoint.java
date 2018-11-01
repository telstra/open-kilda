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
import lombok.Data;

import java.io.Serializable;

@Data
public abstract class NoviBfdEndpoint implements Serializable {
    @JsonProperty("target")
    protected final Switch target;

    @JsonProperty("remote")
    protected final Switch remote;

    @JsonProperty("physical-port-number")
    protected final int physicalPortNumber;

    @JsonProperty("udp-port-number")
    protected final int udpPortNumber;

    @JsonProperty("discriminator")
    protected final int discriminator;

    @JsonCreator
    protected NoviBfdEndpoint(
            @JsonProperty("target") Switch target,
            @JsonProperty("remote") Switch remote,
            @JsonProperty("physical-port-number") int physicalPortNumber,
            @JsonProperty("udp-port-number") int udpPortNumber,
            @JsonProperty("discriminator") int discriminator) {
        this.target = target;
        this.remote = remote;
        this.physicalPortNumber = physicalPortNumber;
        this.udpPortNumber = udpPortNumber;
        this.discriminator = discriminator;
    }
}
