/* Copyright 2019 Telstra Open Source
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

package org.openkilda.messaging.command.flow;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.model.FlowDto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class FlowRequest extends CommandData {

    @JsonProperty(Utils.PAYLOAD)
    private FlowDto payload;

    @JsonProperty("type")
    private Type type;

    public FlowRequest(@JsonProperty(Utils.PAYLOAD) FlowDto payload,
                       @JsonProperty("type") Type type) {
        this.payload = payload;
        this.type = type;
    }

    public enum Type {
        CREATE,
        READ,
        UPDATE,
        DELETE,
        REROUTE
    }
}
