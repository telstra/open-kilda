package org.openkilda.functionaltests.spec.switches

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.helpers.Wrappers

class MultiTableRulesSpec  extends HealthCheckSpecification {

    def "Switch migration to multi table mode and vice-versa leave no discrepancies in default rules"() {
        given: "Active switch without multi-table mode"
        def sw = topology.activeSwitches[0]
        def properties = northbound.getSwitchProperties(sw.dpId)
        assert !properties.multiTable

        when: "Change switch mode to multi-table mode"
        properties.multiTable = true
        northbound.updateSwitchProperties(sw.dpId, properties)



        then: "No discrepancy found"
        Wrappers.wait(WAIT_OFFSET) {
            def validationResult = northbound.validateSwitch(sw.dpId)

            validationResult.rules.missing.empty
            validationResult.rules.excess.empty
            validationResult.rules.misconfigured.empty
        }


        when: "Single Table mode is enabled"
        properties.multiTable = false
        northbound.updateSwitchProperties(sw.dpId, properties)

        then: "No discrepancy found"
        Wrappers.wait(WAIT_OFFSET) {
            def validationResult = northbound.validateSwitch(sw.dpId)

            validationResult.rules.missing.empty
            validationResult.rules.excess.empty
            validationResult.rules.misconfigured.empty
        }
        cleanup:
        Wrappers.wait(WAIT_OFFSET) {
            def validationResult = northbound.synchronizeSwitch(sw.dpId, true)

            validationResult.rules.missing.empty
            validationResult.rules.excess.empty
            validationResult.rules.misconfigured.empty
        }
    }
}
