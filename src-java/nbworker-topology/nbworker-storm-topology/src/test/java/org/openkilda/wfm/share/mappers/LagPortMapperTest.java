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

package org.openkilda.wfm.share.mappers;

import static org.junit.Assert.assertEquals;

import org.openkilda.messaging.nbtopology.response.LagPortDto;
import org.openkilda.model.LagLogicalPort;
import org.openkilda.model.SwitchId;

import com.google.common.collect.Lists;
import org.junit.Test;

public class LagPortMapperTest {
    public static final SwitchId SWITCH_ID = new SwitchId("1");
    public static final int LAG_PORT = 2001;
    public static final int PHYSICAL_PORT_1 = 1;
    public static final int PHYSICAL_PORT_2 = 2;

    @Test
    public void mapLagPortToDtoTest() {
        LagLogicalPort lagLogicalPort = new LagLogicalPort(SWITCH_ID, LAG_PORT,
                Lists.newArrayList(PHYSICAL_PORT_1, PHYSICAL_PORT_2));

        LagPortDto dto = LagPortMapper.INSTANCE.map(lagLogicalPort);
        assertEquals(LAG_PORT, dto.getLogicalPortNumber());
        assertEquals(PHYSICAL_PORT_1, dto.getPortNumbers().get(0).intValue());
        assertEquals(PHYSICAL_PORT_2, dto.getPortNumbers().get(1).intValue());
    }
}
