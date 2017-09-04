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
 * Defines the payload payload of a Message representing a path info.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "message_type",
        "id",
        "latency_ns",
        "path",
        "state"})
public class PathInfoData extends InfoData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Latency value in nseconds.
     */
    @JsonProperty("latency_ns")
    protected long latency;

    /**
     * Path.
     */
    @JsonProperty("path")
    protected List<PathNode> path;

    /**
     * Isl state.
     */
    @JsonProperty("state")
    protected IslChangeType state;

    /**
     * Instance constructor.
     */
    public PathInfoData() {
    }

    /**
     * Instance constructor.
     *
     * @param latency latency
     * @param path    path
     * @param state   isl discovery result
     */
    @JsonCreator
    public PathInfoData(@JsonProperty("latency_ns") long latency,
                        @JsonProperty("path") List<PathNode> path,
                        @JsonProperty("state") IslChangeType state) {
        this.latency = latency;
        this.path = path;
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
    public void setLatency(long latency) {
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
    public void setPath(List<PathNode> path) {
        this.path = path;
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
                .add("state", state)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(latency, path, state);
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

        PathInfoData that = (PathInfoData) object;
        return Objects.equals(getLatency(), that.getLatency())
                && Objects.equals(getPath(), that.getPath())
                && Objects.equals(getState(), that.getState());
    }
}
