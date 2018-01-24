package org.openkilda.constants;

/**
 * The Interface IConstants.
 *
 * @author Gaurav Chugh
 */
public abstract class IConstants {

    public static final String SESSION_OBJECT = "sessionObject";

    public class Role {
        public static final String ADMIN = "ROLE_ADMIN";
        public static final String USER = "ROLE_USER";
    }


    public class View {
        public static final String ERROR = "error";
        public static final String ERROR_403 = "403";
        public static final String LOGIN = "login";
        public static final String HOME = "home";
        public static final String TOPOLOGY = "topology";
        public static final String LOGOUT = "logout";
        public static final String REDIRECT_HOME = "redirect:/home";
        public static final String REDIRECT_LOGIN = "redirect:/login";
        public static final String SWITCH = "switchdetails";
        public static final String ISL = "isl";
        public static final String FLOW_LIST = "flows";
        public static final String FLOW_DETAILS = "flowdetails";
        public static final String PORT_DETAILS = "portdetails";
        public static final String SWITCH_LIST = "switch";
    }

    public static class Metrics {
        public static final String PEN_FLOW_BITS = "pen.flow.bits";
        public static final String PEN_FLOW_BYTES = "pen.flow.bytes";
        public static final String PEN_FLOW_PACKETS = "pen.flow.packets";
        public static final String PEN_FLOW_TABLEID = "pen.flow.tableid";
        public static final String PEN_ISL_LATENCY = "pen.isl.latency";
        public static final String PEN_SWITCH_COLLISIONS = "pen.switch.collisions";
        public static final String PEN_SWITCH_RX_BITS = "pen.switch.rx-bits";
        public static final String PEN_SWITCH_RX_BYTES = "pen.switch.rx-bytes";
        public static final String PEN_SWITCH_RX_CRC_ERROR = "pen.switch.rx-crc-error";
        public static final String PEN_SWITCH_RX_DROPPED = "pen.switch.rx-dropped";
        public static final String PEN_SWITCH_RX_ERRORS = "pen.switch.rx-errors";
        public static final String PEN_SWITCH_RX_FRAME_ERROR = "pen.switch.rx-frame-error";
        public static final String PEN_SWITCH_RX_OVER_ERROR = "pen.switch.rx-over-error";
        public static final String PEN_SWITCH_RX_PACKETS = "pen.switch.rx-packets";
        public static final String PEN_SWITCH_TX_BITS = "pen.switch.tx-bits";
        public static final String PEN_SWITCH_TX_BYTES = "pen.switch.tx-bytes";
        public static final String PEN_SWITCH_TX_DROPPED = "pen.switch.tx-dropped";
        public static final String PEN_SWITCH_TX_ERRORS = "pen.switch.tx-errors";
        public static final String PEN_SWITCH_TX_PACKETS = "pen.switch.tx-packets";

        public static final String[] LIST =
                {PEN_FLOW_BITS, PEN_FLOW_BYTES, PEN_FLOW_PACKETS, PEN_FLOW_TABLEID, PEN_ISL_LATENCY,
                        PEN_SWITCH_COLLISIONS, PEN_SWITCH_RX_BITS, PEN_SWITCH_RX_BYTES,
                        PEN_SWITCH_RX_CRC_ERROR, PEN_SWITCH_RX_DROPPED, PEN_SWITCH_RX_ERRORS,
                        PEN_SWITCH_RX_FRAME_ERROR, PEN_SWITCH_RX_OVER_ERROR, PEN_SWITCH_RX_PACKETS,
                        PEN_SWITCH_TX_BITS,
                        PEN_SWITCH_TX_BYTES, PEN_SWITCH_TX_DROPPED, PEN_SWITCH_TX_ERRORS,
                        PEN_SWITCH_TX_PACKETS};
    }
}
