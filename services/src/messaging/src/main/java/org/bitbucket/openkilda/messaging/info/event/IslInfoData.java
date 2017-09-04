package org.bitbucket.openkilda.messaging.info.event;

import static com.google.common.base.MoreObjects.toStringHelper;

import com.fasterxml.jackson.annotation.JsonCreator;
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
@JsonPropertyOrder({
        "message_type",
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
     * Port speed.
     */
    @JsonProperty("speed")
    private Long speed;

    /**
     * Available bandwidth.
     */
    @JsonProperty("available_bandwidth")
    private Long availableBandwidth;

    /**
     * Default constructor.
     */
    public IslInfoData() {
    }

    /**
     * Instance constructor.
     *
     * @param latency latency
     * @param path    path
     * @param speed   port speed
     * @param state   isl discovery result
     */
    @JsonCreator
    public IslInfoData(@JsonProperty("latency_ns") long latency,
                       @JsonProperty("path") List<PathNode> path,
                       @JsonProperty("speed") Long speed,
                       @JsonProperty("state") IslChangeType state,
                       @JsonProperty("available_bandwidth") Long availableBandwidth) {
        this.latency = latency;
        this.path = path;
        this.speed = speed;
        this.state = state;
        this.availableBandwidth = availableBandwidth;
        this.id = String.format("%s_%s", path.get(0).getSwitchId(), String.valueOf(path.get(0).getPortNo()));
    }

    /**
     * Gets port speed.
     *
     * @return port speed
     */
    public Long getSpeed() {
        return speed;
    }

    /**
     * Sets port speed.
     *
     * @param speed port speed
     */
    public void setSpeed(Long speed) {
        this.speed = speed;
    }

    /**
     * Gets available bandwidth.
     *
     * @return available bandwidth
     */
    public Long getAvailableBandwidth() {
        return availableBandwidth;
    }

    /**
     * Sets available bandwidth.
     *
     * @param availableBandwidth available bandwidth
     */
    public void setAvailableBandwidth(Long availableBandwidth) {
        this.availableBandwidth = availableBandwidth;
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
        return Objects.hash(latency, path, speed, state);
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
