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

package org.openkilda.wfm.share.utils.rule.validation;

import org.openkilda.model.Cookie;
import org.openkilda.wfm.share.model.validate.FlowPathReference;

public final class OfCookieUtil {
    public static final OfCookieUtil INSTANCE = new OfCookieUtil();

    public boolean isSameCookie(long left, long right) {
        return isSameCookie(new Cookie(left), new Cookie(right));
    }

    public boolean isSameCookie(Cookie left, Cookie right) {
        return makeFlowPathRef(left).equals(makeFlowPathRef(right));
    }

    public FlowPathReference makeFlowPathRef(Cookie cookie) {
        return new FlowPathReference(cookie.getFlowPathDirection(), cookie.getFlowEffectiveId());
    }

    private OfCookieUtil() {}
}
