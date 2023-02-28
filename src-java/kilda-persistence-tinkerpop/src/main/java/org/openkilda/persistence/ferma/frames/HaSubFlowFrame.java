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

import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow.HaSubFlowData;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;

import com.syncleus.ferma.FramedGraph;
import com.syncleus.ferma.annotations.Property;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public abstract class HaSubFlowFrame extends KildaBaseVertexFrame implements HaSubFlowData {
    public static final String FRAME_LABEL = "ha_subflow";
    public static final String HA_FLOW_ID_PROPERTY = "ha_flow_id";
    public static final String HA_SUBFLOW_ID_PROPERTY = "subflow_id";
    public static final String STATUS_PROPERTY = "status";
    public static final String ENDPOINT_SWITCH_ID_PROPERTY = "endpoint_switch_id";
    public static final String ENDPOINT_PORT_PROPERTY = "endpoint_port";
    public static final String ENDPOINT_VLAN_PROPERTY = "endpoint_vlan";
    public static final String ENDPOINT_INNER_VLAN_PROPERTY = "endpoint_inner_vlan";
    public static final String DESCRIPTION_PROPERTY = "description";

    private HaFlow haFlow;

    @Override
    @Property(HA_FLOW_ID_PROPERTY)
    public abstract String getHaFlowId();

    @Override
    @Property(HA_SUBFLOW_ID_PROPERTY)
    public abstract String getHaSubFlowId();

    @Override
    @Property(HA_SUBFLOW_ID_PROPERTY)
    public abstract void setHaSubFlowId(String subFlowId);

    @Override
    @Property(STATUS_PROPERTY)
    public abstract FlowStatus getStatus();

    @Override
    @Property(STATUS_PROPERTY)
    public abstract void setStatus(FlowStatus status);

    @Override
    @Property(ENDPOINT_SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getEndpointSwitchId();

    @Override
    @Property(ENDPOINT_SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract void setEndpointSwitchId(SwitchId endpointSwitchId);

    @Override
    @Property(ENDPOINT_PORT_PROPERTY)
    public abstract int getEndpointPort();

    @Override
    @Property(ENDPOINT_PORT_PROPERTY)
    public abstract void setEndpointPort(int endpointPort);

    @Override
    @Property(ENDPOINT_VLAN_PROPERTY)
    public abstract int getEndpointVlan();

    @Override
    @Property(ENDPOINT_VLAN_PROPERTY)
    public abstract void setEndpointVlan(int endpointVlan);

    @Override
    @Property(ENDPOINT_INNER_VLAN_PROPERTY)
    public abstract int getEndpointInnerVlan();

    @Override
    @Property(ENDPOINT_INNER_VLAN_PROPERTY)
    public abstract void setEndpointInnerVlan(int endpointInnerVlan);

    @Override
    @Property(DESCRIPTION_PROPERTY)
    public abstract String getDescription();

    @Override
    @Property(DESCRIPTION_PROPERTY)
    public abstract void setDescription(String description);

    @Override
    public HaFlow getHaFlow() {
        if (haFlow == null) {
            List<? extends HaFlowFrame> haFlowFrames = traverse(v -> v.in(HaFlowFrame.OWNS_SUB_FLOW_EDGE)
                    .hasLabel(HaFlowFrame.FRAME_LABEL))
                    .toListExplicit(HaFlowFrame.class);
            haFlow = !haFlowFrames.isEmpty() ? new HaFlow(haFlowFrames.get(0)) : null;
            String haFlowId = haFlow != null ? haFlow.getHaFlowId() : null;
            if (!Objects.equals(getHaFlowId(), haFlowId)) {
                throw new IllegalStateException(format("The ha_subflow %s has inconsistent ha_flow_id %s / %s",
                        getId(), getHaFlowId(), haFlowId));
            }
        }
        return haFlow;
    }

    public static Optional<HaSubFlowFrame> load(FramedGraph graph, String haSubFlowId) {
        List<? extends HaSubFlowFrame> haSubFlowFrames = graph.traverse(g -> g.V()
                        .hasLabel(HaSubFlowFrame.FRAME_LABEL)
                        .has(HaSubFlowFrame.HA_SUBFLOW_ID_PROPERTY, haSubFlowId))
                .toListExplicit(HaSubFlowFrame.class);
        return haSubFlowFrames.isEmpty() ? Optional.empty() : Optional.of(haSubFlowFrames.get(0));
    }
}
