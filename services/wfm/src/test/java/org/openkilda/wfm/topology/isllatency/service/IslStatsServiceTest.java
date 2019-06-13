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

package org.openkilda.wfm.topology.isllatency.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.openkilda.wfm.topology.isllatency.model.LatencyRecord;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;

import java.time.Clock;

public class IslStatsServiceTest {
    public static final int LATENCY_TIMEOUT = 3;

    @Mock
    private static IslStatsCarrier carrier;
    private static IslStatsService islStatsService;

    @BeforeClass
    public static void setUpOnce() {
        islStatsService = new IslStatsService(carrier, LATENCY_TIMEOUT);
    }

    @Test
    public void isRecordStillValidTest() {
        assertFalse(islStatsService.isRecordStillValid(new LatencyRecord(1, 0)));

        long expiredTimestamp = Clock.systemUTC().instant().minusSeconds(LATENCY_TIMEOUT * 2).toEpochMilli();
        assertFalse(islStatsService.isRecordStillValid(new LatencyRecord(1, expiredTimestamp)));

        assertTrue(islStatsService.isRecordStillValid(new LatencyRecord(1, System.currentTimeMillis())));

        long freshTimestamp = Clock.systemUTC().instant().plusSeconds(LATENCY_TIMEOUT * 2).toEpochMilli();
        assertTrue(islStatsService.isRecordStillValid(new LatencyRecord(1, freshTimestamp)));
    }
}
