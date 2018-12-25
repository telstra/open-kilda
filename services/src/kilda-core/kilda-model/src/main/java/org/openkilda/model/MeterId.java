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

@Value
public final class MeterId {
    /** Mask is being used to get meter id for corresponding system rule.
     * E.g. for 0x8000000000000002L & METER_ID_DEFAULT_RULE_MASK we will get meter id 2.
     */
    public static final long METER_ID_DEFAULT_RULE_MASK = 0x000000000000000FL;
    public static final long MIN_DEFAULT_RULE_METER_ID = 1;
    public static final long MAX_DEFAULT_RULE_METER_ID = 10;

    private final long value;

    public static boolean isMeterIdOfDefaultRule(long meterId) {
        return MIN_DEFAULT_RULE_METER_ID <= meterId && meterId <= MAX_DEFAULT_RULE_METER_ID;
    }

    public static MeterId createMeterIdForDefaultRule(long cookie) {
        return new MeterId(cookie & METER_ID_DEFAULT_RULE_MASK);
    }
}
