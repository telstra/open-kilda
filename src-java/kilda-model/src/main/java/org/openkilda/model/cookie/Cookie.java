/* Copyright 2019 Telstra Open Source
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

package org.openkilda.model.cookie;

import org.openkilda.model.MeterId;
import org.openkilda.model.cookie.ServiceCookie.ServiceCookieTag;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Builder;

/**
 * Old cookie representation for kilda.
 */
public class Cookie extends CookieBase implements Comparable<Cookie> {
    // FIXME(surabujin): get rid from this constants (it will allow to merge CookieBase into Cookie)
    public static final long DROP_RULE_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.DROP_RULE_COOKIE).getValue();
    public static final long VERIFICATION_BROADCAST_RULE_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.VERIFICATION_BROADCAST_RULE_COOKIE).getValue();
    public static final long VERIFICATION_UNICAST_RULE_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.VERIFICATION_UNICAST_RULE_COOKIE).getValue();
    public static final long DROP_VERIFICATION_LOOP_RULE_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.DROP_VERIFICATION_LOOP_RULE_COOKIE).getValue();
    public static final long CATCH_BFD_RULE_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.CATCH_BFD_RULE_COOKIE).getValue();
    public static final long ROUND_TRIP_LATENCY_RULE_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.ROUND_TRIP_LATENCY_RULE_COOKIE).getValue();
    public static final long VERIFICATION_UNICAST_VXLAN_RULE_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.VERIFICATION_UNICAST_VXLAN_RULE_COOKIE).getValue();
    public static final long MULTITABLE_PRE_INGRESS_PASS_THROUGH_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.MULTITABLE_PRE_INGRESS_PASS_THROUGH_COOKIE).getValue();
    public static final long MULTITABLE_INGRESS_DROP_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.MULTITABLE_INGRESS_DROP_COOKIE).getValue();
    public static final long MULTITABLE_POST_INGRESS_DROP_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.MULTITABLE_POST_INGRESS_DROP_COOKIE).getValue();
    public static final long MULTITABLE_EGRESS_PASS_THROUGH_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.MULTITABLE_EGRESS_PASS_THROUGH_COOKIE).getValue();
    public static final long MULTITABLE_TRANSIT_DROP_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.MULTITABLE_TRANSIT_DROP_COOKIE).getValue();
    public static final long LLDP_INPUT_PRE_DROP_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.LLDP_INPUT_PRE_DROP_COOKIE).getValue();
    public static final long LLDP_TRANSIT_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.LLDP_TRANSIT_COOKIE).getValue();
    public static final long LLDP_INGRESS_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.LLDP_INGRESS_COOKIE).getValue();
    public static final long LLDP_POST_INGRESS_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.LLDP_POST_INGRESS_COOKIE).getValue();
    public static final long LLDP_POST_INGRESS_VXLAN_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.LLDP_POST_INGRESS_VXLAN_COOKIE).getValue();
    public static final long LLDP_POST_INGRESS_ONE_SWITCH_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.LLDP_POST_INGRESS_ONE_SWITCH_COOKIE).getValue();
    public static final long ARP_INPUT_PRE_DROP_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.ARP_INPUT_PRE_DROP_COOKIE).getValue();
    public static final long ARP_TRANSIT_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.ARP_TRANSIT_COOKIE).getValue();
    public static final long ARP_INGRESS_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.ARP_INGRESS_COOKIE).getValue();
    public static final long ARP_POST_INGRESS_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.ARP_POST_INGRESS_COOKIE).getValue();
    public static final long ARP_POST_INGRESS_VXLAN_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.ARP_POST_INGRESS_VXLAN_COOKIE).getValue();
    public static final long ARP_POST_INGRESS_ONE_SWITCH_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.ARP_POST_INGRESS_ONE_SWITCH_COOKIE).getValue();
    public static final long SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE).getValue();
    public static final long SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE).getValue();
    public static final long SERVER_42_FLOW_RTT_TURNING_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.SERVER_42_FLOW_RTT_TURNING_COOKIE).getValue();
    public static final long SERVER_42_ISL_RTT_OUTPUT_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.SERVER_42_ISL_RTT_OUTPUT_COOKIE).getValue();
    public static final long SERVER_42_ISL_RTT_TURNING_COOKIE = new ServiceCookie(
            ServiceCookie.ServiceCookieTag.SERVER_42_ISL_RTT_TURNING_COOKIE).getValue();
    public static final long SERVER_42_FLOW_RTT_VXLAN_TURNING_COOKIE = new ServiceCookie(
            ServiceCookieTag.SERVER_42_FLOW_RTT_VXLAN_TURNING_COOKIE).getValue();
    public static final long DROP_SLOW_PROTOCOLS_LOOP_COOKIE = new ServiceCookie(
            ServiceCookieTag.DROP_SLOW_PROTOCOLS_LOOP_COOKIE).getValue();
    public static final long SKIP_EGRESS_FLOW_PING_COOKIE = new ServiceCookie(
            ServiceCookieTag.SKIP_EGRESS_FLOW_PING_COOKIE).getValue();

    @JsonCreator
    public Cookie(long value) {
        super(value);
    }

    @Builder
    public Cookie(CookieType type) {
        super(0, type);
    }

    /**
     * Convert existing {@link Cookie} instance into {@link CookieBuilder}.
     */
    public CookieBuilder toBuilder() {
        return new CookieBuilder()
                .type(getType());
    }

    protected Cookie(long value, CookieType type) {
        super(value, type);
    }

    @Override
    public int compareTo(Cookie other) {
        return cookieComparison(other);
    }

    /**
     * Convert port number into isl-VLAN-egress "cookie".
     */
    @Deprecated
    public static long encodeIslVlanEgress(int port) {
        // FIXME(surabujin): replace with direct cookie call
        return new PortColourCookie(CookieType.MULTI_TABLE_ISL_VLAN_EGRESS_RULES, port).getValue();
    }

    /**
     * Convert port number into isl-VxLAN-egress "cookie".
     */
    @Deprecated
    public static long encodeIslVxlanEgress(int port) {
        // FIXME(surabujin): replace with direct cookie call
        return new PortColourCookie(CookieType.MULTI_TABLE_ISL_VXLAN_EGRESS_RULES, port).getValue();
    }

    /**
     * Convert port number into isl-VxLAN-transit "cookie".
     */
    @Deprecated
    public static long encodeIslVxlanTransit(int port) {
        // FIXME(surabujin): replace with direct cookie call
        return new PortColourCookie(CookieType.MULTI_TABLE_ISL_VXLAN_TRANSIT_RULES, port).getValue();
    }

    /**
     * Convert port number into ingress-rule-pass-through "cookie".
     */
    @Deprecated
    public static long encodeIngressRulePassThrough(int port) {
        // FIXME(surabujin): replace with direct cookie call
        return new PortColourCookie(CookieType.MULTI_TABLE_INGRESS_RULES, port).getValue();
    }

    /**
     * Creates masked cookie for LLDP rule.
     */
    @Deprecated
    public static long encodeLldpInputCustomer(int port) {
        // FIXME(surabujin): replace with direct cookie call
        return new PortColourCookie(CookieType.LLDP_INPUT_CUSTOMER_TYPE, port).getValue();
    }

    @Deprecated
    public static long encodeArpInputCustomer(int port) {
        // FIXME(surabujin): replace with direct cookie call
        return new PortColourCookie(CookieType.ARP_INPUT_CUSTOMER_TYPE, port).getValue();
    }

    public static long encodeServer42FlowRttInput(int port) {
        return new PortColourCookie(CookieType.SERVER_42_FLOW_RTT_INPUT, port).getValue();
    }

    public static long encodeServer42IslRttInput(int port) {
        return new PortColourCookie(CookieType.SERVER_42_ISL_RTT_INPUT, port).getValue();
    }

    /**
     * Create Cookie from meter ID of default rule by using of `DEFAULT_RULES_FLAG`.
     *
     * @param meterId meter ID
     * @return cookie
     * @throws IllegalArgumentException if meter ID is out of range of default meter ID range
     */
    @Deprecated
    public static CookieBase createCookieForDefaultRule(long meterId) {
        // FIXME(surabujin): replace with direct schema call
        return new ServiceCookie(new MeterId(meterId));
    }

    @Deprecated
    public static boolean isDefaultRule(long cookie) {
        // FIXME(surabujin): replace with direct cookie call
        return new Cookie(cookie).getServiceFlag();
    }

    /**
     * Check is cookie have type MULTI_TABLE_INGRESS_RULES.
     *
     * <p>Deprecated {@code ServiceCookieSchema.getType()} must be used instead of this method.
     */
    @Deprecated
    public static boolean isIngressRulePassThrough(long raw) {
        // FIXME(surabujin): replace with direct cookie call
        return new Cookie(raw).getType() == CookieType.MULTI_TABLE_INGRESS_RULES;
    }
}
