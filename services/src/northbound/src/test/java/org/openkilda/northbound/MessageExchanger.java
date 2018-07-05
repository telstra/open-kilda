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
import org.openkilda.northbound.messaging.MessageConsumer;
import org.openkilda.northbound.messaging.MessageProducer;

import java.util.HashMap;
import java.util.Map;

/**
 * Message producer/consumer implementation for testing purposes. Processes all send/poll operations and
 * sends back prepared in advance responses by specified request id (correlation id).
 */
public class MessageExchanger implements MessageConsumer<Message>, MessageProducer {

    private Map<String, Message> pendingResponses = new HashMap<>();

    public MessageExchanger() { }

    public MessageExchanger(Message response, String correlationId) {
        pendingResponses.put(correlationId, response);
    }

    @Override
    public Message poll(String correlationId) {
        return pendingResponses.remove(correlationId);
    }

    @Override
    public void clear() {
    }

    @Override
    public void send(String topic, Message message) {
        final String requestId = message.getCorrelationId();
        if (!pendingResponses.containsKey(requestId)) {
            throw new IllegalStateException(String.format(
                    "There is no pending response for request \"%s\"", requestId));
        }
    }

    @Override
    public void onResponse(Message message) {
    }

    public void mockResponse(Message message) {
        pendingResponses.put(message.getCorrelationId(), message);
    }

    public void resetMockedResponses() {
        pendingResponses.clear();
    }
}
