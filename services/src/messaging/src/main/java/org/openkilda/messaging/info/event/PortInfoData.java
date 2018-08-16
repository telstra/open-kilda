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

package org.openkilda.messaging.info.event;

import static com.google.common.base.MoreObjects.toStringHelper;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Objects;

/**
 * Defines the payload payload of a Message representing a port info.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "switch_id",
        "port_no",
        "max_capacity",
        "state"})
public class PortInfoData extends InfoData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Switch id.
     */
    @JsonProperty("switch_id")
    private SwitchId switchId;

    /**
     * Port number.
     */
    @JsonProperty("port_no")
    private int portNo;
    /**
     * Maximum capacity.
     */
    @JsonProperty("max_capacity")
    private Integer maxCapacity;

    /**
     * Port state.
     */
    @JsonProperty("state")
    private PortChangeType state;

    /**
     * Default constructor.
     */
    public PortInfoData() {
    }

    /**
     * Instance constructor.
     *
     * @param path path node
     */
    public PortInfoData(PathNode path) {
        this.switchId = path.getSwitchId();
        this.portNo = path.getPortNo();
        this.state = PortChangeType.OTHER_UPDATE;
    }

    /**
     * Instance constructor.
     *
     * @param switchId switch id
     * @param portNo   port number
     * @param state    port state
     */
    public PortInfoData(final SwitchId switchId, final int portNo, final PortChangeType state) {
        this.switchId = switchId;
        this.portNo = portNo;
        this.state = state;
    }

    /**
     * Instance constructor.
     *
     * @param switchId    switch id
     * @param portNo      port number
     * @param maxCapacity maximum capacity
     * @param state       port state
     */
    @JsonCreator
    public PortInfoData(@JsonProperty("switch_id") final SwitchId switchId,
                        @JsonProperty("port_no") final int portNo,
                        @JsonProperty("max_capacity") final Integer maxCapacity,
                        @JsonProperty("state") final PortChangeType state) {
        this.switchId = switchId;
        this.portNo = portNo;
        this.maxCapacity = maxCapacity;
        this.state = state;
    }

    /**
     * Returns switch id.
     *
     * @return switch id
     */
    public SwitchId getSwitchId() {
        return switchId;
    }

    /**
     * Sets switch id.
     *
     * @param switchId switch id to set
     */
    public void setSwitchId(final SwitchId switchId) {
        this.switchId = switchId;
    }

    /**
     * Returns port number.
     *
     * @return port number
     */
    public int getPortNo() {
        return portNo;
    }

    /**
     * Sets port number.
     *
     * @param portNo port number to set
     */
    public void setPortNo(final int portNo) {
        this.portNo = portNo;
    }

    /**
     * Returns maximum capacity.
     *
     * @return maximum capacity
     */
    public Integer getMaxCapacity() {
        return maxCapacity;
    }

    /**
     * Sets maximum capacity.
     *
     * @param maxCapacity maximum capacity to set
     */
    public void setMaxCapacity(final int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    /**
     * Returns port state.
     *
     * @return port state
     */
    public PortChangeType getState() {
        return state;
    }

    /**
     * Sets port state.
     *
     * @param state port state to set
     */
    public void setState(final PortChangeType state) {
        this.state = state;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("switch_id", switchId)
                .add("port_no", portNo)
                .add("max_capacity", maxCapacity)
                .add("state", state)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(switchId, portNo, maxCapacity, state);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        PortInfoData that = (PortInfoData) object;
        return Objects.equals(getSwitchId(), that.getSwitchId())
                && Objects.equals(getPortNo(), that.getPortNo())
                && Objects.equals(getMaxCapacity(), that.getMaxCapacity())
                && Objects.equals(getState(), that.getState());
    }
}
