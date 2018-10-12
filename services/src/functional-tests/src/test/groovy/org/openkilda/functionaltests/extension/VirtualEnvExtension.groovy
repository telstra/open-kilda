package org.openkilda.functionaltests.extension

import groovy.util.logging.Slf4j
import org.openkilda.functionaltests.extension.spring.SpringContextExtension
import org.openkilda.functionaltests.extension.spring.SpringContextListener
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.SwitchState
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.labservice.LabService
import org.openkilda.testing.service.northbound.NorthboundService
import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext

import static org.openkilda.testing.Constants.SWITCHES_ACTIVATION_TIME
import static org.openkilda.testing.Constants.TOPOLOGY_DISCOVERING_TIME

@Slf4j
class VirtualEnvExtension extends AbstractGlobalExtension implements SpringContextListener {

    @Autowired
    TopologyDefinition topologyDefinition

    @Autowired
    NorthboundService northbound

    @Autowired
    LabService labService

    @Override
    void start() {
        SpringContextExtension.listeners << this
    }

    @Override
    void notifyContextInitialized(ApplicationContext applicationContext) {
        if (applicationContext.environment.getActiveProfiles().contains("virtual")) {
            applicationContext.autowireCapableBeanFactory.autowireBean(this)
            buildVirtualEnvironment()
            log.info("Virtual topology successfully created")
        }
    }

    void buildVirtualEnvironment() {
        //turn on all features
        def features = northbound.getFeatureToggles()
        features.metaClass.properties.each {
            if (it.type == Boolean.class) {
                features.metaClass.setAttribute(features, it.name, true)
            }
        }
        northbound.toggleFeature(features)
        northbound.deleteAllFlows()

        labService.getLab()
        //wait until topology is discovered
        assert Wrappers.wait(TOPOLOGY_DISCOVERING_TIME) {
            northbound.getAllLinks().findAll {
                it.state == IslChangeType.DISCOVERED
            }.size() == topologyDefinition.islsForActiveSwitches.size() * 2
        }
        assert Wrappers.wait(SWITCHES_ACTIVATION_TIME) {
            northbound.getAllSwitches().findAll {
                it.state == SwitchState.ACTIVATED
            }.size() == topologyDefinition.activeSwitches.size()
        }
    }

}
