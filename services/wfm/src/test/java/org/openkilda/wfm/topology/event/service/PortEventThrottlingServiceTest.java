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

package org.openkilda.wfm.topology.event.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.model.SwitchId;

import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;

public class PortEventThrottlingServiceTest {

    private PortEventThrottlingService service;
    private Clock clock;

    @Before
    public void init() {
        clock = mock(Clock.class);
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        service = new PortEventThrottlingService(1, 5, 5, clock);
    }

    @Test
    public void processEventGoThru() {
        when(clock.instant()).thenReturn(Instant.now());
        assertTrue(service.processEvent(getPortUp(1), ""));
        assertTrue(service.getPortInfos().isEmpty());
    }

    @Test
    public void oneFlap() {
        Instant downEvent = Instant.now();
        Instant upEvent = downEvent.plusMillis(500);
        Instant firstTick = downEvent.plusSeconds(2);
        Instant secondTick = downEvent.plusSeconds(6);
        when(clock.instant()).thenReturn(downEvent, upEvent, firstTick, secondTick);
        assertFalse(service.processEvent(getPortDown(1), ""));
        assertFalse(service.processEvent(getPortUp(1), ""));
        assertTrue(service.getPortInfos().isEmpty());
        assertTrue(service.getPortInfos().isEmpty());
    }

    @Test
    public void coupleFlaps() {
        Instant downEvent = Instant.now();
        Instant upEvent = downEvent.plusMillis(500);
        Instant secondDownEvent = downEvent.minusSeconds(2);
        Instant secondUpEvent = secondDownEvent.minusMillis(500);
        Instant firstTick = downEvent.plusSeconds(4);
        Instant secondTick = downEvent.plusSeconds(6);
        when(clock.instant()).thenReturn(downEvent, upEvent, secondDownEvent, secondUpEvent, firstTick, secondTick);
        assertFalse(service.processEvent(getPortDown(1), ""));
        assertFalse(service.processEvent(getPortUp(1), ""));
        assertFalse(service.processEvent(getPortDown(1), ""));
        assertFalse(service.processEvent(getPortUp(1), ""));
        assertTrue(service.getPortInfos().isEmpty());
        assertTrue(service.getPortInfos().isEmpty());
    }

    @Test
    public void longUpDelay() {
        Instant downEvent = Instant.now();
        Instant firstTick = downEvent.plusMillis(1100);
        Instant upEvent = downEvent.plusSeconds(2);
        Instant secondTick = firstTick.plusSeconds(4);
        Instant thirdTick = firstTick.plusSeconds(6);
        when(clock.instant()).thenReturn(downEvent, firstTick, upEvent, secondTick, thirdTick);
        assertFalse(service.processEvent(getPortDown(1), ""));
        checkPortInfos(getPortDown(1));
        assertFalse(service.processEvent(getPortUp(1), ""));
        assertTrue(service.getPortInfos().isEmpty());
        checkPortInfos(getPortUp(1));
    }

    @Test
    public void flapAtEndOfWarmUp() {
        Instant downEvent = Instant.now();
        Instant upEvent = downEvent.plusMillis(500);
        Instant secondDownEvent = upEvent.plusSeconds(4);
        Instant secondUpEvent = secondDownEvent.plusMillis(100);
        Instant firstTick = secondUpEvent.plusMillis(100);
        Instant secondTick = downEvent.plusSeconds(6);
        Instant thirdTick = secondTick.plusSeconds(3);
        Instant fourthTick = secondTick.plusSeconds(6);
        when(clock.instant()).thenReturn(downEvent, upEvent, secondDownEvent, secondUpEvent, firstTick, secondTick,
                thirdTick, fourthTick);
        assertFalse(service.processEvent(getPortDown(1), ""));
        assertFalse(service.processEvent(getPortUp(1), ""));
        assertFalse(service.processEvent(getPortDown(1), ""));
        assertFalse(service.processEvent(getPortUp(1), ""));
        assertTrue(service.getPortInfos().isEmpty());
        checkPortInfos(getPortDown(1));
        assertTrue(service.getPortInfos().isEmpty());
        checkPortInfos(getPortUp(1));
    }

    @Test
    public void extendCoolingDown() {
        Instant downEvent = Instant.now();
        Instant firstTick = downEvent.plusMillis(1100);
        Instant upEvent = firstTick.plusSeconds(2);
        Instant secondTick = firstTick.plusSeconds(6);
        Instant thirdTick = firstTick.plusSeconds(8);
        when(clock.instant()).thenReturn(downEvent, firstTick, upEvent, secondTick, thirdTick);
        assertFalse(service.processEvent(getPortDown(1), ""));
        checkPortInfos(getPortDown(1));
        assertFalse(service.processEvent(getPortUp(1), ""));
        assertTrue(service.getPortInfos().isEmpty());
        checkPortInfos(getPortUp(1));
    }

    @Test
    public void veryLongUp() {
        Instant downEvent = Instant.now();
        Instant firstTick = downEvent.plusMillis(1100);
        Instant secondTick = firstTick.plusSeconds(6);
        Instant thirdTick = secondTick.plusSeconds(8);
        when(clock.instant()).thenReturn(downEvent, firstTick, secondTick, thirdTick);
        assertFalse(service.processEvent(getPortDown(1), ""));
        checkPortInfos(getPortDown(1));
        assertTrue(service.getPortInfos().isEmpty());
        assertTrue(service.getPortInfos().isEmpty());
        assertTrue(service.processEvent(getPortUp(1), ""));
    }

    @Test
    public void twoPorts() {
        Instant downEvent = Instant.now();
        Instant downEventPort2 = downEvent.plusMillis(200);
        Instant firstTick = downEvent.plusMillis(500);
        Instant secondTick = firstTick.plusSeconds(1);
        when(clock.instant()).thenReturn(downEvent, downEventPort2, firstTick, secondTick);
        assertFalse(service.processEvent(getPortDown(1), ""));
        assertFalse(service.processEvent(getPortDown(2), ""));
        assertTrue(service.getPortInfos().isEmpty());
        checkPortInfos(getPortDown(1), getPortDown(2));
    }

    private PortInfoData getPortUp(int port) {
        return new PortInfoData(new SwitchId("00:01"), port, PortChangeType.UP);
    }

    private PortInfoData getPortDown(int port) {
        return new PortInfoData(new SwitchId("00:01"), port, PortChangeType.DOWN);
    }

    private void checkPortInfos(PortInfoData... values) {
        assertEquals(new HashSet<>(Arrays.asList(values)),
                service.getPortInfos().stream().map(container -> container.portInfoData).collect(Collectors.toSet()));
    }
}
