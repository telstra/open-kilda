package org.openkilda.functionaltests.spec.northbound.flows

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.extension.fixture.rule.CleanupSwitches
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import spock.lang.Ignore

@CleanupSwitches
@Ignore("Unstable. Under investigation") //TODO(rtretiak): To investigate.
class FlowsSpec extends BaseSpecification {

    def "Removing flow while it is still in progress of being set up should not cause rule discrepancies"() {
        given: "A potential flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        def paths = database.getPaths(srcSwitch.dpId, dstSwitch.dpId)*.path
        def switches = pathHelper.getInvolvedSwitches(paths.min { pathHelper.getCost(it) })

        when: "Init creation of a new flow"
        northbound.addFlow(flow)

        and: "Immediately remove the flow"
        northbound.deleteFlow(flow.id)

        then: "All related switches have no discrepancies in rules"
        Wrappers.wait(WAIT_OFFSET) { switches.each { verifySwitchRules(it.dpId) } }
    }
}
