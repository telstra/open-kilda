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
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import lombok.Getter;
import org.apache.commons.lang3.builder.EqualsBuilder;

import java.util.Objects;

/**
 * Flow endpoint representation class.
 * <p/>
 * TODO(surabujin): should be moved into org.openkilda.messaging.model package
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowEndpointPayload extends NetworkEndpoint {
    /**
     * The constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    @Getter
    @JsonProperty("vlan-id")
    private Integer vlanId;

    @Getter
    @JsonProperty("inner-vlan-id")
    private Integer innerVlanId;

    /**
     * Collect info about devices connected to endpoint.
     */
    @JsonProperty("detect-connected-devices")
    private DetectConnectedDevicesPayload detectConnectedDevices = new DetectConnectedDevicesPayload(false, false);

    public FlowEndpointPayload(
            SwitchId switchId, Integer portId, Integer vlanId, DetectConnectedDevicesPayload detectConnectedDevices) {
        this(switchId, portId, vlanId, 0, detectConnectedDevices);
    }

    /**
     * Instance constructor.
     *
     * @param switchId switch id
     * @param portId   port id
     * @param vlanId   vlan id
     */
    @JsonCreator
    public FlowEndpointPayload(@JsonProperty("switch-id") SwitchId switchId,
                               @JsonProperty("port-id") Integer portId,
                               @JsonProperty("vlan-id") Integer vlanId,
                               @JsonProperty("inner-vlan-id") Integer innerVlanId,
                               @JsonProperty("detect-connected-devices")
                                           DetectConnectedDevicesPayload detectConnectedDevices) {
        super(switchId, portId);
        setVlanId(vlanId);
        setInnerVlanId(innerVlanId);
        setDetectConnectedDevices(detectConnectedDevices);
    }

    public void setVlanId(Integer vlanId) {
        this.vlanId = verifyVlanId(vlanId);
    }

    public void setInnerVlanId(Integer innerVlanId) {
        this.innerVlanId = verifyVlanId(innerVlanId);
    }

    public DetectConnectedDevicesPayload getDetectConnectedDevices() {
        return detectConnectedDevices;
    }

    /**
     * Set detect connected devices flags.
     */
    public void setDetectConnectedDevices(DetectConnectedDevicesPayload detectConnectedDevices) {
        if (detectConnectedDevices == null) {
            this.detectConnectedDevices = new DetectConnectedDevicesPayload(false, false);
        } else {
            this.detectConnectedDevices = detectConnectedDevices;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        FlowEndpointPayload that = (FlowEndpointPayload) obj;
        return new EqualsBuilder()
                .appendSuper(super.equals(obj))
                .append(vlanId, that.vlanId)
                .append(detectConnectedDevices, that.detectConnectedDevices)
                .isEquals();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(getSwitchDpId(), getPortId(), vlanId, innerVlanId, detectConnectedDevices);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("switch-id", getSwitchDpId())
                .add("port-id", getPortId())
                .add("vlan-id", vlanId)
                .add("inner-vlan-id", innerVlanId)
                .add("detect-connected-devices", detectConnectedDevices)
                .toString();
    }

    private int verifyVlanId(Integer vlanId) {
        if (vlanId == null) {
            return 0;
        }

        if (!Utils.validateVlanRange(vlanId)) {
            throw new IllegalArgumentException("need to set valid value for vlan id");
        }
        return vlanId;
    }
}
