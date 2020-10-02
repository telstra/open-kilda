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

import org.openkilda.model.PortProperties.PortPropertiesData;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;

import com.syncleus.ferma.VertexFrame;
import com.syncleus.ferma.annotations.Property;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;

import java.util.List;
import java.util.Objects;

public abstract class PortPropertiesFrame extends KildaBaseVertexFrame implements PortPropertiesData {
    public static final String FRAME_LABEL = "port_properties";
    public static final String OWNS_SWITCH_EDGE = "owns";
    public static final String SWITCH_ID_PROPERTY = "switch_id";
    public static final String PORT_NO_PROPERTY = "port_no";

    private Switch switchObj;

    @Override
    public Switch getSwitchObj() {
        if (switchObj == null) {
            List<? extends SwitchFrame> switchFrames = traverse(v -> v.in(OWNS_SWITCH_EDGE)
                    .hasLabel(SwitchFrame.FRAME_LABEL))
                    .toListExplicit(SwitchFrame.class);
            switchObj = !switchFrames.isEmpty() ? new Switch(switchFrames.get(0)) : null;
            SwitchId switchId = switchObj != null ? switchObj.getSwitchId() : null;
            if (!Objects.equals(getSwitchId(), switchId)) {
                throw new IllegalStateException(format("The port properties %s has inconsistent switch %s / %s",
                        getId(), getSwitchId(), switchId));
            }
        }
        return switchObj;
    }

    @Override
    public void setSwitchObj(Switch switchObj) {
        this.switchObj = switchObj;
        String switchId = SwitchIdConverter.INSTANCE.toGraphProperty(switchObj.getSwitchId());
        setProperty(SWITCH_ID_PROPERTY, switchId);

        getElement().edges(Direction.IN, OWNS_SWITCH_EDGE).forEachRemaining(Edge::remove);
        Switch.SwitchData data = switchObj.getData();
        if (data instanceof SwitchFrame) {
            linkIn((VertexFrame) data, OWNS_SWITCH_EDGE);
        } else {
            SwitchFrame frame = SwitchFrame.load(getGraph(), switchId).orElseThrow(() ->
                    new IllegalArgumentException("Unable to link to non-existent switch " + switchObj));
            linkIn(frame, OWNS_SWITCH_EDGE);
        }
    }

    @Override
    @Property(SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getSwitchId();

    @Override
    @Property(PORT_NO_PROPERTY)
    public abstract int getPort();

    @Override
    @Property(PORT_NO_PROPERTY)
    public abstract void setPort(int port);

    @Override
    @Property("discovery_enabled")
    public abstract boolean isDiscoveryEnabled();

    @Override
    @Property("discovery_enabled")
    public abstract void setDiscoveryEnabled(boolean discoveryEnabled);
}
