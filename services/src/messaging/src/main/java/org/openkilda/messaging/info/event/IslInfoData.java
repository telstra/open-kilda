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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Objects;

/**
 * Defines the payload payload of a Message representing an isl info.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IslInfoData extends PathInfoData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Instance id.
     */
    @JsonProperty("id")
    protected final String id;

    /**
     * Port speed.
     */
    @JsonProperty("speed")
    private final long speed;

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

    @JsonProperty("time_create")
    private final Long timeCreateMillis;

    @JsonProperty("time_modify")
    private final Long timeModifyMillis;

    /**
     * Copy constructor.
     *
     * @param that {@link IslInfoData} instance
     */
    public IslInfoData(IslInfoData that) {
        this(
                that.getLatency(),
                that.getPath(),
                that.getSpeed(),
                that.getAvailableBandwidth(),
                that.getState(),
                that.getTimeCreateMillis(),
                that.getTimeModifyMillis());
    }

    /**
     * Simple constructor for an ISL with only path and state.
     * @param path path of ISL.
     * @param state current state.
     */
    public IslInfoData(List<PathNode> path, IslChangeType state) {
        this(-1, path, 0, 0, state, null, null);
    }

    public IslInfoData(long latency, List<PathNode> path, long speed, IslChangeType state, long availableBandwidth) {
        this(latency, path, speed, availableBandwidth, state, null, null);
    }

    @JsonCreator
    public IslInfoData(@JsonProperty("latency_ns") long latency,
                       @JsonProperty("path") List<PathNode> path,
                       @JsonProperty("speed") long speed,
                       @JsonProperty("available_bandwidth") long availableBandwidth,
                       @JsonProperty("state") IslChangeType state,
                       @JsonProperty("time_create") Long timeCreateMillis,
                       @JsonProperty("time_modify") Long timeModifyMillis) {
        super(latency, path);

        this.speed = speed;
        this.availableBandwidth = availableBandwidth;
        this.state = state;
        this.timeCreateMillis = timeCreateMillis;
        this.timeModifyMillis = timeModifyMillis;
        this.id = String.format("%s_%d", path.get(0).getSwitchId(), path.get(0).getPortNo());
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
     * Sets isl state.
     *
     * @param state isl state to set
     */
    public void setState(IslChangeType state) {
        this.state = state;
    }

    /**
     * Check whether source and destination switch are the same.
     * @return true if ISL is self looped.
     */
    @JsonIgnore
    public boolean isSelfLooped() {
        PathNode source = this.getPath().get(0);
        PathNode destination = this.getPath().get(1);
        return Objects.equals(source.getSwitchId(), destination.getSwitchId());
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
