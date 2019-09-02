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

package org.openkilda.persistence.ferma.repositories.frames;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.model.Flow;
import org.openkilda.persistence.ferma.model.FlowPath;
import org.openkilda.persistence.ferma.model.Switch;

import com.syncleus.ferma.AbstractElementFrame;
import com.syncleus.ferma.AbstractVertexFrame;
import com.syncleus.ferma.DelegatingFramedGraph;
import com.syncleus.ferma.FramedGraph;
import com.syncleus.ferma.Traversable;
import com.syncleus.ferma.VertexFrame;
import com.syncleus.ferma.annotations.GraphElement;
import com.syncleus.ferma.annotations.Property;
import org.apache.tinkerpop.gremlin.neo4j.structure.Neo4jVertex;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@GraphElement
public abstract class FlowFrame extends AbstractVertexFrame implements Flow {
    public static final String FRAME_LABEL = "flow";

    public static final String SOURCE_EDGE = "source";
    public static final String DESTINATION_EDGE = "destination";
    static final String OWNS_PATHS_EDGE = "owns";

    public static final String FLOW_ID_PROPERTY = "flow_id";
    public static final String SRC_PORT_PROPERTY = "src_port";
    public static final String DST_PORT_PROPERTY = "dst_port";
    public static final String GROUP_ID_PROPERTY = "group_id";
    public static final String PERIODIC_PINGS_PROPERTY = "periodic_pings";
    public static final String STATUS_PROPERTY = "status";

    private Vertex cachedElement;

    @Override
    public Vertex getElement() {
        // A workaround for the issue with neo4j-gremlin and Ferma integration.
        if (cachedElement == null) {
            try {
                java.lang.reflect.Field field = AbstractElementFrame.class.getDeclaredField("element");
                field.setAccessible(true);
                Object value = field.get(this);
                field.setAccessible(false);
                if (value instanceof Neo4jVertex) {
                    cachedElement = (Vertex) value;
                }
            } catch (NoSuchFieldException | IllegalAccessException ex) {
                // just ignore
            }

            if (cachedElement == null) {
                cachedElement = super.getElement();
            }
        }

        return cachedElement;
    }

    @Property(FLOW_ID_PROPERTY)
    @Override
    public abstract String getFlowId();

    @Property(FLOW_ID_PROPERTY)
    @Override
    public abstract void setFlowId(String flowId);

    @Override
    public int getSrcPort() {
        return ((Long) getProperty(SRC_PORT_PROPERTY)).intValue();
    }

    @Override
    public void setSrcPort(int srcPort) {
        setProperty(SRC_PORT_PROPERTY, (long) srcPort);
    }

    @Override
    public int getSrcVlan() {
        return ((Long) getProperty("src_vlan")).intValue();
    }

    @Override
    public void setSrcVlan(int srcVlan) {
        setProperty("src_vlan", (long) srcVlan);
    }

    @Override
    public int getDestPort() {
        return ((Long) getProperty(DST_PORT_PROPERTY)).intValue();
    }

    @Override
    public void setDestPort(int destPort) {
        setProperty(DST_PORT_PROPERTY, (long) destPort);
    }

    @Override
    public int getDestVlan() {
        return ((Long) getProperty("dst_vlan")).intValue();
    }

    @Override
    public void setDestVlan(int destVlan) {
        setProperty("dst_vlan", (long) destVlan);
    }

    @Override
    public PathId getForwardPathId() {
        return Optional.ofNullable((String) getProperty("forward_path_id")).map(PathId::new).orElse(null);
    }

    @Override
    public void setForwardPathId(PathId forwardPathId) {
        setProperty("forward_path_id", forwardPathId == null ? null : forwardPathId.getId());
    }

    @Override
    public PathId getReversePathId() {
        return Optional.ofNullable((String) getProperty("reverse_path_id")).map(PathId::new).orElse(null);
    }

    @Override
    public void setReversePathId(PathId reversePathId) {
        setProperty("reverse_path_id", reversePathId == null ? null : reversePathId.getId());
    }

    @Property("allocate_protected_path")
    @Override
    public abstract boolean isAllocateProtectedPath();

    @Property("allocate_protected_path")
    @Override
    public abstract void setAllocateProtectedPath(boolean allocateProtectedPath);

    @Override
    public PathId getProtectedForwardPathId() {
        return Optional.ofNullable((String) getProperty("protected_forward_path_id"))
                .map(PathId::new).orElse(null);
    }

    @Override
    public void setProtectedForwardPathId(PathId protectedForwardPathId) {
        setProperty("protected_forward_path_id",
                protectedForwardPathId == null ? null : protectedForwardPathId.getId());
    }

    @Override
    public PathId getProtectedReversePathId() {
        return Optional.ofNullable((String) getProperty("protected_reverse_path_id"))
                .map(PathId::new).orElse(null);
    }

    @Override
    public void setProtectedReversePathId(PathId protectedReversePathId) {
        setProperty("protected_reverse_path_id",
                protectedReversePathId == null ? null : protectedReversePathId.getId());
    }

    @Property(GROUP_ID_PROPERTY)
    @Override
    public abstract String getGroupId();

    @Property(GROUP_ID_PROPERTY)
    @Override
    public abstract void setGroupId(String groupId);

    @Property("bandwidth")
    @Override
    public abstract long getBandwidth();

    @Property("bandwidth")
    @Override
    public abstract void setBandwidth(long bandwidth);

    @Property("ignore_bandwidth")
    @Override
    public abstract boolean isIgnoreBandwidth();

    @Property("ignore_bandwidth")
    @Override
    public abstract void setIgnoreBandwidth(boolean ignoreBandwidth);

    @Property("description")
    @Override
    public abstract String getDescription();

    @Property("description")
    @Override
    public abstract void setDescription(String description);

    @Property(PERIODIC_PINGS_PROPERTY)
    @Override
    public abstract boolean isPeriodicPings();

    @Property(PERIODIC_PINGS_PROPERTY)
    @Override
    public abstract void setPeriodicPings(boolean periodicPings);

    @Override
    public FlowEncapsulationType getEncapsulationType() {
        String value = getProperty("encapsulation_type");
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return FlowEncapsulationType.valueOf(value.toUpperCase());
    }

    @Override
    public void setEncapsulationType(FlowEncapsulationType encapsulationType) {
        setProperty("encapsulation_type", encapsulationType == null ? null : encapsulationType.name().toLowerCase());
    }

    @Override
    public FlowStatus getStatus() {
        String value = getProperty(STATUS_PROPERTY);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return FlowStatus.valueOf(value.toUpperCase());
    }

    @Override
    public void setStatus(FlowStatus status) {
        setProperty(STATUS_PROPERTY, status == null ? null : status.name().toLowerCase());
    }

    @Override
    public Integer getMaxLatency() {
        Long value = getProperty("max_latency");
        return value == null ? null : value.intValue();
    }

    @Override
    public void setMaxLatency(Integer maxLatency) {
        setProperty("max_latency", maxLatency == null ? null : maxLatency.longValue());
    }

    @Override
    public Integer getPriority() {
        Long value = getProperty("priority");
        return value == null ? null : value.intValue();
    }

    @Override
    public void setPriority(Integer priority) {
        setProperty("priority", priority == null ? null : priority.longValue());
    }

    @Override
    public Instant getTimeCreate() {
        String value = getProperty("time_create");
        return value == null ? null : Instant.parse(value);
    }

    @Override
    public void setTimeCreate(Instant timeCreate) {
        setProperty("time_create", timeCreate == null ? null : timeCreate.toString());
    }

    @Override
    public Instant getTimeModify() {
        String value = getProperty("time_modify");
        return value == null ? null : Instant.parse(value);
    }

    @Override
    public void setTimeModify(Instant timeModify) {
        setProperty("time_modify", timeModify == null ? null : timeModify.toString());
    }

    @Property("pinned")
    @Override
    public abstract boolean isPinned();

    @Property("pinned")
    @Override
    public abstract void setPinned(boolean pinned);

    @Override
    public Switch getSrcSwitch() {
        return traverse(v -> v.out(SOURCE_EDGE).hasLabel(SwitchFrame.FRAME_LABEL)).nextExplicit(SwitchFrame.class);
    }

    @Override
    public SwitchId getSrcSwitchId() {
        return new SwitchId(traverse(v -> v.out(SOURCE_EDGE).hasLabel(SwitchFrame.FRAME_LABEL)
                .values(SwitchFrame.SWITCH_ID_PROPERTY)).nextExplicit(String.class));
    }

    @Override
    public void setSrcSwitch(Switch srcSwitch) {
        traverse(v -> v.out(SOURCE_EDGE).hasLabel(SwitchFrame.FRAME_LABEL)).toListExplicit(SwitchFrame.class)
                .forEach(sw -> unlinkOut(sw, SOURCE_EDGE));
        if (srcSwitch instanceof VertexFrame) {
            linkOut((VertexFrame) srcSwitch, SOURCE_EDGE);
        } else {
            SwitchFrame switchFrame = SwitchFrame.load(getGraph(), srcSwitch.getSwitchId());
            if (switchFrame == null) {
                throw new IllegalArgumentException("Unable to link to unknown switch " + srcSwitch.getSwitchId());
            }
            linkOut(switchFrame, SOURCE_EDGE);
        }
    }

    @Override
    public Switch getDestSwitch() {
        return traverse(v -> v.out(DESTINATION_EDGE).hasLabel(SwitchFrame.FRAME_LABEL)).nextExplicit(SwitchFrame.class);
    }

    @Override
    public SwitchId getDestSwitchId() {
        return new SwitchId(traverse(v -> v.out(DESTINATION_EDGE).hasLabel(SwitchFrame.FRAME_LABEL)
                .values(SwitchFrame.SWITCH_ID_PROPERTY)).nextExplicit(String.class));
    }

    @Override
    public void setDestSwitch(Switch destSwitch) {
        traverse(v -> v.out(DESTINATION_EDGE).hasLabel(SwitchFrame.FRAME_LABEL)).toListExplicit(SwitchFrame.class)
                .forEach(sw -> unlinkOut(sw, DESTINATION_EDGE));
        if (destSwitch instanceof VertexFrame) {
            linkOut((VertexFrame) destSwitch, DESTINATION_EDGE);
        } else {
            SwitchFrame switchFrame = SwitchFrame.load(getGraph(), destSwitch.getSwitchId());
            if (switchFrame == null) {
                throw new IllegalArgumentException("Unable to link to unknown switch " + destSwitch.getSwitchId());
            }

            linkOut(switchFrame, DESTINATION_EDGE);
        }
    }

    @Override
    public Set<FlowPath> getPaths() {
        return Collections.unmodifiableSet(traverse(v -> v.out(OWNS_PATHS_EDGE).hasLabel(FlowPathFrame.FRAME_LABEL))
                .toSetExplicit(FlowPathFrame.class));
    }

    @Override
    public void setPaths(Set<FlowPath> paths) {
        Set<PathId> actualPathIds = paths.stream().map(FlowPath::getPathId).collect(Collectors.toSet());

        addPaths(paths.toArray(new FlowPath[paths.size()]));

        traverse(v -> v.out(OWNS_PATHS_EDGE).hasLabel(FlowPathFrame.FRAME_LABEL))
                .toListExplicit(FlowPathFrame.class)
                .forEach(pathFrame -> {
                    if (!actualPathIds.contains(pathFrame.getPathId())) {
                        unlinkOut(pathFrame, OWNS_PATHS_EDGE);
                        pathFrame.delete();
                    }
                });
    }

    @Override
    public void addPaths(FlowPath... paths) {
        for (FlowPath path : paths) {
            if (path instanceof FlowPathFrame) {
                linkOut((VertexFrame) path, OWNS_PATHS_EDGE);
            } else {
                FlowPathFrame pathFrame = FlowPathFrame.load(getGraph(), path.getPathId());
                if (pathFrame != null) {
                    pathFrame.updateWith(path);
                } else {
                    pathFrame = FlowPathFrame.addNew(getGraph(), path);
                }
                linkOut(pathFrame, OWNS_PATHS_EDGE);
            }
        }
    }

    @Override
    public void removePaths(PathId... pathIds) {
        Set<PathId> pathIdsToRemove = new HashSet(Arrays.asList(pathIds));

        traverse(v -> v.out(OWNS_PATHS_EDGE).hasLabel(FlowPathFrame.FRAME_LABEL))
                .toListExplicit(FlowPathFrame.class)
                .forEach(pathFrame -> {
                    if (pathIdsToRemove.contains(pathFrame.getPathId())) {
                        unlinkOut(pathFrame, OWNS_PATHS_EDGE);
                        pathFrame.delete();
                    }
                });
    }

    public void updateWith(Flow flow) {
        setSrcPort(flow.getSrcPort());
        setSrcVlan(flow.getSrcVlan());
        setDestPort(flow.getDestPort());
        setDestVlan(flow.getDestVlan());
        setForwardPathId(flow.getForwardPathId());
        setReversePathId(flow.getReversePathId());
        setAllocateProtectedPath(flow.isAllocateProtectedPath());
        setProtectedForwardPathId(flow.getProtectedForwardPathId());
        setProtectedReversePathId(flow.getProtectedReversePathId());
        setGroupId(flow.getGroupId());
        setBandwidth(flow.getBandwidth());
        setIgnoreBandwidth(flow.isIgnoreBandwidth());
        setDescription(flow.getDescription());
        setPeriodicPings(flow.isPeriodicPings());
        setEncapsulationType(flow.getEncapsulationType());
        setStatus(flow.getStatus());
        setMaxLatency(flow.getMaxLatency());
        setPriority(flow.getPriority());
        setTimeModify(flow.getTimeModify());
        setPinned(flow.isPinned());
        setSrcSwitch(flow.getSrcSwitch());
        setDestSwitch(flow.getDestSwitch());
        setPaths(getPaths());
    }

    public void delete() {
        traverse(v -> v.out(OWNS_PATHS_EDGE).hasLabel(FlowPathFrame.FRAME_LABEL)).toListExplicit(FlowPathFrame.class)
                .forEach(pathFrame -> pathFrame.delete());
        remove();
    }

    public static FlowFrame addNew(FramedGraph graph, Flow newFlow) {
        // A workaround for improper implementation of the untyped mode in OrientTransactionFactoryImpl.
        Vertex element = ((DelegatingFramedGraph) graph).getBaseGraph().addVertex(T.label, FRAME_LABEL);
        FlowFrame frame = graph.frameElementExplicit(element, FlowFrame.class);
        frame.setFlowId(newFlow.getFlowId());
        frame.setTimeCreate(newFlow.getTimeCreate());
        frame.updateWith(newFlow);
        return frame;
    }

    public static FlowFrame load(FramedGraph graph, String flowId) {
        /*long t1 = Instant.now().toEpochMilli(), t2 = 0;
        try {*/
        final Traversable<?, ?> traverse = graph.traverse(input -> input.V().hasLabel(FRAME_LABEL).has(FLOW_ID_PROPERTY, flowId));
        org.apache.tinkerpop.gremlin.structure.Element obj = (org.apache.tinkerpop.gremlin.structure.Element) traverse.getRawTraversal().next();
        //t2 = Instant.now().toEpochMilli();
        return graph.frameElementExplicit(obj, FlowFrame.class);
        /*} finally {
            System.out.println("Times: " + t1 + ", " + t2 + ", " + Instant.now().toEpochMilli());
        }*/
    }

    public static void delete(FramedGraph graph, Flow flow) {
        if (flow instanceof FlowFrame) {
            ((FlowFrame) flow).delete();
        } else {
            FlowFrame flowFrame = load(graph, flow.getFlowId());
            if (flowFrame != null) {
                flowFrame.delete();
            }
        }
    }
}
