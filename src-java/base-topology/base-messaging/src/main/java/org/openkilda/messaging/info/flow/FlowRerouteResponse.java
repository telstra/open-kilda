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

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.PathInfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Value;

/**
 * Represents a flow reroute northbound response.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@Value
public class FlowRerouteResponse extends InfoData {

    private static final long serialVersionUID = 1L;

    /**
     * The response payload.
     */
    @JsonProperty(Utils.PAYLOAD)
    protected PathInfoData payload;

    @JsonProperty("rerouted")
    private boolean rerouted;

    @JsonCreator
    public FlowRerouteResponse(
            @JsonProperty(Utils.PAYLOAD) PathInfoData payload,
            @JsonProperty("rerouted") boolean rerouted) {
        this.payload = payload;
        this.rerouted = rerouted;
    }
}
