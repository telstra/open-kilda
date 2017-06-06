package org.bitbucket.openkilda.topology.domain;

import static com.google.common.base.Objects.toStringHelper;

import org.neo4j.ogm.annotation.GraphId;
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.Labels;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

import java.util.HashSet;
import java.util.Set;

/**
 * Switch Node Entity.
 */
@NodeEntity(label = "switch")
public class Switch {
    /**
     * Graph id.
     */
    @GraphId
    private Long id;

    /**
     * Switch datapath id.
     */
    @Property(name = "dpid")
    @Index
    private String dpid;

    /**
     * Switch state.
     */
    @Property(name = "state")
    private String state;

    /**
     * Switch address.
     */
    @Property(name = "address")
    private String address;

    /**
     * Switch name.
     */
    @Property(name = "name")
    private String name;

    /**
     * Switch description.
     */
    @Property(name = "description")
    private String description;
    @Labels
    private Set<String> labels = new HashSet<>();

    /**
     * Internal Neo4j constructor.
     */
    private Switch() {
    }

    /**
     * Constructs entity.
     *
     * @param dpid        switch datapath id
     * @param state       switch state
     * @param address     switch address
     * @param name        switch name
     * @param description switch description
     */
    public Switch(final String dpid, final String state,
                  final String address, final String name,
                  final String description) {
        this.dpid = dpid;
        this.state = state;
        this.address = address;
        this.name = name;
        this.description = description;

    }

    /**
     * Gets switch datapath id.
     *
     * @return switch datapath id
     */
    public String getDpid() {
        return dpid;
    }

    /**
     * Sets switch datapath id.
     *
     * @param dpid switch datapath id
     */
    public void setDpid(String dpid) {
        this.dpid = dpid;
    }

    /**
     * Gets switch name.
     *
     * @return switch name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets switch state.
     *
     * @return switch state
     */
    public String getState() {
        return state;
    }

    /**
     * Sets switch state.
     *
     * @param state switch state
     */
    public void setState(String state) {
        this.state = state;
    }

    /**
     * Gets switch address.
     *
     * @return switch address
     */
    public String getAddress() {
        return address;
    }

    /**
     * Sets switch address.
     *
     * @param address switch address
     */
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * Gets switch description.
     *
     * @return switch description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets switch description.
     *
     * @param description switch description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add("dpid", dpid)
                .add("state", state)
                .add("address", address)
                .add("name", name)
                .add("description", description)
                .toString();
    }

    /**
     * Sets labels fro switch.
     *
     * @param state       state label
     * @param description description label
     */
    public void setLabels(final String state, final String description) {
        this.labels.clear();
        this.labels.add(state);
        this.labels.add(description.replaceAll("(\\D | \\W)", "_"));
    }
}
