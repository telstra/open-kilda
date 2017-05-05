package org.bitbucket.openkilda.northbound.service.impl;

import org.bitbucket.openkilda.northbound.messaging.kafka.KafkaMessageConsumer;
import org.bitbucket.openkilda.northbound.messaging.kafka.KafkaMessageProducer;
import org.bitbucket.openkilda.northbound.model.Flow;
import org.bitbucket.openkilda.northbound.service.FlowService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    public Flow create(Flow flow) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Flow delete(String id) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Flow get(String id) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Flow update(Flow flow) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Flow> dump() {
        return null;
    }
}
