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
import org.openkilda.messaging.info.event.PathInfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Value;

import java.io.Serializable;

/**
 * Flow path representation class.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@Value
public class FlowPathPayload implements Serializable {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The id of the flow.
     */
    @JsonProperty(Utils.FLOW_ID)
    protected String id;

    /**
     * The forward path of the flow.
     */
    @JsonProperty(Utils.FLOW_PATH)
    protected PathInfoData forwardPath;

    /**
     * The reverse path of the flow.
     */
    @JsonProperty("reverse")
    protected PathInfoData reversePath;

    @JsonCreator
    public FlowPathPayload(@JsonProperty(Utils.FLOW_ID) String id,
                           @JsonProperty(Utils.FLOW_PATH) PathInfoData forwardPath,
                           @JsonProperty("reverse") PathInfoData reversePath) {
        this.id = id;
        this.forwardPath = forwardPath;
        this.reversePath = reversePath;
    }
}
