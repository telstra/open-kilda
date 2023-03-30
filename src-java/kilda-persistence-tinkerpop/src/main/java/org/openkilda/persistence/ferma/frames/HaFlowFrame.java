/* Copyright 2023 Telstra Open Source
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

package org.openkilda.persistence.ferma.frames;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow.HaFlowData;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.FlowEncapsulationTypeConverter;
import org.openkilda.persistence.ferma.frames.converters.FlowStatusConverter;
import org.openkilda.persistence.ferma.frames.converters.PathComputationStrategyConverter;
import org.openkilda.persistence.ferma.frames.converters.PathIdConverter;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;

import com.syncleus.ferma.FramedGraph;
import com.syncleus.ferma.annotations.Property;
import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public abstract class HaFlowFrame extends KildaBaseVertexFrame implements HaFlowData {
    public static final String FRAME_LABEL = "ha_flow";
    public static final String OWNS_PATHS_EDGE = "owns";
    public static final String OWNS_SUB_FLOW_EDGE = "owns";
    public static final String HA_FLOW_ID_PROPERTY = "ha_flow_id";
    public static final String SHARED_ENDPOINT_SWITCH_ID_PROPERTY = "shared_endpoint_switch_id";
    public static final String SHARED_ENDPOINT_PORT_PROPERTY = "shared_endpoint_port";
    public static final String SHARED_ENDPOINT_VLAN_PROPERTY = "shared_endpoint_vlan";
    public static final String SHARED_ENDPOINT_INNER_VLAN_PROPERTY = "shared_endpoint_inner_vlan";
    public static final String ALLOCATE_PROTECTED_PATH_PROPERTY = "allocate_protected_path";
    public static final String MAXIMUM_BANDWIDTH_PROPERTY = "maximum_bandwidth";
    public static final String IGNORE_BANDWIDTH_PROPERTY = "ignore_bandwidth";
    public static final String STRICT_BANDWIDTH_PROPERTY = "strict_bandwidth";
    public static final String DESCRIPTION_PROPERTY = "description";
    public static final String PERIODIC_PINGS_PROPERTY = "periodic_pings";
    public static final String ENCAPSULATION_TYPE_PROPERTY = "encapsulation_type";
    public static final String STATUS_PROPERTY = "status";
    public static final String MAX_LATENCY_PROPERTY = "max_latency";
    public static final String MAX_LATENCY_TIER_2_PROPERTY = "max_latency_tier2";
    public static final String PINNED_PROPERTY = "pinned";
    public static final String PRIORITY_PROPERTY = "priority";
    public static final String PATH_COMPUTATION_STRATEGY_PROPERTY = "path_computation_strategy";
    public static final String FORWARD_PATH_ID_PROPERTY = "forward_path_id";
    public static final String REVERSE_PATH_ID_PROPERTY = "reverse_path_id";
    public static final String PROTECTED_FORWARD_PATH_ID_PROPERTY = "protected_forward_path_id";
    public static final String PROTECTED_REVERSE_PATH_ID_PROPERTY = "protected_reverse_path_id";
    public static final String DIVERSE_GROUP_ID_PROPERTY = "diverse_group_id";
    public static final String AFFINITY_GROUP_ID_PROPERTY = "affinity_group_id";

    private Map<String, HaSubFlow> subFlows;
    private Set<PathId> pathIds;
    private Map<PathId, HaFlowPath> paths;
    private Switch sharedSwitch;

    @Override
    @Property(HA_FLOW_ID_PROPERTY)
    public abstract String getHaFlowId();

    @Override
    @Property(HA_FLOW_ID_PROPERTY)
    public abstract void setHaFlowId(String haFlowId);

    @Override
    @Property(SHARED_ENDPOINT_SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getSharedSwitchId();

    @Override
    @Property(SHARED_ENDPOINT_PORT_PROPERTY)
    public abstract int getSharedPort();

    @Override
    @Property(SHARED_ENDPOINT_PORT_PROPERTY)
    public abstract void setSharedPort(int sharedPort);

    @Override
    @Property(SHARED_ENDPOINT_VLAN_PROPERTY)
    public abstract int getSharedOuterVlan();

    @Override
    @Property(SHARED_ENDPOINT_VLAN_PROPERTY)
    public abstract void setSharedOuterVlan(int sharedPort);

    @Override
    @Property(SHARED_ENDPOINT_INNER_VLAN_PROPERTY)
    public abstract int getSharedInnerVlan();

    @Override
    @Property(SHARED_ENDPOINT_INNER_VLAN_PROPERTY)
    public abstract void setSharedInnerVlan(int sharedPort);

    @Override
    @Property(ALLOCATE_PROTECTED_PATH_PROPERTY)
    public abstract boolean isAllocateProtectedPath();

    @Override
    @Property(ALLOCATE_PROTECTED_PATH_PROPERTY)
    public abstract void setAllocateProtectedPath(boolean allocateProtectedPath);

    @Override
    @Property(MAXIMUM_BANDWIDTH_PROPERTY)
    public abstract long getMaximumBandwidth();

    @Override
    @Property(MAXIMUM_BANDWIDTH_PROPERTY)
    public abstract void setMaximumBandwidth(long maximumBandwidth);

    @Override
    @Property(IGNORE_BANDWIDTH_PROPERTY)
    public abstract boolean isIgnoreBandwidth();

    @Override
    @Property(IGNORE_BANDWIDTH_PROPERTY)
    public abstract void setIgnoreBandwidth(boolean ignoreBandwidth);

    @Override
    @Property(STRICT_BANDWIDTH_PROPERTY)
    public abstract boolean isStrictBandwidth();

    @Override
    @Property(STRICT_BANDWIDTH_PROPERTY)
    public abstract void setStrictBandwidth(boolean strictBandwidth);

    @Override
    @Property(DESCRIPTION_PROPERTY)
    public abstract String getDescription();

    @Override
    @Property(DESCRIPTION_PROPERTY)
    public abstract void setDescription(String description);

    @Override
    @Property(PERIODIC_PINGS_PROPERTY)
    public abstract boolean isPeriodicPings();

    @Override
    @Property(PERIODIC_PINGS_PROPERTY)
    public abstract void setPeriodicPings(boolean periodicPings);

    @Override
    @Property(ENCAPSULATION_TYPE_PROPERTY)
    @Convert(FlowEncapsulationTypeConverter.class)
    public abstract FlowEncapsulationType getEncapsulationType();

    @Override
    @Property(ENCAPSULATION_TYPE_PROPERTY)
    @Convert(FlowEncapsulationTypeConverter.class)
    public abstract void setEncapsulationType(FlowEncapsulationType encapsulationType);

    @Override
    @Property(STATUS_PROPERTY)
    @Convert(FlowStatusConverter.class)
    public abstract FlowStatus getStatus();

    @Override
    @Property(STATUS_PROPERTY)
    @Convert(FlowStatusConverter.class)
    public abstract void setStatus(FlowStatus status);

    @Override
    @Property(MAX_LATENCY_PROPERTY)
    public abstract Long getMaxLatency();

    @Override
    @Property(MAX_LATENCY_PROPERTY)
    public abstract void setMaxLatency(Long maxLatency);

    @Override
    @Property(MAX_LATENCY_TIER_2_PROPERTY)
    public abstract Long getMaxLatencyTier2();

    @Override
    @Property(MAX_LATENCY_TIER_2_PROPERTY)
    public abstract void setMaxLatencyTier2(Long maxLatencyTier2);

    @Override
    @Property(PINNED_PROPERTY)
    public abstract boolean isPinned();

    @Override
    @Property(PINNED_PROPERTY)
    public abstract void setPinned(boolean pinned);

    @Override
    @Property(PRIORITY_PROPERTY)
    public abstract Integer getPriority();

    @Override
    @Property(PRIORITY_PROPERTY)
    public abstract void setPriority(Integer priority);

    @Override
    @Property(PATH_COMPUTATION_STRATEGY_PROPERTY)
    @Convert(PathComputationStrategyConverter.class)
    public abstract PathComputationStrategy getPathComputationStrategy();

    @Override
    @Property(PATH_COMPUTATION_STRATEGY_PROPERTY)
    @Convert(PathComputationStrategyConverter.class)
    public abstract void setPathComputationStrategy(PathComputationStrategy pathComputationStrategy);

    @Override
    @Property(DIVERSE_GROUP_ID_PROPERTY)
    public abstract String getDiverseGroupId();

    @Override
    @Property(DIVERSE_GROUP_ID_PROPERTY)
    public abstract void setDiverseGroupId(String diverseGroupId);

    @Override
    @Property(AFFINITY_GROUP_ID_PROPERTY)
    public abstract String getAffinityGroupId();

    @Override
    @Property(AFFINITY_GROUP_ID_PROPERTY)
    public abstract void setAffinityGroupId(String affinityGroupId);

    @Override
    @Property(FORWARD_PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract PathId getForwardPathId();

    @Override
    @Property(FORWARD_PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract void setForwardPathId(PathId forwardPathId);

    @Override
    @Property(REVERSE_PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract PathId getReversePathId();

    @Override
    @Property(REVERSE_PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract void setReversePathId(PathId reversePathId);

    @Override
    @Property(PROTECTED_FORWARD_PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract PathId getProtectedForwardPathId();

    @Override
    @Property(PROTECTED_FORWARD_PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract void setProtectedForwardPathId(PathId protectedForwardPathId);

    @Override
    @Property(PROTECTED_REVERSE_PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract PathId getProtectedReversePathId();

    @Override
    @Property(PROTECTED_REVERSE_PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract void setProtectedReversePathId(PathId protectedReversePathId);

    @Override
    public Switch getSharedSwitch() {
        if (sharedSwitch == null) {
            String switchId = getProperty(SHARED_ENDPOINT_SWITCH_ID_PROPERTY);
            sharedSwitch = SwitchFrame.load(getGraph(), switchId).map(Switch::new).orElse(null);
        }
        return sharedSwitch;
    }

    @Override
    public void setSharedSwitch(Switch sharedSwitch) {
        this.sharedSwitch = sharedSwitch;
        String switchId = SwitchIdConverter.INSTANCE.toGraphProperty(sharedSwitch.getSwitchId());
        setProperty(SHARED_ENDPOINT_SWITCH_ID_PROPERTY, switchId);
    }

    @Override
    public Optional<HaSubFlow> getSubFlow(String subFlowId) {
        if (subFlows == null) {
            // init the cache map with sub flows.
            getSubFlows();
        }
        return Optional.ofNullable(subFlows.get(subFlowId));
    }

    @Override
    public List<HaSubFlow> getSubFlows() {
        if (subFlows == null) {
            subFlows = traverse(v -> v.out(HaFlowFrame.OWNS_SUB_FLOW_EDGE)
                    .hasLabel(HaSubFlowFrame.FRAME_LABEL))
                    .toListExplicit(HaSubFlowFrame.class).stream()
                    .map(HaSubFlow::new)
                    .collect(Collectors.toMap(HaSubFlow::getHaSubFlowId, v -> v));
        }
        return new ArrayList<>(subFlows.values());
    }

    @Override
    public void setSubFlows(Set<HaSubFlow> subFlows) {
        for (HaSubFlow subFlow : subFlows) {
            HaSubFlow.HaSubFlowData data = subFlow.getData();
            HaSubFlowFrame frame;
            if (data instanceof HaSubFlowFrame) {
                frame = (HaSubFlowFrame) data;
                // Unlink the sub flow from the previous owner.
                frame.getElement().edges(Direction.IN, HaFlowFrame.OWNS_SUB_FLOW_EDGE)
                        .forEachRemaining(Edge::remove);
            } else {
                // We intentionally don't allow to add transient entities.
                // A sub flow must be added via corresponding repository first.
                throw new IllegalArgumentException("Unable to link to transient ha-subflow " + subFlow);
            }
            frame.setProperty(HaSubFlowFrame.HA_FLOW_ID_PROPERTY, getHaFlowId());
            linkOut(frame, HaFlowFrame.OWNS_SUB_FLOW_EDGE);
        }

        // force to reload
        this.subFlows = null;
    }

    @Override
    public Set<PathId> getPathIds() {
        if (pathIds == null) {
            pathIds = traverse(v -> v.out(HaFlowFrame.OWNS_PATHS_EDGE)
                    .hasLabel(HaFlowPathFrame.FRAME_LABEL)
                    .values(HaFlowPathFrame.HA_PATH_ID_PROPERTY))
                    .getRawTraversal().toStream()
                    .map(s -> (String) s)
                    .map(PathIdConverter.INSTANCE::toEntityAttribute)
                    .collect(Collectors.toSet());
        }
        return pathIds;
    }

    @Override
    public Collection<HaFlowPath> getPaths() {
        if (paths == null) {
            paths = traverse(v -> v.out(HaFlowFrame.OWNS_PATHS_EDGE)
                    .hasLabel(HaFlowPathFrame.FRAME_LABEL))
                    .toListExplicit(HaFlowPathFrame.class).stream()
                    .map(HaFlowPath::new)
                    .collect(Collectors.toMap(HaFlowPath::getHaPathId, v -> v));
            pathIds = Collections.unmodifiableSet(paths.keySet());
        }
        return Collections.unmodifiableCollection(paths.values());
    }

    @Override
    public Optional<HaFlowPath> getPath(PathId pathId) {
        if (paths == null) {
            // init the cache map with paths.
            getPaths();
        }
        return Optional.ofNullable(paths.get(pathId));
    }

    @Override
    public boolean hasPath(HaFlowPath path) {
        return getPathIds().contains(path.getHaPathId());
    }

    @Override
    public void addPaths(HaFlowPath... paths) {
        for (HaFlowPath path : paths) {
            HaFlowPath.HaFlowPathData data = path.getData();
            HaFlowPathFrame frame;
            if (data instanceof HaFlowPathFrame) {
                frame = (HaFlowPathFrame) data;
                // Unlink the path from the previous owner.
                frame.getElement().edges(Direction.IN, HaFlowFrame.OWNS_PATHS_EDGE)
                        .forEachRemaining(Edge::remove);
            } else {
                // We intentionally don't allow to add transient entities.
                // A path must be added via corresponding repository first.
                throw new IllegalArgumentException("Unable to link to transient ha-flow path " + path);
            }
            frame.setProperty(HaFlowPathFrame.HA_FLOW_ID_PROPERTY, getHaFlowId());
            linkOut(frame, HaFlowFrame.OWNS_PATHS_EDGE);
            if (this.paths != null) {
                this.paths.put(path.getHaPathId(), path);
            }
        }
        if (this.paths != null) {
            this.pathIds = Collections.unmodifiableSet(this.paths.keySet());
        }
    }

    public static Optional<HaFlowFrame> load(FramedGraph graph, String haFlowId) {
        List<? extends HaFlowFrame> haFlowFrames = graph.traverse(g -> g.V()
                        .hasLabel(HaFlowFrame.FRAME_LABEL)
                        .has(HaFlowFrame.HA_FLOW_ID_PROPERTY, haFlowId))
                .toListExplicit(HaFlowFrame.class);
        return haFlowFrames.isEmpty() ? Optional.empty() : Optional.of(haFlowFrames.get(0));
    }
}
