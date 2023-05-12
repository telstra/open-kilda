package org.openkilda.functionaltests.spec.flows.haflows

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static groovyx.gpars.GParsPool.withPool
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.openkilda.messaging.error.MessageError
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.haflows.HaFlow
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchEndpoint
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload
import org.openkilda.northbound.dto.v2.haflows.HaSubFlowPatchPayload

import com.shazam.shazamcrest.matcher.CustomisableMatcher
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

@Slf4j
@Narrative("Verify update and partial update operations on ha-flows.")
class HaFlowUpdateSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    HaFlowHelper haFlowHelper

    @Tidy
    def "User can update #data.descr of a ha-flow"() {
        assumeTrue(useMultitable, "HA-flow operations require multiTable switch mode")
        given: "Existing ha-flow"
        def swT = topologyHelper.switchTriplets[0]
        def haFlowRequest = haFlowHelper.randomHaFlow(swT)
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)
        haFlow.tap(data.updateClosure)
        def update = haFlowHelper.convertToUpdate(haFlow)

        when: "Update the ha-flow"
        def updateResponse = haFlowHelper.updateHaFlow(haFlow.haFlowId, update)
        def ignores = ["subFlows.timeUpdate", "subFlows.status", "timeUpdate", "status"]
        ignores.addAll(data.additionalIgnores)

        then: "Requested updates are reflected in the response and in 'get' API"
        expect updateResponse, sameBeanAs(haFlow, ignores)
        expect northboundV2.getHaFlow(haFlow.haFlowId), sameBeanAs(haFlow, ignores)

        and: "And involved switches pass validation"
        withPool {
            haFlowHelper.getInvolvedSwitches(haFlow.haFlowId).eachParallel { SwitchId switchId ->
                assert northboundV2.validateSwitch(switchId).isAsExpected()
            }
        }

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)

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
                        },
                        additionalIgnores: []
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
                        },
                        additionalIgnores: ["yPoint"]
                ],
                [
                        descr: "[without any changes in update request]",
                        updateClosure: { },
                        additionalIgnores: []
                ]
        ]
    }

    @Tidy
    def "User can update ha-flow where one of subflows has both ends on shared switch"() {
        assumeTrue(useMultitable, "HA-flow operations require multiTable switch mode")
        given: "Existing ha-flow where one of subflows has both ends on shared switch"
        def switchTriplet = topologyHelper.getSwitchTriplets(true, true)
                .find{it.ep1 == it.shared && it.ep2 != it.shared}
        def haFlowRequest = haFlowHelper.randomHaFlow(switchTriplet)
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)
        haFlow.setDescription("new description")
        def endPoint = haFlow.getSubFlows().get(0).getEndpoint()
        endPoint.setPortNumber(topology.getAllowedPortsForSwitch(topology.find(endPoint.getSwitchId())).first())
        def update = haFlowHelper.convertToUpdate(haFlow)

        when: "Update the ha-flow"
        def updateResponse = haFlowHelper.updateHaFlow(haFlow.haFlowId, update)
        def ignores = ["subFlows.timeUpdate", "subFlows.status", "timeUpdate", "status"]

        then: "Requested updates are reflected in the response and in 'get' API"
        expect updateResponse, sameBeanAs(haFlow, ignores)
        expect northboundV2.getHaFlow(haFlow.haFlowId), sameBeanAs(haFlow, ignores)

        and: "And involved switches pass validation"
        withPool {
            haFlowHelper.getInvolvedSwitches(haFlow.haFlowId).eachParallel { SwitchId switchId ->
                assert northboundV2.validateSwitch(switchId).isAsExpected()
            }
        }

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
    }

    @Tidy
    def "User can partially update #data.descr of a ha-flow"() {
        assumeTrue(useMultitable, "HA-flow operations require multiTable switch mode")
        given: "Existing ha-flow"
        def swT = topologyHelper.switchTriplets.find { it.ep1 != it.ep2 }
        def haFlowRequest = haFlowHelper.randomHaFlow(swT)
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)
        def patch = data.buildPatchRequest(haFlow)

        when: "Partial update the ha-flow"
        def updateResponse = haFlowHelper.partialUpdateHaFlow(haFlow.haFlowId, patch)
        def ignores = ["subFlows.timeUpdate", "subFlows.status", "timeUpdate", "status"]

        then: "Requested updates are reflected in the response and in 'get' API"
        expect updateResponse, sameBeanAs(haFlow, ignores)
        expect northboundV2.getHaFlow(haFlow.haFlowId), sameBeanAs(haFlow, ignores)

        and: "And involved switches pass validation"
        withPool {
            haFlowHelper.getInvolvedSwitches(haFlow.haFlowId).eachParallel { SwitchId switchId ->
                assert northboundV2.validateSwitch(switchId).isAsExpected()
            }
        }

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)

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

    @Tidy
    def "User cannot update a ha-flow #data.descr"() {
        assumeTrue(useMultitable, "HA-flow operations require multiTable switch mode")
        given: "Existing ha-flow"
        def swT = topologyHelper.switchTriplets[0]
        def haFlowRequest = haFlowHelper.randomHaFlow(swT)
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)
        haFlow.tap(data.updateClosure)
        def update = haFlowHelper.convertToUpdate(haFlow)
        def involvedSwitchIds = haFlowHelper.getInvolvedSwitches(haFlow.haFlowId)

        when: "Try to update the ha-flow with invalid payload"
        northboundV2.updateHaFlow(haFlow.haFlowId, update)

        then: "Error is received"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == data.errorStatusCode
        exc.responseBodyAsString.to(MessageError).with {
            assert errorMessage == "Couldn't update HA-flow"
            assertThat(errorDescription).matches(data.errorDescrPattern)
        }

        and: "And involved switches pass validation"
        withPool {
            involvedSwitchIds.eachParallel { SwitchId switchId ->
                assert northboundV2.validateSwitch(switchId).isAsExpected()
            }
        }

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)

        where: data << [
                [
                        descr: "with non-existent subflowId",
                        updateClosure: { HaFlow payload ->
                            payload.subFlows[0].flowId += "non-existent"
                            def allowedPorts = topology.getAllowedPortsForSwitch(topology.find(
                                    payload.subFlows[0].endpoint.switchId)) - payload.subFlows[0].endpoint.portNumber -
                                    payload.subFlows[1].endpoint.portNumber
                            payload.subFlows[0].endpoint.portNumber = allowedPorts[0]
                            setRandomVlans(payload) // to do not conflict with existing sub flows
                        },
                        errorStatusCode: HttpStatus.BAD_REQUEST,
                        errorDescrPattern: /Invalid sub flow IDs: .*?\. Valid sub flows IDs are:.*?/
                ],
                [
                        descr: "with subflowId not specified",
                        updateClosure: { HaFlow payload ->
                            payload.subFlows[1].flowId = null
                        },
                        errorStatusCode: HttpStatus.BAD_REQUEST,
                        errorDescrPattern: /The sub-flow of ha-flow .*? has no sub-flow id provided.*?/
                ],
                [
                        descr: "to one switch ha-flow",
                        updateClosure: { HaFlow payload ->
                            payload.subFlows[0].endpoint.switchId = payload.getSharedEndpoint().switchId
                            payload.subFlows[1].endpoint.switchId = payload.getSharedEndpoint().switchId
                        },
                         errorStatusCode: HttpStatus.BAD_REQUEST,
                        errorDescrPattern: /The ha-flow .*? is one switch flow\..*?/
                ]
        ]
    }

    private void setRandomVlans(HaFlow payload) {
        payload.sharedEndpoint.vlanId = haFlowHelper.randomVlan(payload.sharedEndpoint.vlanId)
        payload.subFlows.forEach { it.endpoint.vlanId = haFlowHelper.randomVlan(it.endpoint.vlanId) }
    }

    @Tidy
    def "User cannot partial update a ha-flow with #data.descr"() {
        assumeTrue(useMultitable, "HA-flow operations require multiTable switch mode")
        given: "Existing ha-flow"
        def swT = topologyHelper.switchTriplets[0]
        def haFlowRequest = haFlowHelper.randomHaFlow(swT)
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)
        def patch = data.buildPatchRequest(haFlow)
        def involvedSwitchIds = haFlowHelper.getInvolvedSwitches(haFlow.haFlowId)

        when: "Try to partial update the ha-flow with invalid payload"
        northboundV2.partialUpdateHaFlow(haFlow.haFlowId, patch)

        then: "Error is received"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == data.errorStatusCode
        exc.responseBodyAsString.to(MessageError).with {
            assert errorMessage == data.errorMessage
            assertThat(errorDescription).matches(data.errorDescrPattern)
        }

        and: "And involved switches pass validation"
        withPool {
            involvedSwitchIds.eachParallel { SwitchId switchId ->
                assert northboundV2.validateSwitch(switchId).isAsExpected()
            }
        }

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)

        where: data << [
                [
                        descr: "non-existent subflowId",
                        buildPatchRequest: { HaFlow payload ->
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
                        errorStatusCode: HttpStatus.BAD_REQUEST,
                        errorMessage: "Couldn't update HA-flow",
                        errorDescrPattern: /HA-flow .*? has no sub flow .*?/
                ],
                [
                        descr: "switch conflict in request",
                        buildPatchRequest: { HaFlow payload ->
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
                        errorStatusCode: HttpStatus.BAD_REQUEST,
                        errorMessage: "Couldn't update HA-flow",
                        errorDescrPattern: /The sub-flows .* and .* have endpoint conflict: .*/
                ],
                [
                        descr: "switch conflict after update",
                        buildPatchRequest: { HaFlow payload ->
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
                        errorStatusCode  : HttpStatus.BAD_REQUEST,
                        errorMessage     : "Couldn't update HA-flow",
                        errorDescrPattern: /The sub-flows .* and .* have endpoint conflict: .*/
                ],
                [
                        descr: "different inner vlans of sub flows on one switch",
                        buildPatchRequest: { HaFlow payload ->
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
                        errorStatusCode  : HttpStatus.BAD_REQUEST,
                        errorMessage     : "Couldn't update HA-flow",
                        errorDescrPattern: "To have ability to use double vlan tagging for both sub flow destination " +
                                "endpoints which are placed on one switch .* you must set equal inner vlan for both endpoints.*"
                ]
        ]
    }

    static <T> CustomisableMatcher<T> sameBeanAs(final T expected, List<String> ignores) {
        def matcher = sameBeanAs(expected)
        ignores.each { matcher.ignoring(it) }
        return matcher
    }
}
