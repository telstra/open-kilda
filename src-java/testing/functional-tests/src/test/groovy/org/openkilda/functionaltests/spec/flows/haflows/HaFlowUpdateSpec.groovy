package org.openkilda.functionaltests.spec.flows.haflows

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.model.Switch

import static org.openkilda.functionaltests.extension.tags.Tag.HA_FLOW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.haflow.HaFlowNotUpdatedExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.HaFlowFactory
import org.openkilda.functionaltests.helpers.model.HaFlowExtended
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchEndpoint
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload
import org.openkilda.northbound.dto.v2.haflows.HaSubFlowPatchPayload

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

@Slf4j
@Narrative("Verify update and partial update operations on HA-Flows.")
@Tags([HA_FLOW])
class HaFlowUpdateSpec extends HealthCheckSpecification {

    @Shared
    @Autowired
    HaFlowFactory haFlowFactory

    def "User can update #data.descr of an HA-Flow"() {
        given: "Existing HA-Flow"
        def swT = topologyHelper.switchTriplets[0]
        def haFlow = haFlowFactory.getRandom(swT)

        haFlow.tap(data.updateClosure)
        def update = haFlow.convertToUpdateRequest()

        when: "Update the HA-Flow"
        def updateResponse = haFlow.sendUpdateRequest(update)
        def updatedHaFlow = haFlow.waitForBeingInState(FlowState.UP)

        then: "Requested updates are reflected in the response and in 'get' API"
        updateResponse.hasTheSamePropertiesAs(haFlow)
        updatedHaFlow.hasTheSamePropertiesAs(haFlow)

        and: "And involved switches pass validation"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(haFlow.retrievedAllEntityPaths().getInvolvedSwitches()).isEmpty()

        and: "HA-Flow pass validation"
        haFlow.validate().asExpected

        where: data << [
                [
                        descr: "shared port and subflow ports",
                        updateClosure: { HaFlowExtended payload ->
                            def allowedSharedPorts = topology.getAllowedPortsForSwitch(topology.find(
                                    payload.sharedEndpoint.switchId)) - payload.sharedEndpoint.portNumber
                            payload.sharedEndpoint.portNumber = allowedSharedPorts[0]
                            payload.subFlows.each {
                                def allowedPorts = topology.getAllowedPortsForSwitch(topology.find(
                                        it.endpointSwitchId)) - it.endpointPort
                                it.endpointPort = allowedPorts[0]
                            }
                        }
                ],
                [
                        descr: "shared switch and subflow switches",
                        updateClosure: { HaFlowExtended payload ->
                            def newSwT = topologyHelper.getSwitchTriplets(true).find {
                                it.shared.dpId != payload.sharedEndpoint.switchId &&
                                        it.ep1.dpId != payload.subFlows[0].endpointSwitchId &&
                                        it.ep2.dpId != payload.subFlows[1].endpointSwitchId &&
                                        it.ep1 != it.ep2
                            }
                            payload.sharedEndpoint.switchId = newSwT.shared.dpId
                            payload.subFlows[0].endpointSwitchId = newSwT.ep1.dpId
                            payload.subFlows[1].endpointSwitchId = newSwT.ep2.dpId
                            payload.sharedEndpoint.portNumber = topology
                                    .getAllowedPortsForSwitch(topology.find(newSwT.shared.dpId))[-1]
                            payload.subFlows[0].endpointPort = topology
                                    .getAllowedPortsForSwitch(topology.find(newSwT.ep1.dpId))[-1]
                            payload.subFlows[1].endpointPort = topology
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

        def haFlow = haFlowFactory.getRandom(switchTriplet)

        haFlow.setDescription("new description")
        def subflow = haFlow.subFlows.first()
        subflow.endpointPort = topology.getAllowedPortsForSwitch(topology.find(subflow.endpointSwitchId)).first()

        when: "Update the HA-Flow"
        def update = haFlow.convertToUpdateRequest()
        def updateResponse = haFlow.sendUpdateRequest(update)
        def updatedHaFlow = haFlow.waitForBeingInState(FlowState.UP)

        then: "Requested updates are reflected in the response and in 'get' API"
        updateResponse.hasTheSamePropertiesAs(haFlow)
        updatedHaFlow.hasTheSamePropertiesAs(haFlow)

        and: "And involved switches pass validation"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(haFlow.retrievedAllEntityPaths().getInvolvedSwitches()).isEmpty()

        and: "HA-Flow pass validation"
        haFlow.validate().asExpected
    }

    def "User can partially update #data.descr of an HA-Flow"() {
        given: "Existing HA-Flow"
        def swT = topologyHelper.switchTriplets.find { it.ep1 != it.ep2 }
        def haFlow = haFlowFactory.getRandom(swT)

        def patch = data.buildPatchRequest(haFlow)

        when: "Partial update the HA-Flow"
        def updateResponse = haFlow.sendPartialUpdateRequest(patch)
        def updatedHaFlow = haFlow.waitForBeingInState(FlowState.UP)

        then: "Requested updates are reflected in the response and in 'get' API"
        updateResponse.hasTheSamePropertiesAs(haFlow)
        updatedHaFlow.hasTheSamePropertiesAs(haFlow)

        and: "And involved switches pass validation"
        Wrappers.wait(RULES_INSTALLATION_TIME + WAIT_OFFSET) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(
                    haFlow.retrievedAllEntityPaths().getInvolvedSwitches()).isEmpty()
        }

        and: "HA-Flow pass validation"
        haFlow.validate().asExpected

        //buildPatchRequest in addition to providing a patch payload should also updated the haFlow object
        //in order to reflect the expect result after update
        where: data << [
                [
                        descr: "shared port and subflow ports",
                        buildPatchRequest: { HaFlowExtended payload ->
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
                                        it.endpointSwitchId)) - it.endpointPort
                                subFlows << HaSubFlowPatchPayload.builder()
                                        .endpoint(HaFlowPatchEndpoint.builder()
                                                .portNumber(allowedPorts[0])
                                                .build())
                                        .flowId(it.haSubFlowId)
                                        .build()
                                it.endpointPort = allowedPorts[0]
                            }
                            patchBuilder.subFlows(subFlows)
                            return patchBuilder.build()
                        }
                ],
                [
                        descr: "sub flow vlan on only one sub flow",
                        buildPatchRequest: { HaFlowExtended payload ->
                            def subFlow = payload.subFlows[0]
                            def newVlan = subFlow.endpointVlan + 1
                            def patchBuilder = HaFlowPatchPayload.builder()
                                    .subFlows([HaSubFlowPatchPayload.builder()
                                                       .flowId(subFlow.haSubFlowId)
                                                       .endpoint(HaFlowPatchEndpoint.builder()
                                                               .vlanId(newVlan)
                                                               .build())
                                                       .build()])
                            subFlow.endpointVlan = newVlan
                            return patchBuilder.build()
                        }
                ],
                [
                        descr: "shared switch and subflow switches",
                        buildPatchRequest: { HaFlowExtended payload ->
                            def newSwT = topologyHelper.getSwitchTriplets(true).find {
                                it.shared.dpId != payload.sharedEndpoint.switchId &&
                                        it.ep1.dpId != payload.subFlows[0].endpointSwitchId &&
                                        it.ep2.dpId != payload.subFlows[1].endpointSwitchId &&
                                        it.ep1 != it.ep2
                            }
                            payload.sharedEndpoint.switchId = newSwT.shared.dpId
                            payload.subFlows[0].endpointSwitchId = newSwT.ep1.dpId
                            payload.subFlows[1].endpointSwitchId = newSwT.ep2.dpId
                            def port1 = topology
                                    .getAllowedPortsForSwitch(topology.find(newSwT.ep1.dpId))[-1]
                            def port2 = topology
                                    .getAllowedPortsForSwitch(topology.find(newSwT.ep1.dpId))[-1]
                            def portS = topology
                                    .getAllowedPortsForSwitch(topology.find(newSwT.shared.dpId))[-1]
                            payload.subFlows[0].endpointPort = port1
                            payload.subFlows[1].endpointPort = port2
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
                                                       .flowId(payload.subFlows[0].haSubFlowId)
                                                       .build(),
                                               HaSubFlowPatchPayload.builder()
                                                       .endpoint(HaFlowPatchEndpoint.builder()
                                                               .switchId(newSwT.ep2.dpId)
                                                               .portNumber(port2)
                                                               .build())
                                                       .flowId(payload.subFlows[1].haSubFlowId)
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

    def "User cannot update an HA-Flow #data.descr"() {
        given: "Existing HA-Flow"
        def swT = topologyHelper.switchTriplets[0]
        def haFlow = haFlowFactory.getRandom(swT)

        haFlow.tap(data.updateClosure)
        def update = haFlow.convertToUpdateRequest()

        when: "Try to update the HA-Flow with invalid payload"
        haFlow.update(update)

        then: "Error is received"
        def exc = thrown(HttpClientErrorException)
        new HaFlowNotUpdatedExpectedError(data.errorDescription).matches(exc)

        and: "And involved switches pass validation"
        def involvedSwitchIds = haFlow.retrievedAllEntityPaths().getInvolvedSwitches()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitchIds).isEmpty()

        and: "HA-Flow pass validation"
        haFlow.validate().asExpected

        where: data << [
                [
                        descr: "with non-existent subflowId",
                        updateClosure: { HaFlowExtended payload ->
                            payload.subFlows[0].haSubFlowId += "non-existent"
                            def allowedPorts = topology.getAllowedPortsForSwitch(topology.find(
                                    payload.subFlows[0].endpointSwitchId)) - payload.subFlows[0].endpointPort -
                                    payload.subFlows[1].endpointPort
                            payload.subFlows[0].endpointPort = allowedPorts[0]
                            setRandomVlans(payload) // to do not conflict with existing sub flows
                        },
                        errorDescription: ~/Invalid sub flow IDs: .*\. Valid sub flows IDs are: .*?/
                ],
                [
                        descr: "with subflowId not specified",
                        updateClosure: { HaFlowExtended payload ->
                            payload.subFlows[1].haSubFlowId = null
                        },
                        errorDescription: ~/The sub-flow of .* has no sub-flow id provided/
                ],
                [
                        descr: "to one switch HA-Flow",
                        updateClosure: { HaFlowExtended payload ->
                            payload.subFlows[0].endpointSwitchId = payload.getSharedEndpoint().switchId
                            payload.subFlows[1].endpointSwitchId = payload.getSharedEndpoint().switchId
                        },
                        errorDescription: ~/The ha-flow.* ? is one switch flow\. \
At least one of subflow endpoint switch id must differ from shared endpoint switch.* ?/
                ]
        ]
    }

    private void setRandomVlans(HaFlowExtended payload) {
        payload.sharedEndpoint.vlanId = flowHelperV2.randomVlan([payload.sharedEndpoint.vlanId])
        payload.subFlows.forEach { it.endpointVlan = flowHelperV2.randomVlan([it.endpointVlan]) }
    }

    def "User cannot partial update an HA-Flow with #data.descr"() {
        given: "Existing HA-Flow"
        def swT = topologyHelper.switchTriplets[0]
        def haFlow = haFlowFactory.getRandom(swT)
        HaFlowPatchPayload patch = data.buildPatchRequest(haFlow)


        when: "Try to partial update the HA-Flow with invalid payload"
        haFlow.partialUpdate(patch)

        then: "Error is received"
        def exc = thrown(HttpClientErrorException)
        new HaFlowNotUpdatedExpectedError(data.errorDescrPattern).matches(exc)

        and: "And involved switches pass validation"
        def involvedSwitchIds = haFlow.retrievedAllEntityPaths().getInvolvedSwitches()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitchIds).isEmpty()

        and: "HA-Flow pass validation"
        haFlow.validate().asExpected

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
                                                       .flowId(payload.subFlows[1].haSubFlowId)
                                                       .build()])
                                    .build()
                        },
                        errorDescrPattern: ~/HA-flow .*? has no sub flow .*?/
                ],
                [
                        descr: "switch conflict in request",
                        buildPatchRequest: { HaFlowExtended payload ->
                            def subFlow1 = payload.subFlows[0]
                            def subFlow2 = payload.subFlows[1]
                            def endpoint = HaFlowPatchEndpoint.builder()
                                    .switchId(subFlow1.endpointSwitchId)
                                    .portNumber(subFlow1.endpointPort)
                                    .vlanId(subFlow1.endpointVlan + 1)
                                    .innerVlanId(0)
                                    .build()
                            return HaFlowPatchPayload.builder()
                                    .subFlows([HaSubFlowPatchPayload.builder()
                                                       .flowId(subFlow1.haSubFlowId)
                                                       .endpoint(endpoint)
                                                       .build(),
                                               HaSubFlowPatchPayload.builder()
                                                       .flowId(subFlow2.haSubFlowId)
                                                       .endpoint(endpoint)
                                                       .build()])
                                    .build()
                        },
                        errorDescrPattern: ~/The sub-flows .* and .* have endpoint conflict: .*/
                ],
                [
                        descr: "switch conflict after update",
                        buildPatchRequest: { HaFlowExtended payload ->
                            def subFlow1 = payload.subFlows[0]
                            def subFlow2 = payload.subFlows[1]
                            return HaFlowPatchPayload.builder()
                                    .subFlows([
                                            HaSubFlowPatchPayload.builder()
                                                    .flowId(subFlow2.haSubFlowId)
                                                    .endpoint(HaFlowPatchEndpoint.builder()
                                                            .switchId(subFlow1.endpointSwitchId)
                                                            .portNumber(subFlow1.endpointPort)
                                                            .vlanId(subFlow1.endpointVlan)
                                                            .innerVlanId(subFlow1.endpointInnerVlan)
                                                            .build())
                                                    .build()])
                                    .build()
                        },
                        errorDescrPattern: ~/The sub-flows .* and .* have endpoint conflict: .*/
                ],
                [
                        descr: "different inner vlans of sub flows on one switch",
                        buildPatchRequest: { HaFlowExtended payload ->
                            def subFlow1 = payload.subFlows[0]
                            def subFlow2 = payload.subFlows[1]
                            return HaFlowPatchPayload.builder()
                                    .subFlows([
                                            HaSubFlowPatchPayload.builder()
                                                    .flowId(subFlow2.haSubFlowId)
                                                    .endpoint(HaFlowPatchEndpoint.builder()
                                                            .switchId(subFlow1.endpointSwitchId)
                                                            .portNumber(subFlow1.endpointPort)
                                                            .vlanId(subFlow1.endpointVlan)
                                                            .innerVlanId(subFlow1.endpointInnerVlan + 1)
                                                            .build())
                                                    .build()])
                                    .build()
                        },
                        errorDescrPattern: ~/To have ability to use double vlan tagging for both sub flow destination \
endpoints which are placed on one switch .* you must set equal inner vlan for both endpoints.*/
                ]
        ]
    }
}
