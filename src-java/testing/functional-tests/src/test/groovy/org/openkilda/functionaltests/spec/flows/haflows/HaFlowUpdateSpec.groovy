package org.openkilda.functionaltests.spec.flows.haflows

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static org.openkilda.functionaltests.extension.tags.Tag.HA_FLOW
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.haflow.HaFlowNotUpdatedExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.model.HaFlowExtended
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.haflows.HaFlow
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchEndpoint
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload
import org.openkilda.northbound.dto.v2.haflows.HaSubFlowPatchPayload

import com.shazam.shazamcrest.matcher.CustomisableMatcher
import groovy.util.logging.Slf4j
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative

@Slf4j
@Narrative("Verify update and partial update operations on HA-Flows.")
@Tags([HA_FLOW])
class HaFlowUpdateSpec extends HealthCheckSpecification {
    def "User can update #data.descr of a HA-Flow"() {
        given: "Existing HA-Flow"
        def swT = topologyHelper.switchTriplets[0]
        def haFlow = HaFlowExtended.build(swT, northboundV2, topology).create()
        def haFlowDetails = haFlow.waitForBeingInState(FlowState.UP)

        haFlowDetails.tap(data.updateClosure)
        def update = new HaFlowExtended(haFlowDetails, northboundV2, topology).convertToUpdateRequest()

        when: "Update the HA-Flow"
        def updateResponse = northboundV2.updateHaFlow(haFlow.haFlowId, update)
        def ignores = ["subFlows.timeUpdate", "subFlows.status", "subFlows.forwardLatency",
                       "subFlows.reverseLatency", "subFlows.latencyLastModifiedTime", "timeUpdate", "status"]

        then: "Requested updates are reflected in the response and in 'get' API"
        expect updateResponse, sameBeanAs(haFlowDetails, ignores)
        expect northboundV2.getHaFlow(haFlow.haFlowId), sameBeanAs(haFlowDetails, ignores)

        and: "And involved switches pass validation"
        haFlow.waitForBeingInState(FlowState.UP)
        switchHelper.synchronizeAndCollectFixedDiscrepancies(haFlow.retrievedAllEntityPaths().getInvolvedSwitches(true)).isEmpty()

        and: "HA-Flow pass validation"
        haFlow.validate().asExpected

        cleanup:
        haFlow && haFlow.delete()

        where: data << [
                [
                        descr: "shared port and subflow ports",
                        updateClosure: { HaFlow payload ->
                            def allowedSharedPorts = topology.getAllowedPortsForSwitch(topology.find(
                                    payload.sharedEndpoint.switchId)) - payload.sharedEndpoint.portNumber
                            payload.sharedEndpoint.portNumber = allowedSharedPorts[0]
                            payload.subFlows.each {
                                def allowedPorts = topology.getAllowedPortsForSwitch(topology.find(
                                        it.endpoint.switchId)) - it.endpoint.portNumber
                                it.endpoint.portNumber = allowedPorts[0]
                            }
                        }
                ],
                [
                        descr: "shared switch and subflow switches",
                        updateClosure: { HaFlow payload ->
                            def newSwT = topologyHelper.getSwitchTriplets(true).find {
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
                        }
                ],
                [
                        descr: "[without any changes in update request]",
                        updateClosure: { },
                ]
        ]
    }

    def "User can update HA-Flow where one of subflows has both ends on shared switch"() {
        given: "Existing HA-Flow where one of subflows has both ends on shared switch"
        def switchTriplet = topologyHelper.getSwitchTriplets(true, true)
                .find{it.ep1 == it.shared && it.ep2 != it.shared}

        def haFlow = HaFlowExtended.build(switchTriplet, northboundV2, topology).create()
        def haFlowDetails = haFlow.waitForBeingInState(FlowState.UP)

        haFlowDetails.setDescription("new description")
        def endPoint = haFlowDetails.getSubFlows().get(0).getEndpoint()
        endPoint.setPortNumber(topology.getAllowedPortsForSwitch(topology.find(endPoint.getSwitchId())).first())

        when: "Update the HA-Flow"
        def update = new HaFlowExtended(haFlowDetails, northboundV2, topology).convertToUpdateRequest()
        def updateResponse = northboundV2.updateHaFlow(haFlow.haFlowId, update)
        def ignores = ["subFlows.timeUpdate", "subFlows.status", "subFlows.forwardLatency",
                       "subFlows.reverseLatency", "subFlows.latencyLastModifiedTime", "timeUpdate", "status"]

        then: "Requested updates are reflected in the response and in 'get' API"
        expect updateResponse, sameBeanAs(haFlowDetails, ignores)
        expect northboundV2.getHaFlow(haFlow.haFlowId), sameBeanAs(haFlowDetails, ignores)

        and: "And involved switches pass validation"
        haFlow.waitForBeingInState(FlowState.UP)
        switchHelper.synchronizeAndCollectFixedDiscrepancies(haFlow.retrievedAllEntityPaths().getInvolvedSwitches(true)).isEmpty()

        and: "HA-Flow pass validation"
        haFlow.validate().asExpected

        cleanup:
        haFlow && haFlow.delete()
    }

    def "User can partially update #data.descr of a HA-Flow"() {
        given: "Existing HA-Flow"
        def swT = topologyHelper.switchTriplets.find { it.ep1 != it.ep2 }
        def haFlow = HaFlowExtended.build(swT, northboundV2, topology).create()
        def haFlowDetails = haFlow.waitForBeingInState(FlowState.UP)

        def patch = data.buildPatchRequest(haFlowDetails)

        when: "Partial update the HA-Flow"
        def updateResponse = northboundV2.partialUpdateHaFlow(haFlow.haFlowId, patch)
        def ignores = ["subFlows.timeUpdate", "subFlows.status", "subFlows.forwardLatency",
                       "subFlows.reverseLatency", "subFlows.latencyLastModifiedTime", "timeUpdate", "status"]

        then: "Requested updates are reflected in the response and in 'get' API"
        expect updateResponse, sameBeanAs(haFlowDetails, ignores)
        expect northboundV2.getHaFlow(haFlow.haFlowId), sameBeanAs(haFlowDetails, ignores)

        and: "And involved switches pass validation"
        haFlow.waitForBeingInState(FlowState.UP)
        switchHelper.synchronizeAndCollectFixedDiscrepancies(haFlow.retrievedAllEntityPaths().getInvolvedSwitches(true)).isEmpty()

        and: "HA-Flow pass validation"
        haFlow.validate().asExpected

        cleanup:
        haFlow && haFlow.delete()

        //buildPatchRequest in addition to providing a patch payload should also updated the haFlow object
        //in order to reflect the expect result after update
        where: data << [
                [
                        descr: "shared port and subflow ports",
                        buildPatchRequest: { HaFlow payload ->
                            def allowedSharedPorts = topology.getAllowedPortsForSwitch(topology.find(
                                    payload.sharedEndpoint.switchId)) - payload.sharedEndpoint.portNumber
                            def patchBuilder = HaFlowPatchPayload.builder()
                                    .sharedEndpoint(HaFlowPatchEndpoint.builder()
                                            .portNumber(allowedSharedPorts[0])
                                            .build())
                            payload.sharedEndpoint.portNumber = allowedSharedPorts[0]
                            def subFlows = []
                            payload.subFlows.each {
                                def allowedPorts = topology.getAllowedPortsForSwitch(topology.find(
                                        it.endpoint.switchId)) - it.endpoint.portNumber
                                subFlows << HaSubFlowPatchPayload.builder()
                                        .endpoint(HaFlowPatchEndpoint.builder()
                                                .portNumber(allowedPorts[0])
                                                .build())
                                        .flowId(it.flowId)
                                        .build()
                                it.endpoint.portNumber = allowedPorts[0]
                            }
                            patchBuilder.subFlows(subFlows)
                            return patchBuilder.build()
                        }
                ],
                [
                        descr: "sub flow vlan on only one sub flow",
                        buildPatchRequest: { HaFlow payload ->
                            def subFlow = payload.subFlows[0]
                            def newVlan = subFlow.endpoint.vlanId + 1
                            def patchBuilder = HaFlowPatchPayload.builder()
                                    .subFlows([HaSubFlowPatchPayload.builder()
                                                       .flowId(subFlow.flowId)
                                                       .endpoint(HaFlowPatchEndpoint.builder()
                                                               .vlanId(newVlan)
                                                               .build())
                                                       .build()])
                            subFlow.endpoint.vlanId = newVlan
                            return patchBuilder.build()
                        }
                ],
                [
                        descr: "shared switch and subflow switches",
                        buildPatchRequest: { HaFlow payload ->
                            def newSwT = topologyHelper.getSwitchTriplets(true).find {
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

                            return HaFlowPatchPayload.builder()
                                    .sharedEndpoint(HaFlowPatchEndpoint.builder()
                                            .switchId(newSwT.shared.dpId)
                                            .portNumber(portS)
                                            .build())
                                    .subFlows([HaSubFlowPatchPayload.builder()
                                                       .endpoint(HaFlowPatchEndpoint.builder()
                                                               .switchId(newSwT.ep1.dpId)
                                                               .portNumber(port1)
                                                               .build())
                                                       .flowId(payload.subFlows[0].flowId)
                                                       .build(),
                                               HaSubFlowPatchPayload.builder()
                                                       .endpoint(HaFlowPatchEndpoint.builder()
                                                               .switchId(newSwT.ep2.dpId)
                                                               .portNumber(port2)
                                                               .build())
                                                       .flowId(payload.subFlows[1].flowId)
                                                       .build()])
                                    .build()
                        }
                ],
                [
                        descr: "[without any changes in update request]",
                        buildPatchRequest: {
                            HaFlowPatchPayload.builder().build()
                        }
                ]
        ]
    }

    def "User cannot update a HA-Flow #data.descr"() {
        given: "Existing HA-Flow"
        def swT = topologyHelper.switchTriplets[0]
        def haFlow = HaFlowExtended.build(swT, northboundV2, topology).create()

        haFlow.tap(data.updateClosure)
        def update = haFlow.convertToUpdateRequest()

        when: "Try to update the HA-Flow with invalid payload"
        northboundV2.updateHaFlow(haFlow.haFlowId, update)

        then: "Error is received"
        def exc = thrown(HttpClientErrorException)
        new HaFlowNotUpdatedExpectedError(data.errorDescription).matches(exc)

        and: "And involved switches pass validation"
        def involvedSwitchIds = haFlow.retrievedAllEntityPaths().getInvolvedSwitches(true)
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitchIds).isEmpty()

        and: "HA-Flow pass validation"
        haFlow.validate().asExpected

        cleanup:
        haFlow && haFlow.delete()

        where: data << [
                [
                        descr: "with non-existent subflowId",
                        updateClosure: { HaFlowExtended payload ->
                            payload.subFlows[0].flowId += "non-existent"
                            def allowedPorts = topology.getAllowedPortsForSwitch(topology.find(
                                    payload.subFlows[0].endpoint.switchId)) - payload.subFlows[0].endpoint.portNumber -
                                    payload.subFlows[1].endpoint.portNumber
                            payload.subFlows[0].endpoint.portNumber = allowedPorts[0]
                            setRandomVlans(payload) // to do not conflict with existing sub flows
                        },
                        errorDescription: ~/Invalid sub flow IDs: .*\. Valid sub flows IDs are: .*?/
                ],
                [
                        descr: "with subflowId not specified",
                        updateClosure: { HaFlowExtended payload ->
                            payload.subFlows[1].flowId = null
                        },
                        errorDescription: ~/The sub-flow of .* has no sub-flow id provided/
                ],
                [
                        descr: "to one switch HA-Flow",
                        updateClosure: { HaFlowExtended payload ->
                            payload.subFlows[0].endpoint.switchId = payload.getSharedEndpoint().switchId
                            payload.subFlows[1].endpoint.switchId = payload.getSharedEndpoint().switchId
                        },
                        errorDescription: ~/The ha-flow.* ? is one switch flow\. \
At least one of subflow endpoint switch id must differ from shared endpoint switch.* ?/
                ]
        ]
    }

    private void setRandomVlans(HaFlowExtended payload) {
        payload.sharedEndpoint.vlanId = flowHelperV2.randomVlan([payload.sharedEndpoint.vlanId])
        payload.subFlows.forEach { it.endpoint.vlanId = flowHelperV2.randomVlan([it.endpoint.vlanId]) }
    }

    def "User cannot partial update a HA-Flow with #data.descr"() {
        given: "Existing HA-Flow"
        def swT = topologyHelper.switchTriplets[0]
        def haFlow = HaFlowExtended.build(swT, northboundV2, topology).create()
        def patch = data.buildPatchRequest(haFlow)


        when: "Try to partial update the HA-Flow with invalid payload"
        northboundV2.partialUpdateHaFlow(haFlow.haFlowId, patch)

        then: "Error is received"
        def exc = thrown(HttpClientErrorException)
        new HaFlowNotUpdatedExpectedError(data.errorDescrPattern).matches(exc)

        and: "And involved switches pass validation"
        def involvedSwitchIds = haFlow.retrievedAllEntityPaths().getInvolvedSwitches(true)
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitchIds).isEmpty()

        and: "HA-Flow pass validation"
        haFlow.validate().asExpected

        cleanup:
        haFlow && haFlow.delete()

        where: data << [
                [
                        descr: "non-existent subflowId",
                        buildPatchRequest: { HaFlowExtended payload ->
                            return HaFlowPatchPayload.builder()
                                    .subFlows([HaSubFlowPatchPayload.builder()
                                                       .endpoint(HaFlowPatchEndpoint.builder()
                                                               .vlanId(33)
                                                               .build())
                                                       .flowId("non-existent-flowid")
                                                       .build(),
                                               HaSubFlowPatchPayload.builder()
                                                       .flowId(payload.subFlows[1].flowId)
                                                       .build()])
                                    .build()
                        },
                        errorDescrPattern: ~/HA-flow .*? has no sub flow .*?/
                ],
                [
                        descr: "switch conflict in request",
                        buildPatchRequest: { HaFlowExtended payload ->
                            def subFlow1 = payload.subFlows[0];
                            def subFlow2 = payload.subFlows[1];
                            def endpoint = HaFlowPatchEndpoint.builder()
                                    .switchId(subFlow1.endpoint.switchId)
                                    .portNumber(subFlow1.endpoint.portNumber)
                                    .vlanId(subFlow1.endpoint.vlanId + 1)
                                    .innerVlanId(0)
                                    .build();
                            return HaFlowPatchPayload.builder()
                                    .subFlows([HaSubFlowPatchPayload.builder()
                                                       .flowId(subFlow1.flowId)
                                                       .endpoint(endpoint)
                                                       .build(),
                                               HaSubFlowPatchPayload.builder()
                                                       .flowId(subFlow2.flowId)
                                                       .endpoint(endpoint)
                                                       .build()])
                                    .build()
                        },
                        errorDescrPattern: ~/The sub-flows .* and .* have endpoint conflict: .*/
                ],
                [
                        descr: "switch conflict after update",
                        buildPatchRequest: { HaFlowExtended payload ->
                            def subFlow1 = payload.subFlows[0];
                            def subFlow2 = payload.subFlows[1];
                            return HaFlowPatchPayload.builder()
                                    .subFlows([
                                            HaSubFlowPatchPayload.builder()
                                                    .flowId(subFlow2.flowId)
                                                    .endpoint(HaFlowPatchEndpoint.builder()
                                                            .switchId(subFlow1.endpoint.switchId)
                                                            .portNumber(subFlow1.endpoint.portNumber)
                                                            .vlanId(subFlow1.endpoint.vlanId)
                                                            .innerVlanId(subFlow1.endpoint.innerVlanId)
                                                            .build())
                                                    .build()])
                                    .build()
                        },
                        errorDescrPattern: ~/The sub-flows .* and .* have endpoint conflict: .*/
                ],
                [
                        descr: "different inner vlans of sub flows on one switch",
                        buildPatchRequest: { HaFlowExtended payload ->
                            def subFlow1 = payload.subFlows[0];
                            def subFlow2 = payload.subFlows[1];
                            return HaFlowPatchPayload.builder()
                                    .subFlows([
                                            HaSubFlowPatchPayload.builder()
                                                    .flowId(subFlow2.flowId)
                                                    .endpoint(HaFlowPatchEndpoint.builder()
                                                            .switchId(subFlow1.endpoint.switchId)
                                                            .portNumber(subFlow1.endpoint.portNumber)
                                                            .vlanId(subFlow1.endpoint.vlanId)
                                                            .innerVlanId(subFlow1.endpoint.innerVlanId + 1)
                                                            .build())
                                                    .build()])
                                    .build()
                        },
                        errorDescrPattern: ~/To have ability to use double vlan tagging for both sub flow destination \
endpoints which are placed on one switch .* you must set equal inner vlan for both endpoints.*/
                ]
        ]
    }

    static <T> CustomisableMatcher<T> sameBeanAs(final T expected, List<String> ignores) {
        def matcher = sameBeanAs(expected)
        ignores.each { matcher.ignoring(it) }
        return matcher
    }
}
