package org.openkilda.functionaltests.spec.flows.haflows

import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.payload.flow.FlowState

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("""Verify that missing haFlow rule is detected by switch/flow validations.
And make sure that the haFlow rule can be installed by syncSw/syncHaFlow endpoints.""")
class HaFlowValidationSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    HaFlowHelper haFlowHelper

    @Tidy
    def "HA-Flow validation should fail in case of missing ha-flow shared rule (#data.description)"() {
        given: "Existing HA-flow"
        def swT = topologyHelper.switchTriplets[0]
        def haFlowRequest = data.haFlow(swT)
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)

        when: "Delete shared HA-flow rule"
        def swIdToManipulate = swT.shared.dpId
        def sharedMeterId = northbound.getAllMeters(swIdToManipulate).meterEntries.findAll {
            it.meterId > MAX_SYSTEM_RULE_METER_ID
        }.max { it.meterId }.meterId
        def sharedRules = northbound.getSwitchRules(swIdToManipulate).flowEntries.findAll {
            it.instructions.goToMeter == sharedMeterId
        }
        sharedRules.each { northbound.deleteSwitchRules(swIdToManipulate, it.cookie) }

        then: "HA-Flow validate detects discrepancies"
        Wrappers.wait(RULES_DELETION_TIME) { assert !northboundV2.validateHaFlow(haFlow.haFlowId).asExpected }

        and: "Simple flow validation detects discrepancies"
        haFlow.subFlows.each {
            northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
        }

        and: "Switch validation detects missing HA-flow rule"
        with(northboundV2.validateSwitch(swIdToManipulate).rules) {
            it.misconfigured.empty
            it.excess.empty
            it.missing.size() == sharedRules.size()
            it.missing.sort() == sharedRules*.cookie.sort()
        }

        when: "Synchronize the shared switch"
        northbound.synchronizeSwitch(swIdToManipulate, false)

        then: "HA-Flow/subFlow passes flow validation"
        northboundV2.validateHaFlow(haFlow.haFlowId).asExpected
        haFlow.subFlows.each {
            northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
        }

        and: "Switch passes validation"
        northbound.validateSwitch(swIdToManipulate).verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)

        where:
        data << [
                [
                        description: "multiSwtich HA-flow",
                        haFlow: { SwitchTriplet swTriplet -> haFlowHelper.randomHaFlow(swTriplet) }
                ]
        ]
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "HA-Flow/flow validation should fail in case of missing subFlow rule (#data.description)"() {
        given: "Existing HA-flow"
        def swT = topologyHelper.switchTriplets[0]
        def haFlowRequest = data.haFlow(swT)
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)

        when: "Delete reverse rule of subFlow_1"
        def subFl_1 = haFlow.subFlows[0]
        def subFl_2 = haFlow.subFlows[1]
        def swIdToManipulate = swT.ep2.dpId
        def cookieToDelete = database.getFlow(subFl_1.flowId).reversePath.cookie.value
        northbound.deleteSwitchRules(swIdToManipulate, cookieToDelete)

        then: "HA-Flow validate detects discrepancies"
        def validateYflowInfo = northboundV2.validateHaFlow(haFlow.haFlowId)
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

        when: "Sync HA-flow"
        northboundV2.synchronizeHaFlow(haFlow.haFlowId)

        then: "HA-Flow/subFlow passes flow validation"
        Wrappers.wait(WAIT_OFFSET) {
            northboundV2.getHaFlow(haFlow.haFlowId).status == FlowState.UP.toString()
        }
        northboundV2.validateHaFlow(haFlow.haFlowId).asExpected
        haFlow.subFlows.each {
            northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
        }

        and: "Switches pass validation"
        northbound.validateSwitch(swIdToManipulate).verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)

        where:
        data << [
                [
                        description: "multiSwtich HA-flow",
                        haFlow: { SwitchTriplet swTriplet -> haFlowHelper.randomHaFlow(swTriplet) }
                ]
        ]
    }

    @Tidy
    def "Unable to #data.action a non-existent HA-flow"() {
        when: "Invoke a certain action for a non-existent HA-flow"
        data.method()

        then: "Human readable error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.NOT_FOUND
        verifyAll(e.responseBodyAsString.to(MessageError)) {
            errorMessage == "Could not ${data.actionInMsg} HA-flow"
            errorDescription == "HA-flow $NON_EXISTENT_FLOW_ID not found"
        }

        where:
        data << [
                [
                        action     : "validate",
                        actionInMsg: "validate",
                        method     : { northboundV2.validateHaFlow(NON_EXISTENT_FLOW_ID) }
                ],
                [
                        action     : "synchronize",
                        actionInMsg: "sync",
                        method     : { northboundV2.synchronizeHaFlow(NON_EXISTENT_FLOW_ID) }
                ],
                [
                        action     : "get",
                        actionInMsg: "get",
                        method     : { northboundV2.getHaFlow(NON_EXISTENT_FLOW_ID) }
                ],
                [
                        action     : "delete",
                        actionInMsg: "delete",
                        method     : { northboundV2.deleteHaFlow(NON_EXISTENT_FLOW_ID) }
                ]
        ]
    }
}
