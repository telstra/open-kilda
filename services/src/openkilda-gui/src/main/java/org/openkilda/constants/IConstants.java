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

    private IConstants() {

    }

    public final class Role {
        
        private Role() {

        }

        public static final String ADMIN = "ROLE_ADMIN";
        public static final String USER = "ROLE_USER";
    }

    public final class Status {
        
        private Status() {

        }

        public static final String UP = "UP";
        public static final String DOWN = "DOWN";
    }
    
    public final class NorthBoundUrl {

        private NorthBoundUrl() {

        }

        public static final String GET_FLOW = "/flows";
        public static final String GET_FLOW_STATUS = GET_FLOW + "/status/";
        public static final String GET_FLOW_REROUTE = GET_FLOW + "/{flow_id}/reroute";
        public static final String GET_FLOW_VALIDATE = GET_FLOW + "/{flow_id}/validate";
        public static final String GET_PATH_FLOW = GET_FLOW + "/path";
        public static final String GET_SWITCHES = "/switches";
        public static final String GET_SWITCH_RULES = GET_SWITCHES + "/{switch_id}/rules";
        public static final String GET_LINKS = "/links";
        public static final String GET_LINK_PROPS = "/link/props";
        public static final String UPDATE_FLOW = GET_FLOW + "/{flow_id}";
        public static final String GET_FLOW_PATH = GET_FLOW + "/{flow_id}/path";
        public static final String RESYNC_FLOW = GET_FLOW + "/{flow_id}/sync";
        public static final String CONFIG_SWITCH_PORT = GET_SWITCHES + "/{switch_id}/port/{port_no}/config";
    }
    
    public final class OpenTsDbUrl {

        private OpenTsDbUrl() {

        }

        public static final String OPEN_TSDB_QUERY = "/api/query/";
    }

    public final class Permission {
        
        private Permission() {

        }

        public static final String MENU_TOPOLOGY = "menu_topology";
        
        public static final String MENU_FLOWS = "menu_flows";
        
        public static final String MENU_ISL = "menu_isl";
        
        public static final String MENU_SWITCHES = "menu_switches";
        
        public static final String MENU_USER_MANAGEMENT = "menu_user_management";
        
        public static final String MENU_USER_ACTIVITY = "menu_user_activity";

        public static final String UM_ROLE = "um_role";
        
        public static final String UM_PERMISSION = "um_permission";

        public static final String UM_USER_ADD = "um_user_add";
        
        public static final String UM_USER_EDIT = "um_user_edit";
        
        public static final String UM_USER_DELETE = "um_user_delete";
        
        public static final String UM_USER_RESET = "um_user_reset";
        
        public static final String UM_USER_RESET_ADMIN = "um_user_reset_admin";
        
        public static final String UM_USER_RESET2FA = "um_user_reset2fa";
        
        public static final String UM_ROLE_ADD = "um_role_add";
        
        public static final String UM_ROLE_EDIT = "um_role_edit";
        
        public static final String UM_ROLE_DELETE = "um_role_delete";
        
        public static final String UM_ROLE_VIEW_USERS = "um_role_view_users";
        
        public static final String UM_PERMISSION_ADD = "um_permission_add";
        
        public static final String UM_PERMISSION_EDIT = "um_permission_edit";
        
        public static final String UM_PERMISSION_DELETE = "um_permission_delete";
        
        public static final String UM_PERMISSION_VIEW_ROLES = "um_permission_view_roles";
        
        public static final String UM_PERMISSION_ASSIGN_ROLES = "um_permission_assign_roles";
        
        public static final String UM_ASSIGN_ROLE_TO_USERS = "um_assign_role_to_users";

        public static final String UM_USER_ACTIVATE = "um_user_activate";
        
        public static final String UM_PERMISSION_ACTIVATE = "um_permission_activate";
        
        public static final String UM_ROLE_ASSIGN_USERS = "um_role_assign_users";
        
        public static final String UM_ASSIGN_PERMISSION_TO_ROLES = "um_assign_permission_to_roles";

        public static final String SW_PERMISSION_RULES = "sw_permission_rules";
        
        public static final String FW_PERMISSION_REROUTE = "fw_permission_reroute";
        
        public static final String ISL_PERMISSION_EDITCOST = "isl_permission_editcost";
        
        public static final String FW_PERMISSION_VALIDATE = "fw_permission_validate";

        public static final String FW_FLOW_CREATE = "fw_flow_create";
        
        public static final String FW_FLOW_UPDATE = "fw_flow_update";
        
        public static final String FW_FLOW_DELETE = "fw_flow_delete";

        public static final String FW_FLOW_RESYNC = "fw_flow_resync";
        
        public static final String SW_PORT_CONFIG = "sw_port_config";
        
        public static final String STORE_SETTING = "store_setting";
        
    }

    public final class Settings {
        
        private Settings() {

        }

        public static final String TOPOLOGY_SETTING = "topology_setting";
    }

    public final class View {
        
        private View() {

        }

        public static final String ERROR = "errors/error";
        
        public static final String ERROR_403 = "errors/403";
        
        public static final String LOGIN = "login";
        
        public static final String HOME = "home";
        
        public static final String TOPOLOGY = "topology/topology";
        
        public static final String LOGOUT = "login/logout";
        
        public static final String REDIRECT_HOME = "redirect:/home";
        
        public static final String REDIRECT_LOGIN = "redirect:/login";
        
        public static final String SWITCH = "switch/switchdetails";
        
        public static final String ISL = "isl/isl";
        
        public static final String ISL_LIST = "isl/isllist";
        
        public static final String FLOW_LIST = "flows/flows";
        
        public static final String FLOW_DETAILS = "flows/flowdetails";
        
        public static final String PORT_DETAILS = "port/portdetails";
        
        public static final String SWITCH_LIST = "switch/switch";
        
        public static final String USER_MANAGEMENT = "usermanagement/usermanagement";
        public static final String STORE_SETTING = "storesetting/storesetting";
        
        public static final String TWO_FA_GENERATOR = "twofa";
        
        public static final String OTP = "otp";
        
        public static final String ACTIVITY_LOGS = "useractivity/useractivity";
    }

    public enum Metrics {

        PEN_FLOW_BITS("Flow_bits", "pen.flow.bits"),

        PEN_FLOW_BYTES("Flow_bytes", "pen.flow.bytes"),

        PEN_FLOW_PACKETS("Flow_packets", "pen.flow.packets"),

        PEN_FLOW_INGRESS_PACKETS("Flow_ingress_packets", "pen.flow.ingress.packets"),

        PEN_FLOW_RAW_PACKETS("Flow_raw_packets", "pen.flow.raw.packets"),
        
        PEN_FLOW_RAW_BITS("Flow_raw_bits", "pen.flow.raw.bits"),
        
        PEN_FLOW_RAW_BYTES("Flow_raw_bytes", "pen.flow.raw.bytes"),

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

        PEN_SWITCH_RX_PACKETS("Switch_packets", "pen.switch.rx-packets"),

        PEN_SWITCH_STATE("Switch_state", "pen.switch.state");

        private String tag;
        
        private String displayTag;

        /**
         * Instantiates a new metrics.
         *
         * @param tag the tag
         * @param displayTag the display tag
         */
        private Metrics(final String tag, final String displayTag) {
            setTag(tag);
            setDisplayTag(displayTag);
        }

        /**
         * Sets the tag.
         *
         * @param tag the new tag
         */
        private void setTag(final String tag) {
            this.tag = tag;
        }

        /**
         * Gets the tag.
         *
         * @return the tag
         */
        public String getTag() {
            return tag;
        }

        /**
         * Sets the display tag.
         *
         * @param displayTag the new display tag
         */
        private void setDisplayTag(final String displayTag) {
            this.displayTag = displayTag;
        }

        /**
         * Gets the display tag.
         *
         * @return the display tag
         */
        public String getDisplayTag() {
            return displayTag;
        }

        /**
         * Flow value.
         *
         * @param tag the tag
         * @param uniDirectional the uni directional
         * @return the list
         */
        public static List<String> flowValue(String tag, boolean uniDirectional) {
            List<String> list = new ArrayList<String>();
            tag = "Flow_" + tag;
            for (Metrics metric : values()) {
                if (metric.getTag().equalsIgnoreCase(tag)) {
                    list.add(metric.getDisplayTag());
                    if (uniDirectional) {
                        list.add(metric.getDisplayTag());
                    }
                }
            }
            return list;
        }
        
        /**
         * Flow raw value.
         *
         * @param tag the tag
         * @return the list
         */
        public static List<String> flowRawValue(String tag) {
            List<String> list = new ArrayList<String>();
            tag = "Flow_raw_" + tag;
            for (Metrics metric : values()) {
                if (metric.getTag().equalsIgnoreCase(tag)) {
                    list.add(metric.getDisplayTag());
                }
            }
            return list;
        }
        
        /**
         * Switch value.
         *
         * @param tag the tag
         * @return the list
         */
        public static List<String> switchValue(String tag) {
            List<String> list = new ArrayList<String>();

            if (tag.equalsIgnoreCase("latency")) {
                tag = "Isl_" + tag;
            } else {
                tag = "Switch_" + tag;
            }
            for (Metrics metric : values()) {
                if (metric.getTag().equalsIgnoreCase(tag)) {
                    list.add(metric.getDisplayTag());
                }
            }
            return list;
        }

        /**
         * Gets the starts with.
         *
         * @param tag the tag
         * @return the starts with
         */
        public static List<String> getStartsWith(String tag) {
            List<String> list = new ArrayList<String>();
            for (Metrics metric : values()) {
                if (metric.getTag().startsWith(tag)) {
                    list.add(metric.getDisplayTag());
                }
            }
            return list;
        }

        /**
         * List.
         *
         * @return the list
         */
        public static List<String> list() {
            List<String> list = new ArrayList<String>();
            for (Metrics metric : values()) {
                list.add(metric.getDisplayTag());
            }
            return list;
        }

        /**
         * Tags.
         *
         * @return the sets the
         */
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
