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

package org.openkilda.wfm.topology.flowmonitoring.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.openkilda.model.SwitchId;
import org.openkilda.server42.messaging.FlowDirection;
import org.openkilda.wfm.topology.flowmonitoring.model.Link;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class CalculateFlowLatencyServiceTest {

    private static final String FLOW_ID = "flow-id";
    private static final Link FIRST_LINK = Link.builder()
            .srcSwitchId(new SwitchId("1"))
            .srcPort(1)
            .destSwitchId(new SwitchId("2"))
            .destPort(2)
            .build();
    private static final Link SECOND_LINK = Link.builder()
            .srcSwitchId(new SwitchId("2"))
            .srcPort(3)
            .destSwitchId(new SwitchId("3"))
            .destPort(4)
            .build();
    private static final List<Link> FLOW_PATH = Arrays.asList(FIRST_LINK, SECOND_LINK);

    @Mock
    private FlowCacheBoltCarrier carrier;
    private CalculateFlowLatencyService service;

    @Before
    public void setup() {
        service = new CalculateFlowLatencyService(carrier);
    }

    @Test
    public void shouldSendLinkRequests() {
        service.handleCalculateFlowLatencyRequest(FLOW_ID, FlowDirection.FORWARD, FLOW_PATH);

        verify(carrier).emitGetLinkLatencyRequest(eq(FLOW_ID), any(String.class), eq(FIRST_LINK));
        verify(carrier).emitGetLinkLatencyRequest(eq(FLOW_ID), any(String.class), eq(SECOND_LINK));

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void shouldSendCalculatedResponse() {
        service.handleCalculateFlowLatencyRequest(FLOW_ID, FlowDirection.FORWARD, FLOW_PATH);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(carrier).emitGetLinkLatencyRequest(eq(FLOW_ID), captor.capture(), eq(FIRST_LINK));
        String requestId = captor.getValue();
        verify(carrier).emitGetLinkLatencyRequest(eq(FLOW_ID), eq(requestId), eq(SECOND_LINK));

        Duration latency1 = Duration.ofMillis(10);
        Duration latency2 = Duration.ofMillis(20);
        service.handleGetLinkLatencyResponse(requestId, FIRST_LINK, latency1);
        service.handleGetLinkLatencyResponse(requestId, SECOND_LINK, latency2);

        verify(carrier).emitCheckFlowLatencyRequest(FLOW_ID, FlowDirection.FORWARD, latency1.plus(latency2));
        verify(carrier).emitLatencyStats(FLOW_ID, FlowDirection.FORWARD, latency1.plus(latency2));
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void shouldNotSendCalculatedResponseForNotFinishedRequests() {
        service.handleCalculateFlowLatencyRequest(FLOW_ID, FlowDirection.FORWARD, FLOW_PATH);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(carrier).emitGetLinkLatencyRequest(eq(FLOW_ID), captor.capture(), eq(FIRST_LINK));
        String requestId = captor.getValue();
        verify(carrier).emitGetLinkLatencyRequest(eq(FLOW_ID), eq(requestId), eq(SECOND_LINK));

        Duration latency = Duration.ofMillis(10);
        for (int i = 0; i < 3; i++) {
            service.handleGetLinkLatencyResponse(requestId, FIRST_LINK, latency);
        }
        verifyNoMoreInteractions(carrier);
    }
}
