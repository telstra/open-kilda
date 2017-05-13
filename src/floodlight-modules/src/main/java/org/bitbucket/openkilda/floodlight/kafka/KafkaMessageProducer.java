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

/**
 * Created by jonv on 6/3/17.
 */
public class KafkaMessageProducer implements IFloodlightModule, IFloodlightService {
    private Logger logger;
    private KafkaProducer<String, String> producer;

    /*
     * IFloodlightModule Methods
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return Collections.singletonList(KafkaMessageProducer.class);
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return Collections.singletonMap(KafkaMessageProducer.class, this);
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        return Collections.singletonList(IFloodlightProviderService.class);
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        logger = LoggerFactory.getLogger(this.getClass());
        Map<String, String> configParameters = context.getConfigParams(this);
        Properties kafkaProps = new Properties();
        kafkaProps.put("bootstrap.servers", configParameters.get("bootstrap-servers"));
        kafkaProps.put("acks", "all");
        kafkaProps.put("retries", 0);
        kafkaProps.put("batch.size", 16384);
        kafkaProps.put("buffer.memory", 33554432);
        kafkaProps.put("linger.ms", 10);
        kafkaProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producer = new KafkaProducer<>(kafkaProps);
    }

    @Override
    public void startUp(FloodlightModuleContext floodlightModuleContext) throws FloodlightModuleException {}

    public void send(ProducerRecord<String, String> record) {
        logger.debug("posting {} to {}", record.topic(), record.value());
        producer.send(record);
    }
}
