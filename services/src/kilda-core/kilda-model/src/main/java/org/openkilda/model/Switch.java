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
import org.neo4j.ogm.annotation.Required;
import org.neo4j.ogm.annotation.Transient;
import org.neo4j.ogm.annotation.typeconversion.Convert;

import java.io.Serializable;
import java.util.List;

/**
 * Represents information about a switch.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"entityId", "incomingLinks", "outgoingLinks"})
@ToString(exclude = {"incomingLinks", "outgoingLinks"})
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
    @Required
    private SwitchId switchId;

    @Property(name = "state")
    // Enforce usage of custom converters.
    @Convert(graphPropertyType = String.class)
    private SwitchStatus status;

    private String address;

    private String hostname;

    private String controller;

    private String description;

    private boolean underMaintenance;

    /**
     * TODO(siakovenko): incomingLinks and outgoingLinks are marked as transient as Neo4j OGM handles load strategy
     * "depth" improperly: when a relation entity is being loaded, OGM fetches ALL relations of start and end nodes
     * of the requested relation. Even with the "depth" = 1.
     * See {@link org.neo4j.ogm.session.request.strategy.impl.SchemaRelationshipLoadClauseBuilder}
     */
    @Transient
    private List<Isl> incomingLinks;

    @Transient
    private List<Isl> outgoingLinks;

    @Builder(toBuilder = true)
    Switch(SwitchId switchId, SwitchStatus status, String address, String hostname, //NOSONAR
            String controller, String description, boolean underMaintenance,
            List<Isl> incomingLinks, List<Isl> outgoingLinks) {
        this.switchId = switchId;
        this.status = status;
        this.address = address;
        this.hostname = hostname;
        this.controller = controller;
        this.description = description;
        this.underMaintenance = underMaintenance;
        this.incomingLinks = incomingLinks;
        this.outgoingLinks = outgoingLinks;
    }
}
