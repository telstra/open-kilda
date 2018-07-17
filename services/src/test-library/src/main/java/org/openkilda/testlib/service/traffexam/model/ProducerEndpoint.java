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

package org.openkilda.testlib.service.traffexam.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.UUID;

@Accessors(chain = true)
@Getter
@Setter
public class ProducerEndpoint extends Endpoint {
    @JsonDeserialize(using = BandwidthJsonDeserializer.class)
    @JsonSerialize(using = BandwidthJsonSerializer.class)
    private Bandwidth bandwidth = null;

    @JsonProperty("burst_pkt")
    private int burstPkt = 0;

    @JsonDeserialize(using = TimeLimitJsonDeserializer.class)
    @JsonSerialize(using = TimeLimitJsonSerializer.class)
    private TimeLimit time = null;

    @JsonProperty("use_udp")
    private boolean useUdp = false;

    @JsonProperty("remote_address")
    private final EndpointAddress targetAddress;

    public ProducerEndpoint(EndpointAddress targetAddress) {
        this(null, null, targetAddress);
    }

    public ProducerEndpoint(UUID bindAddressId, EndpointAddress targetAddress) {
        this(null, bindAddressId, targetAddress);
    }

    @JsonCreator
    public ProducerEndpoint(
            @JsonProperty("idnr") UUID id,
            @JsonProperty("bind_address") UUID bindAddressId,
            @JsonProperty("remote_address") EndpointAddress targetAddress) {
        super(id, bindAddressId);
        this.targetAddress = targetAddress;
    }
}
