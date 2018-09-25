package org.openkilda.functionaltests.spec.northbound.links

import static org.openkilda.testing.Constants.WAIT_OFFSET

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

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.HttpClientErrorException

class LinkSpec extends BaseSpecification {

    @Autowired
    TopologyDefinition topology
    @Autowired
    FlowHelper flowHelper
    @Autowired
    PathHelper pathHelper
    @Autowired
    NorthboundService northboundService
    @Autowired
    TopologyEngineService topologyEngineService

    @Value('${reroute.delay}')
    int rerouteDelay
    @Value('${discovery.interval}')
    int discoveryInterval

    def "Get all flows (UP/DOWN) going through a particular existing link"() {
        given: "Two active not neighboring switches"
        def switches = topology.getActiveSwitches()
        def allLinks = northboundService.getAllLinks()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            allLinks.every { link ->
                def switchIds = link.path*.switchId
                !(switchIds.contains(src.dpId) && switchIds.contains(dst.dpId))
            }
        }
        assert srcSwitch && dstSwitch

        and: "Forward flow from source switch to destination switch"
        def flow1 = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow1.maximumBandwidth = 1000
        flow1 = northboundService.addFlow(flow1)
        assert Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow1.id).status == FlowState.UP }

        and: "Reverse flow from destination switch to source switch"
        def flow2 = flowHelper.randomFlow(dstSwitch, srcSwitch)
        flow2.maximumBandwidth = 1000
        flow2 = northboundService.addFlow(flow2)
        assert Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow2.id).status == FlowState.UP }

        and: "Forward flow from source switch to some 'internal' switch"
        def internalSwitch = switches.find {
            it.dpId == northboundService.getFlowPath(flow1.id).forwardPath[1].switchId
        }

        def flow3 = flowHelper.randomFlow(srcSwitch, internalSwitch)
        flow3.maximumBandwidth = 1000
        flow3 = northboundService.addFlow(flow3)
        assert Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow3.id).status == FlowState.UP }

        and: "Reverse flow from 'internal' switch to source switch"
        def flow4 = flowHelper.randomFlow(internalSwitch, srcSwitch)
        flow4.maximumBandwidth = 1000
        flow4 = northboundService.addFlow(flow4)
        assert Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow4.id).status == FlowState.UP }

        when: "Get all flows going through the link from source switch to 'internal' switch"
        def islToInternal = pathHelper.getInvolvedIsls(
                PathHelper.convert(northboundService.getFlowPath(flow3.id))).first()
        def linkFlows = northboundService.getLinkFlows(islToInternal.srcSwitch.dpId, islToInternal.srcPort,
                islToInternal.dstSwitch.dpId, islToInternal.dstPort)

        then: "All created flows are in the response list"
        [flow1, flow2, flow3, flow4].every { it in linkFlows }

        when: "Get all flows going through the link from some 'internal' switch to destination switch"
        def islFromInternal = pathHelper.getInvolvedIsls(
                PathHelper.convert(northboundService.getFlowPath(flow1.id))).last()
        linkFlows = northboundService.getLinkFlows(islFromInternal.srcSwitch.dpId, islFromInternal.srcPort,
                islFromInternal.dstSwitch.dpId, islFromInternal.dstPort)

        then: "Only the first and second flows are in the response list"
        [flow1, flow2].every { it in linkFlows }
        [flow3, flow4].every { !(it in linkFlows) }

        when: "Bring all ports down on source switch that are involved in current and alternative paths"
        List<List<PathNode>> possibleFlowPaths = topologyEngineService.getPaths(srcSwitch.dpId, dstSwitch.dpId)*.path
        List<PathNode> broughtDownPorts = []
        possibleFlowPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northboundService.portDown(src.switchId, src.portNo)
        }

        then: "All flows go to 'Down' status"
        Wrappers.wait(rerouteDelay + 2) {
            [flow1, flow2, flow3, flow4].every { northboundService.getFlowStatus(it.id).status == FlowState.DOWN }
        }

        when: "Get all flows going through the link from source switch to 'internal' switch"
        linkFlows = northboundService.getLinkFlows(islToInternal.srcSwitch.dpId, islToInternal.srcPort,
                islToInternal.dstSwitch.dpId, islToInternal.dstPort)

        then: "All created flows are in the response list"
        [flow1, flow2, flow3, flow4].every { it in linkFlows }

        when: "Get all flows going through the link from 'internal' switch to destination switch"
        linkFlows = northboundService.getLinkFlows(islFromInternal.srcSwitch.dpId, islFromInternal.srcPort,
                islFromInternal.dstSwitch.dpId, islFromInternal.dstPort)

        then: "Only the first and second flows are in the response list"
        [flow1, flow2].every { it in linkFlows }
        [flow3, flow4].every { !(it in linkFlows) }

        when: "Bring ports up"
        broughtDownPorts.each { northboundService.portUp(it.switchId, it.portNo) }

        then: "All flows go to 'Up' status"
        Wrappers.wait(rerouteDelay + discoveryInterval + 5) {
            [flow1, flow2, flow3, flow4].every { northboundService.getFlowStatus(flow1.id).status == FlowState.UP }
        }

        and: "Delete all created flows"
        [flow1, flow2, flow3, flow4].every { northboundService.deleteFlow(it.id) }
    }

    def "Get flows for NOT existing link"() {
        when: "Get flows for NOT existing link"
        def isl = topology.islsForActiveSwitches.first()
        northboundService.getLinkFlows(isl.srcSwitch.dpId, 1000, isl.dstSwitch.dpId, 1001)

        then: "An error is received (404 code)"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404

    }

    def "Get flows without specifying a particular link"() {
        when: "Get flows without specifying a particular link"
        northboundService.getLinkFlows(null, null, null, null)

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
    }
}
