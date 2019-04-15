package org.openkilda.functionaltests.spec.flows

import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.model.FlowDto
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v1.flows.FlowValidationDto
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

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
        def flowToBreak = (potentialFlow.src == potentialFlow.dst) ? flowHelper.singleSwitchFlow(potentialFlow.src) 
                : flowHelper.randomFlow(potentialFlow)
        def intactFlow = (potentialFlow.src == potentialFlow.dst) ? flowHelper.singleSwitchFlow(potentialFlow.src) 
                : flowHelper.randomFlow(potentialFlow)

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
        flowConfig      | potentialFlow                         | item | switchNo | flowType
        "single switch" | getTopologyHelper().singleSwitch()    | 0    | "single" | "forward"
        "single switch" | getTopologyHelper().singleSwitch()    | 0    | "single" | "reverse"
        "neighbouring"  | getTopologyHelper().findNeighbors()   | 0    | "first"  | "forward"
        "neighbouring"  | getTopologyHelper().findNeighbors()   | 0    | "first"  | "reverse"
        "neighbouring"  | getTopologyHelper().findNeighbors()   | 1    | "last"   | "forward"
        "neighbouring"  | getTopologyHelper().findNeighbors()   | 1    | "last"   | "reverse"
        "transit"       | getTopologyHelper().findNonNeighbors()| 0    | "first"  | "forward"
        "transit"       | getTopologyHelper().findNonNeighbors()| 0    | "first"  | "reverse"
        "transit"       | getTopologyHelper().findNonNeighbors()| 1    | "middle" | "forward"
        "transit"       | getTopologyHelper().findNonNeighbors()| 1    | "middle" | "reverse"
        "transit"       | getTopologyHelper().findNonNeighbors()| -1   | "last"   | "forward"
        "transit"       | getTopologyHelper().findNonNeighbors()| -1   | "last"   | "reverse"
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

    def "Able to detect discrepancies when a flow is created with protected path"() {
        when: "Create a flow with protected path"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

        then: "Flow with protected path is created"
        northbound.getFlowPath(flow.id).protectedPath

        and: "Validation of flow with protected path must be successful"
        northbound.validateFlow(flow.id).each { direction ->
            assert direction.discrepancies.empty
        }

        when: "Delete rule of protected path on the srcSwitch"
        def flowPathInfo = northbound.getFlowPath(flow.id)
        def protectedPath = flowPathInfo.protectedPath.forwardPath
        def rules = northbound.getSwitchRules(srcSwitch.dpId).flowEntries.findAll {
            !org.openkilda.model.Cookie.isDefaultRule(it.cookie)
        }

        def ruleToDelete = rules.find {
            it.instructions?.applyActions?.flowOutput == protectedPath[0].inputPort.toString() &&
                    it.match.inPort == protectedPath[0].outputPort.toString()
        }.cookie

        northbound.deleteSwitchRules(srcSwitch.dpId, ruleToDelete)

        then: "Flow validate detect discrepancies"
        //TODO(andriidovhan) maybe: the in:1-12 value is incorrect in the 'discrepancies' field
        // and actual filed for protected path == correct cookId
        def responseValidateFlow = northbound.validateFlow(flow.id).findAll { !it.discrepancies.empty }*.discrepancies
        assert responseValidateFlow.size() == 1
        responseValidateFlow[0].expectedValue[0] == ruleToDelete.toString()

        when: "Delete all rules except default on the all involved switches"
        def mainPath = flowPathInfo.forwardPath
        def involvedSwitchIds = (mainPath*.switchId + protectedPath*.switchId).unique()
        involvedSwitchIds.each { switchId ->
            northbound.deleteSwitchRules(switchId, DeleteRulesAction.IGNORE_DEFAULTS)
        }

        then: "Flow validate detects more discrepancies"
        def responseValidateFlow2 = northbound.validateFlow(flow.id).findAll { !it.discrepancies.empty }*.discrepancies
        assert responseValidateFlow2.size() == 4

        and: "Cleanup: delete the flow"
        flowHelper.deleteFlow(flow.id)
    }

    /**
     * Checks if there is no discrepancies in the flow validation results
     * //TODO: Don't skip MeterId discrepancies when OVS 2.10 support is added for virtual envs
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
