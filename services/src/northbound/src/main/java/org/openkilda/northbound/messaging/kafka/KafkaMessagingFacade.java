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
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.northbound.messaging.MessageProducer;
import org.openkilda.northbound.messaging.MessagingFacade;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The main component for all operations with kafka. All sent operations will be performed asynchronous and
 * wrapped into {@link CompletableFuture}. The main purpose of this class is to have one entrypoint for
 * sending messages and receiving them back in one place and doing it in non-blocking way.
 */
@Component
public class KafkaMessagingFacade implements MessagingFacade {

    private static final Logger logger = LoggerFactory.getLogger(KafkaMessagingFacade.class);

    /**
     * Requests that are in progress of processing.
     */
    private final Map<String, CompletableFuture<InfoMessage>> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<List<ChunkedInfoMessage>>> pendingChunkedRequests =
            new ConcurrentHashMap<>();

    /**
     * Collects messages that are not related to any known chain.
     */
    private final Map<String, ChunkedInfoMessage> chunkedMessages = new ConcurrentHashMap<>();

    /**
     * Chains of chunked messages, it is filling by messages one by one as soon as the next linked message is received.
     */
    private final List<LinkedList<ChunkedInfoMessage>> messagesChains = new ArrayList<>();

    @Autowired
    private MessageProducer messageProducer;

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<InfoMessage> sendAndGet(String topic, Message message) {
        CompletableFuture<InfoMessage> future = new CompletableFuture<>();
        pendingRequests.put(message.getCorrelationId(), future);
        messageProducer.send(topic, message);
        return future;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<List<ChunkedInfoMessage>> sendAndGetChunked(String topic, Message message) {
        CompletableFuture<List<ChunkedInfoMessage>> future = new CompletableFuture<>();
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
     * If this response is for the pended request then such request will be marked as completed.
     * Messages might be chunked and not chunked. If chunked we need to wait until we receive the last one
     * and only then collect all responses and complete the request.
     * @param message received message.
     */
    void onResponse(Message message) {
        if (!isValid(message)) {
            logger.warn("Received invalid message: {}", message);
            return;
        } else if (message instanceof ErrorMessage) {
            ErrorMessage error = (ErrorMessage) message;
            logger.error("Response message is error: {}", error);
            throw new MessageException(error);
        }

        InfoMessage infoMessage = (InfoMessage) message;
        if (infoMessage instanceof ChunkedInfoMessage) {
            ChunkedInfoMessage chunked = (ChunkedInfoMessage) infoMessage;
            processChunkedMessage(chunked);
        } else {
            CompletableFuture<InfoMessage> request = pendingRequests.remove(infoMessage.getCorrelationId());
            if (request != null) {
                request.complete(infoMessage);
            } else {
                logger.warn("Received non-pending message: {}", infoMessage);
            }
        }
    }

    /**
     * Tries to find and collect all chunked messages into one chain.
     */
    private void processChunkedMessage(final ChunkedInfoMessage received) {
        ChunkedInfoMessage message = received;

        LinkedList<ChunkedInfoMessage> currentChain = messagesChains.stream()
                .filter(chain -> chain.getLast().getNextRequestId().equals(received.getCorrelationId()))
                .findAny()
                .orElse(null);

        while (message != null) {
            final String correlationId = message.getCorrelationId();
            ChunkedInfoMessage nextMessage = null;

            if (currentChain != null) {
                currentChain.add(message);
            } else {
                if (pendingChunkedRequests.containsKey(correlationId)) {
                    logger.trace("Received first message of the chain {}", correlationId);
                    LinkedList<ChunkedInfoMessage> chain = new LinkedList<>();
                    chain.add(message);
                    messagesChains.add(chain);

                    currentChain = chain;
                } else {
                    chunkedMessages.put(correlationId, message);
                }
            }

            if (currentChain != null) {
                final String nextCorrelationId = message.getNextRequestId();
                if (StringUtils.isEmpty(nextCorrelationId)) {
                    logger.debug("Found last message in the chain for request {}",
                            currentChain.getFirst().getCorrelationId());
                    completeChain(currentChain);
                } else if (chunkedMessages.containsKey(nextCorrelationId)) {
                    logger.trace("Message {} is already received", nextCorrelationId);
                    nextMessage = chunkedMessages.remove(message.getNextRequestId());
                }
            }
            message = nextMessage;
        }
    }

    private void completeChain(LinkedList<ChunkedInfoMessage> chain) {
        String requestId = chain.getFirst().getCorrelationId();
        CompletableFuture<List<ChunkedInfoMessage>> future = pendingChunkedRequests.remove(requestId);
        future.complete(chain);

        messagesChains.remove(chain);
    }

    private boolean isValid(Message message) {
        if (StringUtils.isEmpty(message.getCorrelationId())) {
            return false;
        }

        return message instanceof InfoMessage || message instanceof ErrorMessage;
    }

}
