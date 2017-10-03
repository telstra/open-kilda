package org.bitbucket.openkilda.wfm;

import static org.bitbucket.openkilda.messaging.Utils.MAPPER;
import static org.bitbucket.openkilda.messaging.Utils.PAYLOAD;

import org.bitbucket.openkilda.messaging.info.InfoData;
import org.bitbucket.openkilda.messaging.info.event.IslChangeType;
import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathNode;

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
 * "type": "INFO",
 * "timestamp": 1489980143,
 * "payload": {
 * ""message_type"": "switch",
 * "switch_id": "0x0000000000000001",
 * "state": "ACTIVATED | ADDED | CHANGE | DEACTIVATED | REMOVED"
 * }
 * }
 * <p>
 * {
 * "type": "INFO",
 * "timestamp": 1489980143,
 * "payload": {
 * ""message_type"": "port",
 * "switch_id": "0x0000000000000001",
 * "state": "UP | DOWN | .. "
 * "port_no": LONG
 * "max_capacity": LONG
 * }
 * }
 * <p>
 * {"type": "INFO", "payload": {""message_type"": "switch", "switch_id": "0x0000000000000001", "state": "ACTIVATED"}}
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
        StringBuffer sb = new StringBuffer("{'type': 'INFO', ");
        sb.append("'timestamp': ").append(System.currentTimeMillis()).append(", ");
        String type = (isSwitch) ? "switch" : "port";
        sb.append("'payload': ").append(createDataMessage(type, state, switchID, portID));
        sb.append("}");
        return sb.toString().replace("'", "\"");
    }

    public static String createSwitchDataMessage(String state, String switchId) {
        return createDataMessage("switch", state, switchId, null);
    }

    public static String createPortDataMessage(String state, String switchId, String portId) {
        return createDataMessage("port", state, switchId, portId);
    }

    /**
     * @return the {"payload":{}} portion of a Kilda message type
     */
    public static String createDataMessage(String type, String state, String switchId, String
            portId) {
        StringBuffer sb = new StringBuffer();
        sb.append("{'message_type': '").append(type).append("', ");
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

    public static String createIslFail(String switchId, String portId) throws IOException {
        PathNode node = new PathNode(switchId, Integer.parseInt(portId), 0, 0L);
        InfoData data = new IslInfoData(0L, Collections.singletonList(node), 0L, IslChangeType.FAILED, 0L);
        return MAPPER.writeValueAsString(data);
    }

    // ==============  ==============  ==============  ==============  ==============
    // ISL Discovery
    // ==============  ==============  ==============  ==============  ==============

    /**
     * @return a JSON string that can be used to for link event
     */
    public static String createIslDiscovery(String switchID, String portID) {
        StringBuffer sb = new StringBuffer("{\"type\": \"COMMAND\"");
        sb.append(", \"destination\": \"CONTROLLER\"");
        sb.append(", \"timestamp\": ").append(System.currentTimeMillis());
        sb.append(", \"payload\": ");
        sb.append("{\"command\": \"discover_isl\"");
        sb.append(", \"switch_id\": \"").append(switchID).append("\"");
        sb.append(", \"port_no\": \"").append(portID).append("\"");
        sb.append("}}");
        return sb.toString();
    }
}
