package org.openkilda.functionaltests.extension.virtualenv

import static org.openkilda.testing.Constants.SWITCHES_ACTIVATION_TIME
import static org.openkilda.testing.Constants.TOPOLOGY_DISCOVERING_TIME

import org.openkilda.functionaltests.extension.spring.SpringContextExtension
import org.openkilda.functionaltests.extension.spring.SpringContextListener
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.labservice.LabService
import org.openkilda.testing.service.northbound.NorthboundService

import groovy.util.logging.Slf4j
import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext

/**
 * This extension is responsible for creating a virtual topology at the start of the test run.
 */
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
        SpringContextExtension.addListener(this)
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
        //TODO(dpoltavets): Feature toggles has been removed from the TE, but after implementing them in nbworker, this code needs to be revised.
        //turn on all features
        //def features = northbound.getFeatureToggles()
        //features.metaClass.properties.each {
        //    if (it.type == Boolean.class) {
        //        features.metaClass.setAttribute(features, it.name, true)
        //    }
        //}
        //northbound.toggleFeature(features)

        labService.flushLabs()
        labService.getLab()

        //wait until topology is discovered
        Wrappers.wait(TOPOLOGY_DISCOVERING_TIME) {
            assert northbound.getAllLinks().findAll {
                it.state == IslChangeType.DISCOVERED
            }.size() == topologyDefinition.islsForActiveSwitches.size() * 2
        }
        //wait until switches are activated
        Wrappers.wait(SWITCHES_ACTIVATION_TIME) {
            assert northbound.getAllSwitches().findAll {
                it.state == SwitchChangeType.ACTIVATED
            }.size() == topologyDefinition.activeSwitches.size()
        }
    }
}
