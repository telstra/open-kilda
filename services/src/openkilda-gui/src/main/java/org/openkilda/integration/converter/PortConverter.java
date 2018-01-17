package org.openkilda.integration.converter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.openkilda.integration.model.PortDetail;
import org.openkilda.integration.model.PortsDetail;
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
                PortsDetail portsDetail =
                        JsonUtil.toObject(val, PortsDetail.class);

                List<PortDetail> portDetailList = portsDetail.getPortDetail();
                if (portDetailList != null && !portDetailList.isEmpty()) {
                    ports = getPortsInfo(portDetailList, switchId, ports);
                }
            }
        }
        return ports;
    }

    private static List<PortInfo> getPortsInfo(final List<PortDetail> portsDetail,
            final String key, final List<PortInfo> switchPortsInfoList) {
        for (PortDetail portDetail : portsDetail) {

            if (!portDetail.getPortNumber().equalsIgnoreCase("local")) {
                PortInfo info = setPortInfo(key, portDetail);
                switchPortsInfoList.add(info);
            }

        }
        Collections.sort(switchPortsInfoList);
        return switchPortsInfoList;
    }

    private static PortInfo setPortInfo(final String key, final PortDetail portDetail) {
        PortInfo portInfo = new PortInfo();
        StringBuilder currentFeatures = new StringBuilder();

        if (portDetail.getCurrentFeatures() != null
                && portDetail.getCurrentFeatures().size() > 0) {

            for (int i = 0; i < portDetail.getCurrentFeatures().size(); i++) {
                if (currentFeatures.length() == 0) {
                    currentFeatures = currentFeatures.append(portDetail
                            .getCurrentFeatures().get(i));
                } else {
                    currentFeatures = currentFeatures.append(","+portDetail
                            .getCurrentFeatures().get(i));
                }
            }
            portInfo.setInterfacetype(currentFeatures.toString());
        }
        portInfo.setSwitchName(key);
        portInfo.setPortNumber(portDetail.getPortNumber());
        portInfo.setPortName(portDetail.getName());
        if (portDetail.getState() != null && !portDetail.getState().isEmpty()) {
            portInfo.setStatus(portDetail.getState().get(0));
        }

        return portInfo;
    }
}
