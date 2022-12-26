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

import static java.lang.String.format;

import org.openkilda.model.FlowMirror.FlowMirrorData;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.FlowPathStatusConverter;
import org.openkilda.persistence.ferma.frames.converters.PathIdConverter;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;

import com.syncleus.ferma.annotations.Property;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public abstract class FlowMirrorFrame extends KildaBaseVertexFrame implements FlowMirrorData {
    public static final String FRAME_LABEL = "flow_mirror";
    public static final String OWNS_PATH_EDGE = "owns";
    public static final String FLOW_MIRROR_ID_PROPERTY = "flow_mirror_id";
    public static final String FORWARD_PATH_ID_PROPERTY = "forward_path_id";
    public static final String MIRROR_SWITCH_ID_PROPERTY = "mirror_switch_id";
    public static final String EGRESS_SWITCH_ID_PROPERTY = "egress_switch_id";
    public static final String EGRESS_PORT_PROPERTY = "egress_port";
    public static final String EGRESS_OUTER_VLAN_PROPERTY = "egress_outer_vlan";
    public static final String EGRESS_INNER_VLAN_PROPERTY = "egress_inner_vlan";
    public static final String STATUS_PROPERTY = "status";

    private Switch srcSwitch;
    private Switch destSwitch;
    private FlowMirrorPoints flowMirrorPoints;
    private Map<PathId, FlowMirrorPath> mirrorPaths;

    @Override
    @Property(FLOW_MIRROR_ID_PROPERTY)
    public abstract String getFlowMirrorId();

    @Override
    @Property(FLOW_MIRROR_ID_PROPERTY)
    public abstract void setFlowMirrorId(String flowMirrorId);

    @Override
    @Property(FORWARD_PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract PathId getForwardPathId();

    @Override
    @Property(FORWARD_PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract void setForwardPathId(@NonNull PathId forwardPathId);

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
    @Property(STATUS_PROPERTY)
    @Convert(FlowPathStatusConverter.class)
    public abstract FlowPathStatus getStatus();

    @Override
    @Property(STATUS_PROPERTY)
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
    public Set<FlowMirrorPath> getMirrorPaths() {
        if (mirrorPaths == null) {
            mirrorPaths = traverse(v -> v.out(OWNS_PATH_EDGE)
                    .hasLabel(FlowMirrorPathFrame.FRAME_LABEL))
                    .toListExplicit(FlowMirrorPathFrame.class).stream()
                    .map(FlowMirrorPath::new)
                    .collect(Collectors.toMap(FlowMirrorPath::getMirrorPathId, v -> v));
        }
        return new HashSet<>(mirrorPaths.values());
    }

    @Override
    public Optional<FlowMirrorPath> getPath(PathId flowMirrorPathId) {
        if (mirrorPaths == null) {
            // init the cache map with paths.
            getMirrorPaths();
        }
        return Optional.ofNullable(mirrorPaths.get(flowMirrorPathId));
    }

    @Override
    public void addMirrorPaths(FlowMirrorPath... paths) {
        for (FlowMirrorPath path : paths) {
            FlowMirrorPath.FlowMirrorPathData data = path.getData();
            FlowMirrorPathFrame frame;
            if (data instanceof FlowMirrorPathFrame) {
                frame = (FlowMirrorPathFrame) data;
                // Unlink the path from the previous owner.
                frame.getElement().edges(Direction.IN, FlowMirrorFrame.OWNS_PATH_EDGE)
                        .forEachRemaining(Edge::remove);
            } else {
                // We intentionally don't allow to add transient entities.
                // A path must be added via corresponding repository first.
                throw new IllegalArgumentException(format("Unable to link to transient flow mirror path %s", path));
            }
            frame.setProperty(FlowMirrorPathFrame.FLOW_MIRROR_ID_PROPERTY, getFlowMirrorId());
            linkOut(frame, FlowMirrorFrame.OWNS_PATH_EDGE);
            if (this.mirrorPaths != null) {
                this.mirrorPaths.put(path.getMirrorPathId(), path);
            }
            //TODO force reload?
        }
    }

    @Override
    public boolean hasPath(FlowMirrorPath path) {
        getMirrorPaths();
        return mirrorPaths.containsKey(path.getMirrorPathId());
    }

    @Override
    public FlowMirrorPoints getFlowMirrorPoints() {
        if (flowMirrorPoints == null) {
            List<? extends FlowMirrorPointsFrame> flowMirrorPointsFrames =
                    traverse(v -> v.in(FlowMirrorPointsFrame.OWNS_FLOW_MIRROR_EDGE)
                            .hasLabel(FlowMirrorPointsFrame.FRAME_LABEL))
                            .toListExplicit(FlowMirrorPointsFrame.class);
            flowMirrorPoints = !flowMirrorPointsFrames.isEmpty()
                    ? new FlowMirrorPoints(flowMirrorPointsFrames.get(0)) : null;
        }
        return flowMirrorPoints;
    }
}
