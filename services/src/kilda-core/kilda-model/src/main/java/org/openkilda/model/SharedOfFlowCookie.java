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

import lombok.Getter;

public class SharedOfFlowCookie extends Cookie {
    // update ALL_FIELDS if modify fields list
    // 0xFFF0_0000_0000_0000 used by generic cookie
    static final BitField SHARED_TYPE_FIELD         = new BitField(0x000F_0000_0000_0000L);
    static final BitField UNIQUE_ID_FIELD           = new BitField(0x0000_0000_FFFF_FFFFL);

    static final BitField SHARED_OF_COOKIE_OWN_BITS = new BitField(0x000F_FFFF_FFFF_FFFFL);

    // used by unit tests to check fields intersections
    static final BitField[] ALL_FIELDS = new BitField[] {
            SHARED_TYPE_FIELD, UNIQUE_ID_FIELD};

    public static SharedOfFlowCookie unpackTrusted(long rawValue) {
        return new SharedOfFlowCookie(rawValue);
    }

    public static SharedOfFlowCookie unpack(Cookie baseValue) throws CookieTypeMismatchException {
        baseValue.ensureType(CookieType.SHARED_OF_FLOW);
        return new SharedOfFlowCookie(baseValue);
    }

    /**
     * Convert cookie into shared-of-flow cookie by replacing type field and zeroed all bits used by ingress-segment
     * cookie.
     */
    public static SharedOfFlowCookie convert(Cookie baseValue) {
        SharedOfFlowCookie cookie = new SharedOfFlowCookie(baseValue);
        cookie.setType(CookieType.SHARED_OF_FLOW);
        cookie.setField(SHARED_OF_COOKIE_OWN_BITS, 0);
        return cookie;
    }

    public SharedOfFlowCookie() {
        super(CookieType.SHARED_OF_FLOW);
    }

    protected SharedOfFlowCookie(Cookie cookie) {
        this(cookie.getValue());
    }

    protected SharedOfFlowCookie(long rawValue) {
        super(rawValue);
    }

    public long getUniqueIdField() {
        return getField(UNIQUE_ID_FIELD);
    }

    /**
     * Produce and store unique shared flow id from port number.
     */
    public SharedOfFlowCookie setUniqueIdField(int portNumber) {
        return setUniqueIdField(portNumber, 0);
    }

    /**
     * Produce and store unique shared flow id from port number and vlan-id.
     */
    public SharedOfFlowCookie setUniqueIdField(int portNumber, int vlanId) {
        int vlanUpperRange = 0xFFF;
        if ((vlanId & vlanUpperRange) != vlanId) {
            throw new IllegalArgumentException(String.format(
                    "VLAN-id %d is not within allowed range from 0 to %d", vlanId, vlanUpperRange));
        }

        int portUpperRange = 0xFFFF;
        if ((portNumber & portUpperRange) != portNumber) {
            throw new IllegalArgumentException(String.format(
                    "Port number %d is not within allowed range from 0 to %d", portNumber, portUpperRange));
        }

        return fork()
                .setField(UNIQUE_ID_FIELD, (vlanId << 16) | portNumber);
    }

    @Override
    SharedOfFlowCookie setField(BitField field, long payload) {
        super.setField(field, payload);
        return this;
    }

    private SharedOfFlowCookie fork() {
        return new SharedOfFlowCookie(this);
    }

    public enum IngressSegmentSubType implements CookieEnumField {
        CUSTOMER_PORT(0),
        QINQ_OUTER_VLAN(1);

        @Getter
        private final int value;

        IngressSegmentSubType(int value) {
            this.value = value;
        }
    }
}
