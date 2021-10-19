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

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;
import static org.openkilda.messaging.info.switches.LogicalPortType.BFD;
import static org.openkilda.messaging.info.switches.LogicalPortType.LAG;

import org.openkilda.messaging.info.switches.LogicalPortInfoEntry;
import org.openkilda.messaging.info.switches.LogicalPortMisconfiguredInfoEntry;
import org.openkilda.messaging.info.switches.LogicalPortsValidationEntry;
import org.openkilda.messaging.model.SwitchPatch;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.northbound.dto.v1.switches.LogicalPortInfoDto;
import org.openkilda.northbound.dto.v1.switches.LogicalPortsValidationDto;
import org.openkilda.northbound.dto.v1.switches.SwitchDto;
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto;
import org.openkilda.northbound.dto.v2.switches.SwitchDtoV2;
import org.openkilda.northbound.dto.v2.switches.SwitchLocationDtoV2;
import org.openkilda.northbound.dto.v2.switches.SwitchPatchDto;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;

@RunWith(SpringRunner.class)
public class SwitchMapperTest {
    public static final int LOGICAL_PORT_NUMBER_1 = 1;
    public static final int LOGICAL_PORT_NUMBER_2 = 2;
    public static final int LOGICAL_PORT_NUMBER_3 = 3;
    public static final int LOGICAL_PORT_NUMBER_4 = 4;
    public static final int PHYSICAL_PORT_1 = 1;
    public static final int PHYSICAL_PORT_2 = 2;
    public static final int PHYSICAL_PORT_3 = 3;
    public static final int PHYSICAL_PORT_4 = 4;
    public static final int PHYSICAL_PORT_5 = 5;
    public static final int PHYSICAL_PORT_6 = 6;

    @Autowired
    private SwitchMapper switchMapper;

    @Test
    public void testSwitchPropertiesDto() {
        SwitchPropertiesDto properties = new SwitchPropertiesDto();
        properties.setMultiTable(true);
        properties.setSwitchArp(true);
        properties.setSwitchLldp(true);
        properties.setServer42FlowRtt(true);
        properties.setServer42Port(42);
        properties.setServer42MacAddress("42:42:42:42:42:42");
        properties.setSupportedTransitEncapsulation(newArrayList(
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
        properties.setSupportedTransitEncapsulation(newArrayList(
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

    @Test
    public void testToLogicalPortsValidationDto() {
        LogicalPortInfoEntry missing = LogicalPortInfoEntry.builder()
                .logicalPortNumber(LOGICAL_PORT_NUMBER_1)
                .type(LAG)
                .physicalPorts(newArrayList(PHYSICAL_PORT_1, PHYSICAL_PORT_2))
                .build();
        LogicalPortInfoEntry excess = LogicalPortInfoEntry.builder()
                .logicalPortNumber(LOGICAL_PORT_NUMBER_2)
                .type(LAG)
                .physicalPorts(newArrayList(PHYSICAL_PORT_3))
                .build();
        LogicalPortInfoEntry misconfigured = LogicalPortInfoEntry.builder()
                .logicalPortNumber(LOGICAL_PORT_NUMBER_3)
                .type(BFD)
                .physicalPorts(newArrayList(PHYSICAL_PORT_4))
                .actual(new LogicalPortMisconfiguredInfoEntry(BFD, newArrayList(PHYSICAL_PORT_4)))
                .expected(new LogicalPortMisconfiguredInfoEntry(LAG, newArrayList(
                        PHYSICAL_PORT_4, PHYSICAL_PORT_5)))
                .build();
        LogicalPortInfoEntry proper = LogicalPortInfoEntry.builder()
                .logicalPortNumber(LOGICAL_PORT_NUMBER_4)
                .type(LAG)
                .physicalPorts(newArrayList(PHYSICAL_PORT_6))
                .build();

        LogicalPortsValidationEntry validationEntry = LogicalPortsValidationEntry.builder()
                .missing(newArrayList(missing))
                .misconfigured(newArrayList(misconfigured))
                .proper(newArrayList(proper))
                .excess(newArrayList(excess))
                .build();

        LogicalPortsValidationDto validationDto = switchMapper.toLogicalPortsValidationDto(validationEntry);
        assertEquals(1, validationDto.getProper().size());
        assertEquals(1, validationDto.getMissing().size());
        assertEquals(1, validationDto.getMisconfigured().size());
        assertEquals(1, validationDto.getExcess().size());

        assertEqualsLogicalPortInfoDto(validationDto.getMissing().get(0), LOGICAL_PORT_NUMBER_1, LAG.toString(),
                PHYSICAL_PORT_1, PHYSICAL_PORT_2);
        assertEqualsLogicalPortInfoDto(validationDto.getExcess().get(0), LOGICAL_PORT_NUMBER_2, LAG.toString(),
                PHYSICAL_PORT_3);
        assertEqualsLogicalPortInfoDto(validationDto.getMisconfigured().get(0), LOGICAL_PORT_NUMBER_3, BFD.toString(),
                PHYSICAL_PORT_4);
        assertEqualsLogicalPortInfoDto(validationDto.getProper().get(0), LOGICAL_PORT_NUMBER_4, LAG.toString(),
                PHYSICAL_PORT_6);

        assertEquals(BFD.toString(), validationDto.getMisconfigured().get(0).getActual().getType());
        assertEquals(newArrayList(PHYSICAL_PORT_4),
                validationDto.getMisconfigured().get(0).getActual().getPhysicalPorts());
        assertEquals(LAG.toString(), validationDto.getMisconfigured().get(0).getExpected().getType());
        assertEquals(newArrayList(PHYSICAL_PORT_4, PHYSICAL_PORT_5),
                validationDto.getMisconfigured().get(0).getExpected().getPhysicalPorts());
    }

    private void assertEqualsLogicalPortInfoDto(LogicalPortInfoDto port, int logicalPortNumber, String type,
                                            Integer... physicalPorts) {
        assertEquals(logicalPortNumber, port.getLogicalPortNumber().intValue());
        assertEquals(type, port.getType());
        assertEquals(Arrays.asList(physicalPorts), port.getPhysicalPorts());
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

    @TestConfiguration
    @ComponentScan({"org.openkilda.northbound.converter"})
    static class Config {
        // nothing to define here
    }
}
