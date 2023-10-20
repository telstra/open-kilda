/* Copyright 2021 Telstra Open Source
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

import org.openkilda.model.FlowMirrorPath.FlowMirrorPathData;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.FlowPathStatusConverter;
import org.openkilda.persistence.ferma.frames.converters.FlowSegmentCookieConverter;
import org.openkilda.persistence.ferma.frames.converters.PathIdConverter;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;

import com.syncleus.ferma.annotations.Property;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public abstract class FlowMirrorPathFrame extends KildaBaseVertexFrame implements FlowMirrorPathData {
    public static final String FRAME_LABEL = "flow_mirror_path";
    public static final String OWNS_SEGMENTS_EDGE = "owns";
    public static final String PATH_ID_PROPERTY = "path_id";
    public static final String MIRROR_SWITCH_ID_PROPERTY = "mirror_switch_id";
    public static final String EGRESS_SWITCH_ID_PROPERTY = "egress_switch_id";
    public static final String EGRESS_PORT_PROPERTY = "egress_port";
    public static final String EGRESS_OUTER_VLAN_PROPERTY = "egress_outer_vlan";
    public static final String EGRESS_INNER_VLAN_PROPERTY = "egress_inner_vlan";
    public static final String COOKIE_PROPERTY = "cookie";
    public static final String IGNORE_BANDWIDTH_PROPERTY = "ignore_bandwidth";
    public static final String BANDWIDTH_PROPERTY = "bandwidth";

    private Switch srcSwitch;
    private Switch destSwitch;
    private FlowMirrorPoints flowMirrorPoints;
    private List<PathSegment> segments;

    @Override
    @Property(PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract PathId getPathId();

    @Override
    @Property(PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract void setPathId(@NonNull PathId pathId);

    @Override
    @Property(MIRROR_SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getMirrorSwitchId();

    @Override
    @Property(EGRESS_SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getEgressSwitchId();

    @Override
    @Property(EGRESS_PORT_PROPERTY)
    public abstract int getEgressPort();

    @Override
    @Property(EGRESS_PORT_PROPERTY)
    public abstract void setEgressPort(int egressPort);

    @Override
    @Property(EGRESS_OUTER_VLAN_PROPERTY)
    public abstract int getEgressOuterVlan();

    @Override
    @Property(EGRESS_OUTER_VLAN_PROPERTY)
    public abstract void setEgressOuterVlan(int egressOuterVlan);

    @Override
    @Property(EGRESS_INNER_VLAN_PROPERTY)
    public abstract int getEgressInnerVlan();

    @Override
    @Property(EGRESS_INNER_VLAN_PROPERTY)
    public abstract void setEgressInnerVlan(int egressInnerVlan);

    @Override
    @Property(COOKIE_PROPERTY)
    @Convert(FlowSegmentCookieConverter.class)
    public abstract FlowSegmentCookie getCookie();

    @Override
    @Property(COOKIE_PROPERTY)
    @Convert(FlowSegmentCookieConverter.class)
    public abstract void setCookie(FlowSegmentCookie cookie);

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
    public Switch getMirrorSwitch() {
        if (srcSwitch == null) {
            srcSwitch = SwitchFrame.load(getGraph(), getProperty(MIRROR_SWITCH_ID_PROPERTY))
                    .map(Switch::new).orElse(null);
        }
        return srcSwitch;
    }

    @Override
    public void setMirrorSwitch(Switch mirrorSwitch) {
        this.srcSwitch = mirrorSwitch;
        String switchId = SwitchIdConverter.INSTANCE.toGraphProperty(mirrorSwitch.getSwitchId());
        setProperty(MIRROR_SWITCH_ID_PROPERTY, switchId);
    }

    @Override
    public Switch getEgressSwitch() {
        if (destSwitch == null) {
            destSwitch = SwitchFrame.load(getGraph(), getProperty(EGRESS_SWITCH_ID_PROPERTY))
                    .map(Switch::new).orElse(null);
        }
        return destSwitch;
    }

    @Override
    public void setEgressSwitch(Switch egressSwitch) {
        this.destSwitch = egressSwitch;
        String switchId = SwitchIdConverter.INSTANCE.toGraphProperty(egressSwitch.getSwitchId());
        setProperty(EGRESS_SWITCH_ID_PROPERTY, switchId);
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
    public FlowMirrorPoints getFlowMirrorPoints() {
        if (flowMirrorPoints == null) {
            List<? extends FlowMirrorPointsFrame> flowMirrorPointsFrames =
                    traverse(v -> v.in(FlowMirrorPointsFrame.OWNS_MIRROR_PATHS_EDGE)
                            .hasLabel(FlowMirrorPointsFrame.FRAME_LABEL))
                            .toListExplicit(FlowMirrorPointsFrame.class);
            flowMirrorPoints = !flowMirrorPointsFrames.isEmpty()
                    ? new FlowMirrorPoints(flowMirrorPointsFrames.get(0)) : null;
        }
        return flowMirrorPoints;
    }
}
