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

import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.SpeakerSwitchDescription;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;

import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;

import java.util.Set;

public class SwitchMapperTest {

    private static final SwitchId TEST_SWITCH_ID = new SwitchId(1);

    @Test
    public void switchMapperTest() {
        Set<SwitchFeature> features = Sets.newHashSet(SwitchFeature.METERS, SwitchFeature.PKTPS_FLAG);
        SpeakerSwitchView switchView = SpeakerSwitchView.builder()
                .ofVersion("13")
                .description(SpeakerSwitchDescription.builder()
                        .datapath("datapath")
                        .hardware("hardware")
                        .manufacturer("manufacturer")
                        .serialNumber("serial_number")
                        .software("software")
                        .build())
                .features(features)
                .build();

        Switch sw = Switch.builder()
                .switchId(TEST_SWITCH_ID)
                .status(SwitchStatus.ACTIVE)
                .address("address")
                .hostname("hostname")
                .description("description")
                .controller("controller")
                .underMaintenance(false)
                .build();
        sw.setOfVersion(switchView.getOfVersion());
        sw.setOfDescriptionDatapath(switchView.getDescription().getDatapath());
        sw.setOfDescriptionManufacturer(switchView.getDescription().getManufacturer());
        sw.setOfDescriptionHardware(switchView.getDescription().getHardware());
        sw.setOfDescriptionSoftware(switchView.getDescription().getSoftware());
        sw.setOfDescriptionSerialNumber(switchView.getDescription().getSerialNumber());
        sw.setFeatures(switchView.getFeatures());

        SwitchInfoData switchInfoData = new SwitchInfoData(TEST_SWITCH_ID, SwitchMapper.INSTANCE.map(sw.getStatus()),
                sw.getAddress(), sw.getHostname(), sw.getDescription(), sw.getController(), sw.isUnderMaintenance(),
                switchView);

        SwitchInfoData switchInfoDataMapping = SwitchMapper.INSTANCE.map(sw);
        Assert.assertEquals(switchInfoData, switchInfoDataMapping);

        Switch swMapping = SwitchMapper.INSTANCE.map(switchInfoData);
        Assert.assertEquals(sw, swMapping);
    }
}
