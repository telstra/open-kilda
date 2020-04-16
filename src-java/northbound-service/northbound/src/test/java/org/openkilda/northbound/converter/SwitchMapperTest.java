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
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto;

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
}
