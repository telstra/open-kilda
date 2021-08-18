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

import org.openkilda.model.LagLogicalPort.LagLogicalPortData;
import org.openkilda.model.PhysicalPort;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;

import com.syncleus.ferma.FramedGraph;
import com.syncleus.ferma.annotations.Property;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public abstract class LagLogicalPortFrame extends KildaBaseVertexFrame implements LagLogicalPortData {
    public static final String FRAME_LABEL = "lag_logical_port";
    public static final String COMPRISES_PHYSICAL_PORT_EDGE = "comprises";
    public static final String SWITCH_ID_PROPERTY = "switch_id";
    public static final String LOGICAL_PORT_NUMBER_PROPERTY = "logical_port_number";

    private List<PhysicalPort> physicalPorts;

    @Override
    @Property(SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getSwitchId();

    @Override
    @Property(SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract void setSwitchId(@NonNull SwitchId switchId);

    @Override
    @Property(LOGICAL_PORT_NUMBER_PROPERTY)
    public abstract int getLogicalPortNumber();

    @Override
    @Property(LOGICAL_PORT_NUMBER_PROPERTY)
    public abstract void setLogicalPortNumber(int logicalPortNumber);

    @Override
    public List<PhysicalPort> getPhysicalPorts() {
        if (physicalPorts == null) {
            physicalPorts = traverse(v -> v.out(COMPRISES_PHYSICAL_PORT_EDGE)
                    .hasLabel(PhysicalPortFrame.FRAME_LABEL))
                    .toListExplicit(PhysicalPortFrame.class).stream()
                    .map(PhysicalPort::new)
                    .sorted(Comparator.comparingInt(PhysicalPort::getPortNumber))
                    .collect(Collectors.toList());
        }
        return physicalPorts;
    }

    @Override
    public void setPhysicalPorts(List<PhysicalPort> physicalPorts) {
        getElement().edges(Direction.OUT, COMPRISES_PHYSICAL_PORT_EDGE)
                .forEachRemaining(edge -> {
                    edge.inVertex().remove();
                    edge.remove();
                });

        for (PhysicalPort physicalPort : physicalPorts) {
            PhysicalPort.PhysicalPortData data = physicalPort.getData();
            PhysicalPortFrame frame;

            if (data instanceof PhysicalPortFrame) {
                frame = (PhysicalPortFrame) data;
                // Unlink physical port from the previous owner.
                frame.getElement().edges(Direction.IN, COMPRISES_PHYSICAL_PORT_EDGE)
                        .forEachRemaining(Edge::remove);
            } else {
                frame = PhysicalPortFrame.create(getGraph(), data);
            }
            linkOut(frame, COMPRISES_PHYSICAL_PORT_EDGE);
        }

        // force to reload
        this.physicalPorts = null;
    }

    public static Optional<LagLogicalPortFrame> load(FramedGraph graph, SwitchId switchId, int logicalPortNumber) {
        List<? extends LagLogicalPortFrame> logicalPortFrames = graph.traverse(input -> input.V()
                        .hasLabel(FRAME_LABEL)
                        .has(SWITCH_ID_PROPERTY, SwitchIdConverter.INSTANCE.toGraphProperty(switchId))
                        .has(LOGICAL_PORT_NUMBER_PROPERTY, logicalPortNumber))
                .toListExplicit(LagLogicalPortFrame.class);
        return logicalPortFrames.isEmpty() ? Optional.empty() : Optional.of(logicalPortFrames.get(0));
    }
}
