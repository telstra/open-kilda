/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.share.mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.openkilda.messaging.model.SwitchPropertiesDto;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.MacAddress;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.SwitchProperties.RttState;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.Test;

public class SwitchPropertiesMapperTest {
    private static final SwitchId SWITCH_ID = new SwitchId(1);
    private static final Switch SWITCH = Switch.builder().switchId(SWITCH_ID).build();

    @Test
    public void propertiesMapping() {
        SwitchProperties switchProperties = SwitchProperties.builder()
                .switchObj(SWITCH)
                .supportedTransitEncapsulation(Sets.newHashSet(FlowEncapsulationType.TRANSIT_VLAN))
                .switchLldp(true)
                .switchArp(false)
                .server42FlowRtt(true)
                .server42IslRtt(RttState.AUTO)
                .server42Port(1)
                .server42MacAddress(MacAddress.SLOW_PROTOCOLS)
                .server42Vlan(2)
                .build();
        SwitchPropertiesDto dto = SwitchPropertiesMapper.INSTANCE.map(switchProperties);
        SwitchProperties mappedProperties = SwitchPropertiesMapper.INSTANCE.map(dto);
        mappedProperties.setSwitchObj(SWITCH); // mapper ignores switch object
        assertEquals(switchProperties, mappedProperties);
    }

    @Test
    public void multiTableIsTrueByDefault() {
        SwitchProperties emptyProperties = SwitchProperties.builder().switchObj(SWITCH).build();
        SwitchPropertiesDto dto = SwitchPropertiesMapper.INSTANCE.map(emptyProperties);
        assertTrue(dto.isMultiTable());
    }
}
