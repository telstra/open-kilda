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

package org.openkilda.messaging.payload.flow;

import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.io.Serializable;

/**
 * Flow path node NB representation class.
 */
@Value
public class PathNodePayload implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Switch id.
     */
    @JsonProperty("switch_id")
    private SwitchId switchId;

    /**
     * Input port number.
     */
    @JsonProperty("input_port")
    private int inputPort;

    /**
     * Output port number.
     */
    @JsonProperty("output_port")
    private int outputPort;

    @JsonCreator
    public PathNodePayload(
            @JsonProperty("switch_id") SwitchId switchId,
            @JsonProperty("input_port") int inputPort,
            @JsonProperty("output_port") int outputPort) {
        this.switchId = switchId;
        this.inputPort = inputPort;
        this.outputPort = outputPort;
    }
}
