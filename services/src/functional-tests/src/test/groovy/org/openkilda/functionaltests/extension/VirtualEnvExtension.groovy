package org.openkilda.functionaltests.extension

import org.openkilda.functionaltests.extension.spring.SpringContextExtension
import org.openkilda.functionaltests.extension.spring.SpringContextListener
import org.openkilda.functionaltests.helpers.MininetTopologyBuilder
import org.openkilda.testing.service.mininet.Mininet

import groovy.util.logging.Slf4j
import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext

@Slf4j
class VirtualEnvExtension extends AbstractGlobalExtension implements SpringContextListener {
    @Autowired
    MininetTopologyBuilder mininetTopology
    @Autowired
    Mininet mininet

    @Override
    void start() {
        SpringContextExtension.listeners << this
    }

    @Override
    void notifyContextInitialized(ApplicationContext applicationContext) {
        if (applicationContext.environment.getActiveProfiles().contains("virtual")) {
            applicationContext.autowireCapableBeanFactory.autowireBean(this)
            //delete any existing mininet topologies
            mininet.deleteTopology()
            mininetTopology.buildVirtualEnvironment()
            log.info("Virtual topology successfully created")
        }
    }

}
