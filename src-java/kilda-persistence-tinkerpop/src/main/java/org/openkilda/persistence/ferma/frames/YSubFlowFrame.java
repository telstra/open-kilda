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

import org.openkilda.model.Flow;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.model.YSubFlow.YSubFlowData;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;

import com.syncleus.ferma.FramedGraph;
import com.syncleus.ferma.annotations.Property;

import java.util.List;
import java.util.Objects;

public abstract class YSubFlowFrame extends KildaBaseEdgeFrame implements YSubFlowData {
    public static final String FRAME_LABEL = "y_subflow";
    public static final String YFLOW_ID_PROPERTY = "y_flow_id";
    public static final String SUBFLOW_ID_PROPERTY = "subflow_id";

    private YFlow yFlow;
    private Flow flow;

    @Override
    @Property(YFLOW_ID_PROPERTY)
    public abstract String getYFlowId();

    @Override
    @Property(SUBFLOW_ID_PROPERTY)
    public abstract String getSubFlowId();

    @Override
    @Property("shared_endpoint_vlan")
    public abstract int getSharedEndpointVlan();

    @Override
    @Property("shared_endpoint_vlan")
    public abstract void setSharedEndpointVlan(int sharedEndpointVlan);

    @Override
    @Property("shared_endpoint_inner_vlan")
    public abstract int getSharedEndpointInnerVlan();

    @Override
    @Property("shared_endpoint_inner_vlan")
    public abstract void setSharedEndpointInnerVlan(int sharedEndpointInnerVlan);

    @Override
    @Property("endpoint_switch_id")
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getEndpointSwitchId();

    @Override
    @Property("endpoint_switch_id")
    @Convert(SwitchIdConverter.class)
    public abstract void setEndpointSwitchId(SwitchId endpointSwitchId);

    @Override
    @Property("endpoint_port")
    public abstract int getEndpointPort();

    @Override
    @Property("endpoint_port")
    public abstract void setEndpointPort(int endpointPort);

    @Override
    @Property("endpoint_vlan")
    public abstract int getEndpointVlan();

    @Override
    @Property("endpoint_vlan")
    public abstract void setEndpointVlan(int endpointVlan);

    @Override
    @Property("endpoint_inner_vlan")
    public abstract int getEndpointInnerVlan();

    @Override
    @Property("endpoint_inner_vlan")
    public abstract void setEndpointInnerVlan(int endpointInnerVlan);

    @Override
    public YFlow getYFlow() {
        if (yFlow == null) {
            List<? extends YFlowFrame> yFlowFrames = traverse(e -> e.outV()
                    .hasLabel(YFlowFrame.FRAME_LABEL))
                    .toListExplicit(YFlowFrame.class);
            yFlow = !yFlowFrames.isEmpty() ? new YFlow(yFlowFrames.get(0)) : null;
            String yFlowId = yFlow != null ? yFlow.getYFlowId() : null;
            if (!Objects.equals(getYFlowId(), yFlowId)) {
                throw new IllegalStateException(format("The y_subflow %s has inconsistent y_flow_id %s / %s",
                        getId(), getYFlowId(), yFlowId));
            }
        }
        return yFlow;
    }

    @Override
    public void setYFlow(YFlow yFlow) {
        throw new UnsupportedOperationException("Changing edge's source is not supported");
    }

    @Override
    public Flow getFlow() {
        if (flow == null) {
            List<? extends FlowFrame> flowFrames = traverse(e -> e.inV()
                    .hasLabel(FlowFrame.FRAME_LABEL))
                    .toListExplicit(FlowFrame.class);
            flow = !flowFrames.isEmpty() ? new Flow((flowFrames.get(0))) : null;
            String subFlowId = flow != null ? flow.getFlowId() : null;
            if (!Objects.equals(getSubFlowId(), subFlowId)) {
                throw new IllegalStateException(format("The y_subflow %s has inconsistent subflow_id %s / %s",
                        getId(), getSubFlowId(), subFlowId));
            }
        }
        return flow;
    }

    @Override
    public void setFlow(Flow flow) {
        throw new UnsupportedOperationException("Changing edge's destination is not supported");
    }

    public static YSubFlowFrame create(FramedGraph framedGraph, YSubFlowData data) {
        YFlowFrame source = YFlowFrame.load(framedGraph, data.getYFlowId())
                .orElseThrow(() -> new IllegalArgumentException("Unable to locate the y-flow " + data.getYFlowId()));
        FlowFrame destination = FlowFrame.load(framedGraph, data.getSubFlowId())
                .orElseThrow(() -> new IllegalArgumentException("Unable to locate the flow " + data.getSubFlowId()));
        YSubFlowFrame frame = KildaBaseEdgeFrame.addNewFramedEdge(framedGraph, source, destination,
                YSubFlowFrame.FRAME_LABEL, YSubFlowFrame.class);
        frame.setProperty(YFLOW_ID_PROPERTY, data.getYFlowId());
        frame.setProperty(SUBFLOW_ID_PROPERTY, data.getSubFlowId());
        YSubFlow.YSubFlowCloner.INSTANCE.copyWithoutFlows(data, frame);
        return frame;
    }
}
