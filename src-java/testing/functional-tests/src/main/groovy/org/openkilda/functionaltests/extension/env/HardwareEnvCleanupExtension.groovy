package org.openkilda.functionaltests.extension.env

import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.model.SwitchFeature

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
    @Value('${use.multitable}')
    boolean useMultitable

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
            unsetSwitchMaintenance(activeSwitches)
            removeFlowRules(activeSwitches)
            removeExcessMeters(activeSwitches)
            deleteLagPorts(activeSwitches)


            log.info("Configure 'multiTable/s42/islRtt' props according to the 'kilda.properties' file")
            northbound.getAllSwitches().findAll { it.state == SwitchChangeType.ACTIVATED }.each { sw ->
                if (database.getSwitch(sw.switchId).features.contains(SwitchFeature.MULTI_TABLE)) {
                    def s42Config = topology.activeSwitches.find { it.dpId == sw.switchId }.prop
                    def payload
                    if (useMultitable) {
                        payload = northbound.getSwitchProperties(sw.switchId).tap { it.multiTable = true }
                        northbound.updateSwitchProperties(sw.switchId, northbound.getSwitchProperties(sw.switchId).tap {
                            it.multiTable = true
                        })
                    } else {
                        payload = northbound.getSwitchProperties(sw.switchId).tap {
                            it.multiTable = false
                            // arp/lldp  properties can be set to 'true' only if 'multiTable' property is 'true'.
                            it.switchLldp = false
                            it.switchArp = false
                        }
                    }
                    northbound.updateSwitchProperties(sw.switchId, payload.tap {
                        it.server42FlowRtt = s42Config.server42FlowRtt
                        it.server42Port = s42Config.server42Port
                        it.server42MacAddress = s42Config.server42MacAddress
                        it.server42Vlan = s42Config.server42Vlan
                        it.server42IslRtt = (s42Config.server42IslRtt == null ?
                                "AUTO" : (s42Config.server42IslRtt ? "ENABLED" : "DISABLED"))
                    })
                }
            }
        }
    }
}
