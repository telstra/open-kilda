package org.openkilda.functionaltests.spec.flows.yflows

import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.PathComputationStrategy

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.messaging.error.MessageError
import org.openkilda.northbound.dto.v2.flows.FlowPatchEndpoint
import org.openkilda.northbound.dto.v2.yflows.SubFlowPatchPayload
import org.openkilda.northbound.dto.v2.yflows.YFlow
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchPayload
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchSharedEndpoint
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchSharedEndpointEncapsulation
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import com.shazam.shazamcrest.matcher.CustomisableMatcher
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

@Slf4j
@Narrative("Verify update and partial update operations on y-flows.")
class YFlowUpdateSpec extends HealthCheckSpecification {
    @Autowired @Shared
    YFlowHelper yFlowHelper

    @Tidy
    def "User can update #data.descr of a y-flow"() {
        given: "Existing y-flow"
        def swT = topologyHelper.switchTriplets[0]
        def yFlowRequest = yFlowHelper.randomYFlow(swT, false)
        def yFlow = yFlowHelper.addYFlow(yFlowRequest)
        def oldSharedSwitch = yFlow.sharedEndpoint.switchId
        yFlow.tap(data.updateClosure)
        List<Switch> involvedSwitches = pathHelper.getInvolvedYSwitches(yFlow.YFlowId)
        def update = yFlowHelper.convertToUpdate(yFlow)

        when: "Update the y-flow"
        def updateResponse = yFlowHelper.updateYFlow(yFlow.YFlowId, update)
        //update involved switches after update
        involvedSwitches.addAll(pathHelper.getInvolvedYSwitches(yFlow.YFlowId))
        involvedSwitches.unique { it.dpId }
        def ignores = ["subFlows.timeUpdate", "subFlows.status", "timeUpdate", "status"]
        ignores.addAll(data.additionalIgnores)
        // https://github.com/telstra/open-kilda/issues/3411
        northbound.synchronizeSwitch(oldSharedSwitch, true)

        then: "Requested updates are reflected in the response and in 'get' API"
        expect updateResponse, sameBeanAs(yFlow, ignores)
        expect northboundV2.getYFlow(yFlow.YFlowId), sameBeanAs(yFlow, ignores)

        and: "All related switches have no discrepancies"
        involvedSwitches.each { sw ->
            northboundV2.validateSwitch(sw.dpId).verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
            northboundV2.validateSwitch(sw.dpId).verifyMeterSectionsAreEmpty(["missing", "excess", "misconfigured"])
        }

        cleanup:
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)

        where: data << [
                [
                        descr: "shared port and subflow ports",
                        updateClosure: { YFlow payload ->
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
                        updateClosure: { YFlow payload ->
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
    def "User can update y-flow where one of subflows has both ends on shared switch"() {
        given: "Existing y-flow where one of subflows has both ends on shared switch"
        def switchTriplet = topologyHelper.getSwitchTriplets(true, true)
                .find{it.ep1 == it.shared && it.ep2 != it.shared}
        def yFlowRequest = yFlowHelper.randomYFlow(switchTriplet, false)
        def yFlow = yFlowHelper.addYFlow(yFlowRequest)
        def oldSharedSwitch = yFlow.sharedEndpoint.switchId
        yFlow.setDescription("new description")
        def endPoint = yFlow.getSubFlows().get(0).getEndpoint()
        endPoint.setPortNumber(topology.getAllowedPortsForSwitch(topology.find(endPoint.getSwitchId())).first())
        List<Switch> involvedSwitches = pathHelper.getInvolvedYSwitches(yFlow.YFlowId)
        def update = yFlowHelper.convertToUpdate(yFlow)

        when: "Update the y-flow"
        def updateResponse = yFlowHelper.updateYFlow(yFlow.YFlowId, update)
        //update involved switches after update
        involvedSwitches.addAll(pathHelper.getInvolvedYSwitches(yFlow.YFlowId))
        involvedSwitches.unique { it.dpId }
        def ignores = ["subFlows.timeUpdate", "subFlows.status", "timeUpdate", "status",
                       "subFlows.description" /* https://github.com/telstra/open-kilda/issues/4984 */]

        then: "Requested updates are reflected in the response and in 'get' API"
        expect updateResponse, sameBeanAs(yFlow, ignores)
        expect northboundV2.getYFlow(yFlow.YFlowId), sameBeanAs(yFlow, ignores)

        and: "All related switches have no discrepancies"
        involvedSwitches.each { sw ->
            northboundV2.validateSwitch(sw.dpId).verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
            northboundV2.validateSwitch(sw.dpId).verifyMeterSectionsAreEmpty(["missing", "excess", "misconfigured"])
        }

        cleanup:
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
    }

    @Tidy
    def "User can partially update fields of one-switch y-flow"() {
        given: "Existing one-switch y-flow"
        def singleSwitch = topologyHelper.getRandomSwitch()
        def switchId = singleSwitch.dpId
        def yFlowRequest = yFlowHelper.singleSwitchYFlow(singleSwitch, false)
        yFlowRequest.setMaxLatency(50)
        yFlowRequest.setMaxLatencyTier2(100)
        def yFlow = yFlowHelper.addYFlow(yFlowRequest)
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
        yFlow.setEncapsulationType(patch.getEncapsulationType())
        yFlow.setMaxLatency(patch.getMaxLatency())
        yFlow.setMaxLatencyTier2(patch.getMaxLatencyTier2())
        yFlow.setIgnoreBandwidth(patch.getIgnoreBandwidth())
        yFlow.setPinned(patch.getPinned())
        yFlow.setPriority(patch.getPriority())
        yFlow.setStrictBandwidth(patch.getStrictBandwidth())
        yFlow.setDescription(patch.getDescription())


        when: "Partial update the y-flow"
        def updateResponse = yFlowHelper.partialUpdateYFlow(yFlow.YFlowId, patch)
        def ignores = ["subFlows", "timeUpdate", "status"]

        then: "Requested updates are reflected in the response and in 'get' API"
        expect updateResponse, sameBeanAs(yFlow, ignores)
        expect northboundV2.getYFlow(yFlow.YFlowId), sameBeanAs(yFlow, ignores)

        and: "All related switches have no discrepancies"
        northboundV2.validateSwitch(switchId).verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
        northboundV2.validateSwitch(switchId).verifyMeterSectionsAreEmpty(["missing", "excess", "misconfigured"])


        cleanup:
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
    }

    @Tidy
    def "User can partially update #data.descr of a y-flow"() {
        given: "Existing y-flow"
        def swT = topologyHelper.switchTriplets.find { it.ep1 != it.ep2 }
        def yFlowRequest = yFlowHelper.randomYFlow(swT, false)
        def yFlow = yFlowHelper.addYFlow(yFlowRequest)
        List<Switch> involvedSwitches = pathHelper.getInvolvedYSwitches(yFlow.YFlowId)
        def oldSharedSwitch = yFlow.sharedEndpoint.switchId
        def patch = data.buildPatchRequest(yFlow)

        when: "Partial update the y-flow"
        def updateResponse = yFlowHelper.partialUpdateYFlow(yFlow.YFlowId, patch)
        //update involved switches after update
        involvedSwitches.addAll(pathHelper.getInvolvedYSwitches(yFlow.YFlowId))
        involvedSwitches.unique { it.dpId }
        def ignores = ["subFlows.timeUpdate", "subFlows.status", "timeUpdate", "status"]
        ignores.addAll(data.additionalIgnores)
        // https://github.com/telstra/open-kilda/issues/3411
        northbound.synchronizeSwitch(oldSharedSwitch, true)

        then: "Requested updates are reflected in the response and in 'get' API"
        expect updateResponse, sameBeanAs(yFlow, ignores)
        expect northboundV2.getYFlow(yFlow.YFlowId), sameBeanAs(yFlow, ignores)

        and: "All related switches have no discrepancies"
        involvedSwitches.each { sw ->
            northboundV2.validateSwitch(sw.dpId).verifyRuleSectionsAreEmpty(["missing", "excess", "misconfigured"])
            northboundV2.validateSwitch(sw.dpId).verifyMeterSectionsAreEmpty(["missing", "excess", "misconfigured"])
        }

        cleanup:
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)

        //buildPatchRequest in addition to providing a patch payload should also updated the yFlow object
        //in order to reflect the expect result after update
        where: data << [
                [
                        descr: "shared port and subflow ports",
                        buildPatchRequest: { YFlow payload ->
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
                        additionalIgnores: []
                ],
                [
                        descr: "shared switch and subflow switches",
                        buildPatchRequest: { YFlow payload ->
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
                        additionalIgnores: ["yPoint"]
                ],
                [
                        descr: "[without any changes in update request]",
                        buildPatchRequest: {
                            YFlowPatchPayload.builder().build()
                        },
                        additionalIgnores: []
                ]
        ]
    }

    @Tidy
    def "User cannot update a y-flow with #data.descr"() {
        given: "Existing y-flow"
        def swT = topologyHelper.switchTriplets[0]
        def yFlowRequest = yFlowHelper.randomYFlow(swT, false)
        def yFlow = yFlowHelper.addYFlow(yFlowRequest)
        yFlow.tap(data.updateClosure)
        def update = yFlowHelper.convertToUpdate(yFlow)

        when: "Try to update the y-flow with invalid payload"
        northboundV2.updateYFlow(yFlow.YFlowId, update)

        then: "Error is received"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == data.errorStatusCode
        exc.responseBodyAsString.to(MessageError).with {
            assert errorMessage == "Could not update y-flow"
            assertThat(errorDescription).matches(data.errorDescrPattern)
        }

        cleanup:
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)

        where: data << [
                [
                        descr: "non-existent subflowId",
                        updateClosure: { YFlow payload ->
                            payload.subFlows[0].flowId += "non-existent"
                            def allowedPorts = topology.getAllowedPortsForSwitch(topology.find(
                                    payload.subFlows[0].endpoint.switchId)) - payload.subFlows[0].endpoint.portNumber -
                                    payload.subFlows[1].endpoint.portNumber
                            payload.subFlows[0].endpoint.portNumber = allowedPorts[0]
                        },
                        errorStatusCode: HttpStatus.CONFLICT,
                        errorDescrPattern: /Requested flow '.*?' conflicts with existing flow '.*'. Details: .*/
                ],
                [
                        descr: "subflowId not specified",
                        updateClosure: { YFlow payload ->
                            payload.subFlows[1].flowId = null
                        },
                        errorStatusCode: HttpStatus.CONFLICT,
                        errorDescrPattern: /Requested flow '.*?' conflicts with existing flow '.*'. Details: .*/
                ],
                [
                        descr: "only 1 subflow in payload",
                        updateClosure: { YFlow payload ->
                            payload.subFlows.removeLast()
                        },
                        errorStatusCode: HttpStatus.BAD_REQUEST,
                        errorDescrPattern: /The y-flow .* must have at least 2 sub flows/
                ],
                [
                        descr: "conflict after update",
                        updateClosure: { YFlow payload ->
                            payload.subFlows[0].sharedEndpoint.vlanId = payload.subFlows[1].sharedEndpoint.vlanId
                        },
                        errorStatusCode: HttpStatus.BAD_REQUEST,
                        errorDescrPattern: /The sub-flows .* and .* have shared endpoint conflict: .*/
                ]
        ]
    }

    @Tidy
    def "User cannot partial update a y-flow with #data.descr"() {
        given: "Existing y-flow"
        def swT = topologyHelper.switchTriplets[0]
        def yFlowRequest = yFlowHelper.randomYFlow(swT, false)
        def yFlow = yFlowHelper.addYFlow(yFlowRequest)
        def patch = data.buildPatchRequest(yFlow)

        when: "Try to partial update the y-flow with invalid payload"
        northboundV2.partialUpdateYFlow(yFlow.YFlowId, patch)

        then: "Error is received"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == data.errorStatusCode
        exc.responseBodyAsString.to(MessageError).with {
            assert errorMessage == data.errorMessage
            assertThat(errorDescription).matches(data.errorDescrPattern)
        }

        cleanup:
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)

        where: data << [
                [
                        descr: "non-existent subflowId",
                        buildPatchRequest: { YFlow payload ->
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
                        errorDescrPattern: /There is no sub-flows with sub-flow id: non-existent-flowid/
                ],
                [
                        descr: "only 1 subflow specified",
                        buildPatchRequest: { YFlow payload ->
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
                        errorDescrPattern: /The y-flow .* must have at least 2 sub flows/
                ],
                [
                        descr: "switch conflict after update",
                        buildPatchRequest: { YFlow payload ->
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
                        errorDescrPattern: /The sub-flows .* and .* have shared endpoint conflict: .*/
                ]
        ]
    }


    static <T> CustomisableMatcher<T> sameBeanAs(final T expected, List<String> ignores) {
        def matcher = sameBeanAs(expected)
        ignores.each { matcher.ignoring(it) }
        return matcher
    }
}
