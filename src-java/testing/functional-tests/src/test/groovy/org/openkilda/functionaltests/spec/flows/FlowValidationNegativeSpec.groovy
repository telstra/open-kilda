package org.openkilda.functionaltests.spec.flows

import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.messaging.payload.flow.FlowState.UP
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.SwitchRulesFactory
import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.error.MessageError
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.northbound.dto.v1.flows.FlowValidationDto

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

@Slf4j
@Narrative("""The specification covers the following scenarios:
              -- Deleting flow rule from a switch and check if switch and flow validation fails.
              -- Failed switch validation should not cause validation errors for flows with all rules in place.
              Test case permutations (full-factored):
                 - forward and reverse flows
                 - ingress, transit and egress switches
                 - Single switch, two switch and three+ switch flow spans.
            """)

class FlowValidationNegativeSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    FlowFactory flowFactory
    @Autowired
    @Shared
    SwitchRulesFactory switchRulesFactory


    @IterationTag(tags = [SMOKE], iterationNameRegex = /reverse/)
    def "Flow and switch validation should fail in case of missing rules with #flowConfig configuration [#flowType]"() {
        given: "Two flows with #flowConfig configuration"
        def flowToBreak = flowFactory.getRandom(switchPair, false)
        def intactFlow = flowFactory.getRandom(
                switchPair, false, UP, flowToBreak.occupiedEndpoints())

        and: "Both flows have the same switches in path"
        def damagedFlowSwitches = flowToBreak.retrieveAllEntityPaths().getInvolvedSwitches()
        def intactFlowSwitches = intactFlow.retrieveAllEntityPaths().getInvolvedSwitches()
        assert damagedFlowSwitches == intactFlowSwitches

        when: "#flowType flow rule from first flow on #switchNo switch gets deleted"
        def cookieToDelete = flowType == "forward" ? flowToBreak.retrieveDetailsFromDB().forwardPath.cookie.value :
                flowToBreak.retrieveDetailsFromDB().reversePath.cookie.value
        SwitchId damagedSwitch = damagedFlowSwitches[item]
        def swRules = switchRulesFactory.get(damagedSwitch)
        swRules.delete(cookieToDelete)

        then: "Intact flow should be validated successfully"
        intactFlow.validateAndCollectDiscrepancies().isEmpty()

        and: "Damaged #flowType flow validation should fail, while other direction should be validated successfully"
        flowToBreak.validateAndCollectDiscrepancies().size() == 1
        def damagedDirection = flowToBreak.validate().findAll { !it.discrepancies.empty && !it.asExpected }

        and: "Flow rule discrepancy should contain dpID of the affected switch and cookie of the damaged flow"
        def rules = findRulesDiscrepancies(damagedDirection[0])
        rules.size() == 1
        rules[damagedSwitch.toString()] == cookieToDelete.toString()

        and: "Affected switch should have one missing rule with the same cookie as the damaged flow"
        def switchSynchronizationResult = switchHelper.synchronizeAndCollectFixedDiscrepancies(damagedSwitch).get()
        switchSynchronizationResult.getRules().getMissing() == [cookieToDelete]

        and: "There should be no excess rules on the affected switch"
        switchSynchronizationResult.getRules().getExcess().isEmpty()

        and: "Validation of non-affected switches (if any) should succeed"
        if (damagedFlowSwitches.size() > 1) {
            def nonAffectedSwitches = damagedFlowSwitches.findAll { it != damagedSwitch }
            switchHelper.synchronizeAndCollectFixedDiscrepancies(nonAffectedSwitches).isEmpty()
        }

        where:
        flowConfig      | switchPair                                   | item | switchNo | flowType
        "single switch" | switchPairs.singleSwitch().random()          | 0    | "single" | "forward"
        "single switch" | switchPairs.singleSwitch().random()          | 0    | "single" | "reverse"
        "neighbouring"  | switchPairs.all().neighbouring().random()    | 0    | "first"  | "forward"
        "neighbouring"  | switchPairs.all().neighbouring().random()    | 0    | "first"  | "reverse"
        "neighbouring"  | switchPairs.all().neighbouring().random()    | 1    | "last"   | "forward"
        "neighbouring"  | switchPairs.all().neighbouring().random()    | 1    | "last"   | "reverse"
        "transit"       | switchPairs.all().nonNeighbouring().random() | 0    | "first"  | "forward"
        "transit"       | switchPairs.all().nonNeighbouring().random() | 0    | "first"  | "reverse"
        "transit"       | switchPairs.all().nonNeighbouring().random() | 1    | "middle" | "forward"
        "transit"       | switchPairs.all().nonNeighbouring().random() | 1    | "middle" | "reverse"
        "transit"       | switchPairs.all().nonNeighbouring().random() | -1   | "last"   | "forward"
        "transit"       | switchPairs.all().nonNeighbouring().random() | -1   | "last"   | "reverse"
    }

    def "Unable to #data.description a non-existent flow"() {
        when: "Trying to #action a non-existent flow"
        data.operation.call()

        then: "An error is received (404 code)"
        def t = thrown(HttpClientErrorException)
        t.rawStatusCode == 404
        verifyAll(t.responseBodyAsString.to(MessageError)) {
            errorMessage == data.message
            errorDescription == data.errorDescr
        }

        where:
        data << [
                [
                        description: "get",
                        operation: { getNorthboundV2().getFlow(NON_EXISTENT_FLOW_ID) },
                        message: "Can not get flow: Flow $NON_EXISTENT_FLOW_ID not found",
                        errorDescr: "Flow not found"
                ],
                [
                        description: "reroute",
                        operation: { getNorthboundV2().rerouteFlow(NON_EXISTENT_FLOW_ID) },
                        message: "Could not reroute flow",
                        errorDescr: "Flow $NON_EXISTENT_FLOW_ID not found"
                ],
                [
                        description: "validate",
                        operation: { getNorthbound().validateFlow(NON_EXISTENT_FLOW_ID) },
                        message: "Could not validate flow: Flow $NON_EXISTENT_FLOW_ID not found",
                        errorDescr: "Receiving rules operation in FlowValidationFsm"
                ],
                [
                        description: "synchronize",
                        operation: { getNorthbound().synchronizeFlow(NON_EXISTENT_FLOW_ID) },
                        message: "Could not sync flow",
                        errorDescr: "Flow $NON_EXISTENT_FLOW_ID not found"
                ],
                [
                        description: "get",
                        operation: { getNorthbound().getFlow(NON_EXISTENT_FLOW_ID) },
                        message: "Can not get flow: Flow $NON_EXISTENT_FLOW_ID not found",
                        errorDescr: "Flow not found"
                ],
                [
                        description: "reroute",
                        operation: { getNorthbound().rerouteFlow(NON_EXISTENT_FLOW_ID) },
                        message: "Could not reroute flow",
                        errorDescr: "Flow $NON_EXISTENT_FLOW_ID not found"
                ]
        ]
    }

    def "Able to detect discrepancies for a flow with protected path"() {
        when: "Create a flow with protected path"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(2).random()
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(true)
                .build().create()

        then: "Flow with protected path is created"
        flow.retrieveAllEntityPaths().getPathNodes(Direction.FORWARD, true)

        and: "Validation of flow with protected path must be successful"
        flow.validateAndCollectDiscrepancies().isEmpty()

        when: "Delete rule of protected path on the srcSwitch"
        def flowPathInfo = flow.retrieveAllEntityPaths()
        def protectedPath = flowPathInfo.getPathNodes(Direction.FORWARD, true)
        def swRules = switchRulesFactory.get(switchPair.src.dpId)
        def rules = swRules.getRules().findAll { !new Cookie(it.cookie).serviceFlag }
        def ruleToDelete = rules.find {
            it.match.inPort == protectedPath[0].portNo.toString()
        }.cookie
        swRules.delete(ruleToDelete)

        then: "Flow validate detects discrepancies"
        //TODO(andriidovhan) try to extend this test when the issues/2302 is fixed
        def validateFlow = flow.validateAndCollectDiscrepancies()
        assert validateFlow.size() == 1
        validateFlow.toString().contains(ruleToDelete.toString())

        when: "Delete all rules except default on the all involved switches"
        def flowPath = flow.retrieveAllEntityPaths()
        def involvedSwitchIds = flowPath.getInvolvedSwitches()
        involvedSwitchIds.each { switchId ->
            switchHelper.deleteSwitchRules(switchId, DeleteRulesAction.IGNORE_DEFAULTS)
        }

        then: "Flow validate detects discrepancies for all deleted rules"
        flow.validateAndCollectDiscrepancies().size() == 4
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
