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

package org.openkilda.northbound.converter;

import static org.junit.Assert.assertEquals;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.northbound.dto.v1.switches.SwitchDto;
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto;

import com.google.common.collect.Lists;
import org.junit.Test;
import org.mapstruct.factory.Mappers;

import java.net.InetSocketAddress;

public class SwitchMapperTest {
    private SwitchMapper switchMapper = Mappers.getMapper(SwitchMapper.class);

    @Test
    public void testSwitchPropertiesDto() {
        SwitchPropertiesDto properties = new SwitchPropertiesDto();
        properties.setMultiTable(true);
        properties.setSwitchArp(true);
        properties.setSwitchLldp(true);
        properties.setServer42FlowRtt(true);
        properties.setServer42Port(42);
        properties.setServer42MacAddress("42:42:42:42:42:42");
        properties.setSupportedTransitEncapsulation(Lists.newArrayList(
                FlowEncapsulationType.TRANSIT_VLAN.toString().toLowerCase()));

        org.openkilda.messaging.model.SwitchPropertiesDto messagingProperties = switchMapper.map(properties);
        SwitchPropertiesDto apiProperties = switchMapper.map(messagingProperties);
        assertEquals(properties, apiProperties);
    }

    @Test
    public void testNullServer42SwitchProperties() {
        SwitchPropertiesDto properties = new SwitchPropertiesDto();
        properties.setServer42FlowRtt(false);
        properties.setServer42Port(null);
        properties.setServer42MacAddress(null);
        properties.setSupportedTransitEncapsulation(Lists.newArrayList(
                FlowEncapsulationType.TRANSIT_VLAN.toString().toLowerCase()));

        org.openkilda.messaging.model.SwitchPropertiesDto messagingProperties = switchMapper.map(properties);
        SwitchPropertiesDto apiProperties = switchMapper.map(messagingProperties);
        assertEquals(properties, apiProperties);
    }

    @Test
    public void testSwitchToSwitchDto() {
        Switch sw = new Switch();
        sw.setSwitchId(new SwitchId(1));
        sw.setSocketAddress(new InetSocketAddress("localhost", 5050));
        sw.setHostname("hostname");
        sw.setDescription("description");
        sw.setStatus(SwitchStatus.ACTIVE);
        sw.setUnderMaintenance(true);
        sw.setOfVersion("OF_13");
        sw.setOfDescriptionManufacturer("manufacturer");
        sw.setOfDescriptionHardware("hardware");
        sw.setOfDescriptionSoftware("software");
        sw.setOfDescriptionSerialNumber("serialNumber");
        sw.setPop("pop");
        sw.setLatitude(48.860611);
        sw.setLongitude(2.337633);
        sw.setStreet("Rue de Rivoli");
        sw.setCity("Paris");
        sw.setCountry("France");

        SwitchDto switchDto = switchMapper.toSwitchDto(sw);
        assertEquals(sw.getSwitchId(), switchDto.getSwitchId());
        assertEquals("127.0.0.1", switchDto.getAddress());
        assertEquals(sw.getSocketAddress().getPort(), switchDto.getPort());
        assertEquals(sw.getHostname(), switchDto.getHostname());
        assertEquals(sw.getDescription(), switchDto.getDescription());
        assertEquals(switchMapper.convertStatus(sw.getStatus()), switchDto.getState());
        assertEquals(sw.isUnderMaintenance(), switchDto.isUnderMaintenance());
        assertEquals(sw.getOfVersion(), switchDto.getOfVersion());
        assertEquals(sw.getOfDescriptionManufacturer(), switchDto.getManufacturer());
        assertEquals(sw.getOfDescriptionHardware(), switchDto.getHardware());
        assertEquals(sw.getOfDescriptionSoftware(), switchDto.getSoftware());
        assertEquals(sw.getOfDescriptionSerialNumber(), switchDto.getSerialNumber());
        assertEquals(sw.getPop(), switchDto.getPop());
        assertEquals((Double) sw.getLatitude(), switchDto.getLocation().getLatitude());
        assertEquals((Double) sw.getLongitude(), switchDto.getLocation().getLongitude());
        assertEquals(sw.getStreet(), switchDto.getLocation().getStreet());
        assertEquals(sw.getCity(), switchDto.getLocation().getCity());
        assertEquals(sw.getCountry(), switchDto.getLocation().getCountry());
    }
}
