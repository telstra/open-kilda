package org.openkilda.integration.converter;

import java.util.ArrayList;
import java.util.List;

import org.openkilda.integration.model.response.SwitchResponse;
import org.openkilda.model.SwitchInfo;
import org.openkilda.utility.CollectionUtil;

public final class SwitchConverter {

    private SwitchConverter() {
    }

    public static List<SwitchInfo> toSwitchesInfo(final List<SwitchResponse> switchesResponse) {
        if(!CollectionUtil.isEmpty(switchesResponse)) {
            final List<SwitchInfo> switchesInfo = new ArrayList<>();
            switchesResponse.forEach(switchResponse -> {
                switchesInfo.add(toSwitchInfo(switchResponse));
            });
            return switchesInfo;
        }
        return null;
    }

    public static SwitchInfo toSwitchInfo(final SwitchResponse switchResponse) {
        SwitchInfo switchInfo = new SwitchInfo();
        String inetAddress = switchResponse.getInetAddress();
        if (inetAddress != null) {
            String[] splitInetAddress = inetAddress.split("/");
            switchInfo.setController(splitInetAddress[0]);
            switchInfo.setAddress(splitInetAddress[1]);
        }
        switchInfo.setDescription(switchResponse.getOpenFlowVersion());
        switchInfo.setName(switchResponse.getSwitchDPID());
        return switchInfo;
    }
}
