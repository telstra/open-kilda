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

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.MacAddress;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties.SwitchPropertiesData;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.FlowEncapsulationTypeConverter;
import org.openkilda.persistence.ferma.frames.converters.MacAddressConverter;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;

import com.syncleus.ferma.VertexFrame;
import com.syncleus.ferma.annotations.Property;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class SwitchPropertiesFrame extends KildaBaseVertexFrame implements SwitchPropertiesData {
    public static final String FRAME_LABEL = "switch_properties";
    public static final String HAS_BY_EDGE = "has";
    public static final String SWITCH_ID_PROPERTY = "switch_id";
    public static final String SUPPORTED_TRANSIT_ENCAPSULATION_PROPERTY = "supported_transit_encapsulation";

    private Switch switchObj;

    @Override
    public Switch getSwitchObj() {
        if (switchObj == null) {
            List<? extends SwitchFrame> switchFrames = traverse(v -> v.in(HAS_BY_EDGE)
                    .hasLabel(SwitchFrame.FRAME_LABEL))
                    .toListExplicit(SwitchFrame.class);
            switchObj = !switchFrames.isEmpty() ? new Switch(switchFrames.get(0)) : null;
            SwitchId switchId = switchObj != null ? switchObj.getSwitchId() : null;
            if (!Objects.equals(getSwitchId(), switchId)) {
                throw new IllegalStateException(format("The switch properties %s has inconsistent switch %s / %s",
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

        getElement().edges(Direction.IN, HAS_BY_EDGE).forEachRemaining(Edge::remove);
        Switch.SwitchData data = switchObj.getData();
        if (data instanceof SwitchFrame) {
            linkIn((VertexFrame) data, HAS_BY_EDGE);
        } else {
            SwitchFrame frame = SwitchFrame.load(getGraph(), switchId).orElseThrow(() ->
                    new IllegalArgumentException("Unable to link to non-existent switch " + switchObj));
            linkIn(frame, HAS_BY_EDGE);
        }
    }

    @Override
    public Set<FlowEncapsulationType> getSupportedTransitEncapsulation() {
        Set<FlowEncapsulationType> results = new HashSet<>();
        getElement().properties(SUPPORTED_TRANSIT_ENCAPSULATION_PROPERTY).forEachRemaining(property -> {
            if (property.isPresent()) {
                Object propertyValue = property.value();
                if (propertyValue instanceof Collection) {
                    ((Collection<String>) propertyValue).forEach(entry ->
                            results.add(FlowEncapsulationTypeConverter.INSTANCE.toEntityAttribute(entry)));
                } else {
                    results.add(FlowEncapsulationTypeConverter.INSTANCE.toEntityAttribute((String) propertyValue));
                }
            }
        });
        return results;
    }

    @Override
    public void setSupportedTransitEncapsulation(Set<FlowEncapsulationType> supportedTransitEncapsulation) {
        getElement().property(VertexProperty.Cardinality.set, SUPPORTED_TRANSIT_ENCAPSULATION_PROPERTY,
                supportedTransitEncapsulation.stream()
                        .map(FlowEncapsulationTypeConverter.INSTANCE::toGraphProperty)
                        .collect(Collectors.toSet()));
    }

    @Override
    @Property(SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getSwitchId();

    @Override
    @Property("multi_table")
    public abstract boolean isMultiTable();

    @Override
    @Property("multi_table")
    public abstract void setMultiTable(boolean multiTable);

    @Override
    @Property("switch_lldp")
    public abstract boolean isSwitchLldp();

    @Override
    @Property("switch_lldp")
    public abstract void setSwitchLldp(boolean switchLldp);

    @Override
    @Property("switch_arp")
    public abstract boolean isSwitchArp();

    @Override
    @Property("switch_arp")
    public abstract void setSwitchArp(boolean switchArp);

    @Override
    @Property("server42_flow_rtt")
    public abstract boolean isServer42FlowRtt();

    @Override
    @Property("server42_flow_rtt")
    public abstract void setServer42FlowRtt(boolean server42FlowRtt);

    @Override
    @Property("server42_port")
    public abstract Integer getServer42Port();

    @Override
    @Property("server42_port")
    public abstract void setServer42Port(Integer server42Port);

    @Override
    @Property("server42_mac_address")
    @Convert(MacAddressConverter.class)
    public abstract MacAddress getServer42MacAddress();

    @Override
    @Property("server42_mac_address")
    @Convert(MacAddressConverter.class)
    public abstract void setServer42MacAddress(MacAddress server42MacAddress);

    @Override
    @Property("server42_vlan")
    public abstract Integer getServer42Vlan();

    @Override
    @Property("server42_vlan")
    public abstract void setServer42Vlan(Integer server42Vlan);

    @Override
    @Property("inbound_telescope_port")
    public abstract Integer getInboundTelescopePort();

    @Override
    @Property("inbound_telescope_port")
    public abstract void setInboundTelescopePort(Integer inboundTelescopePort);

    @Override
    @Property("outbound_telescope_port")
    public abstract Integer getOutboundTelescopePort();

    @Override
    @Property("outbound_telescope_port")
    public abstract void setOutboundTelescopePort(Integer outboundTelescopePort);

    @Override
    @Property("telescope_ingress_vlan")
    public abstract Integer getTelescopeIngressVlan();

    @Override
    @Property("telescope_ingress_vlan")
    public abstract void setTelescopeIngressVlan(Integer telescopeIngressVlan);

    @Override
    @Property("telescope_egress_vlan")
    public abstract Integer getTelescopeEgressVlan();

    @Override
    @Property("telescope_egress_vlan")
    public abstract void setTelescopeEgressVlan(Integer telescopeEgressVlan);
}
