/* Copyright 2018 Telstra Open Source
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

import lombok.Value;

/**
 * Represents information about a cookie.
 */
@Value
public class Cookie {
    public static final long DROP_RULE_COOKIE = 0x8000000000000001L;
    public static final long VERIFICATION_BROADCAST_RULE_COOKIE = 0x8000000000000002L;
    public static final long VERIFICATION_UNICAST_RULE_COOKIE = 0x8000000000000003L;
    public static final long DROP_VERIFICATION_LOOP_RULE_COOKIE = 0x8000000000000004L;
    public static final long CATCH_BFD_RULE_COOKIE = 0x8000000000000005L;
    public static final long DEFAULT_RULES_MASK = 0x8000000000000000L;

    private final long value;

    public boolean isDefaultRule() {
        return isDefaultRule(value);
    }

    public static boolean isDefaultRule(long cookie) {
        return (cookie & DEFAULT_RULES_MASK) != 0L;
    }

    @Override
    public String toString() {
        return toString(value);
    }

    public static String toString(long cookie) {
        return String.format("0x%016X", cookie);
    }
}
