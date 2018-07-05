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

import org.openkilda.messaging.Utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.io.Serializable;

@Value
public class PingReport implements Serializable {
    @JsonProperty(Utils.FLOW_ID)
    private String flowId;

    @JsonProperty("status")
    private State state;

    @JsonCreator
    public PingReport(
            @JsonProperty(Utils.FLOW_ID) String flowId,
            @JsonProperty("status") State state) {
        this.flowId = flowId;
        this.state = state;
    }

    public enum State {
        OPERATIONAL,
        FAILED,
        UNRELIABLE
    }
}
