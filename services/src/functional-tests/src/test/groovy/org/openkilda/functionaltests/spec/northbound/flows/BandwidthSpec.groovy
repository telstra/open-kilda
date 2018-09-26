package org.openkilda.functionaltests.spec.northbound.flows

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.topology.TopologyEngineService
import org.openkilda.testing.tools.IslUtils

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException

class BandwidthSpec extends BaseSpecification {

    @Autowired
    TopologyDefinition topology
    @Autowired
    FlowHelper flowHelper
    @Autowired
    NorthboundService northboundService
    @Autowired
    TopologyEngineService topologyEngineService
    @Autowired
    PathHelper pathHelper
    @Autowired
    IslUtils islUtils
    @Autowired
    Database db

    def "Available bandwidth on ISLs changes respectively when creating/updating a flow"() {
        given: "Two active not neighboring switches"
        def switches = topology.getActiveSwitches()
        def linksBeforeFlow = northboundService.getAllLinks()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            linksBeforeFlow.every { link ->
                def switchIds = link.path*.switchId
                !(switchIds.contains(src.dpId) && switchIds.contains(dst.dpId))
            }
        }
        assert srcSwitch && dstSwitch

        when: "Create a flow with a valid bandwidth"
        def maximumBandwidth = 1000

        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = maximumBandwidth
        northboundService.addFlow(flow)

        then: "Flow is successfully created and has 'Up' status"
        Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        northboundService.getFlow(flow.id).maximumBandwidth == maximumBandwidth

        and: "Available bandwidth on ISLs is changed in accordance with flow maximum bandwidth"
        def linksAfterFlow = northboundService.getAllLinks()
        def flowPath = PathHelper.convert(northboundService.getFlowPath(flow.id))
        pathHelper.getInvolvedIsls(flowPath).every { link ->
            [link, islUtils.reverseIsl(link)].every {
                def bwBeforeFlow = islUtils.getIslInfo(linksBeforeFlow, it).get().availableBandwidth
                def bwAfterFlow = islUtils.getIslInfo(linksAfterFlow, it).get().availableBandwidth
                bwAfterFlow == bwBeforeFlow - maximumBandwidth
            }
        }

        when: "Update the flow with a valid bandwidth"
        def maximumBandwidthUpdated = 2000

        flow.maximumBandwidth = maximumBandwidthUpdated
        northboundService.updateFlow(flow.id, flow)

        then: "Flow is successfully updated and has 'Up' status"
        Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        northboundService.getFlow(flow.id).maximumBandwidth == maximumBandwidthUpdated

        and: "Available bandwidth on ISLs is changed in accordance with new flow maximum bandwidth"
        def linksBeforeFlowUpdate = linksAfterFlow
        def linksAfterFlowUpdate = northboundService.getAllLinks()
        def flowPathAfterUpdate = PathHelper.convert(northboundService.getFlowPath(flow.id))
        pathHelper.getInvolvedIsls(flowPathAfterUpdate).every { link ->
            [link, islUtils.reverseIsl(link)].every {
                def bwBeforeFlow = islUtils.getIslInfo(linksBeforeFlowUpdate, it).get().availableBandwidth
                def bwAfterFlow = islUtils.getIslInfo(linksAfterFlowUpdate, it).get().availableBandwidth
                bwAfterFlow == bwBeforeFlow + maximumBandwidth - maximumBandwidthUpdated
            }
        }

        and: "Delete created flow"
        northboundService.deleteFlow(flow.id)
    }

    def "Longer path is chosen in case of not enough available bandwidth on a shorter path"() {
        given: "Two active switches and two possible flow paths at least"
        def switches = topology.getActiveSwitches()
        List<List<PathNode>> possibleFlowPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            possibleFlowPaths = topologyEngineService.getPaths(src.dpId, dst.dpId)*.path.sort { it.size() }
            possibleFlowPaths.size() > 1
        }
        assert srcSwitch && dstSwitch

        // Make the first path more preferable than others.
        possibleFlowPaths[1..-1].each { pathHelper.makePathMorePreferable(possibleFlowPaths.first(), it) }

        // Get min available bandwidth on the preferable path.
        def involvedBandwidths = []
        def allLinks = northboundService.getAllLinks()
        pathHelper.getInvolvedIsls(possibleFlowPaths.first()).each {
            involvedBandwidths.add(islUtils.getIslInfo(allLinks, it).get().availableBandwidth)
        }
        def minAvailableBandwidth = involvedBandwidths.min()

        when: "Create a flow to reduce available bandwidth on links of the expected preferable path"
        def flow1 = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow1.maximumBandwidth = minAvailableBandwidth - 100
        northboundService.addFlow(flow1)
        def flow1Path = PathHelper.convert(northboundService.getFlowPath(flow1.id))

        then: "Flow is successfully created and really built through the expected preferable path"
        Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow1.id).status == FlowState.UP }
        flow1Path == possibleFlowPaths.first()

        when: "Create another flow. One path is shorter but available bandwidth is not enough, another path is longer"
        def flow2 = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow2.maximumBandwidth = 101
        northboundService.addFlow(flow2)

        then: "Flow is successfully created and built through longer path where available bandwidth is enough"
        Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow2.id).status == FlowState.UP }
        def flow2Path = PathHelper.convert(northboundService.getFlowPath(flow2.id))
        pathHelper.getCost(flow2Path) > pathHelper.getCost(flow1Path)

        and: "Delete created flows and link props"
        [flow1.id, flow2.id].every { northboundService.deleteFlow(it) }
    }

    def "Unable to exceed bandwidth limit on ISL when creating a flow"() {
        given: "Two active switches"
        def switches = topology.getActiveSwitches()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .find { Switch src, Switch dst -> src != dst }
        assert srcSwitch && dstSwitch

        when: "Create a flow with a bandwidth that exceeds available bandwidth on ISL"
        def possibleFlowPaths = topologyEngineService.getPaths(srcSwitch.dpId, dstSwitch.dpId)*.path
        def involvedBandwidths = []

        possibleFlowPaths.each { path ->
            pathHelper.getInvolvedIsls(path).each { link ->
                involvedBandwidths.add(islUtils.getIslInfo(link).get().availableBandwidth)
            }
        }

        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = involvedBandwidths.max() + 1
        northboundService.addFlow(flow)

        then: "Flow is not created because flow path should not be found"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
    }

    def "Unable to exceed bandwidth limit on ISL when updating a flow"() {
        given: "Two active switches"
        def switches = topology.getActiveSwitches()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .find { Switch src, Switch dst -> src != dst }
        assert srcSwitch && dstSwitch

        when: "Create a flow with a valid bandwidth"
        def maximumBandwidth = 1000

        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = maximumBandwidth
        northboundService.addFlow(flow)

        then: "Flow is successfully created and has 'Up' status"
        Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        northboundService.getFlow(flow.id).maximumBandwidth == maximumBandwidth

        when: "Update a flow with a bandwidth that exceeds available bandwidth on ISL"
        def possibleFlowPaths = topologyEngineService.getPaths(srcSwitch.dpId, dstSwitch.dpId)*.path
        List<Long> involvedBandwidths = []

        possibleFlowPaths.each { path ->
            pathHelper.getInvolvedIsls(path).each { link ->
                involvedBandwidths.add(islUtils.getIslInfo(link).get().availableBandwidth)
            }
        }

        flow.maximumBandwidth += involvedBandwidths.max() + 1
        northboundService.updateFlow(flow.id, flow)

        then: "Flow is not updated because flow path should not be found"
        def e = thrown(HttpClientErrorException)
        e.rawStatusCode == 404

        and: "Delete created flow"
        northboundService.deleteFlow(flow.id)
    }

    def "Able to exceed bandwidth limit on ISL when creating/updating a flow with ignore_bandwidth = true"() {
        given: "Two active switches"
        def switches = topology.getActiveSwitches()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .find { Switch src, Switch dst -> src != dst }
        assert srcSwitch && dstSwitch

        when: "Create a flow with a bandwidth that exceeds available bandwidth on ISL (ignore_bandwidth = true)"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = Integer.MAX_VALUE - 1
        flow.ignoreBandwidth = true
        northboundService.addFlow(flow)

        then: "Flow is successfully created and has 'Up' status"
        Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        northboundService.getFlow(flow.id).maximumBandwidth == Integer.MAX_VALUE - 1

        when: "Update the flow with a bandwidth that exceeds available bandwidth on ISL (ignore_bandwidth = true)"
        flow.maximumBandwidth = Integer.MAX_VALUE
        northboundService.updateFlow(flow.id, flow)

        then: "Flow is successfully updated and has 'Up' status"
        Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        northboundService.getFlow(flow.id).maximumBandwidth == Integer.MAX_VALUE

        and: "Delete created flow"
        northboundService.deleteFlow(flow.id)
    }

    def "Able to update bandwidth to maximum link speed without using alternate links"() {
        given: "Two active neighboring switches"
        def isl = topology.getIslsForActiveSwitches().first()
        def srcSwitch = isl.srcSwitch
        def dstSwitch = isl.dstSwitch
        assert srcSwitch && dstSwitch

        when: "Create a flow with a valid small bandwidth"
        def maximumBandwidth = 1000

        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = maximumBandwidth
        northboundService.addFlow(flow)

        then: "Flow is successfully created and has 'Up' status"
        Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        northboundService.getFlow(flow.id).maximumBandwidth == maximumBandwidth

        and: "Only one link is involved in flow path"
        def flowPath = PathHelper.convert(northboundService.getFlowPath(flow.id))
        def involvedIsls = pathHelper.getInvolvedIsls(flowPath)
        involvedIsls.size() == 1
        involvedIsls.first() == isl

        when: "Update flow bandwidth to maximum link speed"
        def linkSpeed = islUtils.getIslInfo(involvedIsls.first()).get().speed
        flow.maximumBandwidth = linkSpeed
        northboundService.updateFlow(flow.id, flow)

        then: "Flow is successfully updated and has 'Up' status"
        Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        northboundService.getFlow(flow.id).maximumBandwidth == linkSpeed

        and: "The same path is used by updated flow"
        def flowPathUpdated = PathHelper.convert(northboundService.getFlowPath(flow.id))
        flowPathUpdated == flowPath

        and: "Delete created flow"
        northboundService.deleteFlow(flow.id)
    }

    def cleanup() {
        northboundService.deleteLinkProps(northboundService.getAllLinkProps())
        db.resetCosts()
    }
}
