package org.openkilda.functionaltests.helpers

import groovy.util.logging.Slf4j
import org.openkilda.functionaltests.model.cleanup.CleanupAfter
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.northbound.dto.v2.links.BfdProperties
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.tools.IslUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.DELETE_ISLS_PROPERTIES
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.OTHER
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESET_ISLS_COST
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESET_ISL_AVAILABLE_BANDWIDTH
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_ISL
import static org.openkilda.functionaltests.model.cleanup.CleanupAfter.TEST
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

/**
 * Holds utility methods for manipulating y-flows.
 */
@Component
@Slf4j
@Scope(SCOPE_PROTOTYPE)
class IslHelper {
    @Autowired
    IslUtils islUtils
    @Autowired
    PortAntiflapHelper antiflapHelper
    @Autowired
    CleanupManager cleanupManager
    @Autowired
    Database database
    @Autowired
    TopologyDefinition topology
    @Autowired @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundV2


    def breakIsl(Isl islToBreak, CleanupAfter cleanupAfter = TEST) {
        cleanupManager.addAction(RESTORE_ISL,{restoreIsl(islToBreak)}, cleanupAfter)
        cleanupManager.addAction(RESET_ISLS_COST,{database.resetCosts(topology.isls)}, cleanupAfter)
        if (getIslStatus(islToBreak).equals(DISCOVERED)) {
            antiflapHelper.portDown(islToBreak.getSrcSwitch().getDpId(), islToBreak.getSrcPort())
        }
        islUtils.waitForIslStatus([islToBreak], FAILED)
    }

    def breakIsls(Set<Isl> islsToBreak, CleanupAfter cleanupAfter = TEST) {
        withPool {
            islsToBreak.eachParallel{
                breakIsl(it, cleanupAfter)
            }
        }
    }

    def breakIsls(List<Isl> islsToBreak, CleanupAfter cleanupAfter = TEST) {
        breakIsls(islsToBreak as Set, cleanupAfter)
    }

    def restoreIsl(Isl islToRestore) {
        if(!getIslStatus(islToRestore).equals(DISCOVERED)) {
            withPool{
                [{antiflapHelper.portUp(islToRestore.getSrcSwitch().getDpId(), islToRestore.getSrcPort())},
                 {antiflapHelper.portUp(islToRestore.getDstSwitch().getDpId(), islToRestore.getDstPort())}
                ].eachParallel{it()}
            }
        }
        islUtils.waitForIslStatus([islToRestore], DISCOVERED)
    }

    def restoreIsls(Set<Isl> islsToRestore) {
        withPool {
            islsToRestore.eachParallel{
                restoreIsl(it)
            }
        }
    }

    def restoreIsls(List<Isl> islsToRestore) {
        restoreIsls(islsToRestore as Set)
    }

    def getIslStatus(Isl isl) {
        def islInfo = islUtils.getIslInfo(isl)
        if (islInfo.isPresent()) {
            return islInfo.get().state
        } else {
            return null
        }
    }

    def setAvailableBandwidth(Isl isl, long newValue) {
        cleanupManager.addAction(RESET_ISL_AVAILABLE_BANDWIDTH, {resetAvailableBandwidth([isl, isl.reversed])})
        database.updateIslAvailableBandwidth(isl, newValue)
    }
    def setAvailableBandwidth(List<Isl> isls, long newValue) {
        cleanupManager.addAction(RESET_ISL_AVAILABLE_BANDWIDTH, {resetAvailableBandwidth(isls + isls.collect{it.reversed})})
        isls.each {database.updateIslAvailableBandwidth(it, newValue)}
    }

    def resetAvailableBandwidth(List<Isl> isls) {
        database.resetIslsBandwidth(isls)
    }

    def updateLinkMaxBandwidthUsingApi(Isl isl, long newMaxBandwidth) {
        cleanupManager.addAction(DELETE_ISLS_PROPERTIES, {deleteAllLinksProperties()})
        northbound.updateLinkMaxBandwidth(isl.srcSwitch.dpId, isl.srcPort, isl.dstSwitch.dpId,
                isl.dstPort, newMaxBandwidth)
    }

    def deleteAllLinksProperties() {
        return northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
    }

    def setLinkBfd(Isl isl, BfdProperties props = new BfdProperties(350L, (short) 3)) {
        cleanupManager.addAction(OTHER, {deleteLinkBfd(isl)})
        return northboundV2.setLinkBfd(isl, props)
    }

    def setLinkBfdFromApiV1(Isl isl, boolean isEnabled) {
        cleanupManager.addAction(OTHER, {deleteLinkBfd(isl)})
        return northbound.setLinkBfd(islUtils.toLinkEnableBfd(isl, isEnabled))
    }

    def deleteLinkBfd(Isl isl) {
        return northboundV2.deleteLinkBfd(isl)
    }

    def setLinkMaintenance(Isl isl, boolean isUnderMaintenance, boolean isEvacuate) {
        cleanupManager.addAction(OTHER, {unsetLinkMaintenance(isl)})
        return northbound.setLinkMaintenance(islUtils.toLinkUnderMaintenance(isl, isUnderMaintenance, isEvacuate))
    }

    def unsetLinkMaintenance(Isl isl) {
        northbound.setLinkMaintenance(islUtils.toLinkUnderMaintenance(isl, false, false))
    }
}
