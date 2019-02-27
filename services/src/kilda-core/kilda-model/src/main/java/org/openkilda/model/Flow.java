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

import static com.google.common.base.Preconditions.checkArgument;

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
import java.util.Objects;

/**
 * Represents a bi-directional flow. This includes the source and destination, flow status,
 * bandwidth and description, active paths, encapsulation type.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = "entityId")
@RelationshipEntity(type = "flow")
public class Flow implements Serializable {
    private static final long serialVersionUID = 1L;

    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Long entityId;

    @NonNull
    @Property(name = "flowid")
    @Index(unique = true)
    private String flowId;

    @NonNull
    @StartNode
    private Switch srcSwitch;

    @NonNull
    @EndNode
    private Switch destSwitch;

    @Property(name = "src_port")
    private int srcPort;

    @Property(name = "src_vlan")
    private int srcVlan;

    @Property(name = "dst_port")
    private int destPort;

    @Property(name = "dst_vlan")
    private int destVlan;

    // No setter as forwardPath must be used for this.
    @Property(name = "forward_path_id")
    @Convert(graphPropertyType = String.class)
    @Setter(AccessLevel.NONE)
    private PathId forwardPathId;

    // No setter as reversePath must be used for this.
    @Property(name = "reverse_path_id")
    @Convert(graphPropertyType = String.class)
    @Setter(AccessLevel.NONE)
    private PathId reversePathId;

    @Transient
    private FlowPath forwardPath;

    @Transient
    private FlowPath reversePath;

    @Property(name = "group_id")
    private String groupId;

    private long bandwidth;

    @Property(name = "ignore_bandwidth")
    private boolean ignoreBandwidth;

    private String description;

    @Property(name = "periodic_pings")
    private boolean periodicPings;

    @NonNull
    @Property(name = "encapsulation_type")
    @Convert(graphPropertyType = String.class)
    private FlowEncapsulationType encapsulationType;

    @NonNull
    // Enforce usage of custom converters.
    @Convert(graphPropertyType = String.class)
    private FlowStatus status;

    @Property(name = "max_latency")
    private Integer maxLatency;

    @Property(name = "priority")
    private Integer priority;

    @Property(name = "time_create")
    @Convert(InstantStringConverter.class)
    private Instant timeCreate;

    @Property(name = "time_modify")
    @Convert(InstantStringConverter.class)
    private Instant timeModify;

    @Builder(toBuilder = true)
    public Flow(@NonNull String flowId, @NonNull Switch srcSwitch, @NonNull Switch destSwitch,
                int srcPort, int srcVlan, int destPort, int destVlan,
                FlowPath forwardPath, FlowPath reversePath,
                String groupId, long bandwidth, boolean ignoreBandwidth, String description, boolean periodicPings,
                FlowEncapsulationType encapsulationType, FlowStatus status,
                Integer maxLatency, Integer priority,
                Instant timeCreate, Instant timeModify) {
        this.flowId = flowId;
        this.srcSwitch = srcSwitch;
        this.destSwitch = destSwitch;
        this.srcPort = srcPort;
        this.srcVlan = srcVlan;
        this.destPort = destPort;
        this.destVlan = destVlan;
        setForwardPath(forwardPath);
        setReversePath(reversePath);
        this.groupId = groupId;
        this.bandwidth = bandwidth;
        this.ignoreBandwidth = ignoreBandwidth;
        this.description = description;
        this.periodicPings = periodicPings;
        this.encapsulationType = encapsulationType;
        this.status = status;
        this.maxLatency = maxLatency;
        this.priority = priority;
        this.timeCreate = timeCreate;
        this.timeModify = timeModify;
    }

    /**
     * Checks whether the flow is through a single switch.
     *
     * @return true if source and destination switches are the same, otherwise false
     */
    public boolean isOneSwitchFlow() {
        return srcSwitch.getSwitchId().equals(destSwitch.getSwitchId());
    }


    public boolean isActive() {
        return status == FlowStatus.UP;
    }

    /**
     * Set the forward path.
     */
    public final void setForwardPath(FlowPath forwardPath) {
        if (forwardPath != null) {
            this.forwardPath = validateForwardPath(forwardPath);
            this.forwardPathId = forwardPath.getPathId();
        } else {
            this.forwardPath = null;
            this.forwardPathId = null;
        }
    }

    private FlowPath validateForwardPath(FlowPath path) {
        validatePath(path);

        checkArgument(Objects.equals(path.getSrcSwitch().getSwitchId(), getSrcSwitch().getSwitchId()),
                "Forward path %s and the flow have different source switch, but expected the same.",
                path.getPathId());

        checkArgument(Objects.equals(path.getDestSwitch().getSwitchId(), getDestSwitch().getSwitchId()),
                "Forward path %s and the flow have different destination switch, but expected the same.",
                path.getPathId());

        return path;
    }

    /**
     * Set the reverse path.
     */
    public final void setReversePath(FlowPath reversePath) {
        if (reversePath != null) {
            this.reversePath = validateReversePath(reversePath);
            this.reversePathId = reversePath.getPathId();
        } else {
            this.reversePath = null;
            this.reversePathId = null;
        }
    }

    private FlowPath validateReversePath(FlowPath path) {
        validatePath(path);

        checkArgument(Objects.equals(path.getSrcSwitch().getSwitchId(), getDestSwitch().getSwitchId()),
                "Reverse path %s source and the flow destination are different, but expected the same.",
                path.getPathId());

        checkArgument(Objects.equals(path.getDestSwitch().getSwitchId(), getSrcSwitch().getSwitchId()),
                "Reverse path %s destination and the flow source are different, but expected the same.",
                path.getPathId());

        return path;
    }

    private FlowPath validatePath(FlowPath path) {
        checkArgument(Objects.equals(path.getFlowId(), getFlowId()),
                "Path %s belongs to another flow, but expected the same.", path.getPathId());

        checkArgument(Objects.equals(path.getSrcSwitch().getSwitchId(), getSrcSwitch().getSwitchId())
                        || Objects.equals(path.getSrcSwitch().getSwitchId(), getDestSwitch().getSwitchId()),
                "Path %s source doesn't correspond to any of flow endpoints.", path.getSrcSwitch());

        checkArgument(Objects.equals(path.getDestSwitch().getSwitchId(), getSrcSwitch().getSwitchId())
                        || Objects.equals(path.getDestSwitch().getSwitchId(), getDestSwitch().getSwitchId()),
                "Path %s destination doesn't correspond to any of flow endpoints.", path.getSrcSwitch());

        return path;
    }
}
