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
import static org.neo4j.ogm.annotation.Relationship.OUTGOING;

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
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.typeconversion.Convert;
import org.neo4j.ogm.typeconversion.InstantStringConverter;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a bi-directional flow. This includes the source and destination, flow status,
 * bandwidth and description, active paths, encapsulation type.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"entityId", "paths"})
@NodeEntity(label = "flow")
public class Flow implements Serializable {
    private static final long serialVersionUID = 1L;

    // Hidden as needed for OGM only.
    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private Long entityId;

    @NonNull
    @Property(name = "flow_id")
    @Index(unique = true)
    private String flowId;

    @NonNull
    @Relationship(type = "source", direction = OUTGOING)
    private Switch srcSwitch;

    @NonNull
    @Relationship(type = "destination", direction = OUTGOING)
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

    @Relationship(type = "owns", direction = OUTGOING)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<FlowPath> paths = new ArrayList<>();

    @Property(name = "allocate_protected_path")
    private boolean allocateProtectedPath;

    // No setter as protectedForwardPath must be used for this.
    @Property(name = "protected_forward_path_id")
    @Convert(graphPropertyType = String.class)
    @Setter(AccessLevel.NONE)
    private PathId protectedForwardPathId;

    // No setter as protectedReversePath must be used for this.
    @Property(name = "protected_reverse_path_id")
    @Convert(graphPropertyType = String.class)
    @Setter(AccessLevel.NONE)
    private PathId protectedReversePathId;

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

    @Property(name = "pinned")
    private boolean pinned;

    @Builder(toBuilder = true)
    public Flow(@NonNull String flowId, @NonNull Switch srcSwitch, @NonNull Switch destSwitch,
                int srcPort, int srcVlan, int destPort, int destVlan,
                String groupId, long bandwidth, boolean ignoreBandwidth, String description, boolean periodicPings,
                boolean allocateProtectedPath, FlowEncapsulationType encapsulationType, FlowStatus status,
                Integer maxLatency, Integer priority,
                Instant timeCreate, Instant timeModify, boolean pinned) {
        this.flowId = flowId;
        this.srcSwitch = srcSwitch;
        this.destSwitch = destSwitch;
        this.srcPort = srcPort;
        this.srcVlan = srcVlan;
        this.destPort = destPort;
        this.destVlan = destVlan;
        this.groupId = groupId;
        this.bandwidth = bandwidth;
        this.ignoreBandwidth = ignoreBandwidth;
        this.description = description;
        this.periodicPings = periodicPings;
        this.allocateProtectedPath = allocateProtectedPath;
        this.encapsulationType = encapsulationType;
        this.status = status;
        this.maxLatency = maxLatency;
        this.priority = priority;
        this.timeCreate = timeCreate;
        this.timeModify = timeModify;
        this.pinned = pinned;
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

    public List<FlowPath> getPaths() {
        return Collections.unmodifiableList(paths);
    }

    /**
     * Add and associate flow path(s) with the flow.
     */
    public void addPaths(FlowPath... paths) {
        for (FlowPath pathToAdd : paths) {
            this.paths.removeIf(path -> path.getPathId().equals(pathToAdd.getPathId()));
            this.paths.add(pathToAdd);
        }
    }

    /**
     * Set the forward path.
     */
    public final void setForwardPath(FlowPath forwardPath) {
        if (forwardPath != null) {
            addPaths(validateForwardPath(forwardPath));
            this.forwardPathId = forwardPath.getPathId();
        } else {
            this.forwardPathId = null;
        }
    }

    /**
     * Get the forward path.
     */
    public FlowPath getForwardPath() {
        if (getForwardPathId() == null) {
            return null;
        }

        return paths.stream()
                .filter(path -> path.getPathId().equals(getForwardPathId()))
                .findAny()
                .orElse(null);
    }

    /**
     * Set the protected forward path.
     */
    public final void setProtectedForwardPath(FlowPath forwardPath) {
        paths.removeIf(path -> path.getPathId().equals(getProtectedForwardPathId()));

        if (forwardPath != null) {
            paths.add(validateForwardPath(forwardPath));
            this.protectedForwardPathId = forwardPath.getPathId();
        } else {
            this.protectedForwardPathId = null;
        }
    }

    /**
     * Get the protected forward path.
     */
    public FlowPath getProtectedForwardPath() {
        if (getProtectedForwardPathId() == null) {
            return null;
        }

        return paths.stream()
                .filter(path -> path.getPathId().equals(getProtectedForwardPathId()))
                .findAny()
                .orElse(null);
    }

    /**
     * Check whether the path corresponds to the forward flow.
     */
    public boolean isForward(FlowPath path) {
        return Objects.equals(path.getFlow().getFlowId(), this.getFlowId())
                && Objects.equals(path.getSrcSwitch().getSwitchId(), getSrcSwitch().getSwitchId())
                && Objects.equals(path.getDestSwitch().getSwitchId(), getDestSwitch().getSwitchId())
                && (!isOneSwitchFlow() || path.getCookie() != null && path.getCookie().isMaskedAsForward());
    }

    /**
     * Check whether the path corresponds to the reverse flow.
     */
    public boolean isReverse(FlowPath path) {
        return Objects.equals(path.getFlow().getFlowId(), this.getFlowId())
                && Objects.equals(path.getSrcSwitch().getSwitchId(), getDestSwitch().getSwitchId())
                && Objects.equals(path.getDestSwitch().getSwitchId(), getSrcSwitch().getSwitchId())
                && (!isOneSwitchFlow() || path.getCookie() != null && path.getCookie().isMaskedAsReversed());
    }

    private FlowPath validateForwardPath(FlowPath path) {
        checkArgument(isForward(path),
                "Forward path %s and the flow have different endpoints, but expected the same.",
                path.getPathId());

        return path;
    }

    /**
     * Set the reverse path.
     */
    public final void setReversePath(FlowPath reversePath) {
        if (reversePath != null) {
            addPaths(validateReversePath(reversePath));
            this.reversePathId = reversePath.getPathId();
        } else {
            this.reversePathId = null;
        }
    }

    /**
     * Get the reverse path.
     */
    public FlowPath getReversePath() {
        if (getReversePathId() == null) {
            return null;
        }

        return paths.stream()
                .filter(path -> path.getPathId().equals(getReversePathId()))
                .findAny()
                .orElse(null);
    }

    /**
     * Set the protected reverse path.
     */
    public final void setProtectedReversePath(FlowPath reversePath) {
        paths.removeIf(path -> path.getPathId().equals(getProtectedReversePathId()));

        if (reversePath != null) {
            paths.add(validateReversePath(reversePath));
            this.protectedReversePathId = reversePath.getPathId();
        } else {
            this.protectedReversePathId = null;
        }
    }

    /**
     * Get the protected reverse path.
     */
    public FlowPath getProtectedReversePath() {
        if (getProtectedReversePathId() == null) {
            return null;
        }

        return paths.stream()
                .filter(path -> path.getPathId().equals(getProtectedReversePathId()))
                .findAny()
                .orElse(null);
    }

    private FlowPath validateReversePath(FlowPath path) {
        checkArgument(isReverse(path),
                "Reverse path %s and the flow have different endpoints, but expected the same.",
                path.getPathId());

        return path;
    }

    /**
     * Return opposite pathId to passed pathId.
     */
    public PathId getOppositePathId(@NonNull PathId pathId) {
        if (pathId.equals(forwardPathId)) {
            return reversePathId;
        } else if (pathId.equals(reversePathId)) {
            return forwardPathId;
        } else if (pathId.equals(protectedForwardPathId)) {
            return protectedReversePathId;
        } else if (pathId.equals(protectedReversePathId)) {
            return protectedForwardPathId;
        } else {
            throw new IllegalArgumentException(String.format("Flow %s does not have path pathId=%s", flowId, pathId));
        }
    }

    public List<PathId> getFlowPathIds() {
        return paths.stream().map(FlowPath::getPathId).collect(Collectors.toList());
    }


    /**
     * Return main flow prioritized paths status.
     */
    public FlowPathStatus getMainFlowPrioritizedPathsStatus() {
        return getFlowPrioritizedPathStatus(getForwardPath(), getReversePath());
    }

    /**
     * Return protected flow prioritized paths status.
     */
    public FlowPathStatus getProtectedFlowPrioritizedPathsStatus() {
        return getFlowPrioritizedPathStatus(getProtectedForwardPath(), getProtectedReversePath());
    }

    private FlowPathStatus getFlowPrioritizedPathStatus(FlowPath... flowPaths) {
        return Stream.of(flowPaths)
                .filter(Objects::nonNull)
                .map(FlowPath::getStatus)
                .max(FlowPathStatus::compareTo)
                .orElse(null);
    }
}
