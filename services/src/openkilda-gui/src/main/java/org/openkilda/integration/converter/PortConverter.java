package org.openkilda.integration.converter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.openkilda.integration.model.response.PortDesc;
import org.openkilda.integration.model.response.PortDetailResponse;
import org.openkilda.model.PortInfo;
import org.openkilda.utility.JsonUtil;

public final class PortConverter {

    private PortConverter() {}

    public static List<PortInfo> toPortsInfo(final JSONObject jsonObject, final String switchId) {
        List<PortInfo> ports = new ArrayList<PortInfo>();
        if (jsonObject != null) {
            Object object = jsonObject.get(switchId);
            if (object != null) {
                String val = JSONValue.toJSONString(object);
                PortDetailResponse portDetailResponse =
                        JsonUtil.toObject(val, PortDetailResponse.class);

                List<PortDesc> portDescList = portDetailResponse.getPortDesc();
                if (portDescList != null && !portDescList.isEmpty()) {
                    ports = getPortsInfo(portDescList, switchId, ports);
                }
            }
        }
        return ports;
    }

    private static List<PortInfo> getPortsInfo(final List<PortDesc> portDescList,
            final String key, final List<PortInfo> switchPortsInfoList) {
        for (PortDesc portDesc : portDescList) {

            if (!portDesc.getPortNumber().equalsIgnoreCase("local")) {
                PortInfo info = setPortInfo(key, portDesc);
                switchPortsInfoList.add(info);
            }

        }
        Collections.sort(switchPortsInfoList);
        return switchPortsInfoList;
    }

    private static PortInfo setPortInfo(final String key, final PortDesc portDesc) {
        PortInfo portInfo = new PortInfo();
        StringBuilder currentFeatures = new StringBuilder();

        if (portDesc.getCurrentFeatures() != null
                && portDesc.getCurrentFeatures().size() > 0) {

            for (int i = 0; i < portDesc.getCurrentFeatures().size(); i++) {
                if (currentFeatures.length() == 0) {
                    currentFeatures = currentFeatures.append(portDesc
                            .getCurrentFeatures().get(i));
                } else {
                    currentFeatures = currentFeatures.append(","+portDesc
                            .getCurrentFeatures().get(i));
                }
            }
            portInfo.setInterfacetype(currentFeatures.toString());
        }
        portInfo.setSwitchName(key);
        portInfo.setPortNumber(portDesc.getPortNumber());
        portInfo.setPortName(portDesc.getName());
        if (portDesc.getState() != null && !portDesc.getState().isEmpty()) {
            portInfo.setStatus(portDesc.getState().get(0));
        }

        return portInfo;
    }
}
