package org.bitbucket.openkilda.topology.model;

import static com.google.common.base.Objects.toStringHelper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Network topology representation class.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Topology implements Serializable {
    /**
     * The constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * List of nodes.
     */
    @JsonProperty("nodes")
    private List<Node> nodes;

    /**
     * Constructs entity.
     *
     * @param nodes list of nodes
     */
    @JsonCreator
    public Topology(@JsonProperty("nodes") final List<Node> nodes) {
        this.nodes = nodes;
    }

    /**
     * Gets nodes.
     *
     * @return list of nodes
     */
    public List<Node> getNodes() {
        return nodes;
    }

    /**
     * Sets nodes.
     *
     * @param nodes list of nodes
     */
    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("nodes", nodes)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (object == null || !(object instanceof Topology)) {
            return false;
        }

        Topology that = (Topology) object;
        return Objects.equals(getNodes(), that.getNodes());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(nodes);
    }
}
