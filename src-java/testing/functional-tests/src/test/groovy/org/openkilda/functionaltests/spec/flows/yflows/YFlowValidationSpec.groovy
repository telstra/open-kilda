package org.openkilda.functionaltests.spec.flows.yflows

import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.payload.flow.FlowState

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("""Verify that missing yFlow rule is detected by switch/flow validations.
And make sure that the yFlow rule can be installed by syncSw/syncYFlow endpoints.""")
class YFlowValidationSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    YFlowHelper yFlowHelper

    @Tidy
    def "Y-Flow validation should fail in case of missing y-flow shared rule (#data.description)"() {
        given: "Existing y-flow"
        def swT = topologyHelper.switchTriplets[0]
        def yFlowRequest = data.yFlow(swT)
        def yFlow = yFlowHelper.addYFlow(yFlowRequest)

        when: "Delete shared y-flow rule"
        def swIdToManipulate = swT.shared.dpId
        def sharedMeterId = northbound.getAllMeters(swIdToManipulate).meterEntries.findAll {
            it.meterId > MAX_SYSTEM_RULE_METER_ID
        }.max { it.meterId }.meterId
        def sharedRules = northbound.getSwitchRules(swIdToManipulate).flowEntries.findAll {
            it.instructions.goToMeter == sharedMeterId
        }
        sharedRules.each { northbound.deleteSwitchRules(swIdToManipulate, it.cookie) }

        then: "Y-Flow validate detects discrepancies"
        Wrappers.wait(RULES_DELETION_TIME) { assert !northboundV2.validateYFlow(yFlow.YFlowId).asExpected }

        and: "Simple flow validation detects discrepancies"
        yFlow.subFlows.each {
            northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
        }

        and: "Switch validation detects missing y-flow rule"
        with(northbound.validateSwitch(swIdToManipulate).rules) {
            it.misconfigured.empty
            it.excess.empty
            it.missing.size() == sharedRules.size()
            it.missing.sort() == sharedRules*.cookie.sort()
        }

        when: "Synchronize the shared switch"
        northbound.synchronizeSwitch(swIdToManipulate, false)

        then: "Y-Flow/subFlow passes flow validation"
        northboundV2.validateYFlow(yFlow.YFlowId).asExpected
        yFlow.subFlows.each {
            northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
        }

        and: "Switch passes validation"
        northbound.validateSwitch(swIdToManipulate).verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])

        cleanup:
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)

        where:
        data << [
                [
                        description: "multiSwtich y-flow",
                        yFlow: { SwitchTriplet swTriplet -> yFlowHelper.randomYFlow(swTriplet) }
                ],
                [
                        description: "one-switch y-flow",
                        yFlow: { SwitchTriplet swTriplet -> yFlowHelper.singleSwitchYFlow(swTriplet.shared) }
                ]
        ]
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "Y-Flow/flow validation should fail in case of missing subFlow rule (#data.description)"() {
        given: "Existing y-flow"
        def swT = topologyHelper.switchTriplets[0]
        def yFlowRequest = data.yFlow(swT)
        def yFlow = yFlowHelper.addYFlow(yFlowRequest)

        when: "Delete reverse rule of subFlow_1"
        def subFl_1 = yFlow.subFlows[0]
        def subFl_2 = yFlow.subFlows[1]
        def swIdToManipulate = swT.ep2.dpId
        def cookieToDelete = database.getFlow(subFl_1.flowId).reversePath.cookie.value
        northbound.deleteSwitchRules(swIdToManipulate, cookieToDelete)

        then: "Y-Flow validate detects discrepancies"
        def validateYflowInfo = northboundV2.validateYFlow(yFlow.YFlowId)
        !validateYflowInfo.asExpected
        !validateYflowInfo.subFlowValidationResults.findAll { it.flowId == subFl_1.flowId }
                .findAll { !it.discrepancies.empty }.empty
        validateYflowInfo.subFlowValidationResults.findAll { it.flowId == subFl_2.flowId }
                .findAll { !it.discrepancies.empty }.empty

        and: "Simple flow validation detects discrepancies for the subFlow_1 only"
        !northbound.validateFlow(subFl_1.flowId).findAll { !it.discrepancies.empty }.empty
        northbound.validateFlow(subFl_2.flowId).each { direction -> assert direction.asExpected }

        and: "Switch validation detects missing reverse rule only for the subFlow_1"
        with(northbound.validateSwitch(swIdToManipulate).rules) {
            it.misconfigured.empty
            it.excess.empty
            it.missing.size() == 1
            it.missing[0] == cookieToDelete
        }

        when: "Sync y-flow"
        northboundV2.synchronizeYFlow(yFlow.YFlowId)

        then: "Y-Flow/subFlow passes flow validation"
        Wrappers.wait(WAIT_OFFSET) {
            northboundV2.getYFlow(yFlow.YFlowId).status == FlowState.UP.toString()
        }
        northboundV2.validateYFlow(yFlow.YFlowId).asExpected
        yFlow.subFlows.each {
            northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
        }

        and: "Switches pass validation"
        northbound.validateSwitch(swIdToManipulate).verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])

        cleanup:
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)

        where:
        data << [
                [
                        description: "multiSwtich y-flow",
                        yFlow: { SwitchTriplet swTriplet -> yFlowHelper.randomYFlow(swTriplet) }
                ],
                [
                        description: "one-switch y-flow",
                        yFlow: { SwitchTriplet swTriplet -> yFlowHelper.singleSwitchYFlow(swTriplet.ep2) }
                ]
        ]
    }

    @Tidy
    def "Unable to #data.action a non-existent y-flow"() {
        when: "Invoke a certain action for a non-existent y-flow"
        data.method()

        then: "Human readable error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.NOT_FOUND
        verifyAll(e.responseBodyAsString.to(MessageError)) {
            errorMessage == "Could not ${data.actionInMsg} y-flow"
            errorDescription == "Y-flow $NON_EXISTENT_FLOW_ID not found"
        }

        where:
        data << [
                [
                        action     : "validate",
                        actionInMsg: "validate",
                        method     : { northboundV2.validateYFlow(NON_EXISTENT_FLOW_ID) }
                ],
                [
                        action     : "synchronize",
                        actionInMsg: "reroute",
                        method     : { northboundV2.synchronizeYFlow(NON_EXISTENT_FLOW_ID) }
                ]
        ]
    }
}
