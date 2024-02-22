package org.openkilda.functionaltests.listeners

import static org.openkilda.functionaltests.extension.tags.Tag.ISL_PROPS_DB_RESET
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.testing.Constants.TOPOLOGY_DISCOVERING_TIME

import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.PortAntiflapHelper
import org.openkilda.functionaltests.helpers.SwitchHelper
import org.openkilda.model.SwitchStatus
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.floodlight.FloodlightsHelper
import org.openkilda.testing.service.lockkeeper.model.FloodlightResourceAddress
import org.openkilda.testing.service.northbound.NorthboundService

import groovy.util.logging.Slf4j
import org.spockframework.runtime.model.ErrorInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

@Slf4j
class DoCleanupListener extends AbstractSpringListener {

    @Autowired
    TopologyDefinition topology

    @Autowired @Qualifier("islandNb")
    NorthboundService northbound

    @Autowired
    Database database

    @Autowired
    PortAntiflapHelper antiflap

    @Autowired
    SwitchHelper switchHelper

    @Autowired
    FloodlightsHelper flHelper


    @Override
    void error(ErrorInfo error) {
        context && context.autowireCapableBeanFactory.autowireBean(this)
        def thrown = error.exception
        if (thrown instanceof AssertionError) {
            if (thrown.getMessage() && thrown.getMessage().contains("SwitchValidationExtendedResult(")) {
                switchHelper.synchronizeAndCollectFixedDiscrepancies(topology.activeSwitches*.dpId)
            }
        }
        if (error.method.name && SWITCH_RECOVER_ON_FAIL in error.method.getAnnotation(Tags)?.value()) {
            log.info("Verifying all switches are registered in FloodLight due to the failure in " + error.method.parent.name)
            def registeredFlSwitches = flHelper.fls.findAll { !it.region.contains("stats") }
                    .collect { fl -> fl.floodlightService.switches.switchId }.flatten().unique()
            List<Switch> switchesToRegisterInFl = topology.getSwitches()
                    .collect { sw ->
                        if (!registeredFlSwitches.contains(sw.dpId)) {
                            return sw
                        }
                    }.findAll()

            log.info("The following switches should be registered in FloodLight: " + switchesToRegisterInFl.dpId)
            switchesToRegisterInFl.each { sw ->
                def switchDetails = database.getSwitch(sw.dpId)
                switchDetails.status == SwitchStatus.ACTIVE && database.setSwitchStatus(sw.dpId, SwitchStatus.INACTIVE)
                List<FloodlightResourceAddress> addresses = []
                sw.regions.each { swRegion ->
                    //virtual env: use only flRegion for adding new controller(sw)
                    //hardware env: random port is assigned by floodlight (unlock switch)
                    !swRegion.contains("stats") && addresses.add(new FloodlightResourceAddress(swRegion, flHelper.getFlByRegion(swRegion).getContainer(),
                            switchDetails.socketAddress.address, switchDetails.socketAddress.port))
                }
                switchHelper.reviveSwitch(sw, addresses)
                log.info("The switch " + sw.dpId + " has been successfully registered in FloodLight")
                switchHelper.synchronize(sw.dpId)
            }
        }
        if (error.method.name && ISL_PROPS_DB_RESET in error.method.getAnnotation(Tags)?.value()) {
            log.info("Resetting ISLs bandwidth due to the failure in " + error.method.parent.name + "\nISLs: " + topology.isls)
            database.resetIslsBandwidth(topology.isls)
            log.info("Resetting ISLs cost due to the failure in " + error.method.parent.name)
            database.resetCosts(topology.isls)
        }

        if (error.method.name && ISL_RECOVER_ON_FAIL in error.method.getAnnotation(Tags)?.value()) {
            def failedLinks = northbound.getAllLinks().findAll { it.state == FAILED }
            failedLinks && log.info("ISLs recovering(ports up) due to the failure in " + error.method.parent.name)
            failedLinks.collectMany { return [it.source.switchId, it.destination.switchId] }.unique()
                    .each { switchId ->
                        northbound.getPorts(switchId).findAll { port -> port.config == ["PORT_DOWN"] }
                                .each { portDetails -> antiflap.portUp(switchId, portDetails.portNumber) }
                    }
            if (failedLinks) {
                log.info("Waiting for ISLs to become DISCOVERED")
                wait(TOPOLOGY_DISCOVERING_TIME) {
                    assert northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2
                }
            }
        }
    }
}
