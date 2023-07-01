package org.openkilda.functionaltests.spec.flows.haflows

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.SwitchMetersFactory
import org.openkilda.functionaltests.helpers.model.SwitchRulesFactory
import org.openkilda.northbound.dto.v2.haflows.HaFlow
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.helpers.model.SwitchRules.missingRuleCookieIds
import static org.openkilda.testing.Constants.RULES_DELETION_TIME

@Narrative("""Verify that missing HA-Flow rule is detected by switch/flow validations""")

class HaFlowValidationSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    HaFlowHelper haFlowHelper

    @Autowired
    @Shared
    SwitchRulesFactory switchRulesFactory

    @Autowired
    @Shared
    SwitchMetersFactory switchMetersFactory


    @Tidy
    @Tags(SMOKE)
    def "HA-Flow passes validation after creation"() {
        given: "HA-Flow on non-neighbouring switches"
        def swT = topologyHelper.getAllNotNeighbouringSwitchTriplets().shuffled().first()
        def haFlow = haFlowHelper.addHaFlow(haFlowHelper.randomHaFlow(swT))

        when: "Validate HA-Flow"
        def validationResult = haFlowHelper.validate(haFlow.getHaFlowId())

        then: "HA-Flow is validated successfully"
        validationResult.asExpected
        validationResult.getSubFlowValidationResults().every {it.getDiscrepancies().isEmpty()}

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.getHaFlowId())
    }

    @Tidy
    def "HA-Flow validation should fail in case of missing rule on #switchRole switch"() {
        given: "HA-Flow on non-neighbouring switches"
        def swT = topologyHelper.getAllNotNeighbouringSwitchTriplets().shuffled().first()
        def haFlow = haFlowHelper.addHaFlow(haFlowHelper.randomHaFlow(swT))

        when: "Delete HA-Flow rule on switch"
        def swIdToManipulate = switchToManipulate(haFlow)
        def switchRules = switchRulesFactory.get(swIdToManipulate)
        def haFlowRuleToDelete = switchRules.forHaFlow(haFlow).shuffled().first()
        switchRules.delete(haFlowRuleToDelete)

        then: "HA-Flow validation returns deleted rule in 'Discrepancies' section"
        Wrappers.wait(RULES_DELETION_TIME) {
            def missingRules = haFlowHelper.validate(haFlow.getHaFlowId()).getSubFlowValidationResults().collect {it.getDiscrepancies()}.flatten()
            assert missingRules.size() == 1, "We deleted only one rule"
            assert missingRules.get(0).getRule().contains(haFlowRuleToDelete.getCookie().toString())
        }

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.getHaFlowId())

        where:
        switchRole | switchToManipulate
        "shared endpoint"    | { HaFlow flow -> flow.getSharedEndpoint().getSwitchId()}
        "other endpoint"| {HaFlow flow -> flow.getSubFlows().shuffled().first().getEndpoint().getSwitchId()}
        "transit"| {HaFlow flow -> (haFlowHelper.getInvolvedSwitches(flow.getHaFlowId()) -
                (flow.subFlows*.endpoint.switchId + flow.sharedEndpoint.switchId)).first()}
    }

    @Tidy
    def "HA-Flow validation should fail in case of missing meter on #switchRole switch"() {
        given: "HA-Flow on non-neighbouring switches"
        def swT = topologyHelper.getAllNotNeighbouringSwitchTriplets().shuffled().first()
        def haFlow = haFlowHelper.addHaFlow(haFlowHelper.randomHaFlow(swT))

        when: "Delete HA-Flow meter"
        def swIdToManipulate = switchToManipulate(haFlow)
        def switchMeters = switchMetersFactory.get(swIdToManipulate)
        def switchRules = switchRulesFactory.get(swIdToManipulate)
        def haFlowMeterToDelete = switchMeters.forHaFlow(haFlow).first()
        def expectedDeletedSwitchRules = switchRules.relatedToMeter(haFlowMeterToDelete)
        switchMeters.delete(haFlowMeterToDelete)

        then: "HA-Flow validation returns rules related to deleted meter in 'Discrepancies' section"
        Wrappers.wait(RULES_DELETION_TIME) {
            def missingMeters = haFlowHelper.validate(haFlow.getHaFlowId()).getSubFlowValidationResults().collect {it.getDiscrepancies()}.flatten()
            assert missingMeters.size() == expectedDeletedSwitchRules.size()
            assert missingRuleCookieIds(missingMeters) == expectedDeletedSwitchRules.collect {it.getCookie()} as Set
        }

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.getHaFlowId())

        where:
        switchRole | switchToManipulate
        "shared endpoint"    | { HaFlow flow -> flow.getSharedEndpoint().getSwitchId()}
        "other endpoint"| {HaFlow flow -> flow.getSubFlows().shuffled().first().getEndpoint().getSwitchId()}
        "transit"| {HaFlow flow -> (haFlowHelper.getYPoint(flow))}
    }

}
