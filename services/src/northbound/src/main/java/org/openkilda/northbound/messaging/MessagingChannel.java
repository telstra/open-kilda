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

package org.openkilda.northbound.messaging;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.ChunkedInfoMessage;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.northbound.messaging.kafka.KafkaMessagingChannel;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface MessagingChannel {

    /**
     * Sends message to the specified topic and provides response wrapped in {@link CompletableFuture}.
     * Note: this type of request expects to receive back the response,
     * if you don't need any responses please use {@link KafkaMessagingChannel#send(String, Message)}
     * @param topic kafka topic where message should be sent.
     * @param message data to be sent.
     * @return response for the request.
     */
    CompletableFuture<InfoMessage> sendAndGet(String topic, Message message);

    /**
     * Sends message to the specified topic and collects all chunked responses for this request into the list.
     * @param topic kafka topic where message should be sent.
     * @param message data to be sent.
     * @return response for the request.
     */
    CompletableFuture<List<ChunkedInfoMessage>> sendAndGetChunked(String topic, Message message);

    /**
     * Sends message to the specified topic without waiting for the response.
     * @param topic kafka topic where message should be sent.
     * @param message data to be sent.
     */
    void send(String topic, Message message);

}
