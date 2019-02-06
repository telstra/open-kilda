package org.openkilda.functionaltests.spec.flows

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.model.system.FeatureTogglesDto
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition.Isl

import spock.lang.Narrative
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

@Narrative("Verify different cases when Kilda is supposed to automatically reroute certain flow(s).")
class AutoRerouteSpec extends BaseSpecification {

    def "Flow is rerouted when one of the flow ISLs fails"() {
        given: "A flow with one alternative path at least"
        def (flow, allFlowPaths) = noIntermediateSwitchFlow(true, true)
        flowHelper.addFlow(flow)
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.id))

        when: "Fail a flow ISL (bring switch port down)"
        Set<Isl> altFlowIsls = []
        def flowIsls = pathHelper.getInvolvedIsls(flowPath)
        allFlowPaths.findAll { it != flowPath }.each { altFlowIsls.addAll(pathHelper.getInvolvedIsls(it)) }
        def islToFail = flowIsls.find { !(it in altFlowIsls) && !(islUtils.reverseIsl(it) in altFlowIsls) }
        northbound.portDown(islToFail.srcSwitch.dpId, islToFail.srcPort)

        then: "The flow was rerouted after reroute timeout"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert northbound.getFlowStatus(flow.id).status == FlowState.UP
            assert PathHelper.convert(northbound.getFlowPath(flow.id)) != flowPath
        }

        and: "Revive the ISL back (bring switch port up) and delete the flow"
        northbound.portUp(islToFail.srcSwitch.dpId, islToFail.srcPort)
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
    }

    def "Flow goes to 'Down' status when one of the flow ISLs fails and there is no ability to reroute"() {
        given: "A flow without alternative paths"
        def (flow, allFlowPaths) = noIntermediateSwitchFlow(false, true)
        flowHelper.addFlow(flow)
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.id))

        def altPaths = allFlowPaths.findAll { it != flowPath }
        List<PathNode> broughtDownPorts = []
        altPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northbound.portDown(src.switchId, src.portNo)
        }

        when: "One of the flow ISLs goes down"
        def isl = pathHelper.getInvolvedIsls(flowPath).first()
        northbound.portDown(isl.dstSwitch.dpId, isl.dstPort)

        then: "The flow becomes 'Down'"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) { assert northbound.getFlowStatus(flow.id).status == FlowState.DOWN }

        when: "ISL goes back up"
        northbound.portUp(isl.dstSwitch.dpId, isl.dstPort)
        Wrappers.wait(antiflapCooldown + discoveryInterval + WAIT_OFFSET) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED
        }

        then: "The flow becomes 'Up'"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert northbound.getFlowStatus(flow.id).status == FlowState.UP
        }

        and: "Restore topology to the original state, remove the flow"
        broughtDownPorts.every { northbound.portUp(it.switchId, it.portNo) }
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
    }

    def "Flow is rerouted when an intermediate switch is disconnected"() {
        requireProfiles("virtual")

        given: "An intermediate-switch flow with one alternative path at least"
        def flow = intermediateSwitchFlow(true)
        flowHelper.addFlow(flow)
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.id))

        when: "An intermediate switch is disconnected"
        lockKeeper.knockoutSwitch(flowPath[1].switchId)

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
            assert northbound.getFlowStatus(flow.id).status == FlowState.UP
            assert PathHelper.convert(northbound.getFlowPath(flow.id)) != flowPath
        }

        and: "Connect the intermediate switch back and delete the flow"
        lockKeeper.reviveSwitch(flowPath[1].switchId)
        Wrappers.wait(WAIT_OFFSET) { assert flowPath[1].switchId in northbound.getActiveSwitches()*.switchId }
        northbound.deleteSwitchRules(flowPath[1].switchId, DeleteRulesAction.IGNORE_DEFAULTS) || true
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
    }

    @Unroll
    def "Flow goes to 'Down' status when #switchType switch is disconnected (#flowType)"() {
        requireProfiles("virtual")

        given: "#flowType.capitalize()"
        //TODO(ylobankov): Remove this code once the issue #1464 is resolved.
        assumeTrue("Test is skipped because of the issue #1464", switchType != "single")

        flowHelper.addFlow(flow)

        when: "The #switchType switch is disconnected"
        lockKeeper.knockoutSwitch(sw)

        then: "The flow becomes 'Down'"
        Wrappers.wait(discoveryTimeout + rerouteDelay + WAIT_OFFSET * 2) {
            assert northbound.getFlowStatus(flow.id).status == FlowState.DOWN
        }

        when: "The #switchType switch is connected back"
        lockKeeper.reviveSwitch(sw)

        then: "The flow becomes 'Up'"
        Wrappers.wait(rerouteDelay + discoveryInterval + WAIT_OFFSET) {
            assert northbound.getFlowStatus(flow.id).status == FlowState.UP
        }

        and: "Remove the flow"
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }

        where:
        flowType                      | switchType    | flow                       | sw
        "single-switch flow"          | "single"      | singleSwitchFlow()         | flow.source.datapath
        "no-intermediate-switch flow" | "source"      | noIntermediateSwitchFlow() | flow.source.datapath
        "no-intermediate-switch flow" | "destination" | noIntermediateSwitchFlow() | flow.destination.datapath
        "intermediate-switch flow"    | "source"      | intermediateSwitchFlow()   | flow.source.datapath
        "intermediate-switch flow"    | "destination" | intermediateSwitchFlow()   | flow.destination.datapath
    }

    @Unroll
    def "Flow goes to 'Down' status when an intermediate switch is disconnected and there is no ability to reroute"() {
        requireProfiles("virtual")

        given: "An intermediate-switch flow without alternative paths"
        def (flow, allFlowPaths) = intermediateSwitchFlow(false, true)
        flowHelper.addFlow(flow)
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.id))

        def altPaths = allFlowPaths.findAll { it != flowPath && it.first().portNo != flowPath.first().portNo }
        List<PathNode> broughtDownPorts = []
        altPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northbound.portDown(src.switchId, src.portNo)
        }

        when: "The intermediate switch is disconnected"
        lockKeeper.knockoutSwitch(flowPath[1].switchId)

        then: "The flow becomes 'Down'"
        Wrappers.wait(discoveryTimeout + rerouteDelay + WAIT_OFFSET * 2) {
            assert northbound.getFlowStatus(flow.id).status == FlowState.DOWN
        }

        when: "Set flowsRerouteOnIslDiscovery=#flowsRerouteOnIslDiscovery"
        northbound.toggleFeature(FeatureTogglesDto.builder()
                        .flowsRerouteOnIslDiscoveryEnabled(flowsRerouteOnIslDiscovery)
                        .build())

        and: "Connect the intermediate switch back"
        lockKeeper.reviveSwitch(flowPath[1].switchId)
        Wrappers.wait(WAIT_OFFSET) { assert northbound.activeSwitches*.switchId.contains(flowPath[1].switchId) }

        then: "The flow is #flowStatus"
        TimeUnit.SECONDS.sleep(discoveryInterval + rerouteDelay + WAIT_OFFSET * 2)
        northbound.getFlowStatus(flow.id).status == flowStatus

        and: "Restore topology to the original state, remove the flow, reset toggles"
        northbound.toggleFeature(FeatureTogglesDto.builder()
                .flowsRerouteOnIslDiscoveryEnabled(true)
                .build())
        broughtDownPorts.every { northbound.portUp(it.switchId, it.portNo) }
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }

        where:
        flowsRerouteOnIslDiscovery | flowStatus
        true                       | FlowState.UP
        false                      | FlowState.DOWN
    }

    def "Flow in 'Down' status is rerouted when discovering a new ISL"() {
        given: "An intermediate-switch flow with one alternative path at least"
        def (flow, allFlowPaths) = noIntermediateSwitchFlow(true, true)
        flowHelper.addFlow(flow)
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.id))

        when: "Bring all ports down on the source switch that are involved in the current and alternative paths"
        List<PathNode> broughtDownPorts = []
        allFlowPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northbound.portDown(src.switchId, src.portNo)
        }

        then: "The flow goes to 'Down' status"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) { assert northbound.getFlowStatus(flow.id).status == FlowState.DOWN }

        when: "Bring all ports up on the source switch that are involved in the alternative paths"
        broughtDownPorts.findAll {
            it.portNo != flowPath.first().portNo
        }.each {
            northbound.portUp(it.switchId, it.portNo)
        }

        then: "The flow goes to 'Up' status"
        Wrappers.wait(rerouteDelay + discoveryInterval + WAIT_OFFSET * 2) {
            assert northbound.getFlowStatus(flow.id).status == FlowState.UP
        }

        and: "The flow was rerouted"
        PathHelper.convert(northbound.getFlowPath(flow.id)) != flowPath
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.id).status == FlowState.UP }

        and: "Bring port involved in the original path up and delete the flow"
        northbound.portUp(flowPath.first().switchId, flowPath.first().portNo)
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
    }

    def "Flow in 'Down' status is rerouted when connecting a new switch"() {
        requireProfiles("virtual")

        given: "An intermediate-switch flow with one alternative path at least"
        def (flow, allFlowPaths) = intermediateSwitchFlow(true, true)
        flowHelper.addFlow(flow)
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.id))

        when: "Disconnect all intermediate switches that are involved in the current and alternative paths"
        List<PathNode> disconnectedSwitches = []
        allFlowPaths.flatten().unique { it.switchId }.findAll {
            !(it.switchId in [flowPath.first(), flowPath.last()]*.switchId)
        }.each { sw ->
            disconnectedSwitches.add(sw)
            lockKeeper.knockoutSwitch(sw.switchId)
        }
        Wrappers.wait(WAIT_OFFSET) {
            def actualSwitches = northbound.activeSwitches*.switchId
            assert !actualSwitches.any { it in disconnectedSwitches*.switchId }
        }

        then: "The flow goes to 'Down' status"
        Wrappers.wait(discoveryTimeout + rerouteDelay + WAIT_OFFSET) {
            assert northbound.getFlowStatus(flow.id).status == FlowState.DOWN
        }

        when: "Connect switches that are involved in the alternative paths"
        disconnectedSwitches.findAll { !(it.switchId in flowPath*.switchId) }.each {
            lockKeeper.reviveSwitch(it.switchId)
            disconnectedSwitches.remove(it)
        }
        def connectedSwitches = topology.activeSwitches*.dpId.findAll { !disconnectedSwitches*.switchId.contains(it) }
        Wrappers.wait(WAIT_OFFSET) {
            def actualSwitches = northbound.activeSwitches*.switchId
            assert actualSwitches.containsAll(connectedSwitches)
        }

        then: "The flow goes to 'Up' status"
        Wrappers.wait(rerouteDelay + discoveryInterval + WAIT_OFFSET) {
            assert northbound.getFlowStatus(flow.id).status == FlowState.UP
        }

        and: "The flow was rerouted"
        PathHelper.convert(northbound.getFlowPath(flow.id)) != flowPath

        and: "Connect switches back involved in the original path and delete the flow"
        disconnectedSwitches.each { lockKeeper.reviveSwitch(it.switchId) }
        Wrappers.wait(WAIT_OFFSET) {
            def activeSwitches = northbound.getActiveSwitches()*.switchId
            disconnectedSwitches.each { assert it.switchId in activeSwitches }
        }
        disconnectedSwitches.each { northbound.deleteSwitchRules(it.switchId, DeleteRulesAction.IGNORE_DEFAULTS) }
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
    }

    def "Flow in 'Up' status is not rerouted when discovering a new ISL and more preferable path is available"() {
        given: "A flow with one alternative path at least"
        def (flow, allFlowPaths) = noIntermediateSwitchFlow(true, true)
        flowHelper.addFlow(flow)
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.id))

        and: "Make the current flow path less preferable than others"
        allFlowPaths.findAll { it != flowPath }.each { pathHelper.makePathMorePreferable(it, flowPath) }

        when: "One of the links not used by flow goes down"
        def involvedIsls = pathHelper.getInvolvedIsls(flowPath)
        def islToFail = topology.islsForActiveSwitches.find {
            !involvedIsls.contains(it) && !involvedIsls.contains(islUtils.reverseIsl(it))
        }
        northbound.portDown(islToFail.srcSwitch.dpId, islToFail.srcPort)

        then: "Link status becomes 'FAILED'"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert islUtils.getIslInfo(islToFail).get().state == IslChangeType.FAILED
        }

        when: "Failed link goes up"
        northbound.portUp(islToFail.srcSwitch.dpId, islToFail.srcPort)

        then: "Link status becomes 'DISCOVERED'"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert islUtils.getIslInfo(islToFail).get().state == IslChangeType.DISCOVERED
        }

        and: "The flow is not rerouted and doesn't use more preferable path"
        TimeUnit.SECONDS.sleep(rerouteDelay + WAIT_OFFSET)
        northbound.getFlowStatus(flow.id).status == FlowState.UP
        PathHelper.convert(northbound.getFlowPath(flow.id)) == flowPath

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)
    }

    def "Flow in 'Up' status is not rerouted when connecting a new switch and more preferable path is available"() {
        requireProfiles("virtual")

        given: "A flow with one alternative path at least"
        def (flow, allFlowPaths) = noIntermediateSwitchFlow(true, true)
        flowHelper.addFlow(flow)
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.id))

        and: "Make the current flow path less preferable than others"
        allFlowPaths.findAll { it != flowPath }.each { pathHelper.makePathMorePreferable(it, flowPath) }

        when: "Disconnect one of the switches not used by flow"
        def involvedSwitches = pathHelper.getInvolvedSwitches(flowPath)
        def switchToDisconnect = topology.getActiveSwitches().find { !involvedSwitches.contains(it) }
        lockKeeper.knockoutSwitch(switchToDisconnect.dpId)

        then: "The switch is really disconnected from the controller"
        Wrappers.wait(WAIT_OFFSET) { assert !(switchToDisconnect.dpId in northbound.getActiveSwitches()*.switchId) }

        when: "Connect the switch back to the controller"
        lockKeeper.reviveSwitch(switchToDisconnect.dpId)

        then: "The switch is really connected to the controller"
        Wrappers.wait(WAIT_OFFSET) { assert switchToDisconnect.dpId in northbound.getActiveSwitches()*.switchId }

        and: "The flow is not rerouted and doesn't use more preferable path"
        TimeUnit.SECONDS.sleep(rerouteDelay + WAIT_OFFSET)
        northbound.getFlowStatus(flow.id).status == FlowState.UP
        PathHelper.convert(northbound.getFlowPath(flow.id)) == flowPath

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)
    }

    def singleSwitchFlow() {
        flowHelper.singleSwitchFlow(topology.getActiveSwitches().first())
    }

    def noIntermediateSwitchFlow(boolean ensureAltPathsExist = false, boolean getAllPaths = false) {
        def flowWithPaths = getFlowWithPaths(2, 2, ensureAltPathsExist ? 1 : 0)
        return getAllPaths ? flowWithPaths : flowWithPaths[0]
    }

    def intermediateSwitchFlow(boolean ensureAltPathsExist = false, boolean getAllPaths = false) {
        def flowWithPaths = getFlowWithPaths(3, Integer.MAX_VALUE, ensureAltPathsExist ? 1 : 0)
        return getAllPaths ? flowWithPaths : flowWithPaths[0]
    }

    def getFlowWithPaths(int minSwitchesInPath, int maxSwitchesInPath, int minAltPaths) {
        def allFlowPaths = []
        def switches = topology.getActiveSwitches()
        def switchPair = [switches, switches].combinations().findAll {
            src, dst -> src != dst
        }.unique {
            it.sort()
        }.find { src, dst ->
            allFlowPaths = database.getPaths(src.dpId, dst.dpId)*.path
            allFlowPaths.size() > minAltPaths && allFlowPaths.min {
                it.size()
            }.size() in (minSwitchesInPath..maxSwitchesInPath)
        } ?: assumeTrue("No suiting switches found", false)

        return [flowHelper.randomFlow(switchPair[0], switchPair[1]), allFlowPaths]
    }

    def cleanup() {
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        database.resetCosts()
    }
}
