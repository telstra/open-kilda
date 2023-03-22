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

import static java.lang.String.format;

import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.GroupId;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath.HaFlowPathData;
import org.openkilda.model.HaSubFlowEdge;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.FlowPathStatusConverter;
import org.openkilda.persistence.ferma.frames.converters.FlowSegmentCookieConverter;
import org.openkilda.persistence.ferma.frames.converters.GroupIdConverter;
import org.openkilda.persistence.ferma.frames.converters.MeterIdConverter;
import org.openkilda.persistence.ferma.frames.converters.PathIdConverter;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;

import com.syncleus.ferma.FramedGraph;
import com.syncleus.ferma.annotations.Property;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public abstract class HaFlowPathFrame extends KildaBaseVertexFrame implements HaFlowPathData {
    public static final String FRAME_LABEL = "ha_flow_path";
    public static final String OWNS_SEGMENTS_EDGE = "owns";
    public static final String HA_PATH_ID_PROPERTY = "ha_path_id";
    public static final String HA_FLOW_ID_PROPERTY = "ha_flow_id";
    public static final String SHARED_SWITCH_ID_PROPERTY = "shared_switch_id";
    public static final String Y_POINT_SWITCH_ID_PROPERTY = "y_point_switch_id";
    public static final String COOKIE_PROPERTY = "cookie";
    public static final String Y_POINT_METER_ID = "y_point_meter_id";
    public static final String SHARED_POINT_METER_ID = "shared_point_meter_id";
    public static final String Y_POINT_GROUP_ID = "y_point_group_id";
    public static final String IGNORE_BANDWIDTH_PROPERTY = "ignore_bandwidth";
    public static final String BANDWIDTH_PROPERTY = "bandwidth";
    public static final String STATUS_PROPERTY = "status";
    public static final String SHARED_BANDWIDTH_GROUP_ID_PROPERTY = "shared_bw_group_id";

    private Switch sharedSwitch;
    private HaFlow haFlow;
    private List<PathSegment> segments;
    private Set<HaSubFlowEdge> subFlowEdges;

    @Override
    @Property(HA_PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract PathId getHaPathId();

    @Override
    @Property(HA_PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract void setHaPathId(@NonNull PathId haPathId);

    @Override
    @Property(HA_FLOW_ID_PROPERTY)
    public abstract String getHaFlowId();

    @Override
    @Property(SHARED_SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getSharedSwitchId();

    @Override
    @Property(Y_POINT_SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getYPointSwitchId();

    @Override
    @Property(Y_POINT_SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract void setYPointSwitchId(SwitchId yPointSwitchId);

    @Override
    @Property(COOKIE_PROPERTY)
    @Convert(FlowSegmentCookieConverter.class)
    public abstract FlowSegmentCookie getCookie();

    @Override
    @Property(COOKIE_PROPERTY)
    @Convert(FlowSegmentCookieConverter.class)
    public abstract void setCookie(FlowSegmentCookie cookie);

    @Override
    @Property(Y_POINT_METER_ID)
    @Convert(MeterIdConverter.class)
    public abstract MeterId getYPointMeterId();

    @Override
    @Property(Y_POINT_METER_ID)
    @Convert(MeterIdConverter.class)
    public abstract void setYPointMeterId(MeterId meterId);

    @Override
    @Property(SHARED_POINT_METER_ID)
    @Convert(MeterIdConverter.class)
    public abstract MeterId getSharedPointMeterId();

    @Override
    @Property(SHARED_POINT_METER_ID)
    @Convert(MeterIdConverter.class)
    public abstract void setSharedPointMeterId(MeterId meterId);

    @Override
    @Property(Y_POINT_GROUP_ID)
    @Convert(GroupIdConverter.class)
    public abstract GroupId getYPointGroupId();

    @Override
    @Property(Y_POINT_GROUP_ID)
    @Convert(GroupIdConverter.class)
    public abstract void setYPointGroupId(GroupId meterId);

    @Override
    @Property(BANDWIDTH_PROPERTY)
    public abstract long getBandwidth();

    @Override
    @Property(BANDWIDTH_PROPERTY)
    public abstract void setBandwidth(long bandwidth);

    @Override
    @Property(IGNORE_BANDWIDTH_PROPERTY)
    public abstract boolean isIgnoreBandwidth();

    @Override
    @Property(IGNORE_BANDWIDTH_PROPERTY)
    public abstract void setIgnoreBandwidth(boolean ignoreBandwidth);

    @Override
    @Property(STATUS_PROPERTY)
    @Convert(FlowPathStatusConverter.class)
    public abstract FlowPathStatus getStatus();

    @Override
    @Property(STATUS_PROPERTY)
    @Convert(FlowPathStatusConverter.class)
    public abstract void setStatus(FlowPathStatus status);

    @Override
    @Property(SHARED_BANDWIDTH_GROUP_ID_PROPERTY)
    public abstract String getSharedBandwidthGroupId();

    @Override
    @Property(SHARED_BANDWIDTH_GROUP_ID_PROPERTY)
    public abstract void setSharedBandwidthGroupId(String sharedBandwidthGroupId);

    @Override
    public Switch getSharedSwitch() {
        if (sharedSwitch == null) {
            sharedSwitch = SwitchFrame.load(getGraph(), getProperty(SHARED_SWITCH_ID_PROPERTY))
                    .map(Switch::new).orElse(null);
        }
        return sharedSwitch;
    }

    @Override
    public void setSharedSwitch(Switch sharedSwitch) {
        this.sharedSwitch = sharedSwitch;
        String switchId = SwitchIdConverter.INSTANCE.toGraphProperty(sharedSwitch.getSwitchId());
        setProperty(SHARED_SWITCH_ID_PROPERTY, switchId);
    }

    @Override
    public List<PathSegment> getSegments() {
        if (segments == null) {
            segments = traverse(v -> v.out(HaFlowPathFrame.OWNS_SEGMENTS_EDGE)
                    .hasLabel(PathSegmentFrame.FRAME_LABEL))
                    .toListExplicit(PathSegmentFrame.class).stream()
                    .map(PathSegment::new)
                    .sorted(Comparator.comparingInt(PathSegment::getSeqId))
                    .collect(Collectors.toList());
        }
        return segments;
    }

    @Override
    public void setSegments(List<PathSegment> segments) {
        getElement().edges(Direction.OUT, HaFlowPathFrame.OWNS_SEGMENTS_EDGE)
                .forEachRemaining(edge -> {
                    edge.inVertex().remove();
                    edge.remove();
                });

        PathId pathId = getHaPathId();
        for (int idx = 0; idx < segments.size(); idx++) {
            PathSegment segment = segments.get(idx);
            PathSegment.PathSegmentData data = segment.getData();
            data.setPathId(pathId);
            data.setSeqId(idx);

            PathSegmentFrame frame;
            if (data instanceof PathSegmentFrame) {
                frame = (PathSegmentFrame) data;
                // Unlink the path from the previous owner.
                frame.getElement().edges(Direction.IN, HaFlowPathFrame.OWNS_SEGMENTS_EDGE)
                        .forEachRemaining(Edge::remove);
            } else {
                frame = PathSegmentFrame.create(getGraph(), data);
            }
            linkOut(frame, HaFlowPathFrame.OWNS_SEGMENTS_EDGE);
        }

        // force to reload
        this.segments = null;
    }

    @Override
    public Set<HaSubFlowEdge> getHaSubFlowEdges() {
        if (subFlowEdges == null) {
            subFlowEdges = traverse(v -> v.outE(HaSubFlowEdgeFrame.FRAME_LABEL))
                    .toListExplicit(HaSubFlowEdgeFrame.class).stream()
                    .map(HaSubFlowEdge::new)
                    .collect(Collectors.toSet());
        }
        return subFlowEdges;
    }

    @Override
    public void setHaSubFlowEdges(Collection<HaSubFlowEdge> haSubFlowEdges) {
        getElement().edges(Direction.OUT, HaSubFlowEdgeFrame.FRAME_LABEL)
                .forEachRemaining(Element::remove);

        haSubFlowEdges.forEach(edge -> edge.setData(HaSubFlowEdgeFrame.create(
                getGraph(), edge.getData(), getHaPathId())));

        // force to reload
        this.subFlowEdges = null;
    }

    @Override
    public HaFlow getHaFlow() {
        if (haFlow == null) {
            List<? extends HaFlowFrame> haFlowFrames = traverse(v -> v.in(HaFlowFrame.OWNS_PATHS_EDGE)
                    .hasLabel(HaFlowFrame.FRAME_LABEL))
                    .toListExplicit(HaFlowFrame.class);
            haFlow = !haFlowFrames.isEmpty() ? new HaFlow(haFlowFrames.get(0)) : null;
            String flowId = haFlow != null ? haFlow.getHaFlowId() : null;
            if (!Objects.equals(getHaFlowId(), flowId)) {
                throw new IllegalStateException(format("The HA-flow path %s has inconsistent ha_flow_id %s / %s",
                        getId(), getHaFlowId(), flowId));
            }
        }
        return haFlow;
    }

    public static Optional<HaFlowPathFrame> load(FramedGraph graph, PathId haPathId) {
        List<? extends HaFlowPathFrame> haPathFrames = graph.traverse(g -> g.V()
                        .hasLabel(HaFlowPathFrame.FRAME_LABEL)
                        .has(HaFlowPathFrame.HA_PATH_ID_PROPERTY, PathIdConverter.INSTANCE.toGraphProperty(haPathId)))
                .toListExplicit(HaFlowPathFrame.class);
        return haPathFrames.isEmpty() ? Optional.empty() : Optional.of(haPathFrames.get(0));
    }
}
