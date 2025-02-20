package org.openkilda.functionaltests.spec.flows.haflows

import static org.openkilda.functionaltests.extension.tags.Tag.HA_FLOW
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.testing.Constants.RULES_DELETION_TIME

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.factory.HaFlowFactory
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.HaFlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchExtended
import org.openkilda.model.SwitchId

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("""Verify that missing HA-Flow rule is detected by switch/flow validations""")
@Tags([HA_FLOW])
class HaFlowValidationSpec extends HealthCheckSpecification {

    @Shared
    @Autowired
    HaFlowFactory haFlowFactory

    @Tags(SMOKE)
    def "HA-Flow passes validation after creation"() {
        given: "HA-Flow on non-neighbouring switches"
        def swT = switchTriplets.all().nonNeighbouring().random()
        def haFlow = haFlowFactory.getRandom(swT)

        when: "Validate HA-Flow"
        def validationResult = haFlow.validateAndCollectDiscrepancy()

        then: "HA-Flow is validated successfully"
        validationResult.asExpected
        validationResult.subFlowsDiscrepancies.isEmpty()
    }

    def "HA-Flow validation should fail in case of missing rule on #switchRole switch"() {
        given: "HA-Flow on non-neighbouring switches"
        def swT = switchTriplets.all().nonNeighbouring().random()
        def haFlow = haFlowFactory.getRandom(swT)

        when: "Delete HA-Flow rule on switch"
        SwitchExtended swToManipulate = switches.all().findSpecific(switchToManipulate(haFlow) as SwitchId)
        def haFlowRuleToDelete = swToManipulate.rulesManager.forHaFlow(haFlow).shuffled().first()
        swToManipulate.rulesManager.delete(haFlowRuleToDelete)

        then: "HA-Flow validation returns deleted rule in 'Discrepancies' section"
        Wrappers.wait(RULES_DELETION_TIME) {
            def discrepancies = haFlow.validateAndCollectDiscrepancy()
            assert !discrepancies.asExpected
            def missingRules = discrepancies.retrieveAllRulesCookieFromDiscrepancy()
            assert missingRules.size() == 1, "We deleted only one rule"
            assert missingRules.first() == haFlowRuleToDelete.getCookie()
        }

        where:
        switchRole        | switchToManipulate
        "shared endpoint" | { HaFlowExtended flow -> flow.sharedEndpoint.switchId }
        "other endpoint"  | { HaFlowExtended flow -> flow.subFlows.shuffled().first().endpointSwitchId }
        "transit"         | { HaFlowExtended flow ->
            (flow.retrievedAllEntityPaths().getInvolvedSwitches() - (flow.subFlows*.endpointSwitchId + flow.sharedEndpoint.switchId)).first()
        }
    }

    def "HA-Flow validation should fail in case of missing meter on #switchRole switch"() {
        given: "HA-Flow on non-neighbouring switches"
        def swT = switchTriplets.all().nonNeighbouring().random()
        def haFlow = haFlowFactory.getRandom(swT)

        when: "Delete HA-Flow meter"
        SwitchExtended swToManipulate = switches.all().findSpecific(switchToManipulate(haFlow) as SwitchId)

        def haFlowMeterToDelete = swToManipulate.metersManager.forHaFlow(haFlow).first()
        def expectedDeletedSwitchRules = swToManipulate.rulesManager.relatedToMeter(haFlowMeterToDelete)
        swToManipulate.metersManager.delete(haFlowMeterToDelete)

        then: "HA-Flow validation returns rules related to deleted meter in 'Discrepancies' section"
        Wrappers.wait(RULES_DELETION_TIME) {
            def discrepancies = haFlow.validateAndCollectDiscrepancy()
            assert !discrepancies.asExpected
            def missingMeters = discrepancies.retrieveAllRulesCookieFromDiscrepancy()
            assert missingMeters == expectedDeletedSwitchRules.collect { it.getCookie() } as Set
        }

        where:
        switchRole         | switchToManipulate
        "shared endpoint"  | { HaFlowExtended flow -> flow.sharedEndpoint.switchId }
        "other endpoint"   | { HaFlowExtended flow -> flow.subFlows.shuffled().first().endpointSwitchId }
        "transit (YPoint)" | { HaFlowExtended flow ->
            def flowPath = flow.retrievedAllEntityPaths()
            flowPath.sharedPath.path.isPathAbsent() ? flow.sharedEndpoint.switchId : flowPath.sharedPath.path.forward.nodes.nodes.last().switchId
        }
    }

}
