/* Copyright 2020 Telstra Open Source
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

import org.openkilda.model.Flow;
import org.openkilda.model.FlowApplication;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath.FlowPathData;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.GroupId;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.FlowApplicationConverter;
import org.openkilda.persistence.ferma.frames.converters.FlowPathStatusConverter;
import org.openkilda.persistence.ferma.frames.converters.FlowSegmentCookieConverter;
import org.openkilda.persistence.ferma.frames.converters.GroupIdConverter;
import org.openkilda.persistence.ferma.frames.converters.MeterIdConverter;
import org.openkilda.persistence.ferma.frames.converters.PathIdConverter;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;

import com.syncleus.ferma.annotations.Property;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public abstract class FlowPathFrame extends KildaBaseVertexFrame implements FlowPathData {
    public static final String FRAME_LABEL = "flow_path";
    public static final String OWNS_SEGMENTS_EDGE = "owns";
    public static final String HAS_SEGMENTS_EDGE = "has";
    public static final String PATH_ID_PROPERTY = "path_id";
    public static final String FLOW_ID_PROPERTY = "flow_id";
    public static final String SRC_SWITCH_ID_PROPERTY = "src_switch_id";
    public static final String DST_SWITCH_ID_PROPERTY = "dst_switch_id";
    public static final String COOKIE_PROPERTY = "cookie";
    public static final String IGNORE_BANDWIDTH_PROPERTY = "ignore_bandwidth";
    public static final String BANDWIDTH_PROPERTY = "bandwidth";
    public static final String SRC_MULTI_TABLE_PROPERTY = "src_with_multi_table";
    public static final String DST_MULTI_TABLE_PROPERTY = "dst_with_multi_table";

    private Switch srcSwitch;
    private Switch destSwitch;
    private Flow flow;
    private List<PathSegment> segments;
    private Set<FlowMirrorPoints> flowMirrorPointsSet;

    @Override
    @Property(PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract PathId getPathId();

    @Override
    @Property(PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract void setPathId(@NonNull PathId pathId);

    @Override
    @Property(FLOW_ID_PROPERTY)
    public abstract String getFlowId();

    @Override
    @Property(SRC_SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getSrcSwitchId();

    @Override
    @Property(DST_SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getDestSwitchId();

    @Override
    @Property(COOKIE_PROPERTY)
    @Convert(FlowSegmentCookieConverter.class)
    public abstract FlowSegmentCookie getCookie();

    @Override
    @Property(COOKIE_PROPERTY)
    @Convert(FlowSegmentCookieConverter.class)
    public abstract void setCookie(FlowSegmentCookie cookie);

    @Override
    @Property("meter_id")
    @Convert(MeterIdConverter.class)
    public abstract MeterId getMeterId();

    @Override
    @Property("meter_id")
    @Convert(MeterIdConverter.class)
    public abstract void setMeterId(MeterId meterId);

    @Override
    public long getLatency() {
        return Optional.ofNullable((Long) getProperty("latency")).orElse(0L);
    }

    @Override
    @Property("latency")
    public abstract void setLatency(long latency);

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
    @Property("status")
    @Convert(FlowPathStatusConverter.class)
    public abstract FlowPathStatus getStatus();

    @Override
    @Property("status")
    @Convert(FlowPathStatusConverter.class)
    public abstract void setStatus(FlowPathStatus status);

    @Override
    @Property("ingress_mirror_group_id")
    @Convert(GroupIdConverter.class)
    public abstract void setIngressMirrorGroupId(GroupId meterId);

    @Override
    @Property("ingress_mirror_group_id")
    @Convert(GroupIdConverter.class)
    public abstract GroupId getIngressMirrorGroupId();

    @Override
    @Property(SRC_MULTI_TABLE_PROPERTY)
    public abstract boolean isSrcWithMultiTable();

    @Override
    @Property(SRC_MULTI_TABLE_PROPERTY)
    public abstract void setSrcWithMultiTable(boolean srcWithMultiTable);

    @Override
    @Property(DST_MULTI_TABLE_PROPERTY)
    public abstract boolean isDestWithMultiTable();

    @Override
    @Property(DST_MULTI_TABLE_PROPERTY)
    public abstract void setDestWithMultiTable(boolean destWithMultiTable);


    @Override
    public Set<FlowApplication> getApplications() {
        Set<FlowApplication> results = new HashSet<>();
        getElement().properties("applications").forEachRemaining(property -> {
            if (property.isPresent()) {
                Object propertyValue = property.value();
                if (propertyValue instanceof Collection) {
                    ((Collection<String>) propertyValue).forEach(entry ->
                            results.add(FlowApplicationConverter.INSTANCE.toEntityAttribute(entry)));
                } else {
                    results.add(FlowApplicationConverter.INSTANCE.toEntityAttribute((String) propertyValue));
                }
            }
        });
        return results;
    }

    @Override
    public void setApplications(Set<FlowApplication> applications) {
        getElement().property(VertexProperty.Cardinality.set, "applications", applications.stream()
                .map(FlowApplicationConverter.INSTANCE::toGraphProperty).collect(Collectors.toSet()));
    }

    @Override
    public Switch getSrcSwitch() {
        if (srcSwitch == null) {
            srcSwitch = SwitchFrame.load(getGraph(), getProperty(SRC_SWITCH_ID_PROPERTY))
                    .map(Switch::new).orElse(null);
        }
        return srcSwitch;
    }

    @Override
    public void setSrcSwitch(Switch srcSwitch) {
        this.srcSwitch = srcSwitch;
        String switchId = SwitchIdConverter.INSTANCE.toGraphProperty(srcSwitch.getSwitchId());
        setProperty(SRC_SWITCH_ID_PROPERTY, switchId);
    }

    @Override
    public Switch getDestSwitch() {
        if (destSwitch == null) {
            destSwitch = SwitchFrame.load(getGraph(), getProperty(DST_SWITCH_ID_PROPERTY))
                    .map(Switch::new).orElse(null);
        }
        return destSwitch;
    }

    @Override
    public void setDestSwitch(Switch destSwitch) {
        this.destSwitch = destSwitch;
        String switchId = SwitchIdConverter.INSTANCE.toGraphProperty(destSwitch.getSwitchId());
        setProperty(DST_SWITCH_ID_PROPERTY, switchId);
    }

    @Override
    public List<PathSegment> getSegments() {
        if (segments == null) {
            segments = traverse(v -> v.out(OWNS_SEGMENTS_EDGE)
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
        getElement().edges(Direction.OUT, OWNS_SEGMENTS_EDGE)
                .forEachRemaining(edge -> {
                    edge.inVertex().remove();
                    edge.remove();
                });

        PathId pathId = getPathId();
        for (int idx = 0; idx < segments.size(); idx++) {
            PathSegment segment = segments.get(idx);
            PathSegment.PathSegmentData data = segment.getData();
            data.setPathId(pathId);
            data.setSeqId(idx);

            PathSegmentFrame frame;
            if (data instanceof PathSegmentFrame) {
                frame = (PathSegmentFrame) data;
                // Unlink the path from the previous owner.
                frame.getElement().edges(Direction.IN, OWNS_SEGMENTS_EDGE)
                        .forEachRemaining(Edge::remove);
            } else {
                frame = PathSegmentFrame.create(getGraph(), data);
            }
            linkOut(frame, OWNS_SEGMENTS_EDGE);
        }

        // force to reload
        this.segments = null;
    }

    @Override
    public Flow getFlow() {
        if (flow == null) {
            List<? extends FlowFrame> flowFrames = traverse(v -> v.in(FlowFrame.OWNS_PATHS_EDGE)
                    .hasLabel(FlowFrame.FRAME_LABEL))
                    .toListExplicit(FlowFrame.class);
            flow = !flowFrames.isEmpty() ? new Flow(flowFrames.get(0)) : null;
            String flowId = flow != null ? flow.getFlowId() : null;
            if (!Objects.equals(getFlowId(), flowId)) {
                throw new IllegalStateException(format("The flow path %s has inconsistent flow_id %s / %s",
                        getId(), getFlowId(), flowId));
            }
        }
        return flow;
    }

    @Override
    public Set<FlowMirrorPoints> getFlowMirrorPointsSet() {
        if (flowMirrorPointsSet == null) {
            flowMirrorPointsSet = traverse(v -> v.out(HAS_SEGMENTS_EDGE)
                    .hasLabel(FlowMirrorPointsFrame.FRAME_LABEL))
                    .toListExplicit(FlowMirrorPointsFrame.class).stream()
                    .map(FlowMirrorPoints::new)
                    .collect(Collectors.toSet());
        }
        return Collections.unmodifiableSet(flowMirrorPointsSet);
    }

    @Override
    public void addFlowMirrorPoints(FlowMirrorPoints flowMirrorPoints) {
        FlowMirrorPoints.FlowMirrorPointsData data = flowMirrorPoints.getData();
        FlowMirrorPointsFrame frame;
        if (data instanceof FlowMirrorPointsFrame) {
            frame = (FlowMirrorPointsFrame) data;
            // Unlink the mirror points from the previous owner.
            frame.getElement().edges(Direction.IN, FlowPathFrame.HAS_SEGMENTS_EDGE)
                    .forEachRemaining(Edge::remove);
        } else {
            // We intentionally don't allow to add transient entities.
            // A path must be added via corresponding repository first.
            throw new IllegalArgumentException("Unable to link to transient flow mirror points " + flowMirrorPoints);
        }
        PathId flowPathId = getPathId();
        frame.setProperty(FlowMirrorPointsFrame.FLOW_PATH_ID_PROPERTY,
                PathIdConverter.INSTANCE.toGraphProperty(flowPathId));
        linkOut(frame, HAS_SEGMENTS_EDGE);
        flowMirrorPoints.getMirrorGroup().setPathId(flowPathId);
        if (this.flowMirrorPointsSet != null) {
            this.flowMirrorPointsSet.add(flowMirrorPoints);
        }
    }
}
