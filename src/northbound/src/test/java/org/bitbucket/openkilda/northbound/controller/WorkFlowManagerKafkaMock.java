package org.bitbucket.openkilda.northbound.controller;

import static java.util.Collections.singletonList;
import static org.bitbucket.openkilda.messaging.payload.response.FlowStatusType.INSTALLATION;
import static org.bitbucket.openkilda.northbound.utils.Constants.DEFAULT_CORRELATION_ID;

import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.command.CommandData;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.flow.FlowCreateRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowGetRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowPathRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowStatusRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowsGetRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowsStatusRequest;
import org.bitbucket.openkilda.messaging.error.ErrorData;
import org.bitbucket.openkilda.messaging.error.ErrorMessage;
import org.bitbucket.openkilda.messaging.error.ErrorType;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.flow.FlowPathResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowStatusResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowsResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowsStatusResponse;
import org.bitbucket.openkilda.messaging.payload.FlowEndpointPayload;
import org.bitbucket.openkilda.messaging.payload.FlowPayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowPathResponsePayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowStatusResponsePayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowsResponsePayload;
import org.bitbucket.openkilda.messaging.payload.response.FlowsStatusResponsePayload;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.io.IOException;

/**
 * Spring component which mocks WorkFlow Manager.
 * This instance listens kafka ingoing requests and sends back appropriate kafka responses.
 * Response type choice is based on request type.
 */
@Component
@PropertySource("classpath:northbound.properties")
public class WorkFlowManagerKafkaMock {
    static final String FLOW_ID = "test-flow";
    static final String ERROR_FLOW_ID = "error-flow";
    static final FlowEndpointPayload flowEndpoint = new FlowEndpointPayload(FLOW_ID, 1, 1);
    static final FlowPayload flow = new FlowPayload(FLOW_ID, flowEndpoint, flowEndpoint, 10000, "", "");
    static final FlowStatusResponsePayload flowStatus = new FlowStatusResponsePayload(FLOW_ID, INSTALLATION);
    static final FlowsStatusResponsePayload flowsStatus = new FlowsStatusResponsePayload(singletonList(flowStatus));
    static final FlowsResponsePayload flows = new FlowsResponsePayload(singletonList(flow));
    static final FlowPathResponsePayload flowPath = new FlowPathResponsePayload(FLOW_ID, singletonList(FLOW_ID));
    private static final FlowResponse flowResponse = new FlowResponse(flow);
    private static final FlowsResponse flowsResponse = new FlowsResponse(flows);
    private static final FlowPathResponse flowPathResponse = new FlowPathResponse(flowPath);
    private static final FlowsStatusResponse flowsStatusResponse = new FlowsStatusResponse(flowsStatus);
    private static final FlowStatusResponse flowStatusResponse = new FlowStatusResponse(flowStatus);
    private static final Logger logger = LoggerFactory.getLogger(WorkFlowManagerKafkaMock.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Spring KafkaProducer wrapper.
     */
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Kafka outgoing topic.
     */
    @Value("${kafka.topic.wfm.nb}")
    private String topic;

    /**
     * Spring KafkaConsumer wrapper.
     *
     * @param record received kafka message
     */
    @KafkaListener(topics = "${kafka.topic.nb.wfm}")
    public void receive(final String record) {
        logger.debug("Message received: {}", record);
        try {
            CommandMessage request = (CommandMessage) objectMapper.readValue(record, Message.class);
            String response = objectMapper.writeValueAsString(formatResponse(request.getData()));

            kafkaTemplate.send(topic, response).addCallback(new ListenableFutureCallback<SendResult<String, String>>() {
                @Override
                public void onSuccess(SendResult<String, String> result) {
                    logger.debug("Message sent: topic={}, message={}", topic, response);
                }

                @Override
                public void onFailure(Throwable exception) {
                    logger.error("Unable to send message: topic={}, message={}", topic, response, exception);
                }
            });
        } catch (IOException exception) {
            logger.error("Could not deserialize message: {}", record, exception);
        }
    }

    /**
     * Chooses response by request.
     *
     * @param data received from kafka CommandData message payload
     * @return InfoMassage to be send as response payload
     */
    private Message formatResponse(final CommandData data) {
        if (data instanceof FlowCreateRequest) {
            return new InfoMessage(flowResponse, 0, DEFAULT_CORRELATION_ID);
        } else if (data instanceof FlowDeleteRequest) {
            return new InfoMessage(flowResponse, 0, DEFAULT_CORRELATION_ID);
        } else if (data instanceof FlowUpdateRequest) {
            return new InfoMessage(flowResponse, 0, DEFAULT_CORRELATION_ID);
        } else if (data instanceof FlowGetRequest) {
            if (ERROR_FLOW_ID.equals(((FlowGetRequest) data).getPayload().getId())) {
                return new ErrorMessage(new ErrorData(0, null, ErrorType.NOT_FOUND, ""),
                        0, DEFAULT_CORRELATION_ID);
            } else {
                return new InfoMessage(flowResponse, 0, DEFAULT_CORRELATION_ID);
            }
        } else if (data instanceof FlowsGetRequest) {
            return new InfoMessage(flowsResponse, 0, DEFAULT_CORRELATION_ID);
        } else if (data instanceof FlowStatusRequest) {
            return new InfoMessage(flowStatusResponse, 0, DEFAULT_CORRELATION_ID);
        } else if (data instanceof FlowsStatusRequest) {
            return new InfoMessage(flowsStatusResponse, 0, DEFAULT_CORRELATION_ID);
        } else if (data instanceof FlowPathRequest) {
            return new InfoMessage(flowPathResponse, 0, DEFAULT_CORRELATION_ID);
        } else {
            return null;
        }
    }
}
