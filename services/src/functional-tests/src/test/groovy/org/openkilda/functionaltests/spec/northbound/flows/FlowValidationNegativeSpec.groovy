package org.openkilda.functionaltests.spec.northbound.flows

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.messaging.model.Flow
import org.openkilda.messaging.model.SwitchId
import org.openkilda.northbound.dto.flows.FlowValidationDto
import org.openkilda.testing.service.database.Database

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Unroll


@Slf4j
@Narrative("""The specification covers the following scenarios:
              -- Deleting flow rule from a switch and check if switch and flow validation fails.
              -- Failed switch validation should not cause validation errors for flows with all rules in place.
              Test case permutations (full-factored):
                 - forward and reverse flows
                 - ingress, transit and egress switches
                 - Single switch, two switch and three+ switch flow spans.
            """)
class FlowValidationNegativeSpec extends BaseSpecification {

    @Autowired
    FlowHelper flowHelper

    @Autowired
    PathHelper pathHelper

    @Autowired
    Database database

    @Unroll
    def "Flow and switch validation should fail in case of missing rules"() {
        given: "Two flows with #flowConfig configuration"
        def (src, dest) = pathHelper.getSwitchPair(distance)
        def flowToBreak = flowHelper.addFlow(flowHelper.randomFlow(src, dest))
        def intactFlow = flowHelper.addFlow(flowHelper.randomFlow(src, dest))

        and: "Both flows have the same switches in path"
        def damagedFlowSwitches = pathHelper.getInvolvedSwitches(flowToBreak.id)*.dpId
        def intactFlowSwitches = pathHelper.getInvolvedSwitches(intactFlow.id)*.dpId
        assert damagedFlowSwitches.size() == distance + 1
        assert damagedFlowSwitches.equals(intactFlowSwitches)

        when: "#flowType flow rule from first flow on #switchNo switch gets deleted"
        Flow damagedFlow = flowType == "forward" ? database.getFlow(flowToBreak.id).left :
                database.getFlow(flowToBreak.id).right
        SwitchId damagedSwitch = damagedFlowSwitches[item]
        northbound.deleteSwitchRules(damagedSwitch, damagedFlow.cookie)

        then: "Intact flow should be validated successfully"
        northbound.validateFlow(intactFlow.id).every { isFlowValid(it) }

        and: "Damaged #flowType flow validation should fail, while other direction should be validated successfully"
        def validationResult = northbound.validateFlow(flowToBreak.id)
        validationResult.findAll { isFlowValid(it) }.size() == 1
        def invalidFlow = validationResult.findAll { !isFlowValid(it) }
        invalidFlow.size() == 1

        and: "Flow rule discrepancy should contain dpID of the affected switch and cookie of the damaged flow"
        def rules = findRulesDiscrepancies(invalidFlow[0])
        rules.size() == 1
        rules[damagedSwitch.toString()] == damagedFlow.cookie.toString()

        and: "Validation of non-affected flow should succeed"
        northbound.validateFlow(intactFlow.id).every { isFlowValid(it) }

        and: "Affected switch should have one missing rule with the same cookie as the damaged flow"
        def switchValidationResult = northbound.validateSwitchRules(damagedSwitch)
        switchValidationResult.missingRules.size() == 1
        switchValidationResult.missingRules[0] == damagedFlow.cookie

        and: "There should be no excess rules on the affected switch"
        switchValidationResult.excessRules.size() == 0

        and: "Validation of non-affected switches (if any) should succeed"
        if (damagedFlowSwitches.size() > 1) {
            def nonAffectedSwitches = damagedFlowSwitches.findAll { it != damagedFlowSwitches[item] }
            assert nonAffectedSwitches.every { sw -> northbound.validateSwitchRules(sw).missingRules.size() == 0 }
            assert nonAffectedSwitches.every { sw -> northbound.validateSwitchRules(sw).excessRules.size() == 0 }
        }

        and: "Delete the flows"
        [flowToBreak.id, intactFlow.id].each { flowHelper.deleteFlow(it) }

        where:
        flowConfig      | distance | item | switchNo | flowType
        "single switch" | 0        | 0    | "single" | "forward"
        "single switch" | 0        | 0    | "single" | "reverse"
        "neighbouring"  | 1        | 0    | "first"  | "forward"
        "neighbouring"  | 1        | 1    | "last"   | "forward"
        "transit"       | 2        | 0    | "first"  | "reverse"
        "transit"       | 2        | 1    | "middle" | "forward"
        "transit"       | 2        | 1    | "middle" | "reverse"
        "transit"       | 2        | -1   | "last"   | "reverse"
    }

    /**
     * Checks if there is no discrepancies in the flow validation results
     * //TODO: Don't skip MeterId discrepancies when virtual env starts supporting the meters.
     * @param flow Flow validation results
     * @return boolean
     */
    boolean isFlowValid(FlowValidationDto flow) {
        if (this.profile.equalsIgnoreCase("virtual")) {
            return flow.discrepancies.findAll { it.field != "meterId" }.empty
        }
        return flow.discrepancies.empty
    }

    /**
     * Parses discrepancies in the flow validation result
     * @param flow - FlowValidationDto
     * @return Map < String , String >  in dpId:cookie format
     */
    Map<String, String> findRulesDiscrepancies(FlowValidationDto flow) {
        def discrepancies = flow.discrepancies.findAll { it.field != "meterId" }
        def cookies = [:]
        discrepancies.each { disc ->
            def dpId = (disc.rule =~ /sw:(.*?),/)[0][1]
            def cookie = (disc.rule =~ /ck:(.*?),/)[0][1]
            cookies[dpId] = cookie
        }
        return cookies
    }
}