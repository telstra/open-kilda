package org.openkilda.functionaltests.spec.flows

import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.model.FlowDto
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v1.flows.FlowValidationDto

import groovy.util.logging.Slf4j
import org.springframework.web.client.HttpClientErrorException
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

    @Unroll
    def "Flow and switch validation should fail in case of missing rules with #flowConfig configuration"() {
        given: "Two flows with #flowConfig configuration"
        def flowToBreak = (switchPair.src == switchPair.dst) ? flowHelper.singleSwitchFlow(switchPair.src)
                : flowHelper.randomFlow(switchPair)
        def intactFlow = (switchPair.src == switchPair.dst) ? flowHelper.singleSwitchFlow(switchPair.src)
                : flowHelper.randomFlow(switchPair)

        flowHelper.addFlow(flowToBreak)
        flowHelper.addFlow(intactFlow)

        and: "Both flows have the same switches in path"
        def damagedFlowSwitches = pathHelper.getInvolvedSwitches(flowToBreak.id)*.dpId
        def intactFlowSwitches = pathHelper.getInvolvedSwitches(intactFlow.id)*.dpId
        assert damagedFlowSwitches.equals(intactFlowSwitches)

        when: "#flowType flow rule from first flow on #switchNo switch gets deleted"
        FlowDto damagedFlow = flowType == "forward" ? database.getFlow(flowToBreak.id).left :
                database.getFlow(flowToBreak.id).right
        SwitchId damagedSwitch = damagedFlowSwitches[item]
        northbound.deleteSwitchRules(damagedSwitch, damagedFlow.cookie)

        then: "Intact flow should be validated successfully"
        def intactFlowValidation = northbound.validateFlow(intactFlow.id)
        intactFlowValidation.each { direction ->
            assert direction.discrepancies.empty
            assert direction.asExpected
        }

        and: "Damaged #flowType flow validation should fail, while other direction should be validated successfully"
        def brokenFlowValidation = northbound.validateFlow(flowToBreak.id)
        brokenFlowValidation.findAll { it.discrepancies.empty && it.asExpected }.size() == 1
        def damagedDirection = brokenFlowValidation.findAll { !it.discrepancies.empty && !it.asExpected }
        damagedDirection.size() == 1

        and: "Flow rule discrepancy should contain dpID of the affected switch and cookie of the damaged flow"
        def rules = findRulesDiscrepancies(damagedDirection[0])
        rules.size() == 1
        rules[damagedSwitch.toString()] == damagedFlow.cookie.toString()

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
        flowConfig      | switchPair                                        | item | switchNo | flowType
        "single switch" | getTopologyHelper().getSingleSwitchPair()         | 0    | "single" | "forward"
        "single switch" | getTopologyHelper().getSingleSwitchPair()         | 0    | "single" | "reverse"
        "neighbouring"  | getTopologyHelper().getNeighboringSwitchPair()    | 0    | "first"  | "forward"
        "neighbouring"  | getTopologyHelper().getNeighboringSwitchPair()    | 0    | "first"  | "reverse"
        "neighbouring"  | getTopologyHelper().getNeighboringSwitchPair()    | 1    | "last"   | "forward"
        "neighbouring"  | getTopologyHelper().getNeighboringSwitchPair()    | 1    | "last"   | "reverse"
        "transit"       | getTopologyHelper().getNotNeighboringSwitchPair() | 0    | "first"  | "forward"
        "transit"       | getTopologyHelper().getNotNeighboringSwitchPair() | 0    | "first"  | "reverse"
        "transit"       | getTopologyHelper().getNotNeighboringSwitchPair() | 1    | "middle" | "forward"
        "transit"       | getTopologyHelper().getNotNeighboringSwitchPair() | 1    | "middle" | "reverse"
        "transit"       | getTopologyHelper().getNotNeighboringSwitchPair() | -1   | "last"   | "forward"
        "transit"       | getTopologyHelper().getNotNeighboringSwitchPair() | -1   | "last"   | "reverse"
    }

    @Unroll
    def "Unable to #action a non-existent flow"() {
        when: "Trying to #action a non-existent flow"
        northbound."${action}Flow"(NON_EXISTENT_FLOW_ID)

        then: "An error is received (404 code)"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorMessage == message

        where:
        action        | message
        "get"         | "Can not get flow: Flow $NON_EXISTENT_FLOW_ID not found"
        "reroute"     | "Could not reroute flow: Flow $NON_EXISTENT_FLOW_ID not found"
        "validate"    | "Could not validate flow: Flow $NON_EXISTENT_FLOW_ID not found"
        "synchronize" | "Could not reroute flow: Flow $NON_EXISTENT_FLOW_ID not found"
    }

    /**
     * Parses discrepancies in the flow validation result
     * @param flow - FlowValidationDto
     * @return Map in dpId:cookie format
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
