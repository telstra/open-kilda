package org.openkilda.functionaltests.spec.northbound.flows

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.extension.fixture.rule.CleanupSwitches
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import spock.lang.Narrative

@CleanupSwitches
@Narrative("Verify that on-demand reroute operations are performed accurately.")
class IntentionalRerouteSpec extends BaseSpecification {

    def "Should not be able to reroute to a path with not enough bandwidth available"() {
        given: "A flow with alternate paths available"
        def switches = topology.getActiveSwitches()
        List<List<PathNode>> allPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.unique { it.sort() }.find { Switch src, Switch dst ->
            allPaths = database.getPaths(src.dpId, dst.dpId)*.path
            allPaths.size() > 1
        } ?: assumeTrue("No suiting switches found", false)
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = 10000
        flowHelper.addFlow(flow)
        def currentPath = PathHelper.convert(northbound.getFlowPath(flow.id))

        when: "Make the current path less preferable than alternatives"
        def alternativePaths = allPaths.findAll { it != currentPath }
        alternativePaths.each { pathHelper.makePathMorePreferable(it, currentPath) }

        and: "Make all alternative paths to have not enough bandwidth to handle the flow"
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def changedIsls = alternativePaths.collect { altPath ->
            def thinIsl = pathHelper.getInvolvedIsls(altPath).find {
                !currentIsls.contains(it) && !currentIsls.contains(islUtils.reverseIsl(it))
            }
            def newBw = flow.maximumBandwidth - 1
            database.updateLinkMaxBandwidth(thinIsl, newBw)
            database.updateLinkMaxBandwidth(islUtils.reverseIsl(thinIsl), newBw)
            database.updateLinkAvailableBandwidth(thinIsl, newBw)
            database.updateLinkAvailableBandwidth(islUtils.reverseIsl(thinIsl), newBw)
            thinIsl
        }

        and: "Init a reroute to a more preferable path"
        def rerouteResponse = northbound.rerouteFlow(flow.id)

        then: "The flow is NOT rerouted because of not enough bandwidth on alternative paths"
        int seqId = 0

        !rerouteResponse.rerouted
        rerouteResponse.path.path == currentPath
        rerouteResponse.path.path.each { assert it.seqId == seqId++ }

        PathHelper.convert(northbound.getFlowPath(flow.id)) == currentPath
        northbound.getFlowStatus(flow.id).status == FlowState.UP

        and: "Remove the flow, restore the bandwidth on ISLs, reset costs"
        flowHelper.deleteFlow(flow.id)
        changedIsls.each {
            database.revertIslBandwidth(it)
            database.revertIslBandwidth(islUtils.reverseIsl(it))
        }
    }

    def "Should be able to reroute to a better path if it has enough bandwidth"() {
        given: "A flow with alternate paths available"
        def switches = topology.getActiveSwitches()
        List<List<PathNode>> allPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.unique { it.sort() }.find { Switch src, Switch dst ->
            allPaths = database.getPaths(src.dpId, dst.dpId)*.path
            allPaths.size() > 1
        } ?: assumeTrue("No suiting switches found", false)
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = 10000
        flowHelper.addFlow(flow)
        def currentPath = PathHelper.convert(northbound.getFlowPath(flow.id))

        when: "Make one of the alternative paths to be the most preferable among all others"
        def preferableAltPath = allPaths.find { it != currentPath }
        allPaths.findAll { it != preferableAltPath }.each { pathHelper.makePathMorePreferable(preferableAltPath, it) }

        and: "Make the future path to have exact bandwidth to handle the flow"
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def thinIsl = pathHelper.getInvolvedIsls(preferableAltPath).find {
            !currentIsls.contains(it) && !currentIsls.contains(islUtils.reverseIsl(it))
        }
        database.updateLinkMaxBandwidth(thinIsl, flow.maximumBandwidth)
        database.updateLinkMaxBandwidth(islUtils.reverseIsl(thinIsl), flow.maximumBandwidth)
        database.updateLinkAvailableBandwidth(thinIsl, flow.maximumBandwidth)
        database.updateLinkAvailableBandwidth(islUtils.reverseIsl(thinIsl), flow.maximumBandwidth)

        and: "Init a reroute of the flow"
        def rerouteResponse = northbound.rerouteFlow(flow.id)

        then: "The flow is successfully rerouted and goes through the preferable path"
        def newPath = PathHelper.convert(northbound.getFlowPath(flow.id))
        int seqId = 0

        rerouteResponse.rerouted
        rerouteResponse.path.path == newPath
        rerouteResponse.path.path.each { assert it.seqId == seqId++ }

        newPath == preferableAltPath
        pathHelper.getInvolvedIsls(newPath).contains(thinIsl)
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.id).status == FlowState.UP }

        and: "'Thin' ISL has 0 available bandwidth left"
        Wrappers.wait(WAIT_OFFSET) { assert islUtils.getIslInfo(thinIsl).get().availableBandwidth == 0 }

        and: "Remove the flow, restore bandwidths on ISLs, reset costs"
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.id).status == FlowState.UP }
        flowHelper.deleteFlow(flow.id)
        [thinIsl, islUtils.reverseIsl(thinIsl)].each { database.revertIslBandwidth(it) }
    }

    def cleanup() {
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        database.resetCosts()
    }
}
