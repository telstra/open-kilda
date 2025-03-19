package org.openkilda.functionaltests.spec.flows


import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.model.switches.Manufacturer.CENTEC
import static org.openkilda.testing.Constants.DefaultRule.VERIFICATION_UNICAST_RULE
import static org.openkilda.testing.Constants.DefaultRule.VERIFICATION_UNICAST_VXLAN_RULE_COOKIE
import static org.openkilda.testing.Constants.STATS_LOGGING_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowNotCreatedExpectedError
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowDirection
import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.functionaltests.helpers.model.SwitchExtended
import org.openkilda.model.cookie.Cookie
import org.openkilda.northbound.dto.v1.flows.PingInput
import org.openkilda.testing.model.topology.TopologyDefinition.Isl

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/flow-ping")
@Narrative("""
This spec tests all the functionality related to flow pings. 
Flow ping feature sends a 'ping' packet at the one end of the flow, expecting that this packet will 
be delivered at the other end. 'Pings' the flow in both directions(forward and reverse).
""")

class FlowPingSpec extends HealthCheckSpecification {

    @Value('${flow.ping.interval}')
    int pingInterval
    @Autowired
    @Shared
    FlowFactory flowFactory

    @Tags([TOPOLOGY_DEPENDENT])
    def "Able to ping a flow with vlan between #switchPairDescription"(SwitchExtended srcSwitch, SwitchExtended dstSwitch) {
        given: "A flow with random vlan"
        def flow = flowFactory.getBuilder(srcSwitch, dstSwitch)
                .withEncapsulationType(FlowEncapsulationType.TRANSIT_VLAN).build()
                .create()

        when: "Ping the flow"
        def unicastCounterBefore = srcSwitch.rulesManager.getRules().find {
            it.cookie == VERIFICATION_UNICAST_RULE.cookie
        }.byteCount
        def response = flow.ping()

        then: "Ping is successful"
        response.forward.pingSuccess
        response.reverse.pingSuccess

        and: "No errors"
        !response.error
        !response.forward.error
        !response.reverse.error

        // 0 latency issue https://github.com/telstra/open-kilda/issues/3312
        // and: "Latency is present in response"
        // response.forward.latency
        // response.reverse.latency

        and: "Unicast rule packet count is increased and logged to otsdb"

        Wrappers.wait(STATS_LOGGING_TIMEOUT, 2) {
            assert srcSwitch.rulesManager.getRules().find {
                it.cookie == VERIFICATION_UNICAST_RULE.cookie
            }.byteCount > unicastCounterBefore

        }

        where:
        [srcSwitch, dstSwitch] << ofSwitchCombinations
        switchPairDescription = "src[$srcSwitch.description $srcSwitch.ofVersion]-dst[$dstSwitch.description $dstSwitch.ofVersion]"
    }

    @Tags([TOPOLOGY_DEPENDENT])
    def "Able to ping a flow with vxlan"() {
        given: "A flow with random vxlan"
        def switchPair = switchPairs.all().neighbouring().withBothSwitchesVxLanEnabled().random()
        def flow = flowFactory.getBuilder(switchPair)
                .withEncapsulationType(FlowEncapsulationType.VXLAN).build()
                .create()

        when: "Ping the flow"
        def unicastCounterBefore = switchPair.src.rulesManager.getRules().find {
            it.cookie == VERIFICATION_UNICAST_VXLAN_RULE_COOKIE.cookie //rule for the vxlan differs from vlan
        }.byteCount
        def response = flow.ping()

        then: "Ping is successful"
        response.forward.pingSuccess
        response.reverse.pingSuccess

        and: "No errors"
        !response.error
        !response.forward.error
        !response.reverse.error


        and: "Unicast rule packet count is increased and logged to otsdb"
        Wrappers.wait(STATS_LOGGING_TIMEOUT, 2) {
            assert switchPair.src.rulesManager.getRules().find {
                it.cookie == VERIFICATION_UNICAST_VXLAN_RULE_COOKIE.cookie
            }.byteCount > unicastCounterBefore
        }
    }

    @Tags([TOPOLOGY_DEPENDENT])
    def "Able to ping a flow with no vlan between #switchPairDescription"(SwitchExtended srcSwitch, SwitchExtended dstSwitch) {
        given: "A flow with no vlan"
        def flow = flowFactory.getBuilder(srcSwitch, dstSwitch)
                .withSourceVlan(0)
                .withDestinationVlan(0).build()
                .create()

        when: "Ping the flow"
        def response = flow.ping()

        then: "Ping is successful"
        response.forward.pingSuccess
        response.reverse.pingSuccess

        and: "No errors"
        !response.error
        !response.forward.error
        !response.reverse.error

        where:
        [srcSwitch, dstSwitch] << ofSwitchCombinations
        switchPairDescription = "src[$srcSwitch.description $srcSwitch.ofVersion]-dst[$dstSwitch.description $dstSwitch.ofVersion]"

    }

    @IterationTag(tags = [SMOKE], iterationNameRegex = /forward path/)
    def "Flow ping can detect a broken path(#description) for a vlan flow"() {
        given: "A flow with at least 1 a-switch link"
        List<List<Isl>> allPaths = []
        List<Isl> aswitchPathIsls = []
        //select src and dst switches that have an a-switch path
        def swPair = switchPairs.all().withSourceSwitchNotManufacturedBy(CENTEC).getSwitchPairs().find {
            allPaths = it.retrieveAvailablePaths().collect { it.getInvolvedIsls() }
            aswitchPathIsls = allPaths.find { it.find { isl ->  isl.aswitch }}
            aswitchPathIsls
        } ?: assumeTrue(false, "Wasn't able to find suitable switch pair")

        //make a-switch path the most preferable
        allPaths.findAll { !it.containsAll(aswitchPathIsls) }.each { islHelper.makePathIslsMorePreferable(aswitchPathIsls, it) }

        //build a flow
        def flow = flowFactory.getRandom(swPair)
        assert aswitchPathIsls == flow.retrieveAllEntityPaths().getInvolvedIsls()

        when: "Break the flow by removing rules from a-switch"
        def islToBreak = aswitchPathIsls.find { it.aswitch }
        def rulesToRemove = []
        data.breakForward && rulesToRemove << islToBreak.aswitch
        data.breakReverse && rulesToRemove << islToBreak.aswitch.reversed
        aSwitchFlows.removeFlows(rulesToRemove)

        and: "Ping the flow"
        def discrepancies = flow.pingAndCollectDiscrepancies(data.pingInput)

        then: "Ping response properly shows that certain direction is unpingable"
        discrepancies == data.expectedDiscrepancy

        where:
        data << [
                [
                        breakForward: true,
                        breakReverse: false,
                        pingInput   : new PingInput(),
                        expectedDiscrepancy: [(FlowDirection.FORWARD): "No ping for reasonable time"]
                ],
                [
                        breakForward: false,
                        breakReverse: true,
                        pingInput   : new PingInput(),
                        expectedDiscrepancy: [(FlowDirection.REVERSE): "No ping for reasonable time"]
                ],
                [
                        breakForward: true,
                        breakReverse: true,
                        pingInput   : new PingInput(),
                        expectedDiscrepancy: [(FlowDirection.FORWARD): "No ping for reasonable time",
                                              (FlowDirection.REVERSE): "No ping for reasonable time"]
                ],
                [
                        breakForward: true,
                        breakReverse: false,
                        pingInput   : new PingInput((getDiscoveryInterval() + 1) * 1000),
                        expectedDiscrepancy: [(FlowDirection.FORWARD): "No ping for reasonable time"]
                ],
                [
                        breakForward: false,
                        breakReverse: true,
                        pingInput   : new PingInput((getDiscoveryInterval() + 1) * 1000),
                        expectedDiscrepancy: [(FlowDirection.REVERSE): "No ping for reasonable time"]
                ],
                [
                        breakForward: true,
                        breakReverse: true,
                        pingInput   : new PingInput((getDiscoveryInterval() + 1) * 1000),
                        expectedDiscrepancy: [(FlowDirection.FORWARD): "No ping for reasonable time",
                                              (FlowDirection.REVERSE): "No ping for reasonable time"]
                ]
        ]
        description = "${data.breakForward ? "forward" : ""}${data.breakForward && data.breakReverse ? " and " : ""}" +
                "${data.breakReverse ? "reverse" : ""} path with ${data.pingInput.timeoutMillis}ms timeout"
    }

    def "Unable to ping a single-switch flow"() {
        given: "A single-switch flow"
        def sw = switches.all().notOF12Version().find { !it.isCentec() }
        assert sw
        def flow = flowFactory.getSingleSwRandom(sw)

        when: "Ping the flow"
        def response = flow.ping()

        then: "Error received"
        !response.forward
        !response.reverse
        response.error == "Flow ${flow.flowId} should not be one-switch flow"
    }

    def "Verify error if try to ping with wrong flowId"() {
        when: "Send ping request with non-existing flowId"
        def wrongFlowId = "nonexistent"
        def response = northbound.pingFlow(wrongFlowId, new PingInput())

        then: "Receive error response"
        verifyAll(response) {
            flowId == wrongFlowId
            !forward
            !reverse
            error == "Flow $wrongFlowId does not exist"
        }
    }

    @Tags(TOPOLOGY_DEPENDENT)
    def "Flow ping can detect a broken path for a vxlan flow on an intermediate switch"() {
        given: "A vxlan flow with intermediate switch(es)"
        def switchPair = switchPairs.all().nonNeighbouring().withBothSwitchesVxLanEnabled().random()

        def flow = flowFactory.getBuilder(switchPair)
                .withEncapsulationType(FlowEncapsulationType.VXLAN).build()
                .create()
       assert flow.pingAndCollectDiscrepancies().isEmpty()

        when: "Break the flow by removing flow rules from the intermediate switch"
        def intermediateSw = switches.all().findSpecific(flow.retrieveAllEntityPaths().getInvolvedSwitches()[1])
        def rulesToDelete = intermediateSw.rulesManager.getRules().findAll {
            !new Cookie(it.cookie).serviceFlag
        }*.cookie

        rulesToDelete.each { cookie -> intermediateSw.rulesManager.delete(cookie) }

        then: "Ping shows that path is broken"
        assert flow.pingAndCollectDiscrepancies().keySet() == [FlowDirection.FORWARD, FlowDirection.REVERSE].toSet()
    }

    def "Able to turn on periodic pings on a flow"() {
        when: "Create a flow with periodic pings turned on"
        def endpointSwitches = switchPairs.all().nonNeighbouring().random()
        def flow = flowFactory.getBuilder(endpointSwitches).withPeriodicPing(true).build()
                .create()

        then: "Packet counter on catch ping rules grows due to pings happening"
        String encapsulationType = flow.encapsulationType.toString()
        def srcSwitchPacketCount = endpointSwitches.src.rulesManager.pingRule(encapsulationType).packetCount
        def dstSwitchPacketCount = endpointSwitches.dst.rulesManager.pingRule(encapsulationType).packetCount

        Wrappers.wait(pingInterval + WAIT_OFFSET / 2) {
            def srcPacketCountNow = endpointSwitches.src.rulesManager.pingRule(encapsulationType).packetCount
            def dstPacketCountNow = endpointSwitches.dst.rulesManager.pingRule(encapsulationType).packetCount

            srcPacketCountNow > srcSwitchPacketCount && dstPacketCountNow > dstSwitchPacketCount
        }
    }

    @Tags([LOW_PRIORITY])
    def "Unable to create a single-switch flow with periodic pings"() {
        when: "Try to create a single-switch flow with periodic pings"
        def singleSwitch = switches.all().first()
        flowFactory.getSingleSwBuilder(singleSwitch)
                .withPeriodicPing(true).build()
                .create()

        then: "Error is returned in response"
        def e = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(~/Couldn\'t turn on periodic pings for one-switch flow/).matches(e)
    }


    /**
     * Returns all switch combinations with unique description, excluding single-switch combinations,
     * combinations with Centec switches and OF_12 switches
     */
    def getOfSwitchCombinations() {
        def switches = switches.all().notOF12Version().findAll { !it.isCentec() }

        [switches, switches].combinations().findAll { src, dst ->
            src != dst
        }.unique { [it*.description.sort(), it*.ofVersion.sort()] }
    }
}
