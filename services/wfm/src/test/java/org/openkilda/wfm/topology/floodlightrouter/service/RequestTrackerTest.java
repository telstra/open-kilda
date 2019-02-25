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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class RequestTrackerTest {
    private static final long REQUEST_TIMEOUT = 5L;
    private static final long BLACKLIST_TIMEOUT = 5L;
    private static final String BASE_CORRELATION_ID = "test";

    @Test
    public void testTrackMessage() {
        RequestTracker tracker = new RequestTracker(REQUEST_TIMEOUT, BLACKLIST_TIMEOUT);
        tracker.trackMessage(BASE_CORRELATION_ID);
        assertTrue(tracker.trackedRequests.containsKey(BASE_CORRELATION_ID));
    }

    @Test
    public void testCheckReplyMessageIsBlackListed() {
        RequestTracker tracker = new RequestTracker(REQUEST_TIMEOUT, BLACKLIST_TIMEOUT);
        long now = System.currentTimeMillis();
        tracker.blackList.putIfAbsent(BASE_CORRELATION_ID, new TrackedMessage(BASE_CORRELATION_ID,
                now, now, 0L));
        assertFalse(tracker.checkReplyMessage(BASE_CORRELATION_ID, false));
    }

    @Test
    public void testCheckReplyMessageNoMessageFound() {
        RequestTracker tracker = new RequestTracker(REQUEST_TIMEOUT, BLACKLIST_TIMEOUT);
        assertFalse(tracker.checkReplyMessage(BASE_CORRELATION_ID, false));

    }

    @Test
    public void testCheckReplyMessageIsExpired() {
        RequestTracker tracker = new RequestTracker(REQUEST_TIMEOUT, BLACKLIST_TIMEOUT);
        long now = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(10);
        tracker.trackedRequests.putIfAbsent(BASE_CORRELATION_ID, new TrackedMessage(BASE_CORRELATION_ID,
                now, now, 0L));
        assertFalse(tracker.checkReplyMessage(BASE_CORRELATION_ID, false));
        assertTrue(tracker.blackList.containsKey(BASE_CORRELATION_ID));
    }

    @Test
    public void testCheckReplyMessageSuccess() {
        RequestTracker tracker = new RequestTracker(REQUEST_TIMEOUT, BLACKLIST_TIMEOUT);
        tracker.trackMessage(BASE_CORRELATION_ID);
        TrackedMessage trackedMessage = tracker.trackedRequests.get(BASE_CORRELATION_ID);
        long lastReplyTime = trackedMessage.getLastReplyTime();
        assertTrue(tracker.checkReplyMessage(BASE_CORRELATION_ID, false));
        // Check that we update last reply time
        assertTrue(lastReplyTime <= trackedMessage.getLastReplyTime());
    }

    @Test
    public void testMessagesRemovedFromBlacklist() {
        RequestTracker tracker = new RequestTracker(REQUEST_TIMEOUT, BLACKLIST_TIMEOUT);
        long now = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(20);
        TrackedMessage tracked = new TrackedMessage(BASE_CORRELATION_ID,
                now, now, now);
        tracker.blackList.putIfAbsent(BASE_CORRELATION_ID, tracked);
        tracker.cleanupOldMessages();
        assertTrue(tracker.blackList.isEmpty());
    }

    @Test
    public void testMessagesAreMovedToBlacklist() {
        RequestTracker tracker = new RequestTracker(REQUEST_TIMEOUT, BLACKLIST_TIMEOUT);
        long now = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(20);
        TrackedMessage tracked = new TrackedMessage(BASE_CORRELATION_ID,
                now, now, 0L);
        tracker.trackedRequests.putIfAbsent(BASE_CORRELATION_ID, tracked);
        tracker.cleanupOldMessages();
        assertTrue(tracker.trackedRequests.isEmpty());
        assertTrue(tracker.blackList.containsKey(BASE_CORRELATION_ID));
    }
}
