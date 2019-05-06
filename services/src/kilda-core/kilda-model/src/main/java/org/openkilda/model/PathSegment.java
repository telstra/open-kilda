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

import static org.neo4j.ogm.annotation.Relationship.INCOMING;
import static org.neo4j.ogm.annotation.Relationship.OUTGOING;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;

import java.io.Serializable;

/**
 * Represents a segment of a flow path.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"entityId"})
@ToString(exclude = {"path"})
@NodeEntity(label = "path_segment")
public class PathSegment implements Serializable {
    private static final long serialVersionUID = 1L;

    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Long entityId;

    @NonNull
    @Relationship(type = "owns", direction = INCOMING)
    private FlowPath path;

    @NonNull
    @Relationship(type = "source", direction = OUTGOING)
    private Switch srcSwitch;

    @NonNull
    @Relationship(type = "destination", direction = OUTGOING)
    private Switch destSwitch;

    @Property(name = "src_port")
    private int srcPort;

    @Property(name = "dst_port")
    private int destPort;

    // Hidden as used only by mapping to keep the order within a list of segments.
    @Property(name = "seq_id")
    @Setter(AccessLevel.PACKAGE)
    private int seqId;

    private Long latency;

    private boolean failed = false;

    @Builder(toBuilder = true)
    public PathSegment(@NonNull FlowPath path, @NonNull Switch srcSwitch, @NonNull Switch destSwitch,
                       int srcPort, int destPort, Long latency) {
        this.path = path;
        this.srcSwitch = srcSwitch;
        this.destSwitch = destSwitch;
        this.srcPort = srcPort;
        this.destPort = destPort;
        this.latency = latency;
    }

    /**
     * Checks whether endpoint belongs to segment or not.
     * @param switchId target switch
     * @param port target port
     * @return result of check
     */
    public boolean containsNode(SwitchId switchId, int port) {
        if (switchId == null) {
            throw new IllegalArgumentException("Switch id must be not null");
        }
        return  (switchId.equals(srcSwitch.getSwitchId()) && port == srcPort)
               || (switchId.equals(destSwitch.getSwitchId()) && port == destPort);
    }
}
