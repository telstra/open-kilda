package org.bitbucket.openkilda.northbound.service.impl;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.ServiceType;
import org.bitbucket.openkilda.messaging.Topic;
import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.command.discovery.HealthCheckCommandData;
import org.bitbucket.openkilda.messaging.model.HealthCheck;
import org.bitbucket.openkilda.northbound.messaging.HealthCheckMessageConsumer;
import org.bitbucket.openkilda.northbound.messaging.MessageProducer;
import org.bitbucket.openkilda.northbound.service.HealthCheckService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
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
    private static final String topic = Topic.HEALTH_CHECK.getId();

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
    public HealthCheck getHealthCheck(String correlationId) {

        healthCheckMessageConsumer.clear();

        messageProducer.send(topic, new CommandMessage(
                NORTHBOUND_REQUESTER, System.currentTimeMillis(), correlationId, null));

        Map<String, String> status = healthCheckMessageConsumer.poll(correlationId);

        Arrays.stream(ServiceType.values())
                .forEach(service -> status.putIfAbsent(service.getId(), Utils.HEALTH_CHECK_NON_OPERATIONAL_STATUS));

        HealthCheck healthCheck = new HealthCheck(serviceName, serviceVersion, serviceDescription, status);

        logger.info("Health-Check Status: {}", healthCheck);

        return healthCheck;
    }
}
