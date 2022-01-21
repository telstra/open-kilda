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

package org.openkilda.northbound.converter;

import static org.junit.Assert.assertEquals;

import org.openkilda.messaging.nbtopology.response.LagPortDto;
import org.openkilda.messaging.swmanager.response.LagPortResponse;

import com.google.common.collect.Lists;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class LagPortMapperTest {
    public static final int LOGICAL_PORT_NUMBER_1 = 2021;
    public static final int PHYSICAL_PORT_NUMBER_1 = 1;
    public static final int PHYSICAL_PORT_NUMBER_2 = 2;

    @Autowired
    private LagPortMapper lagMapper;

    @Test
    public void mapLagPortDtoTest() {
        LagPortDto response = new LagPortDto(LOGICAL_PORT_NUMBER_1,
                Lists.newArrayList(PHYSICAL_PORT_NUMBER_1, PHYSICAL_PORT_NUMBER_2));

        org.openkilda.northbound.dto.v2.switches.LagPortDto dto = lagMapper.map(response);
        assertEquals(LOGICAL_PORT_NUMBER_1, dto.getLogicalPortNumber());
        assertEquals(PHYSICAL_PORT_NUMBER_1, dto.getPortNumbers().get(0).intValue());
        assertEquals(PHYSICAL_PORT_NUMBER_2, dto.getPortNumbers().get(1).intValue());
    }

    @Test
    public void mapLagResponseTest() {
        LagPortResponse response = new LagPortResponse(LOGICAL_PORT_NUMBER_1,
                Lists.newArrayList(PHYSICAL_PORT_NUMBER_1, PHYSICAL_PORT_NUMBER_2));

        org.openkilda.northbound.dto.v2.switches.LagPortDto dto = lagMapper.map(response);
        assertEquals(LOGICAL_PORT_NUMBER_1, dto.getLogicalPortNumber());
        assertEquals(PHYSICAL_PORT_NUMBER_1, dto.getPortNumbers().get(0).intValue());
        assertEquals(PHYSICAL_PORT_NUMBER_2, dto.getPortNumbers().get(1).intValue());
    }

    @TestConfiguration
    @ComponentScan({"org.openkilda.northbound.converter"})
    static class Config {
        // nothing to define here
    }
}
