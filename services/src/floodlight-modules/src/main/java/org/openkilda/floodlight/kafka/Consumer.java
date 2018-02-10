package org.openkilda.floodlight.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class Consumer implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(Consumer.class);

    private final List<String> topics;
    private final ConsumerContext context;
    private final ExecutorService handlersPool;
    private final RecordHandler.Factory handlerFactory;

    public Consumer(
            ConsumerContext context, ExecutorService handlersPool, RecordHandler.Factory handlerFactory,
            String topic, String ...moreTopics) {
        this.topics = new ArrayList<>(moreTopics.length + 1);
        this.topics.add(topic);
        this.topics.addAll(Arrays.asList(moreTopics));

        this.context = context;
        this.handlersPool = handlersPool;
        this.handlerFactory = handlerFactory;
    }

    @Override
    public void run() {
        while (true) {
            /*
             * Ensure we try to keep processing messages. It is possible that the consumer needs
             * to be re-created, either due to internal error, or if it fails to poll within the
             * max.poll.interval.ms seconds.
             *
             * From the Kafka source code, here are the default values for the following fields:
             *  - max.poll.interval.ms = 300000 (ie 300 seconds)
             *  - max.poll.records = 500 (must be able to process about 2 records per second
             */
            KafkaConsumer<String, String> consumer = null;
            try {
                consumer = new KafkaConsumer<>(context.getKafkaConfig());
                consumer.subscribe(topics);

                while (true) {
                    ConsumerRecords<String, String> batch = consumer.poll(100);
                    if (batch.count() < 1) {
                        continue;
                    }

                    logger.debug("Received records batch contain {} messages", batch.count());
                    for (ConsumerRecord<String, String> record : batch) {
                        handle(record);
                    }
                }
            } catch (Exception e) {
                /*
                 * Just log the exception, and start processing again with a new consumer
                 */
                logger.error("Exception received during main kafka consumer loop: {}", e);
            } finally {
                if (consumer != null) {
                    consumer.close(); // we'll create a new one
                }
            }
        }
    }

    protected void handle(ConsumerRecord<String, String> record) {
        logger.trace("received message: {} - {}", record.offset(), record.value());
        handlersPool.execute(handlerFactory.produce(record));
    }
}
