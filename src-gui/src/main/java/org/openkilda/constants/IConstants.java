/* Copyright 2023 Telstra Open Source
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

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Interface IConstants.
 *
 * @author Gaurav Chugh
 */

public abstract class IConstants {

    private static final Logger LOGGER = LoggerFactory.getLogger(IConstants.class);

    public static final String SESSION_OBJECT = "sessionObject";

    private IConstants() {

    }

    public static final class SessionTimeout {
        private SessionTimeout() {

        }

        public static Integer TIME_IN_MINUTE;

        public static Integer DEFAULT_TIME_IN_MINUTE = 45;
    }

    public static final class InvalidLoginAttempt {
        private InvalidLoginAttempt() {

        }

        public static Integer INVALID_LOGIN_ATTEMPTS_COUNT;

        public static Integer DEFAULT_INVALID_LOGIN_ATTEMPTS_COUNT = 5;
    }

    public static final class UserAccUnlockTime {
        private UserAccUnlockTime() {

        }

        public static Integer USER_ACCOUNT_UNLOCK_TIME;

        public static Integer DEFAULT_USER_ACCOUNT_UNLOCK_TIME = 60;
    }


    @Getter
    public enum ApplicationSetting {

        SESSION_TIMEOUT(String.valueOf(SessionTimeout.DEFAULT_TIME_IN_MINUTE)), SWITCH_NAME_STORAGE_TYPE(
                StorageType.FILE_STORAGE.name()), INVALID_LOGIN_ATTEMPT(String.valueOf(InvalidLoginAttempt
                .DEFAULT_INVALID_LOGIN_ATTEMPTS_COUNT)), USER_ACCOUNT_UNLOCK_TIME(
                String.valueOf(UserAccUnlockTime.DEFAULT_USER_ACCOUNT_UNLOCK_TIME));

        final String value;

        ApplicationSetting(final String value) {
            this.value = value;
        }

    }

    public enum StorageType {

        DATABASE_STORAGE, FILE_STORAGE;

        public static StorageType get(final String name) {
            return name.equalsIgnoreCase(DATABASE_STORAGE.name()) ? DATABASE_STORAGE : FILE_STORAGE;
        }
    }

    public static StorageType STORAGE_TYPE_FOR_SWITCH_NAME = null;

    public static final class Role {

        private Role() {

        }

    }

    public enum ProviderType {

        FILE, URL

    }

    public static final class Status {

        private Status() {

        }

        public static final String UP = "UP";
        public static final String DOWN = "DOWN";
    }

    public static final class NorthBoundUrl {

        private NorthBoundUrl() {

        }

        public static final String VERSION_ONE = "/v1";
        public static final String VERSION_TWO = "/v2";
        public static final String GET_FLOW = VERSION_ONE + "/flows";
        public static final String GET_FLOW_V2 = VERSION_TWO + "/flows";
        public static final String GET_FLOW_STATUS = GET_FLOW_V2 + "/status/";
        public static final String GET_FLOW_REROUTE = GET_FLOW_V2 + "/{flow_id}/reroute";
        public static final String GET_FLOW_VALIDATE = GET_FLOW + "/{flow_id}/validate";
        public static final String GET_PATH_FLOW = GET_FLOW + "/path";
        public static final String GET_SWITCHES = VERSION_ONE + "/switches";
        public static final String GET_SWITCH = GET_SWITCHES + "/{switch_id}";
        public static final String GET_SWITCH_RULES = GET_SWITCHES + "/{switch_id}/rules";
        public static final String GET_LINKS = VERSION_ONE + "/links";
        public static final String GET_LINK_PROPS = VERSION_ONE + "/link/props";
        public static final String UPDATE_FLOW = GET_FLOW_V2 + "/{flow_id}";
        public static final String GET_FLOW_PATH = GET_FLOW + "/{flow_id}/path";
        public static final String RESYNC_FLOW = GET_FLOW + "/{flow_id}/sync";
        public static final String CONFIG_SWITCH_PORT = GET_SWITCHES + "/{switch_id}/port/{port_no}/config";
        public static final String GET_ISL_FLOW = GET_LINKS
                + "/flows?src_switch={src_switch}&src_port={src_port}&dst_switch={dst_switch}&dst_port={dst_port}";
        public static final String GET_SWITCH_METERS = GET_SWITCHES + "/{switch_id}/meters";
        public static final String FLOW_PING = GET_FLOW + "/{flow_id}/ping";
        public static final String UPDATE_SWITCH_UNDER_MAINTENANCE = GET_SWITCHES + "/{switch_id}/under-maintenance";
        public static final String GET_SWITCH_FLOWS = GET_SWITCHES + "/{switch_id}/flows";
        public static final String GET_SWITCH_FLOWS_BY_PORTS = VERSION_TWO + "/switches/{switch_id}/flows-by-port";
        public static final String GET_SWITCH_PORT_FLOWS = GET_SWITCHES + "/{switch_id}/flows?port={port}";
        public static final String UPDATE_LINK_UNDER_MAINTENANCE = GET_LINKS + "/under-maintenance";
        public static final String UPDATE_LINK_MAINTENANCE = GET_LINKS + "/under-maintenance";
        public static final String DELETE_LINK = GET_LINKS;
        public static final String UPDATE_LINK_BANDWIDTH = GET_LINKS
                + "/bandwidth?src_switch={src_switch}&src_port={src_port}&"
                + "dst_switch={dst_switch}&dst_port={dst_port}";
        public static final String GET_NETWORK_PATH = VERSION_ONE + "/network/paths?src_switch={src_switch}"
                + "&dst_switch={dst_switch}&path_computation_strategy={strategy}&max_latency={max_latency}";
        public static final String DELETE_SWITCH = GET_SWITCHES + "/{switch_id}?force={force}";
        public static final String UPDATE_LINK_BFD_FLAG = GET_LINKS + "/enable-bfd";
        public static final String GET_FLOW_HISTORY = GET_FLOW + "/{flow_id}/history";
        public static final String GET_FLOW_CONNECTED_DEVICE = GET_FLOW + "/{flow_id}/devices";
        public static final String UPDATE_SWITCH_PORT_PROPERTY = VERSION_TWO
                + "/switches/{switch_id}/ports/{port}/properties";
        public static final String GET_SWITCH_PORT_PROPERTY = VERSION_TWO + "/switches/{switch_id}"
                + "/ports/{port}/properties";
        public static final String UPDATE_SWITCH_LOCATION = VERSION_TWO + "/switches/{switch_id}";
        public static final String GET_LINK_BFD_PROPERTIES = VERSION_TWO
                + "/links/{src-switch}_{src-port}/{dst-switch}_{dst-port}/bfd";
    }

    public static final class OpenTsDbUrl {

        private OpenTsDbUrl() {

        }

        public static final String OPEN_TSDB_QUERY = "/api/query/";
    }


    public static final class VictoriaMetricsUrl {

        private VictoriaMetricsUrl() {

        }

        public static final String VICTORIA_RANGE_QUERY = "/api/v1/query_range";
    }

    public static final class Permission {

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

        public static final String FW_FLOW_PING = "fw_flow_ping";

        public static final String SW_PORT_CONFIG = "sw_port_config";

        public static final String STORE_SETTING = "store_setting";

        public static final String APPLICATION_SETTING = "application_setting";

        public static final String SW_SWITCH_UPDATE_NAME = "sw_switch_update_name";

        public static final String SW_SWITCH_MAINTENANCE = "sw_switch_maintenance";

        public static final String ISL_UPDATE_MAINTENANCE = "isl_update_maintenance";

        public static final String ISL_DELETE_LINK = "isl_delete_link";

        public static final String ISL_UPDATE_BANDWIDTH = "isl_update_bandwidth";

        public static final String FW_FLOW_INVENTORY = "fw_flow_inventory";

        public static final String FW_FLOW_CONTRACT = "fw_flow_contract";

        public static final String SW_SWITCH_INVENTORY = "sw_switch_inventory";

        public static final String SW_SWITCH_DELETE = "sw_switch_delete";

        public static final String MENU_AVAILABLE_PATH = "menu_available_path";

        public static final String ISL_UPDATE_BFD_FLAG = "isl_update_bfd_flag";

        public static final String FW_FLOW_HISTORY = "fw_flow_history";

        public static final String SW_UPDATE_PORT_PROPERTIES = "sw_update_port_properties";

        public static final String SW_SWITCH_METERS = "sw_switch_meters";

        public static final String UM_USER_ACCOUNT_UNLOCK = "um_user_account_unlock";

        public static final String SAML_SETTING = "saml_setting";

        public static final String SW_SWITCH_LOCATION_UPDATE = "sw_switch_location_update";

        public static final String TOPOLOGY_WORLD_MAP_VIEW = "topology_world_map_view";

        public static final String ISL_UPDATE_BFD_PROPERTIES = "isl_update_bfd_properties";

        public static final String ISL_DELETE_BFD = "isl_delete_bfd";
    }

    public static final class Settings {

        private Settings() {

        }

        public static final String TOPOLOGY_SETTING = "topology_setting";
    }

    public interface SamlUrl {

        String SAML = "/saml/";
        String SAML_METADATA = SAML + "metadata";
        String SAML_LOGIN = SAML + "login";
        String SAML_SSO = SAML + "SSO";
        String SAML_LOGOUT = SAML + "logout";
        String SAML_AUTHENTICATE = SAML + "authenticate";

    }


    public static final class View {

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
}
