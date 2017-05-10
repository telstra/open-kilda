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

@Component
@PropertySource("classpath:northbound.properties")
public class WorkFlowManagerKafkaMock {
    static final String FLOW_ID = "test-flow";
    private static FlowEndpointPayload flowEndpoint = new FlowEndpointPayload(FLOW_ID, 1, 1);
    static FlowPayload flow = new FlowPayload(FLOW_ID, flowEndpoint, flowEndpoint, 10000, "", "");
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final FlowStatusResponsePayload flowStatus = new FlowStatusResponsePayload(FLOW_ID, INSTALLATION);
    private static FlowStatusResponse flowStatusResponse = new FlowStatusResponse(flowStatus);
    private static FlowResponse flowResponse = new FlowResponse(flow);
    private static FlowsResponse flowsResponse = new FlowsResponse(new FlowsResponsePayload(singletonList(flow)));
    private static FlowsStatusResponse flowsStatusResponse =
            new FlowsStatusResponse(new FlowsStatusResponsePayload(singletonList(flowStatus)));
    private static FlowPathResponse flowPathResponse =
            new FlowPathResponse(new FlowPathResponsePayload(FLOW_ID, singletonList(FLOW_ID)));
    private static final Logger logger = LoggerFactory.getLogger(WorkFlowManagerKafkaMock.class);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.topic.wfm.nb}")
    private String topic;

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

    private InfoMessage formatResponse(final CommandData data) {

        if (data instanceof FlowCreateRequest) {
            return new InfoMessage(flowResponse, 0, DEFAULT_CORRELATION_ID);
        } else if (data instanceof FlowDeleteRequest) {
            return new InfoMessage(flowResponse, 0, DEFAULT_CORRELATION_ID);
        } else if (data instanceof FlowUpdateRequest) {
            return new InfoMessage(flowResponse, 0, DEFAULT_CORRELATION_ID);
        } else if (data instanceof FlowGetRequest) {
            return new InfoMessage(flowResponse, 0, DEFAULT_CORRELATION_ID);
        } else if (data instanceof FlowsGetRequest) {
            return new InfoMessage(flowsResponse, 0, DEFAULT_CORRELATION_ID);
        } else if (data instanceof FlowStatusRequest) {
            return new InfoMessage(flowStatusResponse, 0, DEFAULT_CORRELATION_ID);
        } else if (data instanceof FlowsStatusRequest) {
            return new InfoMessage(flowsStatusResponse, 0, DEFAULT_CORRELATION_ID);
        }
        if (data instanceof FlowPathRequest) {
            return new InfoMessage(flowPathResponse, 0, DEFAULT_CORRELATION_ID);
        } else {
            return null;
        }
    }
}
