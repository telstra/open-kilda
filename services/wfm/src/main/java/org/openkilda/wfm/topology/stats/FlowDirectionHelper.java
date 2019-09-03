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

package org.openkilda.wfm.topology.stats;

import static org.openkilda.model.Cookie.FORWARD_FLOW_COOKIE_MASK;
import static org.openkilda.model.Cookie.REVERSE_FLOW_COOKIE_MASK;

public final class FlowDirectionHelper {

    private FlowDirectionHelper() {}

    public enum Direction {
        UNKNOWN,
        FORWARD,
        REVERSE
    }


    /**
     * Trys to determine the direction of the flow based on the cookie.
     *
     * @param cookie cookie
     * @return flow direction
     */
    public static Direction findDirection(long cookie) throws FlowCookieException {
        // Kilda flow first number represents direction with 4 = forward and 2 = reverse
        // high order nibble represents type of flow with a 2 representing a forward flow
        // and a 4 representing the reverse flow
        if (!isKildaCookie(cookie)) {
            throw new FlowCookieException(cookie + " is not a Kilda flow");
        }
        long direction = cookie & 0x6000000000000000L;
        if ((direction != FORWARD_FLOW_COOKIE_MASK) && (direction != REVERSE_FLOW_COOKIE_MASK)) {
            throw new FlowCookieException("unknown direction for " + cookie);
        }
        return direction == FORWARD_FLOW_COOKIE_MASK ? Direction.FORWARD : Direction.REVERSE;
    }

    static boolean isKildaCookie(long cookie) {
        // First 3 bits of cookie represent default rule, forward direction and reverse direction
        // only one of them may be equal to 1 at one moment
        long oldFlowType = cookie >>> 61 & 0x7;
        if (oldFlowType != 1 && oldFlowType != 2 && oldFlowType != 4) {
            return false;
        }

        // next 9 bits reserved for type
        // next 20 bits must be zeros (this condition will be changed when subtypes will be introduced)
        return (cookie & 0x000FFFFF00000000L) == 0;
    }
}
