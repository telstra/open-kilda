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

import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.SwitchId;

import org.junit.Assert;
import org.junit.Test;

public class IslMapperTest {

    private static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    private static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);

    @Test
    public void islMapperTest() {
        IslInfoData islInfoData = new IslInfoData(1, new PathNode(TEST_SWITCH_A_ID, 1, 0),
                new PathNode(TEST_SWITCH_B_ID, 1, 1),
                2, IslChangeType.DISCOVERED, 4, false, null);

        Isl isl = IslMapper.INSTANCE.map(islInfoData);

        Assert.assertEquals(IslStatus.ACTIVE, isl.getStatus());

        IslInfoData islInfoDataMapping = IslMapper.INSTANCE.map(isl);
        islInfoDataMapping.setState(IslChangeType.DISCOVERED);

        Assert.assertEquals(islInfoData, islInfoDataMapping);
    }
}
