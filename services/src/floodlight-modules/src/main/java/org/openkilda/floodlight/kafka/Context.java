package org.openkilda.floodlight.kafka;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

import java.util.Collection;
import java.util.Map;

public class Context {
    private KafkaConfig kafkaConfig;
    private Map<String, String> moduleConfig;

    public static void fillDependencies(Collection<Class<? extends IFloodlightService>> dependencies) {}

    public Context(FloodlightModuleContext moduleContext, IFloodlightModule module) {
        moduleConfig = moduleContext.getConfigParams(module);
        kafkaConfig = new KafkaConfig(moduleConfig);
    }

    public KafkaConfig getKafkaConfig() {
        return kafkaConfig;
    }

    public String configLookup(String option) {
        return moduleConfig.get(option);
    }
}
