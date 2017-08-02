package org.bitbucket.openkilda.messaging.info.event;

import static com.google.common.base.MoreObjects.toStringHelper;

import org.bitbucket.openkilda.messaging.info.InfoData;

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
        "latency_ns",
        "path",
        "speed",
        "state"})
public class IslInfoData extends InfoData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Latency value in nseconds.
     */
    @JsonProperty("latency_ns")
    private long latency;

    /**
     * Path.
     */
    @JsonProperty("path")
    private List<PathNode> path;

    /**
     * Port speed.
     */
    @JsonProperty("speed")
    private long speed;

    /**
     * Isl state.
     */
    @JsonProperty("state")
    private IslChangeType state;

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
    public IslInfoData(@JsonProperty("latency_ns") final long latency,
                       @JsonProperty("path") final List<PathNode> path,
                       @JsonProperty("speed") final long speed,
                       @JsonProperty("state") final IslChangeType state) {
        this.latency = latency;
        this.path = path;
        this.speed = speed;
        this.state = state;
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
     * Returns path.
     *
     * @return path
     */
    public List<PathNode> getPath() {
        return path;
    }

    /**
     * Sets path.
     *
     * @param path latency to set
     */
    public void setPath(final List<PathNode> path) {
        this.path = path;
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
    public void setState(final IslChangeType state) {
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
                && Objects.equals(getState(), that.getState());
    }
}
