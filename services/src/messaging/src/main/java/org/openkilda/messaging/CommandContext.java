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

package org.openkilda.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.io.Serializable;
import java.util.UUID;

@Value
public class CommandContext implements Serializable {

    @JsonProperty
    private final String correlationId;

    @JsonProperty
    private final long createTime;

    public CommandContext() {
        this(UUID.randomUUID().toString());
    }

    public CommandContext(Message message) {
        this(message.getCorrelationId(), message.getTimestamp());
    }

    public CommandContext(String correlationId) {
        this(correlationId, System.currentTimeMillis());
    }

    protected CommandContext(String correlationId, long createTime) {
        this.correlationId = correlationId;
        this.createTime = System.currentTimeMillis();
    }
}
