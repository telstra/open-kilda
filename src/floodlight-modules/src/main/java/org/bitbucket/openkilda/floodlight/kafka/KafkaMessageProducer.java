package org.bitbucket.openkilda.floodlight.kafka;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by jonv on 6/3/17.
 */
public class KafkaMessageProducer implements IFloodlightModule, IFloodlightService {
    private Logger logger;
    private Properties kafkaProps;
    private ConcurrentLinkedQueue<ProducerRecord<String, String>> queue;

    /*
     * IFloodlightModule Methods
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>();
        services.add(KafkaMessageProducer.class);
        return services;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> map = new HashMap<>();
        map.put(KafkaMessageProducer.class, this);
        return map;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>();
        services.add(IFloodlightProviderService.class);
        return services;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        logger = LoggerFactory.getLogger(this.getClass());
        IFloodlightProviderService floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        Map<String, String> configParameters = context.getConfigParams(this);
        kafkaProps = new Properties();
        kafkaProps.put("bootstrap.servers", configParameters.get("bootstrap-servers"));
        kafkaProps.put("acks", "all");
        kafkaProps.put("retries", 0);
        kafkaProps.put("batch.size", 16384);
        kafkaProps.put("buffer.memory", 33554432);
        kafkaProps.put("linger.ms", 10);
        kafkaProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        queue = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void startUp(FloodlightModuleContext floodlightModuleContext) throws FloodlightModuleException {
        ExecutorService producerExecutor = Executors.newSingleThreadExecutor();
        producerExecutor.execute(new Producer());
    }

    public void send(ProducerRecord<String, String> record) {
        queue.add(record);
    }

    /**
     * Kafka Producer runnable.
     */
    public class Producer implements Runnable {

        /**
         * Dequeue message from ConcurrentQueue.
         *
         * @return Map or null
         */
        public ProducerRecord<String, String> dequeueItem() {
            if (!queue.isEmpty()) {
                logger.debug("Queue size: " + queue.size());
                return queue.remove();
            } else {
                return null;
            }
        }

        @Override
        public void run() {
            logger.debug("Starting a Producer");
            KafkaProducer<String, String> producer = new KafkaProducer<>(kafkaProps);
            try {
                while (true) {
                    ProducerRecord<String, String> record = dequeueItem();
                    if ( record != null) {
                        logger.debug("posting {} to {}".format(record.topic(), record.value()));
                        producer.send(record);
                    }
                    Thread.sleep(5);
                }
            } catch (Exception exception) {
                logger.error("Error: ", exception);
            }
            producer.close();
        }
    }
}
