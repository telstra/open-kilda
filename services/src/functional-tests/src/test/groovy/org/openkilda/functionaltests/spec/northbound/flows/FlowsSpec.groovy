package org.openkilda.functionaltests.spec.northbound.flows

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Unroll

class FlowsSpec extends BaseSpecification {

    @Autowired
    TopologyDefinition topology
    @Autowired
    FlowHelper flowHelper
    @Autowired
    NorthboundService northboundService

    @Unroll
    def "Able to create a single-switch flow for switch with #sw.ofVersion"() {
        requireProfiles("hardware")

        when: "Create a single-switch flow"
        def flow = flowHelper.singleSwitchFlow(sw)
        northboundService.addFlow(flow)
        assert Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }

        then: "Flow is created with no discrepancies"
        northboundService.validateFlow(flow.id).every { it.discrepancies.empty }

        and: "Switch has no missing or excess rules"
        def switchRules = northboundService.validateSwitchRules(sw.dpId)
        switchRules.excessRules.empty
        switchRules.missingRules.empty

        and: "Delete flow"
        northboundService.deleteFlow(flow.id)

        where:
        sw << getTopology().activeSwitches.unique { it.ofVersion }
    }

    def "Able to create a single-switch flow"() {
        requireProfiles("virtual")

        when: "Create a single-switch flow"
        def sw = topology.activeSwitches.first()
        def flow = flowHelper.singleSwitchFlow(sw)
        northboundService.addFlow(flow)
        assert Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }

        then: "Flow is created with no discrepancies, excluding meter discrepancies"
        northboundService.validateFlow(flow.id).every { direction ->
            direction.discrepancies.findAll { it.field != "meterId" }.empty
        }

        and: "Switch has no missing or excess rules"
        def switchRules = northboundService.validateSwitchRules(sw.dpId)
        switchRules.excessRules.empty
        switchRules.missingRules.empty

        cleanup:
        flow && northboundService.deleteFlow(flow.id)
    }
}