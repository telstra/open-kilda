package org.openkilda.functionaltests.spec.xresilience

import static org.openkilda.functionaltests.extension.tags.Tag.ISL_PROPS_DB_RESET
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOCKKEEPER
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.helpers.Wrappers.timedLoop
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.functionaltests.helpers.model.FlowActionType.DELETE
import static org.openkilda.functionaltests.helpers.model.FlowActionType.PATH_SWAP
import static org.openkilda.functionaltests.helpers.model.FlowActionType.REROUTE
import static org.openkilda.functionaltests.helpers.model.FlowActionType.REROUTE_FAILED
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESET_ISLS_COST
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_ISL
import static org.openkilda.messaging.payload.flow.FlowState.DOWN
import static org.openkilda.messaging.payload.flow.FlowState.UP
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.model.SwitchId
import org.openkilda.model.SwitchStatus
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.service.lockkeeper.model.TrafficControlData

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Isolated
import spock.lang.Shared

import java.util.concurrent.TimeUnit

@Slf4j
class RetriesSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    FlowFactory flowFactory

    @Tags([ISL_RECOVER_ON_FAIL, ISL_PROPS_DB_RESET, SWITCH_RECOVER_ON_FAIL])
    def "System retries the reroute (global retry) if it fails to install rules on one of the current target path's switches"() {
        given: "Switch pair with at least 3 available paths, one path should have a transit switch that we will break \
and at least 1 path must remain safe"
        List<Isl> mainPathIsls, failoverPathIsls, safePathIsls
        SwitchId switchIdToBreak //will belong to failoverPath and be absent in safePath
        Isl islToBreak //will be used to break the mainPath. This ISL is not used in safePath or failoverPath
        def switchPair = switchPairs.all().getSwitchPairs().find { swPair ->
            if(swPair.paths.size() >= 3) {
                def availablePath = swPair.retrieveAvailablePaths()
                failoverPathIsls = availablePath.find { failoverPathCandidate ->
                    def failoverSwitches = failoverPathCandidate.getInvolvedSwitches()
                    safePathIsls = availablePath.find { safePathCandidate ->
                        def safeSwitches = safePathCandidate.getInvolvedSwitches()
                        def mainPath = availablePath.find { it != failoverPathCandidate && it != safePathCandidate }
                        mainPathIsls = mainPath.getInvolvedIsls()
                        def mainSwitches = mainPath.getInvolvedSwitches()
                        switchIdToBreak = failoverSwitches.find { swId -> !safeSwitches.contains(swId) && !mainSwitches.contains(swId) }
                        safePathIsls = safePathCandidate.getInvolvedIsls()
                        Set<Isl> allSafeIsls = safePathIsls.collectMany { [it, it.reversed] }
                        Set<Isl> allFailoverIsls = failoverPathCandidate.getInvolvedIsls().collectMany { [it, it.reversed] }
                        islToBreak = mainPathIsls.collectMany { [it, it.reversed] }.find {
                            !allSafeIsls.contains(it) && !allFailoverIsls.contains(it) && it.srcSwitch.dpId != switchIdToBreak
                        }
                        switchIdToBreak && islToBreak
                    }?.getInvolvedIsls()
                }?.getInvolvedIsls()
            }
            failoverPathIsls
        }
        assert switchPair, "Not able to find a switch pair with suitable paths"
        log.debug("main path: $mainPathIsls\nfailover path: $failoverPathIsls\nsafe path: $safePathIsls\nisl to break: " +
                "$islToBreak\nswitch to break: $switchIdToBreak")

        and: "A flow using given switch pair"
        def availablePathsIsls = switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }
        availablePathsIsls.findAll { it != mainPathIsls }.each { islHelper.makePathIslsMorePreferable(mainPathIsls, it) }
        def flow = flowFactory.getRandom(switchPair)
        assert flow.retrieveAllEntityPaths().getInvolvedIsls() == mainPathIsls

        and: "Switch on the preferred failover path will suddenly be unavailable for rules installation when the reroute starts"
        //select a required failover path beforehand
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
        availablePathsIsls.findAll { it != failoverPathIsls }.each { islHelper.makePathIslsMorePreferable(failoverPathIsls, it) }
        //disconnect the switch, but make it look like 'active'
        def blockData = switchHelper.knockoutSwitch(topology.activeSwitches.find { it.dpId == switchIdToBreak }, RW)
        database.setSwitchStatus(switchIdToBreak, SwitchStatus.ACTIVE)

        when: "Main path of the flow breaks initiating a reroute"
        islHelper.breakIsl(islToBreak)

        then: "System fails to install rules on desired path and tries to retry reroute and find new path (global retry)"
        wait(WAIT_OFFSET * 3, 0.1) {
            assert flow.retrieveFlowHistory().getEntriesByType(REROUTE).find {
                it.taskId =~ (/.+ : retry #1/)
            }
        }

        when: "Switch is marked as offline"
        database.setSwitchStatus(switchIdToBreak, SwitchStatus.INACTIVE)

        then: "System finds another working path and successfully reroutes the flow (one of the retries succeeds)"
        wait(PATH_INSTALLATION_TIME) {
            assert flow.retrieveFlowStatus().status == UP
        }
        def flowPathInfo = flow.retrieveAllEntityPaths()
        def flowPathIsls = flowPathInfo.getInvolvedIsls()
        flowPathIsls != mainPathIsls
        flowPathIsls != failoverPathIsls
        !flowPathInfo.getInvolvedSwitches().contains(switchIdToBreak)
        !flowPathInfo.getInvolvedIsls().contains(islToBreak)
        !flowPathInfo.getInvolvedIsls().contains(islToBreak.reversed)

        and: "All related switches have no rule anomalies"
        def switchesToVerify = islHelper.retrieveInvolvedSwitches((mainPathIsls + failoverPathIsls + flowPathIsls).unique())
                .findAll { it.dpId != switchIdToBreak }
        switchHelper.validateAndCollectFoundDiscrepancies(switchesToVerify*.getDpId()).isEmpty()
    }

    @Tags([SMOKE_SWITCHES, LOCKKEEPER, ISL_RECOVER_ON_FAIL, ISL_PROPS_DB_RESET, SWITCH_RECOVER_ON_FAIL])
    def "System tries to retry rule installation during #data.description if previous one is failed"(){
        given: "Two active neighboring switches with two diverse paths at least"
        def swPair = switchPairs.all().neighbouring()
                .withAtLeastNNonOverlappingPaths(2)
                .withExactlyNIslsBetweenSwitches(1)
                .random()
        def allPaths = swPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }
        List<Isl> mainPathIsls = allPaths.min { it.size() }
        //find path with more than two switches(more than 1 Isl)
        def filteredPathsIsls = allPaths.findAll { it != mainPathIsls && it.size() > 1 }
        def minSize = filteredPathsIsls.min { it.size() }.size()
        // find all possible protected paths with minimal size and pick the first one
        def possibleProtectedPaths = filteredPathsIsls.findAll { it.size() == minSize }
        List<Isl> protectedPathIsls = possibleProtectedPaths.first()

        and: "All alternative paths unavailable (bring ports down)"
        def involvedIsls = mainPathIsls + protectedPathIsls
        List<Isl> altIsls = possibleProtectedPaths.flatten().unique()
                .findAll { !involvedIsls.contains(it)  && !involvedIsls.contains(it.reversed)} as List<Isl>
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
        def flow = flowFactory.getBuilder(swPair).withProtectedPath(true).build().create()
        def flowPathInfo = flow.retrieveAllEntityPaths()
        assert flowPathInfo.getMainPathInvolvedIsls() == mainPathIsls
        assert flowPathInfo.getProtectedPathInvolvedIsls() == protectedPathIsls

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
            assert flow.retrieveFlowHistory().getEntriesByType(data.historyAction)
            .last().payload*.details.findAll{ it =~ /.+ Retrying/}.size() == data.retriesAmount
        }

        then: "Flow is DOWN"
        wait(WAIT_OFFSET) {
            assert flow.retrieveFlowStatus().status == DOWN
        }

        and: "Flow is not rerouted"
        def flowPathInfoAfterSwap = flow.retrieveAllEntityPaths()
        flowPathInfoAfterSwap.getMainPathInvolvedIsls() == mainPathIsls
        flowPathInfoAfterSwap.getProtectedPathInvolvedIsls() == protectedPathIsls


        and: "All involved switches pass switch validation(except dst switch)"
        def involvedSwitchIds = islHelper.retrieveInvolvedSwitches(protectedPathIsls)[0..-2]*.dpId
        wait(WAIT_OFFSET / 2) {
            switchHelper.validateAndCollectFoundDiscrepancies(involvedSwitchIds).isEmpty()
        }

        when: "Connect dst switch back to the controller"
        database.setSwitchStatus(swToManipulate.dpId, SwitchStatus.INACTIVE) //set real status
        switchHelper.reviveSwitch(swPair.dst, blockData)
        isSwitchActivated = true

        then: "Flow is UP"
        wait(discoveryInterval + rerouteDelay + WAIT_OFFSET) {
            flow.retrieveFlowStatus().status == UP
        }

        and: "Flow is valid and pingable"
        flow.validateAndCollectDiscrepancies().isEmpty()
        with(flow.ping()) {
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
//                issue #3237
//                [
//                       description: "update",
//                        historyAction: FlowActionType.UPDATE,
//                        retriesAmount: 15, //
//                        //install: ingress 2 * 3(attempts) + remove: ingress 2 * 3(attempts) + 1 egress * 3(attempts)
//                        action: { FlowExtended f -> f.update(f.tap { it.description = "updated" }) }
//                ],
                [
                        description: "swap paths",
                        historyAction: PATH_SWAP,
                        retriesAmount: 15,
                        // swap:  install: 3 attempts, revert: delete 9 attempts + install 3 attempts
                        // delete: 3 attempts * (1 flow rule + 1 ingress mirror rule + 1 egress mirror rule) = 9 attempts
                        action:  { FlowExtended f -> f.swapFlowPath() }
                ]
        ]
    }

    @Tags([SWITCH_RECOVER_ON_FAIL])
    def "Flow is successfully deleted from the system even if some rule delete commands fail (no rollback for delete)"() {
        given: "A flow"
        def swPair = switchPairs.all().first()
        def flow = flowFactory.getRandom(swPair)

        when: "Send delete request for the flow"
        switchHelper.shapeSwitchesTraffic([swPair.src], new TrafficControlData(1000))
        flow.sendDeleteRequest()

        and: "One of the related switches does not respond"
        switchHelper.knockoutSwitch(swPair.src, RW)

        then: "Flow history shows failed delete rule retry attempts but flow deletion is successful at the end"
        wait(WAIT_OFFSET) {
            def history = flow.retrieveFlowHistory().getEntriesByType(DELETE).last().payload
            //egress and ingress rule and egress and ingress mirror rule on a broken switch, 3 retries each = total 12
            assert history.count { it.details ==~ /Failed to remove the rule.*Retrying \(attempt \d+\)/ } == 12
            assert history.last().action == DELETE.payloadLastAction
        }
        !flow.retrieveFlowStatus()
    }

    @Tags([ISL_RECOVER_ON_FAIL, ISL_PROPS_DB_RESET, SWITCH_RECOVER_ON_FAIL])
    def "System retries the intentional rerouteV1 if it fails to install rules on a switch"() {
        given: "Two active neighboring switches with two diverse paths at least(main and backup paths)"
        def swPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(2).random()

        def availablePaths = swPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }
        List<Isl> mainPathIsls = availablePaths.min { it.size() }
        //find path with more than two switches(more than 1 Isl)
        List<Isl> backupPathIsls = availablePaths.findAll { it != mainPathIsls && it.size() > 1 }.min { it.size() }

        and: "All alternative paths unavailable (bring ports down)"
        def usedIsls = [mainPathIsls.first(), backupPathIsls.first()].collectMany { [it, it.reversed] }
        def altIsls = topology.getRelatedIsls(swPair.src) - usedIsls
        islHelper.breakIsls(altIsls)

        and: "A flow on the main path"
        def flow = flowFactory.getRandom(swPair)
        assert flow.retrieveAllEntityPaths().getInvolvedIsls() == mainPathIsls

        when: "Make backupPath more preferable than mainPath"
        islHelper.makePathIslsMorePreferable(backupPathIsls, mainPathIsls)

        and: "Disconnect the dst switch"
        def swToManipulate = swPair.dst
        def blockData = switchHelper.knockoutSwitch(swToManipulate, RW)
        def isSwitchActivated = false

        and: "Mark the dst switch as ACTIVE in db"
        database.setSwitchStatus(swToManipulate.dpId, SwitchStatus.ACTIVE)

        and: "Init intentional flow reroute(APIv1)"
        flow.rerouteV1()

        then: "System retries to install/delete rules on the dst switch"
        wait(WAIT_OFFSET) {
            assert flow.retrieveFlowHistory().getEntriesByType(REROUTE)
                .last().payload*.details.findAll{ it =~ /.+ Retrying/}.size() == 15
            //install: 3 attempts, revert: delete 9 attempts + install 3 attempts
            // delete: 3 attempts * (1 flow rule + 1 ingress mirror rule + 1 egress mirror rule) = 9 attempts
        }

        then: "Flow is not rerouted"
        flow.retrieveAllEntityPaths().getInvolvedIsls() == mainPathIsls

        and: "All involved switches pass switch validation(except dst switch)"
        def involvedSwitchIds = islHelper.retrieveInvolvedSwitches(backupPathIsls)[0..-2]*.dpId
        wait(WAIT_OFFSET / 2) {
            switchHelper.validateAndCollectFoundDiscrepancies(involvedSwitchIds).isEmpty()
        }

        when: "Connect dst switch back to the controller"
        database.setSwitchStatus(swToManipulate.dpId, SwitchStatus.INACTIVE) //set real status
        switchHelper.reviveSwitch(swPair.dst, blockData)
        isSwitchActivated = true

        then: "Flow is UP"
        wait(discoveryInterval + rerouteDelay + WAIT_OFFSET) {
           flow.retrieveFlowStatus().status == UP
        }

        and: "Flow is valid and pingable"
        flow.validateAndCollectDiscrepancies().isEmpty()
        with(flow.ping()) {
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
    @Shared int globalTimeout = 45 //global timeout for reroute operation
    @Autowired @Shared
    CleanupManager cleanupManager

    @Autowired
    @Shared
    FlowFactory flowFactory

    //isolation: requires no 'up' events in the system while flow is Down
    @Tags([ISL_RECOVER_ON_FAIL, LOW_PRIORITY])
    def "System does not retry after global timeout for reroute operation"() {
        given: "A flow with ability to reroute"
        def swPair = switchPairs.all().nonNeighbouring().switchPairs
                .find { it.src.dpId.toString().contains("03") && it.dst.dpId.toString().contains("07")}
        def availablePaths = swPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }

        def expectedInitialPath = availablePaths.find { it.size() >= 5 }
        availablePaths.findAll { it != expectedInitialPath }.each { islHelper.makePathIslsMorePreferable(expectedInitialPath, it) }

        def flow = flowFactory.getRandom(swPair)
        assert  expectedInitialPath == flow.retrieveAllEntityPaths().getInvolvedIsls()

        def flowInvolvedSwitches = islHelper.retrieveInvolvedSwitches(expectedInitialPath)
        switchHelper.shapeSwitchesTraffic(flowInvolvedSwitches[1..-1], new TrafficControlData(8000))

        when: "Break current path to trigger a reroute"
        def islToBreak = flow.retrieveAllEntityPaths().getInvolvedIsls().first()
        cleanupManager.addAction(RESTORE_ISL, {islHelper.restoreIsl(islToBreak)})
        cleanupManager.addAction(RESET_ISLS_COST,{database.resetCosts(topology.isls)})
        northbound.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)

        and: "Connection to src switch is slow in order to simulate a global timeout on reroute operation"
        switchHelper.shapeSwitchesTraffic([swPair.src], new TrafficControlData(8500))

        then: "After global timeout expect flow reroute to fail and flow to become DOWN"
        TimeUnit.SECONDS.sleep(globalTimeout)
        // 1 reroute event: main ISL break -> GlobalTimeout
        // 1 reroute event: delay on the src(new path in the scope of triggered reroute)
        int rerouteEventsAmount = 2
        wait(globalTimeout + WAIT_OFFSET, 1) { //long wait, may be doing some revert actions after global t/o
            def history = flow.retrieveFlowHistory()
            def rerouteEvent = history.getEntriesByType(REROUTE).first()
            assert rerouteEvent.payload.find { it.action == sprintf('Global timeout reached for reroute operation on flow "%s"', flow.flowId) }
            assert rerouteEvent.payload.last().action == REROUTE_FAILED.payloadLastAction
            assert flow.retrieveFlowStatus().status == DOWN
            assert history.getEntriesByType(REROUTE).size() == rerouteEventsAmount
        }

        and: "Flow remains down and no new history events appear for the next 5 seconds (no retry happens)"
        timedLoop(5) {
            assert flow.retrieveFlowStatus().status == DOWN
            assert flow.retrieveFlowHistory().entries.size() == rerouteEventsAmount + 1 // +1 creation event
        }

        and: "The last reroute event was caused by delay on src for the alternative path"
        def rerouteEvents = flow.retrieveFlowHistory().getEntriesByType(REROUTE).last()
        rerouteEvents.payload.last().action == REROUTE_FAILED.payloadLastAction
        rerouteEvents.payload.last().details.toString().contains(
                "No paths of the flow ${flow.flowId} are affected by failure on IslEndpoint")

        and: "Src/dst switches are valid"
        switchHelper.cleanupTrafficShaperRules([swPair.src])
        boolean isTrafficShaperRulesCleanedUp = true
        wait(WAIT_OFFSET * 2) { //due to instability
            switchHelper.validateAndCollectFoundDiscrepancies([flow.source.switchId, flow.destination.switchId]).isEmpty()
        }

        cleanup:
        //should be called here as flow deletion is called before removing delay on the switch in our cleanup manager
        !isTrafficShaperRulesCleanedUp && switchHelper.cleanupTrafficShaperRules(flowInvolvedSwitches)
    }
}
