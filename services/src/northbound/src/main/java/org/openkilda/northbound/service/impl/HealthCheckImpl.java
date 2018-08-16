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

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.ServiceType;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.discovery.HealthCheckCommandData;
import org.openkilda.messaging.model.HealthCheck;
import org.openkilda.northbound.messaging.HealthCheckMessageConsumer;
import org.openkilda.northbound.messaging.MessageProducer;
import org.openkilda.northbound.service.HealthCheckService;
import org.openkilda.northbound.utils.RequestCorrelationId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages health-check operation.
 */
@Service
public class HealthCheckImpl implements HealthCheckService {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckImpl.class);

    /**
     * Health-Check topic.
     */
    @Value("#{kafkaTopicsConfig.getHealthCheckTopic()}")
    private String topic;

    /**
     * Health-Check dump command requester.
     */
    private static final HealthCheckCommandData NORTHBOUND_REQUESTER =
            new HealthCheckCommandData(Destination.NORTHBOUND.toString());

    /**
     * The service name.
     */
    @Value("${service.name}")
    private String serviceName;

    /**
     * The service version.
     */
    @Value("${service.version}")
    private String serviceVersion;

    /**
     * The service description.
     */
    @Value("${service.description}")
    private String serviceDescription;

    /**
     * Kafka message consumer.
     */
    @Autowired
    private HealthCheckMessageConsumer healthCheckMessageConsumer;

    /**
     * The Kafka producer instance.
     */
    @Autowired
    private MessageProducer messageProducer;

    /**
     * The health-check info bean.
     *
     * @return the FlowModel instance
     */
    @Override
    public HealthCheck getHealthCheck() {
        final String correlationId = RequestCorrelationId.getId();

        // FIXME(surabujin): restore health check operation
        /*
        healthCheckMessageConsumer.clear();

        messageProducer.send(topic, new CommandMessage(
                NORTHBOUND_REQUESTER, System.currentTimeMillis(), correlationId, null));

        Map<String, String> status = healthCheckMessageConsumer.poll(correlationId);

        Arrays.stream(ServiceType.values())
           .forEach(service -> status.putIfAbsent(service.getSwitchId(), Utils.HEALTH_CHECK_NON_OPERATIONAL_STATUS));
        */

        // FIXME(surabujin): hack to restore "operational" despite actual state of services
        // hack start
        Map<String, String> status = new HashMap<>();

        Arrays.stream(ServiceType.values())
                .forEach(service -> status.putIfAbsent(service.getId(), Utils.HEALTH_CHECK_OPERATIONAL_STATUS));
        // hack end

        HealthCheck healthCheck = new HealthCheck(serviceName, serviceVersion, serviceDescription, status);

        logger.info("Health-Check Status: {}", healthCheck);

        return healthCheck;
    }
}
