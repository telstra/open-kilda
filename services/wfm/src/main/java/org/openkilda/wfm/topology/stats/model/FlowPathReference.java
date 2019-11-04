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

package org.openkilda.wfm.topology.stats.model;

import org.openkilda.model.Cookie;
import org.openkilda.model.FlowPathDirection;

import lombok.Value;

@Value
public class FlowPathReference {
    private FlowPathDirection direction;
    private long effectiveId;

    public FlowPathReference(Long rawCookie) {
        this(new Cookie(rawCookie));
    }

    public FlowPathReference(Cookie cookie) {
        direction = cookie.getFlowPathDirection();
        effectiveId = cookie.getFlowEffectiveId();
    }
}
