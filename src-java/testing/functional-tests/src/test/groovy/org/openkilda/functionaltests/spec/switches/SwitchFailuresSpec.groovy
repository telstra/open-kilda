package org.openkilda.functionaltests.spec.switches

import static groovyx.gpars.dataflow.Dataflow.task
import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.helpers.thread.FlowHistoryConstants.REROUTE_FAIL
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Ignore
import spock.lang.Narrative

@Narrative("""
This spec verifies different situations when Kilda switches suddenly disconnect from the controller.
Note: For now it is only runnable on virtual env due to no ability to disconnect hardware switches
""")
class SwitchFailuresSpec extends HealthCheckSpecification {

    def "ISL is still able to properly fail even if switches have reconnected"() {
        given: "A flow"
        def isl = topology.getIslsForActiveSwitches().find { it.aswitch && it.dstSwitch }
        assumeTrue("No a-switch ISL found for the test", isl.asBoolean())
        def flow = flowHelperV2.randomFlow(isl.srcSwitch, isl.dstSwitch)
        flowHelperV2.addFlow(flow)

        when: "Two neighbouring switches of the flow go down simultaneously"
        def srcBlockData = lockKeeper.knockoutSwitch(isl.srcSwitch, mgmtFlManager)
        def dstBlockData = lockKeeper.knockoutSwitch(isl.dstSwitch, mgmtFlManager)
        def timeSwitchesBroke = System.currentTimeMillis()
        def untilIslShouldFail = { timeSwitchesBroke + discoveryTimeout * 1000 - System.currentTimeMillis() }

        and: "ISL between those switches looses connection"
        lockKeeper.removeFlows([isl.aswitch])

        and: "Switches go back up"
        lockKeeper.reviveSwitch(isl.srcSwitch, srcBlockData)
        lockKeeper.reviveSwitch(isl.dstSwitch, dstBlockData)

        then: "ISL still remains up right before discovery timeout should end"
        sleep(untilIslShouldFail() - 2000)
        islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED

        and: "ISL fails after discovery timeout"
        //TODO(rtretiak): Using big timeout here. This is an abnormal behavior
        Wrappers.wait(untilIslShouldFail() / 1000 + WAIT_OFFSET * 1.5) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED
        }

        //depends whether there are alt paths available
        and: "The flow goes down OR changes path to avoid failed ISL after reroute timeout"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            def currentIsls = pathHelper.getInvolvedIsls(PathHelper.convert(northbound.getFlowPath(flow.flowId)))
            def pathChanged = !currentIsls.contains(isl) && !currentIsls.contains(isl.reversed)
            assert pathChanged || (northboundV2.getFlowStatus(flow.flowId).status == FlowState.DOWN &&
                    northbound.getFlowHistory(flow.flowId).last().histories.find { it.action == REROUTE_FAIL })
        }

        and: "Cleanup: restore connection, remove the flow"
        flowHelperV2.deleteFlow(flow.flowId)
        lockKeeper.addFlows([isl.aswitch])
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
    }

    @Ignore("Not ready yet")
    //expected to work only via v2 API
    def "System can handle situation when switch reconnects while flow is being created"() {
        when: "Start creating a flow between switches and lose connection to src before rules are set"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        northboundV2.addFlow(flow)
        sleep(50)
        def blockData = lockKeeper.knockoutSwitch(srcSwitch, mgmtFlManager)

        then: "Flow eventually goes DOWN"
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getSwitch(srcSwitch.dpId).state == SwitchChangeType.DEACTIVATED
        }
        Wrappers.wait(WAIT_OFFSET) {
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.DOWN
        }

        and: "Flow has no path associated"
        with(northbound.getFlowPath(flow.flowId)) {
            forwardPath.empty
            reversePath.empty
        }

        and: "Dst switch validation shows no missing rules"
        with(northbound.validateSwitch(dstSwitch.dpId)) {
            it.verifyRuleSectionsAreEmpty(["missing", "proper", "misconfigured"])
            it.verifyMeterSectionsAreEmpty(["missing", "misconfigured", "proper", "excess"])
        }

        when: "Try to validate flow"
        northbound.validateFlow(flow.flowId)

        then: "Error is returned, explaining that this is impossible for DOWN flows"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.UNPROCESSABLE_ENTITY
        e.responseBodyAsString.to(MessageError).errorDescription ==
                "Could not validate flow: Flow $flow.flowId is in DOWN state"

        when: "Switch returns back UP"
        lockKeeper.reviveSwitch(srcSwitch, blockData)
        Wrappers.wait(WAIT_OFFSET) { northbound.getSwitch(srcSwitch.dpId).state == SwitchChangeType.ACTIVATED }

        then: "Flow is still down, because ISLs had not enough time to fail, so no ISLs are discovered and no reroute happen"
        northboundV2.getFlowStatus(flow.flowId).status == FlowState.DOWN

        when: "Reroute the flow"
        def rerouteResponse = northboundV2.rerouteFlow(flow.flowId)

        then: "Flow is rerouted and in UP state"
        rerouteResponse.rerouted
        Wrappers.wait(WAIT_OFFSET) { northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP }

        and: "Has a path now"
        with(northbound.getFlowPath(flow.flowId)) {
            !forwardPath.empty
            !reversePath.empty
        }

        and: "Can be validated"
        northbound.validateFlow(flow.flowId).each { assert it.discrepancies.empty }

        and: "Flow can be removed"
        flowHelper.deleteFlow(flow.flowId)
    }

    @Ignore("Too unstable due to race condition when doing switch disconnect + reroute")
    def "No discrepancies when target transit switch disconnects while flow is being rerouted to it"() {
        given: "A flow with alternative paths available"
        assumeTrue("This test is only viable for h&s reroutes", northbound.getFeatureToggles().flowsRerouteViaFlowHs)
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().find { it.paths.size() > 2 } ?:
                assumeTrue("No suiting switches found", false)
        def flow = flowHelperV2.randomFlow(switchPair)
        northboundV2.addFlow(flow)
        def originalPath = PathHelper.convert(northbound.getFlowPath(flow.flowId))

        and: "There is a more preferable alternative path"
        Switch uniqueSwitch = null
        def preferredPath = switchPair.paths.find { path ->
            uniqueSwitch = pathHelper.getInvolvedSwitches(path).find {
                !pathHelper.getInvolvedSwitches(originalPath).contains(it)
            }
            uniqueSwitch && path != originalPath
        }
        assert preferredPath.asBoolean(), "Didn't find a proper alternative path"
        switchPair.paths.findAll { it != preferredPath }.each { pathHelper.makePathMorePreferable(preferredPath, it) }

        when: "Init reroute of the flow to a better path"
        def task = task {
            with(northboundV2.rerouteFlow(flow.flowId)) {
                rerouted
                path.nodes*.switchId.contains(uniqueSwitch.dpId)
            }
        }

        and: "Immediately disconnect a switch on the new path"
        def blockData = lockKeeper.knockoutSwitch(uniqueSwitch, mgmtFlManager)
        task.get()

        then: "The flow is UP and valid"
        Wrappers.wait(WAIT_OFFSET) {
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP
        }
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        and: "The flow did not actually change path and fell back to original path"
        PathHelper.convert(northbound.getFlowPath(flow.flowId)) == originalPath

        and: "Revive switch, remove the flow"
        flowHelper.deleteFlow(flow.flowId)
        lockKeeper.reviveSwitch(uniqueSwitch, blockData)
        Wrappers.wait(WAIT_OFFSET) { northbound.getSwitch(uniqueSwitch.dpId).state == SwitchChangeType.ACTIVATED }
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        Wrappers.wait(discoveryInterval) { northbound.getAllLinks().each { it.state == IslChangeType.DISCOVERED } }
    }
}
