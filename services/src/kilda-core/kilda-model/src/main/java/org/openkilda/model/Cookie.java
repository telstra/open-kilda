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

import org.openkilda.exception.CookieTypeMismatchException;
import org.openkilda.utils.CookieEnumField;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.annotations.VisibleForTesting;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents information about a cookie.
 * Uses 64 bit to encode information about the flow:
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |            Payload Reserved           |                       |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           Reserved Prefix           |C|     Rule Type   | | | |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * <p>
 * Rule types:
 * 0 - Customer flow rule
 * 1 - LLDP rule
 * 2 - Multi-table ISL rule for vlan encapsulation for egress table
 * 3 - Multi-table ISL rule for vxlan encapsulation for egress table
 * 4 - Multi-table ISL rule for vxlan encapsulation for transit table
 * 5 - Multi-table customer flow rule for ingress table pass-through
 * </p>
 */
@EqualsAndHashCode(of = {"value"})
public class Cookie implements Comparable<Cookie>, Serializable {
    private static final long serialVersionUID = 1L;

    public static final long DEFAULT_RULE_FLAG                   = 0x8000_0000_0000_0000L;
    public static final long FLOW_PATH_FORWARD_FLAG              = 0x4000_0000_0000_0000L;
    public static final long FLOW_PATH_REVERSE_FLAG              = 0x2000_0000_0000_0000L;

    // There is no alive system that use this deprecated direction flags so it should be save to drop it.
    @Deprecated
    public static final long DEPRECATED_FLOW_PATH_DIRECTION_FLAG = 0x0080_0000_0000_0000L;
    public static final long FLOW_COOKIE_VALUE_MASK              = 0x0000_0000_000F_FFFFL;
    public static final long ISL_COOKIE_VALUE_MASK               = 0x0000_0000_000F_FFFFL;
    public static final long INGRESS_RULE_COOKIE_VALUE_MASK      = 0x0000_0000_000F_FFFFL;

    public static final long DROP_RULE_COOKIE                           = 0x01L | DEFAULT_RULE_FLAG;
    public static final long VERIFICATION_BROADCAST_RULE_COOKIE         = 0x02L | DEFAULT_RULE_FLAG;
    public static final long VERIFICATION_UNICAST_RULE_COOKIE           = 0x03L | DEFAULT_RULE_FLAG;
    public static final long DROP_VERIFICATION_LOOP_RULE_COOKIE         = 0x04L | DEFAULT_RULE_FLAG;
    public static final long CATCH_BFD_RULE_COOKIE                      = 0x05L | DEFAULT_RULE_FLAG;
    public static final long ROUND_TRIP_LATENCY_RULE_COOKIE             = 0x06L | DEFAULT_RULE_FLAG;
    public static final long VERIFICATION_UNICAST_VXLAN_RULE_COOKIE     = 0x07L | DEFAULT_RULE_FLAG;
    public static final long MULTITABLE_PRE_INGRESS_PASS_THROUGH_COOKIE = 0x08L | DEFAULT_RULE_FLAG;
    public static final long MULTITABLE_INGRESS_DROP_COOKIE             = 0x09L | DEFAULT_RULE_FLAG;
    public static final long MULTITABLE_POST_INGRESS_DROP_COOKIE        = 0x0AL | DEFAULT_RULE_FLAG;
    public static final long MULTITABLE_EGRESS_PASS_THROUGH_COOKIE      = 0x0BL | DEFAULT_RULE_FLAG;
    public static final long MULTITABLE_TRANSIT_DROP_COOKIE             = 0x0CL | DEFAULT_RULE_FLAG;
    public static final long LLDP_INPUT_PRE_DROP_COOKIE                 = 0x0DL | DEFAULT_RULE_FLAG;
    public static final long LLDP_TRANSIT_COOKIE                        = 0x0EL | DEFAULT_RULE_FLAG;
    public static final long LLDP_INGRESS_COOKIE                        = 0x0FL | DEFAULT_RULE_FLAG;
    public static final long LLDP_POST_INGRESS_COOKIE                   = 0x10L | DEFAULT_RULE_FLAG;
    public static final long LLDP_POST_INGRESS_VXLAN_COOKIE             = 0x11L | DEFAULT_RULE_FLAG;
    public static final long LLDP_POST_INGRESS_ONE_SWITCH_COOKIE        = 0x12L | DEFAULT_RULE_FLAG;

    // update ALL_FIELDS if modify fields list
    static final BitField DEFAULT_FLAG                = new BitField(0x8000_0000_0000_0000L);
    static final BitField FLOW_FORWARD_DIRECTION_FLAG = new BitField(0x4000_0000_0000_0000L);
    static final BitField FLOW_REVERSE_DIRECTION_FLAG = new BitField(0x2000_0000_0000_0000L);
    static final BitField TYPE_FIELD                  = new BitField(0x1FF0_0000_0000_0000L);
    static final BitField FLOW_EFFECTIVE_ID_FIELD     = new BitField(0x0000_0000_000F_FFFFL);

    // used by unit tests to check fields intersections
    static final BitField[] ALL_FIELDS = new BitField[] {
            DEFAULT_FLAG, FLOW_FORWARD_DIRECTION_FLAG, FLOW_REVERSE_DIRECTION_FLAG, TYPE_FIELD,
            FLOW_EFFECTIVE_ID_FIELD};

    private long value;

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

    protected Cookie(CookieType type) {
        this.value = 0;
        setType(type);
    }

    public static Cookie buildForwardCookie(long unmaskedCookie) {
        return new Cookie(unmaskedCookie | Cookie.FLOW_PATH_FORWARD_FLAG);
    }

    public static Cookie buildReverseCookie(long unmaskedCookie) {
        return new Cookie(unmaskedCookie | Cookie.FLOW_PATH_REVERSE_FLAG);
    }

    /**
     * Convert port number into isl-VLAN-egress "cookie".
     */
    public static long encodeIslVlanEgress(int port) {
        // FIXME(surabujin): do not allow to return "raw" long value
        return new Cookie(port | DEFAULT_RULE_FLAG)
                .setType(CookieType.MULTI_TABLE_ISL_VLAN_EGRESS_RULES)
                .getValue();
    }

    /**
     * Convert port number into isl-VxLAN-egress "cookie".
     */
    public static long encodeIslVxlanEgress(int port) {
        // FIXME(surabujin): do not allow to return "raw" long value
        return new Cookie(port | Cookie.DEFAULT_RULE_FLAG)
                .setType(CookieType.MULTI_TABLE_ISL_VXLAN_EGRESS_RULES)
                .getValue();
    }

    /**
     * Convert port number into isl-VxLAN-transit "cookie".
     */
    public static long encodeIslVxlanTransit(int port) {
        // FIXME(surabujin): do not allow to return "raw" long value
        return new Cookie(port | Cookie.DEFAULT_RULE_FLAG)
                .setType(CookieType.MULTI_TABLE_ISL_VXLAN_TRANSIT_RULES)
                .getValue();
    }

    /**
     * Convert port number into ingress-rule-pass-through "cookie".
     */
    public static long encodeIngressRulePassThrough(int port) {
        // FIXME(surabujin): do not allow to return "raw" long value
        return new Cookie(port | Cookie.DEFAULT_RULE_FLAG)
                .setType(CookieType.MULTI_TABLE_INGRESS_RULES)
                .getValue();
    }

    /**
     * Creates masked cookie for LLDP rule.
     */
    public static long encodeLldpInputCustomer(int port) {
        // FIXME(surabujin): do not allow to return "raw" long value
        return new Cookie(port)
                .setField(DEFAULT_FLAG, 1)
                .setType(CookieType.LLDP)
                .getValue();
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
     * Extract and return normalized representation flow path direction.
     */
    public FlowPathDirection getFlowPathDirection() {
        if (getField(FLOW_FORWARD_DIRECTION_FLAG) != 0) {
            return FlowPathDirection.FORWARD;
        } else if (getField(FLOW_REVERSE_DIRECTION_FLAG) != 0) {
            return FlowPathDirection.REVERSE;
        } else {
            return FlowPathDirection.UNKNOWN;
        }
    }

    public long getFlowEffectiveId() {
        return getField(FLOW_EFFECTIVE_ID_FIELD);
    }

    /**
     * Checks whether the cookie corresponds to the LLDP flow.
     */
    public static boolean isLldpInputCustomer (long value) {
        Cookie cookie = new Cookie(value);
        return cookie.getRawType() == CookieType.LLDP.getValue();
    }

    public static boolean isIslVlanEgress(long value) {
        Cookie cookie = new Cookie(value);
        return cookie.getRawType() == CookieType.MULTI_TABLE_ISL_VLAN_EGRESS_RULES.getValue();
    }

    public static boolean isIslVxlanEgress(long value) {
        Cookie cookie = new Cookie(value);
        return cookie.getRawType() == CookieType.MULTI_TABLE_ISL_VXLAN_EGRESS_RULES.getValue();
    }

    public static boolean isIslVxlanTransit(long value) {
        Cookie cookie = new Cookie(value);
        return cookie.getRawType() == CookieType.MULTI_TABLE_ISL_VXLAN_TRANSIT_RULES.getValue();
    }

    public static boolean isIngressRulePassThrough(long value) {
        Cookie cookie = new Cookie(value);
        return cookie.getRawType() == CookieType.MULTI_TABLE_INGRESS_RULES.getValue();
    }

    public static long getValueFromIntermediateCookie(long value) {
        return value & ISL_COOKIE_VALUE_MASK;
    }

    public long getUnmaskedValue() {
        return value & FLOW_COOKIE_VALUE_MASK;
    }

    /**
     * Extract and return "type" field.
     */
    public CookieType getType() {
        return resolveEnum(CookieType.values(), getRawType(), CookieType.class);
    }

    protected int getRawType() {
        return (int) getField(TYPE_FIELD);
    }

    protected Cookie setType(CookieType type) {
        return setField(TYPE_FIELD, type.getValue());
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

    protected void ensureType(CookieType type) throws CookieTypeMismatchException {
        if (getRawType() != type.getValue()) {
            throw new CookieTypeMismatchException(this, type);
        }
    }

    @VisibleForTesting
    long getField(BitField field) {
        long payload = value & makeFieldMask(field);
        return payload >>> field.offset;
    }

    @VisibleForTesting
    Cookie setField(BitField field, long payload) {
        long mask = makeFieldMask(field);
        payload <<= field.offset;
        payload &= mask;

        mask = ~mask;
        value = (value & mask) | payload;

        return this;
    }

    private long makeFieldMask(BitField field) {
        long mask = -1 << field.width;
        return ~mask << field.offset;
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

    protected static <T extends CookieEnumField> T resolveEnum(T[] valuesSpace, long needle, Class<T> typeRef) {
        for (T entry : valuesSpace) {
            if (entry.getValue() == needle) {
                return entry;
            }
        }

        throw new IllegalArgumentException(String.format(
                "Unable to map value %x value into %s value", needle, typeRef.getSimpleName()));
    }

    @Getter
    static class BitField {
        private final int width;
        private final int offset;

        public BitField(long mask) {
            Integer start = null;
            Integer end = null;

            long probe = 1;
            for (int i = 0; i < 8 * 8 - 1; i++) {
                boolean isSet = (mask & probe) !=0;
                if (start == null && isSet) {
                    start = i;
                } else if (start != null && end == null && ! isSet) {
                    end = i;
                } else if (end != null && isSet) {
                    throw new IllegalArgumentException(String.format(
                            "Illegal bit field mask %s - it contain gaps", Cookie.toString(mask)));
                }
            }

            if (start == null) {
                throw new IllegalArgumentException("Bit field mask must not be 0");
            }
            if (end == null) {
                end = 8 * 8;
            }

            this.width = end - start;
            this.offset = start;
        }
    }

    // 9 bit long field
    public enum CookieType implements CookieEnumField {
        FLOW_SEGMENT(0x000),
        LLDP(0x001),
        MULTI_TABLE_ISL_VLAN_EGRESS_RULES(0x002),
        MULTI_TABLE_ISL_VXLAN_EGRESS_RULES(0x003),
        MULTI_TABLE_ISL_VXLAN_TRANSIT_RULES(0x004),
        MULTI_TABLE_INGRESS_RULES(0x005),
        INGRESS_SEGMENT(0x006),   // used for ingress flow segment and for one switch flow segments
        SHARED_OF_FLOW(0x007);

        private int value;

        CookieType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
