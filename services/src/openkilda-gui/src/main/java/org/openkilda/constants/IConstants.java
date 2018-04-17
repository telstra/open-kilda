package org.openkilda.constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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
        public static final String ISL_LIST = "isllist";
        public static final String FLOW_LIST = "flows";
        public static final String FLOW_DETAILS = "flowdetails";
        public static final String PORT_DETAILS = "portdetails";
        public static final String SWITCH_LIST = "switch";
    }

    public enum Metrics {

        PEN_FLOW_BITS("Flow_bits", "pen.flow.bits"),

        PEN_FLOW_BYTES("Flow_bytes", "pen.flow.bytes"),

        PEN_FLOW_PACKETS("Flow_packets", "pen.flow.packets"),

        PEN_FLOW_TABLEID("Flow_tableid", "pen.flow.tableid"),

        PEN_ISL_LATENCY("Isl_latency", "pen.isl.latency"),

        PEN_SWITCH_COLLISIONS("Switch_collisions", "pen.switch.collisions"),

        PEN_SWITCH_RX_CRC_ERROR("Switch_crcerror", "pen.switch.rx-crc-error"),

        PEN_SWITCH_RX_FRAME_ERROR("Switch_frameerror", "pen.switch.rx-frame-error"),

        PEN_SWITCH_RX_OVER_ERROR("Switch_overerror", "pen.switch.rx-over-error"),

        PEN_SWITCH_RX_BITS("Switch_bits", "pen.switch.rx-bits"),

        PEN_SWITCH_TX_BITS("Switch_bits", "pen.switch.tx-bits"),

        PEN_SWITCH_RX_BYTES("Switch_bytes", "pen.switch.rx-bytes"),

        PEN_SWITCH_TX_BYTES("Switch_bytes", "pen.switch.tx-bytes"),

        PEN_SWITCH_RX_DROPPED("Switch_drops", "pen.switch.rx-dropped"),

        PEN_SWITCH_TX_DROPPED("Switch_drops", "pen.switch.tx-dropped"),

        PEN_SWITCH_RX_ERRORS("Switch_errors", "pen.switch.rx-errors"),

        PEN_SWITCH_TX_ERRORS("Switch_errors", "pen.switch.tx-errors"),

        PEN_SWITCH_TX_PACKETS("Switch_packets", "pen.switch.tx-packets"),

        PEN_SWITCH_RX_PACKETS("Switch_packets", "pen.switch.rx-packets");

        private String tag;
        private String displayTag;

        private Metrics(String tag, String displayTag) {
            this.setTag(tag);
            this.setDisplayTag(displayTag);
        }

        private void setTag(String tag) {
            this.tag = tag;
        }
        
        public String getTag() {
            return tag;
        }

        private void setDisplayTag(String displayTag) {
            this.displayTag = displayTag;
        }

        public String getDisplayTag() {
            return displayTag;
        }

        public static List<String> flowValue(String tag) {
            List<String> list = new ArrayList<String>();
            tag = "Flow_" + tag;
            for (Metrics metric : values()) {
                if (metric.getTag().equalsIgnoreCase(tag)) {
                    list.add(metric.getDisplayTag());
                    list.add(metric.getDisplayTag());
                }
            }
            return list;
        }

        public static List<String> switchValue(String tag) {
            List<String> list = new ArrayList<String>();
           
            if(tag.equalsIgnoreCase("latency"))
            	 tag = "Isl_" + tag;
            else
            	 tag = "Switch_" + tag;
            for (Metrics metric : values()) {
                if (metric.getTag().equalsIgnoreCase(tag)) {
                    list.add(metric.getDisplayTag());
                }
            }
            return list;
        }

        public static List<String> list() {
            List<String> list = new ArrayList<String>();
            for (Metrics metric : values()) {
                list.add(metric.getDisplayTag());
            }
            return list;
        }

        public static Set<String> tags() {
            Set<String> tags = new TreeSet<String>();
            for (Metrics metric : values()) {
                String[] v = metric.getTag().split("_");
                tags.add(v[1]);
            }
            return tags;
        }
    }
    
}
