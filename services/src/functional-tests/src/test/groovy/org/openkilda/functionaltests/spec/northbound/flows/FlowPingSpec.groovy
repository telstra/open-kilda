package org.openkilda.functionaltests.spec.northbound.flows

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.extension.fixture.rule.CleanupSwitches
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.info.event.SwitchInfoData
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.flows.PingInput
import org.openkilda.northbound.dto.flows.PingOutput.PingOutputBuilder
import org.openkilda.northbound.dto.flows.UniFlowPingOutput
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.tools.IslUtils

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.Unroll

@Narrative("""
This spec tests all the functionality related to flow pings. 
Flow ping feature sends a 'ping' packet at the one end of the flow, expecting that this packet will 
be delivered at the other end. 'Pings' the flow in both directions(forward and reverse).
""")
@CleanupSwitches
class FlowPingSpec extends BaseSpecification {
    @Autowired
    TopologyDefinition topology
    @Autowired
    FlowHelper flowHelper
    @Autowired
    IslUtils islUtils
    @Autowired
    PathHelper pathHelper
    @Autowired
    NorthboundService northboundService
    @Autowired
    Database db
    @Autowired
    LockKeeperService lockKeeper

    @Value('${discovery.interval}')
    int discoveryInterval

    @Unroll("Able to ping a flow with vlan between switches #srcSwitch.dpId - #dstSwitch.dpId")
    def "Able to ping a flow with vlan"(Switch srcSwitch, Switch dstSwitch) {
        given: "A flow with random vlan"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        northboundService.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getFlowStatus(flow.id).status == FlowState.UP }

        when: "Ping the flow"
        def response = northboundService.pingFlow(flow.id, new PingInput())

        then: "Ping is successfull"
        response.forward.pingSuccess
        response.reverse.pingSuccess

        and: "No errors"
        !response.error
        !response.forward.error
        !response.reverse.error

        and: "Remove the flow"
        flowHelper.deleteFlow(flow.id)

        where:
        [srcSwitch, dstSwitch] << ofSwitchCombinations
    }

    @Unroll("Able to ping a flow with no vlan between switches #srcSwitch.dpId - #dstSwitch.dpId")
    def "Able to ping a flow with no vlan"(Switch srcSwitch, Switch dstSwitch) {
        given: "A flow with no vlan"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.source.vlanId = 0
        flow.destination.vlanId = 0
        northboundService.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getFlowStatus(flow.id).status == FlowState.UP }

        when: "Ping the flow"
        def response = northboundService.pingFlow(flow.id, new PingInput())

        then: "Ping is successful"
        response.forward.pingSuccess
        response.reverse.pingSuccess

        and: "No errors"
        !response.error
        !response.forward.error
        !response.reverse.error

        and: "Remove the flow"
        flowHelper.deleteFlow(flow.id)

        where:
        [srcSwitch, dstSwitch] << ofSwitchCombinations
    }

    @Issue("https://github.com/telstra/open-kilda/issues/1416")
    @Unroll("Flow ping can detect a broken #description")
    def "Flow ping can detect a broken path"() {
        given: "A flow with at least 1 a-switch link"
        def switches = nonCentecSwitches()
        List<List<PathNode>> allPaths = []
        List<PathNode> aswitchPath
        //select src and dst switches that have an a-switch path
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            allPaths = db.getPaths(src.dpId, dst.dpId)*.path
            aswitchPath = allPaths.find { pathHelper.getInvolvedIsls(it).find { it.aswitch } }
            aswitchPath
        } ?: assumeTrue("Wasn't able to find suitable switch pair", false)
        //make a-switch path the most preferable
        allPaths.findAll { it != aswitchPath }.each { pathHelper.makePathMorePreferable(aswitchPath, it) }
        //build a flow
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        northboundService.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        expectedPingResult.flowId = flow.id
        assert aswitchPath == pathHelper.convert(northboundService.getFlowPath(flow.id))

        when: "Break the flow by removing rules from a-switch"
        def islToBreak = pathHelper.getInvolvedIsls(aswitchPath).find { it.aswitch }
        def rulesToRemove = []
        data.breakForward && rulesToRemove << new ASwitchFlow(islToBreak.aswitch.inPort, islToBreak.aswitch.outPort)
        data.breakReverse && rulesToRemove << new ASwitchFlow(islToBreak.aswitch.outPort, islToBreak.aswitch.inPort)
        lockKeeper.removeFlows(rulesToRemove)

        and: "Ping the flow"
        def response = northboundService.pingFlow(flow.id, data.pingInput)

        then: "Ping response properly shows that certain direction is unpingable"
        expect response, sameBeanAs(expectedPingResult)
                .ignoring("forward.latency").ignoring("reverse.latency")

        and: "Restore rules, costs and remove the flow"
        lockKeeper.addFlows(rulesToRemove)
        flowHelper.deleteFlow(flow.id)
        northboundService.deleteLinkProps(northboundService.getAllLinkProps())
        db.resetCosts()
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert islUtils.getIslInfo(islToBreak).get().state == IslChangeType.DISCOVERED
        }

        where:
        data << [
                [
                        breakForward: true,
                        breakReverse: false,
                        pingInput: new PingInput()
                ],
                [
                        breakForward: false,
                        breakReverse: true,
                        pingInput: new PingInput()
                ],
                [
                        breakForward: true,
                        breakReverse: true,
                        pingInput: new PingInput()
                ],
                //TODO(rtretiak): below are ignored due to #1416
//                [
//                        breakForward: true,
//                        breakReverse: false,
//                        pingInput: new PingInput((getDiscoveryInterval() + 1) * 1000)
//                ],
//                [
//                        breakForward: false,
//                        breakReverse: true,
//                        pingInput: new PingInput((getDiscoveryInterval() + 1) * 1000)
//                ],
//                [
//                        breakForward: true,
//                        breakReverse: true,
//                        pingInput: new PingInput((getDiscoveryInterval() + 1) * 1000)
//                ]
        ]
        description = "${data.breakForward ? "forward" : ""}${data.breakForward && data.breakReverse ? " and " : ""}" +
                "${data.breakReverse ? "reverse" : ""} path with ${data.pingInput.timeoutMillis}ms" +
                " timeout"

        expectedPingResult = new PingOutputBuilder()
                .forward(new UniFlowPingOutput(
                    pingSuccess: !data.breakForward,
                    error: data.breakForward ? "No ping for reasonable time" : null))
                .reverse(new UniFlowPingOutput(
                    pingSuccess: !data.breakReverse,
                    error: data.breakReverse ? "No ping for reasonable time" : null))
                .error(null).build()
    }

    def "Able to ping a single-switch flow"() {
        given: "A single-switch flow"
        def sw = nonCentecSwitches().first()
        def flow = flowHelper.singleSwitchFlow(sw)
        northboundService.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getFlowStatus(flow.id).status == FlowState.UP }

        when: "Ping the flow"
        def response = northboundService.pingFlow(flow.id, new PingInput())

        then: "The flow is pingable"
        response.forward.pingSuccess
        response.reverse.pingSuccess

        and: "No errors are present"
        !response.error
        !response.forward.error
        !response.reverse.error

        and: "Remove the flow"
        flowHelper.deleteFlow(flow.id)
    }

    def "Verify error if try to ping with wrong flowId"() {
        when: "Send ping request with non-existing flowId"
        def wrongFlowId = "nonexistent"
        def response = northboundService.pingFlow(wrongFlowId, new PingInput())

        then: "Receive error response"
        with(response) {
            flowId == wrongFlowId
            !forward
            !reverse
            error == "Can't read flow $wrongFlowId: Flow pair is incomplete: FORWARD is missing and REVERSE is missing"
        }
    }

    /**
     * Returns all switch combinations with unique description, excluding single-switch combinations,
     * combinations with Centec switches and OF_12 switches
     */
    def getOfSwitchCombinations() {
        def nbSwitches = northboundService.getActiveSwitches()
        return [nbSwitches, nbSwitches].combinations()
            .findAll { src, dst ->
                //exclude single-switch flows
                src != dst &&
                //pings are disabled for OF version < 1.3
                [toSwitch(src), toSwitch(dst)].every { it.ofVersion != "OF_12" } &&
                //known issue: pings on Centects do not work
                [src, dst].every { !it.description.contains("Centec") }
            }
            //pick all unique switch description-OfVersion pairs
            .unique { [it*.description.sort(), it.collect{ toSwitch(it) }*.ofVersion.sort()] }
            .collect { nbSrcSwitch, nbDstSwitch ->
                [toSwitch(nbSrcSwitch), toSwitch(nbDstSwitch)]
            }
    }

    def nonCentecSwitches() {
        northboundService.getActiveSwitches()
             .findAll { !it.description.contains("Centec") }
             .collect { toSwitch(it) }
    }

    Switch toSwitch(SwitchInfoData nbSwitch) {
        topology.activeSwitches.find { it.dpId == nbSwitch.switchId }
    }

    def getDiscoveryInterval() {
        discoveryInterval
    }
}
