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

import org.junit.Assert;
import org.junit.Test;
import org.mapstruct.factory.Mappers;

public class PortMapperTest {

    private static final SwitchId TEST_SWITCH_ID = new SwitchId(1);

    @Test
    public void portMapperTest() {
        PortMapper portMapper = Mappers.getMapper(PortMapper.class);
        PortInfoData portInfoData = new PortInfoData(TEST_SWITCH_ID, 1, 10000000L,
                999999L, PortChangeType.UP);

        Port port = portMapper.map(portInfoData);

        Assert.assertNotNull(port.getSwitchObj());
        Assert.assertEquals(portInfoData.getSwitchId(), port.getSwitchObj().getSwitchId());
        Assert.assertEquals(portInfoData.getSwitchId(), port.getSwitchId());
        Assert.assertEquals(portInfoData.getPortNo(), port.getPortNo());
        Assert.assertEquals(portInfoData.getCurrentSpeed().longValue(), port.getCurrentSpeed());
        Assert.assertEquals(portInfoData.getMaxSpeed().longValue(), port.getMaxSpeed());

        PortInfoData portInfoDataMapping = portMapper.map(port);

        Assert.assertEquals(port.getSwitchId(), portInfoDataMapping.getSwitchId());
        Assert.assertEquals(port.getPortNo(), portInfoDataMapping.getPortNo());
        Assert.assertEquals(port.getCurrentSpeed(), portInfoDataMapping.getCurrentSpeed().longValue());
        Assert.assertEquals(port.getMaxSpeed(), portInfoDataMapping.getMaxSpeed().longValue());
    }
}
