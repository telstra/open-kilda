package org.openkilda.functionaltests.extension.env

import groovy.util.logging.Slf4j
import org.springframework.context.ApplicationContext

/**
 * This extension is responsible for cleaning the test environment up at the start of the test run
 * against a virtual topology.
 */
@Slf4j
class VirtualEnvCleanupExtension extends EnvCleanupExtension {

    @Override
    void notifyContextInitialized(ApplicationContext applicationContext) {
        applicationContext.autowireCapableBeanFactory.autowireBean(this)
        if (profile == "virtual") {
            deleteAllFlows()
            unsetSwitchMaintenance()
            def links = northbound.getAllLinks()
            unsetLinkMaintenance(links)
            deleteLinkProps()
            resetCosts()
            resetBandwidth(links)
        }
    }
}
