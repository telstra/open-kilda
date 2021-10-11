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
import org.openkilda.model.FlowStats.FlowStatsData;

import com.syncleus.ferma.VertexFrame;
import com.syncleus.ferma.annotations.Property;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;

import java.util.List;
import java.util.Objects;

public abstract class FlowStatsFrame extends KildaBaseVertexFrame implements FlowStatsData {
    public static final String FRAME_LABEL = "flow_stats";
    public static final String HAS_BY_EDGE = "has";
    public static final String FLOW_ID_PROPERTY = "flow_id";

    private Flow flowObj;

    @Override
    public Flow getFlowObj() {
        if (flowObj == null) {
            List<? extends FlowFrame> flowFrames = traverse(v -> v.in(HAS_BY_EDGE)
                    .hasLabel(FlowFrame.FRAME_LABEL))
                    .toListExplicit(FlowFrame.class);
            flowObj = !flowFrames.isEmpty() ? new Flow(flowFrames.get(0)) : null;
            String flowId = flowObj != null ? flowObj.getFlowId() : null;
            if (!Objects.equals(getFlowId(), flowId)) {
                throw new IllegalStateException(format("The flow stats %s has inconsistent flow %s / %s",
                        getId(), getFlowId(), flowId));
            }
        }

        return flowObj;
    }

    @Override
    public void setFlowObj(Flow flowObj) {
        this.flowObj = flowObj;
        String flowId = flowObj.getFlowId();
        setProperty(FLOW_ID_PROPERTY, flowId);

        getElement().edges(Direction.IN, HAS_BY_EDGE).forEachRemaining(Edge::remove);
        Flow.FlowData data = flowObj.getData();
        if (data instanceof FlowFrame) {
            linkIn((VertexFrame) data, HAS_BY_EDGE);
        } else {
            FlowFrame frame = FlowFrame.load(getGraph(), flowId).orElseThrow(() ->
                    new IllegalArgumentException("Unable to link to non-existent flow " + flowObj));
            linkIn(frame, HAS_BY_EDGE);
        }
    }

    @Override
    @Property(FLOW_ID_PROPERTY)
    public abstract String getFlowId();

    @Override
    @Property("forward_latency")
    public abstract Long getForwardLatency();

    @Override
    @Property("forward_latency")
    public abstract void setForwardLatency(Long latency);

    @Override
    @Property("reverse_latency")
    public abstract Long getReverseLatency();

    @Override
    @Property("reverse_latency")
    public abstract void setReverseLatency(Long latency);
}
