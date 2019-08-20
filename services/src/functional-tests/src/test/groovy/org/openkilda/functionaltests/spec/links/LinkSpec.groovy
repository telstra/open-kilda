package org.openkilda.functionaltests.spec.links

import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.IslInfoData
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v1.links.LinkParametersDto
import org.openkilda.testing.model.topology.TopologyDefinition.Isl

import groovy.transform.Memoized
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Unroll

class LinkSpec extends HealthCheckSpecification {
    @Tags(SMOKE)
    def "Link (not BFD) status is properly changed when link connectivity is broken (not port down)"() {
        given: "A link going through a-switch"
        def isl = topology.islsForActiveSwitches.find {
            it.aswitch?.inPort && it.aswitch?.outPort
        } ?: assumeTrue("Wasn't able to find suitable link", false)

        double interval = discoveryTimeout * 0.2
        double waitTime = discoveryTimeout - interval

        when: "Remove a one-way flow on an a-switch for simulating lost connection(not port down)"
        lockKeeper.removeFlows([isl.aswitch])

        then: "Status of the link is not changed to FAILED until discoveryTimeout is exceeded"
        Wrappers.timedLoop(waitTime) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == IslChangeType.DISCOVERED
            sleep((interval * 1000).toLong())
        }

        and: "Status of the link is changed to FAILED, actual status remains DISCOVERED for the alive direction"
        /**
         * actualState shows real state of ISL and this value is taken from DB
         * also it allows to understand direction where issue has appeared
         * e.g. in our case we've removed a one-way flow(A->B)
         * the other one(B->A) still exists
         * afterward the actualState of ISL on A side is equal to FAILED
         * and on B side is equal to DISCOVERED
         * */
        Wrappers.wait(WAIT_OFFSET + interval) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, isl).get().actualState == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().actualState == IslChangeType.DISCOVERED
        }

        when: "Fail the other part of ISL"
        lockKeeper.removeFlows([isl.aswitch.reversed])

        then: "Status remains FAILED and actual status is changed to failed for both directions"
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, isl).get().actualState == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().actualState == IslChangeType.FAILED
        }

        when: "Add the removed flow rules for one direction"
        lockKeeper.addFlows([isl.aswitch])

        then: "The link remains FAILED, but actual status for one direction is DISCOVERED"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, isl).get().actualState == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().actualState == IslChangeType.FAILED
        }

        when: "Add the remaining missing rules on a-switch"
        lockKeeper.addFlows([isl.aswitch.reversed])

        then: "Link status and actual status both changed to DISCOVERED in both directions"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, isl).get().actualState == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().actualState == IslChangeType.DISCOVERED
        }
    }

    @Tags(SMOKE)
    def "Get all flows (UP/DOWN) going through a particular link"() {
        given: "Two active not neighboring switches"
        def switchPair = topologyHelper.getNotNeighboringSwitchPair()

        and: "Forward flow from source switch to destination switch"
        def flow1 = flowHelper.randomFlow(switchPair)
        flow1 = flowHelper.addFlow(flow1)

        and: "Reverse flow from destination switch to source switch"
        def flow2 = flowHelper.randomFlow(switchPair)
        flow2 = flowHelper.addFlow(flow2)

        and: "Forward flow from source switch to some 'internal' switch"
        def islToInternal = pathHelper.getInvolvedIsls(PathHelper.convert(northbound.getFlowPath(flow1.id))).first()
        def flow3 = flowHelper.randomFlow(islToInternal.srcSwitch, islToInternal.dstSwitch)
        flow3 = flowHelper.addFlow(flow3)

        and: "Reverse flow from 'internal' switch to source switch"
        def flow4 = flowHelper.randomFlow(islToInternal.dstSwitch, islToInternal.srcSwitch)
        flow4 = flowHelper.addFlow(flow4)

        when: "Get all flows going through the link from source switch to 'internal' switch"
        def linkFlows = northbound.getLinkFlows(islToInternal.srcSwitch.dpId, islToInternal.srcPort,
                islToInternal.dstSwitch.dpId, islToInternal.dstPort)

        then: "All created flows are in the response list"
        [flow1, flow2, flow3, flow4].each { assert it.id in linkFlows*.id }

        when: "Get all flows going through the link from some 'internal' switch to destination switch"
        def islFromInternal = pathHelper.getInvolvedIsls(PathHelper.convert(northbound.getFlowPath(flow1.id))).last()
        linkFlows = northbound.getLinkFlows(islFromInternal.srcSwitch.dpId, islFromInternal.srcPort,
                islFromInternal.dstSwitch.dpId, islFromInternal.dstPort)

        then: "Only the first and second flows are in the response list"
        [flow1, flow2].each { assert it.id in linkFlows*.id }
        [flow3, flow4].each { assert !(it.id in linkFlows*.id) }

        when: "Bring all ports down on source switch that are involved in current and alternative paths"
        List<PathNode> broughtDownPorts = []
        switchPair.paths.unique { it.first() }.each { path ->
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
        [flow1, flow2, flow3, flow4].each { assert it.id in linkFlows*.id }

        when: "Get all flows going through the link from 'internal' switch to destination switch"
        linkFlows = northbound.getLinkFlows(islFromInternal.srcSwitch.dpId, islFromInternal.srcPort,
                islFromInternal.dstSwitch.dpId, islFromInternal.dstPort)

        then: "Only the first and second flows are in the response list"
        [flow1, flow2].each { assert it.id in linkFlows*.id }
        [flow3, flow4].each { assert !(it.id in linkFlows*.id) }

        when: "Bring ports up"
        broughtDownPorts.each { northbound.portUp(it.switchId, it.portNo) }

        then: "All flows go to 'Up' status"
        Wrappers.wait(rerouteDelay + discoveryInterval + PATH_INSTALLATION_TIME) {
            [flow1, flow2, flow3, flow4].each { assert northbound.getFlowStatus(it.id).status == FlowState.UP }
        }

        and: "Delete all created flows and reset costs"
        [flow1, flow2, flow3, flow4].each { flowHelper.deleteFlow(it.id) }
        database.resetCosts()
    }

    @Tags(VIRTUAL)
    def "ISL should immediately fail if the port went down while switch was disconnected"() {
        when: "A switch disconnects"
        def isl = topology.islsForActiveSwitches.find { it.aswitch?.inPort && it.aswitch?.outPort }
        lockKeeper.knockoutSwitch(isl.srcSwitch)

        and: "One of its ports goes down"
        //Bring down port on a-switch, which will lead to a port down on the Kilda switch
        lockKeeper.portsDown([isl.aswitch.inPort])

        and: "The switch reconnects back with a port being down"
        lockKeeper.reviveSwitch(isl.srcSwitch)

        then: "The related ISL immediately goes down"
        Wrappers.wait(WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == IslChangeType.FAILED
        }

        when: "The switch disconnects again"
        lockKeeper.knockoutSwitch(isl.srcSwitch)

        and: "The DOWN port is brought back to UP state"
        lockKeeper.portsUp([isl.aswitch.inPort])

        and: "The switch reconnects back with a port being up"
        lockKeeper.reviveSwitch(isl.srcSwitch)

        then: "The related ISL is discovered again"
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == IslChangeType.DISCOVERED
        }
    }

    @Unroll
    def "Unable to get flows for NOT existing link (#item doesn't exist)"() {
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
    def "Unable to get flows with specifying invalid query parameters (#item is invalid)"() {
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

    def "Unable to delete a nonexistent link"() {
        given: "Parameters of nonexistent link"
        def parameters = new LinkParametersDto(new SwitchId(1).toString(), 100, new SwitchId(2).toString(), 100)

        when: "Try to delete nonexistent link"
        northbound.deleteLink(parameters)

        then: "Get 404 NotFound error"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.contains("ISL was not found")
    }

    def "Unable to delete an active link"() {
        given: "An active link"
        def isl = topology.getIslsForActiveSwitches()[0]

        when: "Try to delete the link"
        northbound.deleteLink(islUtils.toLinkParameters(isl))

        then: "Get 400 BadRequest error because the link is active"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.contains("ISL must NOT be in active state")
    }

    @Unroll
    def "Able to delete an inactive #islDescription link and re-discover it back afterwards"() {
        given: "An inactive link"
        assumeTrue("Unable to locate $islDescription ISL for this test", isl as boolean)
        northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(WAIT_OFFSET) { assert islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED }

        when: "Try to delete the link"
        def response = northbound.deleteLink(islUtils.toLinkParameters(isl))

        then: "The link is actually deleted"
        response.size() == 2
        !islUtils.getIslInfo(isl)
        !islUtils.getIslInfo(isl.reversed)

        when: "Removed link becomes active again (port brought UP)"
        northbound.portUp(isl.srcSwitch.dpId, isl.srcPort)

        then: "The link is rediscovered in both directions"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl.reversed).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.DISCOVERED
        }
        database.resetCosts()

        where:
        [islDescription, isl] << [
                ["direct", getTopology().islsForActiveSwitches.find { !it.aswitch }],
                ["a-switch", getTopology().islsForActiveSwitches.find {
                    it.aswitch?.inPort && it.aswitch?.outPort
                }]
        ]
    }

    def "Reroute all flows going through a particular link"() {
        given: "Two active not neighboring switches with two possible paths at least"
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue("No suiting switches found", false)

        and: "Make the first path more preferable than others by setting corresponding link props"
        switchPair.paths[1..-1].each { pathHelper.makePathMorePreferable(switchPair.paths.first(), it) }

        and: "Create a couple of flows going through these switches"
        def flow1 = flowHelper.randomFlow(switchPair)
        flowHelper.addFlow(flow1)
        def flow1Path = PathHelper.convert(northbound.getFlowPath(flow1.id))

        def flow2 = flowHelper.randomFlow(switchPair)
        flowHelper.addFlow(flow2)
        def flow2Path = PathHelper.convert(northbound.getFlowPath(flow2.id))

        assert flow1Path == switchPair.paths.first()
        assert flow2Path == switchPair.paths.first()

        and: "Delete link props from all links of alternative paths to allow rerouting flows"
        northbound.deleteLinkProps(northbound.getAllLinkProps())

        and: "Make the current flows path not preferable"
        switchPair.paths[1..-1].each { pathHelper.makePathMorePreferable(it, switchPair.paths.first()) }

        when: "Submit request for rerouting flows"
        def isl = pathHelper.getInvolvedIsls(flow1Path).first()
        def response = northbound.rerouteLinkFlows(isl.srcSwitch.dpId, isl.srcPort, isl.dstSwitch.dpId, isl.dstPort)

        then: "Flows are rerouted"
        response.containsAll([flow1, flow2]*.id)
        Wrappers.wait(PATH_INSTALLATION_TIME) {
            [flow1, flow2].each { assert northbound.getFlowStatus(it.id).status == FlowState.UP }
            assert PathHelper.convert(northbound.getFlowPath(flow1.id)) != flow1Path
            assert PathHelper.convert(northbound.getFlowPath(flow2.id)) != flow2Path
        }

        and: "Delete flows and delete link props"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }
        northbound.deleteLinkProps(northbound.getAllLinkProps())
    }

    @Unroll
    def "Unable to reroute flows with specifying NOT existing link (#item doesn't exist)"() {
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
    def "Unable to reroute flows with specifying invalid query parameters (#item is invalid)"() {
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
    def "Get links with specifying NOT existing query parameters (#item doesn't exist)"() {
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
    def "Unable to get links with specifying invalid query parameters (#item is invalid)"() {
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

    @Tags([VIRTUAL, SMOKE])
    def "ISL is able to properly fail when both src and dst switches suddenly disconnect"() {
        given: "An active ISL"
        def isl = topology.islsForActiveSwitches.first()

        when: "Source and destination switches of the ISL suddenly disconnect"
        lockKeeper.knockoutSwitch(isl.srcSwitch)
        lockKeeper.knockoutSwitch(isl.dstSwitch)

        then: "ISL gets failed after discovery timeout"
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == IslChangeType.FAILED
        }

        and: "Restore broken switches and revive ISL"
        lockKeeper.reviveSwitch(isl.srcSwitch)
        lockKeeper.reviveSwitch(isl.dstSwitch)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getActiveSwitches()*.switchId.containsAll([isl.srcSwitch.dpId, isl.dstSwitch.dpId])
            northbound.getAllLinks().each {
                assert it.state == IslChangeType.DISCOVERED
            }
        }
    }

    @Tags(SMOKE)
    def "Able to update max bandwidth for a link"() {
        given: "An active ISL"
        // Find such an ISL that is the only ISL between switches.
        def isl = topology.islsForActiveSwitches.find { isl ->
            topology.islsForActiveSwitches.findAll {
                isl.srcSwitch == it.srcSwitch && isl.dstSwitch == it.dstSwitch
            }.size() == 1
        }
        def islInfo = islUtils.getIslInfo(isl).get()
        def initialMaxBandwidth = islInfo.maxBandwidth
        def initialAvailableBandwidth = islInfo.availableBandwidth

        when: "Create a flow going through this ISL"
        def flow = flowHelper.randomFlow(isl.srcSwitch, isl.dstSwitch)
        def flowMaxBandwidth = 12345
        flow.maximumBandwidth = flowMaxBandwidth
        flowHelper.addFlow(flow)

        and: "Update max bandwidth for the link"
        def offset = 10000
        def newMaxBandwidth = initialMaxBandwidth - offset
        northbound.updateLinkMaxBandwidth(isl.srcSwitch.dpId, isl.srcPort, isl.dstSwitch.dpId, isl.dstPort,
                newMaxBandwidth)
        def links = northbound.getActiveLinks()
        def linkProps = northbound.getAllLinkProps()

        then: "Max bandwidth is really updated and available bandwidth is also recalculated"
        [isl, isl.reversed].each {
            assert islUtils.getIslInfo(links, it).get().defaultMaxBandwidth == initialMaxBandwidth
            assert islUtils.getIslInfo(links, it).get().maxBandwidth == newMaxBandwidth
            assert islUtils.getIslInfo(links, it).get().availableBandwidth ==
                    initialAvailableBandwidth - offset - flowMaxBandwidth
        }

        and: "The corresponding link props are created"
        assert linkProps.size() == 2
        linkProps.each { assert it.props["max_bandwidth"].toLong() == newMaxBandwidth }

        when: "Update max bandwidth to a value lesser than max bandwidth of the created flow"
        northbound.updateLinkMaxBandwidth(isl.srcSwitch.dpId, isl.srcPort, isl.dstSwitch.dpId, isl.dstPort,
                flowMaxBandwidth - 1)

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage == "Requested maximum bandwidth is too small"

        when: "Update max bandwidth to the value equal to max bandwidth of the created flow"
        northbound.updateLinkMaxBandwidth(isl.srcSwitch.dpId, isl.srcPort, isl.dstSwitch.dpId, isl.dstPort,
                flowMaxBandwidth)
        links = northbound.getActiveLinks()
        linkProps = northbound.getAllLinkProps()

        then: "Max bandwidth is really updated and available bandwidth is also recalculated"
        [isl, isl.reversed].each {
            assert islUtils.getIslInfo(links, it).get().maxBandwidth == flowMaxBandwidth
            assert islUtils.getIslInfo(links, it).get().availableBandwidth == 0
        }

        and: "Link props are also updated"
        assert linkProps.size() == 2
        linkProps.each { assert it.props["max_bandwidth"].toLong() == flowMaxBandwidth }

        when: "Update max bandwidth to the initial value"
        northbound.updateLinkMaxBandwidth(isl.srcSwitch.dpId, isl.srcPort, isl.dstSwitch.dpId, isl.dstPort,
                initialMaxBandwidth)
        links = northbound.getActiveLinks()
        linkProps = northbound.getAllLinkProps()

        then: "Max bandwidth is really updated and available bandwidth is also recalculated"
        [isl, isl.reversed].each {
            assert islUtils.getIslInfo(links, it).get().maxBandwidth == initialMaxBandwidth
            assert islUtils.getIslInfo(links, it).get().availableBandwidth ==
                    initialAvailableBandwidth - flowMaxBandwidth
        }

        and: "Link props are also updated"
        assert linkProps.size() == 2
        linkProps.each { assert it.props["max_bandwidth"].toLong() == initialMaxBandwidth }

        when: "Delete link props"
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        links = northbound.getActiveLinks()

        then: "Max bandwidth and available bandwidth are not changed"
        [isl, isl.reversed].each {
            assert islUtils.getIslInfo(links, it).get().maxBandwidth == initialMaxBandwidth
            assert islUtils.getIslInfo(links, it).get().availableBandwidth ==
                    initialAvailableBandwidth - flowMaxBandwidth
        }

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)
    }

    @Unroll
    def "Unable to update max bandwidth with specifying invalid query parameters (#item is invalid)"() {
        when: "Update max bandwidth with specifying invalid #item"
        northbound.updateLinkMaxBandwidth(srcSwId, srcSwPort, dstSwId, dstSwPort, 1000000)

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage.matches("Invalid value of (source|destination) port")

        where:
        srcSwId                 | srcSwPort        | dstSwId                 | dstSwPort        | item
        getIsl().srcSwitch.dpId | -1               | getIsl().dstSwitch.dpId | getIsl().dstPort | "src_port"
        getIsl().srcSwitch.dpId | getIsl().srcPort | getIsl().dstSwitch.dpId | -2               | "dst_port"
        getIsl().srcSwitch.dpId | -3               | getIsl().dstSwitch.dpId | -4               | "src_port & dst_port"
    }

    @Unroll
    def "Unable to update max bandwidth without full specifying a particular link (#item is missing)"() {
        when: "Update max bandwidth without specifying #item"
        northbound.updateLinkMaxBandwidth(srcSwId, srcSwPort, dstSwId, dstSwPort, 1000000)

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
