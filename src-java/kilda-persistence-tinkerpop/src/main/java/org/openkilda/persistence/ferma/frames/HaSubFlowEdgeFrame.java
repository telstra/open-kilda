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

import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.HaSubFlowEdge;
import org.openkilda.model.HaSubFlowEdge.HaSubFlowEdgeData;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.MeterIdConverter;
import org.openkilda.persistence.ferma.frames.converters.PathIdConverter;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;

import com.syncleus.ferma.FramedGraph;
import com.syncleus.ferma.annotations.Property;

import java.util.List;
import java.util.Objects;

public abstract class HaSubFlowEdgeFrame extends KildaBaseEdgeFrame implements HaSubFlowEdgeData {
    public static final String FRAME_LABEL = "ha_subflow_edge";
    public static final String HA_FLOW_ID_PROPERTY = "ha_flow_id";
    public static final String HA_FLOW_PATH_ID_PROPERTY = "ha_flow_path_id";
    public static final String HA_SUBFLOW_ID_PROPERTY = "ha_subflow_id";
    public static final String SUBFLOW_ENDPOINT_SWITCH_ID_PROPERTY = "endpoint_switch_id";
    public static final String METER_ID_PROPERTY = "meter_id";

    private HaSubFlow haSubFlow;
    private HaFlowPath haFlowPath;

    @Override
    @Property(HA_FLOW_ID_PROPERTY)
    public abstract String getHaFlowId();

    @Override
    @Property(HA_SUBFLOW_ID_PROPERTY)
    public abstract String getHaSubFlowId();

    @Override
    @Property(METER_ID_PROPERTY)
    @Convert(MeterIdConverter.class)
    public abstract MeterId getMeterId();

    @Override
    @Property(METER_ID_PROPERTY)
    @Convert(MeterIdConverter.class)
    public abstract void setMeterId(MeterId meterId);

    @Override
    @Property(SUBFLOW_ENDPOINT_SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getSubFlowEndpointSwitchId();

    @Override
    @Property(HA_FLOW_PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract PathId getHaFlowPathId();

    @Override
    public HaFlowPath getHaFlowPath() {
        if (haFlowPath == null) {
            List<? extends HaFlowPathFrame> subFlowFrames = traverse(e -> e.outV()
                    .hasLabel(HaFlowPathFrame.FRAME_LABEL))
                    .toListExplicit(HaFlowPathFrame.class);
            haFlowPath = !subFlowFrames.isEmpty() ? new HaFlowPath(subFlowFrames.get(0)) : null;
            PathId pathId = haFlowPath != null ? haFlowPath.getHaPathId() : null;
            if (!Objects.equals(getHaFlowPathId(), pathId)) {
                throw new IllegalStateException(
                        format("The ha_subflow_edge %s has inconsistent ha_flow_path_id %s / %s",
                        getId(), getHaFlowPathId(), pathId));
            }
        }
        return haFlowPath;
    }

    @Override
    public void setHaFlowPath(HaFlowPath flow) {
        throw new UnsupportedOperationException("Changing edge's source is not supported");
    }

    @Override
    public HaSubFlow getHaSubFlow() {
        if (haSubFlow == null) {
            List<? extends HaSubFlowFrame> haSubFlowFrames = traverse(e -> e.inV()
                    .hasLabel(HaSubFlowFrame.FRAME_LABEL))
                    .toListExplicit(HaSubFlowFrame.class);
            haSubFlow = !haSubFlowFrames.isEmpty() ? new HaSubFlow(haSubFlowFrames.get(0)) : null;
            String haSubFlowId = haSubFlow != null ? haSubFlow.getHaSubFlowId() : null;
            if (!Objects.equals(getHaSubFlowId(), haSubFlowId)) {
                throw new IllegalStateException(format("The ha_subflow %s has inconsistent ha_subflow_id %s / %s",
                        getId(), getHaSubFlowId(), haSubFlowId));
            }
        }
        return haSubFlow;
    }

    @Override
    public void setHaSubFlow(HaSubFlow haSubFlow) {
        throw new UnsupportedOperationException("Changing edge's source is not supported");
    }

    public static HaSubFlowEdgeFrame create(FramedGraph framedGraph, HaSubFlowEdgeData data, PathId haPathId) {
        HaFlowPathFrame source = HaFlowPathFrame.load(framedGraph, haPathId)
                .orElseThrow(() -> new IllegalArgumentException("Unable to locate the ha-flow path " + haPathId));
        HaSubFlowFrame destination = HaSubFlowFrame.load(framedGraph, data.getHaSubFlowId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unable to locate the ha sub flow " + data.getHaFlowId()));
        HaSubFlowEdgeFrame frame = KildaBaseEdgeFrame.addNewFramedEdge(framedGraph, source, destination,
                HaSubFlowEdgeFrame.FRAME_LABEL, HaSubFlowEdgeFrame.class);
        frame.setProperty(HA_FLOW_ID_PROPERTY, data.getHaFlowId());
        frame.setProperty(HA_SUBFLOW_ID_PROPERTY, data.getHaSubFlowId());
        frame.setProperty(HA_FLOW_PATH_ID_PROPERTY, PathIdConverter.INSTANCE.toGraphProperty(haPathId));
        frame.setProperty(SUBFLOW_ENDPOINT_SWITCH_ID_PROPERTY, SwitchIdConverter.INSTANCE.toGraphProperty(
                destination.getEndpointSwitchId()));
        HaSubFlowEdge.HaSubFlowEdgeCloner.INSTANCE.copyWithoutFlows(data, frame);
        return frame;
    }
}
