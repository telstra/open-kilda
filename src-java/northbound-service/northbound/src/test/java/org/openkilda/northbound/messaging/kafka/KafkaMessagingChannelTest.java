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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.ChunkedInfoMessage;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.config.KafkaConfig;
import org.openkilda.northbound.messaging.MessageProducer;
import org.openkilda.northbound.messaging.MessagingChannel;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SettableListenableFuture;

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
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessagingChannelTest.class);
    private static final Set<Message> CHUNKED_RESPONSES = new HashSet<>();
    private static final Set<Message> RESPONSES = new HashSet<>();
    private static final String MAIN_TOPIC = "topic";
    private static final String CHUNKED_TOPIC = "chunked";
    private static final String BROKEN_TOPIC = "broken";

    @Autowired
    private KafkaMessagingChannel messagingChannel;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void reset() {
        CHUNKED_RESPONSES.clear();
        RESPONSES.clear();
    }

    @Test
    public void shouldReturnCompletedResponse() throws TimeoutException, InterruptedException, ExecutionException {
        String requestId = UUID.randomUUID().toString();
        long time = System.currentTimeMillis();
        SwitchInfoData data = new SwitchInfoData(new SwitchId("00:00:00:00:00:01"), SwitchChangeType.ACTIVATED, null,
                "hostname", "description", "controller", false);
        InfoMessage expected = new InfoMessage(data, time, requestId);
        RESPONSES.add(expected);

        CompletableFuture<InfoData> response = messagingChannel.sendAndGet(MAIN_TOPIC, new Message(time, requestId));
        prepareResponses(MAIN_TOPIC);

        InfoData result = response.get(1, TimeUnit.SECONDS);
        assertEquals(data, result);
        assertTrue(messagingChannel.getPendingRequests().isEmpty());
    }

    @Test
    public void shouldReturnEmptyCompletedResponse() throws TimeoutException, InterruptedException, ExecutionException {
        String requestId = UUID.randomUUID().toString();
        long time = System.currentTimeMillis();

        InfoMessage expected = new InfoMessage(null, time, requestId);
        RESPONSES.add(expected);

        CompletableFuture<InfoData> response = messagingChannel.sendAndGet(MAIN_TOPIC, new Message(time, requestId));
        prepareResponses(MAIN_TOPIC);

        InfoData result = response.get(1, TimeUnit.SECONDS);
        assertNull(result);
        assertTrue(messagingChannel.getPendingRequests().isEmpty());
    }

    @Test
    public void shouldReturnCompletedChunked() throws TimeoutException, InterruptedException, ExecutionException {
        String requestId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();
        int messagesAmount = 10000;

        prepareChunkedResponses(requestId, timestamp, messagesAmount);
        Message request = new Message(timestamp, requestId);

        CompletableFuture<List<InfoData>> future = messagingChannel.sendAndGetChunked(CHUNKED_TOPIC, request);
        prepareResponses(CHUNKED_TOPIC);

        List<InfoData> result = future.get(10, TimeUnit.SECONDS);
        assertEquals(messagesAmount, result.size());

        assertTrue(messagingChannel.getPendingRequests().isEmpty());
    }

    @Test
    public void shouldReturnCompletedChunkedSingleItem() throws Exception {
        String requestId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();
        int responses = 1;

        prepareChunkedResponses(requestId, timestamp, responses);
        Message request = new Message(timestamp, requestId);

        CompletableFuture<List<InfoData>> future = messagingChannel.sendAndGetChunked(CHUNKED_TOPIC, request);
        prepareResponses(CHUNKED_TOPIC);
        List<InfoData> result = future.get(10, TimeUnit.SECONDS);

        assertEquals(responses, result.size());
        assertTrue(messagingChannel.getPendingRequests().isEmpty());
    }

    @Test
    public void shouldReturnEmptyList() throws Exception {
        String requestId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();

        ChunkedInfoMessage emptyResponse = new ChunkedInfoMessage(null, timestamp, requestId, requestId, 0);
        CHUNKED_RESPONSES.add(emptyResponse);
        Message request = new Message(timestamp, requestId);

        CompletableFuture<List<InfoData>> future = messagingChannel.sendAndGetChunked(CHUNKED_TOPIC, request);
        prepareResponses(CHUNKED_TOPIC);
        List<InfoData> result = future.get(1, TimeUnit.SECONDS);

        assertTrue(result.isEmpty());
        assertTrue(messagingChannel.getPendingChunkedRequests().isEmpty());
    }

    @Test
    public void shouldCompleteResponseExceptionallyIfResponseIsError() throws Exception {
        thrown.expect(ExecutionException.class);

        String requestId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();
        logger.info("looking " + requestId);

        ErrorData error = new ErrorData(ErrorType.INTERNAL_ERROR, "message", "description");
        ErrorMessage errorResponse = new ErrorMessage(error, timestamp, requestId, null);
        RESPONSES.add(errorResponse);
        Message request = new Message(timestamp, requestId);

        CompletableFuture<InfoData> response = messagingChannel.sendAndGet(MAIN_TOPIC, request);
        prepareResponses(MAIN_TOPIC);
        response.get(10, TimeUnit.SECONDS);

        assertTrue(response.isCompletedExceptionally());
        assertTrue(messagingChannel.getPendingRequests().isEmpty());
    }

    @Test
    public void shouldCompleteChunkedResponseExceptionallyIfResponseIsError() throws Exception {
        thrown.expect(ExecutionException.class);

        String requestId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();

        ErrorData error = new ErrorData(ErrorType.INTERNAL_ERROR, "message", "description");
        ErrorMessage errorResponse = new ErrorMessage(error, timestamp, requestId, null);
        CHUNKED_RESPONSES.add(errorResponse);
        Message request = new Message(timestamp, requestId);

        CompletableFuture<List<InfoData>> response = messagingChannel.sendAndGetChunked(CHUNKED_TOPIC, request);
        prepareResponses(CHUNKED_TOPIC);
        response.get(1, TimeUnit.SECONDS);

        assertTrue(response.isCompletedExceptionally());
        assertTrue(messagingChannel.getPendingChunkedRequests().isEmpty());
    }

    @Test
    public void shouldCompleteResponseExceptionallyIfMessageIsNotSent() throws Exception {
        thrown.expect(ExecutionException.class);

        String requestId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();
        Message request = new Message(timestamp, requestId);

        CompletableFuture<InfoData> response = messagingChannel.sendAndGet(BROKEN_TOPIC, request);
        prepareResponses(BROKEN_TOPIC);
        response.get(1, TimeUnit.SECONDS);

        assertTrue(response.isCompletedExceptionally());
        assertTrue(messagingChannel.getPendingRequests().isEmpty());
    }

    @Test
    public void shouldCompleteChunkedResponseExceptionallyIfMessageIsNotSent() throws Exception {
        thrown.expect(ExecutionException.class);

        String requestId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();
        Message request = new Message(timestamp, requestId);

        CompletableFuture<List<InfoData>> response = messagingChannel.sendAndGetChunked(BROKEN_TOPIC, request);
        prepareResponses(BROKEN_TOPIC);
        response.get(1, TimeUnit.SECONDS);

        assertTrue(response.isCompletedExceptionally());
        assertTrue(messagingChannel.getPendingChunkedRequests().isEmpty());
    }

    /**
     * Creates chunk of responses started from requestId, with predefined size.
     */
    private void prepareChunkedResponses(String requestId, long timestamp, int size) {
        for (int i = 0; i < size; i++) {
            InfoData data = new SwitchInfoData(new SwitchId(i), SwitchChangeType.ACTIVATED,
                    null, null, null, null, false);
            ChunkedInfoMessage response = new ChunkedInfoMessage(data, timestamp, requestId, requestId + i, size);
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
        private KafkaMessagingChannel messagingChannel;

        CustomMessageProducer(KafkaMessagingChannel messagingChannel) {
            this.messagingChannel = messagingChannel;
        }

        @Override
        public ListenableFuture<SendResult<String, Message>> send(String topic, Message message) {
            SettableListenableFuture<SendResult<String, Message>> future = new SettableListenableFuture<>();
            ProducerRecord<String, Message> record = new ProducerRecord<>(topic, message);

            if (BROKEN_TOPIC.equals(topic)) {
                // simulation of the inability to send the message.
                future.setException(new RuntimeException("Server is unavailable"));
            } else {
                future.set(new SendResult<>(record, null));
            }
            return future;
        }

    }

    private void prepareResponses(String topic) {
        if (CHUNKED_TOPIC.equals(topic)) {
            // emulate receiving chunked messages.
            ExecutorService responseExecutor = Executors.newFixedThreadPool(10);
            CHUNKED_RESPONSES.forEach(response ->
                    responseExecutor.submit(() -> messagingChannel.onResponse(response)));

        } else {
            RESPONSES.forEach(response -> messagingChannel.onResponse(response));
        }
    }

}
