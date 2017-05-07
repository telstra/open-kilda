package org.bitbucket.openkilda.northbound.service.impl;

import static org.bitbucket.openkilda.northbound.utils.Constants.CORRELATION_ID;

import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.flow.FlowCreateRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowGetRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowPathRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowStatusRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowsGetRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowsStatusRequest;
import org.bitbucket.openkilda.messaging.info.flow.FlowPathResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowStatusResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowsResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowsStatusResponse;
import org.bitbucket.openkilda.messaging.payload.FlowPayload;
import org.bitbucket.openkilda.messaging.payload.request.FlowIdRequestPayload;
import org.bitbucket.openkilda.messaging.payload.request.FlowStatusRequestPayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowPathResponsePayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowStatusResponsePayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowStatusType;
import org.bitbucket.openkilda.messaging.payload.response.FlowsResponsePayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowsStatusResponsePayload;
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
        logger.trace("Create flow: {}={}", CORRELATION_ID, correlationId);
        FlowCreateRequest data = new FlowCreateRequest(flow);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);
        kafkaMessageProducer.send(request);
        Message message = (Message) kafkaMessageConsumer.poll(correlationId);
        FlowResponse response = (FlowResponse) validateInfoMessage(request, message, correlationId);
        return response.getPayload();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowPayload deleteFlow(final String id, final String correlationId) {
        logger.trace("Delete flow: {}={}", CORRELATION_ID, correlationId);
        FlowDeleteRequest data = new FlowDeleteRequest(new FlowIdRequestPayload(id));
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);
        kafkaMessageProducer.send(request);
        Message message = (Message) kafkaMessageConsumer.poll(correlationId);
        FlowResponse response = (FlowResponse) validateInfoMessage(request, message, correlationId);
        return response.getPayload();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowPayload getFlow(final String id, final String correlationId) {
        logger.trace("Get flow: {}={}", CORRELATION_ID, correlationId);
        FlowGetRequest data = new FlowGetRequest(new FlowIdRequestPayload(id));
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);
        kafkaMessageProducer.send(request);
        Message message = (Message) kafkaMessageConsumer.poll(correlationId);
        FlowResponse response = (FlowResponse) validateInfoMessage(request, message, correlationId);
        return response.getPayload();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowPayload updateFlow(final FlowPayload flow, final String correlationId) {
        logger.trace("Update flow: {}={}", CORRELATION_ID, correlationId);
        FlowUpdateRequest data = new FlowUpdateRequest(flow);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);
        kafkaMessageProducer.send(request);
        Message message = (Message) kafkaMessageConsumer.poll(correlationId);
        FlowResponse response = (FlowResponse) validateInfoMessage(request, message, correlationId);
        return response.getPayload();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowsResponsePayload getFlows(final String status, final String correlationId) {
        logger.trace("Get flows: {}={}", CORRELATION_ID, correlationId);
        FlowsGetRequest data = new FlowsGetRequest(new FlowStatusRequestPayload(FlowStatusType.valueOf(status)));
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);
        kafkaMessageProducer.send(request);
        Message message = (Message) kafkaMessageConsumer.poll(correlationId);
        FlowsResponse response = (FlowsResponse) validateInfoMessage(request, message, correlationId);
        return response.getPayload();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowStatusResponsePayload statusFlow(final String id, final String correlationId) {
        logger.trace("Flow status: {}={}", CORRELATION_ID, correlationId);
        FlowStatusRequest data = new FlowStatusRequest(new FlowIdRequestPayload(id));
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);
        kafkaMessageProducer.send(request);
        Message message = (Message) kafkaMessageConsumer.poll(correlationId);
        FlowStatusResponse response = (FlowStatusResponse) validateInfoMessage(request, message, correlationId);
        return response.getPayload();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowsStatusResponsePayload statusFlows(final String status, final String correlationId) {
        logger.trace("Flows status: {}={}", CORRELATION_ID, correlationId);
        FlowsStatusRequest data = new FlowsStatusRequest(new FlowStatusRequestPayload(FlowStatusType.valueOf(status)));
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);
        kafkaMessageProducer.send(request);
        Message message = (Message) kafkaMessageConsumer.poll(correlationId);
        FlowsStatusResponse response = (FlowsStatusResponse) validateInfoMessage(request, message, correlationId);
        return response.getPayload();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowPathResponsePayload pathFlow(final String id, final String correlationId) {
        logger.trace("Flow path: {}={}", CORRELATION_ID, correlationId);
        FlowPathRequest data = new FlowPathRequest(new FlowIdRequestPayload(id));
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId);
        kafkaMessageProducer.send(request);
        Message message = (Message) kafkaMessageConsumer.poll(correlationId);
        FlowPathResponse response = (FlowPathResponse) validateInfoMessage(request, message, correlationId);
        return response.getPayload();
    }
}
