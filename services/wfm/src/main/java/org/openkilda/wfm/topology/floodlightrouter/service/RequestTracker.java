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

package org.openkilda.wfm.topology.floodlightrouter.service;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RequestTracker {
    @VisibleForTesting
    protected Map<String, TrackedMessage> trackedRequests = new HashMap<>();
    @VisibleForTesting
    protected Map<String, TrackedMessage> blackList = new HashMap<>();
    private long requestTimeout;
    private long blacklistTimeout;

    public RequestTracker(long requestTimeout, long blacklistTimeout) {
        this.requestTimeout = TimeUnit.SECONDS.toMillis(requestTimeout);
        this.blacklistTimeout = TimeUnit.SECONDS.toMillis(blacklistTimeout);
    }

    /**
     * Track new message for timeout handling.
     */
    public void trackMessage(String correlationId) {
        log.debug("Track message {}", correlationId);
        long now = System.currentTimeMillis();
        TrackedMessage message = new TrackedMessage(correlationId, now, now, 0L);
        trackedRequests.put(correlationId, message);
    }

    /**
     * Cleans up expired tracked messages.
     * @return collection of messages to be timeouted.
     */
    public Collection<TrackedMessage> cleanupOldMessages() {
        long now = System.currentTimeMillis();
        // Clean up blacklist first
        Set<String> toBeRemovedFromBlackList = new HashSet<>();
        for (Map.Entry<String, TrackedMessage> messageEntry: blackList.entrySet()) {
            if (messageEntry.getValue().isBlackListExpired(now, blacklistTimeout)) {
                toBeRemovedFromBlackList.add(messageEntry.getKey());
            }
        }
        for (String correlationId: toBeRemovedFromBlackList) {
            log.debug("Removing {} from blacklist", correlationId);
            blackList.remove(correlationId);
        }

        // Put new outdated messages to blacklist
        Map<String, TrackedMessage> toBlackList = new HashMap<>();
        for (Map.Entry<String, TrackedMessage> entry: trackedRequests.entrySet()) {
            if (entry.getValue().isExpired(now, requestTimeout)) {
                log.debug("Put {} to blacklist", entry.getKey());
                entry.getValue().setBlacklistTime(now);
                toBlackList.put(entry.getKey(), entry.getValue());
            }
        }
        for (String correlationId: toBlackList.keySet()) {
            trackedRequests.remove(correlationId);
        }
        blackList.putAll(toBlackList);
        return toBlackList.values();
    }

    /**
     * Check new message for blacklist.
     * @return whether is message ok or not.
     */
    public boolean checkReplyMessage(String correlationId) {
        long now = System.currentTimeMillis();
        if (blackList.containsKey(correlationId)) {
            log.debug("Response for {} is already blacklisted", correlationId);
            return false;
        }
        TrackedMessage message = trackedRequests.get(correlationId);
        if (message == null) {
            log.warn("Received response for untracked message {}", correlationId);
            return false;
        }

        boolean expired = message.isExpired(now, requestTimeout);

        if (expired) {
            log.debug("Response for {} is expired", correlationId);
            toBlackList(message);
            return false;
        }
        message.setLastReplyTime(now);

        return true;
    }

    private void toBlackList(TrackedMessage message) {
        log.debug("Put {} to blacklist", message.getCorrelationId());
        message.setBlacklistTime(System.currentTimeMillis());
        blackList.put(message.getCorrelationId(), message);
        trackedRequests.remove(message.getCorrelationId());
    }
}
