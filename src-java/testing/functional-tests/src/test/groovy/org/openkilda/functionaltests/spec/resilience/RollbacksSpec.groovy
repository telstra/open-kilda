package org.openkilda.functionaltests.spec.resilience

import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchStatus
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.util.logging.Slf4j

@Slf4j
class RollbacksSpec extends HealthCheckSpecification {

    @Tidy
    def "System retries the reroute if it fails to install rules on one the current target path's switches"() {
        given: "Switch pair with at least 3 available paths, one path should have a transit switch that we will break \
and at least 1 path must remain safe"
        List<PathNode> mainPath, failoverPath, safePath
        Switch switchToBreak //will belong to failoverPath and be absent in safePath
        Isl islToBreak //will be used to break the mainPath. This ISL is not used in safePath or failoverPath
        def switchPair = topologyHelper.switchPairs.find { swPair ->
            if(swPair.paths.size() >= 3) {
                failoverPath = swPair.paths.find { failoverPathCandidate ->
                    def failoverSwitches = pathHelper.getInvolvedSwitches(failoverPathCandidate)
                    safePath = swPair.paths.find { safePathCandidate ->
                        def safeSwitches = pathHelper.getInvolvedSwitches(safePathCandidate)
                        mainPath = swPair.paths.find { it != failoverPathCandidate && it != safePathCandidate }
                        def mainSwitches = pathHelper.getInvolvedSwitches(mainPath)
                        switchToBreak = failoverSwitches.find { !(it in safeSwitches) && !(it in mainSwitches) }
                        def safeIsls = pathHelper.getInvolvedIsls(safePathCandidate).collectMany { [it, it.reversed] }
                        def failoverIsls = pathHelper.getInvolvedIsls(failoverPathCandidate).collectMany { [it, it.reversed] }
                        islToBreak = pathHelper.getInvolvedIsls(mainPath).collectMany { [it, it.reversed] }.find {
                            !(it in safeIsls) && !(it in failoverIsls) && it.srcSwitch != switchToBreak
                        }
                        switchToBreak && islToBreak
                    }
                }
            }
            failoverPath
        }
        assert switchPair, "Not able to find a switch pair with suitable paths"
        log.debug("main path: $mainPath\nfailover path: $failoverPath\nsafe path: $safePath\nisl to break: " +
                "$islToBreak\nswitch to break: $switchToBreak")

        and: "A flow using given switch pair"
        switchPair.paths.findAll { it != mainPath }.each { pathHelper.makePathMorePreferable(mainPath, it) }
        def flow = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(flow)
        assert pathHelper.convert(northbound.getFlowPath(flow.flowId)) == mainPath

        and: "Switch on the preferred failover path will suddenly be unavailable for rules installation when the reroute starts"
        //select a required failover path beforehand
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        switchPair.paths.findAll { it != failoverPath }.each { pathHelper.makePathMorePreferable(failoverPath, it) }
        //disconnect the switch, but make it look like 'active'
        def blockData = lockKeeper.knockoutSwitch(switchToBreak, mgmtFlManager)
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getSwitch(switchToBreak.dpId).state == SwitchChangeType.DEACTIVATED
        }
        database.setSwitchStatus(switchToBreak.dpId, SwitchStatus.ACTIVE)

        when: "Main path of the flow breaks initiating a reroute"
        def portDown = antiflap.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getLink(islToBreak).state == IslChangeType.FAILED
        }

        then: "System fails to install rules on desired path and tries to retry path installation"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET, 0.1) {
            assert northbound.getFlowHistory(flow.flowId).find {
                it.action == "Flow rerouting" && it.taskId =~ (/[^a-z_A-Z0-9-\s]* : retry #1 :/)
            }
        }

        when: "Switch is officially marked as offline"
        database.setSwitchStatus(switchToBreak.dpId, SwitchStatus.INACTIVE)

        then: "System finds another working path and successfully reroutes the flow (one of the auto-retries succeed)"
        Wrappers.wait(PATH_INSTALLATION_TIME) {
            assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP
        }
        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.flowId))
        currentPath != mainPath
        currentPath != failoverPath
        !pathHelper.getInvolvedSwitches(currentPath).contains(switchToBreak)
        !pathHelper.getInvolvedIsls(currentPath).contains(islToBreak)
        !pathHelper.getInvolvedIsls(currentPath).contains(islToBreak.reversed)

        and: "All related switches have no rule anomalies"
        [mainPath, failoverPath, currentPath].collectMany { pathHelper.getInvolvedSwitches(it) }.unique()
                .findAll { it != switchToBreak }.each {
            northbound.validateSwitch(it.dpId).verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
        }

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
        if(blockData) {
            database.setSwitchStatus(switchToBreak.dpId, SwitchStatus.INACTIVE)
            lockKeeper.reviveSwitch(switchToBreak, blockData)
            Wrappers.wait(WAIT_OFFSET) {
                assert northbound.getSwitch(switchToBreak.dpId).state == SwitchChangeType.ACTIVATED
            }
        }
        if(portDown) {
            antiflap.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        }
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2
        }
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        database.resetCosts()
    }
}
