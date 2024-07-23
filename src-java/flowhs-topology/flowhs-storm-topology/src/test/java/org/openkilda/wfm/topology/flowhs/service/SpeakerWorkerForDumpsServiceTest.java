/* Copyright 2024 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.floodlight.api.response.ChunkedSpeakerDataResponse;
import org.openkilda.floodlight.api.response.SpeakerDataResponse;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.switches.DumpRulesForFlowHsRequest;
import org.openkilda.messaging.info.flow.FlowDumpResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.wfm.error.PipelineException;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class SpeakerWorkerForDumpsServiceTest {
    private static final String CORRELATION_ID_1 = "COR_1";
    private static final SwitchId SWITCH_1 = new SwitchId(1);

    private SpeakerCommandForDumpsCarrier carrier;
    private SpeakerWorkerForDumpsService service;

    @BeforeEach
    public void setUp() {
        carrier = mock(SpeakerCommandForDumpsCarrier.class);
        int chunkedMessagesExpirationMinutes = 10;
        service = new SpeakerWorkerForDumpsService(carrier, chunkedMessagesExpirationMinutes);
    }

    /**
     * Tests the sendCommand method by:
     * - Verifying that a command message is sent with the correct key.
     */
    @Test
    public void testSendCommand() throws PipelineException {
        String key = "test-key";
        CommandMessage request = mock(CommandMessage.class);
        service.sendCommand(key, request);
        verify(carrier).sendCommand(eq(key), eq(request));
    }

    /**
     * Tests the handling of a standard speaker response by:
     * - Sending a command and verifying it is sent correctly.
     * - Handling a response and ensuring it triggers the correct response handling.
     * - Verifying that the response is sent only once and no further interactions occur.
     */
    @Test
    public void testHandleResponse() throws PipelineException {
        String key = "test-key";
        CommandMessage request = mock(CommandMessage.class);
        MessageContext messageContext = new MessageContext(CORRELATION_ID_1);


        FlowDumpResponse flowDumpResponse = buildFlowDumpResponse(SWITCH_1, 1);
        SpeakerDataResponse response = new SpeakerDataResponse(messageContext, flowDumpResponse);

        service.sendCommand(key, request);
        verify(carrier).sendCommand(eq(key), eq(request));
        service.handleResponse(key, response);
        verify(carrier).sendResponse(eq(key), eq(response.getData()));

        verifyNoMoreInteractions(carrier);
        service.handleResponse(key, response);
        verify(carrier, times(1)).sendResponse(anyString(), any());
    }

    /**
     * Tests the complete handling of chunked responses by:
     * - Sending a command and verifying it is stored and sent.
     * - Handling multiple chunked responses, ensuring duplicates are ignored.
     * - Verifying that the service correctly unites and processes the complete set of chunked responses.
     * - Ensuring all internal caches are cleared after processing.
     */
    @Test
    public void testHandleChunkedResponseComplete() throws PipelineException,
            NoSuchFieldException, IllegalAccessException {
        String key = "test-key";
        CommandMessage request = mock(CommandMessage.class);
        DumpRulesForFlowHsRequest data = new DumpRulesForFlowHsRequest(SWITCH_1);
        when(request.getData()).thenReturn(data);

        service.sendCommand(key, request);
        verify(carrier).sendCommand(eq(key), eq(request));

        List<ChunkedSpeakerDataResponse> chinkedList = ChunkedSpeakerDataResponse.createChunkedList(Lists.newArrayList(
                        buildFlowDumpResponse(SWITCH_1, 1),
                        buildFlowDumpResponse(SWITCH_1, 2),
                        buildFlowDumpResponse(SWITCH_1, 2)),
                new MessageContext(CORRELATION_ID_1));

        // Access and track the state of messagesChains
        Field messagesChainsField = SpeakerWorkerForDumpsService.class.getDeclaredField("messagesChains");
        messagesChainsField.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, List<ChunkedSpeakerDataResponse>> messagesChains =
                (Map<String, List<ChunkedSpeakerDataResponse>>) messagesChainsField.get(service);

        // handle first message response
        service.handleChunkedResponse(key, chinkedList.get(0));
        Assertions.assertNotNull(messagesChains.get(key));
        Assertions.assertEquals(chinkedList.get(0), messagesChains.get(key).get(0));
        verifyNoMoreInteractions(carrier);

        // check handling for duplicate messages
        service.handleChunkedResponse(key, chinkedList.get(0));
        Assertions.assertNotNull(messagesChains.get(key));
        Assertions.assertEquals(chinkedList.get(0), messagesChains.get(key).get(0));
        Assertions.assertEquals(1, messagesChains.size());
        verifyNoMoreInteractions(carrier);

        // handle second message response
        service.handleChunkedResponse(key, chinkedList.get(1));
        Assertions.assertNotNull(messagesChains.get(key));
        Assertions.assertEquals(2, messagesChains.get(key).size());
        verifyNoMoreInteractions(carrier);

        // handle third message response
        service.handleChunkedResponse(key, chinkedList.get(2));
        Assertions.assertNull(messagesChains.get(key));

        FlowDumpResponse expectedResult = FlowDumpResponse.unite(chinkedList.stream().map(SpeakerDataResponse::getData)
                .map(FlowDumpResponse.class::cast).collect(Collectors.toList()));

        verify(carrier, times(1)).sendResponse(key, expectedResult);
        verifyNoMoreInteractions(carrier);

        //check that all caches in SpeakerWorkerForDumpsService are empty

        Assertions.assertTrue(messagesChains.isEmpty());
        Field chunkedMessageIdsPerRequestField =
                SpeakerWorkerForDumpsService.class.getDeclaredField("chunkedMessageIdsPerRequest");
        chunkedMessageIdsPerRequestField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Set<String>> chunkedMessageIdsPerRequest =
                (Map<String, Set<String>>) chunkedMessageIdsPerRequestField.get(service);
        Assertions.assertTrue(chunkedMessageIdsPerRequest.isEmpty());

        Field keyToRequestField = SpeakerWorkerForDumpsService.class.getDeclaredField("keyToRequest");
        keyToRequestField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, CommandMessage> keyToRequest
                = (Map<String, CommandMessage>) keyToRequestField.get(service);
        Assertions.assertTrue(keyToRequest.isEmpty());
    }

    /**
     * Tests the handleTimeout method to ensure it correctly handles timeout events by:
     * - Removing the timed-out request from internal caches.
     * - Sending an error response for each timed-out request.
     * This test manually populates the internal maps with keys and simulates timeouts.
     */
    @Test
    public void handleTimeout() throws PipelineException, NoSuchFieldException, IllegalAccessException {
        String key1 = "Key1";
        String key2 = "Key2";
        String key3 = "Key3";
        String key4 = "Key4";
        //populate caches
        Field chunkedMessageIdsPerRequestField
                = SpeakerWorkerForDumpsService.class.getDeclaredField("chunkedMessageIdsPerRequest");
        chunkedMessageIdsPerRequestField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Set<String>> chunkedMessageIdsPerRequest =
                (Map<String, Set<String>>) chunkedMessageIdsPerRequestField.get(service);
        chunkedMessageIdsPerRequest.put(key1, Collections.emptySet());
        chunkedMessageIdsPerRequest.put(key2, Collections.emptySet());
        chunkedMessageIdsPerRequest.put(key3, Collections.emptySet());
        chunkedMessageIdsPerRequest.put(key4, Collections.emptySet());

        Field keyToRequestField = SpeakerWorkerForDumpsService.class.getDeclaredField("keyToRequest");
        keyToRequestField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, CommandMessage> keyToRequest =
                (Map<String, CommandMessage>) keyToRequestField.get(service);
        keyToRequest.put(key1, mock(CommandMessage.class));
        keyToRequest.put(key2, mock(CommandMessage.class));
        keyToRequest.put(key3, mock(CommandMessage.class));
        keyToRequest.put(key4, mock(CommandMessage.class));

        service.handleTimeout(key1);
        Assertions.assertNull(keyToRequest.get(key1));
        Assertions.assertNull(chunkedMessageIdsPerRequest.get(key1));

        service.handleTimeout(key2);
        service.handleTimeout(key3);
        service.handleTimeout(key4);
        Assertions.assertTrue(keyToRequest.isEmpty());
        Assertions.assertTrue(chunkedMessageIdsPerRequest.isEmpty());
    }

    private FlowDumpResponse buildFlowDumpResponse(SwitchId switchId, long cookie) {
        return new FlowDumpResponse(Lists.newArrayList(buildFlowSpeakerData(cookie)), switchId);
    }

    private FlowSpeakerData buildFlowSpeakerData(long cookie) {
        return FlowSpeakerData.builder()
                .cookie(new FlowSegmentCookie(cookie))
                .build();
    }
}
