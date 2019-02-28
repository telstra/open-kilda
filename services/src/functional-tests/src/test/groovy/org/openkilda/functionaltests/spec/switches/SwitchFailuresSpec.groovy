package org.openkilda.functionaltests.spec.switches

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import spock.lang.Ignore
import spock.lang.Narrative

import java.util.concurrent.TimeUnit

@Narrative("""
This spec verifies different situations when Kilda switches suddenly disconnect from the controller.
Note: For now it is only runnable on virtual env due to no ability to disconnect hardware switches
""")
class SwitchFailuresSpec extends BaseSpecification {

    def setupOnce() {
        requireProfiles("virtual")
    }

    def "ISL is still able to properly fail even after switches were reconnected"() {
        given: "A flow"
        def isl = topology.getIslsForActiveSwitches().find { it.aswitch && it.dstSwitch }
        def flow = flowHelper.randomFlow(isl.srcSwitch, isl.dstSwitch)
        flowHelper.addFlow(flow)

        when: "Two neighbouring switches of the flow go down simultaneously"
        lockKeeper.knockoutSwitch(isl.srcSwitch.dpId)
        lockKeeper.knockoutSwitch(isl.dstSwitch.dpId)
        def timeSwitchesBroke = System.currentTimeMillis()
        def untilIslShouldFail = { timeSwitchesBroke + discoveryTimeout * 1000 - System.currentTimeMillis() }

        and: "ISL between those switches looses connection"
        lockKeeper.portsDown([isl.aswitch.inPort, isl.aswitch.outPort])

        and: "Switches go back up"
        lockKeeper.reviveSwitch(isl.srcSwitch.dpId)
        lockKeeper.reviveSwitch(isl.dstSwitch.dpId)

        then: "ISL still remains up right before discovery timeout should end"
        sleep(untilIslShouldFail() - 2000)
        islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED

        and: "ISL fails after discovery timeout"
        //TODO(rtretiak): Using big timeout here. This is an abnormal behavior, currently investigating.
        Wrappers.wait(untilIslShouldFail() / 1000 + WAIT_OFFSET * 1.5) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED
        }

        //depends whether there are alt paths available
        and: "The flow goes down OR changes path to avoid failed ISL after reroute timeout"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            def currentIsls = pathHelper.getInvolvedIsls(PathHelper.convert(northbound.getFlowPath(flow.id)))
            def pathChanged = !currentIsls.contains(isl) && !currentIsls.contains(isl.reversed)
            assert pathChanged || northbound.getFlowStatus(flow.id).status == FlowState.DOWN
        }

        and: "Cleanup: restore connection, remove the flow"
        lockKeeper.portsUp([isl.aswitch.inPort, isl.aswitch.outPort])
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
    }

    @Ignore("This is a known Kilda limitation and feature is not implemented yet.")
    def "System can handle situation when switch reconnects while flow is being created"() {
        given: "Source and destination switches"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches

        when: "Start creating a flow between these switches"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        def addFlow = new Thread({ northbound.addFlow(flow) })
        addFlow.start()

        and: "One of the switches goes down without waiting for flow's UP status"
        lockKeeper.knockoutSwitch(srcSwitch.dpId)
        addFlow.join()

        and: "Goes back up in 2 seconds"
        TimeUnit.SECONDS.sleep(2)
        lockKeeper.reviveSwitch(srcSwitch.dpId)

        then: "The flow is UP and valid"
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getFlowStatus(flow.id).status == FlowState.UP
            northbound.validateFlow(flow.id).each { direction ->
                assert direction.discrepancies.findAll { it.field != "meterId" }.empty
            }
        }

        and: "Rules are valid on the knocked out switch"
        verifySwitchRules(srcSwitch.dpId)

        and: "Remove the flow"
        flowHelper.deleteFlow(flow.id)
    }

    @Ignore("This is a known Kilda limitation and feature is not implemented yet.")
    def "System can handle situation when switch reconnects while flow is being rerouted"() {
        given: "A flow with alternative paths available"
        def switches = topology.getActiveSwitches()
        List<List<PathNode>> allPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.unique { it.sort() }.find { Switch src, Switch dst ->
            allPaths = database.getPaths(src.dpId, dst.dpId)*.path
            allPaths.size() > 1
        } ?: assumeTrue("No suiting switches found", false)
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)
        def currentPath = PathHelper.convert(northbound.getFlowPath(flow.id))

        and: "There is a more preferable alternative path"
        def alternativePaths = allPaths.findAll { it != currentPath }
        def preferredPath = alternativePaths.first()
        def uniqueSwitch = pathHelper.getInvolvedSwitches(preferredPath).find {
            !pathHelper.getInvolvedSwitches(currentPath).contains(it)
        }
        assumeTrue("Didn't find a unique switch for alternative path", uniqueSwitch.asBoolean())
        allPaths.findAll { it != preferredPath }.each { pathHelper.makePathMorePreferable(preferredPath, it) }

        when: "Init reroute of the flow to a better path"
        def reroute = new Thread({ northbound.rerouteFlow(flow.id) })
        reroute.start()

        and: "Immediately disconnect a switch on the new path"
        lockKeeper.knockoutSwitch(uniqueSwitch.dpId)
        reroute.join()

        and: "Reconnect it back in a couple of seconds"
        TimeUnit.SECONDS.sleep(2)
        lockKeeper.reviveSwitch(uniqueSwitch.dpId)

        then: "The flow is UP and valid"
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getFlowStatus(flow.id).status == FlowState.UP
            northbound.validateFlow(flow.id).each { direction ->
                assert direction.discrepancies.findAll { it.field != "meterId" }.empty
            }
        }

        and: "Rules are valid on the knocked out switch"
        verifySwitchRules(uniqueSwitch.dpId)

        and: "Remove the flow and reset costs"
        flowHelper.deleteFlow(flow.id)
        northbound.deleteLinkProps(northbound.getAllLinkProps())
    }
}
