package org.openkilda.floodlight.kafka;

import static java.util.Objects.requireNonNull;

import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.floodlight.config.KafkaFloodlightConfig;
import org.openkilda.floodlight.pathverification.IPathVerificationService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightService;

import java.util.Collection;

public class ConsumerContext extends Context {
    private final IPathVerificationService pathVerificationService;
    private final KafkaMessageProducer kafkaProducer;
    private final ISwitchManager switchManager;
    private final KafkaTopicsConfig kafkaTopicsConfig;

    public static void fillDependencies(Collection<Class<? extends IFloodlightService>> dependencies) {
        dependencies.add(IPathVerificationService.class);
        dependencies.add(ISwitchManager.class);
    }

    public ConsumerContext(FloodlightModuleContext moduleContext, KafkaFloodlightConfig kafkaFloodlightConfig,
                           KafkaTopicsConfig kafkaTopicsConfig) {
        super(moduleContext, kafkaFloodlightConfig);

        pathVerificationService = moduleContext.getServiceImpl(IPathVerificationService.class);
        switchManager = moduleContext.getServiceImpl(ISwitchManager.class);
        kafkaProducer = moduleContext.getServiceImpl(KafkaMessageProducer.class);

        this.kafkaTopicsConfig = requireNonNull(kafkaTopicsConfig, "kafkaTopicsConfig cannot be null");
    }

    public IPathVerificationService getPathVerificationService() {
        return pathVerificationService;
    }

    public KafkaMessageProducer getKafkaProducer() {
        return kafkaProducer;
    }

    public ISwitchManager getSwitchManager() {
        return switchManager;
    }

    public String getKafkaFlowTopic() {
        return kafkaTopicsConfig.getFlowTopic();
    }

    public String getKafkaTopoDiscoTopic() {
        return kafkaTopicsConfig.getTopoDiscoTopic();
    }

    public String getKafkaStatsTopic() {
        return kafkaTopicsConfig.getStatsTopic();
    }

    public String getKafkaNorthboundTopic() {
        return kafkaTopicsConfig.getNorthboundTopic();
    }
}
