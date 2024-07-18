package org.openkilda.functionaltests.extension.env

import static org.openkilda.functionaltests.extension.env.EnvType.VIRTUAL_ENV

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
        if (profile == VIRTUAL_ENV.value) {
            topology.switches.each { database.removeConnectedDevices(it.dpId) }
            def links = northbound.getAllLinks()
            deleteInactiveIsls(links)
            def switches = northbound.getAllSwitches()
            deleteInactiveSwitches(switches)
            unsetSwitchMaintenance(switches)
            unsetLinkMaintenance(links)
            deleteLinkProps()
            resetCosts()
            resetBandwidth(links)
        }
    }
}
