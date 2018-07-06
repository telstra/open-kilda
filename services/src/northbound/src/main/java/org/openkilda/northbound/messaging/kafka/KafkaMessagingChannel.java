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

package org.openkilda.northbound.messaging.kafka;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.ChunkedInfoMessage;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.northbound.messaging.MessageProducer;
import org.openkilda.northbound.messaging.MessagingChannel;

import com.google.common.collect.Lists;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;

/**
 * Implementation of {@link MessagingChannel} for kafka.
 */
@Component
public class KafkaMessagingChannel implements MessagingChannel {

    private static final Logger logger = LoggerFactory.getLogger(KafkaMessagingChannel.class);

    @Value("${northbound.messages.expiration.minutes}")
    private int expiredTime;

    /**
     * Requests that are in progress of processing.
     */
    private Map<String, CompletableFuture<InfoData>> pendingRequests;
    private Map<String, CompletableFuture<List<InfoData>>> pendingChunkedRequests;

    /**
     * Collects messages that are not related to any known chain.
     */
    private Map<String, ChunkedInfoMessage> unlinkedChunks;

    /**
     * Chains of chunked messages, it is filling by messages one by one as soon as the next linked message is received.
     */
    private Map<String, List<ChunkedInfoMessage>> messagesChains;

    @Autowired
    private MessageProducer messageProducer;

    /**
     * Creates storages, that are able to remove outdated messages and requests.
     */
    @PostConstruct
    public void setUp() {
        pendingRequests = new PassiveExpiringMap<>(expiredTime, TimeUnit.MINUTES, new ConcurrentHashMap<>());
        pendingChunkedRequests = new PassiveExpiringMap<>(expiredTime, TimeUnit.MINUTES, new ConcurrentHashMap<>());

        unlinkedChunks = new PassiveExpiringMap<>(expiredTime, TimeUnit.MINUTES, new HashMap<>());
        messagesChains = new PassiveExpiringMap<>(expiredTime, TimeUnit.MINUTES, new HashMap<>());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<InfoData> sendAndGet(String topic, Message message) {
        CompletableFuture<InfoData> future = new CompletableFuture<>();
        pendingRequests.put(message.getCorrelationId(), future);
        messageProducer.send(topic, message);
        return future;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<List<InfoData>> sendAndGetChunked(String topic, Message message) {
        CompletableFuture<List<InfoData>> future = new CompletableFuture<>();
        pendingChunkedRequests.put(message.getCorrelationId(), future);
        messageProducer.send(topic, message);
        return future;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void send(String topic, Message message) {
        messageProducer.send(topic, message);
    }

    /**
     * Processes messages that come back to NB topics, usually messages come as a response to some request.
     * If this response is for pended request then such request will be marked as completed.
     * Messages might be chunked and not chunked. If chunked we need to wait until we receive the last one
     * and only then collect all responses and complete the request.
     * @param message received message.
     */
    void onResponse(Message message) {
        if (!isValid(message)) {
            logger.warn("Received invalid message: {}", message);
            return;
        }

        if (message instanceof ErrorMessage) {
            ErrorMessage error = (ErrorMessage) message;
            logger.error("Response message is error: {}", error);
            throw new MessageException(error);
        }

        if (message instanceof ChunkedInfoMessage) {
            processChunkedMessage((ChunkedInfoMessage) message);
        } else if (pendingRequests.containsKey(message.getCorrelationId())) {
            InfoMessage infoMessage = (InfoMessage) message;
            CompletableFuture<InfoData> future = pendingRequests.remove(message.getCorrelationId());
            future.complete(infoMessage.getData());
        }
    }

    /**
     * Performs searching and collecting all chunked messages into one chain if possible.
     */
    private synchronized void processChunkedMessage(ChunkedInfoMessage received) {
        List<ChunkedInfoMessage> chain;
        if (messagesChains.containsKey(received.getCorrelationId())) {
            chain = messagesChains.remove(received.getCorrelationId());
            chain.add(received);
        } else if (pendingChunkedRequests.containsKey(received.getCorrelationId())) {
            chain = Lists.newArrayList(received);
        } else {
            unlinkedChunks.put(received.getCorrelationId(), received);
            return;
        }

        ChunkedInfoMessage tail = received;
        // Check whether next messages for current chain is already received. If yes - add them into that chain.
        while (tail.getNextRequestId() != null && unlinkedChunks.containsKey(tail.getNextRequestId())) {
            chain.add(tail);
            tail = unlinkedChunks.remove(tail.getNextRequestId());
        }

        if (tail.getNextRequestId() == null) {
            // found last message in the chain
            completeChain(chain);
        } else {
            messagesChains.put(tail.getNextRequestId(), chain);
        }
    }

    /**
     * Completes request.
     */
    private void completeChain(List<ChunkedInfoMessage> chain) {
        List<InfoData> data = chain.stream()
                .map(ChunkedInfoMessage::getData)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        ChunkedInfoMessage firstMessage = chain.get(0);
        pendingChunkedRequests
                .remove(firstMessage.getCorrelationId())
                .complete(data);
    }

    /**
     * Checks whether a message has correlationId and has known type or not.
     */
    private boolean isValid(Message message) {
        if (StringUtils.isEmpty(message.getCorrelationId())) {
            return false;
        }

        return message instanceof InfoMessage || message instanceof ErrorMessage;
    }

}
