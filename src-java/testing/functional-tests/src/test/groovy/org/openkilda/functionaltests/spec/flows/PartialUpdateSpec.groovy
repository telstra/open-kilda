package org.openkilda.functionaltests.spec.flows

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.PathComputationStrategy
import org.openkilda.northbound.dto.v1.flows.FlowPatchDto

import spock.lang.Unroll

class PartialUpdateSpec extends HealthCheckSpecification {

    @Tidy
    @Unroll
    def "Able to partially update flow #data.field without reinstalling its rules"() {
        given: "A flow"
        def swPair = topologyHelper.switchPairs.first()
        def flow = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow)
        def originalCookies = northbound.getSwitchRules(swPair.src.dpId).flowEntries.findAll {
            Cookie.isIngressRulePassThrough(it.cookie) || !Cookie.isDefaultRule(it.cookie)
        }*.cookie

        when: "Request a flow partial update for a #field field"
        def updateRequest = new FlowPatchDto().tap { it."$data.field" = data.newValue }
        def response = northbound.partialUpdate(flow.flowId, updateRequest)

        then: "Update response reflects the changes"
        response."$data.field" == data.newValue

        and: "Changes actually took place"
        northboundV2.getFlow(flow.flowId)."$data.field" == data.newValue

        and: "Flow rules have not been reinstalled"
        northbound.getSwitchRules(swPair.src.dpId).flowEntries*.cookie.containsAll(originalCookies)

        cleanup: "Remove the flow"
        flowHelperV2.deleteFlow(flow.flowId)

        where:
        data << [
                [
                        field   : "maxLatency",
                        newValue: 12345
                ],
                [
                        field   : "priority",
                        newValue: 654
                ],
                [
                        field   : "periodicPings",
                        newValue: true
                ],
                [
                        field   : "targetPathComputationStrategy",
                        newValue: PathComputationStrategy.LATENCY.toString().toLowerCase()
                ]
        ]
    }

    @Tidy
    def "Able to do partial update on a single-switch flow"() {
        given: "A single-switch flow"
        def swPair = topologyHelper.singleSwitchPair
        def flow = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow)
        def originalCookies = northbound.getSwitchRules(swPair.src.dpId).flowEntries.findAll {
            Cookie.isIngressRulePassThrough(it.cookie) || !Cookie.isDefaultRule(it.cookie)
        }*.cookie

        when: "Request a flow partial update for a 'priority' field"
        def newPriority = 777
        def updateRequest = new FlowPatchDto().tap { it.priority = newPriority }
        def response = northbound.partialUpdate(flow.flowId, updateRequest)

        then: "Update response reflects the changes"
        response.priority == newPriority

        and: "Changes actually took place"
        northboundV2.getFlow(flow.flowId).priority == newPriority

        and: "Flow rules have not been reinstalled"
        northbound.getSwitchRules(swPair.src.dpId).flowEntries*.cookie.containsAll(originalCookies)

        cleanup: "Remove the flow"
        flowHelperV2.deleteFlow(flow.flowId)
    }

    def "Partial update with empty body does not actually update flow in any way"() {
        given: "A flow"
        def swPair = topologyHelper.switchPairs.first()
        def flow = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow)
        def originalCookies = northbound.getSwitchRules(swPair.src.dpId).flowEntries.findAll {
            Cookie.isIngressRulePassThrough(it.cookie) || !Cookie.isDefaultRule(it.cookie)
        }*.cookie

        when: "Request a flow partial update without specifying any fields"
        def flowBeforeUpdate = northboundV2.getFlow(flow.flowId)
        northbound.partialUpdate(flow.flowId, new FlowPatchDto())

        then: "Flow is left intact"
        expect northboundV2.getFlow(flow.flowId), sameBeanAs(flowBeforeUpdate)
                .ignoring("lastUpdated")

        and: "Flow rules have not been reinstalled"
        northbound.getSwitchRules(swPair.src.dpId).flowEntries*.cookie.containsAll(originalCookies)

        cleanup: "Remove the flow"
        flowHelperV2.deleteFlow(flow.flowId)
    }
}
