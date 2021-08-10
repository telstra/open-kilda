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

import static org.openkilda.persistence.ferma.frames.LagLogicalPortFrame.COMPRISES_PHYSICAL_PORT_EDGE;

import org.openkilda.model.LagLogicalPort;
import org.openkilda.model.PhysicalPort;
import org.openkilda.model.PhysicalPort.PhysicalPortData;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;

import com.syncleus.ferma.FramedGraph;
import com.syncleus.ferma.annotations.Property;

import java.util.List;

public abstract class PhysicalPortFrame extends KildaBaseVertexFrame implements PhysicalPortData {
    public static final String FRAME_LABEL = "physical_port";
    public static final String SWITCH_ID_PROPERTY = "switch_id";
    public static final String PORT_NUMBER_PROPERTY = "port_number";

    private LagLogicalPort lagLogicalPort;

    @Override
    @Property(SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getSwitchId();

    @Override
    @Property(SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract void setSwitchId(SwitchId switchId);

    @Override
    @Property(PORT_NUMBER_PROPERTY)
    public abstract int getPortNumber();

    @Override
    @Property(PORT_NUMBER_PROPERTY)
    public abstract void setPortNumber(int portNumber);

    @Override
    public LagLogicalPort getLagLogicalPort() {
        if (lagLogicalPort == null) {
            List<? extends LagLogicalPortFrame> lagLogicalPortFrames = traverse(
                    v -> v.in(COMPRISES_PHYSICAL_PORT_EDGE)
                    .hasLabel(LagLogicalPortFrame.FRAME_LABEL))
                    .toListExplicit(LagLogicalPortFrame.class);
            lagLogicalPort = !lagLogicalPortFrames.isEmpty() ? new LagLogicalPort(lagLogicalPortFrames.get(0)) : null;
        }
        return lagLogicalPort;
    }

    public static PhysicalPortFrame create(FramedGraph framedGraph, PhysicalPortData data) {
        PhysicalPortFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph, FRAME_LABEL,
                PhysicalPortFrame.class);
        PhysicalPort.PhysicalPortCloner.INSTANCE.copy(data, frame);
        return frame;
    }
}
