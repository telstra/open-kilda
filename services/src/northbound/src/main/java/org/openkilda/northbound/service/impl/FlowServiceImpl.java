/* Copyright 2017 Telstra Open Source
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

package org.openkilda.northbound.service.impl;

import static org.openkilda.messaging.Utils.CORRELATION_ID;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowCreateRequest;
import org.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.openkilda.messaging.command.flow.FlowGetRequest;
import org.openkilda.messaging.command.flow.FlowPathRequest;
import org.openkilda.messaging.command.flow.FlowStatusRequest;
import org.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.openkilda.messaging.command.flow.FlowsGetRequest;
import org.openkilda.messaging.info.flow.FlowPathResponse;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.FlowStatusResponse;
import org.openkilda.messaging.info.flow.FlowsResponse;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.northbound.messaging.MessageConsumer;
import org.openkilda.northbound.messaging.MessageProducer;
import org.openkilda.northbound.service.FlowService;
import org.openkilda.northbound.utils.Converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

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
     * The kafka topic.
     */
    @Value("${kafka.flow.topic}")
    private String topic;

    /**
     * Kafka message consumer.
     */
    @Autowired
    private MessageConsumer messageConsumer;

    /**
     * Kafka message producer.
     */
    @Autowired
    private MessageProducer messageProducer;

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowPayload createFlow(final FlowPayload flow, final String correlationId) {
        logger.debug("Create flow: {}={}", CORRELATION_ID, correlationId);
        FlowCreateRequest data = new FlowCreateRequest(Converter.buildFlowByFlowPayload(flow));
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
        messageConsumer.clear();
        messageProducer.send(topic, request);
        Message message = (Message) messageConsumer.poll(correlationId);
        FlowResponse response = (FlowResponse) validateInfoMessage(request, message, correlationId);
        return Converter.buildFlowPayloadByFlow(response.getPayload());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowPayload deleteFlow(final String id, final String correlationId) {
        logger.debug("Delete flow: {}={}", CORRELATION_ID, correlationId);
        Flow flow = new Flow();
        flow.setFlowId(id);
        FlowDeleteRequest data = new FlowDeleteRequest(flow);
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
        messageConsumer.clear();
        messageProducer.send(topic, request);
        Message message = (Message) messageConsumer.poll(correlationId);
        FlowResponse response = (FlowResponse) validateInfoMessage(request, message, correlationId);
        return Converter.buildFlowPayloadByFlow(response.getPayload());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowPayload getFlow(final String id, final String correlationId) {
        logger.debug("Get flow: {}={}", CORRELATION_ID, correlationId);
        FlowGetRequest data = new FlowGetRequest(new FlowIdStatusPayload(id, null));
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
        messageConsumer.clear();
        messageProducer.send(topic, request);
        Message message = (Message) messageConsumer.poll(correlationId);
        FlowResponse response = (FlowResponse) validateInfoMessage(request, message, correlationId);
        return Converter.buildFlowPayloadByFlow(response.getPayload());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowPayload updateFlow(final FlowPayload flow, final String correlationId) {
        logger.debug("Update flow: {}={}", CORRELATION_ID, correlationId);
        FlowUpdateRequest data = new FlowUpdateRequest(Converter.buildFlowByFlowPayload(flow));
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
        messageConsumer.clear();
        messageProducer.send(topic, request);
        Message message = (Message) messageConsumer.poll(correlationId);
        FlowResponse response = (FlowResponse) validateInfoMessage(request, message, correlationId);
        return Converter.buildFlowPayloadByFlow(response.getPayload());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<FlowPayload> getFlows(final String correlationId) {
        logger.debug("Get flows: {}={}", CORRELATION_ID, correlationId);
        FlowsGetRequest data = new FlowsGetRequest(new FlowIdStatusPayload());
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
        messageConsumer.clear();
        messageProducer.send(topic, request);
        Message message = (Message) messageConsumer.poll(correlationId);
        FlowsResponse response = (FlowsResponse) validateInfoMessage(request, message, correlationId);
        return Converter.buildFlowsPayloadByFlows(response.getPayload());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlowIdStatusPayload statusFlow(final String id, final String correlationId) {
        logger.debug("Flow status: {}={}", CORRELATION_ID, correlationId);
        FlowStatusRequest data = new FlowStatusRequest(new FlowIdStatusPayload(id, null));
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
        messageConsumer.clear();
        messageProducer.send(topic, request);
        Message message = (Message) messageConsumer.poll(correlationId);
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
        CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
        messageConsumer.clear();
        messageProducer.send(topic, request);
        Message message = (Message) messageConsumer.poll(correlationId);
        FlowPathResponse response = (FlowPathResponse) validateInfoMessage(request, message, correlationId);
        return Converter.buildFlowPathPayloadByFlowPath(id, response.getPayload());
    }
}
