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

package org.openkilda.messaging.command.flow;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Value;

import java.util.UUID;

@Value
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UniFlowVerificationRequest extends CommandData {
    @JsonProperty("packet_id")
    private final UUID packetId;

    @JsonProperty("timeout")
    private int timeout;

    @JsonProperty("flow_id")
    private String flowId;

    @JsonProperty("direction")
    private FlowDirection direction;

    @JsonProperty("source_switch")
    private SwitchId sourceSwitchId;

    @JsonProperty("source_port")
    private int sourcePort;

    @JsonProperty("dest_switch")
    private SwitchId destSwitchId;

    @JsonProperty("vlan")
    private int vlanId;

    @JsonCreator
    public UniFlowVerificationRequest(
            @JsonProperty("packet_id") UUID packetId,
            @JsonProperty("timeout") int timeout,
            @JsonProperty("flow_id") String flowId,
            @JsonProperty("direction") FlowDirection direction,
            @JsonProperty("source_switch") SwitchId sourceSwitchId,
            @JsonProperty("source_port") int sourcePort,
            @JsonProperty("dest_switch") SwitchId destSwitchId,
            @JsonProperty("vlan") int vlanId) {
        if (packetId == null) {
            packetId = UUID.randomUUID();
        }
        this.packetId = packetId;
        this.timeout = timeout;

        this.flowId = flowId;
        this.direction = direction;
        this.sourceSwitchId = sourceSwitchId;
        this.sourcePort = sourcePort;
        this.destSwitchId = destSwitchId;
        this.vlanId = vlanId;
    }

    public UniFlowVerificationRequest(FlowVerificationRequest request, Flow flow, FlowDirection direction) {
        this(
                null,
                request.getTimeout(),
                flow.getFlowId(),
                direction,
                flow.getSourceSwitch(), flow.getSourcePort(),
                flow.getDestinationSwitch(),
                flow.getSourceVlan());
    }
}
