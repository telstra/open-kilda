package org.bitbucket.openkilda.messaging.info.event;

import static com.google.common.base.Objects.toStringHelper;

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
        "message-type",
        "latency_ns",
        "path"})
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
     * Default constructor.
     */
    public IslInfoData() {
    }

    /**
     * Instance constructor.
     *
     * @param   latency  latency
     * @param   path     path
     */
    @JsonCreator
    public IslInfoData(@JsonProperty("latency_ns") final long latency,
                       @JsonProperty("path") final List<PathNode> path) {
        this.latency = latency;
        this.path = path;
    }

    /**
     * Returns latency.
     *
     * @return  latency
     */
    @JsonProperty("latency_ns")
    public long getLatency() {
        return latency;
    }

    /**
     * Sets latency.
     *
     * @param   latency  latency to set
     */
    @JsonProperty("latency_ns")
    public void setLatency(final long latency) {
        this.latency = latency;
    }

    /**
     * Returns path.
     *
     * @return  path
     */
    @JsonProperty("path")
    public List<PathNode> getPath() {
        return path;
    }

    /**
     * Sets path.
     *
     * @param   path  latency to set
     */
    @JsonProperty("path")
    public void setPath(final List<PathNode> path) {
        this.path = path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("latency_ns", latency)
                .add("path", path)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(latency, path);
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
                && Objects.equals(getPath(), that.getPath());
    }
}
