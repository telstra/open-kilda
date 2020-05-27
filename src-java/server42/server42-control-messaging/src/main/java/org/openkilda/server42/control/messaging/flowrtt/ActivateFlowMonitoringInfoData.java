/* Copyright 2020 Telstra Open Source
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

package org.openkilda.server42.control.messaging.flowrtt;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

@Value
@ToString
@EqualsAndHashCode(callSuper = false)
public class ActivateFlowMonitoringInfoData extends InfoData {

    @JsonProperty(Utils.FLOW_ID)
    private String id;

    @NonNull
    @JsonProperty("source")
    private FlowEndpointPayload source;

    @NonNull
    @JsonProperty("destination")
    private FlowEndpointPayload destination;

    @Builder
    @JsonCreator
    public ActivateFlowMonitoringInfoData(@JsonProperty(Utils.FLOW_ID) String flowId,
                                          @JsonProperty("source") FlowEndpointPayload source,
                                          @JsonProperty("destination") FlowEndpointPayload destination) {
        this.id = flowId;
        this.source = source;
        this.destination = destination;
    }
}
