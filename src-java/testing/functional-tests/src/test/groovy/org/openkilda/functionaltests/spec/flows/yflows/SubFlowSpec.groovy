package org.openkilda.functionaltests.spec.flows.yflows

import static org.openkilda.functionaltests.helpers.FlowNameGenerator.FLOW
import static org.openkilda.functionaltests.helpers.SwitchHelper.randomVlan

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowNotModifiedExpectedError
import org.openkilda.functionaltests.helpers.model.YFlowActionType
import org.openkilda.functionaltests.helpers.model.YFlowFactory
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.FlowPathDirection
import org.openkilda.northbound.dto.v1.flows.PingInput
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.flows.FlowLoopPayload
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointPayload
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2
import org.openkilda.northbound.dto.v2.yflows.SubFlow

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("Verify different actions for a sub-flow.")
class SubFlowSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    YFlowFactory yFlowFactory

    def "Unable to #data.action a sub-flow"() {
        given: "Existing Y-Flow"
        def swT = topologyHelper.switchTriplets[0]
        def yFlow = yFlowFactory.getRandom(swT)

        when: "Invoke a certain action for a sub-flow"
        SubFlow subFlow = yFlow.subFlows.first()
        data.method(subFlow)

        then: "Human readable error is returned"
        def e = thrown(HttpClientErrorException)
        new FlowNotModifiedExpectedError(subFlow.flowId).matches(e)

        and: "All involved switches pass switch validation"
        def involvedSwitches = yFlow.retrieveAllEntityPaths().getInvolvedSwitches()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitches).isEmpty()

        and: "Y-Flow and each sublows are in UP state"
        yFlow.waitForBeingInState(FlowState.UP)

        and: "Y-Flow passes flow validation"
        yFlow.validate().asExpected

        and: "SubFlow passes flow validation"
        northbound.validateFlow(subFlow.flowId).each { direction -> assert direction.asExpected }

        and: "Flow history doesn't contain info about illegal action"
        //create action only
        def historyEvents = yFlow.retrieveFlowHistory().entries
        verifyAll {
            historyEvents.size() == 1
            historyEvents.last().payload.last().action == YFlowActionType.CREATE.payloadLastAction
        }

        and: "Sub flow is pingable"
        verifyAll(northbound.pingFlow(subFlow.flowId, new PingInput())) {
            forward.pingSuccess
            reverse.pingSuccess
        }

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
                                    .mirrorPointId(FLOW.generateId())
                                    .mirrorPointDirection(FlowPathDirection.FORWARD.toString().toLowerCase())
                                    .mirrorPointSwitchId(sFlow.endpoint.switchId)
                                    .sinkEndpoint(FlowEndpointV2.builder().switchId(sFlow.endpoint.switchId)
                                            .portNumber(topology.getAllowedPortsForSwitch(
                                                    topology.activeSwitches.find { it.dpId == sFlow.endpoint.switchId }).first())
                                            .vlanId(randomVlan())
                                            .build())
                                    .build()
                            northboundV2.createMirrorPoint(sFlow.flowId, mirrorEndpoint)
                        }
                ]
        ]
    }
}
