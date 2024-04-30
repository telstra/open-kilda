package org.openkilda.functionaltests.spec.flows

import com.fasterxml.jackson.databind.ObjectMapper
import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowEndpointsNotSwappedExpectedError
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.payload.flow.FlowCreatePayload
import org.openkilda.messaging.payload.flow.FlowEndpointPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.SwitchId
import org.openkilda.model.SwitchStatus
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.flows.FlowLoopPayload
import org.openkilda.northbound.dto.v2.flows.SwapFlowPayload
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.tools.FlowTrafficExamBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import spock.lang.Shared

import javax.inject.Provider

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_ACTION
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_FAIL
import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

class SwapEndpointSpec extends HealthCheckSpecification {

    @Autowired
    Provider<TraffExamService> traffExamProvider

    def "Able to swap endpoints(#data.description)"() {
        given: "Some flows in the system according to preconditions"
        flows.each { flowHelper.addFlow(it) }

        when: "Try to swap endpoints with #data.description"
        def response = northbound.swapFlowEndpoint(firstSwap, secondSwap)

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, firstSwap.source, firstSwap.destination, secondSwap.source, secondSwap.destination)
        verifyEndpoints(firstSwap.flowId, secondSwap.flowId, firstSwap.source, firstSwap.destination,
                secondSwap.source, secondSwap.destination)

        and: "Flows validation doesn't show any rule discrepancies"
        Wrappers.wait(RULES_DELETION_TIME + RULES_INSTALLATION_TIME) {
            flows.each {
                assert northbound.validateFlow(it.id).each { direction -> assert direction.asExpected }
            }
        }

        and: "Switch validation doesn't show any missing/excess rules and meters"
        List<Switch> involvedSwitches = flows.collectMany {
            [it.source.datapath, it.destination.datapath].collect { findSw(it) }
        }.unique()
        validateSwitches(involvedSwitches)

        where:
        data << [
                [description: "no vlan vs vlan on the same port on src switch"].tap {
                    def switchPair = getSwitchPairs().all().nonNeighbouring().random()
                    def flow1 = getFlowHelper().randomFlow(switchPair)
                    flow1.source.portNumber = getFreePort(switchPair.src, [switchPair.dst])
                    flow1.source.vlanId = 0
                    def flow2 = getFlowHelper().randomFlow(switchPair, false, [flow1])
                    flow2.source.portNumber = flow1.source.portNumber
                    it.flows = [flow1, flow2]
                    it.firstSwap = new SwapFlowPayload(flow1.id, getFlowHelper().toFlowEndpointV2(flow2.source),
                            getFlowHelper().toFlowEndpointV2(flow1.destination))
                    it.secondSwap = new SwapFlowPayload(flow2.id, getFlowHelper().toFlowEndpointV2(flow1.source),
                            getFlowHelper().toFlowEndpointV2(flow2.destination))
                },
                [description: "same port, swap vlans on dst switch + third idle novlan flow on that port"].tap {
                    def switchPair = getSwitchPairs().all().nonNeighbouring().random()
                    def flow1 = getFlowHelper().randomFlow(switchPair)
                    def flow2 = getFlowHelper().randomFlow(switchPair, false, [flow1])
                    flow1.destination.portNumber = getFreePort(switchPair.dst, [switchPair.src])
                    flow2.destination.portNumber = flow1.destination.portNumber
                    flow2.destination.vlanId = getFreeVlan(flow2.destination.datapath, [flow1])
                    def flow3 = getFlowHelper().randomFlow(switchPair, false, [flow1, flow2])
                    flow3.destination.portNumber = flow1.destination.portNumber
                    flow3.destination.vlanId = 0
                    it.flows = [flow1, flow2, flow3]
                    it.firstSwap = new SwapFlowPayload(flow1.id, getFlowHelper().toFlowEndpointV2(flow1.source),
                            getFlowHelper().toFlowEndpointV2(flow2.destination))
                    it.secondSwap = new SwapFlowPayload(flow2.id, getFlowHelper().toFlowEndpointV2(flow2.source),
                            getFlowHelper().toFlowEndpointV2(flow1.destination))
                },
                [description: "vlan on src1 <-> vlan on dst2, same port numbers"].tap {
                    def switchPair = getSwitchPairs().all().nonNeighbouring().random()
                    def flow1 = getFlowHelper().randomFlow(switchPair)
                    def flow2 = getFlowHelper().randomFlow(switchPair, false, [flow1])
                    flow1.source.portNumber = getFreePort(switchPair.src, [switchPair.dst])
                    flow2.destination.portNumber = flow1.source.portNumber
                    it.flows = [flow1, flow2]
                    it.firstSwap = new SwapFlowPayload(flow1.id,
                            getFlowHelper().toFlowEndpointV2(flow1.source).tap { it.vlanId = flow2.destination.vlanId },
                            getFlowHelper().toFlowEndpointV2(flow1.destination))
                    it.secondSwap = new SwapFlowPayload(flow2.id,
                            getFlowHelper().toFlowEndpointV2(flow2.source),
                            getFlowHelper().toFlowEndpointV2(flow2.destination).tap { it.vlanId = flow1.source.vlanId })
                },
                [description: "port on dst1 <-> port on src2, vlans are equal"].tap {
                    def switchPair = getSwitchPairs().all().nonNeighbouring().random()
                    def flow1 = getFlowHelper().randomFlow(switchPair, false)
                    def flow2 = getFlowHelper().randomFlow(switchPair, false, [flow1])
                    flow1.destination.portNumber = getFreePort(switchPair.dst, [switchPair.src],
                            [flow1.source.portNumber, flow2.source.portNumber])
                    flow2.source.portNumber = getFreePort(switchPair.src, [switchPair.dst],
                            [flow2.destination.portNumber, flow1.source.portNumber])
                    flow2.source.vlanId = flow1.source.vlanId
                    it.flows = [flow1, flow2]
                    it.firstSwap = new SwapFlowPayload(flow1.id,
                            getFlowHelper().toFlowEndpointV2(flow1.source),
                            getFlowHelper().toFlowEndpointV2(flow1.destination)
                                    .tap { it.portNumber = flow2.source.portNumber })
                    it.secondSwap = new SwapFlowPayload(flow2.id,
                            getFlowHelper().toFlowEndpointV2(flow2.source)
                                    .tap { it.portNumber = flow1.destination.portNumber },
                            getFlowHelper().toFlowEndpointV2(flow2.destination))
                },
                [description: "switch on src1 <-> switch on dst2, other params random"].tap {
                    def switchPair = getSwitchPairs().all().nonNeighbouring().random()
                    def flow1 = getFlowHelper().randomFlow(switchPair)
                    def flow2 = getFlowHelper().randomFlow(switchPair, false, [flow1])
                    flow1.source.portNumber = getFreePort(switchPair.src, [switchPair.dst])
                    flow2.destination.portNumber = getFreePort(switchPair.dst, [switchPair.src])
                    it.flows = [flow1, flow2]
                    it.firstSwap = new SwapFlowPayload(flow1.id,
                            getFlowHelper().toFlowEndpointV2(flow1.source)
                                    .tap { it.switchId = flow2.destination.datapath },
                            getFlowHelper().toFlowEndpointV2(flow1.destination))
                    it.secondSwap = new SwapFlowPayload(flow2.id,
                            getFlowHelper().toFlowEndpointV2(flow2.source),
                            getFlowHelper().toFlowEndpointV2(flow2.destination)
                                    .tap { it.switchId = flow1.source.datapath })
                },
                [description: "both endpoints swap, same switches"].tap {
                    def switchPair = getSwitchPairs().all().nonNeighbouring().random()
                    def flow1 = getFlowHelper().randomFlow(switchPair)
                    def flow2 = getFlowHelper().randomFlow(switchPair, false, [flow1])
                    flow1.source.portNumber = getFreePort(switchPair.src, [switchPair.dst])
                    flow1.destination.portNumber = getFreePort(switchPair.dst, [switchPair.src])
                    flow2.source.portNumber = getFreePort(switchPair.src, [switchPair.dst])
                    flow2.destination.portNumber = getFreePort(switchPair.dst, [switchPair.src])
                    it.flows = [flow1, flow2]
                    it.firstSwap = new SwapFlowPayload(flow1.id,
                            getFlowHelper().toFlowEndpointV2(flow2.source),
                            getFlowHelper().toFlowEndpointV2(flow2.destination))
                    it.secondSwap = new SwapFlowPayload(flow2.id,
                            getFlowHelper().toFlowEndpointV2(flow1.source),
                            getFlowHelper().toFlowEndpointV2(flow1.destination))
                },
                [description: "endpoints src1 <-> dst2, same switches"].tap {
                    def switchPair = getSwitchPairs().all().nonNeighbouring().random()
                    def flow1 = getFlowHelper().randomFlow(switchPair)
                    def flow2 = getFlowHelper().randomFlow(switchPair, false, [flow1])
                    flow1.source.portNumber = getFreePort(switchPair.src, [switchPair.dst])
                    flow1.destination.portNumber = getFreePort(switchPair.dst, [switchPair.src])
                    flow2.source.portNumber = getFreePort(switchPair.src, [switchPair.dst], [flow1.source.portNumber])
                    flow2.destination.portNumber = getFreePort(switchPair.dst, [switchPair.src], [flow1.destination.portNumber])
                    flow1.source.vlanId = getFreeVlan(flow2.destination.datapath, [flow2])
                    flow2.destination.vlanId = getFreeVlan(flow1.destination.datapath, [flow1])
                    it.flows = [flow1, flow2]
                    it.firstSwap = new SwapFlowPayload(flow1.id,
                            getFlowHelper().toFlowEndpointV2(flow2.destination),
                            getFlowHelper().toFlowEndpointV2(flow1.destination))
                    it.secondSwap = new SwapFlowPayload(flow2.id,
                            getFlowHelper().toFlowEndpointV2(flow2.source),
                            getFlowHelper().toFlowEndpointV2(flow1.source))
                },
                [description: "endpoints src1 <-> src2, different src switches, same dst"].tap {
                    List<SwitchPair> swPairs = getSwitchPairs().all().nonNeighbouring().getSwitchPairs()
                            .inject(null) { result, switchPair ->
                                if (result) return result
                                def halfDifferent = getHalfDifferentNotNeighboringSwitchPair(switchPair, "dst")
                                if (halfDifferent) result = [switchPair, halfDifferent]
                                return result
                            }
                    def flow1 = getFlowHelper().randomFlow(swPairs[0])
                    def flow2 = getFlowHelper().randomFlow(swPairs[1], false, [flow1])
                    flow1.source.portNumber = getFreePort(swPairs[0].src, [swPairs[1].src])
                    flow2.source.portNumber = getFreePort(swPairs[1].src, [swPairs[0].src])
                    it.flows = [flow1, flow2]
                    it.firstSwap = new SwapFlowPayload(flow1.id,
                            getFlowHelper().toFlowEndpointV2(flow2.source),
                            getFlowHelper().toFlowEndpointV2(flow1.destination))
                    it.secondSwap = new SwapFlowPayload(flow2.id,
                            getFlowHelper().toFlowEndpointV2(flow1.source),
                            getFlowHelper().toFlowEndpointV2(flow2.destination))
                }
        ]
        flows = data.flows as List<FlowCreatePayload>
        firstSwap = data.firstSwap as SwapFlowPayload
        secondSwap = data.secondSwap as SwapFlowPayload
    }

    @Tags([LOW_PRIORITY])
    def "Able to swap #data.endpointsPart (src1 <-> dst2, dst1 <-> src2) for two flows with the same source and different destination \
switches"() {
        given: "Two flows with the same source and different destination switches"
        flowHelper.addFlow(data.flow1)
        flowHelper.addFlow(data.flow2)

        when: "Try to swap #endpointsPart for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(data.flow1.id, flowHelper.toFlowEndpointV2(data.flow1Src),
                        flowHelper.toFlowEndpointV2(data.flow1Dst)),
                new SwapFlowPayload(data.flow2.id, flowHelper.toFlowEndpointV2(data.flow2Src),
                        flowHelper.toFlowEndpointV2(data.flow2Dst)))

        then: "#endpointsPart.capitalize() are successfully swapped"
        verifyEndpoints(response, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)
        verifyEndpoints(data.flow1.id, data.flow2.id, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(data.flow1, data.flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(data.switchPairs[0])
        validateSwitches(data.switchPairs[1])

        where:
        data << [{
                     it.endpointsPart = "vlans"
                     it.flow1Src = changePropertyValue(it.flow1.source, "vlanId", it.flow2.destination.vlanId)
                     it.flow1Dst = changePropertyValue(it.flow1.destination, "vlanId", it.flow2.source.vlanId)
                     it.flow2Src = changePropertyValue(it.flow2.source, "vlanId", it.flow1.destination.vlanId)
                     it.flow2Dst = changePropertyValue(it.flow2.destination, "vlanId", it.flow1.source.vlanId)
                 },
                 {
                     it.endpointsPart = "ports"
                     it.flow1Src = changePropertyValue(it.flow1.source, "portNumber", it.flow2.destination.portNumber)
                     it.flow1Dst = changePropertyValue(it.flow1.destination, "portNumber", it.flow2.source.portNumber)
                     it.flow2Src = changePropertyValue(it.flow2.source, "portNumber", it.flow1.destination.portNumber)
                     it.flow2Dst = changePropertyValue(it.flow2.destination, "portNumber", it.flow1.source.portNumber)
                 },
                 {
                     it.endpointsPart = "switches"
                     it.flow1Src = changePropertyValue(it.flow1.source, "datapath", it.flow2.destination.datapath)
                     it.flow1Dst = changePropertyValue(it.flow1.destination, "datapath", it.flow2.source.datapath)
                     it.flow2Src = changePropertyValue(it.flow2.source, "datapath", it.flow1.destination.datapath)
                     it.flow2Dst = changePropertyValue(it.flow2.destination, "datapath", it.flow1.source.datapath)
                 }].collect { iterationData ->
            def switchPairs = getSwitchPairs().all().nonNeighbouring().getSwitchPairs().inject(null) { result, switchPair ->
                if (result) return result
                def halfDifferent = getHalfDifferentNotNeighboringSwitchPair(switchPair, "src")
                if (halfDifferent) result = [switchPair, halfDifferent]
                return result
            }
            def flow1 = getFirstFlow(switchPairs?.get(0), switchPairs?.get(1))
            def flow2 = getSecondFlow(switchPairs?.get(0), switchPairs?.get(1), flow1)
            [switchPairs: switchPairs, flow1: flow1, flow2: flow2].tap(iterationData)
        }
    }

    @IterationTag(tags = [LOW_PRIORITY], iterationNameRegex = /src1/)
    def "Able to swap endpoints (#data.description) for two flows with the same source and different destination \
switches"() {
        given: "Two flows with the same source and different destination switches"
        flowHelper.addFlow(data.flow1)
        flowHelper.addFlow(data.flow2)

        when: "Try to swap endpoints for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(data.flow1.id, flowHelper.toFlowEndpointV2(data.flow1Src),
                        flowHelper.toFlowEndpointV2(data.flow1Dst)),
                new SwapFlowPayload(data.flow2.id, flowHelper.toFlowEndpointV2(data.flow2Src),
                        flowHelper.toFlowEndpointV2(data.flow2Dst)))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)
        verifyEndpoints(data.flow1.id, data.flow2.id, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(data.flow1, data.flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(data.switchPairs[0])
        validateSwitches(data.switchPairs[1])

        where:
        data << [{
                     it.description = "src1 <-> src2"
                     it.flow1Src = it.flow2.source
                     it.flow1Dst = it.flow1.destination
                     it.flow2Src = it.flow1.source
                     it.flow2Dst = it.flow2.destination
                 },
                 {
                     it.description = "dst1 <-> dst2"
                     it.flow1Src = it.flow1.source
                     it.flow1Dst = it.flow2.destination
                     it.flow2Src = it.flow2.source
                     it.flow2Dst = it.flow1.destination
                 },
                 {
                     it.description = "src1 <-> dst2"
                     it.flow1Src = it.flow2.destination
                     it.flow1Dst = it.flow1.destination
                     it.flow2Src = it.flow2.source
                     it.flow2Dst = it.flow1.source
                 },
                 {
                     it.description = "dst1 <-> src2"
                     it.flow1Src = it.flow1.source
                     it.flow1Dst = it.flow2.source
                     it.flow2Src = it.flow1.destination
                     it.flow2Dst = it.flow2.destination
                 }].collect { iterationData ->
            def switchPairs = getSwitchPairs().all().nonNeighbouring().getSwitchPairs().inject(null) { result, switchPair ->
                if (result) return result
                def halfDifferent = getHalfDifferentNotNeighboringSwitchPair(switchPair, "src")
                if (halfDifferent) result = [switchPair, halfDifferent]
                return result
            }
            def flow1 = getFirstFlow(switchPairs?.get(0), switchPairs?.get(1))
            def flow2 = getSecondFlow(switchPairs?.get(0), switchPairs?.get(1), flow1)
            [switchPairs: switchPairs, flow1: flow1, flow2: flow2].tap(iterationData)
        }
    }

    @Tags(LOW_PRIORITY)
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
        verifyEndpoints(flow1.id, flow2.id, flow1Src, flow1Dst, flow2Src, flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(flow1, flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(swPairs[0])
        validateSwitches(swPairs[1])

        where:
        endpointsPart << ["vlans", "ports", "switches"]
        proprtyName << ["vlanId", "portNumber", "datapath"]
        description = "src1 <-> dst2, dst1 <-> src2"
        swPairs = switchPairs.all().nonNeighbouring().getSwitchPairs().inject(null) { result, switchPair ->
            if (result) return result
            def halfDifferent = getHalfDifferentNotNeighboringSwitchPair(switchPair, "dst")
            if (halfDifferent) result = [switchPair, halfDifferent]
            return result
        }
        flow1 = getFirstFlow(swPairs?.get(0), swPairs?.get(1))
        flow2 = getSecondFlow(swPairs?.get(0), swPairs?.get(1), flow1)
        flow1Src = changePropertyValue(flow1.source, proprtyName, flow2.destination."$proprtyName")
        flow1Dst = changePropertyValue(flow1.destination, proprtyName, flow2.source."$proprtyName")
        flow2Src = changePropertyValue(flow2.source, proprtyName, flow1.destination."$proprtyName")
        flow2Dst = changePropertyValue(flow2.destination, proprtyName, flow1.source."$proprtyName")
    }

    @IterationTag(tags = [LOW_PRIORITY], iterationNameRegex = /dst1/)
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
        verifyEndpoints(flow1.id, flow2.id, flow1Src, flow1Dst, flow2Src, flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(flow1, flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(flow1SwitchPair)
        validateSwitches(flow2SwitchPair)

        where:
        endpointsPart << ["vlans", "ports", "switches"]
        proprtyName << ["vlanId", "portNumber", "datapath"]
        description = "src1 <-> dst2, dst1 <-> src2"
        flow1SwitchPair = switchPairs.all().nonNeighbouring().random()
        flow2SwitchPair = getDifferentNotNeighboringSwitchPair(flow1SwitchPair)
        flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
        flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)
        flow1Src = changePropertyValue(flow1.source, proprtyName, flow2.destination."$proprtyName")
        flow1Dst = changePropertyValue(flow1.destination, proprtyName, flow2.source."$proprtyName")
        flow2Src = changePropertyValue(flow2.source, proprtyName, flow1.destination."$proprtyName")
        flow2Dst = changePropertyValue(flow2.destination, proprtyName, flow1.source."$proprtyName")
    }

    @Tags(LOW_PRIORITY)
    def "Able to swap endpoints (#data.description) for two flows with different source and destination switches"() {
        given: "Two flows with different source and destination switches"
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        when: "Try to swap endpoints for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(data.flow1Src),
                        flowHelper.toFlowEndpointV2(data.flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(data.flow2Src),
                        flowHelper.toFlowEndpointV2(data.flow2Dst)))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)
        verifyEndpoints(flow1.id, flow2.id, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(flow1, flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(flow1SwitchPair)
        validateSwitches(flow2SwitchPair)

        where:
        data << [{
                     it.description = "src1 <-> src2"
                     it.flow2 = changePropertyValue(
                             changePropertyValue(getFlowHelper().randomFlow(it.flow2SwitchPair), "source", "portNumber",
                                     it.flow1.source.portNumber), "source", "vlanId", it.flow1.source.vlanId)
                     it.flow1Src = it.flow2.source
                     it.flow1Dst = it.flow1.destination
                     it.flow2Src = it.flow1.source
                     it.flow2Dst = it.flow2.destination
                 },
                 {
                     it.description = "dst1 <-> dst2"
                     it.flow2 = changePropertyValue(
                             changePropertyValue(getFlowHelper().randomFlow(it.flow2SwitchPair), "destination", "portNumber",
                                     it.flow1.destination.portNumber), "destination", "vlanId", it.flow1.destination.vlanId)
                     it.flow1Src = it.flow1.source
                     it.flow1Dst = it.flow2.destination
                     it.flow2Src = it.flow2.source
                     it.flow2Dst = it.flow1.destination
                 },
                 {
                     it.description = "src1 <-> dst2"
                     it.flow2 = changePropertyValue(
                             changePropertyValue(getFlowHelper().randomFlow(it.flow2SwitchPair), "destination", "portNumber",
                                     it.flow1.source.portNumber), "destination", "vlanId", it.flow1.source.vlanId)
                     it.flow1Src = it.flow2.destination
                     it.flow1Dst = it.flow1.destination
                     it.flow2Src = it.flow2.source
                     it.flow2Dst = it.flow1.source
                 },
                 {
                     it.description = "dst1 <-> src2"
                     it.flow2 = changePropertyValue(
                             changePropertyValue(getFlowHelper().randomFlow(it.flow2SwitchPair), "source", "portNumber",
                                     it.flow1.destination.portNumber), "source", "vlanId", it.flow1.destination.vlanId)
                     it.flow1Src = it.flow1.source
                     it.flow1Dst = it.flow2.source
                     it.flow2Src = it.flow1.destination
                     it.flow2Dst = it.flow2.destination
                 }].collect { iterationData ->
            def flow1SwitchPair = switchPairs.all().nonNeighbouring().random()
            def flow2SwitchPair = getDifferentNotNeighboringSwitchPair(flow1SwitchPair)
            def flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
            [flow1SwitchPair: flow1SwitchPair, flow2SwitchPair: flow2SwitchPair, flow1: flow1].tap(iterationData)
        }
        flow1 = data.flow1 as FlowCreatePayload
        flow2 = data.flow2 as FlowCreatePayload
        flow1SwitchPair = data.flow1SwitchPair as SwitchPair
        flow2SwitchPair = data.flow2SwitchPair as SwitchPair
    }

    def "Unable to swap endpoints for existing flow and non-existing flow"() {
        given: "An active flow"
        def switchPair = switchPairs.all().neighbouring().random()
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
        def error = exc.responseBodyAsString.to(MessageError)
        error.errorMessage == "Could not swap endpoints"
        error.errorDescription == "Flow ${flow2.id} not found"
        def isTestComplete = true
    }

    @Tags(LOW_PRIORITY)
    def "Unable to swap #data.endpointsPart for two flows: #data.description"() {
        given: "Three active flows"
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)
        flowHelper.addFlow(flow3)

        when: "Try to swap #endpointsPart for two flows"
        northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(data.flow1Src),
                        flowHelper.toFlowEndpointV2(data.flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(data.flow2Src),
                        flowHelper.toFlowEndpointV2(data.flow2Dst)))

        then: "An error is received (409 code)"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 409
        def error = exc.responseBodyAsString.to(MessageError)
        error.errorMessage == "Could not swap endpoints"
        error.errorDescription.contains("Requested flow '$flow1.id' conflicts with existing flow '$flow3.id'.")

        where:
        data << [{
                     description = "the same ports and vlans on src switch"
                     endpointsPart = "ports and vlans"
                     flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
                     flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)
                     flow3 = getConflictingFlow(flow1SwitchPair, flow1, "source", changePropertyValue(
                             changePropertyValue(flow1.source, "portNumber", flow2.destination.portNumber), "vlanId",
                             flow2.destination.vlanId))
                     flow1Src = changePropertyValue(changePropertyValue(flow1.source, "portNumber",
                             flow2.destination.portNumber), "vlanId", flow2.destination.vlanId)
                     flow1Dst = flow1.destination
                     flow2Src = flow2.source
                     flow2Dst = changePropertyValue(changePropertyValue(flow2.destination, "portNumber",
                             flow1.source.portNumber), "vlanId", flow1.source.vlanId)
                 },
                 {
                     description = "the same ports and vlans on dst switch"
                     endpointsPart = "ports and vlans"
                     flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
                     flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)
                     flow3 = getConflictingFlow(flow1SwitchPair, flow1, "destination", changePropertyValue(
                             changePropertyValue(flow1.destination, "portNumber", flow2.source.portNumber), "vlanId",
                             flow2.source.vlanId))
                     flow1Src = flow1.source
                     flow1Dst = changePropertyValue(changePropertyValue(flow1.destination, "portNumber",
                             flow2.source.portNumber), "vlanId", flow2.source.vlanId)
                     flow2Src = changePropertyValue(changePropertyValue(flow2.source, "portNumber",
                             flow1.destination.portNumber), "vlanId", flow1.destination.vlanId)
                     flow2Dst = flow2.destination
                 },
                 {
                     description = "the same vlans on the same port on src switch"
                     endpointsPart = "vlans"
                     flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
                     flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)
                     flow3 = getConflictingFlow(flow1SwitchPair, flow1, "source",
                             changePropertyValue(flow1.source, "vlanId", flow2.destination.vlanId))
                     flow1Src = changePropertyValue(flow1.source, "vlanId", flow2.destination.vlanId)
                     flow1Dst = flow1.destination
                     flow2Src = flow2.source
                     flow2Dst = changePropertyValue(flow2.destination, "vlanId", flow1.source.vlanId)
                 },
                 {
                     description = "the same vlans on the same port on dst switch"
                     endpointsPart = "vlans"
                     flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
                     flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)
                     flow3 = getConflictingFlow(flow1SwitchPair, flow1, "destination",
                             changePropertyValue(flow1.destination, "vlanId", flow2.source.vlanId))
                     flow1Src = flow1.source
                     flow1Dst = changePropertyValue(flow1.destination, "vlanId", flow2.source.vlanId)
                     flow2Src = changePropertyValue(flow2.source, "vlanId", flow1.destination.vlanId)
                     flow2Dst = flow2.destination
                 },
                 {
                     description = "no vlans, both flows are on the same port on src switch"
                     endpointsPart = "ports"
                     flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair, true)
                     flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1, true)
                     flow3 = getConflictingFlow(flow1SwitchPair, flow1, "source",
                             changePropertyValue(flow1.source, "portNumber", flow2.destination.portNumber))
                     flow1Src = changePropertyValue(flow1.source, "portNumber", flow2.destination.portNumber)
                     flow1Dst = flow1.destination
                     flow2Src = flow2.source
                     flow2Dst = changePropertyValue(flow2.destination, "portNumber", flow1.source.portNumber)
                 },
                 {
                     description = "no vlans, both flows are on the same port on dst switch"
                     endpointsPart = "ports"
                     flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair, true)
                     flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1, true)
                     flow3 = getConflictingFlow(flow1SwitchPair, flow1, "destination",
                             changePropertyValue(flow1.destination, "portNumber", flow2.source.portNumber))
                     flow1Src = flow1.source
                     flow1Dst = changePropertyValue(flow1.destination, "portNumber", flow2.source.portNumber)
                     flow2Src = changePropertyValue(flow2.source, "portNumber", flow1.destination.portNumber)
                     flow2Dst = flow2.destination
                 }].collect { iterationData ->
            def flow1SwitchPair = switchPairs.all().nonNeighbouring().random()
            def flow2SwitchPair = getDifferentNotNeighboringSwitchPair(flow1SwitchPair)
            def flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
            [flow1SwitchPair: flow1SwitchPair, flow2SwitchPair: flow2SwitchPair, flow1: flow1].tap(iterationData)
        }
        flow1 = data.flow1 as FlowCreatePayload
        flow2 = data.flow2 as FlowCreatePayload
        flow3 = data.flow3 as FlowCreatePayload
    }

    @IterationTag(tags = [LOW_PRIORITY], iterationNameRegex = /the same src endpoint for flows/)
    def "Unable to swap endpoints for two flows (#data.description)"() {
        given: "Two active flows"
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        when: "Try to swap endpoints for two flows"
        northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(data.flow1Src),
                        flowHelper.toFlowEndpointV2(data.flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(data.flow2Src),
                        flowHelper.toFlowEndpointV2(data.flow2Dst)))

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        def error = exc.responseBodyAsString.to(MessageError)
        error.errorMessage == "Could not swap endpoints"
        error.errorDescription == "New requested endpoint for '$flow2.id' conflicts with existing endpoint for '$flow1.id'"

        where:
        data << [{
                     description = "the same src endpoint for flows"
                     flow1Src = flow1.source
                     flow1Dst = changePropertyValue(flow1.destination, "portNumber", flow2.destination.portNumber)
                     flow2Src = flow1.source
                     flow2Dst = changePropertyValue(flow2.destination, "portNumber", flow1.destination.portNumber)
                 },
                 {
                     description = "the same dst endpoint for flows"
                     flow1Src = changePropertyValue(flow1.source, "portNumber", flow2.source.portNumber)
                     flow1Dst = flow1.destination
                     flow2Src = changePropertyValue(flow2.source, "portNumber", flow1.source.portNumber)
                     flow2Dst = flow1.destination
                 }].collect { iterationData ->
            def flow1SwitchPair = switchPairs.all().nonNeighbouring().random()
            def flow2SwitchPair = getDifferentNotNeighboringSwitchPair(flow1SwitchPair)
            def flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
            def flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)
            [flow1SwitchPair: flow1SwitchPair, flow2SwitchPair: flow2SwitchPair, flow1: flow1, flow2: flow2].tap(iterationData)
        }
        flow1 = data.flow1 as FlowCreatePayload
        flow2 = data.flow2 as FlowCreatePayload
        flow1SwitchPair = data.flow1SwitchPair as SwitchPair
        flow2SwitchPair = data.flow2SwitchPair as SwitchPair
    }

    def "Unable to swap ports for two flows (port is occupied by ISL on src switch)"() {
        given: "Two active flows"
        def islPort
        def swPair = switchPairs.all().getSwitchPairs().find {
            def busyPorts = topology.getBusyPortsForSwitch(it.src)
            islPort = topology.getAllowedPortsForSwitch(it.dst).find { it in busyPorts }
        }
        assert islPort
        def flow1 = flowHelper.randomFlow(swPair)
        def flow2 = changePropertyValue(flowHelper.randomFlow(swPair, false, [flow1]),
                "destination", "portNumber", islPort)
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        when: "Try to swap ports for two flows"
        northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(
                        changePropertyValue(flow1.source, "portNumber", flow2.destination.portNumber)),
                        flowHelper.toFlowEndpointV2(flow1.destination)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(flow2.source),
                        flowHelper.toFlowEndpointV2(changePropertyValue(
                                flow1.destination, "portNumber", flow2.source.portNumber))))

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        def error = exc.responseBodyAsString.to(MessageError)
        error.errorMessage == "Could not swap endpoints"
        error.errorDescription == "The port $islPort on the switch '${swPair.src.dpId}' is occupied by an ISL (source endpoint collision)."
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Able to swap endpoints for two flows when all bandwidth on ISL is consumed"() {
        setup: "Create two flows with different source and the same destination switches"
        List<SwitchPair> switchPairs = getSwitchPairs().all().neighbouring().getSwitchPairs().inject(null) { result, switchPair ->
            if (result) return result
            def halfDifferent = getHalfDifferentNeighboringSwitchPair(switchPair, "dst")
            if (halfDifferent) result = [switchPair, halfDifferent]
            return result
        }
        SwitchPair flow1SwitchPair = switchPairs[0]
        SwitchPair flow2SwitchPair = switchPairs[1]
        def flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
        def flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)

        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        and: "Update the first flow so that it consumes all bandwidth on the link"
        def flow1Path = PathHelper.convert(northbound.getFlowPath(flow1.id))
        def flow1Isl = pathHelper.getInvolvedIsls(flow1Path)[0]
        def flow1IslMaxBw = islUtils.getIslInfo(flow1Isl).get().maxBandwidth
        northbound.updateFlow(flow1.id, flow1.tap { it.maximumBandwidth = flow1IslMaxBw })
        Wrappers.wait(FLOW_CRUD_TIMEOUT) { assert northbound.getFlowStatus(flow1.id).status == FlowState.UP }

        and: "Break all alternative paths for the first flow"
        def broughtDownIsls = topology.getRelatedIsls(flow1SwitchPair.src) - flow1Isl
        islHelper.breakIsls(broughtDownIsls)


        and: "Update max bandwidth for the second flow's link so that it is equal to max bandwidth of the first flow"
        def flow2Path = PathHelper.convert(northbound.getFlowPath(flow2.id))
        def flow2Isl = pathHelper.getInvolvedIsls(flow2Path)[0]
        islHelper.updateLinkMaxBandwidthUsingApi(flow2Isl, flow1IslMaxBw)

        and: "Break all alternative paths for the second flow"
        def flow2BroughtDownIsls = topology.getRelatedIsls(flow2SwitchPair.src) - flow2Isl - broughtDownIsls
        islHelper.breakIsls(flow2BroughtDownIsls)

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
        verifyEndpoints(flow1.id, flow2.id, flow1Src, flow1Dst, flow2Src, flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(flow1, flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(flow1SwitchPair)
        validateSwitches(flow2SwitchPair)
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Unable to swap endpoints for two flows when not enough bandwidth on ISL"() {
        setup: "Create two flows with different source and the same destination switches"
        List<SwitchPair> switchPairs = getSwitchPairs().all().neighbouring().getSwitchPairs().inject(null) { result, switchPair ->
            if (result) return result
            def halfDifferent = getHalfDifferentNeighboringSwitchPair(switchPair, "dst")
            if (halfDifferent) result = [switchPair, halfDifferent]
            return result
        }
        SwitchPair flow1SwitchPair = switchPairs[0]
        SwitchPair flow2SwitchPair = switchPairs[1]
        def flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
        def flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)

        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        and: "Update the first flow so that it consumes all bandwidth on the link"
        def flow1Path = PathHelper.convert(northbound.getFlowPath(flow1.id))
        def flow1Isl = pathHelper.getInvolvedIsls(flow1Path)[0]
        def flow1IslMaxBw = islUtils.getIslInfo(flow1Isl).get().maxBandwidth
        northbound.updateFlow(flow1.id, flow1.tap { it.maximumBandwidth = flow1IslMaxBw })
        Wrappers.wait(FLOW_CRUD_TIMEOUT) { assert northbound.getFlowStatus(flow1.id).status == FlowState.UP }

        and: "Break all alternative paths for the first flow"
        def broughtDownIsls = topology.getRelatedIsls(flow1SwitchPair.src) - flow1Isl
        islHelper.breakIsls(broughtDownIsls)

        and: "Update max bandwidth for the second flow's link so that it is not enough bandwidth for the first flow"
        def flow2Path = PathHelper.convert(northbound.getFlowPath(flow2.id))
        def flow2Isl = pathHelper.getInvolvedIsls(flow2Path)[0]
        islHelper.updateLinkMaxBandwidthUsingApi(flow2Isl, flow1IslMaxBw - 1)

        and: "Break all alternative paths for the second flow"
        def flow2BroughtDownIsls = topology.getRelatedIsls(flow2SwitchPair.src) - flow2Isl - broughtDownIsls
        islHelper.breakIsls(flow2BroughtDownIsls)

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

        then: "An error is received (500 code)"
        def exc = thrown(HttpServerErrorException)
        exc.rawStatusCode == 500
        def error = exc.responseBodyAsString.to(MessageError)
        error.errorMessage == "Could not swap endpoints"
        error.errorDescription.contains("Not enough bandwidth or no path found")
    }

    @Tags([LOW_PRIORITY, ISL_RECOVER_ON_FAIL])
    def "Able to swap endpoints for two flows when not enough bandwidth on ISL and ignore_bandwidth=true"() {
        setup: "Create two flows with different source and the same destination switches"
        List<SwitchPair> switchPairs = getSwitchPairs().all().neighbouring().getSwitchPairs().inject(null) { result, switchPair ->
            if (result) return result
            def halfDifferent = getHalfDifferentNeighboringSwitchPair(switchPair, "dst")
            if (halfDifferent) result = [switchPair, halfDifferent]
            return result
        }
        SwitchPair flow1SwitchPair = switchPairs[0]
        SwitchPair flow2SwitchPair = switchPairs[1]
        def flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
        def flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)

        flowHelper.addFlow(flow1.tap { it.ignoreBandwidth = true })
        flowHelper.addFlow(flow2)

        and: "Update the first flow so that it consumes all bandwidth on the link"
        def flow1Path = PathHelper.convert(northbound.getFlowPath(flow1.id))
        def flow1Isl = pathHelper.getInvolvedIsls(flow1Path)[0]
        def flow1IslMaxBw = islUtils.getIslInfo(flow1Isl).get().maxBandwidth
        northbound.updateFlow(flow1.id, flow1.tap { it.maximumBandwidth = flow1IslMaxBw })
        Wrappers.wait(FLOW_CRUD_TIMEOUT) { assert northbound.getFlowStatus(flow1.id).status == FlowState.UP }

        and: "Break all alternative paths for the first flow"
        def broughtDownIsls = topology.getRelatedIsls(flow1SwitchPair.src) - flow1Isl
        islHelper.breakIsls(broughtDownIsls)

        and: "Update max bandwidth for the second flow's link so that it is not enough bandwidth for the first flow"
        def flow2Path = PathHelper.convert(northbound.getFlowPath(flow2.id))
        def flow2Isl = pathHelper.getInvolvedIsls(flow2Path)[0]
        islHelper.updateLinkMaxBandwidthUsingApi(flow2Isl, flow1IslMaxBw - 1)

        and: "Break all alternative paths for the second flow"
        def flow2BroughtDownIsls = topology.getRelatedIsls(flow2SwitchPair.src) - flow2Isl - broughtDownIsls
        islHelper.breakIsls(flow2BroughtDownIsls)

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
        verifyEndpoints(flow1.id, flow2.id, flow1Src, flow1Dst, flow2Src, flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(flow1, flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(flow1SwitchPair)
        validateSwitches(flow2SwitchPair)
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Unable to swap endpoints for two flows when one of them is inactive"() {
        setup: "Create two flows with different source and the same destination switches"
        def switchPairs = getSwitchPairs().all().neighbouring().getSwitchPairs().inject(null) { result, switchPair ->
            if (result) return result
            def halfDifferent = getHalfDifferentNeighboringSwitchPair(switchPair, "dst")
            if (halfDifferent) result = [switchPair, halfDifferent]
            return result
        }
        SwitchPair flow1SwitchPair = switchPairs[0]
        SwitchPair flow2SwitchPair = switchPairs[1]
        def flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
        def flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)

        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)
        def flow1Path = PathHelper.convert(northbound.getFlowPath(flow1.id))
        def flow2Path = PathHelper.convert(northbound.getFlowPath(flow2.id))
        def involvedSwIds = (
                pathHelper.getInvolvedSwitches(flow1Path)*.dpId + pathHelper.getInvolvedSwitches(flow2Path)*.dpId
        ).unique()

        and: "Break all paths for the first flow"
        def broughtDownIsls = topology.getRelatedIsls(flow1SwitchPair.src)
        islHelper.breakIsls(broughtDownIsls)


        when: "Try to swap endpoints for two flows"
        northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(flow2.source),
                        flowHelper.toFlowEndpointV2(flow1.destination)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(flow1.source),
                        flowHelper.toFlowEndpointV2(flow2.destination)))

        then: "An error is received (500 code)"
        def exc = thrown(HttpServerErrorException)
        new FlowEndpointsNotSwappedExpectedError(~/Not enough bandwidth or no path found/).matches(exc)

        and: "All involved switches are valid"
        /** https://github.com/telstra/open-kilda/issues/3770
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert switchHelper.validateAndGetFixedEntries(involvedSwIds).isEmpty()
        }
        Boolean isTestCompleted = true **/
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwIds)
    }

    @Tags(LOW_PRIORITY)
    def "Able to swap endpoints (#data.description) for two protected flows"() {
        given: "Two protected flows with different source and destination switches"
        flowHelper.addFlow(flow1.tap { it.allocateProtectedPath = true })
        flowHelper.addFlow(flow2.tap { it.allocateProtectedPath = true })

        when: "Try to swap endpoints for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(data.flow1Src),
                        flowHelper.toFlowEndpointV2(data.flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(data.flow2Src),
                        flowHelper.toFlowEndpointV2(data.flow2Dst)))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)
        verifyEndpoints(flow1.id, flow2.id, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(flow1, flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(flow1SwitchPair)
        validateSwitches(flow2SwitchPair)

        where:
        data << [{
                     description = "src1 <-> src2"
                     flow1Src = flow2.source
                     flow1Dst = flow1.destination
                     flow2Src = flow1.source
                     flow2Dst = flow2.destination
                 },
                 {
                     description = "dst1 <-> dst2"
                     flow1Src = flow1.source
                     flow1Dst = flow2.destination
                     flow2Src = flow2.source
                     flow2Dst = flow1.destination
                 },
                 {
                     description = "src1 <-> dst2"
                     flow1Src = flow2.destination
                     flow1Dst = flow1.destination
                     flow2Src = flow2.source
                     flow2Dst = flow1.source
                 },
                 {
                     description = "dst1 <-> src2"
                     flow1Src = flow1.source
                     flow1Dst = flow2.source
                     flow2Src = flow1.destination
                     flow2Dst = flow2.destination
                 }].collect { iterationData ->
            def flow1SwitchPair = switchPairs.all().nonNeighbouring().random()
            def flow2SwitchPair = getDifferentNotNeighboringSwitchPair(flow1SwitchPair)
            def flow1 = getFlowHelper().randomFlow(flow1SwitchPair)
            def flow2 = getFlowHelper().randomFlow(flow2SwitchPair, false, [flow1]).tap {
                it.source.portNumber = getFreePort(flow2SwitchPair.src, [flow1SwitchPair.src, flow1SwitchPair.dst])
                it.destination.portNumber = getFreePort(flow2SwitchPair.dst, [flow1SwitchPair.src, flow1SwitchPair.dst])
            }
            [flow1SwitchPair: flow1SwitchPair, flow2SwitchPair: flow2SwitchPair, flow1: flow1, flow2: flow2].tap(iterationData)
        }
        flow1 = data.flow1 as FlowCreatePayload
        flow2 = data.flow2 as FlowCreatePayload
        flow1SwitchPair = data.flow1SwitchPair as SwitchPair
        flow2SwitchPair = data.flow2SwitchPair as SwitchPair
    }

    def "A protected flow with swapped endpoint allows traffic on main and protected paths"() {
        given: "Two protected flows with different source and destination switches"
        def flow1SwitchPair = switchPairs.all(false)
                .neighbouring()
                .withAtLeastNTraffgensOnDestination(1)
                .random()
        def flow2SwitchPair = switchPairs.all(false)
                .neighbouring()
                .excludeSwitches([flow1SwitchPair.getSrc(), flow1SwitchPair.getDst()])
                .withAtLeastNTraffgensOnSource(1)
                .random()
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
        verifyEndpoints(flow1.id, flow2.id, flow2.source, flow1.destination, flow1.source, flow2.destination)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(flow1, flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(flow1SwitchPair)
        validateSwitches(flow2SwitchPair)
        def isSwitchValid = true

        and: "The first flow allows traffic on the main path"
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam)
                .buildBidirectionalExam(northbound.getFlow(flow1.id), 0, 5)
        [exam.forward, exam.reverse].each { direction ->
            def resources = traffExam.startExam(direction)
            direction.setResources(resources)
            assert traffExam.waitExam(direction).hasTraffic()
        }

        and: "The first flow allows traffic on the protected path"
        northbound.swapFlowPath(flow1.id)
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow1.id).status == FlowState.UP }

        def newExam = new FlowTrafficExamBuilder(topology, traffExam)
                .buildBidirectionalExam(northbound.getFlow(flow1.id), 0, 5)
        [newExam.forward, newExam.reverse].each { direction ->
            def resources = traffExam.startExam(direction)
            direction.setResources(resources)
            assert traffExam.waitExam(direction).hasTraffic()
        }
    }

    @Tags(LOW_PRIORITY)
    def "Able to swap endpoints (#data.description) for two vxlan flows with the same source and destination switches"() {
        given: "Two flows with the same source and destination switches"
        flow1.encapsulationType = FlowEncapsulationType.VXLAN
        flow2.encapsulationType = FlowEncapsulationType.VXLAN
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        when: "Try to swap endpoints for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(data.flow1Src),
                        flowHelper.toFlowEndpointV2(data.flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(data.flow2Src),
                        flowHelper.toFlowEndpointV2(data.flow2Dst)))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)
        verifyEndpoints(flow1.id, flow2.id, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(flow1, flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(switchPair)

        where:
        data << [{
                     description = "src1 <-> src2"
                     flow1Src = flow2.source
                     flow1Dst = flow1.destination
                     flow2Src = flow1.source
                     flow2Dst = flow2.destination
                 },
                 {
                     description = "dst1 <-> dst2"
                     flow1Src = flow1.source
                     flow1Dst = flow2.destination
                     flow2Src = flow2.source
                     flow2Dst = flow1.destination
                 }].collect { iterationData ->
            def switchPair = switchPairs.all().neighbouring().withBothSwitchesVxLanEnabled().random()
            def flow1 = getFirstFlow(switchPair, switchPair)
            def flow2 = getSecondFlow(switchPair, switchPair, flow1)
            [switchPair: switchPair, flow1: flow1, flow2: flow2].tap(iterationData)
        }
        switchPair = data.switchPair as SwitchPair
        flow1 = data.flow1 as FlowCreatePayload
        flow2 = data.flow2 as FlowCreatePayload
    }

    def "Able to swap endpoints (#data.description) for two qinq flows with the same source and destination switches"() {
        given: "Two flows with the same source and destination switches"
        flow1.source.innerVlanId = 300
        flow1.destination.innerVlanId = 400
        flow2.source.innerVlanId = 500
        flow2.destination.innerVlanId = 600
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        when: "Try to swap endpoints for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(data.flow1Src),
                        flowHelper.toFlowEndpointV2(data.flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(data.flow2Src),
                        flowHelper.toFlowEndpointV2(data.flow2Dst)))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)
        verifyEndpoints(flow1.id, flow2.id, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(flow1, flow2)

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches(switchPair)

        where:
        data << [{
                     description = "src1 <-> src2"
                     flow1Src = flow2.source
                     flow1Dst = flow1.destination
                     flow2Src = flow1.source
                     flow2Dst = flow2.destination
                 },
                 {
                     description = "dst1 <-> dst2"
                     flow1Src = flow1.source
                     flow1Dst = flow2.destination
                     flow2Src = flow2.source
                     flow2Dst = flow1.destination
                 }].collect { iterationData ->
            def switchPair = switchPairs.all().neighbouring().random()
            def flow1 = getFirstFlow(switchPair, switchPair).tap {
                source.innerVlanId = 300
                destination.innerVlanId = 400
            }
            def flow2 = getSecondFlow(switchPair, switchPair, flow1).tap {
                source.innerVlanId = 500
                destination.innerVlanId = 600
            }
            [switchPair: switchPair, flow1: flow1, flow2: flow2].tap(iterationData)
        }
        switchPair = data.switchPair as SwitchPair
        flow1 = data.flow1 as FlowCreatePayload
        flow2 = data.flow2 as FlowCreatePayload
    }

    @Tags(SWITCH_RECOVER_ON_FAIL)
    def "System reverts both flows if fails during rule installation when swapping endpoints"() {
        given: "Two flows with different src switches and same dst"
        def swPair1 = switchPairs.all().random()
        def swPair2 = switchPairs.all()
                .excludeSwitches([swPair1.getSrc()])
                .includeSourceSwitch(swPair1.getDst())
                .random()
                .getReversed()
        def flow1 = flowHelperV2.randomFlow(swPair1).tap {
            it.source.portNumber = getFreePort(swPair1.src, [swPair2.src])
        }
        def flow2 = flowHelperV2.randomFlow(swPair2).tap {
            it.source.portNumber = getFreePort(swPair2.src, [swPair1.src])
        }
        flowHelperV2.addFlow(flow1)
        flowHelperV2.addFlow(flow2)

        when: "Try to swap flow src endoints, but flow1 src switch does not respond"
        def blockData = switchHelper.knockoutSwitch(swPair1.src, RW)
        database.setSwitchStatus(swPair1.src.dpId, SwitchStatus.ACTIVE)
        northbound.swapFlowEndpoint(new SwapFlowPayload(flow1.flowId, flow2.source, flow1.destination),
                new SwapFlowPayload(flow2.flowId, flow1.source, flow2.destination))

        then: "Receive error response"
        def exc = thrown(HttpServerErrorException)
        exc.rawStatusCode == 500
        def error = exc.responseBodyAsString.to(MessageError)
        error.errorMessage == "Could not swap endpoints"
        error.errorDescription == sprintf("Reverted flows: [%s, %s]", flow2.flowId, flow1.flowId)

        and: "First flow is reverted to Down"
        Wrappers.wait(PATH_INSTALLATION_TIME + WAIT_OFFSET * 2) { // sometimes it takes more time on jenkins
            assert northboundV2.getFlowStatus(flow1.flowId).status == FlowState.DOWN
            def flowHistory = northbound.getFlowHistory(flow1.flowId)
            /* '||' due to instability on jenkins
                * locally: it always retry to reroute (reason of failed reroute: 'No bandwidth or path...')
                * jenkins: - reroute(ISL_1 become INACTIVE) + retry or reroute(ISL_1) + reroute(ISL_2);
                *          - or one reroute only. (reason of failed reroute: 'Failed to allocate flow resources...')
                */
            assert flowHistory.findAll {
                it.action == REROUTE_ACTION && it.payload.last().action == REROUTE_FAIL
            }.size() > 1 || flowHistory.find {
                it.action == REROUTE_ACTION && it.payload.last().action == REROUTE_FAIL &&
                        it.payload.last().details.contains("Failed to allocate flow resources.")
            }
        }
        with(northboundV2.getFlow(flow1.flowId)) {
            source == flow1.source
            destination == flow1.destination
        }

        and: "Second flow is reverted to UP"
        Wrappers.wait(PATH_INSTALLATION_TIME) {
            assert northboundV2.getFlowStatus(flow2.flowId).status == FlowState.UP
        }
        with(northboundV2.getFlow(flow2.flowId)) {
            source == flow2.source
            destination == flow2.destination
        }

        when: "Delete both flows"
        def switches = (pathHelper.getInvolvedSwitches(flow1.flowId) +
                pathHelper.getInvolvedSwitches(flow2.flowId)).unique().findAll { it.dpId != swPair1.src.dpId }
        [flow1, flow2].collect { flowHelperV2.deleteFlow(it.flowId) }

        then: "Related switches have no rule anomalies"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(switches*.getDpId()).isEmpty()

        cleanup:
        if (blockData) {
            database.setSwitchStatus(swPair1.src.dpId, SwitchStatus.INACTIVE)
        }
    }

    def "Able to swap endpoints for a flow with flowLoop"() {
        setup: "Create two flows with the same src and different dst switches"
        def flow1SwitchPair = switchPairs.all().neighbouring().withTraffgensOnBothEnds().random().getReversed()
        def flow2SwitchPair = switchPairs.all().neighbouring()
                .includeSourceSwitch(flow1SwitchPair.getSrc())
                .excludeDestinationSwitches([flow1SwitchPair.getDst()])
                .random()
        def flow1 = flowHelper.randomFlow(flow1SwitchPair)
        def flow2 = flowHelper.randomFlow(flow2SwitchPair, true, [flow1])

        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        and: "FlowLoop is created for the second flow on the dst switch"
        northboundV2.createFlowLoop(flow2.id, new FlowLoopPayload(flow2SwitchPair.dst.dpId))
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getFlowStatus(flow2.id).status == FlowState.UP
        }

        when: "Try to swap dst endpoints for two flows"
        def flow1Dst = flow2.destination
        def flow1Src = flow1.source
        def flow2Dst = flow1.destination
        def flow2Src = flow2.source
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flowHelper.toFlowEndpointV2(flow1Src),
                        flowHelper.toFlowEndpointV2(flow1Dst)),
                new SwapFlowPayload(flow2.id, flowHelper.toFlowEndpointV2(flow2Src),
                        flowHelper.toFlowEndpointV2(flow2Dst)))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, flow1Src, flow1Dst, flow2Src, flow2Dst)
        verifyEndpoints(flow1.id, flow2.id, flow1Src, flow1Dst, flow2Src, flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        validateFlows(flow1, flow2)

        and: "FlowLoop is still created for the second flow but on the new dst switch"
        with(northbound.getFlow(flow2.id)) {
            it.loopSwitchId == flow1SwitchPair.dst.dpId
        }

        and: "FlowLoop rules are created on the new dst switch"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            Map<Long, Long> flowLoopsCounter = getFlowLoopRules(flow1SwitchPair.dst.dpId)
                    .collectEntries { [(it.cookie): it.packetCount] }
            assert flowLoopsCounter.size() == 2
            assert flowLoopsCounter.values().every { it == 0 }
        }

        and: "FlowLoop rules are deleted on the old dst switch"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert getFlowLoopRules(flow2SwitchPair.dst.dpId).empty
        }

        and: "Switch validation doesn't show any missing/excess rules and meters"
        validateSwitches([flow1SwitchPair.src, flow1SwitchPair.dst, flow2SwitchPair.src, flow2SwitchPair.dst].unique())
        def switchesAreValid = true

        when: "Send traffic via flow2"
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam)
                .buildBidirectionalExam(northbound.getFlow(flow2.id), 1000, 5)

        then: "Flow doesn't allow traffic, because it is grubbed by flowLoop rules"
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert !traffExam.waitExam(direction).hasTraffic()
            }
        }

        and: "Counter on the flowLoop rules are increased"
        getFlowLoopRules(flow1SwitchPair.dst.dpId)*.packetCount.every { it > 0 }
    }

    void verifyEndpoints(response, FlowEndpointPayload flow1SrcExpected, FlowEndpointPayload flow1DstExpected,
                         FlowEndpointPayload flow2SrcExpected, FlowEndpointPayload flow2DstExpected) {
        verifyEndpoints(response, flowHelper.toFlowEndpointV2(flow1SrcExpected),
                flowHelper.toFlowEndpointV2(flow1DstExpected), flowHelper.toFlowEndpointV2(flow2SrcExpected),
                flowHelper.toFlowEndpointV2(flow2DstExpected))
    }

    void verifyEndpoints(response, FlowEndpointV2 flow1SrcExpected, FlowEndpointV2 flow1DstExpected,
                         FlowEndpointV2 flow2SrcExpected, FlowEndpointV2 flow2DstExpected) {
        assert response.firstFlow.source == flow1SrcExpected
        assert response.firstFlow.destination == flow1DstExpected
        assert response.secondFlow.source == flow2SrcExpected
        assert response.secondFlow.destination == flow2DstExpected
    }

    void verifyEndpoints(flow1Id, flow2Id, FlowEndpointV2 flow1SrcExpected, FlowEndpointV2 flow1DstExpected,
                         FlowEndpointV2 flow2SrcExpected, FlowEndpointV2 flow2DstExpected) {
        def flow1Updated = northbound.getFlow(flow1Id)
        def flow2Updated = northbound.getFlow(flow2Id)

        assert flowHelper.toFlowEndpointV2(flow1Updated.source) == flow1SrcExpected
        assert flowHelper.toFlowEndpointV2(flow1Updated.destination) == flow1DstExpected
        assert flowHelper.toFlowEndpointV2(flow2Updated.source) == flow2SrcExpected
        assert flowHelper.toFlowEndpointV2(flow2Updated.destination) == flow2DstExpected
    }

    void verifyEndpoints(flow1Id, flow2Id, FlowEndpointPayload flow1SrcExpected, FlowEndpointPayload flow1DstExpected,
                         FlowEndpointPayload flow2SrcExpected, FlowEndpointPayload flow2DstExpected) {
        verifyEndpoints(flow1Id, flow2Id, flowHelper.toFlowEndpointV2(flow1SrcExpected),
                flowHelper.toFlowEndpointV2(flow1DstExpected), flowHelper.toFlowEndpointV2(flow2SrcExpected),
                flowHelper.toFlowEndpointV2(flow2DstExpected))
    }

    void validateFlows(flow1, flow2) {
        Wrappers.wait(RULES_DELETION_TIME + RULES_INSTALLATION_TIME) {
            [flow1, flow2].each {
                assert northbound.validateFlow(it.id).each { direction -> assert direction.asExpected }
            }
        }
    }

    void validateSwitches(SwitchPair switchPair) {
        validateSwitches(switchPair.toList())
    }

    void validateSwitches(List<Switch> switches) {
        Wrappers.wait(RULES_DELETION_TIME + RULES_INSTALLATION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(switches*.getDpId()).isEmpty()
        }
    }

    /**
     * Get port number which is not busy on _any_ of the specified switches
     *
     * @param target switch on which to look for a port
     * @param switches list of switches where resulting port should not be an ISL-busy port
     * @return portnumber which is not an ISL-port on any of the switches
     */
    Integer getFreePort(Switch target, List<Switch> switches, List<Integer> excludePorts = []) {
        pickRandom(topology.getAllowedPortsForSwitch(target) -
                switches.collectMany {
                    def islPorts = topology.getBusyPortsForSwitch(it)
                    def s42Port = it.prop?.server42Port
                    s42Port ? islPorts + s42Port : islPorts
                } - excludePorts)
    }

    /**
     * Get a free vlan which is not used in any of the given flows.
     */
    def getFreeVlan(SwitchId swId, List<FlowCreatePayload> existingFlows = []) {
        def r = new Random()
        def vlans = (flowHelper.KILDA_ALLOWED_VLANS - existingFlows.collectMany { [it.source, it.destination] }.findAll {
            it.datapath == swId
        }.collect { it.vlanId })
        return vlans[r.nextInt(vlans.size())]
    }

    /**
     * Get a FlowCreatePayload instance for the flow. The instance is built considering ISL ports on source and
     * destination switches of the first and second switch pairs. So no ISL port conflicts should appear while creating
     * the flow and swapping flow endpoints.
     *
     * @param firstFlowSwitchPair Switch pair for the first flow
     * @param secondFlowSwitchPair Switch pair for the second flow
     * @return a FlowCreatePayload instance
     */
    def getFirstFlow(SwitchPair firstFlowSwitchPair, SwitchPair secondFlowSwitchPair, noVlans = false) {
        assumeTrue(firstFlowSwitchPair && secondFlowSwitchPair, "Required conditions for switch-pairs for this test are not met")
        def firstFlow = flowHelper.randomFlow(firstFlowSwitchPair)
        firstFlow.source.portNumber = (topology.getAllowedPortsForSwitch(firstFlowSwitchPair.src) -
                topology.getBusyPortsForSwitch(secondFlowSwitchPair.src) -
                topology.getBusyPortsForSwitch(secondFlowSwitchPair.dst) -
                firstFlowSwitchPair.dst.prop?.server42Port -
                secondFlowSwitchPair.src.prop?.server42Port -
                secondFlowSwitchPair.dst.prop?.server42Port)[0]
        firstFlow.destination.portNumber = (topology.getAllowedPortsForSwitch(firstFlowSwitchPair.dst) -
                topology.getBusyPortsForSwitch(secondFlowSwitchPair.src) -
                topology.getBusyPortsForSwitch(secondFlowSwitchPair.dst) - [firstFlow.source.portNumber] -
                firstFlowSwitchPair.src.prop?.server42Port -
                secondFlowSwitchPair.src.prop?.server42Port -
                secondFlowSwitchPair.dst.prop?.server42Port)[0]

        if (noVlans) {
            firstFlow.source.vlanId = null
            firstFlow.destination.vlanId = null
        }

        return firstFlow
    }

    private static <T> T pickRandom(List<T> c) {
        def r = new Random()
        c[r.nextInt(c.size())]
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
        assumeTrue(firstFlowSwitchPair && secondFlowSwitchPair, "Required conditions for switch-pairs for this test are not met")
        def secondFlow = flowHelper.randomFlow(secondFlowSwitchPair)
        secondFlow.source.portNumber = (topology.getAllowedPortsForSwitch(secondFlowSwitchPair.src) -
                topology.getBusyPortsForSwitch(firstFlowSwitchPair.src) -
                topology.getBusyPortsForSwitch(firstFlowSwitchPair.dst) -
                [firstFlow.source.portNumber, firstFlow.destination.portNumber] -
                secondFlowSwitchPair.dst.prop?.server42Port -
                firstFlowSwitchPair.src.prop?.server42Port -
                firstFlowSwitchPair.dst.prop?.server42Port)[0]
        secondFlow.destination.portNumber = (topology.getAllowedPortsForSwitch(secondFlowSwitchPair.dst) -
                topology.getBusyPortsForSwitch(firstFlowSwitchPair.src) -
                topology.getBusyPortsForSwitch(firstFlowSwitchPair.dst) -
                [secondFlow.source.portNumber, firstFlow.source.portNumber, firstFlow.destination.portNumber] -
                secondFlowSwitchPair.src.prop?.server42Port -
                firstFlowSwitchPair.src.prop?.server42Port -
                firstFlowSwitchPair.dst.prop?.server42Port)[0]

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
        switchPairs.all().nonNeighbouring().getSwitchPairs().find {
            it."$equalEndpoint" == switchPairToAvoid."$equalEndpoint" &&
                    it."$differentEndpoint" != switchPairToAvoid."$differentEndpoint"
        }
    }

    def getDifferentNotNeighboringSwitchPair(switchPairToAvoid) {
        switchPairs.all().nonNeighbouring().getSwitchPairs().find {
            !(it.src in [switchPairToAvoid.src, switchPairToAvoid.dst]) &&
                    !(it.dst in [switchPairToAvoid.src, switchPairToAvoid.dst])
        }
    }

    def findSw(SwitchId swId) {
        topology.switches.find { it.dpId == swId }
    }

    def getHalfDifferentNeighboringSwitchPair(switchPairToAvoid, equalEndpoint) {
        def differentEndpoint = (equalEndpoint == "src" ? "dst" : "src")
        switchPairs.all().neighbouring().getSwitchPairs().find {
            it."$equalEndpoint" == switchPairToAvoid."$equalEndpoint" &&
                    it."$differentEndpoint" != switchPairToAvoid."$differentEndpoint"
        }
    }

    def getFlowLoopRules(SwitchId switchId) {
        northbound.getSwitchRules(switchId).flowEntries.findAll {
            def hexCookie = Long.toHexString(it.cookie)
            hexCookie.startsWith("20080000") || hexCookie.startsWith("40080000")
        }
    }
}
