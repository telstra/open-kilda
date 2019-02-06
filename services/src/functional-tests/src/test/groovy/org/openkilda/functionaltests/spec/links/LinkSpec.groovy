package org.openkilda.functionaltests.spec.links

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.IslInfoData
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.links.LinkParametersDto
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.transform.Memoized
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Unroll

class LinkSpec extends BaseSpecification {

    def "Get all flows (UP/DOWN) going through a particular link"() {
        given: "Two active not neighboring switches"
        def switches = topology.getActiveSwitches()
        def allLinks = northbound.getAllLinks()
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
        def internalSwitch = switches.find { it.dpId == northbound.getFlowPath(flow1.id).forwardPath[1].switchId }
        def flow3 = flowHelper.randomFlow(srcSwitch, internalSwitch)
        flow3 = flowHelper.addFlow(flow3)

        and: "Reverse flow from 'internal' switch to source switch"
        def flow4 = flowHelper.randomFlow(internalSwitch, srcSwitch)
        flow4 = flowHelper.addFlow(flow4)

        when: "Get all flows going through the link from source switch to 'internal' switch"
        def islToInternal = pathHelper.getInvolvedIsls(PathHelper.convert(northbound.getFlowPath(flow3.id))).first()
        def linkFlows = northbound.getLinkFlows(islToInternal.srcSwitch.dpId, islToInternal.srcPort,
                islToInternal.dstSwitch.dpId, islToInternal.dstPort)

        then: "All created flows are in the response list"
        [flow1, flow2, flow3, flow4].each { assert it in linkFlows }

        when: "Get all flows going through the link from some 'internal' switch to destination switch"
        def islFromInternal = pathHelper.getInvolvedIsls(PathHelper.convert(northbound.getFlowPath(flow1.id))).last()
        linkFlows = northbound.getLinkFlows(islFromInternal.srcSwitch.dpId, islFromInternal.srcPort,
                islFromInternal.dstSwitch.dpId, islFromInternal.dstPort)

        then: "Only the first and second flows are in the response list"
        [flow1, flow2].each { assert it in linkFlows }
        [flow3, flow4].each { assert !(it in linkFlows) }

        when: "Bring all ports down on source switch that are involved in current and alternative paths"
        List<List<PathNode>> possibleFlowPaths = database.getPaths(srcSwitch.dpId, dstSwitch.dpId)*.path
        List<PathNode> broughtDownPorts = []
        possibleFlowPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northbound.portDown(src.switchId, src.portNo)
        }

        then: "All flows go to 'Down' status"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            [flow1, flow2, flow3, flow4].each { assert northbound.getFlowStatus(it.id).status == FlowState.DOWN }
        }

        when: "Get all flows going through the link from source switch to 'internal' switch"
        linkFlows = northbound.getLinkFlows(islToInternal.srcSwitch.dpId, islToInternal.srcPort,
                islToInternal.dstSwitch.dpId, islToInternal.dstPort)

        then: "All created flows are in the response list"
        [flow1, flow2, flow3, flow4].each { assert it in linkFlows }

        when: "Get all flows going through the link from 'internal' switch to destination switch"
        linkFlows = northbound.getLinkFlows(islFromInternal.srcSwitch.dpId, islFromInternal.srcPort,
                islFromInternal.dstSwitch.dpId, islFromInternal.dstPort)

        then: "Only the first and second flows are in the response list"
        [flow1, flow2].each { assert it in linkFlows }
        [flow3, flow4].each { assert !(it in linkFlows) }

        when: "Bring ports up"
        broughtDownPorts.each { northbound.portUp(it.switchId, it.portNo) }

        then: "All flows go to 'Up' status"
        Wrappers.wait(rerouteDelay + discoveryInterval + WAIT_OFFSET) {
            [flow1, flow2, flow3, flow4].each { assert northbound.getFlowStatus(it.id).status == FlowState.UP }
        }

        and: "Delete all created flows"
        [flow1, flow2, flow3, flow4].each { assert northbound.deleteFlow(it.id) }
    }

    @Unroll
    def "Unable to get flows for NOT existing link (#item doesn't exist) "() {
        when: "Get flows for NOT existing link"
        northbound.getLinkFlows(srcSwId, srcSwPort, dstSwId, dstSwPort)

        then: "An error is received (404 code)"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "There is no ISL between $srcSwId-$srcSwPort and $dstSwId-$dstSwPort."

        where:
        srcSwId                 | srcSwPort        | dstSwId                 | dstSwPort        | item
        NON_EXISTENT_SWITCH_ID  | getIsl().srcPort | getIsl().dstSwitch.dpId | getIsl().dstPort | "src_switch"
        getIsl().srcSwitch.dpId | 4096             | getIsl().dstSwitch.dpId | getIsl().dstPort | "src_port"
        getIsl().srcSwitch.dpId | getIsl().srcPort | NON_EXISTENT_SWITCH_ID  | getIsl().dstPort | "dst_switch"
        getIsl().srcSwitch.dpId | getIsl().srcPort | getIsl().dstSwitch.dpId | 4096             | "dst_port"
    }

    @Unroll
    def "Unable to get flows with specifying invalid query parameters (#item is invalid) "() {
        when: "Get flows with specifying invalid #item"
        northbound.getLinkFlows(srcSwId, srcSwPort, dstSwId, dstSwPort)

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
        northbound.getLinkFlows(srcSwId, srcSwPort, dstSwId, dstSwPort)

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

    def "Unable to delete nonexistent link"() {
        given: "Parameters of nonexistent link"
        def parameters = new LinkParametersDto(new SwitchId(1).toString(), 100, new SwitchId(2).toString(), 100)

        when: "Try to delete nonexistent link"
        northbound.deleteLink(parameters)

        then: "Got 404 NotFound"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.contains("ISL was not found")
    }

    def "Unable to delete active link"() {
        given: "Parameters for active link"
        def link = northbound.getActiveLinks()[0]
        def parameters = new LinkParametersDto(link.source.switchId.toString(), link.source.portNo,
                link.destination.switchId.toString(), link.destination.portNo)

        when: "Try to delete active link"
        northbound.deleteLink(parameters)

        then: "Got 400 BadRequest because link is active"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.contains("ISL must NOT be in active state")
    }

    def "Able to delete inactive link"() {
        given: "Parameters for inactive link"
        def link = northbound.getActiveLinks()[0]
        def srcSwitch = link.source.switchId
        def srcPort = link.source.portNo
        def dstSwitch = link.destination.switchId
        def dstPort = link.destination.portNo

        northbound.portDown(srcSwitch, srcPort)
        Wrappers.wait(WAIT_OFFSET) {
            northbound.getLinksByParameters(srcSwitch, srcPort, dstSwitch, dstPort).each {
                assert it.state == IslChangeType.FAILED
            }
        }

        when: "Try to delete inactive link"
        def parameters = new LinkParametersDto(srcSwitch.toString(), srcPort, dstSwitch.toString(), dstPort)
        def res = northbound.deleteLink(parameters)

        then: "Check that link is actually deleted"
        res.deleted
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getLinksByParameters(srcSwitch, srcPort, dstSwitch, dstPort).empty
        }

        and: "Cleanup: restore link"
        northbound.portUp(srcSwitch, srcPort)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getLinksByParameters(srcSwitch, srcPort, dstSwitch, dstPort).each {
                assert it.state == IslChangeType.DISCOVERED
            }
        }
    }

    def "Reroute all flows going through a particular link"() {
        given: "Two active not neighboring switches with two possible paths at least"
        def switches = topology.getActiveSwitches()
        def allLinks = northbound.getAllLinks()
        List<List<PathNode>> possibleFlowPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            possibleFlowPaths = database.getPaths(src.dpId, dst.dpId)*.path.sort { it.size() }
            allLinks.every { link ->
                !(link.source.switchId == src.dpId && link.destination.switchId == dst.dpId)
            } && possibleFlowPaths.size() > 1
        } ?: assumeTrue("No suiting switches found", false)

        and: "Make the first path more preferable than others by setting corresponding link props"
        possibleFlowPaths[1..-1].each { pathHelper.makePathMorePreferable(possibleFlowPaths.first(), it) }

        and: "Create a couple of flows going through these switches"
        def flow1 = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow1)
        def flow1Path = PathHelper.convert(northbound.getFlowPath(flow1.id))

        def flow2 = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow2)
        def flow2Path = PathHelper.convert(northbound.getFlowPath(flow2.id))

        assert flow1Path == possibleFlowPaths.first()
        assert flow2Path == possibleFlowPaths.first()

        and: "Delete link props from all links of alternative paths to allow rerouting flows"
        northbound.deleteLinkProps(northbound.getAllLinkProps())

        and: "Make the current flows path not preferable"
        possibleFlowPaths[1..-1].each { pathHelper.makePathMorePreferable(it, possibleFlowPaths.first()) }

        when: "Submit request for rerouting flows to avoid the first link involved in flow paths"
        def isl = pathHelper.getInvolvedIsls(flow1Path).first()
        def response = northbound.rerouteLinkFlows(isl.srcSwitch.dpId, isl.srcPort, isl.dstSwitch.dpId, isl.dstPort)

        then: "Flows are rerouted"
        response.containsAll([flow1, flow2]*.id)

        def flow1PathUpdated = PathHelper.convert(northbound.getFlowPath(flow1.id))
        def flow2PathUpdated = PathHelper.convert(northbound.getFlowPath(flow2.id))

        flow1PathUpdated != flow1Path
        flow2PathUpdated != flow2Path

        and: "Requested link is not involved in new flow paths"
        !(isl in pathHelper.getInvolvedIsls(flow1PathUpdated))
        !(isl in pathHelper.getInvolvedIsls(flow2PathUpdated))

        and: "Delete flows and delete link props"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }
        northbound.deleteLinkProps(northbound.getAllLinkProps())
    }

    @Unroll
    def "Unable to reroute flows with specifying NOT existing link (#item doesn't exist) "() {
        when: "Reroute flows with specifying NOT existing link"
        northbound.rerouteLinkFlows(srcSwId, srcSwPort, dstSwId, dstSwPort)

        then: "An error is received (404 code)"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "There is no ISL between $srcSwId-$srcSwPort and $dstSwId-$dstSwPort."

        where:
        srcSwId                 | srcSwPort        | dstSwId                 | dstSwPort        | item
        NON_EXISTENT_SWITCH_ID  | getIsl().srcPort | getIsl().dstSwitch.dpId | getIsl().dstPort | "src_switch"
        getIsl().srcSwitch.dpId | 4096             | getIsl().dstSwitch.dpId | getIsl().dstPort | "src_port"
        getIsl().srcSwitch.dpId | getIsl().srcPort | NON_EXISTENT_SWITCH_ID  | getIsl().dstPort | "dst_switch"
        getIsl().srcSwitch.dpId | getIsl().srcPort | getIsl().dstSwitch.dpId | 4096             | "dst_port"
    }

    @Unroll
    def "Unable to reroute flows with specifying invalid query parameters (#item is invalid) "() {
        when: "Reroute flows with specifying invalid #item"
        northbound.rerouteLinkFlows(srcSwId, srcSwPort, dstSwId, dstSwPort)

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
    def "Unable to reroute flows without full specifying a particular link (#item is missing)"() {
        when: "Reroute flows without specifying #item"
        northbound.rerouteLinkFlows(srcSwId, srcSwPort, dstSwId, dstSwPort)

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

    @Unroll
    def "Get links with specifying query parameters"() {
        when: "Get links with specifying query parameters"
        def links = northbound.getLinks(srcSwId, srcSwPort, dstSwId, dstSwPort)

        then: "The corresponding list of links is returned"
        links.sort() == filterLinks(northbound.getAllLinks(), srcSwId, srcSwPort, dstSwId, dstSwPort).sort()

        where:
        srcSwId                 | srcSwPort        | dstSwId                 | dstSwPort
        null                    | null             | null                    | null
        getIsl().srcSwitch.dpId | null             | null                    | null
        getIsl().srcSwitch.dpId | getIsl().srcPort | null                    | null
        getIsl().srcSwitch.dpId | getIsl().srcPort | getIsl().dstSwitch.dpId | null
        getIsl().srcSwitch.dpId | getIsl().srcPort | getIsl().dstSwitch.dpId | getIsl().dstPort
    }

    @Unroll
    def "Get links with specifying NOT existing query parameters (#item doesn't exist) "() {
        when: "Get links with specifying NOT existing query parameters"
        def links = northbound.getLinks(srcSwId, srcSwPort, dstSwId, dstSwPort)

        then: "An empty list of links is returned"
        links.empty

        where:
        srcSwId                 | srcSwPort        | dstSwId                 | dstSwPort        | item
        NON_EXISTENT_SWITCH_ID  | getIsl().srcPort | getIsl().dstSwitch.dpId | getIsl().dstPort | "src_switch"
        getIsl().srcSwitch.dpId | 4096             | getIsl().dstSwitch.dpId | getIsl().dstPort | "src_port"
        getIsl().srcSwitch.dpId | getIsl().srcPort | NON_EXISTENT_SWITCH_ID  | getIsl().dstPort | "dst_switch"
        getIsl().srcSwitch.dpId | getIsl().srcPort | getIsl().dstSwitch.dpId | 4096             | "dst_port"
    }

    @Unroll
    def "Unable to get links with specifying invalid query parameters (#item is invalid) "() {
        when: "Get links with specifying invalid #item"
        northbound.getLinks(srcSwId, srcSwPort, dstSwId, dstSwPort)

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

    @Memoized
    Isl getIsl() {
        topology.islsForActiveSwitches.first()
    }

    List<IslInfoData> filterLinks(List<IslInfoData> links, SwitchId srcSwId, Integer srcSwPort, SwitchId dstSwId,
                                  Integer dstSwPort) {
        if (srcSwId) {
            links = links.findAll { it.source.switchId == srcSwId }
        }
        if (srcSwPort) {
            links = links.findAll { it.source.portNo == srcSwPort }
        }
        if (dstSwId) {
            links = links.findAll { it.destination.switchId == dstSwId }
        }
        if (dstSwPort) {
            links = links.findAll { it.destination.portNo == dstSwPort }
        }

        return links
    }
}
