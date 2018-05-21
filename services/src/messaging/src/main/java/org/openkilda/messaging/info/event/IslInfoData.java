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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.List;
import java.util.Objects;

/**
 * Defines the payload payload of a Message representing an isl info.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({
        "id",
        "latency_ns",
        "path",
        "speed",
        "available_bandwidth",
        "state"})
public class IslInfoData extends PathInfoData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Instance id.
     */
    @JsonProperty("id")
    protected String id;

    /**
     * Port speed.
     */
    @JsonProperty("speed")
    private long speed;

    /**
     * Available bandwidth.
     */
    @JsonProperty("available_bandwidth")
    private long availableBandwidth;

    /**
     * Isl state.
     */
    @JsonProperty("state")
    protected IslChangeType state;

    /**
     * Default constructor.
     */
    public IslInfoData() {
    }

    /**
     * Copy constructor.
     *
     * @param that {@link IslInfoData} instance
     */
    public IslInfoData(IslInfoData that) {
        this.id = that.id;
        this.path = that.path;
        this.speed = that.speed;
        this.state = that.state;
        this.latency = that.latency;
        this.availableBandwidth = that.availableBandwidth;
    }

    /**
     * Instance constructor.
     *
     * @param latency            latency
     * @param path               path
     * @param speed              port speed
     * @param state              isl discovery result
     * @param availableBandwidth isl available bandwidth
     */
    @JsonCreator
    public IslInfoData(@JsonProperty("latency_ns") long latency,
                       @JsonProperty("path") List<PathNode> path,
                       @JsonProperty("speed") long speed,
                       @JsonProperty("state") IslChangeType state,
                       @JsonProperty("available_bandwidth") long availableBandwidth) {
        this.latency = latency;
        this.path = path;
        this.speed = speed;
        this.state = state;
        this.availableBandwidth = availableBandwidth;
        this.id = String.format("%s_%s", path.get(0).getSwitchId(), String.valueOf(path.get(0).getPortNo()));
    }

    /**
     * Simple constructor for an ISL with only path and state.
     * @param path path of ISL.
     * @param state current state.
     */
    public IslInfoData(List<PathNode> path, IslChangeType state) {
        this.path = path;
        this.state = state;
        this.id = String.format("%s_%s", path.get(0).getSwitchId(), String.valueOf(path.get(0).getPortNo()));
    }

    /**
     * Returns id.
     *
     * @return id
     */
    public String getId() {
        return id;
    }

    /**
     * Sets id.
     *
     * @param id id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Gets port speed.
     *
     * @return port speed
     */
    public long getSpeed() {
        return speed;
    }

    /**
     * Sets port speed.
     *
     * @param speed port speed
     */
    public void setSpeed(long speed) {
        this.speed = speed;
    }

    /**
     * Gets available bandwidth.
     *
     * @return available bandwidth
     */
    public long getAvailableBandwidth() {
        return availableBandwidth;
    }

    /**
     * Sets available bandwidth.
     *
     * @param availableBandwidth available bandwidth
     */
    public void setAvailableBandwidth(long availableBandwidth) {
        this.availableBandwidth = availableBandwidth;
    }

    /**
     * Returns isl state.
     *
     * @return isl state
     */
    public IslChangeType getState() {
        return state;
    }

    /**
     * Sets isl state.
     *
     * @param state isl state to set
     */
    public void setState(IslChangeType state) {
        this.state = state;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("latency_ns", latency)
                .add("path", path)
                .add("speed", speed)
                .add("available_bandwidth", availableBandwidth)
                .add("state", state)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(latency, path, speed, availableBandwidth, state);
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

        IslInfoData that = (IslInfoData) object;
        return Objects.equals(getLatency(), that.getLatency())
                && Objects.equals(getPath(), that.getPath())
                && Objects.equals(getSpeed(), that.getSpeed())
                && Objects.equals(getAvailableBandwidth(), that.getAvailableBandwidth())
                && Objects.equals(getState(), that.getState());
    }
}
