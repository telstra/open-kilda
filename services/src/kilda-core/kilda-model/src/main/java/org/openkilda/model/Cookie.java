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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents information about a cookie.
 */
@Value
public class Cookie implements Comparable<Cookie>, Serializable {
    private static final long serialVersionUID = 1L;

    public static final long DEFAULT_RULE_FLAG                   = 0x8000_0000_0000_0000L;
    public static final long FLOW_PATH_FORWARD_FLAG              = 0x4000_0000_0000_0000L;
    public static final long FLOW_PATH_REVERSE_FLAG              = 0x2000_0000_0000_0000L;

    // There is no alive system that use this deprecated direction flags so it should be save to drop it.
    @Deprecated
    public static final long DEPRECATED_FLOW_PATH_DIRECTION_FLAG = 0x0080_0000_0000_0000L;

    public static final long FLOW_COOKIE_VALUE_MASK              = 0x0000_0000_FFFF_FFFFL;

    public static final long DROP_RULE_COOKIE                    = 0x01L | DEFAULT_RULE_FLAG;
    public static final long VERIFICATION_BROADCAST_RULE_COOKIE  = 0x02L | DEFAULT_RULE_FLAG;
    public static final long VERIFICATION_UNICAST_RULE_COOKIE    = 0x03L | DEFAULT_RULE_FLAG;
    public static final long DROP_VERIFICATION_LOOP_RULE_COOKIE  = 0x04L | DEFAULT_RULE_FLAG;
    public static final long CATCH_BFD_RULE_COOKIE               = 0x05L | DEFAULT_RULE_FLAG;
    public static final long ROUND_TRIP_LATENCY_RULE_COOKIE      = 0x06L | DEFAULT_RULE_FLAG;
    public static final long VERIFICATION_UNICAST_VXLAN_RULE_COOKIE = 0x07L | DEFAULT_RULE_FLAG;

    // 9 bits cookie type "field"
    public static final long TYPE_MASK                           = 0x1FF0_0000_0000_0000L;
    public static final long FLOW_COOKIE_TYPE                    = 0x0000_0000_0000_0000L;
    public static final long LLDP_COOKIE_TYPE                    = 0x0010_0000_0000_0000L;

    private final long value;

    /**
     * Create {@code Cookie} instance and perform it's validation.
     */
    public static Cookie decode(long rawValue) {
        Cookie cookie = new Cookie(rawValue);
        cookie.ensureNoFlagsConflicts();
        return cookie;
    }

    @JsonCreator
    public Cookie(long value) {
        this.value = value;
    }

    public static Cookie buildForwardCookie(long unmaskedCookie) {
        return new Cookie(unmaskedCookie | Cookie.FLOW_PATH_FORWARD_FLAG);
    }

    public static Cookie buildReverseCookie(long unmaskedCookie) {
        return new Cookie(unmaskedCookie | Cookie.FLOW_PATH_REVERSE_FLAG);
    }

    /**
     * Creates masked cookie for LLDP rule.
     */
    public static Cookie buildLldpCookie(Long unmaskedCookie, boolean forward) {
        if (unmaskedCookie == null) {
            return null;
        }
        long directionMask = forward ? FLOW_PATH_FORWARD_FLAG : FLOW_PATH_REVERSE_FLAG;
        return new Cookie(unmaskedCookie | Cookie.LLDP_COOKIE_TYPE | directionMask);
    }

    /**
     * Create Cookie from meter ID of default rule by using of `DEFAULT_RULES_FLAG`.
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

        return new Cookie(meterId | DEFAULT_RULE_FLAG);
    }

    public boolean isDefaultRule() {
        return isDefaultRule(value);
    }

    public static boolean isDefaultRule(long cookie) {
        return (cookie & DEFAULT_RULE_FLAG) != 0L;
    }

    /**
     * Checks whether the cookie corresponds to the forward flow mask.
     */
    public boolean isMaskedAsForward() {
        boolean isMatch;
        if ((value & 0xE000000000000000L) != 0) {
            isMatch = (value & FLOW_PATH_FORWARD_FLAG) != 0;
        } else {
            isMatch = (value & DEPRECATED_FLOW_PATH_DIRECTION_FLAG) == 0;
        }
        return isMatch;
    }

    /**
     * Checks whether the cookie corresponds to the reverse flow mask.
     */
    public boolean isMaskedAsReversed() {
        boolean isMatch;
        if ((value & 0xE000000000000000L) != 0) {
            isMatch = (value & FLOW_PATH_REVERSE_FLAG) != 0;
        } else {
            isMatch = (value & DEPRECATED_FLOW_PATH_DIRECTION_FLAG) != 0;
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

    public long getUnmaskedValue() {
        return value & FLOW_COOKIE_VALUE_MASK;
    }

    private void ensureNoFlagsConflicts() {
        long[] mutuallyExclusiveFlags = {DEFAULT_RULE_FLAG, FLOW_PATH_FORWARD_FLAG, FLOW_PATH_REVERSE_FLAG};
        List<Long> conflict = new ArrayList<>();
        for (long flag : mutuallyExclusiveFlags) {
            if ((value & flag) != 0) {
                conflict.add(flag);
            }
        }

        if (1 < conflict.size()) {
            String conflictAsString = conflict.stream()
                    .map(Cookie::toString)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(String.format(
                    "Invalid flags combination - more than one of mutually exclusive flags(%s) are set in cookie %s",
                    conflictAsString, this));
        }
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

    @Override
    public int compareTo(Cookie compareWith) {
        return Long.compare(value, compareWith.value);
    }
}
