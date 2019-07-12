package org.openkilda.functionaltests.spec.flows

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowCreatePayload
import org.openkilda.messaging.payload.flow.FlowEndpointPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.flows.SwapFlowPayload
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.tools.FlowTrafficExamBuilder

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Ignore
import spock.lang.Unroll

import javax.inject.Provider

class SwapEndpointSpec extends HealthCheckSpecification {

    @Autowired
    Provider<TraffExamService> traffExamProvider

    @Unroll
    def "Able to swap #endpointsPart (#description) for two flows with the same source and destination switches"() {
        given: "Two flows with the same source and destination switches"
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        when: "Try to swap #endpointsPart for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(flow1Src),
                        flowHelper.toFlowEndpointV2(flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(flow2Src),
                        flowHelper.toFlowEndpointV2(flow2Dst)))

        then: "#endpointsPart.capitalize() are successfully swapped"
        verifyEndpoints(response, flow1Src, flow1Dst, flow2Src, flow2Dst)
        verifyEndpoints(flow1, flow2, flow1Src, flow1Dst, flow2Src, flow2Dst)

        and: "Flows validation doesn't show any rule discrepancies"
        validateFlows(flow1, flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(switchPair)

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        endpointsPart << ["vlans", "ports", "switches"]
        description << ["src1 <-> dst2, dst1 <-> src2"] * 3
        switchPair << [getTopologyHelper().getNotNeighboringSwitchPair()] * 3
        flow1 << [getFirstFlow(switchPair, switchPair)] * 3
        flow2 << [getSecondFlow(switchPair, switchPair, flow1)] * 3
        [flow1Src, flow1Dst, flow2Src, flow2Dst] << [
                [changePropertyValue(flow1.source, "vlanId", flow2.destination.vlanId),
                 changePropertyValue(flow1.destination, "vlanId", flow2.source.vlanId),
                 changePropertyValue(flow2.source, "vlanId", flow1.destination.vlanId),
                 changePropertyValue(flow2.destination, "vlanId", flow1.source.vlanId)],

                [changePropertyValue(flow1.source, "portNumber", flow2.destination.portNumber),
                 changePropertyValue(flow1.destination, "portNumber", flow2.source.portNumber),
                 changePropertyValue(flow2.source, "portNumber", flow1.destination.portNumber),
                 changePropertyValue(flow2.destination, "portNumber", flow1.source.portNumber)],

                [changePropertyValue(flow1.source, "datapath", flow2.destination.datapath),
                 changePropertyValue(flow1.destination, "datapath", flow2.source.datapath),
                 changePropertyValue(flow2.source, "datapath", flow1.destination.datapath),
                 changePropertyValue(flow2.destination, "datapath", flow1.source.datapath)],
        ]
    }

    @Unroll
    def "Able to swap endpoints (#description) for two flows with the same source and destination switches"() {
        given: "Two flows with the same source and destination switches"
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        when: "Try to swap endpoints for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(flow1Src),
                        flowHelper.toFlowEndpointV2(flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(flow2Src),
                        flowHelper.toFlowEndpointV2(flow2Dst)))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, flow1Src, flow1Dst, flow2Src, flow2Dst)
        verifyEndpoints(flow1, flow2, flow1Src, flow1Dst, flow2Src, flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(flow1, flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(switchPair)

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        description << ["src1 <-> src2", "dst1 <-> dst2", "src1 <-> dst2", "dst1 <-> src2"]
        switchPair << [getTopologyHelper().getNotNeighboringSwitchPair()] * 4
        flow1 << [getFirstFlow(switchPair, switchPair)] * 4
        flow2 << [getSecondFlow(switchPair, switchPair, flow1)] * 4
        [flow1Src, flow1Dst, flow2Src, flow2Dst] << [
                [flow2.source, flow1.destination, flow1.source, flow2.destination],
                [flow1.source, flow2.destination, flow2.source, flow1.destination],
                [flow2.destination, flow1.destination, flow2.source, flow1.source],
                [flow1.source, flow2.source, flow1.destination, flow2.destination]
        ]
    }

    @Unroll
    def "Able to swap #endpointsPart (#description) for two flows with the same source and different destination \
switches"() {
        given: "Two flows with the same source and different destination switches"
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        when: "Try to swap #endpointsPart for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(flow1Src),
                        flowHelper.toFlowEndpointV2(flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(flow2Src),
                        flowHelper.toFlowEndpointV2(flow2Dst)))

        then: "#endpointsPart.capitalize() are successfully swapped"
        verifyEndpoints(response, flow1Src, flow1Dst, flow2Src, flow2Dst)
        verifyEndpoints(flow1, flow2, flow1Src, flow1Dst, flow2Src, flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(flow1, flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(flow1SwitchPair)
        validateSwitches(flow2SwitchPair)

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        endpointsPart << ["vlans", "ports", "switches"]
        description << ["src1 <-> dst2, dst1 <-> src2"] * 3
        flow1SwitchPair << [getTopologyHelper().getNotNeighboringSwitchPair()] * 3
        flow2SwitchPair << [getHalfDifferentNotNeighboringSwitchPair(flow1SwitchPair, "src")] * 3
        flow1 << [getFirstFlow(flow1SwitchPair, flow2SwitchPair)] * 3
        flow2 << [getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)] * 3
        [flow1Src, flow1Dst, flow2Src, flow2Dst] << [
                [changePropertyValue(flow1.source, "vlanId", flow2.destination.vlanId),
                 changePropertyValue(flow1.destination, "vlanId", flow2.source.vlanId),
                 changePropertyValue(flow2.source, "vlanId", flow1.destination.vlanId),
                 changePropertyValue(flow2.destination, "vlanId", flow1.source.vlanId)],

                [changePropertyValue(flow1.source, "portNumber", flow2.destination.portNumber),
                 changePropertyValue(flow1.destination, "portNumber", flow2.source.portNumber),
                 changePropertyValue(flow2.source, "portNumber", flow1.destination.portNumber),
                 changePropertyValue(flow2.destination, "portNumber", flow1.source.portNumber)],

                [changePropertyValue(flow1.source, "datapath", flow2.destination.datapath),
                 changePropertyValue(flow1.destination, "datapath", flow2.source.datapath),
                 changePropertyValue(flow2.source, "datapath", flow1.destination.datapath),
                 changePropertyValue(flow2.destination, "datapath", flow1.source.datapath)],
        ]
    }

    @Unroll
    def "Able to swap endpoints (#description) for two flows with the same source and different destination \
switches"() {
        given: "Two flows with the same source and different destination switches"
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        when: "Try to swap endpoints for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(flow1Src),
                        flowHelper.toFlowEndpointV2(flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(flow2Src),
                        flowHelper.toFlowEndpointV2(flow2Dst)))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, flow1Src, flow1Dst, flow2Src, flow2Dst)
        verifyEndpoints(flow1, flow2, flow1Src, flow1Dst, flow2Src, flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(flow1, flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(flow1SwitchPair)
        validateSwitches(flow2SwitchPair)

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        description << ["src1 <-> src2", "dst1 <-> dst2", "src1 <-> dst2", "dst1 <-> src2"]
        flow1SwitchPair << [getTopologyHelper().getNotNeighboringSwitchPair()] * 4
        flow2SwitchPair << [getHalfDifferentNotNeighboringSwitchPair(flow1SwitchPair, "src")] * 4
        flow1 << [getFirstFlow(flow1SwitchPair, flow2SwitchPair)] * 4
        flow2 << [getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)] * 4
        [flow1Src, flow1Dst, flow2Src, flow2Dst] << [
                [flow2.source, flow1.destination, flow1.source, flow2.destination],
                [flow1.source, flow2.destination, flow2.source, flow1.destination],
                [flow2.destination, flow1.destination, flow2.source, flow1.source],
                [flow1.source, flow2.source, flow1.destination, flow2.destination]
        ]
    }

    @Unroll
    def "Able to swap #endpointsPart (#description) for two flows with different source and the same destination \
switches"() {
        given: "Two flows with different source and the same destination switches"
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        when: "Try to swap #endpointsPart for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(flow1Src),
                        flowHelper.toFlowEndpointV2(flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(flow2Src),
                        flowHelper.toFlowEndpointV2(flow2Dst)))

        then: "#endpointsPart.capitalize() are successfully swapped"
        verifyEndpoints(response, flow1Src, flow1Dst, flow2Src, flow2Dst)
        verifyEndpoints(flow1, flow2, flow1Src, flow1Dst, flow2Src, flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(flow1, flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(switchPairs[0])
        validateSwitches(switchPairs[1])

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        endpointsPart << ["vlans", "ports", "switches"]
        description << ["src1 <-> dst2, dst1 <-> src2"] * 3
        switchPairs << [getTopologyHelper().getAllNotNeighboringSwitchPairs().inject(null) { result, switchPair ->
            if (result) return result
            def halfDifferent = getHalfDifferentNotNeighboringSwitchPair(switchPair, "dst")
            if (halfDifferent) result = [switchPair, halfDifferent]
            return result
        }] * 3
        flow1 << [getFirstFlow(switchPairs[0], switchPairs[1])] * 3
        flow2 << [getSecondFlow(switchPairs[0], switchPairs[1], flow1)] * 3
        [flow1Src, flow1Dst, flow2Src, flow2Dst] << [
                [changePropertyValue(flow1.source, "vlanId", flow2.destination.vlanId),
                 changePropertyValue(flow1.destination, "vlanId", flow2.source.vlanId),
                 changePropertyValue(flow2.source, "vlanId", flow1.destination.vlanId),
                 changePropertyValue(flow2.destination, "vlanId", flow1.source.vlanId)],

                [changePropertyValue(flow1.source, "portNumber", flow2.destination.portNumber),
                 changePropertyValue(flow1.destination, "portNumber", flow2.source.portNumber),
                 changePropertyValue(flow2.source, "portNumber", flow1.destination.portNumber),
                 changePropertyValue(flow2.destination, "portNumber", flow1.source.portNumber)],

                [changePropertyValue(flow1.source, "datapath", flow2.destination.datapath),
                 changePropertyValue(flow1.destination, "datapath", flow2.source.datapath),
                 changePropertyValue(flow2.source, "datapath", flow1.destination.datapath),
                 changePropertyValue(flow2.destination, "datapath", flow1.source.datapath)],
        ]
    }

    @Unroll
    def "Able to swap endpoints (#description) for two flows with different source and the same destination \
switches"() {
        given: "Two flows with different source and the same destination switches"
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        when: "Try to swap endpoints for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(flow1Src),
                        flowHelper.toFlowEndpointV2(flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(flow2Src),
                        flowHelper.toFlowEndpointV2(flow2Dst)))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, flow1Src, flow1Dst, flow2Src, flow2Dst)
        verifyEndpoints(flow1, flow2, flow1Src, flow1Dst, flow2Src, flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(flow1, flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(flow1SwitchPair)
        validateSwitches(flow2SwitchPair)

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        description << ["src1 <-> src2", "dst1 <-> dst2", "src1 <-> dst2", "dst1 <-> src2"]
        flow1SwitchPair << [getTopologyHelper().getNotNeighboringSwitchPair()] * 4
        flow2SwitchPair << [getHalfDifferentNotNeighboringSwitchPair(flow1SwitchPair, "dst")] * 4
        flow1 << [getFirstFlow(flow1SwitchPair, flow2SwitchPair)] * 4
        flow2 << [getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)] * 4
        [flow1Src, flow1Dst, flow2Src, flow2Dst] << [
                [flow2.source, flow1.destination, flow1.source, flow2.destination],
                [flow1.source, flow2.destination, flow2.source, flow1.destination],
                [flow2.destination, flow1.destination, flow2.source, flow1.source],
                [flow1.source, flow2.source, flow1.destination, flow2.destination]
        ]
    }

    @Unroll
    def "Able to swap #endpointsPart (#description) for two flows with different source and destination switches"() {
        given: "Two flows with different source and destination switches"
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        when: "Try to swap #endpointsPart for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(flow1Src),
                        flowHelper.toFlowEndpointV2(flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(flow2Src),
                        flowHelper.toFlowEndpointV2(flow2Dst)))

        then: "#endpointsPart.capitalize() are successfully swapped"
        verifyEndpoints(response, flow1Src, flow1Dst, flow2Src, flow2Dst)
        verifyEndpoints(flow1, flow2, flow1Src, flow1Dst, flow2Src, flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(flow1, flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(flow1SwitchPair)
        validateSwitches(flow2SwitchPair)

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        endpointsPart << ["vlans", "ports", "switches"]
        description << ["src1 <-> dst2, dst1 <-> src2"] * 3
        flow1SwitchPair << [getTopologyHelper().getNotNeighboringSwitchPair()] * 3
        flow2SwitchPair << [getDifferentNotNeighboringSwitchPair(flow1SwitchPair)] * 3
        flow1 << [getFirstFlow(flow1SwitchPair, flow2SwitchPair)] * 3
        flow2 << [getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)] * 3
        [flow1Src, flow1Dst, flow2Src, flow2Dst] << [
                [changePropertyValue(flow1.source, "vlanId", flow2.destination.vlanId),
                 changePropertyValue(flow1.destination, "vlanId", flow2.source.vlanId),
                 changePropertyValue(flow2.source, "vlanId", flow1.destination.vlanId),
                 changePropertyValue(flow2.destination, "vlanId", flow1.source.vlanId)],

                [changePropertyValue(flow1.source, "portNumber", flow2.destination.portNumber),
                 changePropertyValue(flow1.destination, "portNumber", flow2.source.portNumber),
                 changePropertyValue(flow2.source, "portNumber", flow1.destination.portNumber),
                 changePropertyValue(flow2.destination, "portNumber", flow1.source.portNumber)],

                [changePropertyValue(flow1.source, "datapath", flow2.destination.datapath),
                 changePropertyValue(flow1.destination, "datapath", flow2.source.datapath),
                 changePropertyValue(flow2.source, "datapath", flow1.destination.datapath),
                 changePropertyValue(flow2.destination, "datapath", flow1.source.datapath)],
        ]
    }

    @Unroll
    def "Able to swap endpoints (#description) for two flows with different source and destination switches"() {
        given: "Two flows with different source and destination switches"
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        when: "Try to swap endpoints for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(flow1Src),
                        flowHelper.toFlowEndpointV2(flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(flow2Src),
                        flowHelper.toFlowEndpointV2(flow2Dst)))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, flow1Src, flow1Dst, flow2Src, flow2Dst)
        verifyEndpoints(flow1, flow2, flow1Src, flow1Dst, flow2Src, flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(flow1, flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(flow1SwitchPair)
        validateSwitches(flow2SwitchPair)

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        description << ["src1 <-> src2", "dst1 <-> dst2", "src1 <-> dst2", "dst1 <-> src2"]
        flow1SwitchPair << [getTopologyHelper().getNotNeighboringSwitchPair()] * 4
        flow2SwitchPair << [getDifferentNotNeighboringSwitchPair(flow1SwitchPair)] * 4
        flow1 << [getFirstFlow(flow1SwitchPair, flow2SwitchPair)] * 4
        flow2 << [
                changePropertyValue(
                        changePropertyValue(getFlowHelper().randomFlow(flow2SwitchPair), "source", "portNumber",
                                flow1.source.portNumber), "source", "vlanId", flow1.source.vlanId),
                changePropertyValue(
                        changePropertyValue(getFlowHelper().randomFlow(flow2SwitchPair), "destination", "portNumber",
                                flow1.destination.portNumber), "destination", "vlanId", flow1.destination.vlanId),
                changePropertyValue(
                        changePropertyValue(getFlowHelper().randomFlow(flow2SwitchPair), "destination", "portNumber",
                                flow1.source.portNumber), "destination", "vlanId", flow1.source.vlanId),
                changePropertyValue(
                        changePropertyValue(getFlowHelper().randomFlow(flow2SwitchPair), "source", "portNumber",
                                flow1.destination.portNumber), "source", "vlanId", flow1.destination.vlanId)
        ]
        [flow1Src, flow1Dst, flow2Src, flow2Dst] << [
                [flow2.source, flow1.destination, flow1.source, flow2.destination],
                [flow1.source, flow2.destination, flow2.source, flow1.destination],
                [flow2.destination, flow1.destination, flow2.source, flow1.source],
                [flow1.source, flow2.source, flow1.destination, flow2.destination]
        ]
    }

    def "Unable to swap endpoints for existing flow and non-existing flow"() {
        given: "An active flow"
        def switchPair = topologyHelper.getNeighboringSwitchPair()
        def flow1 = flowHelper.randomFlow(switchPair)
        def flow2 = flowHelper.randomFlow(switchPair)
        flowHelper.addFlow(flow1)
        flow2.id = NON_EXISTENT_FLOW_ID

        when: "Try to swap endpoints for existing flow and non-existing flow"
        northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(flow1.source),
                        flowHelper.toFlowEndpointV2(flow2.destination)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(flow2.source),
                        flowHelper.toFlowEndpointV2(flow1.destination)))

        then: "An error is received (404 code)"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorMessage == "Can not swap endpoints for flows: " +
                "Flow ${flow2.id} not found"

        and: "Delete the flow"
        flowHelper.deleteFlow(flow1.id)
    }

    @Unroll
    def "Unable to swap #endpointsPart for two flows (#description)"() {
        given: "Three active flows"
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)
        flowHelper.addFlow(flow3)

        when: "Try to swap #endpointsPart for two flows"
        northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(flow1Src),
                        flowHelper.toFlowEndpointV2(flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(flow2Src),
                        flowHelper.toFlowEndpointV2(flow2Dst)))

        then: "An error is received (409 code)"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 409
        // TODO check error message
        and: "Delete flows"
        [flow1, flow2, flow3].each { flowHelper.deleteFlow(it.id) }

        where:
        endpointsPart << ["ports and vlans", "ports and vlans", "vlans", "vlans", "ports", "ports"]
        description << ["the same ports and vlans on src switch",
                        "the same ports and vlans on dst switch",
                        "the same vlans on the same port on src switch",
                        "the same vlans on the same port on dst switch",
                        "no vlans, both flows are on the same port on src switch",
                        "no vlans, both flows are on the same port on dst switch"]
        flow1SwitchPair << [getTopologyHelper().getNotNeighboringSwitchPair()] * 6
        flow2SwitchPair << [getDifferentNotNeighboringSwitchPair(flow1SwitchPair)] * 6
        flow1 << [getFirstFlow(flow1SwitchPair, flow2SwitchPair)] * 4 +
                [getFirstFlow(flow1SwitchPair, flow2SwitchPair, true)] * 2
        flow2 << [getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)] * 4 +
                [getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1, true)] * 2
        flow3 << [
                getConflictingFlow(flow1SwitchPair, flow1, "source", changePropertyValue(
                        changePropertyValue(flow1.source, "portNumber", flow2.destination.portNumber), "vlanId",
                        flow2.destination.vlanId)),
                getConflictingFlow(flow1SwitchPair, flow1, "destination", changePropertyValue(
                        changePropertyValue(flow1.destination, "portNumber", flow2.source.portNumber), "vlanId",
                        flow2.source.vlanId)),

                getConflictingFlow(flow1SwitchPair, flow1, "source",
                        changePropertyValue(flow1.source, "vlanId", flow2.destination.vlanId)),
                getConflictingFlow(flow1SwitchPair, flow1, "destination",
                        changePropertyValue(flow1.destination, "vlanId", flow2.source.vlanId)),

                getConflictingFlow(flow1SwitchPair, flow1, "source",
                        changePropertyValue(flow1.source, "portNumber", flow2.destination.portNumber)),
                getConflictingFlow(flow1SwitchPair, flow1, "destination",
                        changePropertyValue(flow1.destination, "portNumber", flow2.source.portNumber))
        ]
        [flow1Src, flow1Dst, flow2Src, flow2Dst] << [
                [changePropertyValue(changePropertyValue(flow1.source, "portNumber",
                        flow2.destination.portNumber), "vlanId", flow2.destination.vlanId), flow1.destination,
                 flow2.source, changePropertyValue(changePropertyValue(flow2.destination, "portNumber",
                        flow1.source.portNumber), "vlanId", flow1.source.vlanId)],
                [flow1.source, changePropertyValue(changePropertyValue(flow1.destination, "portNumber",
                        flow2.source.portNumber), "vlanId", flow2.source.vlanId),
                 changePropertyValue(changePropertyValue(flow2.source, "portNumber",
                         flow1.destination.portNumber), "vlanId", flow1.destination.vlanId), flow2.destination],

                [changePropertyValue(flow1.source, "vlanId", flow2.destination.vlanId), flow1.destination,
                 flow2.source, changePropertyValue(flow2.destination, "vlanId", flow1.source.vlanId)],
                [flow1.source, changePropertyValue(flow1.destination, "vlanId", flow2.source.vlanId),
                 changePropertyValue(flow2.source, "vlanId", flow1.destination.vlanId), flow2.destination],

                [changePropertyValue(flow1.source, "portNumber", flow2.destination.portNumber), flow1.destination,
                 flow2.source, changePropertyValue(flow2.destination, "portNumber", flow1.source.portNumber)],
                [flow1.source, changePropertyValue(flow1.destination, "portNumber", flow2.source.portNumber),
                 changePropertyValue(flow2.source, "portNumber", flow1.destination.portNumber), flow2.destination]
        ]
        conflictingEndpoint << ["source", "destination"] * 3
    }

    @Unroll
    def "Unable to swap endpoints for two flows (#description)"() {
        given: "Two active flows"
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        when: "Try to swap endpoints for two flows"
        northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(flow1Src),
                        flowHelper.toFlowEndpointV2(flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(flow2Src),
                        flowHelper.toFlowEndpointV2(flow2Dst)))

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage == "Can not swap endpoints for flows: " +
                "New requested endpoint for '$flow2.id' conflicts with existing endpoint for '$flow1.id'"

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        description << ["the same src endpoint for flows", "the same dst endpoint for flows"]
        flow1SwitchPair << [getTopologyHelper().getNotNeighboringSwitchPair()] * 2
        flow2SwitchPair << [getDifferentNotNeighboringSwitchPair(flow1SwitchPair)] * 2
        flow1 << [getFirstFlow(flow1SwitchPair, flow2SwitchPair)] * 2
        flow2 << [getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)] * 2
        [flow1Src, flow1Dst, flow2Src, flow2Dst] << [
                [flow1.source, changePropertyValue(flow1.destination, "portNumber", flow2.destination.portNumber),
                 flow1.source, changePropertyValue(flow2.destination, "portNumber", flow1.destination.portNumber)],
                [changePropertyValue(flow1.source, "portNumber", flow2.source.portNumber), flow1.destination,
                 changePropertyValue(flow2.source, "portNumber", flow1.source.portNumber), flow1.destination]
        ]
    }

    @Unroll
    def "Unable to swap ports for two flows (port is occupied by ISL on #switchType switch)"() {
        given: "Two active flows"
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        when: "Try to swap ports for two flows"
        northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(flow1Src),
                        flowHelper.toFlowEndpointV2(flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(flow2Src),
                        flowHelper.toFlowEndpointV2(flow2Dst)))

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage == "Can not swap endpoints for flows: " +
                "The port $islPort on the switch '${switchPair."$switchType".dpId}' is occupied by an ISL."

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        switchType << ["src", "dst"]
        switchPair << [getTopologyHelper().getNotNeighboringSwitchPair()] * 2
        islPort << [getTopology().getBusyPortsForSwitch(switchPair.src).first(),
                    getTopology().getBusyPortsForSwitch(switchPair.dst).first()]
        flow1 << [getFlowHelper().randomFlow(switchPair)] * 2
        flow2 << [
                changePropertyValue(getFlowHelper().randomFlow(switchPair, false, [flow1]),
                        "destination", "portNumber", islPort),
                changePropertyValue(getFlowHelper().randomFlow(switchPair, false, [flow1]),
                        "source", "portNumber", islPort)
        ]
        [flow1Src, flow1Dst, flow2Src, flow2Dst] << [
                [changePropertyValue(flow1.source, "portNumber", flow2.destination.portNumber), flow1.destination,
                 flow2.source, changePropertyValue(flow2.destination, "portNumber", flow1.source.portNumber)],
                [flow1.source, changePropertyValue(flow1.destination, "portNumber", flow2.source.portNumber),
                 changePropertyValue(flow2.source, "portNumber", flow1.destination.portNumber), flow2.destination]
        ]
    }

    def "Able to swap endpoints for two flows when all bandwidth on ISL is consumed"() {
        setup: "Create two flows with different source and the same destination switches"
        def flow1SwitchPair = topologyHelper.getNeighboringSwitchPair()
        def flow2SwitchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            it.src != flow1SwitchPair.src && it.dst == flow1SwitchPair.dst
        }
        def flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
        def flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)

        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        and: "Update the first flow so that it consumes all bandwidth on the link"
        def flow1Path = PathHelper.convert(northbound.getFlowPath(flow1.id))
        def flow1Isl = pathHelper.getInvolvedIsls(flow1Path)[0]
        def flow1IslMaxBw = islUtils.getIslInfo(flow1Isl).get().maxBandwidth
        northbound.updateFlow(flow1.id, flow1.tap { it.maximumBandwidth = flow1IslMaxBw })

        and: "Break all alternative paths for the first flow"
        def altPaths = flow1SwitchPair.paths.findAll { it != flow1Path }
        List<PathNode> broughtDownPorts = []
        altPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northbound.portDown(src.switchId, src.portNo)
        }
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == IslChangeType.FAILED
            }.size() == broughtDownPorts.size() * 2
        }

        and: "Update max bandwidth for the second flow's link so that it is equal to max bandwidth of the first flow"
        def flow2Path = PathHelper.convert(northbound.getFlowPath(flow2.id))
        def flow2Isl = pathHelper.getInvolvedIsls(flow2Path)[0]
        northbound.updateLinkMaxBandwidth(flow2Isl.srcSwitch.dpId, flow2Isl.srcPort, flow2Isl.dstSwitch.dpId,
                flow2Isl.dstPort, flow1IslMaxBw)

        and: "Break all alternative paths for the second flow"
        altPaths = flow2SwitchPair.paths.findAll { it != flow2Path && it[1].switchId != flow1SwitchPair.src.dpId }
        altPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northbound.portDown(src.switchId, src.portNo)
        }
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == IslChangeType.FAILED
            }.size() == broughtDownPorts.size() * 2
        }

        when: "Try to swap endpoints for two flows"
        def flow1Src = flow2.source
        def flow1Dst = changePropertyValue(flow1.destination, "portNumber", flow2.destination.portNumber)
        def flow2Src = flow1.source
        def flow2Dst = changePropertyValue(flow2.destination, "portNumber", flow1.destination.portNumber)
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(flow1Src),
                        flowHelper.toFlowEndpointV2(flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(flow2Src),
                        flowHelper.toFlowEndpointV2(flow2Dst)))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, flow1Src, flow1Dst, flow2Src, flow2Dst)
        verifyEndpoints(flow1, flow2, flow1Src, flow1Dst, flow2Src, flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(flow1, flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(flow1SwitchPair)
        validateSwitches(flow2SwitchPair)

        and: "Restore topology and delete flows"
        broughtDownPorts.every { northbound.portUp(it.switchId, it.portNo) }
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts()
    }

    def "Unable to swap endpoints for two flows when not enough bandwidth on ISL"() {
        setup: "Create two flows with different source and the same destination switches"
        def flow1SwitchPair = topologyHelper.getNeighboringSwitchPair()
        def flow2SwitchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            it.src != flow1SwitchPair.src && it.dst == flow1SwitchPair.dst
        }
        def flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
        def flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)

        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        and: "Update the first flow so that it consumes all bandwidth on the link"
        def flow1Path = PathHelper.convert(northbound.getFlowPath(flow1.id))
        def flow1Isl = pathHelper.getInvolvedIsls(flow1Path)[0]
        def flow1IslMaxBw = islUtils.getIslInfo(flow1Isl).get().maxBandwidth
        northbound.updateFlow(flow1.id, flow1.tap { it.maximumBandwidth = flow1IslMaxBw })

        and: "Break all alternative paths for the first flow"
        def altPaths = flow1SwitchPair.paths.findAll { it != flow1Path }
        List<PathNode> broughtDownPorts = []
        altPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northbound.portDown(src.switchId, src.portNo)
        }
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == IslChangeType.FAILED
            }.size() == broughtDownPorts.size() * 2
        }

        and: "Update max bandwidth for the second flow's link so that it is not enough bandwidth for the first flow"
        def flow2Path = PathHelper.convert(northbound.getFlowPath(flow2.id))
        def flow2Isl = pathHelper.getInvolvedIsls(flow2Path)[0]
        northbound.updateLinkMaxBandwidth(flow2Isl.srcSwitch.dpId, flow2Isl.srcPort, flow2Isl.dstSwitch.dpId,
                flow2Isl.dstPort, flow1IslMaxBw - 1)

        and: "Break all alternative paths for the second flow"
        altPaths = flow2SwitchPair.paths.findAll { it != flow2Path && it[1].switchId != flow1SwitchPair.src.dpId }
        altPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northbound.portDown(src.switchId, src.portNo)
        }
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == IslChangeType.FAILED
            }.size() == broughtDownPorts.size() * 2
        }

        when: "Try to swap endpoints for two flows"
        def flow1Src = flow2.source
        def flow1Dst = changePropertyValue(flow1.destination, "portNumber", flow2.destination.portNumber)
        def flow2Src = flow1.source
        def flow2Dst = changePropertyValue(flow2.destination, "portNumber", flow1.destination.portNumber)
        northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(flow1Src),
                        flowHelper.toFlowEndpointV2(flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(flow2Src),
                        flowHelper.toFlowEndpointV2(flow2Dst)))

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage == "Can not swap endpoints for flows: " +
                "Failed to find path with requested bandwidth=${flow1.maximumBandwidth}: " +
                "Switch ${flow2SwitchPair.src.dpId} doesn't have links with enough bandwidth"

        and: "Restore topology and delete flows"
        broughtDownPorts.every { northbound.portUp(it.switchId, it.portNo) }
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts()
    }

    def "Able to swap endpoints for two flows when not enough bandwidth on ISL and ignore_bandwidth=true"() {
        setup: "Create two flows with different source and the same destination switches"
        def flow1SwitchPair = topologyHelper.getNeighboringSwitchPair()
        def flow2SwitchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            it.src != flow1SwitchPair.src && it.dst == flow1SwitchPair.dst
        }
        def flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
        def flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)

        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2.tap { it.ignoreBandwidth = true })

        and: "Update the first flow so that it consumes all bandwidth on the link"
        def flow1Path = PathHelper.convert(northbound.getFlowPath(flow1.id))
        def flow1Isl = pathHelper.getInvolvedIsls(flow1Path)[0]
        def flow1IslMaxBw = islUtils.getIslInfo(flow1Isl).get().maxBandwidth
        northbound.updateFlow(flow1.id, flow1.tap { it.maximumBandwidth = flow1IslMaxBw })

        and: "Break all alternative paths for the first flow"
        def altPaths = flow1SwitchPair.paths.findAll { it != flow1Path }
        List<PathNode> broughtDownPorts = []
        altPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northbound.portDown(src.switchId, src.portNo)
        }
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == IslChangeType.FAILED
            }.size() == broughtDownPorts.size() * 2
        }

        and: "Update max bandwidth for the second flow's link so that it is not enough bandwidth for the first flow"
        def flow2Path = PathHelper.convert(northbound.getFlowPath(flow2.id))
        def flow2Isl = pathHelper.getInvolvedIsls(flow2Path)[0]
        northbound.updateLinkMaxBandwidth(flow2Isl.srcSwitch.dpId, flow2Isl.srcPort, flow2Isl.dstSwitch.dpId,
                flow2Isl.dstPort, flow1IslMaxBw - 1)

        and: "Break all alternative paths for the second flow"
        altPaths = flow2SwitchPair.paths.findAll { it != flow2Path && it[1].switchId != flow1SwitchPair.src.dpId }
        altPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northbound.portDown(src.switchId, src.portNo)
        }
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == IslChangeType.FAILED
            }.size() == broughtDownPorts.size() * 2
        }

        when: "Try to swap endpoints for two flows"
        def flow1Src = flow2.source
        def flow1Dst = changePropertyValue(flow1.destination, "portNumber", flow2.destination.portNumber)
        def flow2Src = flow1.source
        def flow2Dst = changePropertyValue(flow2.destination, "portNumber", flow1.destination.portNumber)
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(flow1Src),
                        flowHelper.toFlowEndpointV2(flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(flow2Src),
                        flowHelper.toFlowEndpointV2(flow2Dst)))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, flow1Src, flow1Dst, flow2Src, flow2Dst)
        verifyEndpoints(flow1, flow2, flow1Src, flow1Dst, flow2Src, flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(flow1, flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(flow1SwitchPair)
        validateSwitches(flow2SwitchPair)

        and: "Restore topology and delete flows"
        broughtDownPorts.every { northbound.portUp(it.switchId, it.portNo) }
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts()
    }

    def "Unable to swap endpoints for two flows when one of them is inactive"() {
        setup: "Create two flows with different source and the same destination switches"
        def flow1SwitchPair = topologyHelper.getNeighboringSwitchPair()
        def flow2SwitchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            it.src != flow1SwitchPair.src && it.dst == flow1SwitchPair.dst
        }
        def flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
        def flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)

        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        and: "Break all paths for the first flow"
        List<PathNode> broughtDownPorts = []
        flow1SwitchPair.paths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northbound.portDown(src.switchId, src.portNo)
        }
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == IslChangeType.FAILED
            }.size() == broughtDownPorts.size() * 2
            assert northbound.getFlowStatus(flow1.id).status == FlowState.DOWN
        }

        when: "Try to swap endpoints for two flows"
        northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(flow2.source),
                        flowHelper.toFlowEndpointV2(flow1.destination)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(flow1.source),
                        flowHelper.toFlowEndpointV2(flow2.destination)))

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage == "Can not swap endpoints for flows: " +
                "Failed to find path with requested bandwidth=${flow2.maximumBandwidth}: " +
                "Switch ${flow1SwitchPair.src.dpId} doesn't have links with enough bandwidth"

        and: "Restore topology and delete flows"
        broughtDownPorts.every { northbound.portUp(it.switchId, it.portNo) }
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts()
    }

    @Unroll
    def "Able to swap endpoints (#description) for two protected flows"() {
        given: "Two protected flows with different source and destination switches"
        flowHelper.addFlow(flow1.tap { it.allocateProtectedPath = true })
        flowHelper.addFlow(flow2.tap { it.allocateProtectedPath = true })

        when: "Try to swap endpoints for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(flow1Src),
                        flowHelper.toFlowEndpointV2(flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(flow2Src),
                        flowHelper.toFlowEndpointV2(flow2Dst)))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, flow1Src, flow1Dst, flow2Src, flow2Dst)
        verifyEndpoints(flow1, flow2, flow1Src, flow1Dst, flow2Src, flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(flow1, flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(flow1SwitchPair)
        validateSwitches(flow2SwitchPair)

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        description << ["src1 <-> src2", "dst1 <-> dst2", "src1 <-> dst2", "dst1 <-> src2"]
        flow1SwitchPair << [getTopologyHelper().getNotNeighboringSwitchPair()] * 4
        flow2SwitchPair << [getDifferentNotNeighboringSwitchPair(flow1SwitchPair)] * 4
        flow1 << [getFlowHelper().randomFlow(flow1SwitchPair)] * 4
        flow2 << [getFlowHelper().randomFlow(flow2SwitchPair)] * 4
        [flow1Src, flow1Dst, flow2Src, flow2Dst] << [
                [flow2.source, flow1.destination, flow1.source, flow2.destination],
                [flow1.source, flow2.destination, flow2.source, flow1.destination],
                [flow2.destination, flow1.destination, flow2.source, flow1.source],
                [flow1.source, flow2.source, flow1.destination, flow2.destination]
        ]
    }

    @Unroll
    def "A protected flow with swapped endpoint allows traffic on main and protected paths"() {
        given: "Two protected flows with different source and destination switches"
        def tgSwitches = topology.getActiveTraffGens()*.getSwitchConnected()
        assumeTrue("Not enough traffgen switches found", tgSwitches.size() > 1)

        def flow1SwitchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            !(it.src in tgSwitches) && it.dst in tgSwitches
        }
        def flow2SwitchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            !(it.src in [flow1SwitchPair.src, flow1SwitchPair.dst]) &&
                    !(it.dst in [flow1SwitchPair.src, flow1SwitchPair.dst]) &&
                    it.src in tgSwitches && !(it.dst in tgSwitches)
        }
        def flow1 = flowHelper.randomFlow(flow1SwitchPair)
        def flow2 = flowHelper.randomFlow(flow2SwitchPair)

        flowHelper.addFlow(flow1.tap { it.allocateProtectedPath = true })
        flowHelper.addFlow(flow2.tap { it.allocateProtectedPath = true })

        when: "Try to swap endpoints for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(flow2.source),
                        flowHelper.toFlowEndpointV2(flow1.destination)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(flow1.source),
                        flowHelper.toFlowEndpointV2(flow2.destination)))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, flow2.source, flow1.destination, flow1.source, flow2.destination)
        verifyEndpoints(flow1, flow2, flow2.source, flow1.destination, flow1.source, flow2.destination)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(flow1, flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(flow1SwitchPair)
        validateSwitches(flow2SwitchPair)

        and: "The first flow allows traffic on the main path"
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam)
                .buildBidirectionalExam(northbound.getFlow(flow1.id), 0)
        [exam.forward, exam.reverse].each { direction ->
            def resources = traffExam.startExam(direction)
            direction.setResources(resources)
            assert traffExam.waitExam(direction).hasTraffic()
        }

        and: "The first flow allows traffic on the protected path"
        northbound.swapFlowPath(flow1.id)
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow1.id).status == FlowState.UP }

        def newExam = new FlowTrafficExamBuilder(topology, traffExam)
                .buildBidirectionalExam(northbound.getFlow(flow1.id), 0)
        [newExam.forward, newExam.reverse].each { direction ->
            def resources = traffExam.startExam(direction)
            direction.setResources(resources)
            assert traffExam.waitExam(direction).hasTraffic()
        }

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }
    }

    void verifyEndpoints(response, flow1SrcExpected, flow1DstExpected, flow2SrcExpected, flow2DstExpected) {
        assert response.firstFlow.source == flowHelper.toFlowEndpointV2(flow1SrcExpected)
        assert response.firstFlow.destination == flowHelper.toFlowEndpointV2(flow1DstExpected)
        assert response.secondFlow.source == flowHelper.toFlowEndpointV2(flow2SrcExpected)
        assert response.secondFlow.destination == flowHelper.toFlowEndpointV2(flow2DstExpected)
    }

    void verifyEndpoints(flow1, flow2, flow1SrcExpected, flow1DstExpected, flow2SrcExpected, flow2DstExpected) {
        def flow1Updated = northbound.getFlow(flow1.id)
        def flow2Updated = northbound.getFlow(flow2.id)

        assert flow1Updated.source == flow1SrcExpected
        assert flow1Updated.destination == flow1DstExpected
        assert flow2Updated.source == flow2SrcExpected
        assert flow2Updated.destination == flow2DstExpected
    }

    void validateFlows(flow1, flow2) {
        Wrappers.wait(RULES_DELETION_TIME + RULES_INSTALLATION_TIME) {
            [flow1, flow2].each {
                assert northbound.validateFlow(it.id).each { direction -> assert direction.asExpected }
            }
        }
    }

    void validateSwitches(switchPair) {
        Wrappers.wait(RULES_DELETION_TIME + RULES_INSTALLATION_TIME) {
            [switchPair.src, switchPair.dst].each {
                if (it.ofVersion == "OF_13") {
                    def validationResult = northbound.validateSwitch(it.dpId)
                    switchHelper.verifyRuleSectionsAreEmpty(validationResult, ["missing", "excess"])
                    switchHelper.verifyMeterSectionsAreEmpty(validationResult, ["missing", "misconfigured", "excess"])
                } else {
                    def validationResult = northbound.validateSwitchRules(it.dpId)
                    assert validationResult.missingRules.size() == 0
                    assert validationResult.excessRules.size() == 0
                }
            }
        }
    }

    /**
     * Get a FlowCreatePayload instance for the first flow. The instance is built considering ISL ports on source and
     * destination switches of the first and second switch pairs. So no conflicts should appear while creating the flow
     * and swapping flow endpoints.
     *
     * @param firstFlowSwitchPair Switch pair for the first flow
     * @param secondFlowSwitchPair Switch pair for the second flow
     * @param noVlans Whether use vlans or not
     * @return a FlowCreatePayload instance
     */
    def getFirstFlow(firstFlowSwitchPair, secondFlowSwitchPair, noVlans = false) {
        def firstFlow = flowHelper.randomFlow(firstFlowSwitchPair)
        firstFlow.source.portNumber = (topology.getAllowedPortsForSwitch(firstFlowSwitchPair.src) -
                topology.getBusyPortsForSwitch(secondFlowSwitchPair.src) -
                topology.getBusyPortsForSwitch(secondFlowSwitchPair.dst))[0]
        firstFlow.destination.portNumber = (topology.getAllowedPortsForSwitch(firstFlowSwitchPair.dst) -
                topology.getBusyPortsForSwitch(secondFlowSwitchPair.src) -
                topology.getBusyPortsForSwitch(secondFlowSwitchPair.dst) - [firstFlow.source.portNumber])[0]

        if (noVlans) {
            firstFlow.source.vlanId = null
            firstFlow.destination.vlanId = null
        }

        return firstFlow
    }

    /**
     * Get a FlowCreatePayload instance for the second flow. The instance is built considering ISL ports on source and
     * destination switches of the first and second switch pairs. Also ports of the first flow are considered as well.
     * So no conflicts should appear while creating the flow and swapping flow endpoints.
     *
     * @param firstFlowSwitchPair Switch pair for the first flow
     * @param secondFlowSwitchPair Switch pair for the second flow
     * @param firstFlow The first flow instance
     * @param noVlans Whether use vlans or not
     * @return a FlowCreatePayload instance
     */
    def getSecondFlow(firstFlowSwitchPair, secondFlowSwitchPair, firstFlow, noVlans = false) {
        def secondFlow = flowHelper.randomFlow(secondFlowSwitchPair)
        secondFlow.source.portNumber = (topology.getAllowedPortsForSwitch(secondFlowSwitchPair.src) -
                topology.getBusyPortsForSwitch(firstFlowSwitchPair.src) -
                topology.getBusyPortsForSwitch(firstFlowSwitchPair.dst) -
                [firstFlow.source.portNumber, firstFlow.destination.portNumber])[0]
        secondFlow.destination.portNumber = (topology.getAllowedPortsForSwitch(secondFlowSwitchPair.dst) -
                topology.getBusyPortsForSwitch(firstFlowSwitchPair.src) -
                topology.getBusyPortsForSwitch(firstFlowSwitchPair.dst) -
                [secondFlow.source.portNumber, firstFlow.source.portNumber, firstFlow.destination.portNumber])[0]

        if (noVlans) {
            secondFlow.source.vlanId = null
            secondFlow.destination.vlanId = null
        }

        return secondFlow
    }

    def getConflictingFlow(switchPair, neighboringFlow, conflictingEndpointName, conflictingEndpointValue) {
        def conflictingFlow = flowHelper.randomFlow(switchPair, false, [neighboringFlow])
        conflictingFlow."$conflictingEndpointName" = conflictingEndpointValue

        return conflictingFlow
    }

    def changePropertyValue(flowEndpoint, propertyName, newValue) {
        // Deep copy of object
        def mapper = new ObjectMapper()
        return mapper.readValue(mapper.writeValueAsString(flowEndpoint), FlowEndpointPayload).tap {
            it."$propertyName" = newValue
        }
    }

    def changePropertyValue(flow, endpointName, propertyName, newValue) {
        // Deep copy of object
        def mapper = new ObjectMapper()
        return mapper.readValue(mapper.writeValueAsString(flow), FlowCreatePayload).tap {
            it."$endpointName"."$propertyName" = newValue
        }
    }

    def getHalfDifferentNotNeighboringSwitchPair(switchPairToAvoid, equalEndpoint) {
        def differentEndpoint = (equalEndpoint == "src" ? "dst" : "src")
        topologyHelper.getAllNotNeighboringSwitchPairs().find {
            it."$equalEndpoint" == switchPairToAvoid."$equalEndpoint" &&
                    it."$differentEndpoint" != switchPairToAvoid."$differentEndpoint"
        }
    }

    def getDifferentNotNeighboringSwitchPair(switchPairToAvoid) {
        topologyHelper.getAllNotNeighboringSwitchPairs().find {
            !(it.src in [switchPairToAvoid.src, switchPairToAvoid.dst]) &&
                    !(it.dst in [switchPairToAvoid.src, switchPairToAvoid.dst])
        }
    }
}
