/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.stats.service;

import org.openkilda.exception.InvalidCookieException;
import org.openkilda.messaging.info.stats.FlowStatsEntry;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSegmentCookie;

import lombok.extern.slf4j.Slf4j;

@Slf4j
abstract class BaseFlowStatsEntryHandler extends BaseStatsEntryHandler {
    protected final FlowStatsEntry statsEntry;

    public BaseFlowStatsEntryHandler(
            TimeSeriesMeterEmitter meterEmitter, SwitchId switchId, FlowStatsEntry statsEntry, long timestamp) {
        super(meterEmitter, switchId, timestamp);
        this.statsEntry = statsEntry;
    }

    protected static boolean isFlowSatelliteEntry(FlowSegmentCookie cookie) {
        return cookie == null
                || cookie.getType() != CookieType.SERVICE_OR_FLOW_SEGMENT
                || cookie.isLooped()
                || cookie.isMirror();
    }

    protected static FlowSegmentCookie decodeFlowSegmentCookie(long rawCookie) {
        FlowSegmentCookie cookie = new FlowSegmentCookie(rawCookie);
        try {
            cookie.validate();
        } catch (InvalidCookieException e) {
            // not flow segment cookie
            return null;
        }
        return cookie;
    }

    protected static void directionFromCookieIntoTags(FlowSegmentCookie cookie, TagsFormatter tags) {
        if (cookie != null) {
            try {
                tags.addDirectionTag(cookie.getValidatedDirection());
            } catch (IllegalArgumentException e) {
                log.error("Unable to extract direction from flow segment cookie {}", cookie);
            }
        }
    }
}
