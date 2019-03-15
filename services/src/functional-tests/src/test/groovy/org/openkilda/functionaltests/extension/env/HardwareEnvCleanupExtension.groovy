package org.openkilda.functionaltests.extension.env

import org.openkilda.messaging.info.event.SwitchChangeType

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext

/**
 * This extension is responsible for cleaning the test environment up at the start of the test run
 * against a hardware topology.<br>
 * Turned on on-demand by passing a `-Denv.hardware.cleanup=true` property to the build params.<br>
 * This has no guarantee to fully repair the broken env and should be used with care,
 * especially if the target environment is shared between many people.<br>
 *
 * WARNING: do not over-use this option. Cleanup may be destructive and mask potential defects related to improper
 * topology discovery, default rules setup on switch discovery, bandwidth discovery etc.
 */
@Slf4j
class HardwareEnvCleanupExtension extends EnvCleanupExtension {
    @Value('${env.hardware.cleanup:false}')
    boolean cleanup

    @Override
    void notifyContextInitialized(ApplicationContext applicationContext) {
        applicationContext.autowireCapableBeanFactory.autowireBean(this)
        if (profile == "hardware" && cleanup) {
            log.warn("Cleanup mode is ON. Cleanup may be destructive and mask potential defects related to improper" +
                    " topology discovery, default rules setup on switch discovery, bandwidth discovery etc.")
            deleteAllFlows()
            //ISL-related things
            def links = northbound.getAllLinks()
            unsetLinkMaintenance(links)
            reviveFailedLinks(links)
            deleteLinkProps()
            resetCosts()
            resetBandwidth(links)

            //a-switch rules
            resetAswRules()

            //now switches
            def activeSwitches = northbound.getAllSwitches().findAll { it.state == SwitchChangeType.ACTIVATED }
            unsetSwitchMaintenance()
            removeFlowRules(activeSwitches)
            removeExcessMeters(activeSwitches)
        }
    }
}
