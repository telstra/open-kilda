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

import org.openkilda.messaging.info.CacheTimeTag;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Defines the payload payload of a Message representing a path info.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "latency_ns",
        "path"})
public class PathInfoData extends CacheTimeTag {
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
     * Instance constructor.
     */
    public PathInfoData() {
    }

    /**
     * Instance constructor.
     *
     * @param latency latency
     * @param path    path
     */
    @JsonCreator
    public PathInfoData(@JsonProperty("latency_ns") long latency,
                        @JsonProperty("path") List<PathNode> path) {
        this.latency = latency;
        this.path = path;
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
        return Objects.hash(path);
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
        if (this.getPath() == null || that.getPath() == null) {
            return this.getPath() == that.getPath();
        }

        if (this.getPath().size() != that.getPath().size()) {
            return false;
        }

        List<PathNode> thisPath = this.getPath().stream()
                .sorted(Comparator.comparing(PathNode::getSeqId))
                .collect(Collectors.toList());

        List<PathNode> thatPath = that.getPath().stream()
                .sorted(Comparator.comparing(PathNode::getSeqId))
                .collect(Collectors.toList());
        return Objects.equals(thisPath, thatPath);
    }
}
