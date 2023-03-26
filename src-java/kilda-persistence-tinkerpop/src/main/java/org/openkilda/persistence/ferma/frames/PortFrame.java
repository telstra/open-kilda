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

import org.openkilda.model.Port.PortData;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;

import com.syncleus.ferma.annotations.Property;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;

import java.util.List;
import java.util.Objects;

public abstract class PortFrame extends KildaBaseVertexFrame implements PortData {
    public static final String FRAME_LABEL = "port";
    public static final String SWITCH_PORT_EDGE_LABEL = "owns";

    public static final String SWITCH_ID_PROPERTY = "switch_id";
    public static final String PORT_NO_PROPERTY = "port_no";
    public static final String MAX_PORT_SPEED_PROPERTY = "max_speed";
    public static final String CURRENT_PORT_SPEED_PROPERTY = "curr_speed";

    private Switch switchObj;

    @Override
    @Property(SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getSwitchId();

    @Override
    @Property(PORT_NO_PROPERTY)
    public abstract int getPortNo();

    @Override
    @Property(PORT_NO_PROPERTY)
    public abstract void setPortNo(int portNo);

    @Override
    @Property(MAX_PORT_SPEED_PROPERTY)
    public abstract long getMaxSpeed();

    @Override
    @Property(MAX_PORT_SPEED_PROPERTY)
    public abstract void setMaxSpeed(long portSpeed);

    @Override
    @Property(CURRENT_PORT_SPEED_PROPERTY)
    public abstract long getCurrentSpeed();

    @Override
    @Property(CURRENT_PORT_SPEED_PROPERTY)
    public abstract void setCurrentSpeed(long currentSpeed);

    @Override
    public Switch getSwitchObj() {
        if (switchObj == null) {
            List<? extends SwitchFrame> switchFrames = traverse(v -> v.in(SWITCH_PORT_EDGE_LABEL)
                    .hasLabel(SwitchFrame.FRAME_LABEL))
                    .toListExplicit(SwitchFrame.class);
            switchObj = !switchFrames.isEmpty() ? new Switch(switchFrames.get(0)) : null;
            SwitchId switchId = switchObj != null ? switchObj.getSwitchId() : null;
            if (!Objects.equals(getSwitchId(), switchId)) {
                throw new IllegalStateException(format("The port %s has inconsistent switch %s / %s",
                        getId(), getSwitchId(), switchId));
            }
        }
        return switchObj;
    }

    @Override
    public void setSwitchObj(Switch switchObj) {
        String switchId = SwitchIdConverter.INSTANCE.toGraphProperty(switchObj.getSwitchId());
        setProperty(SWITCH_ID_PROPERTY, switchId);

        getElement().edges(Direction.IN, SWITCH_PORT_EDGE_LABEL).forEachRemaining(Edge::remove);

        SwitchFrame frame = SwitchFrame.load(getGraph(), switchId).orElseThrow(() ->
                new IllegalArgumentException("Unable to link to non-existent switch " + switchId));
        linkIn(frame, SWITCH_PORT_EDGE_LABEL);
    }
}
