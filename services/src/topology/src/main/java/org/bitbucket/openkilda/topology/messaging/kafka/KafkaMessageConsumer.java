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

package org.bitbucket.openkilda.topology.messaging.kafka;

import static org.bitbucket.openkilda.messaging.Utils.CORRELATION_ID;
import static org.bitbucket.openkilda.messaging.Utils.MAPPER;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.command.CommandData;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.flow.FlowCreateRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowGetRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowPathRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.bitbucket.openkilda.messaging.command.flow.FlowsGetRequest;
import org.bitbucket.openkilda.messaging.info.InfoData;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPayload;
import org.bitbucket.openkilda.topology.service.FlowService;
import org.bitbucket.openkilda.topology.service.IslService;
import org.bitbucket.openkilda.topology.service.SwitchService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * Kafka message consumer.
 */
@Component
@PropertySource("classpath:topology.properties")
public class KafkaMessageConsumer {
    /**
     * Kafka message queue poll timeout.
     */
    private static final int POLL_TIMEOUT = 5000;

    /**
     * Kafka message queue poll pause.
     */
    private static final int POLL_PAUSE = 100;

    private static final String topic = "kilda-test";

    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageConsumer.class);

    @Autowired
    KafkaMessageProducer kafkaMessageProducer;

    @Autowired
    FlowService flowService;

    @Autowired
    IslService islService;

    @Autowired
    SwitchService switchService;

    /**
     * Receives messages from WorkFlowManager queue.
     *
     * @param record the message object instance
     */
    @KafkaListener(topics = "kilda-test")
    public void receive(final String record) {
        logger.debug("message received: {}", record);
        try {
            Message message = MAPPER.readValue(record, Message.class);

            if (message.getDestination() == null || Destination.TOPOLOGY_ENGINE.equals(message.getDestination())) {

                if (message instanceof CommandMessage) {
                    CommandData data = ((CommandMessage) message).getData();

                    if (data instanceof FlowCreateRequest) {
                        FlowPayload payload = ((FlowCreateRequest) data).getPayload();
                        logger.debug("FlowCreateRequest: {}", payload);

                        Set<CommandMessage> commands = flowService.createFlow(payload, message.getCorrelationId());
                        for (CommandMessage response : commands) {
                            kafkaMessageProducer.send(topic, response);
                        }

                        logger.debug("Response send, {}={}", CORRELATION_ID, message.getCorrelationId());

                    } else if (data instanceof FlowDeleteRequest) {
                        FlowIdStatusPayload payload = ((FlowDeleteRequest) data).getPayload();
                        logger.debug("FlowDeleteRequest: {}", payload);

                        Set<CommandMessage> commands = flowService.deleteFlow(payload, message.getCorrelationId());
                        for (CommandMessage response : commands) {
                            kafkaMessageProducer.send(topic, response);
                        }

                        logger.debug("Response send, {}={}", CORRELATION_ID, message.getCorrelationId());

                    } else if (data instanceof FlowUpdateRequest) {
                        FlowPayload payload = ((FlowUpdateRequest) data).getPayload();
                        logger.debug("FlowUpdateRequest: {}", payload);

                        Set<CommandMessage> commands = flowService.updateFlow(payload, message.getCorrelationId());
                        for (CommandMessage response : commands) {
                            kafkaMessageProducer.send(topic, response);
                        }

                        logger.debug("Response send, {}={}", CORRELATION_ID, message.getCorrelationId());

                    } else if (data instanceof FlowGetRequest) {
                        FlowIdStatusPayload payload = ((FlowGetRequest) data).getPayload();
                        logger.debug("FlowGetRequest: {}", payload);

                        InfoMessage response = flowService.getFlow(payload, message.getCorrelationId());
                        kafkaMessageProducer.send(topic, response);

                        logger.debug("Response send, {}={}", CORRELATION_ID, message.getCorrelationId());

                    } else if (data instanceof FlowsGetRequest) {
                        FlowIdStatusPayload payload = ((FlowsGetRequest) data).getPayload();
                        logger.debug("FlowsGetRequest: {}", payload);

                        InfoMessage response = flowService.getFlows(payload, message.getCorrelationId());
                        kafkaMessageProducer.send(topic, response);

                        logger.debug("Response send, {}={}", CORRELATION_ID, message.getCorrelationId());

                    } else if (data instanceof FlowPathRequest) {
                        FlowIdStatusPayload payload = ((FlowPathRequest) data).getPayload();
                        logger.debug("FlowPathRequest: {}", payload);

                        InfoMessage response = flowService.pathFlow(payload, message.getCorrelationId());
                        kafkaMessageProducer.send(topic, response);

                        logger.debug("Response send, {}={}", CORRELATION_ID, message.getCorrelationId());

                    } else {
                        logger.error("Unexpected command message data type: {}", data);
                    }

                } else if (message instanceof InfoMessage) {
                    InfoData data = ((InfoMessage) message).getData();

                    if (data instanceof SwitchInfoData) {
                        SwitchInfoData payload = (SwitchInfoData) data;

                        switch (payload.getState()) {

                            case ADDED:
                                switchService.add(payload);
                                break;

                            case ACTIVATED:
                                switchService.activate(payload);
                                break;

                            case DEACTIVATED:
                                switchService.deactivate(payload);
                                break;

                            case REMOVED:
                                switchService.remove(payload);
                                break;

                            case CHANGED:
                            default:
                                break;
                        }
                    } else if (data instanceof IslInfoData) {
                        IslInfoData payload = (IslInfoData) data;

                        islService.discoverLink(payload);
                    } else {
                        logger.debug("Unexpected info message data type: {}", data);
                    }
                }
            } else {
                logger.debug("Skip message: {}", message);
            }
        } catch (IOException exception) {
            logger.error("Could not deserialize message: {}", record, exception);
        }
    }
}
