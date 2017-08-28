package org.bitbucket.openkilda.northbound.controller;

import static java.util.Collections.singletonList;
import static org.bitbucket.openkilda.messaging.Utils.MAPPER;

import org.bitbucket.openkilda.messaging.Destination;
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
import org.bitbucket.openkilda.messaging.error.ErrorData;
import org.bitbucket.openkilda.messaging.error.ErrorMessage;
import org.bitbucket.openkilda.messaging.error.ErrorType;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.flow.FlowPathResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowStatusResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowsResponse;
import org.bitbucket.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPathPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowState;
import org.bitbucket.openkilda.messaging.payload.flow.FlowsPayload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.io.IOException;

/**
 * Spring component which mocks WorkFlow Manager. This instance listens kafka ingoing requests and sends back
 * appropriate kafka responses. Response type choice is based on request type.
 */
@Component
public class WorkFlowManagerKafkaMock {
    static final String FLOW_ID = "test-flow";
    static final String ERROR_FLOW_ID = "error-flow";
    static final FlowEndpointPayload flowEndpoint = new FlowEndpointPayload(FLOW_ID, 1, 1);
    static final FlowPayload flow = new FlowPayload(FLOW_ID, flowEndpoint, flowEndpoint, 10000L, "", "");
    static final FlowIdStatusPayload flowStatus = new FlowIdStatusPayload(FLOW_ID, FlowState.IN_PROGRESS);
    static final FlowsPayload flows = new FlowsPayload(singletonList(flow));
    static final FlowPathPayload flowPath = new FlowPathPayload(FLOW_ID, singletonList(FLOW_ID));
    private static final FlowResponse flowResponse = new FlowResponse(flow);
    private static final FlowsResponse flowsResponse = new FlowsResponse(flows);
    private static final FlowPathResponse flowPathResponse = new FlowPathResponse(flowPath);
    private static final FlowStatusResponse flowStatusResponse = new FlowStatusResponse(flowStatus);
    private static final Logger logger = LoggerFactory.getLogger(WorkFlowManagerKafkaMock.class);

    /**
     * Kafka outgoing topic.
     */
    private static final String topic = "kilda-test";

    /**
     * Spring KafkaProducer wrapper.
     */
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Spring KafkaConsumer wrapper.
     *
     * @param record received kafka message
     */
    @KafkaListener(id = "test-listener", topics = "${kafka.topic}")
    public void testReceive(final String record) {
        logger.debug("Test message");
        try {
            Message message = MAPPER.readValue(record, Message.class);

            if (Destination.WFM.equals(message.getDestination()) && message instanceof CommandMessage) {
                CommandData commandData = ((CommandMessage) message).getData();

                logger.debug("Test message received: record={}", record);
                String response = MAPPER.writeValueAsString(formatResponse(message.getCorrelationId(), commandData));

                kafkaTemplate.send(topic, response).addCallback(new ListenableFutureCallback<SendResult<String, String>>() {
                    @Override
                    public void onSuccess(SendResult<String, String> result) {
                        logger.debug("Test message sent: topic={}, message={}", topic, response);
                    }

                    @Override
                    public void onFailure(Throwable exception) {
                        logger.error("Unable to send test message: topic={}, message={}", topic, response, exception);
                    }
                });
            }
        } catch (IOException exception) {
            logger.error("Could not deserialize test message: {}", record, exception);
        }
    }

    /**
     * Chooses response by request.
     *
     * @param data received from kafka CommandData message payload
     * @return InfoMassage to be send as response payload
     */
    private Message formatResponse(final String correlationId, final CommandData data) {
        if (data instanceof FlowCreateRequest) {
            return new InfoMessage(flowResponse, 0, correlationId, Destination.NORTHBOUND);
        } else if (data instanceof FlowDeleteRequest) {
            return new InfoMessage(flowResponse, 0, correlationId, Destination.NORTHBOUND);
        } else if (data instanceof FlowUpdateRequest) {
            return new InfoMessage(flowResponse, 0, correlationId, Destination.NORTHBOUND);
        } else if (data instanceof FlowGetRequest) {
            if (ERROR_FLOW_ID.equals(((FlowGetRequest) data).getPayload().getId())) {
                return new ErrorMessage(new ErrorData(ErrorType.NOT_FOUND, "Flow was not found", ERROR_FLOW_ID),
                        0, correlationId, Destination.NORTHBOUND);
            } else {
                return new InfoMessage(flowResponse, 0, correlationId, Destination.NORTHBOUND);
            }
        } else if (data instanceof FlowsGetRequest) {
            return new InfoMessage(flowsResponse, 0, correlationId, Destination.NORTHBOUND);
        } else if (data instanceof FlowStatusRequest) {
            return new InfoMessage(flowStatusResponse, 0, correlationId, Destination.NORTHBOUND);
        } else if (data instanceof FlowPathRequest) {
            return new InfoMessage(flowPathResponse, 0, correlationId, Destination.NORTHBOUND);
        } else {
            return null;
        }
    }
}
