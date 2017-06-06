package org.bitbucket.openkilda.northbound.service.impl;

import static org.bitbucket.openkilda.messaging.Utils.CORRELATION_ID;

import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.Topic;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.flow.FlowCreateRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowGetRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowPathRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowStatusRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowsGetRequest;
import org.bitbucket.openkilda.messaging.info.flow.FlowPathResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowStatusResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowsResponse;
import org.bitbucket.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPathPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowsPayload;
import org.bitbucket.openkilda.northbound.messaging.kafka.KafkaMessageConsumer;
import org.bitbucket.openkilda.northbound.messaging.kafka.KafkaMessageProducer;
import org.bitbucket.openkilda.northbound.service.FlowService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Manages operations with flows.
 */
@Service
public class FlowServiceImpl implements FlowService {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(FlowServiceImpl.class);

    /**
     * WorkFlow Manager Kafka topic.
     */
    private static final String topic = Topic.NB_WFM.getId();

    /**
     * Kafka message consumer.
     */
    @Autowired
    private KafkaMessageConsumer kafkaMessageConsumer;

    /**
     * Kafka message producer.
     */
    @Autowired
    private KafkaMessageProducer kafkaMessageProducer;

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowPayload createFlow(final FlowPayload flow, final String correlationId) {
        logger.debug("Create flow: {}={}", CORRELATION_ID, correlationId);
        FlowCreateRequest data = new FlowCreateRequest(flow);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);
        kafkaMessageProducer.send(topic, request);
        Message message = (Message) kafkaMessageConsumer.poll(correlationId);
        FlowResponse response = (FlowResponse) validateInfoMessage(request, message, correlationId);
        return response.getPayload();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowIdStatusPayload deleteFlow(final String id, final String correlationId) {
        logger.debug("Delete flow: {}={}", CORRELATION_ID, correlationId);
        FlowDeleteRequest data = new FlowDeleteRequest(new FlowIdStatusPayload(id, null));
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);
        kafkaMessageProducer.send(topic, request);
        Message message = (Message) kafkaMessageConsumer.poll(correlationId);
        FlowStatusResponse response = (FlowStatusResponse) validateInfoMessage(request, message, correlationId);
        return response.getPayload();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowPayload getFlow(final String id, final String correlationId) {
        logger.debug("Get flow: {}={}", CORRELATION_ID, correlationId);
        FlowGetRequest data = new FlowGetRequest(new FlowIdStatusPayload(id, null));
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);
        kafkaMessageProducer.send(topic, request);
        Message message = (Message) kafkaMessageConsumer.poll(correlationId);
        FlowResponse response = (FlowResponse) validateInfoMessage(request, message, correlationId);
        return response.getPayload();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowPayload updateFlow(final FlowPayload flow, final String correlationId) {
        logger.debug("Update flow: {}={}", CORRELATION_ID, correlationId);
        FlowUpdateRequest data = new FlowUpdateRequest(flow);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);
        kafkaMessageProducer.send(topic, request);
        Message message = (Message) kafkaMessageConsumer.poll(correlationId);
        FlowResponse response = (FlowResponse) validateInfoMessage(request, message, correlationId);
        return response.getPayload();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowsPayload getFlows(final String correlationId) {
        logger.debug("Get flows: {}={}", CORRELATION_ID, correlationId);
        FlowsGetRequest data = new FlowsGetRequest(new FlowIdStatusPayload());
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);
        kafkaMessageProducer.send(topic, request);
        Message message = (Message) kafkaMessageConsumer.poll(correlationId);
        FlowsResponse response = (FlowsResponse) validateInfoMessage(request, message, correlationId);
        return response.getPayload();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowIdStatusPayload statusFlow(final String id, final String correlationId) {
        logger.debug("Flow status: {}={}", CORRELATION_ID, correlationId);
        FlowStatusRequest data = new FlowStatusRequest(new FlowIdStatusPayload(id, null));
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);
        kafkaMessageProducer.send(topic, request);
        Message message = (Message) kafkaMessageConsumer.poll(correlationId);
        FlowStatusResponse response = (FlowStatusResponse) validateInfoMessage(request, message, correlationId);
        return response.getPayload();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowPathPayload pathFlow(final String id, final String correlationId) {
        logger.debug("Flow path: {}={}", CORRELATION_ID, correlationId);
        FlowPathRequest data = new FlowPathRequest(new FlowIdStatusPayload(id, null));
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);
        kafkaMessageProducer.send(topic, request);
        Message message = (Message) kafkaMessageConsumer.poll(correlationId);
        FlowPathResponse response = (FlowPathResponse) validateInfoMessage(request, message, correlationId);
        return response.getPayload();
    }
}
