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

import static org.junit.Assert.assertEquals;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.ChunkedInfoMessage;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.northbound.config.KafkaConfig;
import org.openkilda.northbound.messaging.MessageProducer;
import org.openkilda.northbound.messaging.MessagingChannel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(SpringRunner.class)
public class KafkaMessagingChannelTest {
    private static final Set<ChunkedInfoMessage> CHUNKED_RESPONSES = new HashSet<>();
    private static final Set<Message> RESPONSES = new HashSet<>();
    private static final String MAIN_TOPIC = "topic";
    private static final String CHUNKED_TOPIC = "chunked";

    @Autowired
    private KafkaMessagingChannel messagingChannel;

    @Before
    public void reset() {
        CHUNKED_RESPONSES.clear();
        RESPONSES.clear();
    }

    @Test
    public void shouldReturnCompleteResponse() throws TimeoutException, InterruptedException, ExecutionException {
        final String requestId = UUID.randomUUID().toString();
        final long time = System.currentTimeMillis();
        InfoMessage message = new InfoMessage(null, time, requestId);
        RESPONSES.add(message);

        CompletableFuture<InfoMessage> response =
                messagingChannel.sendAndGet(MAIN_TOPIC, new Message(time, requestId));

        InfoMessage result = response.get(1, TimeUnit.SECONDS);
        assertEquals(message.getCorrelationId(), result.getCorrelationId());
        assertEquals(message.getTimestamp(), result.getTimestamp());
    }

    @Test
    public void shouldReturnCompletedChunked() throws TimeoutException, InterruptedException, ExecutionException {
        final String requestId = UUID.randomUUID().toString();
        final long timestamp = System.currentTimeMillis();
        final int messagesAmount = 10000;

        prepareChunkedResponses(requestId, timestamp, messagesAmount);
        Message request = new Message(timestamp, requestId);

        CompletableFuture<List<ChunkedInfoMessage>> future = messagingChannel.sendAndGetChunked(CHUNKED_TOPIC, request);
        List<ChunkedInfoMessage> result = future.get(10, TimeUnit.SECONDS);
        assertEquals(messagesAmount, result.size());
        assertEquals(requestId, result.get(0).getCorrelationId());
    }

    @Test
    public void shouldReturnCompletedChunkedSingleItem() throws Exception {
        final String requestId = UUID.randomUUID().toString();
        final long timestamp = System.currentTimeMillis();
        final int responses = 1;

        prepareChunkedResponses(requestId, timestamp, responses);
        Message request = new Message(timestamp, requestId);

        CompletableFuture<List<ChunkedInfoMessage>> future = messagingChannel.sendAndGetChunked(CHUNKED_TOPIC, request);
        List<ChunkedInfoMessage> result = future.get(60, TimeUnit.SECONDS);
        assertEquals(responses, result.size());
    }

    private void prepareChunkedResponses(String requestId, long timestamp, int size) {
        String prevRequestId = requestId;
        for (int i = 0; i < size; i++) {
            ChunkedInfoMessage response;
            if (i < size - 1) {
                String nextRequestId = UUID.randomUUID().toString();
                response = new ChunkedInfoMessage(null, timestamp, prevRequestId, nextRequestId);
                prevRequestId = nextRequestId;
            } else {
                response = new ChunkedInfoMessage(null, timestamp, prevRequestId, null);
            }
            CHUNKED_RESPONSES.add(response);
        }
    }

    @TestConfiguration
    @Import(KafkaConfig.class)
    @PropertySource({"classpath:northbound.properties"})
    static class Config {
        @Bean
        public MessagingChannel messagingChannel() {
            return new KafkaMessagingChannel();
        }

        @Bean
        public MessageProducer messageProducer(KafkaMessagingChannel messagingChannel) {
            return new CustomMessageProducer(messagingChannel);
        }
    }

    private static class CustomMessageProducer implements MessageProducer {
        private KafkaMessagingChannel facade;

        CustomMessageProducer(KafkaMessagingChannel facade) {
            this.facade = facade;
        }

        @Override
        public void send(String topic, Message message) {
            if (CHUNKED_TOPIC.equals(topic)) {
                ExecutorService executor = Executors.newFixedThreadPool(1000);
                CHUNKED_RESPONSES.forEach(response ->
                        executor.submit(() -> facade.onResponse(response)));
            } else {
                RESPONSES.forEach(response -> facade.onResponse(response));
            }
        }

    }

}
