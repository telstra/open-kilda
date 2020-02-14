/* Copyright 2020 Telstra Open Source
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

import org.openkilda.exception.InvalidCookieException;
import org.openkilda.model.MeterId;
import org.openkilda.model.bitops.BitField;
import org.openkilda.model.bitops.NumericEnumField;

import lombok.Builder;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Optional;

public class ServiceCookie extends CookieBase implements Comparable<ServiceCookie> {
    // update ALL_FIELDS if modify fields list
    //                           used by generic cookie -> 0x9FF0_0000_0000_0000L
    static final BitField SERVICE_TAG_FIELD = new BitField(0x0000_0000_FFFF_FFFFL);

    // used by unit tests to check fields intersections
    static final BitField[] ALL_FIELDS = ArrayUtils.addAll(CookieBase.ALL_FIELDS, SERVICE_TAG_FIELD);

    private static final CookieType VALID_TYPE = CookieType.SERVICE_OR_FLOW_SEGMENT;

    public ServiceCookie(long value) {
        super(value);
    }

    // TODO(surabujin): special case for statistic report, need to find the way to get rid of it
    public ServiceCookie(MeterId meterId) {
        super(makeValue(meterId), CookieType.SERVICE_OR_FLOW_SEGMENT);
    }

    public ServiceCookie(ServiceCookieTag serviceTag) {
        this(CookieType.SERVICE_OR_FLOW_SEGMENT, serviceTag);
    }

    @Builder
    public ServiceCookie(CookieType type, ServiceCookieTag serviceTag) {
        super(makeValue(type, serviceTag), type);
    }

    @Override
    public void validate() throws InvalidCookieException {
        super.validate();
        validateServiceFlag(true);

        CookieType type = getType();
        if (type != VALID_TYPE) {
            throw new InvalidCookieException(formatIllegalTypeError(type, VALID_TYPE), this);
        }
    }

    // TODO - replace existing meter-id generation approach
    public MeterId getMeterId() {
        return new MeterId(getField(SERVICE_TAG_FIELD));
    }

    /**
     * Extract and return "service tag" field is save way (return empty {@link Optional} object if tag is invalid).
     */
    public Optional<ServiceCookieTag> getServiceTagSafe() {
        try {
            return Optional.of(getServiceTag());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public ServiceCookieTag getServiceTag() {
        long raw = getField(SERVICE_TAG_FIELD);
        return resolveEnum(ServiceCookieTag.values(), raw, ServiceCookieTag.class);
    }

    @Override
    public int compareTo(ServiceCookie other) {
        return cookieComparison(other);
    }

    /**
     * Conver existing {@link ServiceCookie} instance into {@link ServiceCookieBuilder}.
     */
    public ServiceCookieBuilder toBuilder() {
        return new ServiceCookieBuilder()
                .type(getType())
                .serviceTag(getServiceTag());
    }

    private static long makeValue(MeterId meterId) {
        if (!MeterId.isMeterIdOfDefaultRule(meterId.getValue())) {
            throw new IllegalArgumentException(
                    String.format("Meter ID '%s' is not a meter ID of default rule.", meterId));
        }
        long value = setField(0, SERVICE_FLAG, 1);
        return setField(value, SERVICE_TAG_FIELD, meterId.getValue());
    }

    private static long makeValue(CookieType type, ServiceCookieTag serviceTag) {
        if (type != VALID_TYPE) {
            throw new IllegalArgumentException(formatIllegalTypeError(type, VALID_TYPE));
        }

        long value = setField(0, SERVICE_FLAG, 1);
        return setField(value, SERVICE_TAG_FIELD, serviceTag.getValue());
    }

    public enum ServiceCookieTag implements NumericEnumField {
        DROP_RULE_COOKIE(0x01),
        VERIFICATION_BROADCAST_RULE_COOKIE(0x02),
        VERIFICATION_UNICAST_RULE_COOKIE(0x03),
        DROP_VERIFICATION_LOOP_RULE_COOKIE(0x04),
        CATCH_BFD_RULE_COOKIE(0x05),
        ROUND_TRIP_LATENCY_RULE_COOKIE(0x06),
        VERIFICATION_UNICAST_VXLAN_RULE_COOKIE(0x07),
        MULTITABLE_PRE_INGRESS_PASS_THROUGH_COOKIE(0x08),
        MULTITABLE_INGRESS_DROP_COOKIE(0x09),
        MULTITABLE_POST_INGRESS_DROP_COOKIE(0x0A),
        MULTITABLE_EGRESS_PASS_THROUGH_COOKIE(0x0B),
        MULTITABLE_TRANSIT_DROP_COOKIE(0x0C),
        LLDP_INPUT_PRE_DROP_COOKIE(0x0D),
        LLDP_TRANSIT_COOKIE(0x0E),
        LLDP_INGRESS_COOKIE(0x0F),
        LLDP_POST_INGRESS_COOKIE(0x10),
        LLDP_POST_INGRESS_VXLAN_COOKIE(0x11),
        LLDP_POST_INGRESS_ONE_SWITCH_COOKIE(0x12),
        ARP_INPUT_PRE_DROP_COOKIE(0x13),
        ARP_TRANSIT_COOKIE(0x14),
        ARP_INGRESS_COOKIE(0x15),
        ARP_POST_INGRESS_COOKIE(0x16),
        ARP_POST_INGRESS_VXLAN_COOKIE(0x17),
        ARP_POST_INGRESS_ONE_SWITCH_COOKIE(0x18),
        SERVER_42_OUTPUT_VLAN_COOKIE(0x19),
        SERVER_42_OUTPUT_VXLAN_COOKIE(0x1A);


        private int value;

        ServiceCookieTag(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
