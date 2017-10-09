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

import static com.google.common.base.MoreObjects.toStringHelper;

import org.openkilda.messaging.Utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.Objects;

/**
 * Flow endpoint representation class.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "switch-id",
        "port-id",
        "vlan-id"})
public class FlowEndpointPayload implements Serializable {
    /**
     * The constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The switch id.
     */
    @JsonProperty("switch-id")
    private String switchId;

    /**
     * The port id.
     */
    @JsonProperty("port-id")
    private Integer portId;

    /**
     * The port id.
     */
    @JsonProperty("vlan-id")
    private Integer vlanId;

    /**
     * Instance constructor.
     *
     * @param switchId switch id
     * @param portId   port id
     * @param vlanId   vlan id
     */
    @JsonCreator
    public FlowEndpointPayload(@JsonProperty("switch-id") String switchId,
                               @JsonProperty("port-id") Integer portId,
                               @JsonProperty("vlan-id") Integer vlanId) {
        setSwitchId(switchId);
        setPortId(portId);
        setVlanId(vlanId);
    }

    /**
     * Returns switch id.
     *
     * @return switch id
     */
    public String getSwitchId() {
        return switchId;
    }

    /**
     * Sets switch id.
     *
     * @param switchId switch id
     */
    public void setSwitchId(String switchId) {
        if (switchId == null) {
            throw new IllegalArgumentException("need to set switch id");
        } else if (!Utils.validateSwitchId(switchId)) {
            throw new IllegalArgumentException("need to set valid value for switch id");
        }

        this.switchId = switchId;
    }

    /**
     * Returns port id.
     *
     * @return port id
     */
    public Integer getPortId() {
        return portId;
    }

    /**
     * Sets port id.
     *
     * @param portId port id
     */
    public void setPortId(Integer portId) {
        if (portId == null) {
            throw new IllegalArgumentException("need to set port id");
        } else if (portId < 0L) {
            throw new IllegalArgumentException("need to set positive value for port id");
        }
        this.portId = portId;
    }

    /**
     * Returns vlan id.
     *
     * @return vlan id
     */
    public Integer getVlanId() {
        return vlanId;
    }

    /**
     * Sets vlan id.
     *
     * @param vlanId vlan id
     */
    public void setVlanId(Integer vlanId) {
        if (vlanId == null) {
            this.vlanId = 0;
        } else if (Utils.validateVlanRange(vlanId)) {
            this.vlanId = vlanId;
        } else {
            throw new IllegalArgumentException("need to set valid value for vlan id");
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

        if (obj == null || !(obj instanceof FlowEndpointPayload)) {
            return false;
        }

        FlowEndpointPayload that = (FlowEndpointPayload) obj;
        return Objects.equals(getSwitchId(), that.getSwitchId())
                && Objects.equals(getPortId(), that.getPortId())
                && Objects.equals(getVlanId(), that.getVlanId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(switchId, portId, vlanId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("switch-id", switchId)
                .add("port-id", portId)
                .add("vlan-id", vlanId)
                .toString();
    }
}
