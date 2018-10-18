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
import org.neo4j.ogm.annotation.EndNode;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.RelationshipEntity;
import org.neo4j.ogm.annotation.StartNode;
import org.neo4j.ogm.annotation.Transient;
import org.neo4j.ogm.annotation.typeconversion.Convert;
import org.neo4j.ogm.typeconversion.InstantStringConverter;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Represents a flow path.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"entityId", "segments"})
@RelationshipEntity(type = "flow_path")
public class FlowPath implements Serializable {
    private static final long serialVersionUID = 1L;

    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Long entityId;

    @NonNull
    @Property(name = "path_id")
    @Index(unique = true)
    @Convert(graphPropertyType = String.class)
    private PathId pathId;

    @NonNull
    @StartNode
    private Switch srcSwitch;

    @NonNull
    @EndNode
    private Switch destSwitch;

    @NonNull
    @Property(name = "flow_id")
    @Index
    private String flowId;

    @Convert(graphPropertyType = Long.class)
    private Cookie cookie;

    @Property(name = "meter_id")
    @Convert(graphPropertyType = Long.class)
    private MeterId meterId;

    private long latency;

    private long bandwidth;

    @Property(name = "ignore_bandwidth")
    private boolean ignoreBandwidth;

    @Property(name = "time_create")
    @Convert(InstantStringConverter.class)
    private Instant timeCreate;

    @Property(name = "time_modify")
    @Convert(InstantStringConverter.class)
    private Instant timeModify;

    @NonNull
    @Property(name = "status")
    // Enforce usage of custom converters.
    @Convert(graphPropertyType = String.class)
    private FlowPathStatus status;

    @NonNull
    @Transient
    private List<PathSegment> segments;

    @Builder(toBuilder = true)
    public FlowPath(@NonNull PathId pathId, @NonNull Switch srcSwitch, @NonNull Switch destSwitch,
                    @NonNull String flowId, Cookie cookie, MeterId meterId,
                    long latency, long bandwidth, boolean ignoreBandwidth,
                    Instant timeCreate, Instant timeModify,
                    FlowPathStatus status, @NonNull List<PathSegment> segments) {
        this.pathId = pathId;
        this.srcSwitch = srcSwitch;
        this.destSwitch = destSwitch;
        this.flowId = flowId;
        this.cookie = cookie;
        this.meterId = meterId;
        this.latency = latency;
        this.bandwidth = bandwidth;
        this.ignoreBandwidth = ignoreBandwidth;
        this.timeCreate = timeCreate;
        this.timeModify = timeModify;
        this.status = status;
        setSegments(segments);
    }

    /**
     * Set the pathId and propagate it to segments.
     */
    public void setPathId(@NonNull PathId pathId) {
        this.pathId = pathId;

        if (segments != null) {
            segments.forEach(pathSegment -> pathSegment.setPathId(pathId));
        }
    }

    /**
     * Set the segments and update the pathId in each.
     */
    public final void setSegments(@NonNull List<PathSegment> segments) {
        this.segments = segments;

        if (pathId != null) {
            segments.forEach(pathSegment -> pathSegment.setPathId(pathId));
        }
    }

    /**
     * Checks whether the flow path goes through a single switch.
     *
     * @return true if source and destination switches are the same, otherwise false
     */
    public boolean isOneSwitchFlow() {
        return srcSwitch.getSwitchId().equals(destSwitch.getSwitchId());
    }
}
