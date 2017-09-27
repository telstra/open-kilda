package org.bitbucket.openkilda.northbound.messaging;

public interface MessageProducer {
    /**
     * Kafka message send timeout.
     */
    int TIMEOUT = 1000;

    /**
     * Sends messages to WorkFlowManager.
     *
     * @param topic  kafka topic
     * @param object object to serialize and send
     */
    void send(final String topic, final Object object);
}
