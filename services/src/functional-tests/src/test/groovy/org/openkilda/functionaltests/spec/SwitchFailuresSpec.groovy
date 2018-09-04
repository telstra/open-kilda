package org.openkilda.functionaltests.spec

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.aswitch.ASwitchService
import org.openkilda.testing.service.aswitch.model.ASwitchFlow
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.tools.IslUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Narrative

import static org.junit.Assume.assumeTrue

@Narrative("""
This spec verifies different situations when Kilda switches suddenly disconnect from the controller.
Note: For now it is only runnable on virtual env due to no ability to disconnect hardware switches
""")
class SwitchFailuresSpec extends BaseSpecification {
    @Value('${spring.profiles.active}')
    String profile
    @Value('${floodlight.endpoint}')
    String floodlightEndpoint //TODO(rtretiak): For correct hardware impl should point to actual 6653 port instead
    @Value('${discovery.timeout}')
    int discoveryTimeout
    @Value('${reroute.delay}')
    int rerouteDelay

    @Autowired
    TopologyDefinition topology
    @Autowired
    FlowHelper flowHelper
    @Autowired
    NorthboundService northboundService
    @Autowired
    ASwitchService aSwitchService
    @Autowired
    IslUtils islUtils
    @Autowired
    PathHelper pathHelper

    def "ISL is still able to properly fail even after switches where reconnected"() {
        assumeTrue("Unable to run on hardware topology. Missing ability to disconnect a certain switch",
                profile == "virtual")

        given: "A flow"
        def isl = topology.getIslsForActiveSwitches().find {it.aswitch && it.dstSwitch}
        def flow = flowHelper.randomFlow(isl.srcSwitch, isl.dstSwitch)
        northboundService.addFlow(flow)
        Wrappers.wait(3) {northboundService.getFlowStatus(flow.id).status == FlowState.UP}

        when: "Two neighbouring switches of the flow go down simultaneously"
        aSwitchService.knockoutSwitch(isl.srcSwitch.dpId.toString())
        aSwitchService.knockoutSwitch(isl.dstSwitch.dpId.toString())

        and: "ISL between those switches looses connection"
        aSwitchService.removeFlows([isl, islUtils.reverseIsl(isl)]
                .collect {new ASwitchFlow(it.aswitch.inPort, it.aswitch.outPort)})

        and: "Switches go back UP"
        aSwitchService.reviveSwitch(isl.srcSwitch.dpId.toString(), floodlightEndpoint)
        aSwitchService.reviveSwitch(isl.dstSwitch.dpId.toString(), floodlightEndpoint)

        then: "Flow goes down OR changes path to avoid failed ISL" //depends whether there are alt paths available
        Wrappers.wait(discoveryTimeout + rerouteDelay + 1) {
            def currentPath = PathHelper.convert(northboundService.getFlowPath(flow.id))
            !pathHelper.getInvolvedIsls(currentPath).contains(isl) ||
                    northboundService.getFlowStatus(flow.id).status == FlowState.DOWN
        }

        and: "Cleanup, restore connection, remove flow"
        aSwitchService.addFlows([isl, islUtils.reverseIsl(isl)]
                .collect {new ASwitchFlow(it.aswitch.inPort, it.aswitch.outPort)})
        northboundService.deleteFlow(flow.id)
    }
}
