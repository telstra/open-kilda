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

package org.openkilda.messaging.info.flow;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.payload.history.FlowEventPayload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Value;

import java.util.List;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@Value
public class FlowHistoryData extends InfoData {
    @JsonProperty(Utils.PAYLOAD)
    private List<FlowEventPayload> payload;

    public FlowHistoryData(@JsonProperty(Utils.PAYLOAD) List<FlowEventPayload> payload) {
        this.payload = payload;
    }
}
