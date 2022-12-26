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

import org.openkilda.model.FlowMirror;
import org.openkilda.model.FlowMirrorPath.FlowMirrorPathData;
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
    public static final String FLOW_MIRROR_ID_PROPERTY = "flow_mirror_id";
    public static final String MIRROR_SWITCH_ID_PROPERTY = "mirror_switch_id";
    public static final String EGRESS_SWITCH_ID_PROPERTY = "egress_switch_id";
    public static final String COOKIE_PROPERTY = "cookie";
    public static final String IGNORE_BANDWIDTH_PROPERTY = "ignore_bandwidth";
    public static final String STATUS_PROPERTY = "status";
    public static final String BANDWIDTH_PROPERTY = "bandwidth";
    public static final String EGRESS_MULTI_TABLE_PROPERTY = "egress_with_multi_table";
    public static final String DUMMY_PROPERTY = "dummy";

    private Switch srcSwitch;
    private Switch destSwitch;
    private FlowMirror flowMirror;
    private List<PathSegment> segments;

    @Override
    @Property(PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract PathId getMirrorPathId();

    @Override
    @Property(PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract void setMirrorPathId(@NonNull PathId pathId);

    @Override
    @Property(PATH_ID_PROPERTY)
    public abstract String getFlowMirrorId();

    @Override
    @Property(MIRROR_SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getMirrorSwitchId();

    @Override
    @Property(EGRESS_SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getEgressSwitchId();

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
    @Property(STATUS_PROPERTY)
    @Convert(FlowPathStatusConverter.class)
    public abstract FlowPathStatus getStatus();

    @Override
    @Property(STATUS_PROPERTY)
    @Convert(FlowPathStatusConverter.class)
    public abstract void setStatus(FlowPathStatus status);

    @Override
    @Property(EGRESS_MULTI_TABLE_PROPERTY)
    public abstract boolean isEgressWithMultiTable();

    @Override
    @Property(EGRESS_MULTI_TABLE_PROPERTY)
    public abstract void setEgressWithMultiTable(boolean egressWithMultiTable);

    @Override
    @Property(DUMMY_PROPERTY)
    public abstract boolean isDummy();

    @Override
    @Property(DUMMY_PROPERTY)
    public abstract void setDummy(boolean dummy);

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

        PathId pathId = getMirrorPathId();
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
    public FlowMirror getFlowMirror() {
        if (flowMirror == null) {
            List<? extends FlowMirrorFrame> flowMirrorFrames =
                    traverse(v -> v.in(FlowMirrorFrame.OWNS_PATH_EDGE)
                            .hasLabel(FlowMirrorFrame.FRAME_LABEL))
                            .toListExplicit(FlowMirrorFrame.class);
            flowMirror = !flowMirrorFrames.isEmpty() ? new FlowMirror(flowMirrorFrames.get(0)) : null;
        }
        return flowMirror;
    }
}
