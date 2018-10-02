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

package org.openkilda.model;

import lombok.Value;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Defines the payload payload of a Message representing a path info.
 */
@Value
public class Path {

    /**
     * Latency value in nseconds.
     */
    private long latency;

    /**
     * Path.
     */
    private List<Node> nodes;

    /**
     * Instance constructor.
     *
     * @param latency latency
     * @param nodes    path
     */
    public Path(long latency, List<Node> nodes) {
        this.latency = latency;
        this.nodes = nodes;
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
     * Returns path.
     *
     * @return path
     */
    public List<Node> getPath() {
        return nodes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(nodes);
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

        Path that = (Path) object;
        if (this.getPath() == null || that.getPath() == null) {
            return this.getPath() == that.getPath();
        }

        if (this.getPath().size() != that.getPath().size()) {
            return false;
        }

        List<Node> thisPath = this.getPath().stream()
                .sorted(Comparator.comparing(Node::getSeqId))
                .collect(Collectors.toList());

        List<Node> thatPath = that.getPath().stream()
                .sorted(Comparator.comparing(Node::getSeqId))
                .collect(Collectors.toList());
        return Objects.equals(thisPath, thatPath);
    }
}
