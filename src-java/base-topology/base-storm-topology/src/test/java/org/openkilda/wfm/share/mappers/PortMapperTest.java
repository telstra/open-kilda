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

import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.model.Port;
import org.openkilda.model.SwitchId;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

public class PortMapperTest {

    private static final SwitchId TEST_SWITCH_ID = new SwitchId(1);

    @Test
    public void portMapperTest() {
        PortMapper portMapper = Mappers.getMapper(PortMapper.class);
        PortInfoData portInfoData = new PortInfoData(TEST_SWITCH_ID, 1, 10000000L,
                999999L, PortChangeType.UP);

        Port port = portMapper.map(portInfoData);

        Assertions.assertNotNull(port.getSwitchObj());
        Assertions.assertEquals(portInfoData.getSwitchId(), port.getSwitchObj().getSwitchId());
        Assertions.assertEquals(portInfoData.getSwitchId(), port.getSwitchId());
        Assertions.assertEquals(portInfoData.getPortNo(), port.getPortNo());
        Assertions.assertEquals(portInfoData.getCurrentSpeed().longValue(), port.getCurrentSpeed());
        Assertions.assertEquals(portInfoData.getMaxSpeed().longValue(), port.getMaxSpeed());

        PortInfoData portInfoDataMapping = portMapper.map(port);

        Assertions.assertEquals(port.getSwitchId(), portInfoDataMapping.getSwitchId());
        Assertions.assertEquals(port.getPortNo(), portInfoDataMapping.getPortNo());
        Assertions.assertEquals(port.getCurrentSpeed(), portInfoDataMapping.getCurrentSpeed().longValue());
        Assertions.assertEquals(port.getMaxSpeed(), portInfoDataMapping.getMaxSpeed().longValue());
    }
}
