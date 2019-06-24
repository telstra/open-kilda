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

import static java.lang.Thread.sleep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.openkilda.model.IslStatus.INACTIVE;
import static org.openkilda.model.IslStatus.MOVED;

import org.openkilda.messaging.info.event.IslOneWayLatency;
import org.openkilda.messaging.info.event.IslRoundTripLatency;
import org.openkilda.messaging.info.event.IslStatusUpdateNotification;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.topology.isllatency.carriers.IslStatsCarrier;
import org.openkilda.wfm.topology.isllatency.model.LatencyRecord;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class IslStatsServiceTest {
    public static final int LATENCY_TIMEOUT = 6;
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final int PORT_1 = 1;
    public static final int PORT_2 = 2;
    public static final Endpoint FORWARD_DESTINATION = Endpoint.of(SWITCH_ID_2, PORT_2);
    public static final Endpoint REVERSE_DESTINATION = Endpoint.of(SWITCH_ID_1, PORT_1);

    private IslStatsCarrier carrier;
    private InOrder inOrderCarrier;
    private IslStatsService islStatsService;

    @Before
    public void setup() {
        carrier = mock(IslStatsCarrier.class);
        inOrderCarrier = inOrder(carrier);
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

    @Test
    public void handleRoundTripLatencyTest() {
        // RTL:    ...X.X.X.X.X.X.X.X.X.X.....
        // OneWay: ...........................

        Instant time = Clock.systemUTC().instant();

        for (int i = 0; i < 10; i++) {
            sendForwardRoundTripLatency(i, time);
            inOrderCarrier.verify(carrier, times(1))
                    .emitLatency(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2, i, time.toEpochMilli());

            time = time.plusSeconds(LATENCY_TIMEOUT / 2);
        }
    }

    @Test
    public void handleRoundTripLatencyAndOneWayLatencyTest() {
        // RTL:    ...X.X.X.X.X.X.X.X.X.X.....
        // OneWay: ..X.X.X.X.X.X.X.X.X.X......

        List<LatencyRecord> buffer = new ArrayList<>();

        Instant time = Clock.systemUTC().instant();

        for (int i = 0; i < 10; i++) {
            sendForwardOneWayLatency(i + 10000, time);
            time = time.plusMillis(10);

            sendForwardRoundTripLatency(i, time);
            buffer.add(new LatencyRecord(i, time.toEpochMilli()));

            time = time.plusSeconds(LATENCY_TIMEOUT / 2);
        }

        assertEmitLatency(buffer);
    }

    @Test
    public void handleOneWayLatencyCollectsRecordsUntilTimeoutTest() {
        // RTL:     .....................
        // OneWay:  ..X.X.X.X.X.X.X.X....
        // Timeout: ...|_____________|...

        List<LatencyRecord> buffer = new ArrayList<>();
        Instant time = Clock.systemUTC().instant().minusSeconds(LATENCY_TIMEOUT).minusMillis(500);

        for (int i = 0; i <= LATENCY_TIMEOUT; i++) {
            sendForwardOneWayLatency(i, time);

            // waiting for timeout and collecting one way records
            verifyNoMoreInteractions(carrier);

            buffer.add(new LatencyRecord(i, time.toEpochMilli()));
            time = time.plusSeconds(1);

        }

        // first record is outdated and we must not emit it
        buffer.remove(0);

        // first record after timeout
        // we received enough latency records and all stored records will be flushed
        sendForwardOneWayLatency(100, time);
        buffer.add(new LatencyRecord(100, time.toEpochMilli()));

        // ISL down notification must flush one way latency storage
        assertEmitLatency(buffer);
    }

    @Test
    public void handleOneWayLatencyEmitByOneAfterTimeoutTest() {
        // RTL:     ...........................
        // OneWay:  ..X.X.X.X.X.X.X.X.X.X.X....
        // Timeout: ...|_____________|.........

        List<LatencyRecord> buffer = new ArrayList<>();
        Instant time = Clock.systemUTC().instant().minusSeconds(LATENCY_TIMEOUT).minusMillis(500);

        for (int i = 0; i <= LATENCY_TIMEOUT; i++) {
            sendForwardOneWayLatency(i, time);

            // waiting for timeout and collecting one way records
            verifyNoMoreInteractions(carrier);

            buffer.add(new LatencyRecord(i, time.toEpochMilli()));
            time = time.plusSeconds(1);

        }

        // first record is outdated and we must not emit it
        buffer.remove(0);

        // first record after timeout
        sendForwardOneWayLatency(100, time);
        buffer.add(new LatencyRecord(100, time.toEpochMilli()));
        time = time.plusSeconds(1);


        // all records after timeout will be emit immediately
        for (int i = 10; i < 13; i++) {
            sendForwardOneWayLatency(i, time);

            buffer.add(new LatencyRecord(i, time.toEpochMilli()));
            time = time.plusSeconds(1);
        }

        assertEmitLatency(buffer);
    }

    @Test
    public void handleOneWayLatencyEmitOnIslDownTest() {
        // RTL:      ...........................
        // OneWay:   ..X.X.X.X..................
        // Timeout:  ...|____________|..........
        // ISL down: .........^.................

        List<LatencyRecord> buffer = new ArrayList<>();
        Instant time = Clock.systemUTC().instant().minusSeconds(LATENCY_TIMEOUT).minusMillis(500);

        for (int i = 0; i < 4; i++) {
            sendForwardOneWayLatency(i, time);

            // waiting for timeout and collecting one way records
            verifyNoMoreInteractions(carrier);

            buffer.add(new LatencyRecord(i, time.toEpochMilli()));
            time = time.plusSeconds(1);
        }

        // first record is outdated and we must not emit it
        buffer.remove(0);

        islStatsService.handleIstStatusUpdateNotification(
                new IslStatusUpdateNotification(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2, INACTIVE));

        assertEmitLatency(buffer);
    }

    @Test
    public void handleOneWayLatencyIslMovedTest() {
        // RTL:       ...........................
        // OneWay:    ..X.X.X.X..................
        // Timeout:   ...|____________|..........
        // ISL moved: .........^.................

        Instant time = Clock.systemUTC().instant().minusSeconds(LATENCY_TIMEOUT).minusMillis(500);

        for (int i = 0; i < 4; i++) {
            sendForwardOneWayLatency(i, time);

            // waiting for timeout and collecting one way records
            verifyNoMoreInteractions(carrier);

            time = time.plusSeconds(1);
        }

        islStatsService.handleIstStatusUpdateNotification(
                new IslStatusUpdateNotification(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2, MOVED));

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void handleRoundTripLatencyForReverseIslTest() {
        // RTL:         ...........................
        // Reverse RTL: ...X.X.X.X.X.X.X.X.X.X.....
        // OneWay:      ..X.X.X.X.X.X.X.X.X.X......

        List<LatencyRecord> buffer = new ArrayList<>();

        Instant time = Clock.systemUTC().instant();

        for (int i = 0; i < 10; i++) {
            sendForwardOneWayLatency(i + 10000, time);
            time = time.plusMillis(10);

            sendReverseRoundTripLatency(i, time);

            time = time.plusSeconds(1);

            // next one way packet will get latency from reverse ISL. but we will get timestamp for one way packet
            buffer.add(new LatencyRecord(i, time.toEpochMilli()));
        }

        // we must emit reverse RTL on each one way packet. Last RTL packet was received after last one way packet
        // so we wouldn't emit it.
        buffer.remove(buffer.size() - 1);

        assertEmitLatency(buffer);
    }

    @Test
    public void handleOneWayLatencyUseForwardsRtlNotReverseRtlTest() {
        // RTL:         ...X.X.X.X.X.X.X.X.X.X.....
        // Reverse RTL: ...X.X.X.X.X.X.X.X.X.X.....
        // OneWay:      ..X.X.X.X.X.X.X.X.X.X......

        List<LatencyRecord> buffer = new ArrayList<>();

        Instant time = Clock.systemUTC().instant();

        for (int i = 0; i < 10; i++) {
            sendForwardOneWayLatency(i + 10000, time);
            time = time.plusMillis(10);

            sendForwardRoundTripLatency(i, time);
            sendReverseRoundTripLatency(i + 100, time);

            // we must emit only forward RTL
            buffer.add(new LatencyRecord(i, time.toEpochMilli()));

            time = time.plusSeconds(1);
        }

        assertEmitLatency(buffer);
    }

    @Test
    public void handleOneWayLatencyEmitOnEachIslDownTest() throws InterruptedException {
        // RTL:      ............................
        // OneWay:   ..X.X.X.X........X.X.X.X....
        // Timeout:  ...|_________|...|_________|
        // ISL down: .........^..............^...

        List<LatencyRecord> buffer = new ArrayList<>();
        Instant time = Clock.systemUTC().instant().minusSeconds(LATENCY_TIMEOUT).minusMillis(500);

        for (int i = 0; i < 4; i++) {
            sendForwardOneWayLatency(i, time);

            // waiting for timeout and collecting one way records
            verifyNoMoreInteractions(carrier);

            buffer.add(new LatencyRecord(i, time.toEpochMilli()));
            time = time.plusSeconds(1);
        }

        buffer.remove(0);
        islStatsService.handleIstStatusUpdateNotification(
                new IslStatusUpdateNotification(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2, INACTIVE));

        time = Clock.systemUTC().instant();

        int secondPartOfPacketsSize = 4;
        for (int i = 0; i < secondPartOfPacketsSize; i++) {
            sendForwardOneWayLatency(i + secondPartOfPacketsSize, time);

            buffer.add(new LatencyRecord(i + secondPartOfPacketsSize, time.toEpochMilli()));
            time = time.plusMillis(500);
            // need to real shift of system clock
            sleep(50);
        }

        islStatsService.handleIstStatusUpdateNotification(
                new IslStatusUpdateNotification(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2, INACTIVE));

        assertEmitLatency(buffer);
    }

    @Test
    public void handleOneWayLatencyEmitOnFirstIslDownTest() throws InterruptedException {
        // RTL:      ............................
        // OneWay:   ..X.X.X.X........X.X.X.X....
        // Timeout:  ...|_________|...|_________|
        // ISL down: .........^..................

        List<LatencyRecord> buffer = new ArrayList<>();
        Instant time = Clock.systemUTC().instant().minusSeconds(LATENCY_TIMEOUT).minusMillis(500);

        for (int i = 0; i < 4; i++) {
            sendForwardOneWayLatency(i, time);

            // waiting for timeout and collecting one way records
            verifyNoMoreInteractions(carrier);

            buffer.add(new LatencyRecord(i, time.toEpochMilli()));
            time = time.plusSeconds(1);
        }

        buffer.remove(0);
        islStatsService.handleIstStatusUpdateNotification(
                new IslStatusUpdateNotification(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2, INACTIVE));

        time = Clock.systemUTC().instant();

        int secondPartOfPacketsSize = 4;
        for (int i = 0; i < secondPartOfPacketsSize; i++) {
            sendForwardOneWayLatency(i + secondPartOfPacketsSize, time);
            // we do not add this record into buffer because we must not emit it

            time = time.plusMillis(500);
            // need to real shift of system clock
            sleep(50);
        }

        assertEmitLatency(buffer);
    }


    @Test
    public void handleOneWayLatencyNoEmitOnEachIslMoveTest() throws InterruptedException {
        // RTL:      ............................
        // OneWay:   ..X.X.X.X........X.X.X.X....
        // Timeout:  ...|_________|...|_________|
        // ISL move: .........^..............^...

        Instant time = Clock.systemUTC().instant().minusSeconds(LATENCY_TIMEOUT - 1);

        for (int i = 0; i < 4; i++) {
            sendForwardOneWayLatency(i, time);

            // waiting for timeout and collecting one way records

            time = time.plusSeconds(1);
        }

        islStatsService.handleIstStatusUpdateNotification(
                new IslStatusUpdateNotification(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2, MOVED));

        time = time.plusSeconds(LATENCY_TIMEOUT + 1);

        int secondPartOfPacketsSize = 4;
        for (int i = 0; i < secondPartOfPacketsSize; i++) {
            sendForwardOneWayLatency(i + secondPartOfPacketsSize, time);

            time = time.plusMillis(50);
            // need to real shift of system clock
            sleep(50);
        }

        islStatsService.handleIstStatusUpdateNotification(
                new IslStatusUpdateNotification(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2, MOVED));

        verifyNoMoreInteractions(carrier);
    }

    private void assertEmitLatency(List<LatencyRecord> expected) {
        ArgumentCaptor<Long> latencyCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> timestampCaptor = ArgumentCaptor.forClass(Long.class);

        verify(carrier, times(expected.size()))
                .emitLatency(eq(SWITCH_ID_1), eq(PORT_1), eq(SWITCH_ID_2), eq(PORT_2),
                        latencyCaptor.capture(), timestampCaptor.capture());

        assertLatencyRecords(expected, latencyCaptor.getAllValues(), timestampCaptor.getAllValues());
    }

    private void assertLatencyRecords(
            List<LatencyRecord> expected, List<Long> actualLatency, List<Long> actualTimestamp) {
        assertEquals(expected.size(), actualLatency.size());
        assertEquals(expected.size(), actualTimestamp.size());

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).getLatency(), (long) actualLatency.get(i));
            assertEquals(expected.get(i).getTimestamp(), (long) actualTimestamp.get(i));
        }
    }

    private void sendForwardRoundTripLatency(int latency, Instant time) {
        IslRoundTripLatency islRoundTripLatency = new IslRoundTripLatency(SWITCH_ID_1, PORT_1, latency, 0L);
        islStatsService.handleRoundTripLatencyMetric(time.toEpochMilli(), islRoundTripLatency, FORWARD_DESTINATION);
    }

    private void sendReverseRoundTripLatency(int latency, Instant time) {
        IslRoundTripLatency islRoundTripLatency = new IslRoundTripLatency(SWITCH_ID_2, PORT_2, latency, 0L);
        islStatsService.handleRoundTripLatencyMetric(time.toEpochMilli(), islRoundTripLatency, REVERSE_DESTINATION);
    }

    private void sendForwardOneWayLatency(int latency, Instant time) {
        IslOneWayLatency oneWayLatency = new IslOneWayLatency(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2, latency, 0L);
        islStatsService.handleOneWayLatencyMetric(time.toEpochMilli(), oneWayLatency);
    }
}
