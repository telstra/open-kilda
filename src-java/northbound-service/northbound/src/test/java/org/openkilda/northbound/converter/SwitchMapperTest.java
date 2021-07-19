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

import org.openkilda.messaging.model.SwitchPatch;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.northbound.dto.v1.switches.SwitchDto;
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto;
import org.openkilda.northbound.dto.v2.switches.SwitchDtoV2;
import org.openkilda.northbound.dto.v2.switches.SwitchLocationDtoV2;
import org.openkilda.northbound.dto.v2.switches.SwitchPatchDto;

import com.google.common.collect.Lists;
import org.junit.Test;
import org.mapstruct.factory.Mappers;

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
        Switch sw = getSwitch();

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

    @Test
    public void testSwitchToSwitchDtoV2() {
        Switch sw = getSwitch();

        SwitchDtoV2 switchDto = switchMapper.map(sw);
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

    @Test
    public void testSwitchPatchDtoToSwitchPatch() {
        SwitchPatchDto switchPatchDto = new SwitchPatchDto("pop",
                new SwitchLocationDtoV2(48.860611, 2.337633, "street", "city", "country"));

        SwitchPatch switchPatch = switchMapper.map(switchPatchDto);
        assertEquals(switchPatchDto.getPop(), switchPatch.getPop());
        assertEquals(switchPatchDto.getLocation().getLatitude(), switchPatch.getLocation().getLatitude());
        assertEquals(switchPatchDto.getLocation().getLongitude(), switchPatch.getLocation().getLongitude());
        assertEquals(switchPatchDto.getLocation().getStreet(), switchPatch.getLocation().getStreet());
        assertEquals(switchPatchDto.getLocation().getCity(), switchPatch.getLocation().getCity());
        assertEquals(switchPatchDto.getLocation().getCountry(), switchPatch.getLocation().getCountry());
    }

    private Switch getSwitch() {
        return Switch.builder()
                .switchId(new SwitchId(1))
                .socketAddress(new IpSocketAddress("127.0.0.1", 5050))
                .hostname("hostname")
                .description("description")
                .status(SwitchStatus.ACTIVE)
                .underMaintenance(true)
                .ofVersion("OF_13")
                .ofDescriptionManufacturer("manufacturer")
                .ofDescriptionHardware("hardware")
                .ofDescriptionSoftware("software")
                .ofDescriptionSerialNumber("serialNumber")
                .pop("pop")
                .latitude(48.860611)
                .longitude(2.337633)
                .street("Rue de Rivoli")
                .city("Paris")
                .country("France")
                .build();
    }
}
