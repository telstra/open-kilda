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

import static org.openkilda.model.Constants.DEFAULT_RULES_COOKIE_MASK;
import static org.openkilda.model.Constants.MAX_DEFAULT_RULE_METER_ID;
import static org.openkilda.model.Constants.METER_ID_DEFAULT_RULE_MASK;
import static org.openkilda.model.Constants.MIN_DEFAULT_RULE_METER_ID;

public final class Utils {
    private Utils() {
        throw new UnsupportedOperationException();
    }

    public static boolean isMeterIdOfDefaultRule(long meterId) {
        return MIN_DEFAULT_RULE_METER_ID <= meterId && meterId <= MAX_DEFAULT_RULE_METER_ID;
    }

    public static boolean isDefaultRule(long cookie) {
        return (cookie & DEFAULT_RULES_COOKIE_MASK) != 0;
    }

    public static long createCookieForDefaultRule(long meterId) {
        return meterId | DEFAULT_RULES_COOKIE_MASK;
    }

    public static long createMeterIdForDefaultRule(long cookie) {
        return cookie & METER_ID_DEFAULT_RULE_MASK;
    }
}
