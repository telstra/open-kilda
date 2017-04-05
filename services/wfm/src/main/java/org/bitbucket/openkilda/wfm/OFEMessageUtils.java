package org.bitbucket.openkilda.wfm;

/**
 * OFEMessageUtils - A utility class that will help with the messages on kilda.speaker and
 * speaker.**.*
 */
public class OFEMessageUtils {

    public static final String SWITCH_UP = "ACTIVATED";
    public static final String SWITCH_DOWN = "DEACTIVATED";
    public static final String PORT_UP = "UP";
    public static final String PORT_DOWN = "DOWN";

    /**
     * @param state - ACTIVATED | ADDED | CHANGE | DEACTIVATED | REMOVED
     */
    public static String createSwitchInfoMessage (String switchID, String state) {
        return createInfoMessage(true,switchID,null,state);
    }

    /**
     * @param state - ADD | OTHER_UPDATE | DELETE | UP | DOWN
     */
    public static String createPortInfoMessage (String switchID, String portID, String state) {
        return createInfoMessage(false,switchID,portID,state);
    }

    /**
     * TODO: this handles switch / port messages, but not ISL. Add it.
     * Example OpenFlow Messages:
     {
     "type": "INFO",
     "timestamp": 1489980143,
     "data": {
     "message_type": "switch",
     "switch_id": "0x0000000000000001",
     "state": "ACTIVATED | ADDED | CHANGE | DEACTIVATED | REMOVED"
     }
     }

     {
     "type": "INFO",
     "timestamp": 1489980143,
     "data": {
     "message_type": "port",
     "switch_id": "0x0000000000000001",
     "state": "UP | DOWN | .. "
     "port_no": LONG
     "max_capacity": LONG
     }
     }

     {"type": "INFO", "data": {"message_type": "switch", "switch_id": "0x0000000000000001", "state": "ACTIVATED"}}

     * @param isSwitch - it is either a switch or port at this stage.
     */
    public static String createInfoMessage (boolean isSwitch, String switchID, String portID, String
            state) {
        StringBuffer sb = new StringBuffer("{'type': 'INFO', ");
        sb.append("'timestamp': ").append(System.currentTimeMillis()).append(", ");
        String type = (isSwitch) ? "switch" : "port";
        sb.append(createDataMessage(type,state,switchID,portID)).append("}");
        return sb.toString().replace("'","\"");
    }

    public static String createSwitchDataMessage(String state, String switchId){
        return createDataMessage("switch", state, switchId, null);
    }

    public static String createPortDataMessage(String state, String switchId, String portId){
        return createDataMessage("port", state, switchId, portId);
    }

    /**
     * @return the {"data":{}} portion of a Kilda message type
     */
    public static String createDataMessage(String type, String state, String switchId, String
            portId){
        StringBuffer sb = new StringBuffer();
        sb.append("'data': {'message_type': '").append(type).append("', ");
        sb.append("'switch_id': '").append(switchId).append("', ");
        if (portId != null && portId.length()>0) {
            sb.append(", 'port_no': ").append(portId).append("', ");
        }
        sb.append("'state': '").append(state).append("'}");

        return sb.toString().replace("'","\"");
    }

}
