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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.openkilda.wfm.topology.isllatency.service.OneWayLatencyManipulationService.ONE_MILLISECOND_IN_NANOSECONDS;
import static org.openkilda.wfm.topology.isllatency.service.OneWayLatencyManipulationService.ONE_WAY_LATENCY_MULTIPLIER;

import org.openkilda.messaging.info.event.IslOneWayLatency;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.isllatency.carriers.OneWayLatencyManipulationCarrier;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class OneWayManipulationServiceTest {
    private static final SwitchId SWITCH_ID_1 = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId SWITCH_ID_2 = new SwitchId("00:00:00:00:00:00:00:02");
    private static final int PORT_1 = 1;
    private static final int PORT_2 = 2;


    private OneWayLatencyManipulationCarrier carrier;
    private OneWayLatencyManipulationService oneWayLatencyManipulationService;

    @Before
    public void setup() {
        carrier = mock(OneWayLatencyManipulationCarrier.class);
        oneWayLatencyManipulationService = new OneWayLatencyManipulationService(carrier);
    }

    @Test()
    public void handleOneWayLatencyNegativeLatencyTest() throws PipelineException {
        runHandleOneWayLatencyTest(-1, ONE_MILLISECOND_IN_NANOSECONDS);
    }

    @Test()
    public void handleOneWayLatencyZeroLatencyTest() throws PipelineException {
        runHandleOneWayLatencyTest(0, ONE_MILLISECOND_IN_NANOSECONDS);
    }

    @Test()
    public void handleOneWayLatencyPositiveLatencyTest() throws PipelineException {
        runHandleOneWayLatencyTest(12, 12 * ONE_WAY_LATENCY_MULTIPLIER);
    }

    private void runHandleOneWayLatencyTest(long initialLatency, long expectedLatency) throws PipelineException {
        oneWayLatencyManipulationService.handleOneWayLatency(createIslOneWayLatency(initialLatency));

        ArgumentCaptor<IslOneWayLatency> captor = ArgumentCaptor.forClass(IslOneWayLatency.class);
        verify(carrier, times(1)).emitIslOneWayLatency(captor.capture());

        assertIslOneWayLatency(expectedLatency, captor.getValue());
    }

    private void assertIslOneWayLatency(long expectedLatency, IslOneWayLatency actual) {
        assertEquals(SWITCH_ID_1, actual.getSrcSwitchId());
        assertEquals(PORT_1, actual.getSrcPortNo());
        assertEquals(SWITCH_ID_2, actual.getDstSwitchId());
        assertEquals(PORT_2, actual.getDstPortNo());
        assertEquals(expectedLatency, actual.getLatency());
    }

    private IslOneWayLatency createIslOneWayLatency(long latency) {
        return new IslOneWayLatency(SWITCH_ID_1, PORT_1, SWITCH_ID_2, PORT_2, latency, 0L);
    }
}
