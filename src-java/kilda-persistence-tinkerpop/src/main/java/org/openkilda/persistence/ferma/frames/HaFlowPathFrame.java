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

import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.GroupId;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath.HaFlowPathData;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public abstract class HaFlowPathFrame extends KildaBaseVertexFrame implements HaFlowPathData {
    public static final String FRAME_LABEL = "ha_flow_path";
    public static final String OWNS_PATH_EDGE = "owns";
    public static final String HAS_HA_SUB_FLOW_EDGE = "has";
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

    private Switch sharedSwitch;
    private HaFlow haFlow;
    private List<FlowPath> subPaths;
    private List<HaSubFlow> subFlows;

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
    public List<FlowPath> getSubPaths() {
        if (subPaths == null) {
            subPaths = traverse(v -> v.out(HaFlowPathFrame.OWNS_PATH_EDGE)
                    .hasLabel(FlowPathFrame.FRAME_LABEL))
                    .toListExplicit(FlowPathFrame.class).stream()
                    .map(FlowPath::new)
                    .collect(Collectors.toList());
        }
        return subPaths;
    }

    @Override
    public void setSubPaths(Collection<FlowPath> subPaths) {
        for (FlowPath path : subPaths) {
            if (path.getHaSubFlow() == null) {
                throw new IllegalArgumentException(
                        format("Sub path %s must has ha sub flow to be added into ha flow path %s",
                                path, getHaPathId()));
            }
            FlowPath.FlowPathData data = path.getData();
            FlowPathFrame frame;
            if (data instanceof FlowPathFrame) {
                frame = (FlowPathFrame) data;
                // Unlink the path from the previous owner.
                frame.getElement().edges(Direction.IN, HaFlowPathFrame.OWNS_PATH_EDGE)
                        .forEachRemaining(Edge::remove);
            } else {
                // We intentionally don't allow to add transient entities.
                // A path must be added via corresponding repository first.
                throw new IllegalArgumentException("Unable to link to transient flow path " + path);
            }
            frame.setProperty(FlowPathFrame.HA_PATH_ID_PROPERTY,
                    PathIdConverter.INSTANCE.toGraphProperty(getHaPathId()));
            linkOut(frame, HaFlowPathFrame.OWNS_PATH_EDGE);
        }

        // force to reload
        this.subPaths = null;
    }

    @Override
    public List<HaSubFlow> getHaSubFlows() {
        if (subFlows == null) {
            subFlows = traverse(v -> v.out(HaFlowPathFrame.HAS_HA_SUB_FLOW_EDGE)
                    .hasLabel(HaSubFlowFrame.FRAME_LABEL))
                    .toListExplicit(HaSubFlowFrame.class).stream()
                    .map(HaSubFlow::new)
                    .collect(Collectors.toList());
        }
        return Collections.unmodifiableList(subFlows);
    }

    @Override
    public void setHaSubFlows(Collection<HaSubFlow> haSubFlows) {
        getElement().edges(Direction.OUT, HaFlowPathFrame.HAS_HA_SUB_FLOW_EDGE)
                .forEachRemaining(edge -> {
                    if (HaSubFlowFrame.FRAME_LABEL.equals(edge.inVertex().label())) {
                        edge.remove();
                    }
                });

        for (HaSubFlow subFlow : haSubFlows) {
            HaSubFlow.HaSubFlowData data = subFlow.getData();
            if (data instanceof HaSubFlowFrame) {
                linkOut((HaSubFlowFrame) data, HaFlowPathFrame.HAS_HA_SUB_FLOW_EDGE);
            } else {
                // We intentionally don't allow to add transient entities.
                // A path must be added via corresponding repository first.
                throw new IllegalArgumentException("Unable to link to transient sub flow " + subFlow);
            }

        }

        // force to reload
        this.subFlows = null;
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
