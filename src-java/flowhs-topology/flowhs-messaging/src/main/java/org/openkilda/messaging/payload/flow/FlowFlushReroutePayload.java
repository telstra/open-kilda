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

package org.openkilda.messaging.payload.flow;

import org.openkilda.messaging.Utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Flow reroute representation class.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowFlushReroutePayload implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The id of the flow.
     */
    @JsonProperty(Utils.FLOW_ID)
    protected String id;

    @JsonProperty("rerouted")
    private boolean rerouted;

    @JsonCreator
    public FlowFlushReroutePayload(
            @JsonProperty(value = Utils.FLOW_ID) String id,
            @JsonProperty(value = "rerouted") boolean rerouted) {
        this.id = id;
        this.rerouted = rerouted;
    }
}
