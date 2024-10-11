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

package org.openkilda.floodlight.api.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.MessageData;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChunkedSpeakerDataResponseTest {

    /**
     * Tests the createChunkedList method to ensure it throws a NullPointerException
     * when passed a null collection. This verifies that the method correctly handles
     * null input by throwing the appropriate exception.
     */
    @Test
    void testCreateChunkedListWithNullCollection() {

        MessageContext messageContext = new MessageContext("Correlation Id");

        // Act & Assert
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                        ChunkedSpeakerDataResponse.createChunkedList(null, messageContext),
                "Expected createChunkedList() to throw, but it didn't"
        );
        assertEquals("data is marked non-null but is null", thrown.getMessage(),
                "Exception message should match");
    }

    /**
     * Tests the createChunkedList method to ensure it throws a NullPointerException
     * when passed an empty collection. This verifies that the method correctly handles
     * empty collections by throwing the appropriate exception.
     */
    @Test
    void testCreateChunkedListWithEmptyCollection() {
        // Arrange
        MessageContext messageContext = new MessageContext("Correlation Id");

        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                        ChunkedSpeakerDataResponse.createChunkedList(Collections.emptyList(), messageContext),
                "Expected createChunkedList() to throw, but it didn't"
        );
        assertEquals("data is marked non-null but is null", thrown.getMessage(),
                "Exception message should match");
    }

    /**
     * Tests the createChunkedList method with a collection containing a single item.
     * Verifies that the method correctly processes the single item and sets the
     * messageId and totalMessages fields appropriately.
     */
    @Test
    void testCreateChunkedListWithOneItem() {
        // Arrange
        MessageContext messageContext = new MessageContext("Correlation Id");
        MessageData mockData = mock(MessageData.class);

        // Act
        List<ChunkedSpeakerDataResponse> result
                = ChunkedSpeakerDataResponse.createChunkedList(Collections.singletonList(mockData), messageContext);

        // Assert
        assertEquals(1, result.size(), "Result list should contain one element");
        ChunkedSpeakerDataResponse response = result.get(0);
        assertSame(mockData, response.getData(), "Data should be the same as the input");
        assertEquals(1, response.getTotalMessages(), "Total messages should be 1");
        assertEquals("0 : Correlation Id", response.getMessageId(),
                "Message ID should be '0 : Correlation Id'");
    }

    /**
     * Tests the createChunkedList method with a collection containing multiple items.
     * Verifies that the method correctly processes each item, setting the messageId
     * and totalMessages fields appropriately for each element in the collection.
     */
    @Test
    void testCreateChunkedListWithMultipleItems() {
        // Arrange
        MessageContext messageContext = new MessageContext("mockCorrelationId");
        MessageData mockData1 = mock(MessageData.class);
        MessageData mockData2 = mock(MessageData.class);

        List<MessageData> dataCollection = new ArrayList<>();
        dataCollection.add(mockData1);
        dataCollection.add(mockData2);

        // Act
        List<ChunkedSpeakerDataResponse> result
                = ChunkedSpeakerDataResponse.createChunkedList(dataCollection, messageContext);

        // Assert
        assertEquals(2, result.size(), "Result list should contain two elements");

        ChunkedSpeakerDataResponse response1 = result.get(0);
        assertSame(mockData1, response1.getData(), "First response data should be mockData1");
        assertEquals(2, response1.getTotalMessages(), "Total messages should be 2");
        assertEquals("0 : mockCorrelationId", response1.getMessageId(),
                "First message ID should be '0 : mockCorrelationId'");

        ChunkedSpeakerDataResponse response2 = result.get(1);
        assertSame(mockData2, response2.getData(), "Second response data should be mockData2");
        assertEquals(2, response2.getTotalMessages(), "Total messages should be 2");
        assertEquals("1 : mockCorrelationId", response2.getMessageId(),
                "Second message ID should be '1 : mockCorrelationId'");
    }
}
