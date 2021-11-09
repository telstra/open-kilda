/* Copyright 2021 Telstra Open Source
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

package org.openkilda.rulemanager;

public final class Constants {
    public static final int VXLAN_UDP_DST = 4789;


    public static final class Priority {
        public static final int MINIMAL_POSITIVE_PRIORITY = 1;
        public static final int FLOW_PRIORITY = 24576;
        public static final int DISCOVERY_RULE_PRIORITY = 31768;


        public static final int VERIFICATION_RULE_VXLAN_PRIORITY = DISCOVERY_RULE_PRIORITY + 1;
        public static final int DROP_VERIFICATION_LOOP_RULE_PRIORITY = DISCOVERY_RULE_PRIORITY + 1;
        public static final int CATCH_BFD_RULE_PRIORITY = DROP_VERIFICATION_LOOP_RULE_PRIORITY + 1;
        public static final int ROUND_TRIP_LATENCY_RULE_PRIORITY = DROP_VERIFICATION_LOOP_RULE_PRIORITY + 1;
        public static final int FLOW_LOOP_PRIORITY = FLOW_PRIORITY + 100;
        public static final int MIRROR_FLOW_PRIORITY = FLOW_PRIORITY + 50;
        public static final int ISL_EGRESS_VXLAN_RULE_PRIORITY_MULTITABLE = FLOW_PRIORITY - 2;
        public static final int ISL_TRANSIT_VXLAN_RULE_PRIORITY_MULTITABLE = FLOW_PRIORITY - 3;
        public static final int INGRESS_CUSTOMER_PORT_RULE_PRIORITY_MULTITABLE = FLOW_PRIORITY - 2;
        public static final int ISL_EGRESS_VLAN_RULE_PRIORITY_MULTITABLE = FLOW_PRIORITY - 5;
        public static final int DEFAULT_FLOW_PRIORITY = FLOW_PRIORITY - 1;

        public static final int SERVER_42_FLOW_RTT_INPUT_PRIORITY = INGRESS_CUSTOMER_PORT_RULE_PRIORITY_MULTITABLE;
        public static final int SERVER_42_FLOW_RTT_TURNING_PRIORITY = DISCOVERY_RULE_PRIORITY;
        public static final int SERVER_42_FLOW_RTT_OUTPUT_VLAN_PRIORITY = DISCOVERY_RULE_PRIORITY;
        public static final int SERVER_42_FLOW_RTT_OUTPUT_VXLAN_PRIORITY = DISCOVERY_RULE_PRIORITY;

        public static final int SERVER_42_ISL_RTT_INPUT_PRIORITY = DISCOVERY_RULE_PRIORITY;
        public static final int SERVER_42_ISL_RTT_TURNING_PRIORITY = DISCOVERY_RULE_PRIORITY;
        public static final int SERVER_42_ISL_RTT_OUTPUT_PRIORITY = DISCOVERY_RULE_PRIORITY;

        public static final int LLDP_INPUT_PRE_DROP_PRIORITY = MINIMAL_POSITIVE_PRIORITY + 1;
        public static final int LLDP_TRANSIT_ISL_PRIORITY = FLOW_PRIORITY - 1;
        public static final int LLDP_INPUT_CUSTOMER_PRIORITY = FLOW_PRIORITY - 1;
        public static final int LLDP_INGRESS_PRIORITY = MINIMAL_POSITIVE_PRIORITY + 1;
        public static final int LLDP_POST_INGRESS_PRIORITY = FLOW_PRIORITY - 2;
        public static final int LLDP_POST_INGRESS_VXLAN_PRIORITY = FLOW_PRIORITY - 1;
        public static final int LLDP_POST_INGRESS_ONE_SWITCH_PRIORITY = FLOW_PRIORITY;

        public static final int ARP_INPUT_PRE_DROP_PRIORITY = MINIMAL_POSITIVE_PRIORITY + 1;
        public static final int ARP_TRANSIT_ISL_PRIORITY = FLOW_PRIORITY - 1;
        public static final int ARP_INPUT_CUSTOMER_PRIORITY = FLOW_PRIORITY - 1;
        public static final int ARP_INGRESS_PRIORITY = MINIMAL_POSITIVE_PRIORITY + 1;
        public static final int ARP_POST_INGRESS_PRIORITY = FLOW_PRIORITY - 2;
        public static final int ARP_POST_INGRESS_VXLAN_PRIORITY = FLOW_PRIORITY - 1;
        public static final int ARP_POST_INGRESS_ONE_SWITCH_PRIORITY = FLOW_PRIORITY;

        public static final int SERVER_42_INGRESS_DEFAULT_FLOW_PRIORITY_OFFSET = -10;
        public static final int SERVER_42_INGRESS_DOUBLE_VLAN_FLOW_PRIORITY_OFFSET = 10;
        public static final int SERVER_42_INGRESS_DEFAULT_FLOW_PRIORITY = FLOW_PRIORITY
                + SERVER_42_INGRESS_DEFAULT_FLOW_PRIORITY_OFFSET;
    }

    private Constants() {
    }
}
