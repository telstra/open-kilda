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

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow.SharedEndpoint;
import org.openkilda.model.YFlow.YFlowData;
import org.openkilda.model.YSubFlow;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.FlowEncapsulationTypeConverter;
import org.openkilda.persistence.ferma.frames.converters.FlowStatusConverter;
import org.openkilda.persistence.ferma.frames.converters.PathComputationStrategyConverter;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;

import com.syncleus.ferma.FramedGraph;
import com.syncleus.ferma.annotations.Property;
import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Element;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public abstract class YFlowFrame extends KildaBaseVertexFrame implements YFlowData {
    public static final String FRAME_LABEL = "yflow";
    public static final String YFLOW_ID_PROPERTY = "yflow_id";

    private Set<YSubFlow> subFlows;

    @Override
    @Property(YFLOW_ID_PROPERTY)
    public abstract String getYFlowId();

    @Override
    @Property(YFLOW_ID_PROPERTY)
    public abstract void setYFlowId(String flowId);

    @Override
    public SharedEndpoint getSharedEndpoint() {
        return new SharedEndpoint(
                SwitchIdConverter.INSTANCE.toEntityAttribute(getProperty("shared_endpoint_switch_id")),
                getProperty("shared_endpoint_port"));
    }

    @Override
    public void setSharedEndpoint(SharedEndpoint sharedEndpoint) {
        setProperty("shared_endpoint_switch_id",
                SwitchIdConverter.INSTANCE.toGraphProperty(sharedEndpoint.getSwitchId()));
        setProperty("shared_endpoint_port", sharedEndpoint.getPortNumber());
    }

    @Override
    @Property("allocate_protected_path")
    public abstract boolean isAllocateProtectedPath();

    @Override
    @Property("allocate_protected_path")
    public abstract void setAllocateProtectedPath(boolean allocateProtectedPath);

    @Override
    @Property("maximum_bandwidth")
    public abstract long getMaximumBandwidth();

    @Override
    @Property("maximum_bandwidth")
    public abstract void setMaximumBandwidth(long maximumBandwidth);

    @Override
    @Property("ignore_bandwidth")
    public abstract boolean isIgnoreBandwidth();

    @Override
    @Property("ignore_bandwidth")
    public abstract void setIgnoreBandwidth(boolean ignoreBandwidth);

    @Override
    @Property("strict_bandwidth")
    public abstract boolean isStrictBandwidth();

    @Override
    @Property("strict_bandwidth")
    public abstract void setStrictBandwidth(boolean strictBandwidth);

    @Override
    @Property("description")
    public abstract String getDescription();

    @Override
    @Property("description")
    public abstract void setDescription(String description);

    @Override
    @Property("periodic_pings")
    public abstract boolean isPeriodicPings();

    @Override
    @Property("periodic_pings")
    public abstract void setPeriodicPings(boolean periodicPings);

    @Override
    @Property("encapsulation_type")
    @Convert(FlowEncapsulationTypeConverter.class)
    public abstract FlowEncapsulationType getEncapsulationType();

    @Override
    @Property("encapsulation_type")
    @Convert(FlowEncapsulationTypeConverter.class)
    public abstract void setEncapsulationType(FlowEncapsulationType encapsulationType);

    @Override
    @Property("status")
    @Convert(FlowStatusConverter.class)
    public abstract FlowStatus getStatus();

    @Override
    @Property("status")
    @Convert(FlowStatusConverter.class)
    public abstract void setStatus(FlowStatus status);

    @Override
    @Property("max_latency")
    public abstract Long getMaxLatency();

    @Override
    @Property("max_latency")
    public abstract void setMaxLatency(Long maxLatency);

    @Override
    @Property("max_latency_tier2")
    public abstract Long getMaxLatencyTier2();

    @Override
    @Property("max_latency_tier2")
    public abstract void setMaxLatencyTier2(Long maxLatencyTier2);

    @Override
    @Property("priority")
    public abstract Integer getPriority();

    @Override
    @Property("priority")
    public abstract void setPriority(Integer priority);

    @Override
    @Property("pinned")
    public abstract boolean isPinned();

    @Override
    @Property("pinned")
    public abstract void setPinned(boolean pinned);

    @Override
    @Property("path_computation_strategy")
    @Convert(PathComputationStrategyConverter.class)
    public abstract PathComputationStrategy getPathComputationStrategy();

    @Override
    @Property("path_computation_strategy")
    @Convert(PathComputationStrategyConverter.class)
    public abstract void setPathComputationStrategy(PathComputationStrategy pathComputationStrategy);

    @Override
    @Property("y_point")
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getYPoint();

    @Override
    @Property("y_point")
    @Convert(SwitchIdConverter.class)
    public abstract void setYPoint(SwitchId ypoint);

    @Override
    public Set<YSubFlow> getSubFlows() {
        if (subFlows == null) {
            subFlows = traverse(v -> v.outE(YSubFlowFrame.FRAME_LABEL))
                    .toListExplicit(YSubFlowFrame.class).stream()
                    .map(YSubFlow::new)
                    .collect(Collectors.toSet());
        }
        return subFlows;
    }

    @Override
    public void setSubFlows(Set<YSubFlow> subFlows) {
        getElement().edges(Direction.OUT, YSubFlowFrame.FRAME_LABEL)
                .forEachRemaining(Element::remove);

        subFlows.forEach(subFlow -> subFlow.setData(YSubFlowFrame.create(getGraph(), subFlow.getData())));

        // force to reload
        this.subFlows = null;
    }


    public static Optional<YFlowFrame> load(FramedGraph graph, String yFlowId) {
        List<? extends YFlowFrame> yFlowFrames = graph.traverse(g -> g.V()
                        .hasLabel(YFlowFrame.FRAME_LABEL)
                        .has(YFlowFrame.YFLOW_ID_PROPERTY, yFlowId))
                .toListExplicit(YFlowFrame.class);
        return yFlowFrames.isEmpty() ? Optional.empty() : Optional.of(yFlowFrames.get(0));
    }
}
