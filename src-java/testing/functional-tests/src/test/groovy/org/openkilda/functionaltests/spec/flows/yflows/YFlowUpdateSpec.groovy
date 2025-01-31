package org.openkilda.functionaltests.spec.flows.yflows

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.yflow.YFlowNotUpdatedExpectedError
import org.openkilda.functionaltests.error.yflow.YFlowNotUpdatedWithConflictExpectedError
import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.functionaltests.helpers.model.YFlowExtended
import org.openkilda.functionaltests.helpers.factory.YFlowFactory
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.PathComputationStrategy
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.flows.FlowPatchEndpoint
import org.openkilda.northbound.dto.v2.yflows.SubFlowPatchPayload
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchPayload
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchSharedEndpoint
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchSharedEndpointEncapsulation

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

@Slf4j
@Narrative("Verify update and partial update operations on y-flows.")
class YFlowUpdateSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    YFlowFactory yFlowFactory

    def "User can update #data.descr of a Y-Flow"() {
        given: "Existing Y-Flow"
        def swT = switchTriplets.all().withAllDifferentEndpoints().random()

        def yFlow = yFlowFactory.getRandom(swT, false)
        def oldSharedSwitch = yFlow.sharedEndpoint.switchId
        List<SwitchId> involvedSwitches = yFlow.retrieveAllEntityPaths().getInvolvedSwitches()

        yFlow.tap(data.updateClosure)
        def update = yFlow.convertToUpdate()

        when: "Update the y-flow"
        def updateResponse = yFlow.sendUpdateRequest(update)
        def updatedYFlow = yFlow.waitForBeingInState(FlowState.UP)
        //update involved switches after update
        involvedSwitches.addAll(yFlow.retrieveAllEntityPaths().getInvolvedSwitches())
        // https://github.com/telstra/open-kilda/issues/3411
        switchHelper.synchronize(oldSharedSwitch, true)

        then: "Requested updates are reflected in the response and in 'get' API"
        updateResponse.hasTheSamePropertiesAs(yFlow, data.isYPointVerificationIncluded)
        updatedYFlow.hasTheSamePropertiesAs(yFlow, data.isYPointVerificationIncluded)

        and: "All related switches have no discrepancies"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitches.unique()).isEmpty()

        where: data << [
                [
                        descr: "shared port and subflow ports",
                        updateClosure: { YFlowExtended payload ->
                            def allowedSharedPorts = topology.getAllowedPortsForSwitch(topology.find(
                                    payload.sharedEndpoint.switchId)) - payload.sharedEndpoint.portNumber
                            payload.sharedEndpoint.portNumber = allowedSharedPorts[0]
                            payload.subFlows.each {
                                def allowedPorts = topology.getAllowedPortsForSwitch(topology.find(
                                        it.endpoint.switchId)) - it.endpoint.portNumber
                                it.endpoint.portNumber = allowedPorts[0]
                            }
                        },
                        isYPointVerificationIncluded: true
                ],
                [
                        descr: "shared switch and subflow switches",
                        updateClosure: { YFlowExtended payload ->
                            def newSwT = switchTriplets.all(true).getSwitchTriplets().find {
                                it.shared.dpId != payload.sharedEndpoint.switchId &&
                                        it.ep1.dpId != payload.subFlows[0].endpoint.switchId &&
                                        it.ep2.dpId != payload.subFlows[1].endpoint.switchId &&
                                        it.ep1 != it.ep2
                            }
                            payload.sharedEndpoint.switchId = newSwT.shared.dpId
                            payload.subFlows[0].endpoint.switchId = newSwT.ep1.dpId
                            payload.subFlows[1].endpoint.switchId = newSwT.ep2.dpId
                            payload.sharedEndpoint.portNumber = topology
                                    .getAllowedPortsForSwitch(topology.find(newSwT.shared.dpId))[-1]
                            payload.subFlows[0].endpoint.portNumber = topology
                                    .getAllowedPortsForSwitch(topology.find(newSwT.ep1.dpId))[-1]
                            payload.subFlows[1].endpoint.portNumber = topology
                                    .getAllowedPortsForSwitch(topology.find(newSwT.ep2.dpId))[-1]
                        },
                        isYPointVerificationIncluded: false
                ],
                [
                        descr: "[without any changes in update request]",
                        updateClosure: { },
                        isYPointVerificationIncluded: true
                ]
        ]
    }

    def "User can update y-flow where one of subflows has both ends on shared switch"() {
        given: "Existing y-flow where one of subflows has both ends on shared switch"
        def switchTriplet = switchTriplets.all(true, true).findSwitchTripletForOneSwitchSubflow()

        def yFlow = yFlowFactory.getRandom(switchTriplet, false)
        yFlow.setDescription("new description")
        def endPoint = yFlow.getSubFlows().get(0).getEndpoint()
        endPoint.setPortNumber(topology.getAllowedPortsForSwitch(topology.find(endPoint.getSwitchId())).first())

        List<SwitchId> involvedSwitches = yFlow.retrieveAllEntityPaths().getInvolvedSwitches()
        def update = yFlow.convertToUpdate()

        when: "Update the y-flow"
        def updateResponse = yFlow.sendUpdateRequest(update)
        def updatedYFlow = yFlow.waitForBeingInState(FlowState.UP)
        //update involved switches after update
        involvedSwitches.addAll(yFlow.retrieveAllEntityPaths().getInvolvedSwitches())

        then: "Requested updates are reflected in the response and in 'get' API"
        updateResponse.hasTheSamePropertiesAs(yFlow)
        updatedYFlow.hasTheSamePropertiesAs(yFlow)

        and: "All related switches have no discrepancies"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitches.unique()).isEmpty()
    }

    def "User can partially update fields of one-switch y-flow"() {
        given: "Existing one-switch y-flow"
        def swT = switchTriplets.all(false, true).singleSwitch().random()
        def yFlow = yFlowFactory.getBuilder(swT, false)
                .withMaxLatency(50).withMaxLatencyTier2(100).build().create()

        def patch = YFlowPatchPayload.builder()
                .maximumBandwidth(yFlow.maximumBandwidth * 2)
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY.toString().toLowerCase())
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN.toString().toLowerCase())
                .maxLatency(yFlow.maxLatency * 2)
                .maxLatencyTier2(yFlow.maxLatencyTier2 * 2)
                .ignoreBandwidth(true)
                .pinned(true)
                .priority(10)
                .strictBandwidth(false)
                .description("updated description")
                .build()

        yFlow.setMaximumBandwidth(patch.getMaximumBandwidth())
        yFlow.setPathComputationStrategy(patch.getPathComputationStrategy())
        yFlow.setEncapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
        yFlow.setMaxLatency(patch.getMaxLatency())
        yFlow.setMaxLatencyTier2(patch.getMaxLatencyTier2())
        yFlow.setIgnoreBandwidth(patch.getIgnoreBandwidth())
        yFlow.setPinned(patch.getPinned())
        yFlow.setPriority(patch.getPriority())
        yFlow.setStrictBandwidth(patch.getStrictBandwidth())
        yFlow.setDescription(patch.getDescription())


        when: "Partial update the y-flow"
        def updateResponse = yFlow.sendPartialUpdateRequest(patch)
        def updatedYFlow = yFlow.waitForBeingInState(FlowState.UP)

        then: "Requested updates are reflected in the response and in 'get' API"
        updateResponse.hasTheSamePropertiesAs(yFlow)
        updatedYFlow.hasTheSamePropertiesAs(yFlow)

        and: "All related switches have no discrepancies"
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(swT.shared.dpId).isPresent()
    }

    def "User can partially update #data.descr of a y-flow"() {
        given: "Existing y-flow"
        def swT = switchTriplets.all().withAllDifferentEndpoints().random()
        def yFlow = yFlowFactory.getRandom(swT, false)

        List<SwitchId> involvedSwitches = yFlow.retrieveAllEntityPaths().getInvolvedSwitches()
        def oldSharedSwitch = yFlow.sharedEndpoint.switchId
        def patch = data.buildPatchRequest(yFlow)

        when: "Partial update the y-flow"
        def updateResponse = yFlow.sendPartialUpdateRequest(patch)
        def updatedYFlow = yFlow.waitForBeingInState(FlowState.UP)
        //update involved switches after update
        involvedSwitches.addAll(yFlow.retrieveAllEntityPaths().getInvolvedSwitches())
        // https://github.com/telstra/open-kilda/issues/3411
        switchHelper.synchronize(oldSharedSwitch, true)

        then: "Requested updates are reflected in the response and in 'get' API"
        updateResponse.hasTheSamePropertiesAs(yFlow, data.isYPointVerificationIncluded)
        updatedYFlow.hasTheSamePropertiesAs(yFlow, data.isYPointVerificationIncluded)

        and: "All related switches have no discrepancies"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitches.unique()).isEmpty()

        //buildPatchRequest in addition to providing a patch payload should also updated the yFlow object
        //in order to reflect the expect result after update
        where: data << [
                [
                        descr: "shared port and subflow ports",
                        buildPatchRequest: { YFlowExtended payload ->
                            def allowedSharedPorts = topology.getAllowedPortsForSwitch(topology.find(
                                    payload.sharedEndpoint.switchId)) - payload.sharedEndpoint.portNumber
                            def patchBuilder = YFlowPatchPayload.builder()
                                    .sharedEndpoint(YFlowPatchSharedEndpoint.builder()
                                            .portNumber(allowedSharedPorts[0])
                                            .build())
                            payload.sharedEndpoint.portNumber = allowedSharedPorts[0]
                            def subFlows = []
                            payload.subFlows.each {
                                def allowedPorts = topology.getAllowedPortsForSwitch(topology.find(
                                        it.endpoint.switchId)) - it.endpoint.portNumber
                                subFlows << SubFlowPatchPayload.builder()
                                        .endpoint(FlowPatchEndpoint.builder()
                                                .portNumber(allowedPorts[0])
                                                .build())
                                        .flowId(it.flowId)
                                        .build()
                                it.endpoint.portNumber = allowedPorts[0]
                            }
                            patchBuilder.subFlows(subFlows)
                            return patchBuilder.build()
                        },
                        isYPointVerificationIncluded: true
                ],
                [
                        descr: "shared switch and subflow switches",
                        buildPatchRequest: { YFlowExtended payload ->
                            def newSwT = switchTriplets.all(true).getSwitchTriplets().find {
                                it.shared.dpId != payload.sharedEndpoint.switchId &&
                                        it.ep1.dpId != payload.subFlows[0].endpoint.switchId &&
                                        it.ep2.dpId != payload.subFlows[1].endpoint.switchId &&
                                        it.ep1 != it.ep2
                            }
                            payload.sharedEndpoint.switchId = newSwT.shared.dpId
                            payload.subFlows[0].endpoint.switchId = newSwT.ep1.dpId
                            payload.subFlows[1].endpoint.switchId = newSwT.ep2.dpId
                            def port1 = topology
                                    .getAllowedPortsForSwitch(topology.find(newSwT.ep1.dpId))[-1]
                            def port2 = topology
                                    .getAllowedPortsForSwitch(topology.find(newSwT.ep1.dpId))[-1]
                            def portS = topology
                                    .getAllowedPortsForSwitch(topology.find(newSwT.shared.dpId))[-1]
                            payload.subFlows[0].endpoint.portNumber = port1
                            payload.subFlows[1].endpoint.portNumber = port2
                            payload.sharedEndpoint.portNumber = portS

                            return YFlowPatchPayload.builder()
                                    .sharedEndpoint(YFlowPatchSharedEndpoint.builder()
                                            .switchId(newSwT.shared.dpId)
                                            .portNumber(portS)
                                            .build())
                                    .subFlows([SubFlowPatchPayload.builder()
                                                       .endpoint(FlowPatchEndpoint.builder()
                                                               .switchId(newSwT.ep1.dpId)
                                                               .portNumber(port1)
                                                               .build())
                                                       .flowId(payload.subFlows[0].flowId)
                                                       .build(),
                                               SubFlowPatchPayload.builder()
                                                       .endpoint(FlowPatchEndpoint.builder()
                                                               .switchId(newSwT.ep2.dpId)
                                                               .portNumber(port2)
                                                               .build())
                                                       .flowId(payload.subFlows[1].flowId)
                                                       .build()])
                                    .build()
                        },
                        isYPointVerificationIncluded: false
                ],
                [
                        descr: "[without any changes in update request]",
                        buildPatchRequest: {
                            YFlowPatchPayload.builder().build()
                        },
                        isYPointVerificationIncluded: true
                ]
        ]
    }

    def "User cannot update a y-flow with #data.descr"() {
        given: "Existing y-flow"
        def swT = switchTriplets.all().first()
        def yFlow = yFlowFactory.getRandom(swT, false)
        yFlow.tap(data.updateClosure)
        def update = yFlow.convertToUpdate()

        when: "Try to update the y-flow with invalid payload"
        northboundV2.updateYFlow(yFlow.yFlowId, update)

        then: "Error is received"
        def exc = thrown(HttpClientErrorException)
        data.expectedError.matches(exc)

        where: data << [
                [
                        descr        : "non-existent subflowId",
                        updateClosure: { YFlowExtended payload ->
                            payload.subFlows[0].flowId += "non-existent"
                            def allowedPorts = topology.getAllowedPortsForSwitch(topology.find(
                                    payload.subFlows[0].endpoint.switchId)) - payload.subFlows[0].endpoint.portNumber -
                                    payload.subFlows[1].endpoint.portNumber
                            payload.subFlows[0].endpoint.portNumber = allowedPorts[0]
                        },
                        expectedError: new YFlowNotUpdatedWithConflictExpectedError(
                                ~/Requested flow '.*?' conflicts with existing flow '.*'. Details:.*/)
                ],
                [
                        descr: "subflowId not specified",
                        updateClosure: { YFlowExtended payload ->
                            payload.subFlows[1].flowId = null
                        },
                        expectedError: new YFlowNotUpdatedWithConflictExpectedError(
                                ~/Requested flow '.*?' conflicts with existing flow '.*'. Details:.*/)
                ],
                [
                        descr: "only 1 subflow in payload",
                        updateClosure: { YFlowExtended payload ->
                            payload.subFlows.removeLast()
                        },
                        expectedError: new YFlowNotUpdatedExpectedError(~/The y-flow.* must have at least 2 sub flows/)
                ],
                [
                        descr: "conflict after update",
                        updateClosure: { YFlowExtended payload ->
                            payload.subFlows[0].sharedEndpoint.vlanId = payload.subFlows[1].sharedEndpoint.vlanId
                        },
                        expectedError: new YFlowNotUpdatedExpectedError(
                                ~/The sub-flows .* and .* have shared endpoint conflict: .*/)
                ],
                [
                        descr: "empty shared endpoint",
                        updateClosure: { YFlowExtended payload ->
                            payload.sharedEndpoint = null
                        },
                        expectedError: new YFlowNotUpdatedExpectedError(~/Errors: SharedEndpoint is required/)
                ]
        ]
    }

    def "User cannot partial update a y-flow with #data.descr"() {
        given: "Existing y-flow"
        def swT = switchTriplets.all().first()
        def yFlow = yFlowFactory.getRandom(swT, false)
        def patch = data.buildPatchRequest(yFlow)

        when: "Try to partial update the y-flow with invalid payload"
        yFlow.sendPartialUpdateRequest(patch)

        then: "Error is received"
        def exc = thrown(HttpClientErrorException)
        new YFlowNotUpdatedExpectedError(data.errorMessage, data.errorDescrPattern).matches(exc)

        where: data << [
                [
                        descr: "non-existent subflowId",
                        buildPatchRequest: { YFlowExtended payload ->
                            return YFlowPatchPayload.builder()
                                    .subFlows([SubFlowPatchPayload.builder()
                                                       .endpoint(FlowPatchEndpoint.builder()
                                                               .vlanId(33)
                                                               .build())
                                                       .flowId("non-existent-flowid")
                                                       .build(),
                                               SubFlowPatchPayload.builder()
                                                       .flowId(payload.subFlows[1].flowId)
                                                       .build()])
                                    .build()
                        },
                        errorStatusCode: HttpStatus.BAD_REQUEST,
                        errorMessage: "Y-flow update error",
                        errorDescrPattern: ~/There is no sub-flows with sub-flow id: non-existent-flowid/
                ],
                [
                        descr: "only 1 subflow specified",
                        buildPatchRequest: { YFlowExtended payload ->
                            return YFlowPatchPayload.builder()
                                    .subFlows([SubFlowPatchPayload.builder()
                                                       .endpoint(FlowPatchEndpoint.builder()
                                                               .vlanId(33)
                                                               .build())
                                                       .flowId(payload.subFlows[0].flowId)
                                                       .build()])
                                    .build()
                        },
                        errorStatusCode: HttpStatus.BAD_REQUEST,
                        errorMessage: "Could not update y-flow",
                        errorDescrPattern: ~/The y-flow .* must have at least 2 sub flows/
                ],
                [
                        descr: "switch conflict after update",
                        buildPatchRequest: { YFlowExtended payload ->
                            payload.subFlows[0].sharedEndpoint.vlanId = payload.subFlows[1].sharedEndpoint.vlanId
                            return YFlowPatchPayload.builder()
                                    .subFlows([SubFlowPatchPayload.builder()
                                                       .sharedEndpoint(YFlowPatchSharedEndpointEncapsulation.builder()
                                                               .vlanId(payload.subFlows[1].sharedEndpoint.vlanId)
                                                               .build())
                                                       .flowId(payload.subFlows[0].flowId)
                                                       .build(),
                                               SubFlowPatchPayload.builder()
                                                       .sharedEndpoint(YFlowPatchSharedEndpointEncapsulation.builder()
                                                               .vlanId(payload.subFlows[1].sharedEndpoint.vlanId)
                                                               .build())
                                                       .flowId(payload.subFlows[1].flowId)
                                                       .build()])
                                    .build()
                        },
                        errorStatusCode: HttpStatus.BAD_REQUEST,
                        errorMessage: "Could not update y-flow",
                        errorDescrPattern: ~/The sub-flows .* and .* have shared endpoint conflict: .*/
                ]
        ]
    }
}
