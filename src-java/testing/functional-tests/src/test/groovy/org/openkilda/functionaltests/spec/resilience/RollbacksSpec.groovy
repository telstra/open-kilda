package org.openkilda.functionaltests.spec.resilience

import static org.openkilda.functionaltests.helpers.thread.FlowHistoryConstants.PATH_SWAP_ACTION
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchStatus
import org.openkilda.northbound.dto.v1.flows.PingInput
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.util.logging.Slf4j
import spock.lang.Unroll

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
        def blockData = switchHelper.knockoutSwitch(switchToBreak, mgmtFlManager)
        database.setSwitchStatus(switchToBreak.dpId, SwitchStatus.ACTIVE)

        when: "Main path of the flow breaks initiating a reroute"
        def portDown = antiflap.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getLink(islToBreak).state == IslChangeType.FAILED
        }

        then: "System fails to install rules on desired path and tries to retry path installation"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET, 0.1) {
            assert northbound.getFlowHistory(flow.flowId).find {
                it.action == "Flow rerouting" && it.taskId =~ (/.+ : retry #1/)
            }
        }

        when: "Switch is officially marked as offline"
        database.setSwitchStatus(switchToBreak.dpId, SwitchStatus.INACTIVE)

        then: "System finds another working path and successfully reroutes the flow (one of the auto-retries succeed)"
        Wrappers.wait(PATH_INSTALLATION_TIME) {
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP
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
            switchHelper.reviveSwitch(switchToBreak, blockData)
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

    @Tidy
    @Unroll
    def "System tries to retry rule installation during #data.description if previous one is failed"(){
        given: "Two active neighboring switches with two diverse paths at least"
        def allPaths
        def swPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            allPaths = it.paths
            allPaths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size() >= 2 &&
                    allPaths.find { it.size() > 2 }
        }

        List<PathNode> mainPath = allPaths.min { it.size() }
        //find path with more than two switches
        List<PathNode> protectedPath = allPaths.findAll { it != mainPath && it.size() != 2 }.min { it.size() }

        and: "All alternative paths unavailable (bring ports down)"
        def broughtDownIsls = []
        def otherIsls = []
        def involvedIsls = (pathHelper.getInvolvedIsls(mainPath) + pathHelper.getInvolvedIsls(protectedPath)).unique()
        allPaths.findAll { it != mainPath && it != protectedPath }.each {
            pathHelper.getInvolvedIsls(it).findAll { !(it in involvedIsls || it.reversed in involvedIsls) }.each {
                otherIsls.add(it)
            }
        }
        broughtDownIsls = otherIsls.unique { a, b -> a == b || a == b.reversed ? 0 : 1 }
        broughtDownIsls.every { antiflap.portDown(it.srcSwitch.dpId, it.srcPort) }
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == IslChangeType.FAILED
            }.size() == otherIsls.size() * 2
        }

        and: "A protected flow"
        /** At this point we have the following topology:
         *
         *   srcSwitch - - - - - dstSwitch <- swToManipulate
         *          \              /
         *           \           /
         *           transitSwitch
         *
         **/
        def flow = flowHelperV2.randomFlow(swPair)
        flow.allocateProtectedPath = true
        flowHelperV2.addFlow(flow)
        def flowPathInfo = northbound.getFlowPath(flow.flowId)
        assert pathHelper.convert(flowPathInfo) == mainPath
        assert pathHelper.convert(flowPathInfo.protectedPath) == protectedPath

        when: "Disconnect dst switch on protected path"
        def swToManipulate = swPair.dst
        def blockData = switchHelper.knockoutSwitch(swToManipulate, mgmtFlManager)
        def isSwitchActivated = false

        and: "Mark the transit switch as ACTIVE in db"
        database.setSwitchStatus(swToManipulate.dpId, SwitchStatus.ACTIVE)

        and: "Init flow #data.description"
        data.action(flow)

        then: "System retried to #data.description"
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getFlowHistory(flow.flowId).findAll {
                it.action == data.historyAction
            }.last().histories*.details.findAll{ it =~ /.+ Retrying/}.size() == data.retriesAmount
        }

        then: "Flow is DOWN"
        Wrappers.wait(WAIT_OFFSET) {
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.DOWN
        }

        and: "Flow is not rerouted"
        def flowPathInfoAfterSwap = northbound.getFlowPath(flow.flowId)
        pathHelper.convert(flowPathInfoAfterSwap) == mainPath
        pathHelper.convert(flowPathInfoAfterSwap.protectedPath) == protectedPath


        and: "All involved switches pass switch validation(except dst switch)"
        def involvedSwitchIds = pathHelper.getInvolvedSwitches(protectedPath)[0..-2]*.dpId
        Wrappers.wait(WAIT_OFFSET / 2) {
            involvedSwitchIds.each { swId ->
                with(northbound.validateSwitch(swId)) { validation ->
                    validation.verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
                    validation.verifyMeterSectionsAreEmpty(["missing", "excess", "misconfigured"])
                }
            }
        }

        when: "Connect dst switch back to the controller"
        database.setSwitchStatus(swToManipulate.dpId, SwitchStatus.INACTIVE) //set real status
        switchHelper.reviveSwitch(swPair.dst, blockData)
        isSwitchActivated = true

        then: "Flow is UP"
        Wrappers.wait(discoveryInterval + rerouteDelay + WAIT_OFFSET) {
            northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP
        }

        and: "Flow is valid and pingable"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }
        with(northbound.pingFlow(flow.flowId, new PingInput())) {
            it.forward.pingSuccess
            it.reverse.pingSuccess
        }

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
        if (!isSwitchActivated && blockData) {
            database.setSwitchStatus(swToManipulate.dpId, SwitchStatus.INACTIVE)
            switchHelper.reviveSwitch(swToManipulate, blockData)
            northbound.synchronizeSwitch(swToManipulate.dpId, true)
        }
        broughtDownIsls.every { antiflap.portUp(it.srcSwitch.dpId, it.srcPort) }
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2
        }
        database.resetCosts()

        where:
        data << [
                //issue #3237
                //[
                //       description: "update",
                //        historyAction: UPDATE_ACTION,
                //        retriesAmount: 15, //
                //        //install: ingress 2 * 3(attempts) + remove: ingress 2 * 3(attempts) + 1 egress * 3(attempts)
                //        action: { FlowRequestV2 f ->
                //           getNorthboundV2().updateFlow(f.flowId, f.tap { it.description = "updated" }) }
                //],
                [
                        description: "swap paths",
                        historyAction: PATH_SWAP_ACTION,
                        retriesAmount: 9,
                        // swap:  install: 3 attempts, revert: delete 3 attempts + install 3 attempts
                        action:  { FlowRequestV2 f ->
                            getNorthbound().swapFlowPath(f.flowId) }
                ]
        ]
    }
}
