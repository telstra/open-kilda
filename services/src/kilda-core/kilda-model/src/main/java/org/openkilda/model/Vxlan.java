/* Copyright 2019 Telstra Open Source
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.typeconversion.Convert;

import java.io.Serializable;

/**
 * Represents a vxlan allocated for a flow path.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"entityId"})
@NodeEntity(label = "vxlan")
public class Vxlan implements Serializable {
    private static final long serialVersionUID = 1L;

    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Long entityId;

    @NonNull
    @Property(name = "flow_id")
    private String flowId;

    @NonNull
    @Property(name = "path_id")
    @Index
    @Convert(graphPropertyType = String.class)
    private PathId pathId;

    @NonNull
    @Property(name = "vni")
    @Index(unique = true)
    private int vni;

    @Builder(toBuilder = true)
    public Vxlan(@NonNull String flowId, @NonNull PathId pathId, int vni) {
        this.flowId = flowId;
        this.pathId = pathId;
        this.vni = vni;
    }
}
