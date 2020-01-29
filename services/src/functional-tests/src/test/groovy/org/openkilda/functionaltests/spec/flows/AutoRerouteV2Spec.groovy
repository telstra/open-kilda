package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.messaging.model.system.FeatureTogglesDto
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.model.SwitchStatus
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2
import org.openkilda.testing.model.topology.TopologyDefinition.Isl

import spock.lang.Narrative
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

@Narrative("Verify different cases when Kilda is supposed to automatically reroute certain flow(s).")
class AutoRerouteV2Spec extends HealthCheckSpecification {

    @Tags(SMOKE)
    def "Flow is rerouted when one of the flow ISLs fails"() {
        given: "A flow with one alternative path at least"
        def (FlowRequestV2 flow, allFlowPaths) = noIntermediateSwitchFlow(1, true)
        flowHelperV2.addFlow(flow)
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.flowId))

        when: "Fail a flow ISL (bring switch port down)"
        Set<Isl> altFlowIsls = []
        def flowIsls = pathHelper.getInvolvedIsls(flowPath)
        allFlowPaths.findAll { it != flowPath }.each { altFlowIsls.addAll(pathHelper.getInvolvedIsls(it)) }
        def islToFail = flowIsls.find { !(it in altFlowIsls) && !(it.reversed in altFlowIsls) }
        antiflap.portDown(islToFail.srcSwitch.dpId, islToFail.srcPort)

        then: "The flow was rerouted after reroute timeout"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP
            assert PathHelper.convert(northbound.getFlowPath(flow.flowId)) != flowPath
        }

        and: "Flow writes stats"
        statsHelper.verifyFlowWritesStats(flow.flowId)

        and: "Revive the ISL back (bring switch port up) and delete the flow"
        antiflap.portUp(islToFail.srcSwitch.dpId, islToFail.srcPort)
        flowHelperV2.deleteFlow(flow.flowId)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts()
    }

    @Tags(SMOKE)
    def "Flow goes to 'Down' status when one of the flow ISLs fails and there is no ability to reroute"() {
        given: "A flow without alternative paths"
        def (flow, allFlowPaths) = noIntermediateSwitchFlow(0, true)
        flowHelperV2.addFlow(flow)
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.flowId))

        def altPaths = allFlowPaths.findAll { it != flowPath }
        List<PathNode> broughtDownPorts = []
        altPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            antiflap.portDown(src.switchId, src.portNo)
        }

        when: "One of the flow ISLs goes down"
        def isl = pathHelper.getInvolvedIsls(flowPath).first()
        antiflap.portDown(isl.dstSwitch.dpId, isl.dstPort)

        then: "The flow becomes 'Down'"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) { assert northbound.getFlowStatus(flow.flowId).status == FlowState.DOWN }

        when: "ISL goes back up"
        antiflap.portUp(isl.dstSwitch.dpId, isl.dstPort)
        Wrappers.wait(antiflapCooldown + discoveryInterval + WAIT_OFFSET) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED
        }

        then: "The flow becomes 'Up'"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP
        }

        and: "Restore topology to the original state, remove the flow"
        broughtDownPorts.every { antiflap.portUp(it.switchId, it.portNo) }
        flowHelperV2.deleteFlow(flow.flowId)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts()
    }

    @Tags([VIRTUAL, LOW_PRIORITY])
    //the actual reroute is caused by the ISL down event which follows the initial sw disconnect
    def "Flow is rerouted when an intermediate switch is disconnected"() {
        given: "An intermediate-switch flow with one alternative path at least"
        def flow = intermediateSwitchFlow(1)
        flowHelperV2.addFlow(flow)
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.flowId))

        when: "An intermediate switch is disconnected"
        lockKeeper.knockoutSwitch(findSw(flowPath[1].switchId))

        then: "All ISLs going through the intermediate switch are 'FAILED'"
        Wrappers.wait(discoveryTimeout * 1.5 + WAIT_OFFSET) {
            northbound.getAllLinks().findAll {
                flowPath[1].switchId == it.source.switchId || flowPath[1].switchId == it.destination.switchId
            }.each {
                assert it.state == IslChangeType.FAILED
            }
        }

        and: "The flow was rerouted after reroute timeout"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP
            assert PathHelper.convert(northbound.getFlowPath(flow.flowId)) != flowPath
        }

        and: "Connect the intermediate switch back and delete the flow"
        lockKeeper.reviveSwitch(findSw(flowPath[1].switchId))
        Wrappers.wait(WAIT_OFFSET) { assert flowPath[1].switchId in northbound.getActiveSwitches()*.switchId }
        northbound.deleteSwitchRules(flowPath[1].switchId, DeleteRulesAction.IGNORE_DEFAULTS) || true
        flowHelperV2.deleteFlow(flow.flowId)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
    }

    @Unroll
    @Tags([VIRTUAL, LOW_PRIORITY])
    //the actual reroute is caused by the ISL down event which follows the initial sw disconnect
    def "Flow goes to 'Down' status when an intermediate switch is disconnected and there is no ability to reroute"() {
        given: "An intermediate-switch flow without alternative paths"
        def (flow, allFlowPaths) = intermediateSwitchFlow(0, true)
        flowHelperV2.addFlow(flow)
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.flowId))

        def altPaths = allFlowPaths.findAll { it != flowPath && it.first().portNo != flowPath.first().portNo }
        List<PathNode> broughtDownPorts = []
        altPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            antiflap.portDown(src.switchId, src.portNo)
        }

        when: "The intermediate switch is disconnected"
        lockKeeper.knockoutSwitch(findSw(flowPath[1].switchId))

        then: "The flow becomes 'Down'"
        Wrappers.wait(discoveryTimeout + rerouteDelay + WAIT_OFFSET * 2) {
            assert northbound.getFlowStatus(flow.flowId).status == FlowState.DOWN
        }

        when: "Set flowsRerouteOnIslDiscovery=#flowsRerouteOnIslDiscovery"
        northbound.toggleFeature(FeatureTogglesDto.builder()
                                                  .flowsRerouteOnIslDiscoveryEnabled(flowsRerouteOnIslDiscovery)
                                                  .build())

        and: "Connect the intermediate switch back"
        lockKeeper.reviveSwitch(findSw(flowPath[1].switchId))
        Wrappers.wait(WAIT_OFFSET) { assert northbound.activeSwitches*.switchId.contains(flowPath[1].switchId) }

        then: "The flow is #flowStatus"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getLinks(flowPath[1].switchId, null, null, null)
                      .each { assert it.state == IslChangeType.DISCOVERED }
        }
        Wrappers.wait(WAIT_OFFSET + rerouteDelay) { assert northbound.getFlowStatus(flow.flowId).status == flowStatus }

        and: "Restore topology to the original state, remove the flow, reset toggles"
        flowHelperV2.deleteFlow(flow.flowId)
        northbound.toggleFeature(FeatureTogglesDto.builder().flowsRerouteOnIslDiscoveryEnabled(true).build())
        broughtDownPorts.every { antiflap.portUp(it.switchId, it.portNo) }
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts()

        where:
        flowsRerouteOnIslDiscovery | flowStatus
        true                       | FlowState.UP
        false                      | FlowState.DOWN
    }

    @Tags(SMOKE)
    def "Flow in 'Down' status is rerouted when discovering a new ISL"() {
        given: "An intermediate-switch flow with one alternative path at least"
        def (flow, allFlowPaths) = noIntermediateSwitchFlow(1, true)
        flowHelperV2.addFlow(flow)
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.flowId))

        when: "Bring all ports down on the source switch that are involved in the current and alternative paths"
        List<PathNode> broughtDownPorts = []
        allFlowPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            antiflap.portDown(src.switchId, src.portNo)
        }

        then: "The flow goes to 'Down' status"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) { assert northbound.getFlowStatus(flow.flowId).status == FlowState.DOWN }

        when: "Bring all ports up on the source switch that are involved in the alternative paths"
        broughtDownPorts.findAll {
            it.portNo != flowPath.first().portNo
        }.each {
            antiflap.portUp(it.switchId, it.portNo)
        }

        then: "The flow goes to 'Up' status"
        Wrappers.wait(rerouteDelay + discoveryInterval + WAIT_OFFSET * 2) {
            assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP
        }

        and: "The flow was rerouted"
        PathHelper.convert(northbound.getFlowPath(flow.flowId)) != flowPath
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP }

        and: "Bring port involved in the original path up and delete the flow"
        antiflap.portUp(flowPath.first().switchId, flowPath.first().portNo)
        flowHelperV2.deleteFlow(flow.flowId)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts()
    }

    @Tags(SMOKE)
    def "Flow in 'Up' status is not rerouted when discovering a new ISL and more preferable path is available"() {
        given: "A flow with one alternative path at least"
        def (flow, allFlowPaths) = noIntermediateSwitchFlow(1, true)
        flowHelperV2.addFlow(flow)
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.flowId))

        and: "Make the current flow path less preferable than others"
        allFlowPaths.findAll { it != flowPath }.each { pathHelper.makePathMorePreferable(it, flowPath) }

        when: "One of the links not used by flow goes down"
        def involvedIsls = pathHelper.getInvolvedIsls(flowPath)
        def islToFail = topology.islsForActiveSwitches.find {
            !involvedIsls.contains(it) && !involvedIsls.contains(it.reversed)
        }
        antiflap.portDown(islToFail.srcSwitch.dpId, islToFail.srcPort)

        then: "Link status becomes 'FAILED'"
        Wrappers.wait(WAIT_OFFSET) { assert islUtils.getIslInfo(islToFail).get().state == IslChangeType.FAILED }

        when: "Failed link goes up"
        antiflap.portUp(islToFail.srcSwitch.dpId, islToFail.srcPort)

        then: "Link status becomes 'DISCOVERED'"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert islUtils.getIslInfo(islToFail).get().state == IslChangeType.DISCOVERED
        }

        and: "The flow is not rerouted and doesn't use more preferable path"
        TimeUnit.SECONDS.sleep(rerouteDelay + WAIT_OFFSET)
        northbound.getFlowStatus(flow.flowId).status == FlowState.UP
        PathHelper.convert(northbound.getFlowPath(flow.flowId)) == flowPath

        and: "Delete the flow"
        flowHelperV2.deleteFlow(flow.flowId)
        database.resetCosts()
    }

    @Tags([VIRTUAL, SMOKE])
    def "Flow in 'Up' status is not rerouted when connecting a new switch and more preferable path is available"() {
        given: "A flow with one alternative path at least"
        def (flow, allFlowPaths) = noIntermediateSwitchFlow(1, true)
        flowHelperV2.addFlow(flow)
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.flowId))

        and: "Make the current flow path less preferable than others"
        allFlowPaths.findAll { it != flowPath }.each { pathHelper.makePathMorePreferable(it, flowPath) }

        when: "Disconnect one of the switches not used by flow"
        def involvedSwitches = pathHelper.getInvolvedSwitches(flowPath)
        def switchToDisconnect = topology.getActiveSwitches().find { !involvedSwitches.contains(it) }
        lockKeeper.knockoutSwitch(switchToDisconnect)

        then: "The switch is really disconnected from the controller"
        Wrappers.wait(WAIT_OFFSET) { assert !(switchToDisconnect.dpId in northbound.getActiveSwitches()*.switchId) }

        when: "Connect the switch back to the controller"
        lockKeeper.reviveSwitch(switchToDisconnect)

        then: "The switch is really connected to the controller"
        Wrappers.wait(WAIT_OFFSET) { assert switchToDisconnect.dpId in northbound.getActiveSwitches()*.switchId }

        and: "The flow is not rerouted and doesn't use more preferable path"
        TimeUnit.SECONDS.sleep(rerouteDelay + WAIT_OFFSET)
        northbound.getFlowStatus(flow.flowId).status == FlowState.UP
        PathHelper.convert(northbound.getFlowPath(flow.flowId)) == flowPath

        and: "Delete the flow"
        flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tags([HARDWARE, SMOKE])
    def "Flow is not rerouted when one of the flow ports goes down"() {
        given: "An intermediate-switch flow with one alternative path at least"
        def (FlowRequestV2 flow, List<List<PathNode>> allFlowPaths) = intermediateSwitchFlow(1, true)
        flowHelperV2.addFlow(flow)
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.flowId))

        and: "Make the current flow path less preferable than others"
        allFlowPaths.findAll { it != flowPath }.each { pathHelper.makePathMorePreferable(it, flowPath) }

        when: "Bring the flow port down on the source switch"
        antiflap.portDown(flow.source.switchId, flow.source.portNumber)

        then: "The flow is not rerouted"
        TimeUnit.SECONDS.sleep(rerouteDelay)
        PathHelper.convert(northbound.getFlowPath(flow.flowId)) == flowPath

        when: "Bring the flow port down on the destination switch"
        antiflap.portDown(flow.destination.switchId, flow.destination.portNumber)

        then: "The flow is not rerouted"
        TimeUnit.SECONDS.sleep(rerouteDelay)
        PathHelper.convert(northbound.getFlowPath(flow.flowId)) == flowPath

        and: "Bring flow ports up and delete the flow"
        ["source", "destination"].each { antiflap.portUp(flow."$it".switchId, flow."$it".portNumber) }
        flowHelperV2.deleteFlow(flow.flowId)
        database.resetCosts()
    }

    def "System doesn't reroute flow to a path with not enough bandwidth available"() {
        given: "A flow with alt path available"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue("No suiting switches found", false)

        def flow = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(flow)

        and: "Bring all ports down on the source switch that are not involved in the current and alternative paths"
        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.flowId))
        def altPath = switchPair.paths.find { it != currentPath }
        List<PathNode> broughtDownPorts = []
        switchPair.paths.findAll { it != currentPath }
                .unique { it.first() }
                .each { path ->
                    def src = path.first()
                    broughtDownPorts.add(src)
                    antiflap.portDown(src.switchId, src.portNo)
                }
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == IslChangeType.FAILED
            }.size() == broughtDownPorts.size() * 2
        }

        when: "Make alt path ISLs to have not enough bandwidth to handle the flow"
        def altIsls = pathHelper.getInvolvedIsls(altPath)
        altIsls.each {
            database.updateIslAvailableBandwidth(it, flow.maximumBandwidth - 1)
            database.updateIslAvailableBandwidth(it.reversed, flow.maximumBandwidth - 1)
        }

        and: "Break isl on the main path(bring port down on the source switch) to init auto reroute"
        def islToBreak = pathHelper.getInvolvedIsls(currentPath).first()
        antiflap.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        Wrappers.wait(antiflapMin + 2) {
            assert islUtils.getIslInfo(islToBreak).get().state == IslChangeType.FAILED
        }

        then: "Flow state is changed to DOWN"
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getFlowStatus(flow.flowId).status == FlowState.DOWN
        }

        and: "Flow is not rerouted"
        Wrappers.timedLoop(rerouteDelay) {
            assert pathHelper.convert(northbound.getFlowPath(flow.flowId)) == currentPath
        }

        and: "Cleanup: Restore topology, delete flow and reset costs/bandwidth"
        broughtDownPorts.every { antiflap.portUp(it.switchId, it.portNo) }
        antiflap.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        flowHelperV2.deleteFlow(flow.flowId)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        altIsls.each {
            database.resetIslBandwidth(it)
            database.resetIslBandwidth(it.reversed)
        }
        database.resetCosts()
    }

    @Tidy
    @Tags(VIRTUAL)
    def "Flow in 'Down' status is rerouted after switchUp event"() {
        given: "First switch pair with two parallel links and two available paths"
        assumeTrue("Reroute should be completed before link is FAILED", rerouteDelay * 2 < discoveryTimeout)
        def switchPair1 = topologyHelper.getAllNeighboringSwitchPairs().find {
            it.paths.findAll { it.size() == 2 }.size() > 1
        } ?: assumeTrue("No suiting switches found for the first flow", false)

        and: "Second switch pair where the srс switch from the first switch pair is a transit switch"
        List<PathNode> secondFlowPath
        def switchPair2 = topologyHelper.getAllNotNeighboringSwitchPairs().find { swP ->
            (swP.paths + swP.getReversed().paths).find { pathCandidate ->
                secondFlowPath = pathCandidate
                def involvedSwitches = pathHelper.getInvolvedSwitches(pathCandidate)
                involvedSwitches.size() == 3 && involvedSwitches[1].dpId == switchPair1.src.dpId &&
                        involvedSwitches[-1].dpId == switchPair1.dst.dpId
                /**
                 * Because of this condition we have to include all reversed(mirrored) switch pairs during search.
                 * Because all remaining switch pairs may use switchPair1.dst.dpId as their src
                 */
            }
        } ?: assumeTrue("No suiting switches found for the second flow", false)

        //Main and backup paths of firstFlow for further manipulation with them
        def firstFlowMainPath = switchPair1.paths.min { it.size() }
        def firstFlowBackupPath = switchPair1.paths.findAll { it != firstFlowMainPath }.min { it.size() }
        def firstFlowAltPaths = switchPair1.paths.findAll {
            it != firstFlowMainPath && it != firstFlowBackupPath && it[0] != secondFlowPath[1]
        } // exclude firstFlowMainPath, firstFlowBackupPath and link which is used by second flow
        def secondFlowAltPaths = switchPair2.paths.findAll { it[1] != secondFlowPath[1] }

        //All alternative paths for both flows are unavailable (bring ports down on the srcSwitch)
        List<PathNode> broughtDownPorts = []
        (firstFlowAltPaths + secondFlowAltPaths).unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            antiflap.portDown(src.switchId, src.portNo)
        }
        Wrappers.wait(antiflapMin + WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == FAILED
            }.size() == broughtDownPorts.size() * 2
        }

        //firstFlowMainPath path more preferable than the firstFlowBackupPath
        pathHelper.makePathMorePreferable(firstFlowMainPath, firstFlowBackupPath)

        and: "First flow without transit switches"
        def firstFlow = flowHelperV2.randomFlow(switchPair1)
        flowHelperV2.addFlow(firstFlow)
        assert PathHelper.convert(northbound.getFlowPath(firstFlow.flowId)) == firstFlowMainPath

        and: "Second flow with transit switch"
        def secondFlow = flowHelperV2.randomFlow(switchPair2)
        flowHelperV2.addFlow(secondFlow)
        assert PathHelper.convert(northbound.getFlowPath(secondFlow.flowId)) == secondFlowPath

        when: "Disconnect the src switch of the first flow from the controller"
        def islToBreak = pathHelper.getInvolvedIsls(firstFlowMainPath).first()
        def islToReroute = pathHelper.getInvolvedIsls(firstFlowBackupPath).first()
        lockKeeper.knockoutSwitch(switchPair1.src)
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            assert northbound.getSwitch(switchPair1.src.dpId).state == SwitchChangeType.DEACTIVATED
        }
        def isSwitchActivated = false

        and: "Mark the switch as ACTIVE in db" // just to reproduce #3131
        database.setSwitchStatus(switchPair1.src.dpId, SwitchStatus.ACTIVE)

        and: "Init auto reroute (bring ports down on the dstSwitch)"
        antiflap.portDown(islToBreak.dstSwitch.dpId, islToBreak.dstPort)

        then: "Flows are not rerouted and flows status are 'Down'"
        def flowPathMap = [(firstFlow.flowId): firstFlowMainPath, (secondFlow.flowId): secondFlowPath]
        TimeUnit.SECONDS.sleep(rerouteDelay * 2) // it helps to be sure that the auto-reroute operation is completed
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert northbound.getLink(islToBreak).state == FAILED
            // just to be sure that backup ISL is not failed
            assert northbound.getLink(islToReroute).state == DISCOVERED
            withPool {
                [firstFlow.flowId, secondFlow.flowId].eachParallel { String flowId ->
                    assert northbound.getFlowStatus(flowId).status == FlowState.DOWN
                    assert PathHelper.convert(northbound.getFlowPath(flowId)) == flowPathMap[flowId]
                }
            }
        }

        when: "Connect the switch back to the controller"
        database.setSwitchStatus(switchPair1.src.dpId, SwitchStatus.INACTIVE) // set real status
        lockKeeper.reviveSwitch(switchPair1.src)
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getSwitch(switchPair1.src.dpId).state == SwitchChangeType.ACTIVATED
        }
        isSwitchActivated = true

        then: "Both flows are rerouted"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            withPool {
                [firstFlow.flowId, secondFlow.flowId].eachParallel { String flowId ->
                    assert northbound.getFlowStatus(flowId).status == FlowState.UP
                    assert PathHelper.convert(northbound.getFlowPath(flowId)) != flowPathMap[flowId]
                }
            }
        }

        and: "Flow is rerouted due to switchUp event"
        //TODO(andriidovhan) specify flow history verification(it is not implemented yet) Reroute reason: switchUp event
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert northbound.getFlowHistory(firstFlow.flowId).findAll { it.action == "Flow rerouting" }.size() == 2
            assert northbound.getFlowHistory(secondFlow.flowId).findAll { it.action == "Flow rerouting" }.size() == 2
        }

        cleanup: "Restore topology, delete the flow and reset costs"
        firstFlow && flowHelperV2.deleteFlow(firstFlow.flowId)
        secondFlow && flowHelperV2.deleteFlow(secondFlow.flowId)
        !isSwitchActivated && lockKeeper.reviveSwitch(switchPair1.src)
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getSwitch(switchPair1.src.dpId).state == SwitchChangeType.ACTIVATED
        }
        islToBreak && antiflap.portUp(islToBreak.dstSwitch.dpId, islToBreak.dstPort)
        broughtDownPorts && broughtDownPorts.each { antiflap.portUp(it.switchId, it.portNo) }
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != FAILED }
        }
    }

    @Tidy
    def "Flow is not rerouted when switchUp event appear for a switch which is not related to the flow"() {
        given: "Given a flow in DOWN status on neighboring switches"
        def swP = topologyHelper.getAllNeighboringSwitchPairs().find {
            it.paths.findAll { it.size() == 2 }.size() == 1
        } ?: assumeTrue("No suiting switches found", false)

        def flowPath = swP.paths.min { it.size() }
        def altPath = swP.paths.findAll { it != flowPath }
        def flow = flowHelperV2.randomFlow(swP)
        flowHelperV2.addFlow(flow)
        assert PathHelper.convert(northbound.getFlowPath(flow.flowId)) == flowPath

        //All alternative paths for both flows are unavailable (bring ports down on the srcSwitch)
        List<PathNode> broughtDownPorts = []
        altPath.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            antiflap.portDown(src.switchId, src.portNo)
        }
        Wrappers.wait(antiflapMin + WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == FAILED
            }.size() == broughtDownPorts.size() * 2
        }

        //move the flow to DOWN status
        def islToBreak = pathHelper.getInvolvedIsls(flowPath).first()
        antiflap.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP

        when: "Generate switchUp event on switch which is not related to the flow"
        def involvedSwitches = pathHelper.getInvolvedSwitches(flowPath)*.dpId
        def switchToManipulate = topology.activeSwitches.find { !(it.dpId in involvedSwitches) }
        lockKeeper.knockoutSwitch(switchToManipulate)
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            assert northbound.getSwitch(switchToManipulate.dpId).state == SwitchChangeType.DEACTIVATED
        }
        def isSwitchActivated = false
        lockKeeper.reviveSwitch(switchToManipulate)
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            assert northbound.getSwitch(switchToManipulate.dpId).state == SwitchChangeType.ACTIVATED
        }
        isSwitchActivated = true

        then: "Flow is not triggered for reroute due to switchUp event because switch is not related to the flow"
        TimeUnit.SECONDS.sleep(rerouteDelay * 2) // it helps to be sure that the auto-reroute operation is completed
        Wrappers.timedLoop(rerouteDelay) { // just in case
            assert northbound.getFlowHistory(flow.flowId).findAll { it.action == "Flow rerouting" }.size() == 1
        }

        cleanup: "Restore topology, delete the flow and reset costs"
        flow && flowHelperV2.deleteFlow(flow.flowId)
        islToBreak && antiflap.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        !isSwitchActivated && lockKeeper.reviveSwitch(switchToManipulate)
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getSwitch(switchToManipulate.dpId).state == SwitchChangeType.ACTIVATED
        }
        broughtDownPorts && broughtDownPorts.each { antiflap.portUp(it.switchId, it.portNo) }
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != FAILED }
        }
    }

    def singleSwitchFlow() {
        flowHelperV2.singleSwitchFlow(topology.getActiveSwitches().first())
    }

    def noIntermediateSwitchFlow(int minAltPathsCount = 0, boolean getAllPaths = false) {
        def flowWithPaths = getFlowWithPaths(topologyHelper.getAllNeighboringSwitchPairs(), minAltPathsCount)
        return getAllPaths ? flowWithPaths : flowWithPaths[0]
    }

    def intermediateSwitchFlow(int minAltPathsCount = 0, boolean getAllPaths = false) {
        def flowWithPaths = getFlowWithPaths(topologyHelper.getAllNotNeighboringSwitchPairs(), minAltPathsCount)
        return getAllPaths ? flowWithPaths : flowWithPaths[0]
    }

    def getFlowWithPaths(List<SwitchPair> switchPairs, int minAltPathsCount) {
        def switchPair = switchPairs.find { it.paths.size() > minAltPathsCount } ?:
                assumeTrue("No suiting switches found", false)
        return [flowHelperV2.randomFlow(switchPair), switchPair.paths]
    }

    def findSw(SwitchId swId) {
        topology.switches.find { it.dpId == swId }
    }

    def cleanup() {
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        database.resetCosts()
    }
}
