package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.Wrappers.retry
import static org.openkilda.functionaltests.helpers.Wrappers.timedLoop
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.functionaltests.helpers.model.FlowActionType.REROUTE
import static org.openkilda.functionaltests.helpers.model.FlowActionType.REROUTE_FAILED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowNotReroutedExpectedError
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.FlowHistoryEventExtension
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchFeature
import org.openkilda.model.SwitchStatus
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.service.lockkeeper.model.TrafficControlData

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Isolated
import spock.lang.Narrative
import spock.lang.Shared

import java.util.concurrent.TimeUnit

@Slf4j
@Narrative("Verify different cases when Kilda is supposed to automatically reroute certain flow(s).")

class AutoRerouteSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    FlowFactory flowFactory

    @Tags([SMOKE, ISL_RECOVER_ON_FAIL])
    @IterationTag(tags = [TOPOLOGY_DEPENDENT], iterationNameRegex = /vxlan/)
    def "Flow is rerouted when one of the #description flow ISLs fails"() {
        given: "A flow with one alternative path at least"
        FlowExtended flow = flowFactory.getRandom(swicthPair)
        def allFlowPaths = swicthPair.getPaths()
        def initialPath = flow.retrieveAllEntityPaths()

        when: "Fail a flow ISL (bring switch port down)"
        Set<Isl> altFlowIsls = []
        def flowIsls = initialPath.flowPath.getInvolvedIsls()
        allFlowPaths.findAll { it != initialPath.getPathNodes() }
                .each { altFlowIsls.addAll(pathHelper.getInvolvedIsls(it)) }
        def islToFail = flowIsls.find { !(it in altFlowIsls) && !(it.reversed in altFlowIsls) }
        islHelper.breakIsl(islToFail)

        then: "The flow was rerouted after reroute delay"
        wait(rerouteDelay + WAIT_OFFSET) {
            assert flow.retrieveFlowStatus().status == FlowState.UP
            assert flow.retrieveAllEntityPaths() != initialPath
        }

        where:
        description | swicthPair
        "vlan"      | switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        "vxlan"     | switchPairs.all().neighbouring().withBothSwitchesVxLanEnabled().withAtLeastNPaths(2).random()
    }

    @Tags([SMOKE, ISL_RECOVER_ON_FAIL])
    def "Strict bandwidth true: Flow status is set to DOWN after reroute if no alternative path with enough bandwidth"() {
        given: "A flow with one alternative path at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        List<List<PathNode>> allFlowPaths = switchPair.paths

        FlowExtended flow = flowFactory.getBuilder(switchPair)
                .withStrictBandwidth(true).build()
                .create()
        def initialPath = flow.retrieveAllEntityPaths()

        and: "Alt path ISLs have not enough bandwidth to host the flow"
        def altPaths = allFlowPaths.findAll { it != initialPath.getPathNodes() }
        def flowIsls = initialPath.flowPath.getInvolvedIsls()
        def altIsls = altPaths.collectMany { pathHelper.getInvolvedIsls(it).findAll { !(it in flowIsls || it.reversed in flowIsls) } }
                .unique { a, b -> (a == b || a == b.reversed) ? 0 : 1 }
        List<SwitchPortVlan> busyEndpoints = flow.occupiedEndpoints()
        altIsls.each { isl ->
            def linkProp = islUtils.toLinkProps(isl, [cost: "1"])
            pathHelper.updateIslsCost([isl], 1)
            def extraFlow = flowFactory.getBuilder(isl.srcSwitch, isl.dstSwitch, false, busyEndpoints)
                    .withBandwidth(northbound.getLink(isl).availableBandwidth - flow.maximumBandwidth + 1).build()
                    .create()
            busyEndpoints.addAll(extraFlow.occupiedEndpoints())
            northbound.deleteLinkProps([linkProp])
        }

        when: "Fail a flow ISL (bring switch port down)"
        Set<Isl> altFlowIsls = []
        allFlowPaths.findAll { it != initialPath.getPathNodes() }
                .each { altFlowIsls.addAll(pathHelper.getInvolvedIsls(it)) }
        def islToFail = flowIsls.find { !(it in altFlowIsls) && !(it.reversed in altFlowIsls) }
        islHelper.breakIsl(islToFail)

        then: "Flow history shows 3 retry attempts, eventually bringing flow to Down"
        List<FlowHistoryEventExtension> rerouteEvents
        wait(rerouteDelay + WAIT_OFFSET * 2) {
            rerouteEvents = flow.retrieveFlowHistory().getEntriesByType(REROUTE)
            verifyAll {
                rerouteEvents.size() == 4 //original + 3 retries
                rerouteEvents.last().payload.last().details.endsWith(
                        "Failed to find path with requested bandwidth=$flow.maximumBandwidth")
                rerouteEvents.last().payload.last().action == REROUTE_FAILED.payloadLastAction
            }
        }
        flow.retrieveFlowStatus().status == FlowState.DOWN

        and: "Flow path is unchanged"
        flow.retrieveAllEntityPaths() == initialPath

        when: "Try to manually reroute the Down flow, while there is still not enough bandwidth"
        def manualRerouteTime = System.currentTimeSeconds()
        flow.reroute()

        then: "Error is returned, stating a 'not enough bandwidth' reason"
        def error = thrown(HttpClientErrorException)
        new FlowNotReroutedExpectedError(~/Not enough bandwidth or no path found/).matches(error)

        and: "Flow history shows more reroute attempts after manual command"
        wait(WAIT_OFFSET * 2) {
            rerouteEvents = flow.retrieveFlowHistory(manualRerouteTime).getEntriesByType(REROUTE)
            verifyAll {
                rerouteEvents.size() == 4 //manual original + 3 reties
                rerouteEvents.last().payload.last().details.endsWith(
                        "Failed to find path with requested bandwidth=$flow.maximumBandwidth")
                rerouteEvents.last().payload.last().action == REROUTE_FAILED.payloadLastAction
            }
        }

        and: "Flow remains Down and on the same path"
        flow.retrieveFlowStatus().status == FlowState.DOWN
        flow.retrieveAllEntityPaths() == initialPath

        when: "Broken ISL on the original path is back online"
        antiflap.portUp(islToFail.srcSwitch.dpId, islToFail.srcPort)

        then: "The flow state has been changed to UP without rerouting(remains initial path)"
        flow.waitForBeingInState(FlowState.UP)
        assert flow.retrieveAllEntityPaths() == initialPath
    }

    @Tags([ISL_RECOVER_ON_FAIL, SWITCH_RECOVER_ON_FAIL])
    def "Single switch flow changes status on switch up/down events"() {
        given: "Single switch flow"
        def sw = topology.getActiveSwitches()[0]
        def flow = flowFactory.getRandom(sw, sw)

        when: "The switch is disconnected"
        def blockData = switchHelper.knockoutSwitch(sw, RW)

        then: "Flow becomes 'Down'"
        wait(WAIT_OFFSET) {
            def flowInfo =  flow.retrieveDetails()
            assert flowInfo.status == FlowState.DOWN
            assert flowInfo.statusInfo == "Switch $sw.dpId is inactive"
        }

        when: "Other isl fails"
        def islToFail = topology.isls.find() {isl-> isl.srcSwitch != sw && isl.dstSwitch != sw}
        islHelper.breakIsl(islToFail)

        then: "Flow remains 'DOWN'"
        assert flow.retrieveFlowStatus().status == FlowState.DOWN

        when: "Other isl is back online"
        islHelper.restoreIsl(islToFail)

        then: "Flow remains 'DOWN'"
        assert flow.retrieveFlowStatus().status == FlowState.DOWN

        when: "The switch is connected back"
        switchHelper.reviveSwitch(sw, blockData, true)

        then: "Flow becomes 'Up'"
        wait(WAIT_OFFSET) {
            assert flow.retrieveFlowStatus().status == FlowState.UP
        }

        and: "Flow is valid"
        flow.validateAndCollectDiscrepancies().isEmpty()
    }

    @Tags([SMOKE, ISL_RECOVER_ON_FAIL])
    def "Flow goes to 'Down' status when one of the flow ISLs fails and there is no alt path to reroute"() {
        given: "A flow without alternative paths"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(1).random()
        def allFlowPaths = switchPair.paths
        def flow = flowFactory.getBuilder(switchPair).withStrictBandwidth(strictBw).build()
                .create()

        def initialPath = flow.retrieveAllEntityPaths()

        def altPaths = allFlowPaths.findAll { it != initialPath.getPathNodes() }
        def involvedIsls = initialPath.flowPath.getInvolvedIsls()
        def broughtDownIsls = altPaths.collectMany {
            pathHelper.getInvolvedIsls(it)
                    .findAll { !(it in involvedIsls || it.reversed in involvedIsls) }
        }
                .unique { a, b -> (a == b || a == b.reversed) ? 0 : 1 }
        islHelper.breakIsls(broughtDownIsls)

        when: "One of the flow ISLs goes down"
        def isl = involvedIsls.first()
        islHelper.breakIsl(isl)

        then: "The flow becomes 'Down'"
        wait(rerouteDelay + WAIT_OFFSET * 2) {
            assert flow.retrieveFlowStatus().status == FlowState.DOWN
            def reroutes = flow.retrieveFlowHistory().getEntriesByType(REROUTE)
            assert reroutes.size() == reroutesCount
            assert reroutes.last().payload.last().action == REROUTE_FAILED.payloadLastAction
        }

        when: "ISL goes back up"
        islHelper.restoreIsl(isl)

        then: "The flow becomes 'Up'"
        wait(rerouteDelay + WAIT_OFFSET) {
            assert flow.retrieveFlowStatus().status == FlowState.UP
            assert flow.retrieveFlowHistory().getEntriesByType(REROUTE).last().payload.find {
                it.action == "The flow status was reverted to UP" || it.action == REROUTE.payloadLastAction
            }
        }

        where:
        strictBw    | reroutesCount
        true        | 4 //original + 3 retries
        false       | 2 //original + 1 retry with ignore bw
    }

    @Tags([SMOKE, ISL_RECOVER_ON_FAIL])
    def "Flow in 'Down' status is rerouted when discovering a new ISL"() {
        given: "An intermediate-switch flow with one alternative path at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        def allFlowPaths = switchPair.paths
        def flow = flowFactory.getBuilder(switchPair).withStrictBandwidth(true).build()
                .create()
        def initialPath = flow.retrieveAllEntityPaths()

        when: "Bring all ports down on the source switch that are involved in the current and alternative paths"
        def broughtDownIsls = allFlowPaths.unique { it.first() }.collect { path ->
            pathHelper.getInvolvedIsls(path).first()
        }
        islHelper.breakIsls(broughtDownIsls)

        then: "The flow goes to 'Down' status"
        wait(rerouteDelay + WAIT_OFFSET) {
            assert flow.retrieveFlowStatus().status == FlowState.DOWN
            assert flow.retrieveFlowHistory().getEntriesByType(REROUTE).last().payload
                    .find { it.action == REROUTE_FAILED.payloadLastAction }
        }

        wait(WAIT_OFFSET) {
            def prevHistorySize = flow.retrieveFlowHistory().entries
                    .findAll { !(it.details =~ /Reason: ISL .* status become ACTIVE/) }.size()
            timedLoop(4) {
                //history size should no longer change for the flow, all retries should give up
                def newHistorySize = flow.retrieveFlowHistory().entries
                        .findAll { !(it.details =~ /Reason: ISL .* status become ACTIVE/) }.size()
                assert newHistorySize == prevHistorySize
                assert flow.retrieveFlowStatus().status == FlowState.DOWN
                sleep(500)
            }
        }
        when: "Bring all ports up on the source switch that are involved in the alternative paths"
        islHelper.restoreIsls(broughtDownIsls.findAll {it.srcPort != initialPath.flowPath.getInvolvedIsls().first().srcPort})

        then: "The flow goes to 'Up' status"
        and: "The flow was rerouted"
        //rtretiak: TODO: why such a long wait required(it is indeed required)? investigate
        wait(rerouteDelay + discoveryInterval + WAIT_OFFSET * 3) {
            timedLoop(3) {assert flow.retrieveFlowStatus().status == FlowState.UP }
            assert flow.retrieveAllEntityPaths() != initialPath
        }
    }

    @Tags([SMOKE, ISL_RECOVER_ON_FAIL])
    def "Flow in 'Up' status is not rerouted when discovering a new ISL and more preferable path is available"() {
        given: "A flow with one alternative path at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        def allFlowPaths = switchPair.paths

        def flow = flowFactory.getRandom(switchPair)
        def initialPath = flow.retrieveAllEntityPaths()

        and: "Make the current flow path less preferable than others"
        allFlowPaths.findAll { it != initialPath.getPathNodes() }
                .each { pathHelper.makePathMorePreferable(it, initialPath.getPathNodes()) }

        when: "One of the links not used by flow goes down"
        def involvedIsls = initialPath.flowPath.getInvolvedIsls()
        def islToFail = topology.islsForActiveSwitches.find {
            !involvedIsls.contains(it) && !involvedIsls.contains(it.reversed)
        }
        islHelper.breakIsl(islToFail)

        and: "Failed link goes up"
        islHelper.restoreIsl(islToFail)

        then: "The flow is not rerouted and doesn't use more preferable path"
        TimeUnit.SECONDS.sleep(rerouteDelay + WAIT_OFFSET)
        flow.retrieveFlowStatus().status == FlowState.UP
        flow.retrieveAllEntityPaths() == initialPath
    }

    @Tags([SMOKE])
    def "Flow in 'Up' status is not rerouted when connecting a new switch and more preferable path is available"() {
        given: "A flow with one alternative path at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        def allFlowPaths = switchPair.paths

        def flow = flowFactory.getRandom(switchPair)
        def initialPath = flow.retrieveAllEntityPaths()

        and: "Make the current flow path less preferable than others"
        allFlowPaths.findAll { it != initialPath.getPathNodes() }
                .each { pathHelper.makePathMorePreferable(it, initialPath.getPathNodes()) }

        when: "Disconnect one of the switches not used by flow"
        def involvedSwitches = initialPath.getInvolvedSwitches()
        def switchToDisconnect = topology.getActiveSwitches().find { !involvedSwitches.contains(it) }
        def blockData = lockKeeper.knockoutSwitch(switchToDisconnect, RW)

        then: "The switch is really disconnected from the controller"
        wait(WAIT_OFFSET) { assert !(switchToDisconnect.dpId in northbound.getActiveSwitches()*.switchId) }

        when: "Connect the switch back to the controller"
        lockKeeper.reviveSwitch(switchToDisconnect, blockData)

        then: "The switch is really connected to the controller"
        wait(WAIT_OFFSET) {
            assert northbound.getSwitch(switchToDisconnect.dpId).state == SwitchChangeType.ACTIVATED
            assert northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2
        }

        and: "The flow is not rerouted and doesn't use more preferable path"
        TimeUnit.SECONDS.sleep(rerouteDelay + WAIT_OFFSET)
        flow.retrieveFlowStatus().status == FlowState.UP
        flow.retrieveAllEntityPaths() == initialPath
    }

    @Tags([HARDWARE, SMOKE])
    def "Flow is not rerouted when one of the flow ports goes down"() {
        given: "An intermediate-switch flow with one alternative path at least"
        def switchPair = switchPairs.all().nonNeighbouring().withAtLeastNPaths(2).random()
        def allFlowPaths = switchPair.paths

        def flow = flowFactory.getRandom(switchPair)
        def initialPath = flow.retrieveAllEntityPaths()


        and: "Make the current flow path less preferable than others"
        allFlowPaths.findAll { it != initialPath.getPathNodes() }
                .each { pathHelper.makePathMorePreferable(it, initialPath.getPathNodes()) }

        when: "Bring the flow port down on the source switch"
        antiflap.portDown(flow.source.switchId, flow.source.portNumber)

        then: "The flow is not rerouted"
        TimeUnit.SECONDS.sleep(rerouteDelay)
        flow.retrieveAllEntityPaths() == initialPath

        when: "Bring the flow port down on the destination switch"
        antiflap.portDown(flow.destination.switchId, flow.destination.portNumber)

        then: "The flow is not rerouted"
        TimeUnit.SECONDS.sleep(rerouteDelay)
        flow.retrieveAllEntityPaths() == initialPath
    }

    @Tags(HARDWARE)
    def "Flow in 'UP' status is not rerouted after switchUp event"() {
        given: "Two active neighboring switches which support round trip latency"
        def switchPair = switchPairs.all()
                .neighbouring()
                .withIslRttSupport()
                .random()

        and: "A flow on the given switch pair"
        def flow = flowFactory.getRandom(switchPair)

        when: "Deactivate the src switch"
        def swToDeactivate = switchPair.src
        // it takes more time to DEACTIVATE a switch via the 'knockoutSwitch' method on the stage env
        def blockData = switchHelper.knockoutSwitch(swToDeactivate, RW, false, WAIT_OFFSET * 4)

        then: "Flow is UP"
        flow.retrieveFlowStatus().status == FlowState.UP

        when: "Activate the src switch"
        switchHelper.reviveSwitch(swToDeactivate, blockData, true)

        then: "System doesn't try to reroute the flow on the switchUp event because flow is already in UP state"
        timedLoop(rerouteDelay + WAIT_OFFSET / 2) {
            assert flow.retrieveFlowHistory().getEntriesByType(REROUTE).findAll {
                !(it.details =~ /Reason: ISL .* status become ACTIVE/)
            }.isEmpty()
        }
    }

    @Tags([ISL_RECOVER_ON_FAIL, SWITCH_RECOVER_ON_FAIL])
    def "Flow is not rerouted when switchUp event appear for a switch which is not related to the flow"() {
        given: "Given a flow in DOWN status on neighboring switches"
        def switchPair = switchPairs.all()
                .neighbouring()
                .withExactlyNIslsBetweenSwitches(1)
                .random()
        def flowPath = switchPair.paths.min { it.size() }
        def flow = flowFactory.getRandom(switchPair)
        def initialPath = flow.retrieveAllEntityPaths()

        assert initialPath.getPathNodes() == flowPath

        //All alternative paths for both flows are unavailable
        def untouchableIsls = pathHelper.getInvolvedIsls(flowPath).collectMany { [it, it.reversed] }
        def altPaths = switchPair.paths.findAll { [it, it.reverse()].every { it != flowPath }}
        def islsToBreak = altPaths.collectMany { pathHelper.getInvolvedIsls(it) }
                .collectMany { [it, it.reversed] }.unique()
                .findAll { !untouchableIsls.contains(it) }.unique { [it, it.reversed].sort() }
        islHelper.breakIsls(islsToBreak)
        //move the flow to DOWN status
        def islToBreak = initialPath.flowPath.getInvolvedIsls().first()
        islHelper.breakIsl(islToBreak)

        when: "Generate switchUp event on switch which is not related to the flow"
        def involvedSwitches = pathHelper.getInvolvedSwitches(flowPath)*.dpId
        def switchToManipulate = topology.activeSwitches.find { !(it.dpId in involvedSwitches) }
        def blockData = switchHelper.knockoutSwitch(switchToManipulate, RW)
        wait(WAIT_OFFSET) {
            timedLoop(4) {
                //waiting for the last retry in the scope of flow rerouting due to the ISL failure
                assert flow.retrieveFlowHistory().getEntriesByType(REROUTE).findAll {
                    it.details =~ /Reason: ISL .* become INACTIVE/ && it.taskId.contains("retry #1 ignore_bw true")
                }
                assert flow.retrieveFlowStatus().status == FlowState.DOWN
            sleep(500)
            }
        }
        def expectedZeroReroutesTimestamp = System.currentTimeSeconds()
        switchHelper.reviveSwitch(switchToManipulate, blockData)

        then: "Flow is not triggered for reroute due to switchUp event because switch is not related to the flow"
        TimeUnit.SECONDS.sleep(rerouteDelay * 2) // it helps to be sure that the auto-reroute operation is completed
        flow.retrieveFlowHistory(expectedZeroReroutesTimestamp, System.currentTimeSeconds())
                .getEntriesByType(REROUTE).findAll {
            !(it.details =~ /Reason: ISL .* status become ACTIVE/) //exclude ISL up reasons from parallel streams
        }.size() == 0
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "System properly handles multiple flow reroutes if ISL on new path breaks while first reroute is in progress"() {
        given: "Switch pair that have at least 3 paths and 2 paths that have at least 1 common isl"
        List<PathNode> mainPath, backupPath, thirdPath
        List<Isl> mainIsls, backupIsls
        Isl mainPathUniqueIsl, commonIsl
        def swPair = switchPairs.all().getSwitchPairs().find { pair ->
            //we are looking for 2 paths that have a common isl. This ISL should not be used in third path
            mainPath = pair.paths.find { path ->
                mainIsls = pathHelper.getInvolvedIsls(path)
                //look for a backup path with a common isl
                backupPath = pair.paths.findAll { it != path }.find { currentBackupPath ->
                    backupIsls = pathHelper.getInvolvedIsls(currentBackupPath)
                    def mainPathUniqueIsls = mainIsls.findAll {
                        !backupIsls.contains(it)
                    }
                    def commonIsls = backupIsls.findAll {
                        it in mainIsls
                    }
                    //given possible mainPath isls to break and available common isls
                    List<Isl> result = [mainPathUniqueIsls, commonIsls].combinations().find { unique, common ->
                        //there should be a safe third path that does not involve any of them
                        thirdPath = pair.paths.findAll { it != path && it != currentBackupPath }.find {
                            def isls = pathHelper.getInvolvedIsls(it)
                            !isls.contains(common) && !isls.contains(unique)
                        }
                    }
                    if(result) {
                        mainPathUniqueIsl = result[0]
                        commonIsl = result[1]
                    }
                    thirdPath
                }
            }
        }
        assert swPair, "Not able to find a switch pair with suitable paths"
        log.debug("main isls: $mainIsls")
        log.debug("backup isls: $backupIsls")

        and: "A flow over these switches that uses one of the desired paths that have common ISL"
        swPair.paths.findAll { it != mainPath }.each { pathHelper.makePathMorePreferable(mainPath, it) }
        def flow = flowFactory.getRandom(swPair)

        and: "A potential 'backup' path that shares common isl has the preferred cost (will be preferred during reroute)"
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
        swPair.paths.findAll { it != backupPath }.each { pathHelper.makePathMorePreferable(backupPath, it) }

        when: "An ISL which is unique for current path breaks, leading to a flow reroute"
        antiflap.portDown(mainPathUniqueIsl.srcSwitch.dpId, mainPathUniqueIsl.srcPort)
        wait(3, 0) {
            assert northbound.getLink(mainPathUniqueIsl).state == FAILED
        }

        and: "Right when reroute starts: an ISL which is common for current path and potential backup path breaks too, \
triggering one more reroute of the current path"
        //add latency to make reroute process longer to allow us break the target path while rules are being installed
        switchHelper.shapeSwitchesTraffic([swPair.dst], new TrafficControlData(1000))
        //break the second ISL when the first reroute has started and is in progress
        wait(WAIT_OFFSET) {
            assert flow.retrieveFlowHistory().getEntriesByType(REROUTE).size() == 1
        }
        antiflap.portDown(commonIsl.srcSwitch.dpId, commonIsl.srcPort)
        TimeUnit.SECONDS.sleep(rerouteDelay)
        //first reroute should not be finished at this point, otherwise increase the latency to switches
        assert ![REROUTE.payloadLastAction, REROUTE_FAILED.payloadLastAction].contains(
                flow.retrieveFlowHistory().getEntriesByType(REROUTE).first().payload.last().action)

        then: "System reroutes the flow twice and flow ends up in UP state"
        wait(PATH_INSTALLATION_TIME * 2) {
            def reroutes = flow.retrieveFlowHistory().getEntriesByType(REROUTE)
            assert reroutes.size() == 2 //reroute queue, second reroute starts right after first is finished
            reroutes.each { assert it.payload.last().action == REROUTE.payloadLastAction }
            assert flow.retrieveFlowStatus().status == FlowState.UP
        }

        and: "New flow path avoids both main and backup paths as well as broken ISLs"
        def actualIsls = flow.retrieveAllEntityPaths().flowPath.getInvolvedIsls()
        [commonIsl, commonIsl.reversed, mainPathUniqueIsl, mainPathUniqueIsl.reversed].each {
            assert !actualIsls.contains(it)
        }

        and: "Flow is pingable"
        retry(3, 0) { //Was unstable on Jenkins builds. Fresh env problem?
            with(flow.ping()) {
                it.forward.pingSuccess
                it.reverse.pingSuccess
            }
            true
        }
    }

}


@Slf4j
@Narrative("Verify different cases when Kilda is supposed to automatically reroute certain flow(s).")
@Isolated

class AutoRerouteIsolatedSpec extends HealthCheckSpecification {
    //isolation: global toggle flowsRerouteOnIslDiscoveryEnabled is changed
    @Autowired
    @Shared
    FlowFactory flowFactory

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Flow in 'Down' status is rerouted after switchUp event"() {
        given: "First switch pair with two parallel links and two available paths"
        assumeTrue(rerouteDelay * 2 < discoveryTimeout, "Reroute should be completed before link is FAILED")
        def switchPair1 = switchPairs.all()
                .neighbouring()
                .withAtLeastNIslsBetweenNeighbouringSwitches(2)
                .random()
        // disable auto-reroute on islDiscovery event
        featureToggles.flowsRerouteOnIslDiscoveryEnabled(false)

        and: "Second switch pair where the srс switch from the first switch pair is a transit switch"
        List<PathNode> secondFlowPath
        def switchPair2 = switchPairs.all().getSwitchPairs().find { swP ->
            swP.paths.find { pathCandidate ->
                secondFlowPath = pathCandidate
                def involvedSwitches = pathHelper.getInvolvedSwitches(pathCandidate)
                involvedSwitches.size() == 3 && involvedSwitches[1].dpId == switchPair1.src.dpId &&
                        involvedSwitches[-1].dpId == switchPair1.dst.dpId
                /**
                 * Because of this condition we have to include all reversed(mirrored) switch pairs during search.
                 * Because all remaining switch pairs may use switchPair1.dst.dpId as their src
                 */
            }
        } ?: assumeTrue(false, "No suiting switches found for the second flow")

        //Main and backup paths of firstFlow for further manipulation with them
        def firstFlowMainPath = switchPair1.paths.min { it.size() }
        def firstFlowBackupPath = switchPair1.paths.findAll { it != firstFlowMainPath }.min { it.size() }
        def untouchableIsls = [firstFlowMainPath, firstFlowBackupPath, secondFlowPath]
                .collectMany { pathHelper.getInvolvedIsls(it) }.unique().collectMany { [it, it.reversed] }

        //All alternative paths for both flows are unavailable
        def altPaths1 = switchPair1.paths.findAll {  it != firstFlowMainPath &&  it != firstFlowBackupPath }
        def altPaths2 = switchPair2.paths.findAll {  it != secondFlowPath && it != secondFlowPath.reverse() }
        def islsToBreak = (altPaths1 + altPaths2).collectMany { pathHelper.getInvolvedIsls(it) }
                .collectMany { [it, it.reversed] }.unique()
                .findAll { !untouchableIsls.contains(it) }.unique { [it, it.reversed].sort() }

        islHelper.breakIsls(islsToBreak)

        //firstFlowMainPath path more preferable than the firstFlowBackupPath
        pathHelper.makePathMorePreferable(firstFlowMainPath, firstFlowBackupPath)

        and: "First flow without transit switches"
        def firstFlow = flowFactory.getRandom(switchPair1)
        def initialFirstFlowPath = firstFlow.retrieveAllEntityPaths()
        assert initialFirstFlowPath.getPathNodes() == firstFlowMainPath

        and: "Second flow with transit switch"
        def secondFlow = flowFactory.getRandom(switchPair2)
        //we are not confident which of 2 parallel isls are picked, so just recheck it
        def initialSecondFlowPath = secondFlow.retrieveAllEntityPaths()
        secondFlowPath = initialSecondFlowPath.getPathNodes()

        when: "Disconnect the src switch of the first flow from the controller"
        def islToBreak = initialFirstFlowPath.flowPath.getInvolvedIsls().first()
        def blockData = lockKeeper.knockoutSwitch(switchPair1.src, RW)
        wait(discoveryTimeout + WAIT_OFFSET) {
            assert northbound.getSwitch(switchPair1.src.dpId).state == SwitchChangeType.DEACTIVATED
        }

        and: "Mark the switch as ACTIVE in db" // just to reproduce #3131
        database.setSwitchStatus(switchPair1.src.dpId, SwitchStatus.ACTIVE)

        and: "Init auto reroute (bring ports down on the dstSwitch)"
        antiflap.portDown(islToBreak.dstSwitch.dpId, islToBreak.dstPort)

        then: "System tries to reroute a flow with transit switch"
        wait(WAIT_OFFSET * 3) {
            def firstFlowHistory = firstFlow.retrieveFlowHistory().getEntriesByType(REROUTE)
            assert firstFlowHistory.last().payload.find { it.action == REROUTE_FAILED.payloadLastAction }
            //check that system doesn't retry to reroute the firstFlow (its src is down, no need to retry)
            assert !firstFlowHistory.find { it.taskId =~ /.+ : retry #1/ }
            def secondFlowHistory = secondFlow.retrieveFlowHistory().getEntriesByType(REROUTE)
            /*there should be original reroute + 3 retries or original reroute + 2 retries
            (sometimes the system does not try to retry reroute for linkDown event,
            because the system gets 'ISL timeout' event for other ISLs)
            We are not checking the 'retry #' messages directly,
            since system may have their reasons changed to 'isl timeout' during reroute merge*/
            assert secondFlowHistory.size() == 4 ||
                    (secondFlowHistory.size() == 3 && secondFlowHistory.last().taskId.contains("ignore_bw true"))
            withPool {
                [firstFlow, secondFlow].eachParallel { FlowExtended flow ->
                    assert flow.retrieveAllEntityPaths() == (flow.flowId == firstFlow.flowId ? initialFirstFlowPath : initialSecondFlowPath)
                }
            }
        }

        and: "Flows are 'Down'"
        //to ensure a final 'down' wait for all non-rtl isls to fail and trigger reroutes
        def nonRtIsls = topology.getRelatedIsls(switchPair1.src).findAll {
            !it.srcSwitch.features.contains(SwitchFeature.NOVIFLOW_COPY_FIELD) ||
                    !it.dstSwitch.features.contains(SwitchFeature.NOVIFLOW_COPY_FIELD)
        }
        wait(discoveryTimeout) {
            def allLinks = northbound.getAllLinks()
            nonRtIsls.forEach { assert islUtils.getIslInfo(allLinks, it).get().state == FAILED }
        }
        wait(WAIT_OFFSET) {
            def prevHistorySizes = [firstFlow, secondFlow].collect {  it.retrieveHistoryEventsNumber() }
            timedLoop(4) {
                //history size should no longer change for both flows, all retries should give up
                def newHistorySizes = [firstFlow, secondFlow].collect {  it.retrieveHistoryEventsNumber() }
                assert newHistorySizes == prevHistorySizes
                withPool {
                    [firstFlow, secondFlow].eachParallel { FlowExtended flow ->
                        assert flow.retrieveFlowStatus().status == FlowState.DOWN
                    }
                }
                sleep(500)
            }
            assert firstFlow.retrieveDetails().statusInfo =~ /ISL (.*) become INACTIVE(.*)/
            assert secondFlow.retrieveDetails().statusInfo == "No path found. \
Switch $secondFlow.source.switchId doesn't have links with enough bandwidth, \
Failed to find path with requested bandwidth= ignored"
        }

        when: "Connect the switch back to the controller"
        database.setSwitchStatus(switchPair1.src.dpId, SwitchStatus.INACTIVE) // set real status
        switchHelper.reviveSwitch(switchPair1.src, blockData)

        then: "System tries to reroute the flow on switchUp event"
        /* there is a risk that flows won't find a path during reroute, because switch is online
         but ISLs are not discovered yet, that's why we check that system tries to reroute flow on the switchUp event
         and don't check that flow is UP */
        wait(WAIT_OFFSET) {
            [firstFlow, secondFlow].each { FlowExtended flow ->
                assert flow.retrieveFlowHistory().getEntriesByType(REROUTE).find {
                    it.details == "Reason: Switch '$switchPair1.src.dpId' online"
                }
            }
        }
    }

    @Tags([SMOKE, ISL_RECOVER_ON_FAIL])
    /** isolation: test verifies that flow is rerouted by blinking not involved isl,
     * it can be triggered by parallel test, not by action from this test
     * as a result 'retry' may merge the reroute details with one of the 'become Active' reasons from parallel topologies
     * and test become unstable*/
    def "Strict bandwidth false: Flow is rerouted even if there is no available bandwidth on alternative path, sets status to Degraded"() {
        given: "A flow with one alternative path at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(1).random()
        def allFlowPaths = switchPair.paths
        def flow = flowFactory.getBuilder(switchPair).withStrictBandwidth(false).build()
                .create()
        def initialPath = flow.retrieveAllEntityPaths()

        and: "Alt path ISLs have not enough bandwidth to host the flow"
        def involvedIsls = initialPath.flowPath.getInvolvedIsls()
        def altIsls = topology.getRelatedIsls(topologyHelper.getSwitch(flow.getSource().getSwitchId())) - involvedIsls
        List<SwitchPortVlan> busyEndpoints = flow.occupiedEndpoints()
        altIsls.each {isl ->
            def linkProp = islUtils.toLinkProps(isl, [cost: "1"])
            pathHelper.updateIslsCost([isl], 1)
            def extraFlow = flowFactory.getBuilder(isl.srcSwitch, isl.dstSwitch, false, busyEndpoints)
                    .withBandwidth(northbound.getLink(isl).availableBandwidth - flow.maximumBandwidth + 1).build()
                    .create()
            busyEndpoints.addAll(extraFlow.occupiedEndpoints())
            northbound.deleteLinkProps([linkProp])
        }

        when: "Fail a flow ISL (bring switch port down)"
        Set<Isl> altFlowIsls = []
        allFlowPaths.findAll { it != initialPath.getPathNodes() }.each { altFlowIsls.addAll(pathHelper.getInvolvedIsls(it)) }
        def islToFail = involvedIsls.get(0)
        islHelper.breakIsl(islToFail)

        then: "Flow history shows two reroute attempts, second one succeeds with ignore bw"
        List<FlowHistoryEventExtension> history
        wait(rerouteDelay + WAIT_OFFSET) {
            history = flow.retrieveFlowHistory().getEntriesByType(REROUTE)
            verifyAll {
                history[-2].payload.last().action == REROUTE_FAILED.payloadLastAction
                history[-2].payload.last().details.endsWith(
                        "Failed to find path with requested bandwidth=$flow.maximumBandwidth")
                history[-1].payload.last().action == "Flow reroute completed"
            }
        }

        and: "The flow has changed path and has DEGRADED status"
        flow.retrieveFlowStatus().status == FlowState.DEGRADED
        def pathAfterReroute1 = flow.retrieveAllEntityPaths()
        pathAfterReroute1 != initialPath

        when: "Try to manually reroute the degraded flow, while there is still not enough bandwidth"
        def manualRerouteTime = System.currentTimeSeconds()
        flow.reroute()

        then: "Error is returned, stating a readable reason"
        def error = thrown(HttpClientErrorException)
        new FlowNotReroutedExpectedError(~/Not enough bandwidth or no path found/).matches(error)

        and: "Flow remains DEGRADED and on the same path"
        wait(rerouteDelay + WAIT_OFFSET) {
            assert flow.retrieveFlowHistory(manualRerouteTime).getEntriesByType(REROUTE).findAll {
                it.details == "Reason: initiated via Northbound"
            }.size() == 2 //reroute + retry
            assert flow.retrieveFlowStatus().status == FlowState.DEGRADED
        }
        flow.retrieveAllEntityPaths() == pathAfterReroute1

        when: "Trigger auto reroute by blinking not involved(in flow path) isl"
        def islToBlink = topology.islsForActiveSwitches.find {
            [it.srcSwitch.dpId, it.dstSwitch.dpId].intersect([flow.source.switchId, flow.destination.switchId]).empty
        }
        antiflap.portDown(islToBlink.srcSwitch.dpId, islToBlink.srcPort)
        antiflap.portUp(islToBlink.srcSwitch.dpId, islToBlink.srcPort)

        then: "System tries to reroute the DEGRADED flow"
        and: "Flow remains DEGRADED and on the same path"
        wait(rerouteDelay + WAIT_OFFSET) {
            assert flow.retrieveFlowHistory().getEntriesByType(REROUTE).findAll {
                it.details.contains("status become ACTIVE")
            }.size() == 2 //reroute + retry
            assert flow.retrieveFlowStatus().status == FlowState.DEGRADED
        }

        flow.retrieveAllEntityPaths() == pathAfterReroute1

        when: "Broken ISL on the original path is back online"
        islHelper.restoreIsl(islToFail)

        then: "Flow is rerouted to the original path to UP state"
        wait(rerouteDelay + WAIT_OFFSET) {
            assert flow.retrieveFlowStatus().status == FlowState.UP
        }
    }

}
