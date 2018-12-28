package org.openkilda.functionaltests.extension.virtualenv

import org.openkilda.functionaltests.extension.spring.SpringContextExtension
import org.openkilda.functionaltests.extension.spring.SpringContextListener
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.tools.IslUtils

import groovy.util.logging.Slf4j
import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext

/**
 * This extension is responsible for cleaning the test environment up at the start of the test run
 * against a virtual topology.
 */
@Slf4j
class VirtualEnvCleanupExtension extends AbstractGlobalExtension implements SpringContextListener {

    @Autowired
    TopologyDefinition topology

    @Autowired
    NorthboundService northboundService

    @Autowired
    Database db

    @Autowired
    IslUtils islUtils

    @Override
    void start() {
        SpringContextExtension.addListener(this)
    }

    @Override
    void notifyContextInitialized(ApplicationContext applicationContext) {
        if (applicationContext.environment.getActiveProfiles().contains("virtual")) {
            applicationContext.autowireCapableBeanFactory.autowireBean(this)

            log.info("Deleting all flows")
            northboundService.deleteAllFlows()

            log.info("Resetting available bandwidth on all links")
            topology.islsForActiveSwitches.collect { [it, islUtils.reverseIsl(it)] }.flatten().each {
                db.revertIslBandwidth(it)
            }

            log.info("Deleting all link props")
            northboundService.deleteLinkProps(northboundService.getAllLinkProps())

            log.info("Resetting all link costs")
            db.resetCosts()
        }
    }
}
