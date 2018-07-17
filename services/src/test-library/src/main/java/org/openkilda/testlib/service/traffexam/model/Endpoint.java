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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ConsumerEndpoint.class, name = "consumer"),
        @JsonSubTypes.Type(value = ProducerEndpoint.class, name = "producer")})
public abstract class Endpoint extends HostResource {
    @JsonProperty("bind_address")
    private final UUID bindAddressId;

    public Endpoint(UUID id, UUID bindAddressId) {
        super(id);
        this.bindAddressId = bindAddressId;
    }

    public UUID getBindAddressId() {
        return bindAddressId;
    }
}
