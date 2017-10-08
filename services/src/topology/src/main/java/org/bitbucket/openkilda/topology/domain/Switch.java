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
    @Property(name = "name")
    @Index
    private String name;

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
     * Switch hostname.
     */
    @Property(name = "hostname")
    private String hostname;

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
     * @param name        switch datapath id
     * @param state       switch state
     * @param address     switch address
     * @param hostname    switch hostname
     * @param description switch description
     */
    public Switch(final String name, final String state,
                  final String address, final String hostname,
                  final String description) {
        this.name = name;
        this.state = state;
        this.address = address;
        this.hostname = hostname;
        this.description = description;

    }

    /**
     * Gets switch datapath id.
     *
     * @return switch datapath id
     */
    public String getName() {
        return name;
    }

    /**
     * Sets switch datapath id.
     *
     * @param name switch datapath id
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets switch hostname.
     *
     * @return switch hostname
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * Sets switch hostname.
     *
     * @param hostname switch hostname
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
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
                .add("name", name)
                .add("state", state)
                .add("address", address)
                .add("hostname", hostname)
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
