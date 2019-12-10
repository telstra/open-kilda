package org.openkilda.functionaltests.spec.flows

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.testing.Constants.STATS_LOGGING_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.northbound.dto.v1.flows.PingInput
import org.openkilda.northbound.dto.v1.flows.PingOutput.PingOutputBuilder
import org.openkilda.northbound.dto.v1.flows.UniFlowPingOutput
import org.openkilda.testing.Constants.DefaultRule
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import org.springframework.beans.factory.annotation.Value
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Unroll

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/flow-ping")
@Narrative("""
This spec tests all the functionality related to flow pings. 
Flow ping feature sends a 'ping' packet at the one end of the flow, expecting that this packet will 
be delivered at the other end. 'Pings' the flow in both directions(forward and reverse).
""")
class FlowPingSpec extends HealthCheckSpecification {

    @Value('${opentsdb.metric.prefix}')
    String metricPrefix

    @Tidy
    @Unroll("Able to ping a flow with vlan between switches #srcSwitch.dpId - #dstSwitch.dpId")
    @Tags([TOPOLOGY_DEPENDENT])
    def "Able to ping a flow with vlan"(Switch srcSwitch, Switch dstSwitch) {
        given: "A flow with random vlan"
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flowHelperV2.addFlow(flow)

        when: "Ping the flow"
        def beforePingTime = new Date()
        def unicastCounterBefore = northbound.getSwitchRules(srcSwitch.dpId).flowEntries.find {
            it.cookie == DefaultRule.VERIFICATION_UNICAST_RULE.cookie
        }.byteCount
        def response = northbound.pingFlow(flow.flowId, new PingInput())

        then: "Ping is successful"
        response.forward.pingSuccess
        response.reverse.pingSuccess

        and: "No errors"
        !response.error
        !response.forward.error
        !response.reverse.error

        and: "Unicast rule packet count is increased and logged to otsdb"
        def statsData = null
        Wrappers.wait(STATS_LOGGING_TIMEOUT, 2) {
            statsData = otsdb.query(beforePingTime, metricPrefix + "switch.flow.system.bytes",
                    [switchid : srcSwitch.dpId.toOtsdFormat(),
                     cookieHex: DefaultRule.VERIFICATION_UNICAST_RULE.toHexString()]).dps
            assert statsData && !statsData.empty
        }
        statsData.values().last().toLong() > unicastCounterBefore

        cleanup: "Remove the flow"
        flowHelperV2.deleteFlow(flow.flowId)

        where:
        [srcSwitch, dstSwitch] << ofSwitchCombinations
    }

    @Tidy
    @Unroll("Able to ping a flow with no vlan between switches #srcSwitch.dpId - #dstSwitch.dpId")
    @Tags([TOPOLOGY_DEPENDENT])
    def "Able to ping a flow with no vlan"(Switch srcSwitch, Switch dstSwitch) {
        given: "A flow with no vlan"
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flow.source.vlanId = 0
        flow.destination.vlanId = 0
        flowHelperV2.addFlow(flow)

        when: "Ping the flow"
        def response = northbound.pingFlow(flow.flowId, new PingInput())

        then: "Ping is successful"
        response.forward.pingSuccess
        response.reverse.pingSuccess

        and: "No errors"
        !response.error
        !response.forward.error
        !response.reverse.error

        cleanup: "Remove the flow"
        flowHelperV2.deleteFlow(flow.flowId)

        where:
        [srcSwitch, dstSwitch] << ofSwitchCombinations
    }

    @Tidy
    @Unroll("Flow ping can detect a broken #description")
    @IterationTag(tags = [SMOKE], iterationNameRegex = /forward path/)
    def "Flow ping can detect a broken path"() {
        given: "A flow with at least 1 a-switch link"
        def switches = topology.activeSwitches.findAll { !it.centec && it.ofVersion != "OF_12" }
        List<List<PathNode>> allPaths = []
        List<PathNode> aswitchPath
        //select src and dst switches that have an a-switch path
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            allPaths = database.getPaths(src.dpId, dst.dpId)*.path
            aswitchPath = allPaths.find { pathHelper.getInvolvedIsls(it).find { it.aswitch } }
            aswitchPath
        } ?: assumeTrue("Wasn't able to find suitable switch pair", false)
        //make a-switch path the most preferable
        allPaths.findAll { it != aswitchPath }.each { pathHelper.makePathMorePreferable(aswitchPath, it) }
        //build a flow
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flowHelperV2.addFlow(flow)
        expectedPingResult.flowId = flow.flowId
        assert aswitchPath == pathHelper.convert(northbound.getFlowPath(flow.flowId))

        when: "Break the flow by removing rules from a-switch"
        def islToBreak = pathHelper.getInvolvedIsls(aswitchPath).find { it.aswitch }
        def rulesToRemove = []
        data.breakForward && rulesToRemove << islToBreak.aswitch
        data.breakReverse && rulesToRemove << islToBreak.aswitch.reversed
        lockKeeper.removeFlows(rulesToRemove)

        and: "Ping the flow"
        def response = northbound.pingFlow(flow.flowId, data.pingInput)

        then: "Ping response properly shows that certain direction is unpingable"
        expect response, sameBeanAs(expectedPingResult)
                .ignoring("forward.latency").ignoring("reverse.latency")

        cleanup: "Restore rules, costs and remove the flow"
        rulesToRemove && lockKeeper.addFlows(rulesToRemove)
        flow && flowHelperV2.deleteFlow(flow.flowId)
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert islUtils.getIslInfo(islToBreak).get().state == IslChangeType.DISCOVERED
        }
        database.resetCosts()

        where:
        data << [
                [
                        breakForward: true,
                        breakReverse: false,
                        pingInput   : new PingInput()
                ],
                [
                        breakForward: false,
                        breakReverse: true,
                        pingInput   : new PingInput()
                ],
                [
                        breakForward: true,
                        breakReverse: true,
                        pingInput   : new PingInput()
                ],
                [
                        breakForward: true,
                        breakReverse: false,
                        pingInput   : new PingInput((getDiscoveryInterval() + 1) * 1000)
                ],
                [
                        breakForward: false,
                        breakReverse: true,
                        pingInput   : new PingInput((getDiscoveryInterval() + 1) * 1000)
                ],
                [
                        breakForward: true,
                        breakReverse: true,
                        pingInput   : new PingInput((getDiscoveryInterval() + 1) * 1000)
                ]
        ]
        description = "${data.breakForward ? "forward" : ""}${data.breakForward && data.breakReverse ? " and " : ""}" +
                "${data.breakReverse ? "reverse" : ""} path with ${data.pingInput.timeoutMillis}ms timeout"

        expectedPingResult = new PingOutputBuilder().forward(
                new UniFlowPingOutput(
                        pingSuccess: !data.breakForward,
                        error: data.breakForward ? "No ping for reasonable time" : null)).reverse(
                new UniFlowPingOutput(
                        pingSuccess: !data.breakReverse,
                        error: data.breakReverse ? "No ping for reasonable time" : null))
                .error(null)
                .build()
    }

    /**
     * Single-switch ping does not actually verify the routing. It is an always-success.
     * Should consider disabling the ability to ping such flows at all.
     */
    @Tidy
    def "Able to ping a single-switch flow"() {
        given: "A single-switch flow"
        def sw = topology.activeSwitches.find { !it.centec && it.ofVersion != "OF_12" }
        assert sw
        def flow = flowHelperV2.singleSwitchFlow(sw)
        flowHelperV2.addFlow(flow)

        when: "Ping the flow"
        def response = northbound.pingFlow(flow.flowId, new PingInput())

        then: "The flow is pingable"
        response.forward.pingSuccess
        response.reverse.pingSuccess

        and: "No errors are present"
        !response.error
        !response.forward.error
        !response.reverse.error

        cleanup: "Remove the flow"
        flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
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

    /**
     * Returns all switch combinations with unique description, excluding single-switch combinations,
     * combinations with Centec switches and OF_12 switches
     */
    def getOfSwitchCombinations() {
        def switches = topology.activeSwitches.findAll {
            it.ofVersion != "OF_12" && !it.centec
        }
        [switches, switches].combinations().findAll { src, dst ->
            src != dst
        }.unique { [it*.description.sort(), it*.ofVersion.sort()] }
    }
}
