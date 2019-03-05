package org.openkilda.functionaltests.spec.flows

import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslInfoData
import org.openkilda.messaging.model.FlowDto
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.flows.FlowValidationDto
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.util.logging.Slf4j
import org.junit.Assume
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
        def (src, dest) = switches
        def flowToBreak = (src == dest) ? flowHelper.singleSwitchFlow(src) : flowHelper.randomFlow(src, dest)
        def intactFlow = (src == dest) ? flowHelper.singleSwitchFlow(src) : flowHelper.randomFlow(src, dest)

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
        flowConfig      | switches                     | item | switchNo | flowType
        "single switch" | getSingleSwitch()            | 0    | "single" | "forward"
        "single switch" | getSingleSwitch()            | 0    | "single" | "reverse"
        "neighbouring"  | getNeighbouringSwitches()    | 0    | "first"  | "forward"
        "neighbouring"  | getNeighbouringSwitches()    | 0    | "first"  | "reverse"
        "neighbouring"  | getNeighbouringSwitches()    | 1    | "last"   | "forward"
        "neighbouring"  | getNeighbouringSwitches()    | 1    | "last"   | "reverse"
        "transit"       | getNonNeighbouringSwitches() | 0    | "first"  | "forward"
        "transit"       | getNonNeighbouringSwitches() | 0    | "first"  | "reverse"
        "transit"       | getNonNeighbouringSwitches() | 1    | "middle" | "forward"
        "transit"       | getNonNeighbouringSwitches() | 1    | "middle" | "reverse"
        "transit"       | getNonNeighbouringSwitches() | -1   | "last"   | "forward"
        "transit"       | getNonNeighbouringSwitches() | -1   | "last"   | "reverse"
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

    boolean findDirectLinks(Switch src, Switch dst, List<IslInfoData> links) {
        def connectingLinks = links.findAll { link ->
            (link.source.switchId == src.dpId) && (link.destination.switchId == dst.dpId)
        }
        return connectingLinks.empty
    }

    /**
     * Finds pair of switches with no direct links between them.
     * NOTE: distance between switches is NOT guaranteed
     * @return List < Switch >
     */
    def getNonNeighbouringSwitches() {
        def islInfoData = northbound.getAllLinks()
        def switches = topology.getActiveSwitches()
        def differentSwitches = [switches, switches].combinations().findAll { src, dst -> src.dpId != dst.dpId }

        return differentSwitches.find { src, dst -> findDirectLinks(src, dst, islInfoData) }
    }

    /**
     * Finds pair of swithes with a direct link between them.
     * @return List < Switch >
     */
    def getNeighbouringSwitches() {
        def switches = topology.getActiveSwitches()
        def islInfoData = northbound.getAllLinks()
        return [switches, switches].combinations().unique().find { src, dst -> !findDirectLinks(src, dst, islInfoData) }
    }

    /**
     * Finds a single switch
     * @return List < Switch >    (convenient for randomFlow() method)
     */
    def getSingleSwitch() {
        def switches = topology.getActiveSwitches()
        return [switches.first(), switches.first()]
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
        "synchronize" | "Could not reroute flow: Flow $NON_EXISTENT_FLOW_ID not found"
        "reroute"     | "Could not reroute flow: Flow $NON_EXISTENT_FLOW_ID not found"
        "get"         | "Can not get flow: Flow $NON_EXISTENT_FLOW_ID not found"
        "validate"    | "Could not validate flow: Flow $NON_EXISTENT_FLOW_ID not found"
    }
}
