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
 *  Node representation class.
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
    @JsonProperty("dpid")
    private String dpid;

    /**
     * Outgoing relationships.
     */
    @JsonProperty("outgoing-relationships")
    private List<String> outgoingRelationships;

    /**
     * Default constructor.
     */
    public Node() {
    }

    /**
     * Constructs instance.
     *
     * @param dpid                  node datapath id
     * @param outgoingRelationships node outgoing relationships
     */
    @JsonCreator
    public Node(@JsonProperty("dpid") final String dpid,
                @JsonProperty("outgoing-relationships") final List<String> outgoingRelationships) {
        this.dpid = dpid;
        this.outgoingRelationships = outgoingRelationships;
    }

    /**
     * Gets node datapath id.
     *
     * @return node datapath id
     */
    public String getDpid() {
        return dpid;
    }

    /**
     * Sets node datapath id.
     *
     * @param dpid node datapath id
     */
    public void setDpid(final String dpid) {
        this.dpid = dpid;
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
        return Objects.equals(getDpid(), that.getDpid())
                && Objects.equals(getOutgoingRelationships(), that.getOutgoingRelationships());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(getDpid(), getOutgoingRelationships());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("dpid", dpid)
                .add("outgoing-relationships", outgoingRelationships)
                .toString();
    }
}
