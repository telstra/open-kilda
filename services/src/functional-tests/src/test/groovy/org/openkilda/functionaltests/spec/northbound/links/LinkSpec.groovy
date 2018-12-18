package org.openkilda.functionaltests.spec.northbound.links

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService

import groovy.transform.Memoized
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Unroll

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
    Database db

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
                !(link.source.switchId == src.dpId && link.destination.switchId == dst.dpId)
            }
        }

        and: "Forward flow from source switch to destination switch"
        def flow1 = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow1 = flowHelper.addFlow(flow1)

        and: "Reverse flow from destination switch to source switch"
        def flow2 = flowHelper.randomFlow(dstSwitch, srcSwitch)
        flow2 = flowHelper.addFlow(flow2)

        and: "Forward flow from source switch to some 'internal' switch"
        def internalSwitch = switches.find {
            it.dpId == northboundService.getFlowPath(flow1.id).forwardPath[1].switchId
        }
        def flow3 = flowHelper.randomFlow(srcSwitch, internalSwitch)
        flow3 = flowHelper.addFlow(flow3)

        and: "Reverse flow from 'internal' switch to source switch"
        def flow4 = flowHelper.randomFlow(internalSwitch, srcSwitch)
        flow4 = flowHelper.addFlow(flow4)

        when: "Get all flows going through the link from source switch to 'internal' switch"
        def islToInternal = pathHelper.getInvolvedIsls(
                PathHelper.convert(northboundService.getFlowPath(flow3.id))).first()
        def linkFlows = northboundService.getLinkFlows(islToInternal.srcSwitch.dpId, islToInternal.srcPort,
                islToInternal.dstSwitch.dpId, islToInternal.dstPort)

        then: "All created flows are in the response list"
        [flow1, flow2, flow3, flow4].each { assert it in linkFlows }

        when: "Get all flows going through the link from some 'internal' switch to destination switch"
        def islFromInternal = pathHelper.getInvolvedIsls(
                PathHelper.convert(northboundService.getFlowPath(flow1.id))).last()
        linkFlows = northboundService.getLinkFlows(islFromInternal.srcSwitch.dpId, islFromInternal.srcPort,
                islFromInternal.dstSwitch.dpId, islFromInternal.dstPort)

        then: "Only the first and second flows are in the response list"
        [flow1, flow2].each { assert it in linkFlows }
        [flow3, flow4].each { assert !(it in linkFlows) }

        when: "Bring all ports down on source switch that are involved in current and alternative paths"
        List<List<PathNode>> possibleFlowPaths = db.getPaths(srcSwitch.dpId, dstSwitch.dpId)*.path
        List<PathNode> broughtDownPorts = []
        possibleFlowPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northboundService.portDown(src.switchId, src.portNo)
        }

        then: "All flows go to 'Down' status"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            [flow1, flow2, flow3, flow4].each { assert northboundService.getFlowStatus(it.id).status == FlowState.DOWN }
        }

        when: "Get all flows going through the link from source switch to 'internal' switch"
        linkFlows = northboundService.getLinkFlows(islToInternal.srcSwitch.dpId, islToInternal.srcPort,
                islToInternal.dstSwitch.dpId, islToInternal.dstPort)

        then: "All created flows are in the response list"
        [flow1, flow2, flow3, flow4].each { assert it in linkFlows }

        when: "Get all flows going through the link from 'internal' switch to destination switch"
        linkFlows = northboundService.getLinkFlows(islFromInternal.srcSwitch.dpId, islFromInternal.srcPort,
                islFromInternal.dstSwitch.dpId, islFromInternal.dstPort)

        then: "Only the first and second flows are in the response list"
        [flow1, flow2].each { assert it in linkFlows }
        [flow3, flow4].each { assert !(it in linkFlows) }

        when: "Bring ports up"
        broughtDownPorts.each { northboundService.portUp(it.switchId, it.portNo) }

        then: "All flows go to 'Up' status"
        Wrappers.wait(rerouteDelay + discoveryInterval + WAIT_OFFSET) {
            [flow1, flow2, flow3, flow4].each {
                assert northboundService.getFlowStatus(it.id).status == FlowState.UP
            }
        }

        and: "Delete all created flows"
        [flow1, flow2, flow3, flow4].each { assert northboundService.deleteFlow(it.id) }
    }

    @Unroll
    def "Unable to get flows for NOT existing link (#item doesn't exist) "() {
        when: "Get flows for NOT existing link"
        northboundService.getLinkFlows(srcSwId, srcSwPort, dstSwId, dstSwPort)

        then: "An error is received (404 code)"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "There is no ISL between $srcSwId-$srcSwPort and $dstSwId-$dstSwPort."

        where:
        srcSwId                   | srcSwPort        | dstSwId                   | dstSwPort        | item
        new SwitchId("123456789") | getIsl().srcPort | getIsl().dstSwitch.dpId   | getIsl().dstPort | "src_switch"
        getIsl().srcSwitch.dpId   | 4096             | getIsl().dstSwitch.dpId   | getIsl().dstPort | "src_port"
        getIsl().srcSwitch.dpId   | getIsl().srcPort | new SwitchId("987654321") | getIsl().dstPort | "dst_switch"
        getIsl().srcSwitch.dpId   | getIsl().srcPort | getIsl().dstSwitch.dpId   | 4096             | "dst_port"

    }

    @Unroll
    def "Unable to get flows with invalid query parameters (#item is invalid) "() {
        when: "Get flows with invalid #item"
        northboundService.getLinkFlows(srcSwId, srcSwPort, dstSwId, dstSwPort)

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage.contains("Invalid portId:")

        where:
        srcSwId                 | srcSwPort        | dstSwId                 | dstSwPort        | item
        getIsl().srcSwitch.dpId | -1               | getIsl().dstSwitch.dpId | getIsl().dstPort | "src_port"
        getIsl().srcSwitch.dpId | getIsl().srcPort | getIsl().dstSwitch.dpId | -2               | "dst_port"
        getIsl().srcSwitch.dpId | -3               | getIsl().dstSwitch.dpId | -4               | "src_port & dst_port"

    }

    @Unroll
    def "Unable to get flows without full specifying a particular link (#item is missing)"() {
        when: "Get flows without specifying #item"
        northboundService.getLinkFlows(srcSwId, srcSwPort, dstSwId, dstSwPort)

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage.contains("parameter '$item' is not present")

        where:
        srcSwId                 | srcSwPort        | dstSwId                 | dstSwPort | item
        null                    | null             | null                    | null      | "src_switch"
        getIsl().srcSwitch.dpId | null             | null                    | null      | "src_port"
        getIsl().srcSwitch.dpId | getIsl().srcPort | null                    | null      | "dst_switch"
        getIsl().srcSwitch.dpId | getIsl().srcPort | getIsl().dstSwitch.dpId | null      | "dst_port"
    }

    @Memoized
    Isl getIsl() {
        topology.islsForActiveSwitches.first()
    }
}
