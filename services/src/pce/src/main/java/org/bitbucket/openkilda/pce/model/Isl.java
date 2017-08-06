package org.bitbucket.openkilda.pce.model;

import static com.google.common.base.MoreObjects.toStringHelper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents isl entity.
 */
@JsonSerialize
public class Isl implements Serializable {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Isl id.
     */
    @JsonProperty("isl_id")
    private String islId;

    /**
     * Isl source switch.
     */
    @JsonProperty("src_switch")
    private String sourceSwitch;

    /**
     * Isl destination switch.
     */
    @JsonProperty("dst_switch")
    private String destinationSwitch;

    /**
     * Isl source port.
     */
    @JsonProperty("src_port")
    private int sourcePort;

    /**
     * Isl destination port.
     */
    @JsonProperty("dst_port")
    private int destinationPort;

    /**
     * Latency value in nseconds.
     */
    @JsonProperty("latency")
    private long latency;

    /**
     * Isl speed.
     */
    @JsonProperty("speed")
    private long speed;

    /**
     * Isl available bandwidth.
     */
    @JsonProperty("available_bandwidth")
    private long availableBandwidth;

    /**
     * Default constructor.
     */
    public Isl() {
    }

    /**
     * Instance constructor.
     *
     * @param sourceSwitch       source switch
     * @param destinationSwitch  destination switch
     * @param sourcePort         source port
     * @param destinationPort    destination port
     * @param latency            latency
     * @param speed              port speed
     * @param availableBandwidth available bandwidth
     */
    @JsonCreator
    public Isl(@JsonProperty("src_switch") String sourceSwitch,
               @JsonProperty("dst_switch") String destinationSwitch,
               @JsonProperty("src_port") int sourcePort,
               @JsonProperty("dst_port") int destinationPort,
               @JsonProperty("latency") long latency,
               @JsonProperty("speed") long speed,
               @JsonProperty("available_bandwidth") int availableBandwidth) {
        this.sourceSwitch = sourceSwitch;
        this.destinationSwitch = destinationSwitch;
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.latency = latency;
        this.speed = speed;
        this.availableBandwidth = availableBandwidth;
        this.islId = String.format("%s_%d", sourceSwitch, sourcePort);
    }

    /**
     * Gets source switch.
     *
     * @return source switch
     */
    public String getSourceSwitch() {
        return sourceSwitch;
    }

    /**
     * Sets source switch.
     *
     * @param sourceSwitch source switch
     */
    public void setSourceSwitch(String sourceSwitch) {
        this.sourceSwitch = sourceSwitch;
    }

    /**
     * Gets source port.
     *
     * @return source port
     */
    public int getSourcePort() {
        return sourcePort;
    }

    /**
     * Sets source port.
     *
     * @param sourcePort source port
     */
    public void setSourcePort(int sourcePort) {
        this.sourcePort = sourcePort;
    }

    /**
     * Gets destination switch.
     *
     * @return destination switch
     */
    public String getDestinationSwitch() {
        return destinationSwitch;
    }

    /**
     * Sets destination switch.
     *
     * @param destinationSwitch destination switch
     */
    public void setDestinationSwitch(String destinationSwitch) {
        this.destinationSwitch = destinationSwitch;
    }

    /**
     * Gets destination port.
     *
     * @return destination port
     */
    public int getDestinationPort() {
        return destinationPort;
    }

    /**
     * Sets destination port.
     *
     * @param destinationPort destination port
     */
    public void setDestinationPort(int destinationPort) {
        this.destinationPort = destinationPort;
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
     * Returns latency.
     *
     * @return latency
     */
    public long getLatency() {
        return latency;
    }

    /**
     * Sets latency.
     *
     * @param latency latency to set
     */
    public void setLatency(final long latency) {
        this.latency = latency;
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
     * Builds id by source switch and port.
     *
     * @return isl id
     */
    public String getId() {
        return islId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("isl_id", islId)
                .add("src_switch", sourceSwitch)
                .add("src_port", sourcePort)
                .add("dst_switch", destinationSwitch)
                .add("dst_port", destinationPort)
                .add("latency", latency)
                .add("speed", speed)
                .add("available_bandwidth", availableBandwidth)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(sourceSwitch, sourcePort, destinationSwitch,
                destinationPort, latency, speed, availableBandwidth);
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

        Isl that = (Isl) object;
        return Objects.equals(getSourceSwitch(), that.getSourceSwitch())
                && Objects.equals(getDestinationSwitch(), that.getDestinationSwitch())
                && Objects.equals(getSourcePort(), that.getSourcePort())
                && Objects.equals(getDestinationPort(), that.getDestinationPort())
                && Objects.equals(getLatency(), that.getLatency())
                && Objects.equals(getAvailableBandwidth(), that.getAvailableBandwidth())
                && Objects.equals(getSpeed(), that.getSpeed());
    }
}
