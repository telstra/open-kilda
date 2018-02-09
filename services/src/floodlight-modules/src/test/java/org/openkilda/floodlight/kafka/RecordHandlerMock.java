package org.openkilda.floodlight.kafka;

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
        super(context, EasyMock.mock(ConsumerRecord.class));
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
