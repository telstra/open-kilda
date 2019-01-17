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

import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;

import org.junit.Assert;
import org.junit.Test;

public class SwitchMapperTest {

    private static final SwitchId TEST_SWITCH_ID = new SwitchId(1);

    @Test
    public void switchMapperTest() {

        SwitchInfoData switchInfoData = new SwitchInfoData(TEST_SWITCH_ID, SwitchChangeType.ACTIVATED, "address",
                "hostname", "description", "controller", false);
        Switch sw = SwitchMapper.INSTANCE.map(switchInfoData);

        Assert.assertEquals(SwitchStatus.ACTIVE, sw.getStatus());

        SwitchInfoData switchInfoDataMapping = SwitchMapper.INSTANCE.map(sw);
        switchInfoDataMapping.setState(SwitchChangeType.ACTIVATED);

        Assert.assertEquals(switchInfoData, switchInfoDataMapping);
    }

}
