package org.openkilda.functionaltests.spec.northbound.flows

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static org.junit.Assume.assumeTrue
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.flows.PingInput
import org.openkilda.northbound.dto.flows.PingOutput.PingOutputBuilder
import org.openkilda.northbound.dto.flows.UniFlowPingOutput
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.topology.TopologyEngineService
import org.openkilda.testing.tools.IslUtils

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Unroll

class FlowPingSpec extends BaseSpecification {
    @Autowired
    TopologyDefinition topology
    @Autowired
    TopologyEngineService topologyEngineService
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
    
    def setupOnce() {
        //TODO(rtretiak): need to exclude Accton switches for hardware env. depends on #1027
        //TODO(rtretiak): add hw-only test to verify that pings are disabled for Acctons
        requireProfiles("virtual")
    }

    @Unroll("Able to ping a flow with vlan between switches #srcSwitch.ofVersion - #dstSwitch.ofVersion")
    def "Able to ping a flow with vlan"(Switch srcSwitch, Switch dstSwitch) {
        given: "A flow with random vlan"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        northboundService.addFlow(flow)
        Wrappers.wait(3) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }

        when: "Ping the flow"
        def response = northboundService.pingFlow(flow.id, new PingInput(1000))

        then: "Ping is successfull"
        response.forward.pingSuccess
        response.reverse.pingSuccess

        and: "No errors"
        !response.error
        !response.forward.error
        !response.reverse.error

        and: "remove flow"
        northboundService.deleteFlow(flow.id)

        where:
        [srcSwitch, dstSwitch] << ofSwitchCombinations
        //TODO(rtretiak): Cycle over Switch manufacturers too (requires #1027)
    }

    @Unroll("Able to ping a flow with no vlan between switches #srcSwitch.ofVersion - #dstSwitch.ofVersion")
    def "Able to ping a flow with no vlan"(Switch srcSwitch, Switch dstSwitch) {
        given: "A flow with no vlan"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.source.vlanId = 0
        flow.destination.vlanId = 0
        northboundService.addFlow(flow)
        Wrappers.wait(3) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }

        when: "Ping the flow"
        def response = northboundService.pingFlow(flow.id, new PingInput(1000))

        then: "Ping is successfull"
        response.forward.pingSuccess
        response.reverse.pingSuccess

        and: "No errors"
        !response.error
        !response.forward.error
        !response.reverse.error

        and: "remove flow"
        northboundService.deleteFlow(flow.id)

        where:
        [srcSwitch, dstSwitch] << ofSwitchCombinations
        //TODO(rtretiak): Cycle over Switch manufacturers too (requires #1027)
    }

    @Unroll("Flow ping can detect broken #description path")
    def "Flow ping can detect broken path"() {
        given: "A flow with at least 1 a-switch link"
        def switches = topology.getActiveSwitches()
        List<List<PathNode>> allPaths = []
        List<PathNode> aswitchPath
        //select src and dst switches that have an a-switch path
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            allPaths = topologyEngineService.getPaths(src.dpId, dst.dpId)*.path
            aswitchPath = allPaths.find { pathHelper.getInvolvedIsls(it).find { it.aswitch } }
            aswitchPath
        } ?: assumeTrue("Wasn't able to find suitable switch pair", false)
        //make a-switch path the most preferable
        allPaths.findAll { it != aswitchPath }.each { pathHelper.makePathMorePreferable(aswitchPath, it) }
        //build a flow
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        northboundService.addFlow(flow)
        Wrappers.wait(3) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        expectedPingResult.flowId = flow.id
        assert aswitchPath == pathHelper.convert(northboundService.getFlowPath(flow.id))

        when: "Break the flow by removing rules from a-switch"
        def islToBreak = pathHelper.getInvolvedIsls(aswitchPath).find { it.aswitch }
        def rulesToRemove = []
        data.breakForward && rulesToRemove << new ASwitchFlow(islToBreak.aswitch.inPort, islToBreak.aswitch.outPort)
        data.breakReverse && rulesToRemove << new ASwitchFlow(islToBreak.aswitch.outPort, islToBreak.aswitch.inPort)
        lockKeeper.removeFlows(rulesToRemove)

        and: "Ping the flow"
        def response = northboundService.pingFlow(flow.id, new PingInput(1000))

        then: "Ping response properly shows that certain direction is unpingable"
        expect response, sameBeanAs(expectedPingResult)
                .ignoring("forward.latency").ignoring("reverse.latency")

        and: "Restore rules, costs and remove flow"
        lockKeeper.addFlows(rulesToRemove)
        northboundService.deleteFlow(flow.id)
        northboundService.deleteLinkProps(northboundService.getAllLinkProps())
        db.resetCosts()
        Wrappers.wait(discoveryInterval + 1) { islUtils.getIslInfo(islToBreak).get().state == IslChangeType.DISCOVERED }

        where: data << [
                [
                        breakForward: true,
                        breakReverse: false
                ],
                [
                        breakForward: false,
                        breakReverse: true
                ],
                [
                        breakForward: true,
                        breakReverse: true
                ]
        ]
        description = "${data.breakForward ? "forward" : ""}${data.breakForward && data.breakReverse ? " and " : ""}" +
                "${data.breakReverse ? "reverse" : ""}"

        expectedPingResult = new PingOutputBuilder()
                .forward(new UniFlowPingOutput(
                    pingSuccess: !data.breakForward,
                    error: data.breakForward ? "No ping for reasonable time" : null))
                .reverse(new UniFlowPingOutput(
                    pingSuccess: !data.breakReverse,
                    error: data.breakReverse ? "No ping for reasonable time" : null))
                .error(null).build()
    }

    def "Able to ping single-switch flow"() {
        given: "A single-switch flow"
        def sw = topology.activeSwitches.first()
        def flow = flowHelper.singleSwitchFlow(sw)
        northboundService.addFlow(flow)
        Wrappers.wait(3) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }

        when: "Request flow ping for the flow"
        def response = northboundService.pingFlow(flow.id, new PingInput(1000))

        then: "Flow is pingable"
        response.forward.pingSuccess
        response.reverse.pingSuccess

        and: "No errors"
        !response.error
        !response.forward.error
        !response.reverse.error

        and: "remove flow"
        northboundService.deleteFlow(flow.id)
    }

    def "Verify error if try to ping with wrong flowId"() {
        when: "Send ping request with non-existing flowId"
        def wrongFlowId = "nonexistent"
        def response = northboundService.pingFlow(wrongFlowId, new PingInput(1000))

        then: "Receive error response"
        with(response) {
            flowId == wrongFlowId
            !forward
            !reverse
            error == "Can't read flow $wrongFlowId: Flow pair is incomplete: FORWARD is missing and REVERSE is missing"
        }
    }
    
    def getOfSwitchCombinations() {
        return [topology.activeSwitches, topology.activeSwitches].combinations().findAll { src, dst -> src != dst }
                .unique { it.ofVersion.sort() }
    }
}
