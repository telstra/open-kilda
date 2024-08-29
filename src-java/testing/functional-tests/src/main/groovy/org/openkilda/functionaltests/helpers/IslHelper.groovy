package org.openkilda.functionaltests.helpers

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.CLEAN_LINK_DELAY
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.DELETE_ISLS_PROPERTIES
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.OTHER
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESET_ISLS_COST
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESET_ISL_PARAMETERS
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_ISL
import static org.openkilda.functionaltests.model.cleanup.CleanupAfter.TEST
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.messaging.info.event.IslChangeType.MOVED
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.helpers.thread.PortBlinker
import org.openkilda.functionaltests.model.cleanup.CleanupAfter
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.northbound.dto.v2.links.BfdProperties
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.tools.IslUtils

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import java.util.AbstractMap.SimpleEntry
import java.util.stream.Collectors

/**
 * Holds utility methods for manipulating y-flows.
 */
@Component
@Slf4j
@Scope(SCOPE_PROTOTYPE)
class IslHelper {
    static final Integer NOT_PREFERABLE_COST = 99999999

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
    @Autowired
    LockKeeperService lockKeeperService
    @Value('${discovery.exhausted.interval}')
    int discoveryExhaustedInterval
    @Autowired @Qualifier("kafkaProducerProperties")
    Properties producerProps
    @Value("#{kafkaTopicsConfig.getTopoDiscoTopic()}")
    String topoDiscoTopic

    def breakIsl(Isl islToBreak, CleanupAfter cleanupAfter = TEST) {
        cleanupManager.addAction(RESTORE_ISL,{restoreIsl(islToBreak)}, cleanupAfter)
        cleanupManager.addAction(RESET_ISLS_COST,{database.resetCosts(topology.isls)}, cleanupAfter)
        if (getIslStatus(islToBreak).equals(DISCOVERED)) {
            antiflapHelper.portDown(islToBreak.getSrcSwitch().getDpId(), islToBreak.getSrcPort(), cleanupAfter, false)
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
        cleanupManager.addAction(RESET_ISL_PARAMETERS, {resetAvailableBandwidth([isl, isl.reversed])})
        database.updateIslMaxBandwidth(isl, newValue)
        database.updateIslAvailableBandwidth(isl, newValue)
    }

    def setAvailableBandwidth(List<Isl> isls, long newValue) {
        cleanupManager.addAction(RESET_ISL_PARAMETERS, {resetAvailableBandwidth(isls + isls.collect{it.reversed})})
        isls.each {
            database.updateIslMaxBandwidth(it, newValue)
            database.updateIslAvailableBandwidth(it, newValue)
        }
    }

    def resetAvailableBandwidth(List<Isl> isls) {
        database.resetIslsBandwidth(isls)
    }

    def updateLinkMaxBandwidthUsingApi(Isl isl, long newMaxBandwidth) {
        cleanupManager.addAction(DELETE_ISLS_PROPERTIES, {deleteAllLinksProperties()})
        northbound.updateLinkMaxBandwidth(isl.srcSwitch.dpId, isl.srcPort, isl.dstSwitch.dpId,
                isl.dstPort, newMaxBandwidth)
    }

    void updateIslsCostInDatabase(List<Isl> isls, Integer newCost) {
        cleanupManager.addAction(RESET_ISLS_COST,{ database.resetCosts(topology.isls) })
        isls.each { database.updateIslCost(it, newCost) }
    }

    /**
     * All ISLs will have their cost set to the specified value.
     */
    void updateIslsCost(List<Isl> isls, Integer newCost) {
        cleanupManager.addAction(DELETE_ISLS_PROPERTIES,{ northbound.deleteLinkProps(northbound.getLinkProps(topology.isls)) })
        northbound.updateLinkProps(isls.collectMany { isl ->
            [islUtils.toLinkProps(isl, ["cost": (newCost).toString()])]
        })
    }

    def updateIslLatency(Isl isl, long newLatency) {
        def currentLatency = northbound.getLink(isl).latency
        cleanupManager.addAction(RESET_ISL_PARAMETERS, {database.updateIslLatency(isl, currentLatency)})
        return database.updateIslLatency(isl, newLatency)
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

    def replugDestination(Isl srcIsl, Isl dstIsl, boolean plugIntoSource, boolean portDown, IslChangeType expectedRepluggedIslState = DISCOVERED) {
        def newIsl = islUtils.replug(srcIsl, false, dstIsl, plugIntoSource, portDown)
        wait(discoveryExhaustedInterval + WAIT_OFFSET) {
            [newIsl, newIsl.reversed].each { assert northbound.getLink(it).state == expectedRepluggedIslState }
        }
        cleanupManager.addAction(RESTORE_ISL, {
                    islUtils.replug(newIsl, true, srcIsl, !plugIntoSource, portDown)
                    islUtils.waitForIslStatus([srcIsl, srcIsl.reversed], DISCOVERED)
                    islUtils.waitForIslStatus([newIsl, newIsl.reversed], MOVED)
                    northbound.deleteLink(islUtils.toLinkParameters(newIsl))
                    wait(WAIT_OFFSET) { assert !islUtils.getIslInfo(newIsl).isPresent() }
                    database.resetCosts(topology.isls)
                }
        )
        return newIsl
    }

    //TOOD: replace boolean parameter with enum FORCE/NOT_FORCE
    def deleteIsl(Isl isl, boolean isForce = false) {
        cleanupManager.addAction(RESTORE_ISL, {
            def links = northbound.getAllLinks()
            def forwardIsl = islUtils.getIslInfo(links, isl)
            def reverseIsl = islUtils.getIslInfo(links, isl.reversed)
           if(!((forwardIsl.isPresent() && reverseIsl.isPresent()) && (forwardIsl.get().state == DISCOVERED && reverseIsl.get().state == DISCOVERED))) {
               northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)
               northbound.portDown(isl.dstSwitch.dpId, isl.dstPort)
           }
            restoreIsl(isl)
            database.resetCosts([isl, isl.reversed])
        })
        return northbound.deleteLink(islUtils.toLinkParameters(isl), isForce)
    }

    def getPortBlinkerForSource(Isl isl, long interval) {
        def blinker = new PortBlinker(producerProps, topoDiscoTopic, isl.srcSwitch, isl.srcPort, interval)
        cleanupManager.addAction(RESTORE_ISL, {
            closePortBlinker(blinker)
            restoreIsl(isl)
            islUtils.waitForIslStatus([isl], DISCOVERED)
        })
        return blinker
    }

    def setDelay(String bridgeName, Integer delayMs) {
        cleanupManager.addAction(CLEAN_LINK_DELAY, {lockKeeperService.cleanupLinkDelay(bridgeName)})
        return lockKeeperService.setLinkDelay(bridgeName, delayMs)
    }

    private def closePortBlinker(PortBlinker blinker) {
        blinker?.isRunning() && blinker.stop(true)
    }

    /**
     * If required, makes one path more preferable than another.
     * Finds a unique ISL of less preferable path and adds 'cost difference between paths + 1' to its cost
     *
     * @param morePreferablePathIsls ISLs list that should become more preferable over the 'lessPreferablePathIsls'
     * @param lessPreferablePathIsls ISLs list that should become less preferable compared to 'morePreferablePathIsls'
     * @return The changed ISL (one-way ISL, but actually changed in both directions) or null
     */
    Isl makePathIslsMorePreferable(List<Isl> morePreferablePathIsls, List<Isl> lessPreferablePathIsls) {
        List<Isl> uniqueIsls = (morePreferablePathIsls + lessPreferablePathIsls)
                .unique { a, b -> a == b || a == b.reversed ? 0 : 1 }
        HashMap<Isl, Integer> islCosts = uniqueIsls.parallelStream().flatMap({ isl ->
            Integer cost = northbound.getLink(isl).cost ?: 700
            [isl, isl.reversed].stream().map({ biIsl -> new SimpleEntry<>(biIsl, cost) })
        }).collect(Collectors.toMap({ it.getKey() }, { it.getValue() }))
        // under specific condition cost of isl can be 0, but at the same time for the system 0 == 700
        def totalCostOfMorePrefPath = morePreferablePathIsls.sum { islCosts.get(it) }
        def totalCostOfLessPrefPath = lessPreferablePathIsls.sum { islCosts.get(it) }
        def difference = totalCostOfMorePrefPath - totalCostOfLessPrefPath
        def islToAvoid
        if (difference >= 0) {
            islToAvoid = lessPreferablePathIsls.find {
                !morePreferablePathIsls.contains(it) && !morePreferablePathIsls.contains(it.reversed)
            }
            if (!islToAvoid) {
                //this should be impossible
                throw new Exception("Unable to make some path more preferable because both paths use same ISLs")
            }
            log.debug "ISL to avoid: $islToAvoid"
            cleanupManager.addAction(DELETE_ISLS_PROPERTIES, {northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))})
            northbound.updateLinkProps([islUtils.toLinkProps(islToAvoid,
                    ["cost": (islCosts.get(islToAvoid) + difference + 1).toString()])])
        }
        return islToAvoid
    }

    /**
     * Get total cost of all ISLs that are involved in a given path.
     *
     * @param isls represents a given path
     * @return total path cost
     */
    int getCost(List<Isl> isls) {
        return isls.sum { database.getIslCost(it) } as int
    }

    /**
     * Get all switches that are involved in a given path.
     *
     * @param isls represents a given path
     * @return list of switchIds
     */
    List<Switch> retrieveInvolvedSwitches(List<Isl> isls) {
        isls.collectMany{ [it.srcSwitch, it.dstSwitch] }.unique()
    }
}
