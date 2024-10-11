package org.openkilda.functionaltests.spec.links

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOCKKEEPER
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.InvalidRequestParametersExpectedError
import org.openkilda.functionaltests.error.MissingServletRequestParameterException
import org.openkilda.functionaltests.error.UnableToParseRequestArgumentsException
import org.openkilda.functionaltests.error.link.LinkIsInIllegalStateExpectedError
import org.openkilda.functionaltests.error.link.LinkNotFoundExpectedError
import org.openkilda.functionaltests.error.link.LinkPropertiesNotUpdatedExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslInfoData
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v1.links.LinkParametersDto
import org.openkilda.testing.model.topology.TopologyDefinition.Isl

import org.springframework.beans.factory.annotation.Autowired

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.HttpClientErrorException
import spock.lang.See
import spock.lang.Shared

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/network-discovery")

class LinkSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    FlowFactory flowFactory

    @Value('${antiflap.cooldown}')
    int antiflapCooldown

    def setupSpec() {
        deleteAnyFlowsLeftoversIssue5480()
    }

    @Tags([SMOKE_SWITCHES, SMOKE, LOCKKEEPER])
    def "Link (not BFD) status is properly changed when link connectivity is broken (not port down)"() {
        given: "A link going through a-switch"
        def isl = topology.islsForActiveSwitches.find {
            it.aswitch?.inPort && it.aswitch?.outPort
        } ?: assumeTrue(false, "Wasn't able to find suitable link")

        double interval = discoveryTimeout * 0.2
        double waitTime = discoveryTimeout - interval

        when: "Remove a one-way flow on an a-switch for simulating lost connection(not port down)"
        aSwitchFlows.removeFlows([isl.aswitch])

        then: "Status of the link is not changed to FAILED until discoveryTimeout is exceeded"
        Wrappers.timedLoop(waitTime) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == DISCOVERED
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
            assert islUtils.getIslInfo(links, isl).get().state == FAILED
            assert islUtils.getIslInfo(links, isl).get().actualState == FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().actualState == DISCOVERED
        }

        when: "Fail the other part of ISL"
        aSwitchFlows.removeFlows([isl.aswitch.reversed])

        then: "Status remains FAILED and actual status is changed to failed for both directions"
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == FAILED
            assert islUtils.getIslInfo(links, isl).get().actualState == FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().actualState == FAILED
        }

        when: "Add the removed flow rules for one direction"
        aSwitchFlows.addFlows([isl.aswitch])

        then: "The link remains FAILED, but actual status for one direction is DISCOVERED"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == FAILED
            assert islUtils.getIslInfo(links, isl).get().actualState == DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().actualState == FAILED
        }

        when: "Add the remaining missing rules on a-switch"
        aSwitchFlows.addFlows([isl.aswitch.reversed])

        then: "Link status and actual status both changed to DISCOVERED in both directions"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == DISCOVERED
            assert islUtils.getIslInfo(links, isl).get().actualState == DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().actualState == DISCOVERED
        }
    }

    @Tags([SMOKE, ISL_RECOVER_ON_FAIL])
    def "Get all flows (UP/DOWN) going through a particular link"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all().nonNeighbouring().random()

        and: "Forward flow from source switch to destination switch"
        def flow1 = flowFactory.getBuilder(switchPair).withPinned(true).build().create()
        List<SwitchPortVlan> busyEndpoints = flow1.occupiedEndpoints()

        and: "Reverse flow from destination switch to source switch"
        def flow2 = flowFactory.getBuilder(switchPair, false, busyEndpoints)
                .withPinned(true).build()
                .create()
        busyEndpoints.addAll(flow2.occupiedEndpoints())

        and: "Forward flow from source switch to some 'internal' switch"
        def islToInternal = flow1.retrieveAllEntityPaths().getInvolvedIsls().first()
        def flow3 = flowFactory.getBuilder(islToInternal.srcSwitch, islToInternal.dstSwitch, false, busyEndpoints)
                .withPinned(true).build()
                .create()
        busyEndpoints.addAll(flow3.occupiedEndpoints())

        and: "Reverse flow from 'internal' switch to source switch"
        def flow4 = flowFactory.getBuilder(islToInternal.dstSwitch, islToInternal.srcSwitch, false, busyEndpoints)
                .withPinned(true).build()
                .create()

        when: "Get all flows going through the link from source switch to 'internal' switch"
        def linkFlows = northbound.getLinkFlows(islToInternal.srcSwitch.dpId, islToInternal.srcPort,
                islToInternal.dstSwitch.dpId, islToInternal.dstPort)

        then: "All created flows are in the response list"
        [flow1, flow2, flow3, flow4].each { assert it.flowId in linkFlows*.id }

        when: "Get all flows going through the link from some 'internal' switch to destination switch"
        def islFromInternal = flow1.retrieveAllEntityPaths().getInvolvedIsls().last()
        linkFlows = northbound.getLinkFlows(islFromInternal.srcSwitch.dpId, islFromInternal.srcPort,
                islFromInternal.dstSwitch.dpId, islFromInternal.dstPort)

        then: "Only the first and second flows are in the response list"
        [flow1, flow2].each { assert it.flowId in linkFlows*.id }
        [flow3, flow4].each { assert !(it.flowId in linkFlows*.id) }

        when: "Bring all ports down on source switch that are involved in current and alternative paths"
        def allSourceSwithIsls = topology.getRelatedIsls(switchPair.src)
        islHelper.breakIsls(allSourceSwithIsls)

        then: "All flows go to 'Down' status"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            [flow1, flow2, flow3, flow4].each { flow ->
                assert flow.retrieveFlowStatus().status == FlowState.DOWN
                def isls = flow.retrieveAllEntityPaths().getInvolvedIsls()
                assert isls.contains(islToInternal) || isls.contains(islToInternal.reversed)
            }

        }

        when: "Get all flows going through the link from source switch to 'internal' switch"
        linkFlows = northbound.getLinkFlows(islToInternal.srcSwitch.dpId, islToInternal.srcPort,
                islToInternal.dstSwitch.dpId, islToInternal.dstPort)

        then: "All created flows are in the response list"
        [flow1, flow2, flow3, flow4].each { assert it.flowId in linkFlows*.id }

        when: "Get all flows going through the link from 'internal' switch to destination switch"
        linkFlows = northbound.getLinkFlows(islFromInternal.srcSwitch.dpId, islFromInternal.srcPort,
                islFromInternal.dstSwitch.dpId, islFromInternal.dstPort)

        then: "Only the first and second flows are in the response list"
        [flow1, flow2].each { assert it.flowId in linkFlows*.id }
        [flow3, flow4].each { assert !(it.flowId in linkFlows*.id) }

        when: "Bring ports up"
        islHelper.restoreIsls(allSourceSwithIsls)

        then: "All flows go to 'Up' status"
        Wrappers.wait(rerouteDelay + PATH_INSTALLATION_TIME) {
            [flow1, flow2, flow3, flow4].each { flow -> assert flow.retrieveFlowStatus().status == FlowState.UP }
        }
    }

    @Tags(SWITCH_RECOVER_ON_FAIL)
    def "ISL should immediately fail if the port went down while switch was disconnected"() {
        when: "A switch disconnects"
        def isl = topology.islsForActiveSwitches.find { it.aswitch?.inPort && it.aswitch?.outPort }
        def blockData = switchHelper.knockoutSwitch(isl.srcSwitch, RW)

        and: "One of its ports goes down"
        //Bring down port on a-switch, which will lead to a port down on the Kilda switch
        aSwitchPorts.setDown([isl.aswitch.inPort])

        and: "The switch reconnects back with a port being down"
        switchHelper.reviveSwitch(isl.srcSwitch, blockData)

        then: "The related ISL immediately goes down"
        Wrappers.wait(WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == FAILED
        }

        when: "The switch disconnects again"
        blockData = lockKeeper.knockoutSwitch(isl.srcSwitch, RW)

        and: "The DOWN port is brought back to UP state"
        aSwitchPorts.setUp([isl.aswitch.inPort])

        and: "The switch reconnects back with a port being up"
        lockKeeper.reviveSwitch(isl.srcSwitch, blockData)
        Wrappers.wait(WAIT_OFFSET) { northbound.getSwitch(isl.srcSwitch.dpId).state == SwitchChangeType.ACTIVATED }

        then: "The related ISL is discovered again"
        Wrappers.wait(WAIT_OFFSET + discoveryInterval + antiflapCooldown) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == DISCOVERED
        }
    }

    def "Unable to get flows for NOT existing link (#item doesn't exist)"() {
        when: "Get flows for NOT existing link"
        northbound.getLinkFlows(srcSwId, srcSwPort, dstSwId, dstSwPort)

        then: "An error is received (404 code)"
        def exc = thrown(HttpClientErrorException)
        new LinkNotFoundExpectedError("There is no ISL between $srcSwId-$srcSwPort and $dstSwId-$dstSwPort.").matches(exc)

        where:
        srcSwId                 | srcSwPort        | dstSwId                 | dstSwPort        | item
        NON_EXISTENT_SWITCH_ID  | getIsl().srcPort | getIsl().dstSwitch.dpId | getIsl().dstPort | "src_switch"
        getIsl().srcSwitch.dpId | 4096             | getIsl().dstSwitch.dpId | getIsl().dstPort | "src_port"
        getIsl().srcSwitch.dpId | getIsl().srcPort | NON_EXISTENT_SWITCH_ID  | getIsl().dstPort | "dst_switch"
        getIsl().srcSwitch.dpId | getIsl().srcPort | getIsl().dstSwitch.dpId | 4096             | "dst_port"
    }

    def "Unable to get flows with specifying invalid query parameters (#item is invalid)"() {
        when: "Get flows with specifying invalid #item"
        northbound.getLinkFlows(srcSwId, srcSwPort, dstSwId, dstSwPort)

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        new UnableToParseRequestArgumentsException("Invalid portId: ${invalidValue}",
                ~/Can not parse arguments when create "get flows for link" request/).matches(exc)

        where:
        srcSwId                 | srcSwPort        | dstSwId                 | dstSwPort        | item                  | invalidValue
        getIsl().srcSwitch.dpId | -1               | getIsl().dstSwitch.dpId | getIsl().dstPort | "src_port"            | -1
        getIsl().srcSwitch.dpId | getIsl().srcPort | getIsl().dstSwitch.dpId | -2               | "dst_port"            | -2
        getIsl().srcSwitch.dpId | -3               | getIsl().dstSwitch.dpId | -4               | "src_port & dst_port" | -3
    }

    def "Unable to get flows without full specifying a particular link (#item is missing)"() {
        when: "Get flows without specifying #item"
        northbound.getLinkFlows(srcSwId, srcSwPort, dstSwId, dstSwPort)

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        new MissingServletRequestParameterException("Required $itemType parameter \'$item\' is not present").matches(exc)

        where:
        srcSwId                 | srcSwPort        | dstSwId                 | dstSwPort | item         | itemType
        null                    | null             | null                    | null      | "src_switch" | "SwitchId"
        getIsl().srcSwitch.dpId | null             | null                    | null      | "src_port"   | "Integer"
        getIsl().srcSwitch.dpId | getIsl().srcPort | null                    | null      | "dst_switch" | "SwitchId"
        getIsl().srcSwitch.dpId | getIsl().srcPort | getIsl().dstSwitch.dpId | null      | "dst_port"   | "Integer"
    }

    def "Unable to delete a nonexistent link"() {
        given: "Parameters of nonexistent link"
        def parameters = new LinkParametersDto(new SwitchId(1).toString(), 100, new SwitchId(2).toString(), 100)

        when: "Try to delete nonexistent link"
        northbound.deleteLink(parameters)

        then: "Get 404 NotFound error"
        def exc = thrown(HttpClientErrorException)
        new LinkNotFoundExpectedError("There is no ISL between $parameters.srcSwitch-$parameters.srcPort " +
                "and $parameters.dstSwitch-$parameters.dstPort.").matches(exc)
    }

    def "Unable to delete an active link"() {
        given: "An active link"
        def isl = topology.getIslsForActiveSwitches()[0]

        when: "Try to delete the link"
        northbound.deleteLink(islUtils.toLinkParameters(isl))

        then: "Get 400 BadRequest error because the link is active"
        def exc = thrown(HttpClientErrorException)
        new LinkIsInIllegalStateExpectedError("Link with following parameters is in illegal state: " +
                "source \'${isl.srcSwitch.dpId}_${isl.srcPort}\', destination \'${isl.dstSwitch.dpId}_${isl.dstPort}\'. " +
                "ISL must NOT be in active state.").matches(exc)
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Able to delete an inactive #islDescription link and re-discover it back afterwards"() {
        given: "An inactive link"
        assumeTrue(isl as boolean, "Unable to locate $islDescription ISL for this test")
        islHelper.breakIsl(isl)

        when: "Try to delete the link"
        def response = northbound.deleteLink(islUtils.toLinkParameters(isl))

        then: "The link is actually deleted"
        response.size() == 2
        !islUtils.getIslInfo(isl)
        !islUtils.getIslInfo(isl.reversed)

        when: "Removed link becomes active again (port brought UP)"
        antiflap.portUp(isl.srcSwitch.dpId, isl.srcPort)

        then: "The link is rediscovered in both directions"
        Wrappers.wait(discoveryExhaustedInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl.reversed).get().state == DISCOVERED
            assert islUtils.getIslInfo(links, isl).get().state == DISCOVERED
        }

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
        def switchPair = switchPairs.all().nonNeighbouring().withAtLeastNPaths(2).random()
        def availablePaths = switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }

        and: "Make the first path more preferable than others by setting corresponding link props"
        def preferablePath = availablePaths[0]
        availablePaths[1..-1].each { islHelper.makePathIslsMorePreferable(preferablePath, it) }

        and: "Create a couple of flows going through these switches"
        def flow1 = flowFactory.getRandom(switchPair)
        def flow1PathIsls = flow1.retrieveAllEntityPaths().getInvolvedIsls()

        def flow2 = flowFactory.getRandom(switchPair, false, FlowState.UP, flow1.occupiedEndpoints())
        def flow2PathIsls = flow2.retrieveAllEntityPaths().getInvolvedIsls()

        assert flow1PathIsls == preferablePath
        assert flow2PathIsls == preferablePath

        and: "Delete link props from all links of alternative paths to allow rerouting flows"
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))

        and: "Make the current flows path not preferable"
        availablePaths[1..-1].each { islHelper.makePathIslsMorePreferable(it, preferablePath) }

        when: "Submit request for rerouting flows"
        def isl = flow1PathIsls.first()
        def response = northbound.rerouteLinkFlows(isl.srcSwitch.dpId, isl.srcPort, isl.dstSwitch.dpId, isl.dstPort)

        then: "Flows are rerouted"
        response.containsAll([flow1, flow2]*.flowId)
        Wrappers.wait(PATH_INSTALLATION_TIME + WAIT_OFFSET) {
            [flow1, flow2].each { flow -> assert flow.retrieveFlowStatus().status == FlowState.UP }
            assert flow1.retrieveAllEntityPaths().getInvolvedIsls() != flow1PathIsls
            assert flow2.retrieveAllEntityPaths().getInvolvedIsls() != flow2PathIsls
        }
    }

    def "Unable to reroute flows with specifying NOT existing link (#item doesn't exist)"() {
        when: "Reroute flows with specifying NOT existing link"
        northbound.rerouteLinkFlows(srcSwId, srcSwPort, dstSwId, dstSwPort)

        then: "An error is received (404 code)"
        def exc = thrown(HttpClientErrorException)
        new LinkNotFoundExpectedError("There is no ISL between $srcSwId-$srcSwPort and $dstSwId-$dstSwPort.").matches(exc)

        where:
        srcSwId                 | srcSwPort        | dstSwId                 | dstSwPort        | item
        NON_EXISTENT_SWITCH_ID  | getIsl().srcPort | getIsl().dstSwitch.dpId | getIsl().dstPort | "src_switch"
        getIsl().srcSwitch.dpId | 4096             | getIsl().dstSwitch.dpId | getIsl().dstPort | "src_port"
        getIsl().srcSwitch.dpId | getIsl().srcPort | NON_EXISTENT_SWITCH_ID  | getIsl().dstPort | "dst_switch"
        getIsl().srcSwitch.dpId | getIsl().srcPort | getIsl().dstSwitch.dpId | 4096             | "dst_port"
    }

    def "Unable to reroute flows with specifying invalid query parameters (#item is invalid)"() {
        when: "Reroute flows with specifying invalid #item"
        northbound.rerouteLinkFlows(srcSwId, srcSwPort, dstSwId, dstSwPort)

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        new UnableToParseRequestArgumentsException("Invalid portId: ${invalidValue}",
                ~/Can not parse arguments when create "reroute flows for link" request/).matches(exc)

        where:
        srcSwId                 | srcSwPort        | dstSwId                 | dstSwPort        | item                  | invalidValue
        getIsl().srcSwitch.dpId | -1               | getIsl().dstSwitch.dpId | getIsl().dstPort | "src_port"            | -1
        getIsl().srcSwitch.dpId | getIsl().srcPort | getIsl().dstSwitch.dpId | -2               | "dst_port"            | -2
        getIsl().srcSwitch.dpId | -3               | getIsl().dstSwitch.dpId | -4               | "src_port & dst_port" | -3
    }

    def "Unable to reroute flows without full specifying a particular link (#item is missing)"() {
        when: "Reroute flows without specifying #item"
        northbound.rerouteLinkFlows(srcSwId, srcSwPort, dstSwId, dstSwPort)

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        new MissingServletRequestParameterException("Required $itemType parameter \'$item\' is not present").matches(exc)

        where:
        srcSwId                 | srcSwPort        | dstSwId                 | dstSwPort | item         | itemType
        null                    | null             | null                    | null      | "src_switch" | "SwitchId"
        getIsl().srcSwitch.dpId | null             | null                    | null      | "src_port"   | "Integer"
        getIsl().srcSwitch.dpId | getIsl().srcPort | null                    | null      | "dst_switch" | "SwitchId"
        getIsl().srcSwitch.dpId | getIsl().srcPort | getIsl().dstSwitch.dpId | null      | "dst_port"   | "Integer"
    }

    def "Get links with specifying query parameters: #description"() {
        when: "Get links with specifying query parameters"
        def links = northbound.getLinks(srcSwId, srcSwPort, dstSwId, dstSwPort)

        then: "The corresponding list of links is returned"
        links.each {actualLink ->
            assert filterLinks(northbound.getAllLinks(), srcSwId, srcSwPort, dstSwId, dstSwPort).find { IslInfoData expectedLink ->
                actualLink.source.switchId == expectedLink.source.switchId &&
                        actualLink.source.portNo == expectedLink.source.portNo &&
                        actualLink.destination.switchId == expectedLink.destination.switchId &&
                        actualLink.destination.portNo == expectedLink.destination.portNo
            }, "could not find $actualLink"
        }

        where:
        description                              | srcSwId                 | srcSwPort        | dstSwId                 | dstSwPort
        "without params"                         | null                    | null             | null                    | null
        "with src(swId)"                         | getIsl().srcSwitch.dpId | null             | null                    | null
        "with src(swId+port)"                    | getIsl().srcSwitch.dpId | getIsl().srcPort | null                    | null
        "with src(swId+port) and dst(swId)"      | getIsl().srcSwitch.dpId | getIsl().srcPort | getIsl().dstSwitch.dpId | null
        "with src(swId+port) and dst(swId+port)" | getIsl().srcSwitch.dpId | getIsl().srcPort | getIsl().dstSwitch.dpId | getIsl().dstPort
    }

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

    def "Unable to get links with specifying invalid query parameters (#item is invalid)"() {
        when: "Get links with specifying invalid #item"
        northbound.getLinks(srcSwId, srcSwPort, dstSwId, dstSwPort)

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        new UnableToParseRequestArgumentsException("Invalid portId: ${invalidValue}",
                ~/Can not parse arguments when create 'get links' request/).matches(exc)

        where:
        srcSwId                 | srcSwPort        | dstSwId                 | dstSwPort        | item                  | invalidValue
        getIsl().srcSwitch.dpId | -1               | getIsl().dstSwitch.dpId | getIsl().dstPort | "src_port"            | -1
        getIsl().srcSwitch.dpId | getIsl().srcPort | getIsl().dstSwitch.dpId | -2               | "dst_port"            | -2
        getIsl().srcSwitch.dpId | -3               | getIsl().dstSwitch.dpId | -4               | "src_port & dst_port" | -3
    }

    @Tags([SMOKE])
    def "ISL is able to properly fail when both src and dst switches suddenly disconnect"() {
        given: "An active ISL"
        def isl = topology.islsForActiveSwitches.first()

        when: "Source and destination switches of the ISL suddenly disconnect"
        def srcBlockData = switchHelper.knockoutSwitch(isl.srcSwitch, RW)
        def dstBlockData = switchHelper.knockoutSwitch(isl.dstSwitch, RW)

        then: "ISL gets failed after discovery timeout"
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == FAILED
        }
    }

    @Tags(SMOKE)
    def "Able to update max bandwidth for a link"() {
        given: "An active ISL"
        // Find such an ISL that is the only ISL between switches.
        def isl = topology.islsForActiveSwitches.find { item ->
            topology.islsForActiveSwitches.findAll {
                item.srcSwitch == it.srcSwitch && item.dstSwitch == it.dstSwitch
            }.size() == 1
        }
        def islInfo = islUtils.getIslInfo(isl).get()
        def initialMaxBandwidth = islInfo.maxBandwidth
        def initialAvailableBandwidth = islInfo.availableBandwidth

        when: "Create a flow going through this ISL"
        def flowMaxBandwidth = 12345
        flowFactory.getBuilder(isl.srcSwitch, isl.dstSwitch).withBandwidth(flowMaxBandwidth).build().create()

        and: "Update max bandwidth for the link"
        def offset = 10000
        def newMaxBandwidth = initialMaxBandwidth - offset
        islHelper.updateLinkMaxBandwidthUsingApi(isl, newMaxBandwidth)

        def links = northbound.getActiveLinks()
        def linkProps = northbound.getLinkProps(topology.isls)

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
        new LinkPropertiesNotUpdatedExpectedError(~/Not enough available bandwidth for operation/).matches(exc)

        when: "Update max bandwidth to the value equal to max bandwidth of the created flow"
        northbound.updateLinkMaxBandwidth(isl.srcSwitch.dpId, isl.srcPort, isl.dstSwitch.dpId, isl.dstPort,
                flowMaxBandwidth)
        links = northbound.getActiveLinks()
        linkProps = northbound.getLinkProps(topology.isls)

        then: "Max bandwidth is really updated and available bandwidth is also recalculated"
        [isl, isl.reversed].each {
            assert islUtils.getIslInfo(links, it).get().maxBandwidth == flowMaxBandwidth
            assert islUtils.getIslInfo(links, it).get().availableBandwidth == 0
        }

        and: "Link props are also updated"
        assert linkProps.size() == 2
        linkProps.each { assert it.props["max_bandwidth"].toLong() == flowMaxBandwidth }

        when: "Update max bandwidth to the initial value"
        islHelper.updateLinkMaxBandwidthUsingApi(isl, initialMaxBandwidth)
        links = northbound.getActiveLinks()
        linkProps = northbound.getLinkProps(topology.isls)

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
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
        links = northbound.getActiveLinks()

        then: "Max bandwidth and available bandwidth are not changed"
        [isl, isl.reversed].each {
            assert islUtils.getIslInfo(links, it).get().maxBandwidth == initialMaxBandwidth
            assert islUtils.getIslInfo(links, it).get().availableBandwidth ==
                    initialAvailableBandwidth - flowMaxBandwidth
        }
    }

    def "Unable to update max bandwidth with specifying invalid query parameters (#item is invalid)"() {
        when: "Update max bandwidth with specifying invalid #item"
        northbound.updateLinkMaxBandwidth(srcSwId, srcSwPort, dstSwId, dstSwPort, 1000000)

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        new InvalidRequestParametersExpectedError("Invalid value of $invalidEndpoint port",
                ~/Port number can't be negative/).matches(exc)

        where:
        srcSwId                 | srcSwPort        | dstSwId                 | dstSwPort        | item                  | invalidEndpoint
        getIsl().srcSwitch.dpId | -1               | getIsl().dstSwitch.dpId | getIsl().dstPort | "src_port"            | "source"
        getIsl().srcSwitch.dpId | getIsl().srcPort | getIsl().dstSwitch.dpId | -2               | "dst_port"            | "destination"
        getIsl().srcSwitch.dpId | -3               | getIsl().dstSwitch.dpId | -4               | "src_port & dst_port" | "source"
    }

    def "Unable to update max bandwidth without full specifying a particular link (#item is missing)"() {
        when: "Update max bandwidth without specifying #item"
        northbound.updateLinkMaxBandwidth(srcSwId, srcSwPort, dstSwId, dstSwPort, 1000000)

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        new MissingServletRequestParameterException("Required $itemType parameter \'$item\' is not present").matches(exc)

        where:
        srcSwId                 | srcSwPort        | dstSwId                 | dstSwPort | item         | itemType
        null                    | null             | null                    | null      | "src_switch" | "SwitchId"
        getIsl().srcSwitch.dpId | null             | null                    | null      | "src_port"   | "Integer"
        getIsl().srcSwitch.dpId | getIsl().srcPort | null                    | null      | "dst_switch" | "SwitchId"
        getIsl().srcSwitch.dpId | getIsl().srcPort | getIsl().dstSwitch.dpId | null      | "dst_port"   | "Integer"
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Unable to delete inactive link with flowPath"() {
        given: "An inactive link with flow on it"
        def switchPair = switchPairs.all().neighbouring().random()
        def flow = flowFactory.getBuilder(switchPair).withPinned(true).build().create()

        def isl = flow.retrieveAllEntityPaths().getInvolvedIsls().first()
        islHelper.breakIsl(isl)

        when: "Try to delete the link"
        northbound.deleteLink(islUtils.toLinkParameters(isl))

        then: "Get 400 BadRequest error because the link with flow path"
        def exc = thrown(HttpClientErrorException)
        new LinkIsInIllegalStateExpectedError("Link with following parameters is in illegal state: " +
                "source \'${isl.srcSwitch.dpId}_${isl.srcPort}\', destination \'${isl.dstSwitch.dpId}_${isl.dstPort}\'. " +
                "This ISL is busy by flow paths.").matches(exc)
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Able to delete an active link with flowPath if using force delete"() {
        given: "Two active neighboring switches and two possible paths at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(2).random()

        and: "An active link with flow on it"
        def flow = flowFactory.getRandom(switchPair)
        def flowPath = flow.retrieveAllEntityPaths()
        def isl = flowPath.getInvolvedIsls().first()

        when: "Delete the link using force"
        def response = islHelper.deleteIsl(isl, true)

        then: "The link is actually deleted"
        response.size() == 2
        !islUtils.getIslInfo(isl)
        !islUtils.getIslInfo(isl.reversed)

        and: "Flow is not rerouted and UP"
        flow.retrieveAllEntityPaths() == flowPath
        flow.retrieveFlowStatus().status == FlowState.UP

        and: "Flow is valid"
        flow.validateAndCollectDiscrepancies().isEmpty()

        when: "Removed link becomes active again (port brought DOWN/UP)"
        antiflap.portDown(isl.srcSwitch.dpId, isl.srcPort)
        antiflap.portUp(isl.srcSwitch.dpId, isl.srcPort)

        then: "The link is rediscovered in both directions"
        Wrappers.wait(discoveryExhaustedInterval + WAIT_OFFSET*2) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl.reversed).get().state == DISCOVERED
            assert islUtils.getIslInfo(links, isl).get().state == DISCOVERED
        }

        and: "Source and destination switches pass switch validation"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(switchPair.toList()*.getDpId()).isEmpty()
    }

    def "System detects a 1-way ISL as a Failed ISL"() {
        given: "A deleted a-switch ISL"
        def isl = topology.islsForActiveSwitches.find {
            it.aswitch?.inPort && it.aswitch?.outPort
        } ?: assumeTrue(false, "Wasn't able to find suitable link")
        aSwitchFlows.removeFlows([isl.aswitch, isl.aswitch.reversed])
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == FAILED
        }

        northbound.deleteLink(islUtils.toLinkParameters(isl))
        Wrappers.wait(WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert !islUtils.getIslInfo(links, isl).present
        }

        when: "Add a-switch rules for discovering ISL in one direction only"
        aSwitchFlows.addFlows([isl.aswitch])


        then: "The ISL is discovered"
        Wrappers.wait(RULES_INSTALLATION_TIME + discoveryInterval + WAIT_OFFSET) {
            def fw = northbound.getLink(isl)
            def rv = northbound.getLink(isl.reversed)
            assert fw.state == FAILED
            assert fw.actualState == DISCOVERED
            assert rv.state == FAILED
            assert rv.actualState == FAILED
        }

        and: "The src/dst switches are valid"
        //https://github.com/telstra/open-kilda/issues/3906
        def switchesNotAffectedBy3906 = [isl.srcSwitch, isl.dstSwitch].findAll {
            !it.prop || (it.prop.server42IslRtt == "DISABLED")
        }
        switchHelper.validateAndCollectFoundDiscrepancies(switchesNotAffectedBy3906*.getDpId()).isEmpty()
    }

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
