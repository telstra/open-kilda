package org.openkilda.floodlight.kafka;

import org.openkilda.floodlight.pathverification.IPathVerificationService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

import java.util.Collection;

public class ConsumerContext extends Context {
    private final IPathVerificationService pathVerificationService;
    private final KafkaMessageProducer kafkaProducer;
    private final ISwitchManager switchManager;

    public static void fillDependencies(Collection<Class<? extends IFloodlightService>> dependencies) {
        Context.fillDependencies(dependencies);

        dependencies.add(IPathVerificationService.class);
        dependencies.add(ISwitchManager.class);
    }

    public ConsumerContext(FloodlightModuleContext moduleContext,
            IFloodlightModule module) {
        super(moduleContext, module);

        pathVerificationService = moduleContext.getServiceImpl(IPathVerificationService.class);
        switchManager = moduleContext.getServiceImpl(ISwitchManager.class);
        kafkaProducer = moduleContext.getServiceImpl(KafkaMessageProducer.class);
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
}
