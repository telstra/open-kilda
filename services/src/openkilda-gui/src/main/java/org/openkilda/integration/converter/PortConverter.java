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

package org.openkilda.integration.converter;

import org.openkilda.integration.model.PortDetail;
import org.openkilda.integration.model.PortsDetail;
import org.openkilda.model.PortInfo;
import org.openkilda.utility.JsonUtil;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// TODO: Auto-generated Javadoc
/**
 * The Class PortConverter.
 */
public final class PortConverter {

    /**
     * Instantiates a new port converter.
     */
    private PortConverter() {
    }

    /**
     * To ports info.
     *
     * @param jsonObject the json object
     * @param switchId the switch id
     * @return the list
     * @throws JsonParseException the json parse exception
     * @throws JsonMappingException the json mapping exception
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public static List<PortInfo> toPortsInfo(final JSONObject jsonObject, final String switchId)
            throws JsonParseException, JsonMappingException, IOException {
        List<PortInfo> ports = new ArrayList<PortInfo>();
        if (jsonObject != null) {
            Object object = jsonObject.get(switchId);
            if (object != null) {
                String val = JSONValue.toJSONString(object);
                PortsDetail portsDetail = JsonUtil.toObject(val, PortsDetail.class);

                List<PortDetail> portDetailList = portsDetail.getPortDetail();
                if (portDetailList != null && !portDetailList.isEmpty()) {
                    ports = getPortsInfo(portDetailList, switchId, ports);
                }
            }
        }
        return ports;
    }

    /**
     * Gets the ports info.
     *
     * @param portsDetail the ports detail
     * @param key the key
     * @param switchPortsInfoList the switch ports info list
     * @return the ports info
     */
    private static List<PortInfo> getPortsInfo(final List<PortDetail> portsDetail, final String key,
            final List<PortInfo> switchPortsInfoList) {
        for (PortDetail portDetail : portsDetail) {

            if (!portDetail.getPortNumber().equalsIgnoreCase("local")) {
                PortInfo info = setPortInfo(key, portDetail);
                switchPortsInfoList.add(info);
            }

        }
        Collections.sort(switchPortsInfoList);
        return switchPortsInfoList;
    }

    /**
     * Sets the port info.
     *
     * @param key the key
     * @param portDetail the port detail
     * @return the port info
     */
    private static PortInfo setPortInfo(final String key, final PortDetail portDetail) {
        PortInfo portInfo = new PortInfo();
        StringBuilder currentFeatures = new StringBuilder();

        if (portDetail.getCurrentFeatures() != null && portDetail.getCurrentFeatures().size() > 0) {

            for (int i = 0; i < portDetail.getCurrentFeatures().size(); i++) {
                if (currentFeatures.length() == 0) {
                    currentFeatures = currentFeatures.append(portDetail.getCurrentFeatures().get(i));
                } else {
                    currentFeatures = currentFeatures.append("," + portDetail.getCurrentFeatures().get(i));
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
