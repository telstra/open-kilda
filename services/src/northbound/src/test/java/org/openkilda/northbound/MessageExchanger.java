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

package org.openkilda.northbound;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.northbound.messaging.MessagingChannel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Message producer/consumer implementation for testing purposes. Processes all send/poll operations and
 * sends back prepared in advance responses by specified request id (correlation id).
 */
public class MessageExchanger implements MessagingChannel {

    private Map<String, InfoData> pendingResponses = new HashMap<>();
    private Map<String, List<InfoData>> pendingChunkedResponses = new HashMap<>();

    public MessageExchanger() { }

    public MessageExchanger(InfoMessage response, String correlationId) {
        pendingResponses.put(correlationId, response.getData());
    }

    @Override
    public void send(String topic, Message message) {
        final String requestId = message.getCorrelationId();
        if (!pendingResponses.containsKey(requestId) && !pendingChunkedResponses.containsKey(requestId)) {
            throw new IllegalStateException(String.format(
                    "There is no pending response for request \"%s\"", requestId));
        }
    }

    @Override
    public CompletableFuture<InfoData> sendAndGet(String topic, Message message) {
        send(topic, message);

        return CompletableFuture.completedFuture(pendingResponses.remove(message.getCorrelationId()));
    }

    @Override
    public CompletableFuture<List<InfoData>> sendAndGetChunked(String topic, Message message) {
        send(topic, message);

        return CompletableFuture.completedFuture(pendingChunkedResponses.remove(message.getCorrelationId()));
    }

    public void mockResponse(InfoMessage message) {
        pendingResponses.put(message.getCorrelationId(), message.getData());
    }

    public void mockChunkedResponse(String requestId, List<InfoData> messages) {
        pendingChunkedResponses.put(requestId, messages);
    }

    public void resetMockedResponses() {
        pendingResponses.clear();
        pendingChunkedResponses.clear();
    }
}
