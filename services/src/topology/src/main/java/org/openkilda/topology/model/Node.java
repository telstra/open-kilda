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

package org.openkilda.topology.model;

import static com.google.common.base.Objects.toStringHelper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Node representation class.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Node implements Serializable {
    /**
     * The constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Node datapath id.
     */
    @JsonProperty("name")
    private String name;

    /**
     * Outgoing relationships.
     */
    @JsonProperty("outgoing_relationships")
    private List<String> outgoingRelationships;

    /**
     * Constructs instance.
     *
     * @param name                  node datapath id
     * @param outgoingRelationships node outgoing relationships
     */
    @JsonCreator
    public Node(@JsonProperty("name") final String name,
                @JsonProperty("outgoing_relationships") final List<String> outgoingRelationships) {
        this.name = name;
        this.outgoingRelationships = outgoingRelationships;
    }

    /**
     * Gets node datapath id.
     *
     * @return node datapath id
     */
    public String getName() {
        return name;
    }

    /**
     * Sets node datapath id.
     *
     * @param name node datapath id
     */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Gets node outgoing relationships.
     *
     * @return node outgoing relationships
     */
    public List<String> getOutgoingRelationships() {
        return outgoingRelationships;
    }

    /**
     * Sets node outgoing relationships.
     *
     * @param outgoingRelationships outgoing relationships
     */
    public void setOutgoingRelationships(final List<String> outgoingRelationships) {
        this.outgoingRelationships = outgoingRelationships;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (object == null || !(object instanceof Node)) {
            return false;
        }

        Node that = (Node) object;
        return Objects.equals(getName(), that.getName())
                && Objects.equals(getOutgoingRelationships(), that.getOutgoingRelationships());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(getName(), getOutgoingRelationships());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("name", name)
                .add("outgoing-relationships", outgoingRelationships)
                .toString();
    }
}
