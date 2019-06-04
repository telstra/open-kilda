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

package org.openkilda.messaging.model;

import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@EqualsAndHashCode
public class SwapFlowDto implements Serializable {

    private static final long serialVersionUID = -2430234458273123036L;

    @JsonProperty("flow_id")
    private String flowId;

    @JsonProperty("src_switch")
    private SwitchId sourceSwitch;

    @JsonProperty("dst_switch")
    private SwitchId destinationSwitch;

    @JsonProperty("src_port")
    private int sourcePort;

    @JsonProperty("dst_port")
    private int destinationPort;

    @JsonProperty("src_vlan")
    private int sourceVlan;

    @JsonProperty("dst_vlan")
    private int destinationVlan;

    /**
     * Constructs a dto for swap flow endpoints.
     *
     * @param flowId a flow id.
     * @param sourceSwitch a source switch id.
     * @param sourcePort a source port number.
     * @param sourceVlan a source vlan number.
     * @param destinationSwitch a destination switch id.
     * @param destinationPort a destination port number.
     * @param destinationVlan a destination vlan number.
     */
    @JsonCreator
    @Builder(toBuilder = true)
    public SwapFlowDto(@JsonProperty("flow_id") String flowId,
                       @JsonProperty("src_switch") SwitchId sourceSwitch,
                       @JsonProperty("src_port") int sourcePort,
                       @JsonProperty("src_vlan") int sourceVlan,
                       @JsonProperty("dst_switch") SwitchId destinationSwitch,
                       @JsonProperty("dst_port") int destinationPort,
                       @JsonProperty("dst_vlan") int destinationVlan) {
        this.flowId = flowId;
        this.sourceSwitch = sourceSwitch;
        this.sourcePort = sourcePort;
        this.sourceVlan = sourceVlan;
        this.destinationSwitch = destinationSwitch;
        this.destinationPort = destinationPort;
        this.destinationVlan = destinationVlan;
    }
}
