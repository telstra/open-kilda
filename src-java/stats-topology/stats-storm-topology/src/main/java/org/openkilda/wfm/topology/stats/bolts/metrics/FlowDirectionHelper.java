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

package org.openkilda.wfm.topology.stats.bolts.metrics;

import org.openkilda.exception.InvalidCookieException;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.FlowSegmentCookie;

import java.util.Optional;

public final class FlowDirectionHelper {
    private FlowDirectionHelper() {}

    public enum Direction {
        UNKNOWN,
        FORWARD,
        REVERSE
    }

    /**
     * Try to extract the direction of the flow from cookie. Raises {@link FlowCookieException} in case of failure.
     */
    public static Direction findDirection(long rawCookie) throws FlowCookieException {
        return findDirectionSafe(rawCookie)
                .orElseThrow(() -> new FlowCookieException(String.format(
                        "unknown direction for %s", new Cookie(rawCookie))));
    }

    /**
     * Try to extract the direction of the flow from cookie.
     */
    public static Optional<Direction> findDirectionSafe(long rawCookie) {
        FlowSegmentCookie cookie = new FlowSegmentCookie(rawCookie);
        try {
            cookie.validate();
        } catch (InvalidCookieException e) {
            return Optional.empty();
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
                direction = null;
        }

        return Optional.ofNullable(direction);
    }
}
