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

package org.openkilda.wfm;

import static org.openkilda.messaging.Utils.PAYLOAD;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

/**
 * OfeMessageUtils - A utility class that will help with the messages on kilda.speaker and
 * speaker.**.*
 * <p/>
 * Example OpenFlow Messages:
 * {
 * "clazz": "org.openkilda.messaging.info.InfoMessage",
 * "timestamp": 1489980143,
 * "payload": {
 * "clazz": "org.openkilda.messaging.info.event.SwitchInfoData",
 * "switch_id": "0x0000000000000001",
 * "state": "ACTIVATED | ADDED | CHANGE | DEACTIVATED | REMOVED"
 * }
 * }
 * <p/>
 * {
 * "clazz": "org.openkilda.messaging.info.InfoMessage",
 * "timestamp": 1489980143,
 * "payload": {
 * "clazz": "org.openkilda.messaging.info.event.PortInfoData",
 * "switch_id": "0x0000000000000001",
 * "state": "UP | DOWN | .. "
 * "port_no": LONG
 * "max_capacity": LONG
 * }
 * }
 * <p/>
 * {"clazz": "org.openkilda.messaging.info.InfoMessage",
 * "payload": {
 * "clazz": "org.openkilda.messaging.info.event.SwitchInfoData",
 * "switch_id": "0x0000000000000001",
 * "state": "ACTIVATED"}}
 */

public final class OfeMessageUtils {

    public static final String FIELD_SWITCH_ID = "switch_id";
    public static final String FIELD_PORT_ID = "port_no";
    public static final String FIELD_STATE = "state";

    public static final String SWITCH_UP = "ACTIVATED";
    public static final String SWITCH_DOWN = "DEACTIVATED";
    public static final String PORT_UP = "UP";
    public static final String PORT_ADD = "ADD";
    public static final String PORT_DOWN = "DOWN";
    public static final String LINK_UP = "DISCOVERED";
    public static final String LINK_DOWN = "FAILED";

    // TODO: We should use the actual message class, not build from scratch and reference the class
    public static final String MT_SWITCH = "org.openkilda.messaging.info.event.SwitchInfoData";
    public static final String MT_PORT = "org.openkilda.messaging.info.event.PortInfoData";

    // ==============  ==============  ==============  ==============  ==============
    // Parsing Routines
    // ==============  ==============  ==============  ==============  ==============
    private static final ObjectMapper _mapper = new ObjectMapper();

    private OfeMessageUtils() {
        throw new UnsupportedOperationException();
    }

    /**
     * Return switch info message.
     *
     * @param switchId switch id.
     * @param state - ACTIVATED | ADDED | CHANGE | DEACTIVATED | REMOVED
     * @return switch info message.
     */
    public static String createSwitchInfoMessage(String switchId, String state) {
        return createInfoMessage(true, switchId, null, state);
    }

    /**
     * Return port info message.
     *
     * @param switchId switch id.
     * @param portId port id.
     * @param state - ADD | OTHER_UPDATE | DELETE | UP | DOWN
     * @return port info message.
     */
    public static String createPortInfoMessage(String switchId, String portId, String state) {
        return createInfoMessage(false, switchId, portId, state);
    }

    /**
     * Return info message.
     *
     * @param isSwitch it is either a switch or port at this stage.
     * @param switchId switch id.
     * @param portId port id.
     * @param state state.
     * @return info message.
     */
    public static String createInfoMessage(boolean isSwitch, String switchId, String portId, String
            state) {
        // TODO: we don't use "type" anymore .. rewrite and leverage proper message class
        StringBuffer sb = new StringBuffer("{'type': 'INFO', ");
        sb.append("'timestamp': ").append(System.currentTimeMillis()).append(", ");
        String type = (isSwitch) ? MT_SWITCH : MT_PORT;
        sb.append("'payload': ").append(createDataMessage(type, state, switchId, portId));
        sb.append("}");
        return sb.toString().replace("'", "\"");
    }

    public static String createSwitchDataMessage(String state, String switchId) {
        return createDataMessage(MT_SWITCH, state, switchId, null);
    }

    public static String createPortDataMessage(String state, String switchId, String portId) {
        return createDataMessage(MT_PORT, state, switchId, portId);
    }

    /**
     * Return the {"payload":{}} portion of a Kilda message type.
     *
     * @return the {"payload":{}} portion of a Kilda message type.
     */
    public static String createDataMessage(String type, String state, String switchId, String
            portId) {
        StringBuffer sb = new StringBuffer();
        sb.append("{'clazz': '").append(type).append("', ");
        sb.append("'switch_id': '").append(switchId).append("', ");
        if (portId != null && portId.length() > 0) {
            sb.append("'port_no': ").append(portId).append(", ");
        }
        sb.append("'state': '").append(state).append("'}");

        return sb.toString().replace("'", "\"");
    }

    public static Map<String, ?> fromJson(String json) throws IOException {
        return _mapper.readValue(json, Map.class);
    }

    public static String toJson(Map<String, ?> map) throws IOException {
        return _mapper.writeValueAsString(map);
    }

    /**
     * Return data from json.
     *
     * @param json JSON.
     * @return data from json.
     * @throws IOException error getting root from json.
     */
    public static Map<String, ?> getData(String json) throws IOException {
        Map<String, ?> root = OfeMessageUtils.fromJson(json);
        if (root.containsKey("type")) {
            root = (Map<String, ?>) root.get(PAYLOAD);
        }
        return root;
    }
}
