package org.bitbucket.openkilda.northbound.messaging;

public interface MessageConsumer<T> {
    /**
     * Kafka message queue poll timeout.
     */
    int POLL_TIMEOUT = 5000;

    /**
     * Kafka message queue poll pause.
     */
    int POLL_PAUSE = 100;

    /**
     * Polls Kafka message queue.
     *
     * @param correlationId correlation id
     * @return received message
     */
    T poll(final String correlationId);

    /**
     * Clears message queue.
     */
    void clear();
}
