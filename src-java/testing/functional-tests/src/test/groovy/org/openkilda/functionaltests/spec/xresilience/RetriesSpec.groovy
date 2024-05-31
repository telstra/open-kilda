package org.openkilda.functionaltests.spec.xresilience

import groovy.util.logging.Slf4j
import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchStatus
import org.openkilda.northbound.dto.v1.flows.PingInput
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.lockkeeper.model.TrafficControlData
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Isolated
import spock.lang.Shared

import java.util.concurrent.TimeUnit

import static org.openkilda.functionaltests.extension.tags.Tag.ISL_PROPS_DB_RESET
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOCKKEEPER
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.DELETE_SUCCESS
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.PATH_SWAP_ACTION
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_ACTION
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_FAIL
import static org.openkilda.functionaltests.helpers.Wrappers.timedLoop
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESET_ISLS_COST
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_ISL
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

@Slf4j
class RetriesSpec extends HealthCheckSpecification {

    @Tags([ISL_RECOVER_ON_FAIL, ISL_PROPS_DB_RESET, SWITCH_RECOVER_ON_FAIL])
    def "System retries the reroute (global retry) if it fails to install rules on one of the current target path's switches"() {
        given: "Switch pair with at least 3 available paths, one path should have a transit switch that we will break \
and at least 1 path must remain safe"
        List<PathNode> mainPath, failoverPath, safePath
        Switch switchToBreak //will belong to failoverPath and be absent in safePath
        Isl islToBreak //will be used to break the mainPath. This ISL is not used in safePath or failoverPath
        def switchPair = switchPairs.all().getSwitchPairs().find { swPair ->
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
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
        switchPair.paths.findAll { it != failoverPath }.each { pathHelper.makePathMorePreferable(failoverPath, it) }
        //disconnect the switch, but make it look like 'active'
        def blockData = switchHelper.knockoutSwitch(switchToBreak, RW)
        database.setSwitchStatus(switchToBreak.dpId, SwitchStatus.ACTIVE)

        when: "Main path of the flow breaks initiating a reroute"
        islHelper.breakIsl(islToBreak)

        then: "System fails to install rules on desired path and tries to retry reroute and find new path (global retry)"
        wait(WAIT_OFFSET * 3, 0.1) {
            assert flowHelper.getHistoryEntriesByAction(flow.flowId, REROUTE_ACTION).find {
                it.taskId =~ (/.+ : retry #1/)
            }
        }

        when: "Switch is marked as offline"
        database.setSwitchStatus(switchToBreak.dpId, SwitchStatus.INACTIVE)

        then: "System finds another working path and successfully reroutes the flow (one of the retries succeeds)"
        wait(PATH_INSTALLATION_TIME) {
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP
        }
        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.flowId))
        currentPath != mainPath
        currentPath != failoverPath
        !pathHelper.getInvolvedSwitches(currentPath).contains(switchToBreak)
        !pathHelper.getInvolvedIsls(currentPath).contains(islToBreak)
        !pathHelper.getInvolvedIsls(currentPath).contains(islToBreak.reversed)

        and: "All related switches have no rule anomalies"
        def switchesToVerify = [mainPath, failoverPath, currentPath].collectMany { pathHelper.getInvolvedSwitches(it) }.unique()
                .findAll { it != switchToBreak }
        switchHelper.validateAndCollectFoundDiscrepancies(switchesToVerify*.getDpId()).isEmpty()
    }

    @Tags([SMOKE_SWITCHES, LOCKKEEPER, ISL_RECOVER_ON_FAIL, ISL_PROPS_DB_RESET, SWITCH_RECOVER_ON_FAIL])
    def "System tries to retry rule installation during #data.description if previous one is failed"(){
        given: "Two active neighboring switches with two diverse paths at least"
        def swPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(2).random()
        def allPaths = swPair.getPaths()
        List<PathNode> mainPath = allPaths.min { it.size() }
        //find path with more than two switches
        List<PathNode> protectedPath = allPaths.findAll { it != mainPath && it.size() != 2 }.min { it.size() }

        and: "All alternative paths unavailable (bring ports down)"
        def altIsls = topology.getRelatedIsls(swPair.src) - pathHelper.getInvolvedIsls(mainPath).first() -
                pathHelper.getInvolvedIsls(protectedPath).first()
        islHelper.breakIsls(altIsls)

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
        def blockData = switchHelper.knockoutSwitch(swToManipulate, RW)
        def isSwitchActivated = false

        and: "Mark the transit switch as ACTIVE in db"
        database.setSwitchStatus(swToManipulate.dpId, SwitchStatus.ACTIVE)

        and: "Init flow #data.description"
        data.action(flow)

        then: "System retried to #data.description"
        wait(WAIT_OFFSET) {
            assert flowHelper.getHistoryEntriesByAction(flow.flowId, data.historyAction)
            .last().payload*.details.findAll{ it =~ /.+ Retrying/}.size() == data.retriesAmount
        }

        then: "Flow is DOWN"
        wait(WAIT_OFFSET) {
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.DOWN
        }

        and: "Flow is not rerouted"
        def flowPathInfoAfterSwap = northbound.getFlowPath(flow.flowId)
        pathHelper.convert(flowPathInfoAfterSwap) == mainPath
        pathHelper.convert(flowPathInfoAfterSwap.protectedPath) == protectedPath


        and: "All involved switches pass switch validation(except dst switch)"
        def involvedSwitchIds = pathHelper.getInvolvedSwitches(protectedPath)[0..-2]*.dpId
        wait(WAIT_OFFSET / 2) {
            switchHelper.validateAndCollectFoundDiscrepancies(involvedSwitchIds).isEmpty()
        }

        when: "Connect dst switch back to the controller"
        database.setSwitchStatus(swToManipulate.dpId, SwitchStatus.INACTIVE) //set real status
        switchHelper.reviveSwitch(swPair.dst, blockData)
        isSwitchActivated = true

        then: "Flow is UP"
        wait(discoveryInterval + rerouteDelay + WAIT_OFFSET) {
            northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP
        }

        and: "Flow is valid and pingable"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }
        with(northbound.pingFlow(flow.flowId, new PingInput())) {
            it.forward.pingSuccess
            it.reverse.pingSuccess
        }

        cleanup:
        if (!isSwitchActivated && blockData) {
            database.setSwitchStatus(swToManipulate.dpId, SwitchStatus.INACTIVE)
            switchHelper.reviveSwitch(swToManipulate, blockData)
            switchHelper.synchronize(swToManipulate.dpId)
        }
        database.resetCosts(topology.isls)

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
                        retriesAmount: 15,
                        // swap:  install: 3 attempts, revert: delete 9 attempts + install 3 attempts
                        // delete: 3 attempts * (1 flow rule + 1 ingress mirror rule + 1 egress mirror rule) = 9 attempts
                        action:  { FlowRequestV2 f ->
                            getNorthbound().swapFlowPath(f.flowId) }
                ]
        ]
    }

    @Tags([SWITCH_RECOVER_ON_FAIL])
    def "Flow is successfully deleted from the system even if some rule delete commands fail (no rollback for delete)"() {
        given: "A flow"
        def swPair = switchPairs.all().first()
        def flow = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow)

        when: "Send delete request for the flow"
        switchHelper.shapeSwitchesTraffic([swPair.src], new TrafficControlData(1000))
        northboundV2.deleteFlow(flow.flowId)

        and: "One of the related switches does not respond"
        switchHelper.knockoutSwitch(swPair.src, RW)

        then: "Flow history shows failed delete rule retry attempts but flow deletion is successful at the end"
        wait(WAIT_OFFSET) {
            def history = flowHelper.getLatestHistoryEntry(flow.flowId).payload
            //egress and ingress rule and egress and ingress mirror rule on a broken switch, 3 retries each = total 12
            assert history.count { it.details ==~ /Failed to remove the rule.*Retrying \(attempt \d+\)/ } == 12
            assert history.last().action == DELETE_SUCCESS
        }
        !northboundV2.getFlowStatus(flow.flowId)
    }

    @Tags([ISL_RECOVER_ON_FAIL, ISL_PROPS_DB_RESET, SWITCH_RECOVER_ON_FAIL])
    def "System retries the intentional rerouteV1 if it fails to install rules on a switch"() {
        given: "Two active neighboring switches with two diverse paths at least(main and backup paths)"
        def swPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(2).random()

        List<PathNode> mainPath = swPair.paths.min { it.size() }
        //find path with more than two switches
        List<PathNode> backupPath = swPair.paths.findAll { it != mainPath && it.size() != 2 }.min { it.size() }

        and: "All alternative paths unavailable (bring ports down)"
        def altIsls = topology.getRelatedIsls(swPair.src) - pathHelper.getInvolvedIsls(mainPath).first() -
                pathHelper.getInvolvedIsls(backupPath).first()
        islHelper.breakIsls(altIsls)

        and: "A flow on the main path"
        def flow = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow)
        assert pathHelper.convert(northbound.getFlowPath(flow.flowId)) == mainPath

        when: "Make backupPath more preferable than mainPath"
        pathHelper.makePathMorePreferable(backupPath, mainPath)

        and: "Disconnect the dst switch"
        def swToManipulate = swPair.dst
        def blockData = switchHelper.knockoutSwitch(swToManipulate, RW)
        def isSwitchActivated = false

        and: "Mark the dst switch as ACTIVE in db"
        database.setSwitchStatus(swToManipulate.dpId, SwitchStatus.ACTIVE)

        and: "Init intentional flow reroute(APIv1)"
        northbound.rerouteFlow(flow.flowId)

        then: "System retries to install/delete rules on the dst switch"
        wait(WAIT_OFFSET) {
            assert flowHelper.getHistoryEntriesByAction(flow.flowId, REROUTE_ACTION)
                .last().payload*.details.findAll{ it =~ /.+ Retrying/}.size() == 15
            //install: 3 attempts, revert: delete 9 attempts + install 3 attempts
            // delete: 3 attempts * (1 flow rule + 1 ingress mirror rule + 1 egress mirror rule) = 9 attempts
        }

        then: "Flow is not rerouted"
        pathHelper.convert(northbound.getFlowPath(flow.flowId)) == mainPath

        and: "All involved switches pass switch validation(except dst switch)"
        def involvedSwitchIds = pathHelper.getInvolvedSwitches(backupPath)[0..-2]*.dpId
        wait(WAIT_OFFSET / 2) {
            switchHelper.validateAndCollectFoundDiscrepancies(involvedSwitchIds).isEmpty()
        }

        when: "Connect dst switch back to the controller"
        database.setSwitchStatus(swToManipulate.dpId, SwitchStatus.INACTIVE) //set real status
        switchHelper.reviveSwitch(swPair.dst, blockData)
        isSwitchActivated = true

        then: "Flow is UP"
        wait(discoveryInterval + rerouteDelay + WAIT_OFFSET) {
            northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP
        }

        and: "Flow is valid and pingable"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }
        with(northbound.pingFlow(flow.flowId, new PingInput())) {
            it.forward.pingSuccess
            it.reverse.pingSuccess
        }

        cleanup:
        if (!isSwitchActivated && blockData) {
            database.setSwitchStatus(swToManipulate.dpId, SwitchStatus.INACTIVE)
            switchHelper.reviveSwitch(swToManipulate, blockData)
            switchHelper.synchronize(swToManipulate.dpId)
        }
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
        database.resetCosts(topology.isls)
    }
}


@Slf4j
@Isolated
class RetriesIsolatedSpec extends HealthCheckSpecification {
    @Shared int globalTimeout = 30 //global timeout for h&s operation
    @Autowired @Shared
    CleanupManager cleanupManager

    //isolation: requires no 'up' events in the system while flow is Down
    @Tags([ISL_RECOVER_ON_FAIL])
    def "System does not retry after global timeout for reroute operation"() {
        given: "A flow with ability to reroute"
        def swPair = switchPairs.all().withAtLeastNPaths(2).random()
        def flow = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow)

        when: "Break current path to trigger a reroute"
        def islToBreak = pathHelper.getInvolvedIsls(flow.flowId).first()
        cleanupManager.addAction(RESTORE_ISL, {islHelper.restoreIsl(islToBreak)})
        cleanupManager.addAction(RESET_ISLS_COST,{database.resetCosts(topology.isls)})
        northbound.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)

        and: "Connection to src switch is slow in order to simulate a global timeout on reroute operation"
        switchHelper.shapeSwitchesTraffic([swPair.src], new TrafficControlData(5000))

        then: "After global timeout expect flow reroute to fail and flow to become DOWN"
        TimeUnit.SECONDS.sleep(globalTimeout)
        int eventsAmount
        wait(globalTimeout + WAIT_OFFSET, 1) { //long wait, may be doing some revert actions after global t/o
            def history = northbound.getFlowHistory(flow.flowId)
            def lastEvent = history.last().payload
            assert lastEvent.find { it.action == sprintf('Global timeout reached for reroute operation on flow "%s"', flow.flowId) }
            assert lastEvent.last().action == REROUTE_FAIL
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.DOWN
            eventsAmount = history.size()
        }

        and: "Flow remains down and no new history events appear for the next 3 seconds (no retry happens)"
        timedLoop(3) {
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.DOWN
            assert flowHelper.getHistorySize(flow.flowId) == eventsAmount
        }

        and: "Src/dst switches are valid"
        wait(WAIT_OFFSET * 2) { //due to instability
            switchHelper.validateAndCollectFoundDiscrepancies([flow.source.switchId, flow.destination.switchId]).isEmpty()
        }
    }
}
