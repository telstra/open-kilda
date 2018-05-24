/* Copyright 2017 Telstra Open Source
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

package org.openkilda.floodlight.kafka;

import org.openkilda.floodlight.switchmanager.MeterPool;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.info.event.SwitchInfoData;

import net.floodlightcontroller.core.IOFSwitch;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.easymock.EasyMock;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.HashMap;
import java.util.Map;

class RecordHandlerMock extends RecordHandler {
    Map<DatapathId, SwitchInfoData> switchInfoDataOverride;

    RecordHandlerMock(ConsumerContext context) {
        super(context, EasyMock.mock(ConsumerRecord.class), new MeterPool());
        switchInfoDataOverride = new HashMap<>();
    }

    void handleMessage(CommandMessage message) {
        doControllerMsg(message);
    }

    public void overrideSwitchInfoData(DatapathId swId, SwitchInfoData infoData) {
        switchInfoDataOverride.put(swId, infoData);
    }

    @Override
    protected SwitchInfoData buildSwitchInfoData(IOFSwitch sw) {
        if (switchInfoDataOverride.containsKey(sw.getId())) {
            return switchInfoDataOverride.get(sw.getId());
        }
        return super.buildSwitchInfoData(sw);
    }
}
