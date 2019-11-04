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
import org.openkilda.utils.ICookieEnumField;

public class IngressSegmentCookie extends Cookie {

    // update ALL_FIELDS if modify fields list
    // 0xFFF0_0000_000F_FFFF used by generic cookie
    // 0x0008_0000_0000_0000
    static final BitField FORWARDING_FLAG = new BitField(1, 51);
    // 0x0007_0000_0000_0000
    static final BitField SUBTYPE_FIELD = new BitField(3, 48);

    // used by unit tests to check fields intersections
    static final BitField[] ALL_FIELDS = new BitField[] {
            FORWARDING_FLAG, SUBTYPE_FIELD};

    static final BitField OWN_BITS = new BitField(24, 20);

    public static IngressSegmentCookie unpack(Cookie baseValue) throws CookieTypeMismatchException {
        baseValue.ensureType(CookieType.INGRESS_SEGMENT);
        return new IngressSegmentCookie(baseValue);
    }

    /**
     * Convert cookie into ingress-segment cookie by replacing type field and zeroed all bits used by ingress-segment
     * cookie.
     */
    public static IngressSegmentCookie convert(Cookie baseValue) {
        IngressSegmentCookie cookie = new IngressSegmentCookie(baseValue);
        cookie.setType(CookieType.INGRESS_SEGMENT);
        cookie.setField(OWN_BITS, 0);
        return cookie;
    }

    protected IngressSegmentCookie(Cookie baseValue) {
        super(baseValue.getValue());
    }

    public boolean isForwarding() {
        return getField(FORWARDING_FLAG) == 1;
    }

    public IngressSegmentCookie setForwarding(boolean value) {
        return fork()
                .setField(FORWARDING_FLAG, value ? 1 : 0);
    }

    public IngressSegmentSubType getSubtype() {
        return resolveEnum(IngressSegmentSubType.values(), getField(SUBTYPE_FIELD), IngressSegmentSubType.class);
    }

    public IngressSegmentCookie setSubType(IngressSegmentSubType subType) {
        return fork()
                .setField(SUBTYPE_FIELD, subType.getValue());
    }

    @Override
    protected IngressSegmentCookie setField(BitField field, long payload) {
        super.setField(field, payload);
        return this;
    }

    private IngressSegmentCookie fork() {
        return new IngressSegmentCookie(this);
    }

    public enum IngressSegmentSubType implements ICookieEnumField {
        DEFAULT_PORT_FORWARD(0x0),
        OUTER_VLAN_ONLY(0x1),
        OUTER_VLAN_MATCH_AND_REMOVE(0x2),
        SINGLE_VLAN_FORWARD(0x3),
        DOUBLE_VLAN_FORWARD(0x4);

        private int value;

        IngressSegmentSubType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
