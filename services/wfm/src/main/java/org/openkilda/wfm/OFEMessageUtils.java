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

import static org.openkilda.messaging.Utils.MAPPER;
import static org.openkilda.messaging.Utils.PAYLOAD;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.Destination;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * OFEMessageUtils - A utility class that will help with the messages on kilda.speaker and
 * speaker.**.*
 * <p>
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
 * <p>
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
 * <p>
 * {"clazz": "org.openkilda.messaging.info.InfoMessage",
 * "payload": {
 * "clazz": "org.openkilda.messaging.info.event.SwitchInfoData",
 * "switch_id": "0x0000000000000001",
 * "state": "ACTIVATED"}}
 */
public class OFEMessageUtils {

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

    /**
     * @param state - ACTIVATED | ADDED | CHANGE | DEACTIVATED | REMOVED
     */
    public static String createSwitchInfoMessage(String switchID, String state) {
        return createInfoMessage(true, switchID, null, state);
    }

    /**
     * @param state - ADD | OTHER_UPDATE | DELETE | UP | DOWN
     */
    public static String createPortInfoMessage(String switchID, String portID, String state) {
        return createInfoMessage(false, switchID, portID, state);
    }

    /**
     * @param isSwitch - it is either a switch or port at this stage.
     */
    public static String createInfoMessage(boolean isSwitch, String switchID, String portID, String
            state) {
        // TODO: we don't use "type" anymore .. rewrite and leverage proper message class
        StringBuffer sb = new StringBuffer("{'type': 'INFO', ");
        sb.append("'timestamp': ").append(System.currentTimeMillis()).append(", ");
        String type = (isSwitch) ? MT_SWITCH : MT_PORT;
        sb.append("'payload': ").append(createDataMessage(type, state, switchID, portID));
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
     * @return the {"payload":{}} portion of a Kilda message type
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

    public static Map<String, ?> getData(String json) throws IOException {
        Map<String, ?> root = OFEMessageUtils.fromJson(json);
        if (root.containsKey("type")) {
            root = (Map<String, ?>) root.get(PAYLOAD);
        }
        return root;
    }

    public static String createIslFail(String switchId, String portId, String correlationId) throws IOException {
        PathNode node = new PathNode(switchId, Integer.parseInt(portId), 0, 0L);
        InfoData data = new IslInfoData(0L, Collections.singletonList(node), 0L, IslChangeType.FAILED, 0L);
        InfoMessage message = new InfoMessage(data, System.currentTimeMillis(), correlationId);
        return MAPPER.writeValueAsString(message);
    }

    // ==============  ==============  ==============  ==============  ==============
    // ISL Discovery
    // ==============  ==============  ==============  ==============  ==============

    /**
     * @return a JSON string that can be used to for link event
     */
    public static String createIslDiscovery(String switchID, String portID, String correlationId) throws IOException {
        CommandMessage message = new CommandMessage(
                new DiscoverIslCommandData(switchID, Integer.valueOf(portID)), // Payload
                System.currentTimeMillis(),
                correlationId, Destination.CONTROLLER
        );
        return MAPPER.writeValueAsString(message);
    }
}
