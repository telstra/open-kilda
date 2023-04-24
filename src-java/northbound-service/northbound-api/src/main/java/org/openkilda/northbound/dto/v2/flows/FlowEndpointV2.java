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

package org.openkilda.northbound.dto.v2.flows;

import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@JsonNaming(value = SnakeCaseStrategy.class)
@EqualsAndHashCode(callSuper = true)
public class FlowEndpointV2 extends BaseFlowEndpointV2 {
    @JsonProperty("detect_connected_devices")
    private DetectConnectedDevicesV2 detectConnectedDevices;

    @Builder
    @JsonCreator
    public FlowEndpointV2(@JsonProperty("switch_id") SwitchId switchId,
                          @JsonProperty("port_number") Integer portNumber,
                          @JsonProperty("vlan_id") int vlanId,
                          @JsonProperty("inner_vlan_id") int innerVlanId,
                          @JsonProperty("detect_connected_devices") DetectConnectedDevicesV2 detectConnectedDevices) {
        super(switchId, portNumber, vlanId, innerVlanId);
        setDetectConnectedDevices(detectConnectedDevices);
    }

    public FlowEndpointV2(
            SwitchId switchId, Integer portNumber, int vlanId, DetectConnectedDevicesV2 connectedDevices) {
        this(switchId, portNumber, vlanId, 0, connectedDevices);
    }

    public FlowEndpointV2(SwitchId switchId, Integer portNumber, int vlanId) {
        this(switchId, portNumber, vlanId, 0, null);
    }

    public FlowEndpointV2(SwitchId switchId, Integer portNumber, int vlanId, int innerVlanId) {
        this(switchId, portNumber, vlanId, innerVlanId, null);
    }

    /**
     * Sets detectConnectedDevices field.
     */
    public void setDetectConnectedDevices(DetectConnectedDevicesV2 detectConnectedDevices) {
        if (detectConnectedDevices == null) {
            this.detectConnectedDevices = new DetectConnectedDevicesV2();
        } else {
            this.detectConnectedDevices = detectConnectedDevices;
        }
    }

    public static class FlowEndpointV2Builder extends BaseFlowEndpointV2Builder {
        FlowEndpointV2Builder() {
            super();
        }
    }
}

