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

import org.openkilda.exception.InvalidCookieException;
import org.openkilda.model.FlowSegmentCookie;

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
     * @param rawCookie cookie
     * @return flow direction
     */
    public static Direction findDirection(long rawCookie) throws FlowCookieException {
        FlowSegmentCookie cookie = new FlowSegmentCookie(rawCookie);
        try {
            cookie.validate();
        } catch (InvalidCookieException e) {
            throw new FlowCookieException(e.getMessage());
        }

        Direction direction;
        switch (cookie.getDirection()) {
            case FORWARD:
                direction = Direction.FORWARD;
                break;
            case REVERSE:
                direction = Direction.REVERSE;
                break;
            default:
                throw new FlowCookieException("unknown direction for " + cookie);
        }

        return direction;
    }
}
