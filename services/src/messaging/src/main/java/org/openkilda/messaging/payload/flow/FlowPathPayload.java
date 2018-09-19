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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Flow path representation class.
 */
// TODO move into api module
@Data
@NoArgsConstructor
public class FlowPathPayload implements Serializable {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The id of the flow.
     */
    @JsonProperty("flowid")
    protected String id;

    /**
     * The forward path of the flow.
     */
    @JsonProperty("flowpath_forward")
    protected List<PathNodePayload> forwardPath;

    /**
     * The reverse path of the flow.
     */
    @JsonProperty("flowpath_reverse")
    protected List<PathNodePayload> reversePath;

    @JsonCreator
    public FlowPathPayload(@JsonProperty("flowid") String id,
                           @JsonProperty("flowpath_forward") List<PathNodePayload> forwardPath,
                           @JsonProperty("flowpath_reverse") List<PathNodePayload> reversePath) {
        this.id = id;
        this.forwardPath = forwardPath;
        this.reversePath = reversePath;
    }
}
