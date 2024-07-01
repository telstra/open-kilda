/* Copyright 2017 Telstra Open Source
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

package org.openkilda.messaging.info.flow;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.TIMESTAMP;

import org.openkilda.messaging.info.InfoMessage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;



/**
 * Represents a flow reroute northbound response.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@Value
@EqualsAndHashCode(callSuper = false)
@ToString
public class FlowRerouteFlushResponse extends InfoMessage {

    private static final long serialVersionUID = 1L;

    @JsonProperty("flushed")
    private boolean flushed;

    @JsonCreator
    public FlowRerouteFlushResponse(
            @JsonProperty(TIMESTAMP) final long timestamp,
            @JsonProperty(CORRELATION_ID) final String correlationId,
            @JsonProperty("flow_data") FlowResponse flowData) {
        super(flowData, timestamp, correlationId);
        this.flushed = true;
    }
}
