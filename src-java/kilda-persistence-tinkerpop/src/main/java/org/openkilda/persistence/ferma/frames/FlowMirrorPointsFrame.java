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

import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPoints.FlowMirrorPointsData;
import org.openkilda.model.FlowPath;
import org.openkilda.model.GroupId;
import org.openkilda.model.MirrorGroup;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.GroupIdConverter;
import org.openkilda.persistence.ferma.frames.converters.PathIdConverter;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;

import com.syncleus.ferma.VertexFrame;
import com.syncleus.ferma.annotations.Property;
import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public abstract class FlowMirrorPointsFrame extends KildaBaseVertexFrame implements FlowMirrorPointsData {
    public static final String FRAME_LABEL = "flow_mirror_points";
    public static final String SOURCE_EDGE = "source";
    public static final String OWNS_MIRROR_PATHS_EDGE = "owns";
    public static final String HAS_MIRROR_GROUP_EDGE = "has";
    public static final String MIRROR_SWITCH_ID_PROPERTY = "mirror_switch_id";
    public static final String MIRROR_GROUP_ID_PROPERTY = "mirror_group_id";
    public static final String FLOW_PATH_ID_PROPERTY = "flow_path_id";

    private FlowPath flowPath;
    private Switch mirrorSwitch;
    private MirrorGroup mirrorGroup;
    private Set<PathId> pathIds;
    private Map<PathId, FlowMirrorPath> paths;


    @Override
    @Property(MIRROR_SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getMirrorSwitchId();

    @Override
    @Property(MIRROR_GROUP_ID_PROPERTY)
    @Convert(GroupIdConverter.class)
    public abstract GroupId getMirrorGroupId();

    @Override
    @Property(FLOW_PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract PathId getFlowPathId();

    @Override
    public Switch getMirrorSwitch() {
        if (mirrorSwitch == null) {
            List<? extends SwitchFrame> switchFrames = traverse(v -> v.out(SOURCE_EDGE)
                    .hasLabel(SwitchFrame.FRAME_LABEL))
                    .toListExplicit(SwitchFrame.class);
            if (!switchFrames.isEmpty()) {
                mirrorSwitch = new Switch((switchFrames.get(0)));

                if (!Objects.equals(getMirrorSwitchId(), mirrorSwitch.getSwitchId())) {
                    throw new IllegalStateException(
                            format("The flow mirror points %s has inconsistent mirror switch %s / %s",
                            getId(), getMirrorSwitchId(), mirrorSwitch.getSwitchId()));
                }
            } else {
                String switchId = getProperty(MIRROR_SWITCH_ID_PROPERTY);
                log.warn("Fallback to find the mirror switch by a reference instead of an edge. "
                        + "The switch {}, the vertex {}", switchId, this);
                mirrorSwitch = SwitchFrame.load(getGraph(), switchId)
                        .map(Switch::new).orElse(null);
            }
        }
        return mirrorSwitch;
    }

    @Override
    public void setMirrorSwitch(Switch mirrorSwitch) {
        this.mirrorSwitch = mirrorSwitch;
        String switchId = SwitchIdConverter.INSTANCE.toGraphProperty(mirrorSwitch.getSwitchId());
        setProperty(MIRROR_SWITCH_ID_PROPERTY, switchId);

        getElement().edges(Direction.OUT, SOURCE_EDGE).forEachRemaining(Edge::remove);
        Switch.SwitchData data = mirrorSwitch.getData();
        if (data instanceof SwitchFrame) {
            linkOut((VertexFrame) data, SOURCE_EDGE);
        } else {
            SwitchFrame frame = SwitchFrame.load(getGraph(), switchId).orElseThrow(() ->
                    new IllegalArgumentException("Unable to link to non-existent switch " + mirrorSwitch));
            linkOut(frame, SOURCE_EDGE);
        }
    }

    @Override
    public MirrorGroup getMirrorGroup() {
        if (mirrorGroup == null) {
            List<? extends MirrorGroupFrame> mirrorGroupFrames = traverse(v -> v.out(HAS_MIRROR_GROUP_EDGE)
                    .hasLabel(MirrorGroupFrame.FRAME_LABEL))
                    .toListExplicit(MirrorGroupFrame.class);
            if (!mirrorGroupFrames.isEmpty()) {
                mirrorGroup = new MirrorGroup((mirrorGroupFrames.get(0)));

                if (!Objects.equals(getMirrorGroupId(), mirrorGroup.getGroupId())) {
                    throw new IllegalStateException(
                            format("The flow mirror points %s has inconsistent mirror group %s / %s",
                                    getId(), getMirrorGroupId(), mirrorGroup.getGroupId()));
                }
            } else {
                String switchId = getProperty(MIRROR_SWITCH_ID_PROPERTY);
                String pathId = getProperty(FLOW_PATH_ID_PROPERTY);
                log.warn("Fallback to find the mirror group by a reference instead of an edge. "
                        + "The switch {}, the vertex {}", switchId, this);
                mirrorGroup = MirrorGroupFrame.load(getGraph(), switchId, pathId)
                        .map(MirrorGroup::new).orElse(null);
            }
        }
        return mirrorGroup;
    }

    @Override
    public void setMirrorGroup(MirrorGroup mirrorGroup) {
        this.mirrorGroup = mirrorGroup;
        Long groupId = GroupIdConverter.INSTANCE.toGraphProperty(mirrorGroup.getGroupId());
        setProperty(MIRROR_GROUP_ID_PROPERTY, groupId);

        getElement().edges(Direction.OUT, HAS_MIRROR_GROUP_EDGE).forEachRemaining(Edge::remove);
        MirrorGroup.MirrorGroupData data = mirrorGroup.getData();
        if (data instanceof MirrorGroupFrame) {
            linkOut((VertexFrame) data, HAS_MIRROR_GROUP_EDGE);
        } else {
            String switchId = SwitchIdConverter.INSTANCE.toGraphProperty(mirrorGroup.getSwitchId());
            String pathId = PathIdConverter.INSTANCE.toGraphProperty(mirrorGroup.getPathId());
            MirrorGroupFrame frame = MirrorGroupFrame.load(getGraph(), switchId, pathId).orElseThrow(() ->
                    new IllegalArgumentException("Unable to link to non-existent mirror group " + mirrorGroup));
            linkOut(frame, HAS_MIRROR_GROUP_EDGE);
        }
    }

    @Override
    public Collection<FlowMirrorPath> getMirrorPaths() {
        if (paths == null) {
            paths = traverse(v -> v.out(OWNS_MIRROR_PATHS_EDGE)
                    .hasLabel(FlowMirrorPathFrame.FRAME_LABEL))
                    .toListExplicit(FlowMirrorPathFrame.class).stream()
                    .map(FlowMirrorPath::new)
                    .collect(Collectors.toMap(FlowMirrorPath::getPathId, v -> v));
            pathIds = Collections.unmodifiableSet(paths.keySet());
        }
        return Collections.unmodifiableCollection(paths.values());
    }

    @Override
    public Set<PathId> getPathIds() {
        if (pathIds == null) {
            pathIds = traverse(v -> v.out(OWNS_MIRROR_PATHS_EDGE)
                    .hasLabel(FlowMirrorPathFrame.FRAME_LABEL)
                    .values(FlowMirrorPathFrame.PATH_ID_PROPERTY))
                    .getRawTraversal().toStream()
                    .map(s -> (String) s)
                    .map(PathIdConverter.INSTANCE::toEntityAttribute)
                    .collect(Collectors.toSet());
        }
        return pathIds;
    }

    @Override
    public Optional<FlowMirrorPath> getPath(PathId pathId) {
        if (paths == null) {
            // init the cache map with paths.
            getMirrorPaths();
        }
        return Optional.ofNullable(paths.get(pathId));
    }

    @Override
    public boolean hasPath(FlowMirrorPath path) {
        return getPathIds().contains(path.getPathId());
    }

    @Override
    public void addPaths(FlowMirrorPath... paths) {
        for (FlowMirrorPath path : paths) {
            FlowMirrorPath.FlowMirrorPathData data = path.getData();
            FlowMirrorPathFrame frame;
            if (data instanceof FlowMirrorPathFrame) {
                frame = (FlowMirrorPathFrame) data;
                // Unlink the path from the previous owner.
                frame.getElement().edges(Direction.IN, FlowMirrorPointsFrame.OWNS_MIRROR_PATHS_EDGE)
                        .forEachRemaining(Edge::remove);
            } else {
                // We intentionally don't allow to add transient entities.
                // A path must be added via corresponding repository first.
                throw new IllegalArgumentException("Unable to link to transient flow mirror path " + path);
            }
            linkOut(frame, OWNS_MIRROR_PATHS_EDGE);
            if (this.paths != null) {
                this.paths.put(path.getPathId(), path);
            }
        }
        if (this.paths != null) {
            // force to reload
            this.pathIds = Collections.unmodifiableSet(this.paths.keySet());
        }
    }

    @Override
    public FlowPath getFlowPath() {
        if (flowPath == null) {
            List<? extends FlowPathFrame> flowFrames = traverse(v -> v.in(FlowPathFrame.HAS_SEGMENTS_EDGE)
                    .hasLabel(FlowPathFrame.FRAME_LABEL))
                    .toListExplicit(FlowPathFrame.class);
            flowPath = !flowFrames.isEmpty() ? new FlowPath(flowFrames.get(0)) : null;
            PathId pathId = flowPath != null ? flowPath.getPathId() : null;
            if (!Objects.equals(getFlowPathId(), pathId)) {
                throw new IllegalStateException(
                        format("The flow mirror points %s has inconsistent flow_path_id %s / %s",
                                getId(), getFlowPathId(), pathId));
            }
        }
        return flowPath;
    }
}
