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

import org.openkilda.model.IPv4Address;

public final class Constants {
    public static final int VXLAN_UDP_SRC = 4500;
    public static final int VXLAN_UDP_DST = 4789;
    public static final int SERVER_42_FLOW_RTT_FORWARD_UDP_PORT = 4700;
    public static final int SERVER_42_FLOW_RTT_REVERSE_UDP_PORT = 4701;
    public static final int SERVER_42_FLOW_RTT_REVERSE_UDP_VXLAN_PORT = 4702;
    public static final int SERVER_42_ISL_RTT_FORWARD_UDP_PORT = 4710;
    public static final int SERVER_42_ISL_RTT_REVERSE_UDP_PORT = 4711;
    public static final IPv4Address VXLAN_SRC_IPV4_ADDRESS = new IPv4Address("127.0.0.1");
    public static final IPv4Address VXLAN_DST_IPV4_ADDRESS = new IPv4Address("127.0.0.2");
    public static final int NOVIFLOW_TIMESTAMP_SIZE_IN_BITS = 64;
    public static final int STUB_VXLAN_UDP_SRC = 4500;
    public static final int ARP_VXLAN_UDP_SRC = 4501;

    public static final int DISCOVERY_PACKET_UDP_PORT = 61231;
    public static final int LATENCY_PACKET_UDP_PORT = 61232;

    public static final int BDF_DEFAULT_PORT = 3784;

    public static final int MAC_ADDRESS_SIZE_IN_BITS = 48;
    public static final int ETHERNET_HEADER_SIZE = 112; // 48 dst mac, 48 src mac, 16 ether type
    public static final int IP_V4_HEADER_SIZE = 160; /*
     * 4 version, 4 IHL, 8 Type of service, 16 length, 16 ID,
     * 4 flags, 12 Fragment Offset, 8 TTL, 8 Protocol,
     * 16 checksum, 32 src IP, 32 dst IP
     */
    public static final int UDP_HEADER_SIZE = 64;                    // 16 src port, 16 dst port, 16 length, 16 checksum
    public static final int LLDP_TLV_CHASSIS_ID_TOTAL_SIZE = 72;     // 7 type, 9 length, 56 chassisId
    public static final int LLDP_TLV_PORT_ID_TOTAL_SIZE = 40;        // 7 type, 9 length, 24 port
    public static final int LLDP_TLV_TTL_TOTAL_SIZE = 32;            // 7 type, 9 length, 16 port
    public static final int ROUND_TRIP_LATENCY_TIMESTAMP_SIZE = 64;  // 24 bits OUI, 8 bits optional type
    public static final int LLDP_TLV_HEADER_SIZE = 16;               // 7 type, 9 length
    public static final int LLDP_TLV_OPTIONAL_HEADER_SIZE_IN_BYTES = 4; // 24 bits OUI, 8 bits optional type

    public static final int ROUND_TRIP_LATENCY_T0_OFFSET = ETHERNET_HEADER_SIZE
            + IP_V4_HEADER_SIZE
            + UDP_HEADER_SIZE
            + LLDP_TLV_CHASSIS_ID_TOTAL_SIZE
            + LLDP_TLV_PORT_ID_TOTAL_SIZE
            + LLDP_TLV_TTL_TOTAL_SIZE
            + LLDP_TLV_HEADER_SIZE
            + (LLDP_TLV_OPTIONAL_HEADER_SIZE_IN_BYTES * 8);

    public static final int ROUND_TRIP_LATENCY_T1_OFFSET = ROUND_TRIP_LATENCY_T0_OFFSET
            + ROUND_TRIP_LATENCY_TIMESTAMP_SIZE
            + LLDP_TLV_HEADER_SIZE
            + (LLDP_TLV_OPTIONAL_HEADER_SIZE_IN_BYTES * 8);

    public static final class Priority {
        public static final int MINIMAL_POSITIVE_PRIORITY = 1;
        public static final int FLOW_PRIORITY = 24576;
        public static final int DISCOVERY_RULE_PRIORITY = 31768;


        public static final int VERIFICATION_RULE_VXLAN_PRIORITY = DISCOVERY_RULE_PRIORITY + 1;
        public static final int DROP_DISCOVERY_LOOP_RULE_PRIORITY = DISCOVERY_RULE_PRIORITY + 1;
        public static final int CATCH_BFD_RULE_PRIORITY = DROP_DISCOVERY_LOOP_RULE_PRIORITY + 1;
        public static final int ROUND_TRIP_LATENCY_RULE_PRIORITY = DROP_DISCOVERY_LOOP_RULE_PRIORITY + 1;
        public static final int ISL_EGRESS_VXLAN_RULE_PRIORITY_MULTITABLE = FLOW_PRIORITY - 2;
        public static final int ISL_TRANSIT_VXLAN_RULE_PRIORITY_MULTITABLE = FLOW_PRIORITY - 3;
        public static final int INGRESS_CUSTOMER_PORT_RULE_PRIORITY_MULTITABLE = FLOW_PRIORITY - 2;
        public static final int ISL_EGRESS_VLAN_RULE_PRIORITY_MULTITABLE = FLOW_PRIORITY - 5;
        public static final int DEFAULT_FLOW_PRIORITY = FLOW_PRIORITY - 1;
        public static final int DEFAULT_FLOW_VLAN_STATS_PRIORITY = FLOW_PRIORITY - 10;
        public static final int DOUBLE_VLAN_FLOW_PRIORITY = FLOW_PRIORITY + 10;

        public static final int MIRROR_FLOW_PRIORITY = FLOW_PRIORITY + 50;
        public static final int MIRROR_DOUBLE_VLAN_FLOW_PRIORITY = MIRROR_FLOW_PRIORITY + 10;
        //todo change -1 to -10
        public static final int MIRROR_DEFAULT_FLOW_PRIORITY = MIRROR_FLOW_PRIORITY - 1;

        public static final int LOOP_FLOW_PRIORITY = FLOW_PRIORITY + 100;
        public static final int LOOP_DOUBLE_VLAN_FLOW_PRIORITY = LOOP_FLOW_PRIORITY + 10;
        public static final int LOOP_DEFAULT_FLOW_PRIORITY = LOOP_FLOW_PRIORITY - 10;

        public static final int Y_FLOW_PRIORITY = FLOW_PRIORITY + 50;
        public static final int Y_FLOW_DOUBLE_VLAN_PRIORITY = Y_FLOW_PRIORITY + 10;
        public static final int Y_DEFAULT_FLOW_PRIORITY = Y_FLOW_PRIORITY - 10;

        public static final int SERVER_42_FLOW_RTT_INPUT_PRIORITY = INGRESS_CUSTOMER_PORT_RULE_PRIORITY_MULTITABLE;
        public static final int SERVER_42_FLOW_RTT_TURNING_PRIORITY = DISCOVERY_RULE_PRIORITY;
        public static final int SERVER_42_FLOW_RTT_VXLAN_TURNING_PRIORITY = VERIFICATION_RULE_VXLAN_PRIORITY + 1;
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

        public static final int SERVER_42_INGRESS_SINGLE_VLAN_FLOW_PRIORITY = FLOW_PRIORITY;
        public static final int SERVER_42_INGRESS_DOUBLE_VLAN_FLOW_PRIORITY = FLOW_PRIORITY + 10;
        public static final int SERVER_42_INGRESS_DEFAULT_FLOW_PRIORITY = FLOW_PRIORITY - 10;
        public static final int SERVER_42_PRE_INGRESS_FLOW_PRIORITY = FLOW_PRIORITY;
    }

    private Constants() {
    }
}
