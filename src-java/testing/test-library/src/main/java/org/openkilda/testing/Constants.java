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

package org.openkilda.testing;

import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;

import java.util.UUID;

public final class Constants {
    public static final Integer DEFAULT_COST = 700;
    public static final Integer WAIT_OFFSET = 15;
    public static final Integer PROTECTED_PATH_INSTALLATION_TIME = 20;
    public static final Integer PATH_INSTALLATION_TIME = 15;
    public static final Integer FLOW_CRUD_TIMEOUT = 30;
    public static final Integer TOPOLOGY_DISCOVERING_TIME = 120;
    public static final Integer SWITCHES_ACTIVATION_TIME = 10;
    public static final Integer RULES_DELETION_TIME = 10;
    public static final Integer RULES_INSTALLATION_TIME = 25;
    public static final Integer STATS_LOGGING_TIMEOUT = 10;
    public static final Integer FL_DUMP_INTERVAL = 60; //floodlight.dump.interval defaults to 60
    public static final Integer STATS_FROM_SERVER42_LOGGING_TIMEOUT = 60;
    public static final String NON_EXISTENT_FLOW_ID = "non-existent-" + UUID.randomUUID().toString();
    public static final Integer SINGLE_TABLE_ID = 0;
    public static final Integer INGRESS_RULE_MULTI_TABLE_ID = 2;
    public static final Integer EGRESS_RULE_MULTI_TABLE_ID = 4;
    public static final Integer TRANSIT_RULE_MULTI_TABLE_ID = 5;
    public static final Integer SHARED_RULE_TABLE_ID = 1;
    public static final String DUMMY_SW_IP_1 = "192.0.2.1";
    public static final SwitchId NON_EXISTENT_SWITCH_ID = new SwitchId("de:ad:be:ef:de:ad:be:ef");

    private Constants() {
        throw new UnsupportedOperationException();
    }

    public enum DefaultRule {
        DROP_RULE(Cookie.DROP_RULE_COOKIE), //drop all unknown packets
        VERIFICATION_BROADCAST_RULE(Cookie.VERIFICATION_BROADCAST_RULE_COOKIE), //ISL discovery packets
        VERIFICATION_UNICAST_RULE(Cookie.VERIFICATION_UNICAST_RULE_COOKIE), //catch rule for flow pings
        VERIFICATION_UNICAST_VXLAN_RULE_COOKIE(Cookie.VERIFICATION_UNICAST_VXLAN_RULE_COOKIE),
        DROP_LOOP_RULE(Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE), //drop packets that'll lead to self-loop ISLs
        CATCH_BFD_RULE(Cookie.CATCH_BFD_RULE_COOKIE); //catch rule for BFD sessions (noviflow-specific)

        private final long cookie;

        DefaultRule(long cookie) {
            this.cookie = cookie;
        }

        public long getCookie() {
            return cookie;
        }

        public String toHexString() {
            return Cookie.toString(cookie);
        }
    }
}
