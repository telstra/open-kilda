package org.openkilda.functionaltests.spec.flows.haflows

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.openkilda.messaging.error.MessageError
import org.openkilda.northbound.dto.v2.haflows.HaFlow
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchEndpoint
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload
import org.openkilda.northbound.dto.v2.haflows.HaSubFlowPatchPayload

import com.shazam.shazamcrest.matcher.CustomisableMatcher
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.Shared

@Slf4j
@Narrative("Verify update and partial update operations on ha-flows.")
@Ignore("""At this moment HA-flow update operations is just an API stub which doesn't updated switch rules." +
        It means that HA-flow delete operations wouldn't delete rules from switches which HA-flow has before update.
        Update HA-flow spec is temporarily ignored until HA-flow update operation is able to update switch rules""")
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
        def oldSharedSwitch = haFlow.sharedEndpoint.switchId
        haFlow.tap(data.updateClosure)
        def update = haFlowHelper.convertToUpdate(haFlow)

        when: "Update the ha-flow"
        def updateResponse = haFlowHelper.updateHaFlow(haFlow.haFlowId, update)
        def ignores = ["subFlows.timeUpdate", "subFlows.status", "timeUpdate", "status"]
        ignores.addAll(data.additionalIgnores)

        then: "Requested updates are reflected in the response and in 'get' API"
        expect updateResponse, sameBeanAs(haFlow, ignores)
        expect northboundV2.getHaFlow(haFlow.haFlowId), sameBeanAs(haFlow, ignores)

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
//                       "subFlows.description" /* https://github.com/telstra/open-kilda/issues/4984 */]

        then: "Requested updates are reflected in the response and in 'get' API"
        expect updateResponse, sameBeanAs(haFlow, ignores)
        expect northboundV2.getHaFlow(haFlow.haFlowId), sameBeanAs(haFlow, ignores)

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

        when: "Try to update the ha-flow with invalid payload"
        northboundV2.updateHaFlow(haFlow.haFlowId, update)

        then: "Error is received"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == data.errorStatusCode
        exc.responseBodyAsString.to(MessageError).with {
            assert errorMessage == "Couldn't update HA-flow"
            assertThat(errorDescription).matches(data.errorDescrPattern)
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
                        },
                        errorStatusCode: HttpStatus.BAD_REQUEST,
                        errorDescrPattern: /HA-flow .*? has no sub flow .*?/
                ],
                [
                        descr: "with subflowId not specified",
                        updateClosure: { HaFlow payload ->
                            payload.subFlows[1].flowId = null
                        },
                        errorStatusCode: HttpStatus.BAD_REQUEST,
                        errorDescrPattern: /HA-flow .*? has no sub flow .*?/
                ],
                //TODO enable when HA-flow update validation will be ready
                //[
                //        descr: "to one switch ha-flow",
                //        updateClosure: { HaFlow payload ->
                //            payload.subFlows[0].endpoint.switchId = payload.getSharedEndpoint().switchId
                //            payload.subFlows[1].endpoint.switchId = payload.getSharedEndpoint().switchId
                //        },
                //        errorStatusCode: HttpStatus.BAD_REQUEST,
                //        errorDescrPattern: /The ha-flow .*? is one switch flow\..*?/
                //]
        ]
    }

    @Tidy
    def "User cannot partial update a ha-flow with #data.descr"() {
        assumeTrue(useMultitable, "HA-flow operations require multiTable switch mode")
        given: "Existing ha-flow"
        def swT = topologyHelper.switchTriplets[0]
        def haFlowRequest = haFlowHelper.randomHaFlow(swT)
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)
        def patch = data.buildPatchRequest(haFlow)

        when: "Try to partial update the ha-flow with invalid payload"
        northboundV2.partialUpdateHaFlow(haFlow.haFlowId, patch)

        then: "Error is received"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == data.errorStatusCode
        exc.responseBodyAsString.to(MessageError).with {
            assert errorMessage == data.errorMessage
            assertThat(errorDescription).matches(data.errorDescrPattern)
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
                ]
        ]
    }


    static <T> CustomisableMatcher<T> sameBeanAs(final T expected, List<String> ignores) {
        def matcher = sameBeanAs(expected)
        ignores.each { matcher.ignoring(it) }
        return matcher
    }
}
