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
    public FlowEndpointPayload(@JsonProperty("switch-id") SwitchId switchId,
                               @JsonProperty("port-id") Integer portId,
                               @JsonProperty("vlan-id") Integer vlanId) {
        super(switchId, portId);
        setVlanId(vlanId);
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
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        FlowEndpointPayload that = (FlowEndpointPayload) obj;
        return new EqualsBuilder()
                .appendSuper(super.equals(obj))
                .append(vlanId, that.vlanId)
                .isEquals();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(getSwitchDpId(), getPortId(), vlanId);
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
                .toString();
    }
}
