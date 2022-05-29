package org.openkilda.functionaltests.spec.flows.yflows

import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.CREATE_SUCCESS_Y
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.FlowPathDirection
import org.openkilda.northbound.dto.v1.flows.PingInput
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.flows.FlowLoopPayload
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointPayload
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2
import org.openkilda.northbound.dto.v2.yflows.SubFlow

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("Verify different actions for a sub-flow.")
class SubFlowSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    YFlowHelper yFlowHelper

    @Tidy
    def "Unable to #data.action a sub-flow"() {
        given: "Existing y-flow"
        def swT = topologyHelper.switchTriplets[0]
        def yFlowRequest = yFlowHelper.randomYFlow(swT)
        def yFlow = yFlowHelper.addYFlow(yFlowRequest)

        when: "Invoke a certain action for a sub-flow"
        def subFlow = yFlow.subFlows.first()
        data.method(subFlow)

        then: "Human readable error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.BAD_REQUEST
        verifyAll(e.responseBodyAsString.to(MessageError)) {
            errorMessage == "Could not modify flow"
            errorDescription == "${subFlow.flowId} is a sub-flow of a y-flow. Operations on sub-flows are forbidden."
        }

        and: "All involved switches pass switch validation"
        def involvedSwitches = pathHelper.getInvolvedYSwitches(yFlow.YFlowId)
        involvedSwitches.each { sw ->
            northbound.validateSwitch(sw.dpId).verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
            northbound.validateSwitch(sw.dpId).verifyMeterSectionsAreEmpty(["missing", "excess", "misconfigured"])
            northbound.validateSwitch(sw.dpId).verifyRuleSectionsAreEmpty(["missing", "misconfigured"])
            northbound.validateSwitch(sw.dpId).verifyMeterSectionsAreEmpty(["missing", "misconfigured"])
        }

        and: "Y-Flow is UP"
        and: "Sub flows are UP"
        Wrappers.wait(WAIT_OFFSET) {
            with(northboundV2.getYFlow(yFlow.YFlowId)) {
                it.status == FlowState.UP.toString()
                it.subFlows.each { it.status == FlowState.UP.toString() }
            }
        }

        and: "Y-flow passes flow validation"
        northboundV2.validateYFlow(yFlow.YFlowId).asExpected

        and: "SubFlow passes flow validation"
        northbound.validateFlow(subFlow.flowId).each { direction -> assert direction.asExpected }

        and: "Flow history doesn't contain info about illegal action"
        //create action only
        northbound.getFlowHistory(yFlow.YFlowId).last().payload.last().action == CREATE_SUCCESS_Y

        and: "Sub flow is pingable"
        verifyAll(northbound.pingFlow(subFlow.flowId, new PingInput())) {
            forward.pingSuccess
            reverse.pingSuccess
        }

        cleanup:
        yFlowHelper.deleteYFlow(yFlow.YFlowId)

        where:
        data << [
                [
                        action: "rerouteV1",
                        method: { SubFlow sFlow ->
                            northbound.rerouteFlow(sFlow.flowId)
                        }
                ],
                [
                        action: "rerouteV2",
                        method: { SubFlow sFlow ->
                            northboundV2.rerouteFlow(sFlow.flowId)
                        }
                ],
                [
                        action: "synchronize",
                        method: { SubFlow sFlow ->
                            northbound.rerouteFlow(sFlow.flowId)
                        }
                ],
                [
                        action: "update",
                        method: { SubFlow sFlow ->
                            def flowToUpdate = northbound.getFlow(sFlow.flowId)
                            northbound.updateFlow(flowToUpdate.id, flowToUpdate.tap { it.description += " updated" })
                        }
                ],
                [
                        action: "partially update",
                        method: { SubFlow sFlow ->
                            def updateRequest = new FlowPatchV2().tap { it.description += " updated" }
                            northboundV2.partialUpdate(sFlow.flowId, updateRequest)
                        }
                ],
                [
                        action: "delete",
                        method: { SubFlow sFlow ->
                            northboundV2.deleteFlow(sFlow.flowId)
                        }
                ],
                [
                        action: "create a flowLoop on",
                        method: { SubFlow sFlow ->
                            northboundV2.createFlowLoop(sFlow.flowId, new FlowLoopPayload(sFlow.endpoint.switchId))
                        }
                ],
                [
                        action: "create a mirrorPoint on",
                        method: { SubFlow sFlow ->
                            def mirrorEndpoint = FlowMirrorPointPayload.builder()
                                    .mirrorPointId(flowHelperV2.generateFlowId())
                                    .mirrorPointDirection(FlowPathDirection.FORWARD.toString().toLowerCase())
                                    .mirrorPointSwitchId(sFlow.endpoint.switchId)
                                    .sinkEndpoint(FlowEndpointV2.builder().switchId(sFlow.endpoint.switchId)
                                            .portNumber(topology.getAllowedPortsForSwitch(
                                                    topology.activeSwitches.find { it.dpId == sFlow.endpoint.switchId }).first())
                                            .vlanId(flowHelperV2.randomVlan())
                                            .build())
                                    .build()
                            northboundV2.createMirrorPoint(sFlow.flowId, mirrorEndpoint)
                        }
                ]
        ]
    }
}
