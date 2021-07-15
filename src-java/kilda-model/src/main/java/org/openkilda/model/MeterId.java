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

package org.openkilda.model;

import static org.openkilda.model.cookie.Cookie.ARP_INGRESS_COOKIE;
import static org.openkilda.model.cookie.Cookie.ARP_INPUT_PRE_DROP_COOKIE;
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_COOKIE;
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_ONE_SWITCH_COOKIE;
import static org.openkilda.model.cookie.Cookie.ARP_POST_INGRESS_VXLAN_COOKIE;
import static org.openkilda.model.cookie.Cookie.ARP_TRANSIT_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_INGRESS_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_INPUT_PRE_DROP_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_POST_INGRESS_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_POST_INGRESS_ONE_SWITCH_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_POST_INGRESS_VXLAN_COOKIE;
import static org.openkilda.model.cookie.Cookie.LLDP_TRANSIT_COOKIE;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_BROADCAST_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_UNICAST_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_UNICAST_VXLAN_RULE_COOKIE;

import org.openkilda.model.cookie.Cookie;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.Sets;
import lombok.Value;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

@Value
public final class MeterId implements Comparable<MeterId>, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Mask is being used to get meter id for corresponding system rule.
     * E.g. for 0x8000000000000002L & METER_ID_DEFAULT_RULE_MASK we will get meter id 2.
     */
    public static final long METER_ID_DEFAULT_RULE_MASK = 0x000000000000001FL;

    /**
     * Minimum meter id value for flows.
     * This value is used to allocate meter IDs for flows. But also we need to allocate meter IDs for default rules.
     * To do it special mask 'PACKET_IN_RULES_METERS_MASK' is used. With the help of this mask we take last 5 bits of
     * default rule cookie to create meter ID. That means we have range 1..31 for default rule meter IDs.
     * MIN_FLOW_METER_ID is used to do not intersect flow meter IDs with this range.
     */
    public static final int MIN_FLOW_METER_ID = 32;

    /**
     * Maximum meter id value for flows.
     * NB: Should be the same as VLAN range at the least, could be more. The formula ensures we have a sufficient range.
     * As centecs have limit of max value equals to 2560 we set it to 2500.
     */
    public static final int MAX_FLOW_METER_ID = 2500;

    /**
     * Minimum meter id value for system rules.
     */
    public static final int MIN_SYSTEM_RULE_METER_ID = 1;

    /**
     * Maximum meter id value for system rules.
     */
    public static final int MAX_SYSTEM_RULE_METER_ID = 31;

    public static final long VERIFICATION_BROADCAST_METER_ID =
            defaultCookieToMeterId(VERIFICATION_BROADCAST_RULE_COOKIE);
    public static final long VERIFICATION_UNICAST_METER_ID = defaultCookieToMeterId(VERIFICATION_UNICAST_RULE_COOKIE);
    public static final long VERIFICATION_UNICAST_VXLAN_METER_ID =
            defaultCookieToMeterId(VERIFICATION_UNICAST_VXLAN_RULE_COOKIE);
    public static final long LLDP_INGRESS_METER_ID = defaultCookieToMeterId(LLDP_INGRESS_COOKIE);
    public static final long LLDP_INPUT_PRE_DROP_METER_ID = defaultCookieToMeterId(LLDP_INPUT_PRE_DROP_COOKIE);
    public static final long LLDP_TRANSIT_METER_ID = defaultCookieToMeterId(LLDP_TRANSIT_COOKIE);
    public static final long LLDP_POST_INGRESS_METER_ID = defaultCookieToMeterId(LLDP_POST_INGRESS_COOKIE);
    public static final long LLDP_POST_INGRESS_VXLAN_METER_ID = defaultCookieToMeterId(LLDP_POST_INGRESS_VXLAN_COOKIE);
    public static final long LLDP_POST_INGRESS_ONE_SWITCH_METER_ID =
            defaultCookieToMeterId(LLDP_POST_INGRESS_ONE_SWITCH_COOKIE);
    public static final long ARP_INGRESS_METER_ID = defaultCookieToMeterId(ARP_INGRESS_COOKIE);
    public static final long ARP_INPUT_PRE_DROP_METER_ID = defaultCookieToMeterId(ARP_INPUT_PRE_DROP_COOKIE);
    public static final long ARP_TRANSIT_METER_ID = defaultCookieToMeterId(ARP_TRANSIT_COOKIE);
    public static final long ARP_POST_INGRESS_METER_ID = defaultCookieToMeterId(ARP_POST_INGRESS_COOKIE);
    public static final long ARP_POST_INGRESS_VXLAN_METER_ID = defaultCookieToMeterId(ARP_POST_INGRESS_VXLAN_COOKIE);
    public static final long ARP_POST_INGRESS_ONE_SWITCH_METER_ID =
            defaultCookieToMeterId(ARP_POST_INGRESS_ONE_SWITCH_COOKIE);

    public static final Set<Long> DEFAULT_METERS = Collections.unmodifiableSet(Sets.newHashSet(
            VERIFICATION_BROADCAST_METER_ID,
            VERIFICATION_UNICAST_METER_ID,
            VERIFICATION_UNICAST_VXLAN_METER_ID,
            LLDP_INGRESS_METER_ID,
            LLDP_INPUT_PRE_DROP_METER_ID,
            LLDP_TRANSIT_METER_ID,
            LLDP_POST_INGRESS_METER_ID,
            LLDP_POST_INGRESS_VXLAN_METER_ID,
            LLDP_POST_INGRESS_ONE_SWITCH_METER_ID,
            ARP_INGRESS_METER_ID,
            ARP_INPUT_PRE_DROP_METER_ID,
            ARP_TRANSIT_METER_ID,
            ARP_POST_INGRESS_METER_ID,
            ARP_POST_INGRESS_VXLAN_METER_ID,
            ARP_POST_INGRESS_ONE_SWITCH_METER_ID));

    long value;

    @JsonCreator
    public MeterId(long value) {
        this.value = value;
    }

    public static boolean isMeterIdOfDefaultRule(long meterId) {
        return MIN_SYSTEM_RULE_METER_ID <= meterId && meterId <= MAX_SYSTEM_RULE_METER_ID;
    }

    public static boolean isMeterIdOfFlowRule(long meterId) {
        return MIN_FLOW_METER_ID <= meterId && meterId <= MAX_FLOW_METER_ID;
    }

    /**
     * Generates meter ID from cookie of default rule.
     */
    public static MeterId createMeterIdForDefaultRule(long cookie) {
        if (!Cookie.isDefaultRule(cookie)) {
            throw new IllegalArgumentException(String.format("Cookie '%s' is not a cookie of default rule", cookie));
        }

        return new MeterId(defaultCookieToMeterId(cookie));
    }

    public static long defaultCookieToMeterId(long cookie) {
        return cookie & METER_ID_DEFAULT_RULE_MASK;
    }

    @JsonValue
    public long getValue() {
        return value;
    }

    @Override
    public int compareTo(MeterId compareWith) {
        return Long.compare(value, compareWith.value);
    }
}
