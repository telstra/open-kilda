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
import org.openkilda.messaging.info.InfoData;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The main component for sending messages to internal kilda components. All sent operations will be performed
 * asynchronous and  wrapped into {@link CompletableFuture}. The main purpose of this class is to have one entrypoint
 * for sending messages and receiving them back in one place and doing it in non-blocking way.
 */
public interface MessagingChannel {

    /**
     * Sends the message to the specified topic and provides a response wrapped in {@link CompletableFuture}.
     * <p/>
     * Note: this type of request expects to receive back the response, if you don't need any responses please use
     * {@link MessagingChannel#send(String, Message)}
     *
     * @param topic topic where the message should be sent.
     * @param message data to be sent.
     * @return response for the request.
     */
    CompletableFuture<InfoData> sendAndGet(String topic, Message message);

    /**
     * Sends the message to the specified topic and collects all chunked responses for this request into the list.
     *
     * @param topic topic where the message should be sent.
     * @param message data to be sent.
     * @return response for the request.
     */
    CompletableFuture<List<InfoData>> sendAndGetChunked(String topic, Message message);

    /**
     * Sends the message to the specified topic without waiting for a response.
     *
     * @param topic topic where the message should be sent to.
     * @param message the data to be sent.
     */
    void send(String topic, Message message);

}
