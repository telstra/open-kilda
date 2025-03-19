package org.openkilda.functionaltests.spec.flows.yflows

import static org.junit.jupiter.api.Assumptions.assumeTrue

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.yflow.YFlowNotFoundExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.FlowDirection
import org.openkilda.functionaltests.helpers.factory.YFlowFactory
import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.messaging.payload.flow.FlowState
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID
import static org.openkilda.testing.Constants.RULES_DELETION_TIME

@Narrative("""Verify that missing yFlow rule is detected by switch/flow validations.
And make sure that the yFlow rule can be installed by syncSw/syncYFlow endpoints.""")
class YFlowValidationSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    YFlowFactory yFlowFactory

    def "Y-Flow validation should fail in case of missing Y-Flow shared rule (#data.description)"() {
        given: "Existing Y-Flow"
        assumeTrue(data.swT != null, "These cases cannot be covered on given topology.")

        def yFlow = yFlowFactory.getRandom(data.swT)

        when: "Delete shared Y-Flow rule"
        def swToManipulate = data.swT.shared
        def sharedMeter = database.getYFlow(yFlow.yFlowId).sharedEndpointMeterId
        def sharedRules = swToManipulate.rulesManager.getRules().findAll {
            it.instructions.goToMeter == sharedMeter.value
        }
        sharedRules.each { swToManipulate.rulesManager.delete(it.cookie) }

        then: "Y-Flow validate detects discrepancies"
        Wrappers.wait(RULES_DELETION_TIME) { assert !yFlow.validate().asExpected }

        and: "Simple flow validation detects discrepancies"
        yFlow.subFlows.each {
            northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
        }

        and: "Switch validation detects missing Y-Flow rule"
        with(swToManipulate.validate().rules) {
            it.misconfigured.empty
            it.excess.empty
            it.missing.size() == sharedRules.size()
            it.missing*.cookie.sort() == sharedRules*.cookie.sort()
        }

        when: "Synchronize the shared switch"
        swToManipulate.synchronize(false)

        then: "Y-Flow/subFlow passes flow validation"
        yFlow.validate().asExpected
        yFlow.subFlows.each {
            northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
        }

        and: "Switch passes validation"
        swToManipulate.validate().isAsExpected()

        where:
        data << [
                [
                        description: "multiSwitch Y-Flow",
                        swT: switchTriplets.all().withAllDifferentEndpoints().random()
                ],
                [
                        description: "one-switch Y-Flow",
                        swT: switchTriplets.all(false, true).singleSwitch().random()

                ]
        ]
    }

    @Tags(LOW_PRIORITY)
    def "Y-Flow/subFlow validation should fail in case of missing subFlow rule (#data.description)"() {
        given: "Existing Y-Flow"
        def yFlow = yFlowFactory.getRandom(data.swT)

        when: "Delete reverse rule of subFlow_1"
        def subFl_1 = yFlow.subFlows[0]
        def subFl_2 = yFlow.subFlows[1]
        def swToManipulate = data.swT.ep1
        def cookieToDelete = database.getFlow(subFl_1.flowId).reversePath.cookie.value
        swToManipulate.rulesManager.delete(cookieToDelete)

        then: "Y-Flow is not valid"
        def discrepancies = yFlow.validateAndCollectDiscrepancy()
        !discrepancies.asExpected

        and: "Discrepancies has been detected for broken sub-flow REVERSE direction"
        assert discrepancies.subFlowsDiscrepancies.find { it.subFlowId == subFl_1.flowId }.flowDiscrepancies.get(FlowDirection.REVERSE)

        and: "No discrepancies has been detected for broken sub-flow FORWARD direction"
        assert !discrepancies.subFlowsDiscrepancies.find { it.subFlowId == subFl_1.flowId }.flowDiscrepancies.get(FlowDirection.FORWARD)

        and: "No discrepancies has been detected for another sub-flow"
        assert !discrepancies.subFlowsDiscrepancies.find { it.subFlowId != subFl_1.flowId }

        and: "Simple flow validation detects discrepancies for the subFlow_1 REVERSE direction only"
        verifyAll(northbound.validateFlow(subFl_1.flowId)) { subFlow1 ->
            subFlow1.find { it.direction == Direction.FORWARD.value }.discrepancies.empty
            subFlow1.find { it.direction == Direction.FORWARD.value }.asExpected

            subFlow1.find { it.direction == Direction.REVERSE.value }.discrepancies
            !subFlow1.find { it.direction == Direction.REVERSE.value }.asExpected
        }

        northbound.validateFlow(subFl_2.flowId).each { direction -> assert direction.asExpected }

        and: "Switch validation detects missing reverse rule only for the subFlow_1"
        with(swToManipulate.validate().rules) {
            it.misconfigured.empty
            it.excess.empty
            it.missing.size() == 1
            it.missing[0].getCookie() == cookieToDelete
        }

        when: "Sync Y-Flow"
        yFlow.sync()
        yFlow.waitForBeingInState(FlowState.UP)

        then: "Y-Flow/subFlow passes flow validation"
        def discrepanciesAfterSync = yFlow.validateAndCollectDiscrepancy()
        discrepanciesAfterSync.asExpected && discrepanciesAfterSync.subFlowsDiscrepancies.isEmpty()
        yFlow.subFlows.each {
            northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
        }

        and: "Switches pass validation"
        swToManipulate.validate().isAsExpected()

        where:
        data << [
                [
                        description: "multiSwitch Y-Flow",
                        swT: switchTriplets.all().withAllDifferentEndpoints().random()

                ],
                [
                        description: "one-switch Y-Flow",
                        swT: switchTriplets.all(false, true).singleSwitch().random()

                ]
        ]
    }

    def "Unable to #data.action a non-existent Y-Flow"() {
        when: "Invoke a certain action for a non-existent Y-Flow"
        data.method()

        then: "Human readable error is returned"
        def e = thrown(HttpClientErrorException)
        new YFlowNotFoundExpectedError("Could not ${data.actionInMsg} y-flow", NON_EXISTENT_FLOW_ID).matches(e)

        where:
        data << [
                [
                        action     : "validate",
                        actionInMsg: "validate",
                        method     : { northboundV2.validateYFlow(NON_EXISTENT_FLOW_ID) }
                ],
                [
                        action     : "synchronize",
                        actionInMsg: "sync",
                        method     : { northboundV2.synchronizeYFlow(NON_EXISTENT_FLOW_ID) }
                ]
        ]
    }
}
