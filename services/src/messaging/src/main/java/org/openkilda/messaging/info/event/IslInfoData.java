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

import org.openkilda.messaging.info.CacheTimeTag;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.Objects;

/**
 * Defines the payload payload of a Message representing an isl info.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IslInfoData extends CacheTimeTag {
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

    @JsonProperty("max_bandwidth")
    private long maxBandwidth;

    @JsonProperty("default_max_bandwidth")
    private long defaultMaxBandwidth;

    /**
     * Isl state.
     */
    @JsonProperty("state")
    protected IslChangeType state;

    @JsonProperty("actual_state")
    private IslChangeType actualState;

    @JsonProperty("time_create")
    private final Long timeCreateMillis;

    @JsonProperty("time_modify")
    private final Long timeModifyMillis;

    @JsonProperty("cost")
    private int cost;

    @JsonProperty("under_maintenance")
    private boolean underMaintenance;

    @JsonProperty("enable_bfd")
    private boolean enableBfd;

    @JsonProperty("bfd_session_status")
    private String bfdSessionStatus;

    private PathNode source;
    private PathNode destination;

    /**
     * Packet id.
     */
    @JsonProperty("packet_id")
    private Long packetId;

    /**
     * Copy constructor.
     *
     * @param that {@link IslInfoData} instance
     */
    public IslInfoData(IslInfoData that) {
        this(
                that.getSource(),
                that.getDestination(),
                that.getSpeed(),
                that.getAvailableBandwidth(),
                that.getMaxBandwidth(),
                that.getDefaultMaxBandwidth(),
                that.getState(),
                that.getActualState(),
                that.getCost(),
                that.getTimeCreateMillis(),
                that.getTimeModifyMillis(),
                that.isUnderMaintenance(),
                that.isEnableBfd(),
                that.getBfdSessionStatus(),
                that.getPacketId());
    }

    /**
     * Simple constructor for an ISL with only source/destination and state.
     */
    public IslInfoData(PathNode source, PathNode destination, IslChangeType state, boolean underMaintenance) {
        this(source, destination, 0, 0, 0, 0, state, null, 0, null, null, underMaintenance, false, null, null);
    }

    @Builder(toBuilder = true)
    @JsonCreator
    public IslInfoData(@JsonProperty("source") PathNode source,
                       @JsonProperty("destination") PathNode destination,
                       @JsonProperty("speed") long speed,
                       @JsonProperty("available_bandwidth") long availableBandwidth,
                       @JsonProperty("max_bandwidth")  long maxBandwidth,
                       @JsonProperty("default_max_bandwidth") long defaultMaxBandwidth,
                       @JsonProperty("state") IslChangeType state,
                       @JsonProperty("actual_state")  IslChangeType actualState,
                       @JsonProperty("cost") int cost,
                       @JsonProperty("time_create") Long timeCreateMillis,
                       @JsonProperty("time_modify") Long timeModifyMillis,
                       @JsonProperty("under_maintenance") boolean underMaintenance,
                       @JsonProperty("enable_bfd") boolean enableBfd,
                       @JsonProperty("bfd_session_status") String bfdSessionStatus,
                       @JsonProperty("packet_id") Long packetId) {
        this.source = source;
        this.destination = destination;
        this.speed = speed;
        this.availableBandwidth = availableBandwidth;
        this.maxBandwidth = maxBandwidth;
        this.defaultMaxBandwidth = defaultMaxBandwidth;
        this.state = state;
        this.actualState = actualState;
        this.cost = cost;
        this.timeCreateMillis = timeCreateMillis;
        this.timeModifyMillis = timeModifyMillis;
        this.id = String.format("%s_%d", source.getSwitchId(), source.getPortNo());
        this.underMaintenance = underMaintenance;
        this.enableBfd = enableBfd;
        this.bfdSessionStatus = bfdSessionStatus;
        this.packetId = packetId;
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
        return Objects.equals(source.getSwitchId(), destination.getSwitchId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(source, destination, speed, availableBandwidth, maxBandwidth, defaultMaxBandwidth,
                state, actualState, cost, underMaintenance, enableBfd, bfdSessionStatus);
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
        return Objects.equals(getSource(), that.getSource())
                && Objects.equals(getDestination(), that.getDestination())
                && Objects.equals(getSpeed(), that.getSpeed())
                && Objects.equals(getAvailableBandwidth(), that.getAvailableBandwidth())
                && Objects.equals(getMaxBandwidth(), that.getMaxBandwidth())
                && Objects.equals(getDefaultMaxBandwidth(), that.getDefaultMaxBandwidth())
                && Objects.equals(getState(), that.getState())
                && Objects.equals(getActualState(), that.getActualState())
                && Objects.equals(getCost(), that.getCost())
                && Objects.equals(isUnderMaintenance(), that.isUnderMaintenance())
                && Objects.equals(isEnableBfd(), that.isEnableBfd())
                && Objects.equals(getBfdSessionStatus(), that.getBfdSessionStatus());
    }
}
