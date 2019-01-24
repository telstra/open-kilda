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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import org.neo4j.ogm.annotation.EndNode;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.RelationshipEntity;
import org.neo4j.ogm.annotation.StartNode;
import org.neo4j.ogm.annotation.typeconversion.Convert;

import java.io.Serializable;

/**
 * Represents a segment of a flow path.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"entityId"})
@RelationshipEntity(type = "path_segment")
public class PathSegment implements Serializable {
    private static final long serialVersionUID = 1L;

    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Long entityId;

    @NonNull
    @Property(name = "path_id")
    @Index
    @Convert(graphPropertyType = String.class)
    private PathId pathId;

    @NonNull
    @StartNode
    private Switch srcSwitch;

    @NonNull
    @EndNode
    private Switch destSwitch;

    @Property(name = "src_port")
    private int srcPort;

    @Property(name = "dst_port")
    private int destPort;

    @Property(name = "segment_latency")
    private Long latency;

    @Builder(toBuilder = true)
    public PathSegment(@NonNull PathId pathId, @NonNull Switch srcSwitch, @NonNull Switch destSwitch,
                       int srcPort, int destPort, Long latency) {
        this.pathId = pathId;
        this.srcSwitch = srcSwitch;
        this.destSwitch = destSwitch;
        this.srcPort = srcPort;
        this.destPort = destPort;
        this.latency = latency;
    }
}
