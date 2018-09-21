/* Copyright 2018 Telstra Open Source
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

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.typeconversion.Convert;

import java.io.Serializable;
import java.util.List;

/**
 * Represents information about a switch.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"entityId", "incomingLinks", "outgoingLinks", "flows", "flowSegments"})
@ToString(exclude = {"incomingLinks", "outgoingLinks", "flows", "flowSegments"})
@NodeEntity(label = "switch")
public class Switch implements Serializable {
    private static final long serialVersionUID = 1L;

    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    private Long entityId;

    @Property(name = "name")
    @Convert(graphPropertyType = String.class)
    private SwitchId switchId;

    @Property(name = "state")
    // Enforce usage of custom converters.
    @Convert(graphPropertyType = String.class)
    private SwitchStatus status;

    private String address;

    private String hostname;

    private String controller;

    private String description;

    @Relationship(type = "isl", direction = Relationship.INCOMING)
    private List<Isl> incomingLinks;

    @Relationship(type = "isl", direction = Relationship.OUTGOING)
    private List<Isl> outgoingLinks;

    @Relationship(type = "flow", direction = Relationship.UNDIRECTED)
    private List<Flow> flows;

    @Relationship(type = "flow_segments", direction = Relationship.UNDIRECTED)
    private List<Flow> flowSegments;

    @Builder(toBuilder = true)
    Switch(SwitchId switchId, SwitchStatus status, String address, String hostname, //NOSONAR
            String controller, String description,
            List<Isl> incomingLinks, List<Isl> outgoingLinks, List<Flow> flows, List<Flow> flowSegments) {
        this.switchId = switchId;
        this.status = status;
        this.address = address;
        this.hostname = hostname;
        this.controller = controller;
        this.description = description;
        this.incomingLinks = incomingLinks;
        this.outgoingLinks = outgoingLinks;
        this.flows = flows;
        this.flowSegments = flowSegments;
    }
}
