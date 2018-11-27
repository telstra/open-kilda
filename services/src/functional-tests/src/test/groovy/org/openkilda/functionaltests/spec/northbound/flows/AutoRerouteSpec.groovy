package org.openkilda.functionaltests.spec.northbound.flows

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.extension.fixture.rule.CleanupSwitches
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.FeatureTogglePayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.tools.IslUtils

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Narrative
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

@Narrative("Verify different cases when Kilda is supposed to automatically reroute certain flow(s).")
@CleanupSwitches
class AutoRerouteSpec extends BaseSpecification {
    @Autowired
    TopologyDefinition topology
    @Autowired
    FlowHelper flowHelper
    @Autowired
    PathHelper pathHelper
    @Autowired
    NorthboundService northboundService
    @Autowired
    LockKeeperService lockKeeperService
    @Autowired
    Database db
    @Autowired
    IslUtils islUtils

    @Value('${reroute.delay}')
    int rerouteDelay
    @Value('${discovery.interval}')
    int discoveryInterval
    @Value('${discovery.timeout}')
    int discoveryTimeout

    def "Flow is rerouted when one of the flow ISLs fails"() {
        given: "A flow with one alternative path at least"
        def (flow, allFlowPaths) = noIntermediateSwitchFlow(true, true)
        northboundService.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        def flowPath = PathHelper.convert(northboundService.getFlowPath(flow.id))

        when: "Fail a flow ISL (bring switch port down)"
        Set<Isl> altFlowIsls = []
        def flowIsls = pathHelper.getInvolvedIsls(flowPath)
        allFlowPaths.findAll { it != flowPath }.each { altFlowIsls.addAll(pathHelper.getInvolvedIsls(it)) }
        def islToFail = flowIsls.find { !(it in altFlowIsls) }
        northboundService.portDown(islToFail.srcSwitch.dpId, islToFail.srcPort)

        then: "The flow was rerouted after reroute timeout"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert northboundService.getFlowStatus(flow.id).status == FlowState.UP
            assert PathHelper.convert(northboundService.getFlowPath(flow.id)) != flowPath
        }

        and: "Revive the ISL back (bring switch port up) and delete the flow"
        northboundService.portUp(islToFail.srcSwitch.dpId, islToFail.srcPort)
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northboundService.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
    }

    def "Flow goes to 'Down' status when one of the flow ISLs fails and there is no ability to reroute"() {
        given: "A flow without alternative paths"
        def (flow, allFlowPaths) = noIntermediateSwitchFlow(false, true)
        northboundService.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        def flowPath = PathHelper.convert(northboundService.getFlowPath(flow.id))

        def altPaths = allFlowPaths.findAll { it != flowPath }
        List<PathNode> broughtDownPorts = []
        altPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northboundService.portDown(src.switchId, src.portNo)
        }

        when: "One of the flow ISLs goes down"
        def isl = pathHelper.getInvolvedIsls(flowPath).first()
        northboundService.portDown(isl.dstSwitch.dpId, isl.dstPort)

        then: "The flow becomes 'Down'"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert northboundService.getFlowStatus(flow.id).status == FlowState.DOWN
        }

        when: "ISL goes back up"
        northboundService.portUp(isl.dstSwitch.dpId, isl.dstPort)

        then: "The flow becomes 'Up'"
        Wrappers.wait(rerouteDelay + discoveryInterval + WAIT_OFFSET) {
            assert northboundService.getFlowStatus(flow.id).status == FlowState.UP
        }

        and: "Restore topology to the original state, remove the flow"
        broughtDownPorts.every { northboundService.portUp(it.switchId, it.portNo) }
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northboundService.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
    }

    def "Flow is rerouted when an intermediate switch is disconnected"() {
        requireProfiles("virtual")

        given: "An intermediate-switch flow with one alternative path at least"
        def flow = intermediateSwitchFlow(true)
        northboundService.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        def flowPath = PathHelper.convert(northboundService.getFlowPath(flow.id))

        when: "An intermediate switch is disconnected"
        lockKeeperService.knockoutSwitch(flowPath[1].switchId)

        then: "All ISLs going through the intermediate switch are 'FAILED'"
        Wrappers.wait(discoveryTimeout * 1.5 + WAIT_OFFSET) {
            northboundService.getAllLinks().findAll { flowPath[1].switchId in it.path*.switchId }.each {
                assert it.state == IslChangeType.FAILED
            }
        }

        and: "The flow was rerouted after reroute timeout"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert northboundService.getFlowStatus(flow.id).status == FlowState.UP
            assert PathHelper.convert(northboundService.getFlowPath(flow.id)) != flowPath
        }

        and: "Connect the intermediate switch back and delete the flow"
        lockKeeperService.reviveSwitch(flowPath[1].switchId)
        Wrappers.wait(WAIT_OFFSET) { assert flowPath[1].switchId in northboundService.getActiveSwitches()*.switchId }
        northboundService.deleteSwitchRules(flowPath[1].switchId, DeleteRulesAction.IGNORE_DEFAULTS) || true
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northboundService.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
    }

    @Unroll
    def "Flow goes to 'Down' status when #switchType switch is disconnected (#flowType)"() {
        requireProfiles("virtual")

        given: "#flowType.capitalize()"
        //TODO(ylobankov): Remove this code once the issue #1464 is resolved.
        assumeTrue("Test is skipped because of the issue #1464", switchType != "single")

        northboundService.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getFlowStatus(flow.id).status == FlowState.UP }

        when: "The #switchType switch is disconnected"
        lockKeeperService.knockoutSwitch(sw)

        then: "The flow becomes 'Down'"
        Wrappers.wait(discoveryTimeout + rerouteDelay + WAIT_OFFSET * 2) {
            assert northboundService.getFlowStatus(flow.id).status == FlowState.DOWN
        }

        when: "The #switchType switch is connected back"
        lockKeeperService.reviveSwitch(sw)

        then: "The flow becomes 'Up'"
        Wrappers.wait(rerouteDelay + discoveryInterval + WAIT_OFFSET) {
            assert northboundService.getFlowStatus(flow.id).status == FlowState.UP
        }

        and: "Remove the flow"
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northboundService.getAllLinks().each { assert it.state != IslChangeType.FAILED }
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
        northboundService.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        def flowPath = PathHelper.convert(northboundService.getFlowPath(flow.id))

        def altPaths = allFlowPaths.findAll { it != flowPath && it.first().portNo != flowPath.first().portNo }
        List<PathNode> broughtDownPorts = []
        altPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northboundService.portDown(src.switchId, src.portNo)
        }

        when: "The intermediate switch is disconnected"
        lockKeeperService.knockoutSwitch(flowPath[1].switchId)

        then: "The flow becomes 'Down'"
        Wrappers.wait(discoveryTimeout + rerouteDelay + WAIT_OFFSET * 2) {
            assert northboundService.getFlowStatus(flow.id).status == FlowState.DOWN
        }

        when: "Set reflow_on_switch_activation=#reflowOnSwitchActivation"
        northboundService.toggleFeature(
                new FeatureTogglePayload(null, reflowOnSwitchActivation, null, null, null, null, null))

        and: "Connect the intermediate switch back"
        lockKeeperService.reviveSwitch(flowPath[1].switchId)
        Wrappers.wait(WAIT_OFFSET) {
            assert northboundService.activeSwitches*.switchId.contains(flowPath[1].switchId)
        }

        then: "The flow is #flowStatus"
        TimeUnit.SECONDS.sleep(discoveryInterval + rerouteDelay + WAIT_OFFSET * 2)
        northboundService.getFlowStatus(flow.id).status == flowStatus

        and: "Restore topology to the original state, remove the flow"
        broughtDownPorts.every { northboundService.portUp(it.switchId, it.portNo) }
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northboundService.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }

        where:
        reflowOnSwitchActivation | flowStatus
        false                    | FlowState.DOWN
        true                     | FlowState.UP
    }

    def "Flow in 'Down' status is rerouted when discovering a new ISL"() {
        given: "An intermediate-switch flow with one alternative path at least"
        def (flow, allFlowPaths) = noIntermediateSwitchFlow(true, true)
        northboundService.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        def flowPath = PathHelper.convert(northboundService.getFlowPath(flow.id))

        when: "Bring all ports down on the source switch that are involved in the current and alternative paths"
        List<PathNode> broughtDownPorts = []
        allFlowPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northboundService.portDown(src.switchId, src.portNo)
        }

        then: "The flow goes to 'Down' status"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert northboundService.getFlowStatus(flow.id).status == FlowState.DOWN
        }

        when: "Bring all ports up on the source switch that are involved in the alternative paths"
        broughtDownPorts.findAll {
            it.portNo != flowPath.first().portNo
        }.each {
            northboundService.portUp(it.switchId, it.portNo)
        }

        then: "The flow goes to 'Up' status"
        Wrappers.wait(rerouteDelay + discoveryInterval + WAIT_OFFSET * 2) {
            assert northboundService.getFlowStatus(flow.id).status == FlowState.UP
        }

        and: "The flow was rerouted"
        PathHelper.convert(northboundService.getFlowPath(flow.id)) != flowPath
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getFlowStatus(flow.id).status == FlowState.UP }

        and: "Bring port involved in the original path up and delete the flow"
        northboundService.portUp(flowPath.first().switchId, flowPath.first().portNo)
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northboundService.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
    }

    def "Flow in 'Down' status is rerouted when connecting a new switch"() {
        requireProfiles("virtual")

        given: "An intermediate-switch flow with one alternative path at least"
        def (flow, allFlowPaths) = intermediateSwitchFlow(true, true)
        northboundService.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        def flowPath = PathHelper.convert(northboundService.getFlowPath(flow.id))

        when: "Disconnect all intermediate switches that are involved in the current and alternative paths"
        List<PathNode> disconnectedSwitches = []
        allFlowPaths.flatten().unique { it.switchId }.findAll {
            !(it.switchId in [flowPath.first(), flowPath.last()]*.switchId)
        }.each { sw ->
            disconnectedSwitches.add(sw)
            lockKeeperService.knockoutSwitch(sw.switchId)
        }
        Wrappers.wait(WAIT_OFFSET) {
            def actualSwitches = northbound.activeSwitches*.switchId
            assert !actualSwitches.any { it in disconnectedSwitches*.switchId }
        }

        then: "The flow goes to 'Down' status"
        Wrappers.wait(discoveryTimeout + rerouteDelay + WAIT_OFFSET) {
            assert northboundService.getFlowStatus(flow.id).status == FlowState.DOWN
        }

        when: "Connect switches that are involved in the alternative paths"
        disconnectedSwitches.findAll { !(it.switchId in flowPath*.switchId) }.each {
            lockKeeperService.reviveSwitch(it.switchId)
            disconnectedSwitches.remove(it)
        }
        def connectedSwitches = topology.activeSwitches*.dpId.findAll { !disconnectedSwitches*.switchId.contains(it) }
        Wrappers.wait(WAIT_OFFSET) {
            def actualSwitches = northbound.activeSwitches*.switchId
            assert actualSwitches.containsAll(connectedSwitches)
        }

        then: "The flow goes to 'Up' status"
        Wrappers.wait(rerouteDelay + discoveryInterval + WAIT_OFFSET) {
            assert northboundService.getFlowStatus(flow.id).status == FlowState.UP
        }

        and: "The flow was rerouted"
        PathHelper.convert(northboundService.getFlowPath(flow.id)) != flowPath

        and: "Connect switches back involved in the original path and delete the flow"
        disconnectedSwitches.each { lockKeeperService.reviveSwitch(it.switchId) }
        Wrappers.wait(WAIT_OFFSET) {
            def activeSwitches = northboundService.getActiveSwitches()*.switchId
            disconnectedSwitches.each { assert it.switchId in activeSwitches }
        }
        disconnectedSwitches.each {
            northboundService.deleteSwitchRules(it.switchId, DeleteRulesAction.IGNORE_DEFAULTS)
        }
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northboundService.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
    }

    def "Flow in 'Up' status is not rerouted when discovering a new ISL and more preferable path is available"() {
        given: "A flow with one alternative path at least"
        def (flow, allFlowPaths) = noIntermediateSwitchFlow(true, true)
        northboundService.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        def flowPath = PathHelper.convert(northboundService.getFlowPath(flow.id))

        and: "Make the current flow path less preferable than others"
        allFlowPaths.findAll { it != flowPath }.each { pathHelper.makePathMorePreferable(it, flowPath) }

        when: "One of the links not used by flow goes down"
        def involvedIsls = pathHelper.getInvolvedIsls(flowPath)
        def islToFail = topology.islsForActiveSwitches.find { !involvedIsls.contains(it) }
        northboundService.portDown(islToFail.srcSwitch.dpId, islToFail.srcPort)

        then: "Link status becomes 'FAILED'"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert islUtils.getIslInfo(islToFail).get().state == IslChangeType.FAILED
        }

        when: "Failed link goes up"
        northboundService.portUp(islToFail.srcSwitch.dpId, islToFail.srcPort)

        then: "Link status becomes 'DISCOVERED'"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert islUtils.getIslInfo(islToFail).get().state == IslChangeType.DISCOVERED
        }

        and: "The flow is not rerouted and doesn't use more preferable path"
        TimeUnit.SECONDS.sleep(rerouteDelay + WAIT_OFFSET)
        northboundService.getFlowStatus(flow.id).status == FlowState.UP
        PathHelper.convert(northboundService.getFlowPath(flow.id)) == flowPath

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)
    }

    def "Flow in 'Up' status is not rerouted when connecting a new switch and more preferable path is available"() {
        requireProfiles("virtual")
        given: "A flow with one alternative path at least"
        def (flow, allFlowPaths) = noIntermediateSwitchFlow(true, true)
        northboundService.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        def flowPath = PathHelper.convert(northboundService.getFlowPath(flow.id))

        and: "Make the current flow path less preferable than others"
        allFlowPaths.findAll { it != flowPath }.each { pathHelper.makePathMorePreferable(it, flowPath) }

        when: "Disconnect one of the switches not used by flow"
        def involvedSwitches = pathHelper.getInvolvedSwitches(flowPath)
        def switchToDisconnect = topology.getActiveSwitches().find { !involvedSwitches.contains(it) }
        lockKeeperService.knockoutSwitch(switchToDisconnect.dpId)

        then: "The switch is really disconnected from the controller"
        Wrappers.wait(WAIT_OFFSET) {
            assert !(switchToDisconnect.dpId in northboundService.getActiveSwitches()*.switchId)
        }

        when: "Connect the switch back to the controller"
        lockKeeperService.reviveSwitch(switchToDisconnect.dpId)

        then: "The switch is really connected to the controller"
        Wrappers.wait(WAIT_OFFSET) {
            assert switchToDisconnect.dpId in northboundService.getActiveSwitches()*.switchId
        }

        and: "The flow is not rerouted and doesn't use more preferable path"
        TimeUnit.SECONDS.sleep(rerouteDelay + WAIT_OFFSET)
        northboundService.getFlowStatus(flow.id).status == FlowState.UP
        PathHelper.convert(northboundService.getFlowPath(flow.id)) == flowPath

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
            allFlowPaths = db.getPaths(src.dpId, dst.dpId)*.path
            allFlowPaths.size() > minAltPaths && allFlowPaths.min {
                it.size()
            }.size() in (minSwitchesInPath..maxSwitchesInPath)
        } ?: assumeTrue("No suiting switches found", false)

        return [flowHelper.randomFlow(switchPair[0], switchPair[1]), allFlowPaths]
    }

    def cleanup() {
        northboundService.deleteLinkProps(northboundService.getAllLinkProps())
        db.resetCosts()
    }
}
