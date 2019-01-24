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
public class NoviBfdCatch extends NoviBfdEndpoint {
    @JsonCreator
    @Builder(toBuilder = true)
    public NoviBfdCatch(
            @JsonProperty("target") Switch target,
            @JsonProperty("remote") Switch remote,
            @JsonProperty("physical-port-number") int physicalPortNumber,
            @JsonProperty("udp-port-number") int udpPortNumber,
            @JsonProperty("discriminator") int discriminator) {
        super(target, remote, physicalPortNumber, udpPortNumber, discriminator);
    }

    public enum Errors {
        SWITCH_RESPONSE_ERROR
    }
}
