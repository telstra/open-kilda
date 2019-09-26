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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Value;

import java.io.Serializable;

/**
 * Represents information about a cookie.
 */
@Value
public class Cookie implements Comparable<Cookie>, Serializable {
    private static final long serialVersionUID = 1L;

    public static final long DROP_RULE_COOKIE = 0x8000000000000001L;
    public static final long VERIFICATION_BROADCAST_RULE_COOKIE = 0x8000000000000002L;
    public static final long VERIFICATION_UNICAST_RULE_COOKIE = 0x8000000000000003L;
    public static final long DROP_VERIFICATION_LOOP_RULE_COOKIE = 0x8000000000000004L;
    public static final long CATCH_BFD_RULE_COOKIE = 0x8000000000000005L;
    public static final long ROUND_TRIP_LATENCY_RULE_COOKIE = 0x8000000000000006L;
    public static final long VERIFICATION_UNICAST_VXLAN_RULE_COOKIE = 0x8000000000000007L;
    public static final long DEFAULT_RULES_MASK = 0x8000000000000000L;

    public static final long FORWARD_FLOW_COOKIE_MASK = 0x4000000000000000L;
    public static final long REVERSE_FLOW_COOKIE_MASK = 0x2000000000000000L;

    public static final long FLOW_COOKIE_VALUE_MASK = 0x00000000FFFFFFFFL;

    public static final long TYPE_MASK = 0x1FF0000000000000L; // 9 bits for cookie type
    public static final long FLOW_COOKIE_TYPE = 0x0000000000000000L;
    public static final long LLDP_COOKIE_TYPE = 0x0010000000000000L;

    private final long value;

    @JsonCreator
    public Cookie(long value) {
        this.value = value;
    }

    public static Cookie buildForwardCookie(long unmaskedCookie) {
        return new Cookie(unmaskedCookie | Cookie.FORWARD_FLOW_COOKIE_MASK);
    }

    public static Cookie buildReverseCookie(long unmaskedCookie) {
        return new Cookie(unmaskedCookie | Cookie.REVERSE_FLOW_COOKIE_MASK);
    }

    /**
     * Creates masked cookie for LLDP rule.
     */
    public static Cookie buildLldpCookie(Long unmaskedCookie, boolean forward) {
        if (unmaskedCookie == null) {
            return null;
        }
        long directionMask = forward ? FORWARD_FLOW_COOKIE_MASK : REVERSE_FLOW_COOKIE_MASK;
        return new Cookie(unmaskedCookie | Cookie.LLDP_COOKIE_TYPE | directionMask);
    }

    public long getUnmaskedValue() {
        return value & FLOW_COOKIE_VALUE_MASK;
    }

    public boolean isDefaultRule() {
        return isDefaultRule(value);
    }

    public static boolean isDefaultRule(long cookie) {
        return (cookie & DEFAULT_RULES_MASK) != 0L;
    }

    @JsonValue
    public long getValue() {
        return value;
    }

    @Override
    public String toString() {
        return toString(value);
    }

    public static String toString(long cookie) {
        return String.format("0x%016X", cookie);
    }

    /**
     * Create Cookie from meter ID of default rule by using of `DEFAULT_RULES_MASK`.
     *
     * @param meterId meter ID
     * @return cookie
     * @throws IllegalArgumentException if meter ID is out of range of default meter ID range
     */
    public static Cookie createCookieForDefaultRule(long meterId) {
        if (!MeterId.isMeterIdOfDefaultRule(meterId)) {
            throw new IllegalArgumentException(
                    String.format("Meter ID '%s' is not a meter ID of default rule.", meterId));
        }

        return new Cookie(meterId | DEFAULT_RULES_MASK);
    }

    /**
     * Checks whether the cookie corresponds to the forward flow mask.
     */
    public boolean isMaskedAsForward() {
        boolean isMatch;
        if ((value & 0xE000000000000000L) != 0) {
            isMatch = (value & FORWARD_FLOW_COOKIE_MASK) != 0;
        } else {
            isMatch = (value & 0x0080000000000000L) == 0;
        }
        return isMatch;

    }

    /**
     * Checks whether the cookie corresponds to the reverse flow mask.
     */
    public boolean isMaskedAsReversed() {
        boolean isMatch;
        if ((value & 0xE000000000000000L) != 0) {
            isMatch = (value & REVERSE_FLOW_COOKIE_MASK) != 0;
        } else {
            isMatch = (value & 0x0080000000000000L) != 0;
        }
        return isMatch;
    }

    /**
     * Checks whether the cookie corresponds to the LLDP flow.
     */
    public static boolean isMaskedAsLldp(long value) {
        return (TYPE_MASK & value) == LLDP_COOKIE_TYPE;
    }

    /**
     * Checks whether the cookie is main flow cookie.
     */
    public static boolean isMaskedAsFlowCookie(long value) {
        return (TYPE_MASK & value) == FLOW_COOKIE_TYPE;
    }

    @Override
    public int compareTo(Cookie compareWith) {
        return Long.compare(value, compareWith.value);
    }
}
