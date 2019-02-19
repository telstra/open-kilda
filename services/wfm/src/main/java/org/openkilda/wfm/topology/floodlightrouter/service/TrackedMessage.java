/* Copyright 2018 Telstra Open Source;
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

package org.openkilda.wfm.topology.floodlightrouter.service;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TrackedMessage {
    private final String correlationId;
    private final long timestamp;
    private long lastReplyTime;
    private long blacklistTime;

    /**
     * Tests whether request is expired or not.
     * @param interval - to be tested against
     * @return expired result
     */
    public boolean isExpired(long now, long interval) {
        return lastReplyTime + interval < now;
    }

    /**
     * Tests whether request is expired from blacklist.
     * @param interval - to be tested against
     * @return expired result
     */
    public boolean isBlackListExpired(long now, long interval) {
        return blacklistTime + interval < now;
    }

}
