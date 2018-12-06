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

package org.openkilda.wfm.topology.reroute.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public class ExtendableTimeWindowTest {

    private ExtendableTimeWindow extendableTimeWindow;

    private Clock clock;

    @Before
    public void init() {
        clock = mock(Clock.class);
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        extendableTimeWindow = new ExtendableTimeWindow(10, 100, clock);
    }

    @Test
    public void noFlushEmptyWindow() {
        assertFalse(extendableTimeWindow.isTimeToFlush());
    }

    @Test
    public void noFlushEmptyWindow2() {
        when(clock.instant()).thenReturn(Instant.now());
        extendableTimeWindow.registerEvent();
        extendableTimeWindow.flush();
        assertFalse(extendableTimeWindow.isTimeToFlush());
    }

    @Test
    public void basicCheck() {
        Instant event = Instant.now();
        Instant beforeTimeout = event.plusSeconds(5);
        Instant afterTimeout = event.plusSeconds(12);

        when(clock.instant()).thenReturn(event, beforeTimeout, afterTimeout);

        extendableTimeWindow.registerEvent();
        assertFalse(extendableTimeWindow.isTimeToFlush());
        assertTrue(extendableTimeWindow.isTimeToFlush());
    }

    @Test
    public void extendWindow() {
        Instant event = Instant.now();
        Instant secondEvent = event.plusSeconds(5);
        Instant beforeTimeout = event.plusSeconds(12);
        Instant afterTimeout = event.plusSeconds(16);

        when(clock.instant()).thenReturn(event, secondEvent, beforeTimeout, afterTimeout);

        extendableTimeWindow.registerEvent();
        extendableTimeWindow.registerEvent();
        assertFalse(extendableTimeWindow.isTimeToFlush());
        assertTrue(extendableTimeWindow.isTimeToFlush());
    }

    @Test
    public void maxWindow() {
        Instant event = Instant.now();
        Instant secondEvent = event.plusSeconds(95);
        Instant beforeMaxTime = event.plusSeconds(98);
        Instant afterMaxTime = event.plusSeconds(102);

        when(clock.instant()).thenReturn(event, secondEvent, beforeMaxTime, afterMaxTime);

        extendableTimeWindow.registerEvent();
        extendableTimeWindow.registerEvent();
        assertFalse(extendableTimeWindow.isTimeToFlush());
        assertTrue(extendableTimeWindow.isTimeToFlush());
    }
}
