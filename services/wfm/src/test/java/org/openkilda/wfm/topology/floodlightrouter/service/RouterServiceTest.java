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

package org.openkilda.wfm.topology.floodlightrouter.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.AliveResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.model.SwitchId;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mapstruct.ap.internal.util.Collections;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RouterServiceTest {
    private static final String REGION_ONE = "one";
    private static final String REGION_TWO = "two";

    private RouterService service;

    @Mock
    private FloodlightTracker speakerTracker;

    @Mock
    private MessageSender carrier;

    @Before
    public void setUp() {
        service = new RouterService(speakerTracker);
    }

    @Test
    public void testPeriodicProcessing() {
        when(speakerTracker.getRegionsForAliveRequest()).thenReturn(Collections.asSet(REGION_ONE, REGION_TWO));

        service.doPeriodicProcessing(carrier);

        verify(speakerTracker).handleAliveExpiration(carrier);
        verify(speakerTracker).getRegionsForAliveRequest();
        verifyNoMoreInteractions(speakerTracker);

        verify(carrier).emitSpeakerAliveRequest(REGION_ONE);
        verify(carrier).emitSpeakerAliveRequest(REGION_TWO);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void testSpeakerResponse() {
        long remoteTimestamp = 1000L;
        final SwitchId switchId = new SwitchId(1001);
        InfoData payload = new PortInfoData(switchId, 1, PortChangeType.UP);
        Message message = new InfoMessage(payload, remoteTimestamp, "unit-test", REGION_ONE);

        when(speakerTracker.updateSwitchRegion(switchId, REGION_ONE)).thenReturn(true);
        when(speakerTracker.handleAliveResponse(REGION_ONE, remoteTimestamp)).thenReturn(true);

        service.processSpeakerDiscoResponse(carrier, message);

        verify(speakerTracker).handleAliveResponse(REGION_ONE, remoteTimestamp);
        verify(speakerTracker).updateSwitchRegion(switchId, REGION_ONE);
        verifyNoMoreInteractions(speakerTracker);

        verify(carrier).emitRegionNotification(Mockito.eq(new SwitchMapping(switchId, REGION_ONE)));
        verify(carrier).emitNetworkDumpRequest(REGION_ONE);
        verify(carrier).emitControllerMessage(Mockito.eq(message));
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void testConsecutiveSpeakerResponse() {
        long remoteTimestamp = 1000L;
        final SwitchId switchId = new SwitchId(1001);
        InfoData payload = new PortInfoData(switchId, 1, PortChangeType.UP);
        Message message = new InfoMessage(payload, remoteTimestamp, "unit-test", REGION_ONE);

        when(speakerTracker.updateSwitchRegion(switchId, REGION_ONE)).thenReturn(false);
        when(speakerTracker.handleAliveResponse(REGION_ONE, remoteTimestamp)).thenReturn(false);

        service.processSpeakerDiscoResponse(carrier, message);

        verify(speakerTracker).handleAliveResponse(REGION_ONE, remoteTimestamp);
        verify(speakerTracker).updateSwitchRegion(switchId, REGION_ONE);
        verifyNoMoreInteractions(speakerTracker);

        verify(carrier).emitControllerMessage(Mockito.eq(message));
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void testAliveResponseWithErrorMarker() {
        long remoteTimestamp = 1000L;
        InfoData payload = new AliveResponse(REGION_ONE, 1);
        Message message = new InfoMessage(payload, remoteTimestamp, "unit-test", REGION_ONE);

        when(speakerTracker.handleAliveResponse(REGION_ONE, remoteTimestamp)).thenReturn(false);

        service.processSpeakerDiscoResponse(carrier, message);
        verify(speakerTracker).handleAliveResponse(REGION_ONE, remoteTimestamp);
        verifyNoMoreInteractions(speakerTracker);

        verify(carrier).emitNetworkDumpRequest(REGION_ONE);
        verifyNoMoreInteractions(carrier);
    }
}
