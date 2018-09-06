package org.openkilda.functionaltests.spec.northbound.flows

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
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

    def "Available bandwidth on ISLs changes respectively when creating a flow"() {
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

        then: "Flow is really created and has 'Up' status"
        Wrappers.wait(5) {
            northboundService.getFlowStatus(flow.id).status == FlowState.UP
        }

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

        cleanup: "Delete created flow"
        flow?.id && northboundService.deleteFlow(flow.id)
    }

    def "Verify longer path is chosen in case of not enough available bandwidth on shorter path"() {
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

        // Create a flow to reduce available bandwidth on links of the preferable path.
        def flow1 = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow1.maximumBandwidth = minAvailableBandwidth - 100
        northboundService.addFlow(flow1)
        def flow1Path = PathHelper.convert(northboundService.getFlowPath(flow1.id))
        // Make sure that path of created flow is equal to the preferable path.
        assert flow1Path == possibleFlowPaths.first()

        when: "Create a flow. One path is shorter but available bandwidth is not enough, another path is longer"
        def flow2 = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow2.maximumBandwidth = 101
        northboundService.addFlow(flow2)

        then: "Flow is built through longer path"
        def flow2Path = PathHelper.convert(northboundService.getFlowPath(flow2.id))
        pathHelper.getCost(flow2Path) > pathHelper.getCost(flow1Path)

        cleanup: "Delete created flow and link props"
        flow1?.id && northboundService.deleteFlow(flow1.id)
        flow2?.id && northboundService.deleteFlow(flow2.id)
        northboundService.deleteLinkProps(northboundService.getAllLinkProps())
    }

    def "Unable to exceed bandwidth limit on ISL when creating a flow"() {
        given: "Two active switches"
        def switches = topology.getActiveSwitches()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .find { Switch src, Switch dst -> src != dst }
        assert srcSwitch && dstSwitch

        when: "Create a flow with a bandwidth that exceeds available bandwidth on ISL"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        def possibleFlowPaths = topologyEngineService.getPaths(srcSwitch.dpId, dstSwitch.dpId)*.path
        def allLinks = northboundService.getAllLinks()
        def involvedBandwidths = []

        possibleFlowPaths.each { path ->
            pathHelper.getInvolvedIsls(path).each { link ->
                involvedBandwidths.add(islUtils.getIslInfo(allLinks, link).get().availableBandwidth)
            }
        }
        flow.maximumBandwidth = involvedBandwidths.max() + 1
        flow.ignoreBandwidth = false
        northboundService.addFlow(flow)

        then: "Flow is not created because flow path should not be found"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
    }

    def "Able to exceed bandwidth limit on ISL when creating a flow with ignore_bandwidth = true"() {
        given: "Two active switches"
        def switches = topology.getActiveSwitches()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .find { Switch src, Switch dst -> src != dst }
        assert srcSwitch && dstSwitch

        when: "Create a flow with a bandwidth that exceeds available bandwidth on ISL (ignore_bandwidth = true)"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = Integer.MAX_VALUE
        flow.ignoreBandwidth = true
        northboundService.addFlow(flow)

        then: "Flow is successfully created"
        Wrappers.wait(5) {
            northboundService.getFlowStatus(flow.id).status == FlowState.UP
        }

        cleanup: "Delete created flow"
        flow?.id && northboundService.deleteFlow(flow.id)
    }
}
