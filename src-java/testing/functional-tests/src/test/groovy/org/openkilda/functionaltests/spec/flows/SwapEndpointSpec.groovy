package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.helpers.model.FlowActionType.REROUTE
import static org.openkilda.functionaltests.helpers.model.FlowActionType.REROUTE_FAILED
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowEndpointsNotSwappedExpectedError
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.functionaltests.helpers.model.SwitchRulesFactory
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.model.SwitchStatus
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.flows.SwapFlowEndpointPayload
import org.openkilda.northbound.dto.v2.flows.SwapFlowPayload
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.tools.SoftAssertions

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import spock.lang.Ignore
import spock.lang.Shared

import javax.inject.Provider

class SwapEndpointSpec extends HealthCheckSpecification {
    //Kilda allows user to pass reserved VLAN IDs 1 and 4095 if they want.
    static final IntRange KILDA_ALLOWED_VLANS = 1..4095

    @Autowired
    @Shared
    FlowFactory flowFactory

    @Autowired
    @Shared
    SwitchRulesFactory switchRulesFactory

    @Autowired
    Provider<TraffExamService> traffExamProvider

    def setupSpec() {
        upTraffGenPortsIfRequired()
    }

    def "Able to swap endpoints(#data.description)"() {
        given: "Some flows in the system according to preconditions"
        flows.each { it.create() }

        when: "Try to swap endpoints with #data.description"
        def response = northbound.swapFlowEndpoint(firstSwap, secondSwap)

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, firstSwap.source, firstSwap.destination, secondSwap.source, secondSwap.destination)

        verifyEndpoints(flows.find { it.flowId == firstSwap.flowId },
                flows.find { it.flowId == secondSwap.flowId },
                firstSwap.source, firstSwap.destination, secondSwap.source, secondSwap.destination)

        and: "Flows validation doesn't show any rule discrepancies"
        Wrappers.wait(RULES_DELETION_TIME + RULES_INSTALLATION_TIME) {
            flows.each { flowExtended ->
                assert flowExtended.validateAndCollectDiscrepancies().isEmpty()
            }
        }

        and: "Switch validation doesn't show any missing/excess rules and meters"
        List<SwitchId> involvedSwitches = flows.collectMany { flowExtended ->
            flowExtended.retrieveAllEntityPaths().getInvolvedSwitches()
        }.unique()
        Wrappers.wait(RULES_DELETION_TIME + RULES_INSTALLATION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(involvedSwitches).isEmpty()
        }

        where:
        data << [
                [description: "no vlan vs vlan on the same port on src switch"].tap {
                    def switchPair = getSwitchPairs().all().nonNeighbouring().random()
                    def flow1 = getFlowFactory().getBuilder(switchPair)
                            .withSourcePort(getFreePort(switchPair.src, [switchPair.dst]))
                            .withSourceVlan(0).build()
                    def flow2 = getFlowFactory().getBuilder(switchPair, false, flow1.occupiedEndpoints())
                            .withSourcePort(flow1.source.portNumber).build()
                    it.flows = [flow1, flow2]
                    it.firstSwap = new SwapFlowPayload(flow1.flowId, flow2.source, flow1.destination)
                    it.secondSwap = new SwapFlowPayload(flow2.flowId, flow1.source, flow2.destination)
                },
                [description: "same port, swap vlans on dst switch + third idle novlan flow on that port"].tap {
                    def switchPair = getSwitchPairs().all().nonNeighbouring().random()
                    def flow1 = getFlowFactory().getBuilder(switchPair)
                            .withDestinationPort(getFreePort(switchPair.dst, [switchPair.src])).build()
                    List<SwitchPortVlan> busyEndpoints = flow1.occupiedEndpoints()
                    def flow2 = getFlowFactory().getBuilder(switchPair, false, busyEndpoints)
                            .withDestinationPort(flow1.destination.portNumber)
                            .build()
                    flow2.destination.vlanId = getFreeVlan(flow2.destination.switchId, busyEndpoints)
                    busyEndpoints.addAll(flow2.occupiedEndpoints())
                    def flow3 = getFlowFactory().getBuilder(switchPair, false, busyEndpoints)
                            .withDestinationPort(flow1.destination.portNumber)
                            .withDestinationVlan(0).build()
                    it.flows = [flow1, flow2, flow3]

                    it.firstSwap = new SwapFlowPayload(flow1.flowId, flow1.source, flow2.destination)
                    it.secondSwap = new SwapFlowPayload(flow2.flowId, flow2.source, flow1.destination)
                },
                [description: "vlan on src1 <-> vlan on dst2, same port numbers"].tap {
                    def switchPair = getSwitchPairs().all().nonNeighbouring().random()
                    def flow1 = getFlowFactory().getBuilder(switchPair)
                            .withSourcePort(getFreePort(switchPair.src, [switchPair.dst])).build()
                    def flow2 = getFlowFactory().getBuilder(switchPair, false, flow1.occupiedEndpoints())
                            .withDestinationPort(flow1.source.portNumber).build()
                    it.flows = [flow1, flow2]
                    it.firstSwap = new SwapFlowPayload(flow1.flowId,
                            flow1.source.tap { it.vlanId = flow2.destination.vlanId },
                            flow1.destination)
                    it.secondSwap = new SwapFlowPayload(flow2.flowId,
                            flow2.source,
                           flow2.destination.tap { it.vlanId = flow1.source.vlanId })
                },
                [description: "port on dst1 <-> port on src2, vlans are equal"].tap {
                    def switchPair = getSwitchPairs().all().nonNeighbouring().random()
                    def flow1 = getFlowFactory().getBuilder(switchPair, false).build()
                    def flow2 = getFlowFactory().getBuilder(switchPair, false, flow1.occupiedEndpoints())
                            .withSourceVlan(flow1.source.vlanId)
                            .build()
                    flow1.destination.portNumber = getFreePort(switchPair.dst, [switchPair.src],
                            [flow1.source.portNumber, flow2.source.portNumber])
                    flow2.source.portNumber = getFreePort(switchPair.src, [switchPair.dst],
                            [flow2.destination.portNumber, flow1.source.portNumber])
                    it.flows = [flow1, flow2]
                    it.firstSwap = new SwapFlowPayload(flow1.flowId,
                            flow1.source,
                            flow1.destination.tap { it.portNumber = flow2.source.portNumber })
                    it.secondSwap = new SwapFlowPayload(flow2.flowId,
                            flow2.source.tap { it.portNumber = flow1.destination.portNumber },
                            flow2.destination)
                },
                [description: "switch on src1 <-> switch on dst2, other params random"].tap {
                    def switchPair = getSwitchPairs().all().nonNeighbouring().random()
                    def flow1 =  getFlowFactory().getBuilder(switchPair)
                            .withSourcePort(getFreePort(switchPair.src, [switchPair.dst])).build()
                    def flow2 = getFlowFactory().getBuilder(switchPair, false, flow1.occupiedEndpoints())
                            .withDestinationPort(getFreePort(switchPair.dst, [switchPair.src])).build()
                    it.flows = [flow1, flow2]
                    it.firstSwap = new SwapFlowPayload(flow1.flowId,
                            flow1.source.tap { it.switchId = flow2.destination.switchId },
                            flow1.destination)
                    it.secondSwap = new SwapFlowPayload(flow2.flowId,
                            flow2.source,
                            flow2.destination.tap { it.switchId = flow1.source.switchId })
                },
                [description: "both endpoints swap, same switches"].tap {
                    def switchPair = getSwitchPairs().all().nonNeighbouring().random()
                    def flow1 = getFlowFactory().getBuilder(switchPair)
                            .withSourcePort(getFreePort(switchPair.src, [switchPair.dst]))
                            .withDestinationPort(getFreePort(switchPair.dst, [switchPair.src])).build()
                    def flow2 = getFlowFactory().getBuilder(switchPair, false, flow1.occupiedEndpoints())
                            .withSourcePort(getFreePort(switchPair.src, [switchPair.dst]))
                            .withDestinationPort(getFreePort(switchPair.dst, [switchPair.src])).build()
                    it.flows = [flow1, flow2]
                    it.firstSwap = new SwapFlowPayload(flow1.flowId, flow2.source, flow2.destination)
                    it.secondSwap = new SwapFlowPayload(flow2.flowId, flow1.source, flow1.destination)
                },
                [description: "endpoints src1 <-> dst2, same switches"].tap {
                    def switchPair = getSwitchPairs().all().nonNeighbouring().random()
                    def flow1 = getFlowFactory().getBuilder(switchPair)
                            .withSourcePort(getFreePort(switchPair.src, [switchPair.dst]))
                            .withDestinationPort(getFreePort(switchPair.dst, [switchPair.src])).build()
                    def flow2 = getFlowFactory().getBuilder(switchPair, false, flow1.occupiedEndpoints())
                            .withSourcePort(getFreePort(switchPair.src, [switchPair.dst], [flow1.source.portNumber]))
                            .withDestinationPort(getFreePort(switchPair.dst, [switchPair.src], [flow1.destination.portNumber])).build()
                    flow1.source.vlanId = getFreeVlan(flow2.destination.switchId, flow2.occupiedEndpoints())
                    flow2.destination.vlanId = getFreeVlan(flow1.destination.switchId, flow1.occupiedEndpoints())
                    it.flows = [flow1, flow2]
                    it.firstSwap = new SwapFlowPayload(flow1.flowId, flow2.destination, flow1.destination)
                    it.secondSwap = new SwapFlowPayload(flow2.flowId, flow2.source, flow1.source)
                },
                [description: "endpoints src1 <-> src2, different src switches, same dst"].tap {
                    List<SwitchPair> swPairs = getSwitchPairs().all().nonNeighbouring().getSwitchPairs()
                            .inject(null) { result, switchPair ->
                                if (result) return result
                                def halfDifferent = getHalfDifferentNotNeighboringSwitchPair(switchPair, "dst")
                                if (halfDifferent) result = [switchPair, halfDifferent]
                                return result
                            }
                    def flow1 = getFlowFactory().getBuilder(swPairs[0])
                            .withSourcePort(getFreePort(swPairs[0].src, [swPairs[1].src])).build()
                    def flow2 = getFlowFactory().getBuilder(swPairs[1], false, flow1.occupiedEndpoints())
                            .withSourcePort(getFreePort(swPairs[1].src, [swPairs[0].src])).build()
                    it.flows = [flow1, flow2]
                    it.firstSwap = new SwapFlowPayload(flow1.flowId, flow2.source, flow1.destination)
                    it.secondSwap = new SwapFlowPayload(flow2.flowId, flow1.source, flow2.destination)
                }
        ]
        flows = data.flows as List<FlowExtended>
        firstSwap = data.firstSwap as SwapFlowPayload
        secondSwap = data.secondSwap as SwapFlowPayload
    }

    @Tags([LOW_PRIORITY])
    def "Able to swap #data.endpointsPart (src1 <-> dst2, dst1 <-> src2) for two flows with the same source and different destination \
switches"() {
        given: "Two flows with the same source and different destination switches"
        FlowExtended flow = data.flow1.create()
        FlowExtended additionalFlow = data.flow2.create()

        when: "Try to swap #endpointsPart for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow.flowId, data.flow1Src, data.flow1Dst),
                new SwapFlowPayload(additionalFlow.flowId, data.flow2Src, data.flow2Dst))

        then: "#endpointsPart.capitalize() are successfully swapped"
        verifyEndpoints(response, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)

        verifyEndpoints(flow, additionalFlow, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        flow.validateAndCollectDiscrepancies().isEmpty()
        additionalFlow.validateAndCollectDiscrepancies().isEmpty()

        and: "Switch validation doesn't show any missing/excess rules and meters"
        List<SwitchId> switches = [flow, additionalFlow].collectMany{it.retrieveAllEntityPaths().getInvolvedSwitches()}.unique()
        Wrappers.wait(RULES_DELETION_TIME + RULES_INSTALLATION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(switches).isEmpty()
        }

        where:
        data << [{
                     it.endpointsPart = "vlans"
                     it.flow1Src = it.flow1.source.jacksonCopy().tap { endpoint -> endpoint.vlanId = it.flow2.destination.vlanId }
                     it.flow1Dst = it.flow1.destination.jacksonCopy().tap { endpoint -> endpoint.vlanId = it.flow2.source.vlanId }
                     it.flow2Src = it.flow2.source.jacksonCopy().tap { endpoint -> endpoint.vlanId = it.flow1.destination.vlanId }
                     it.flow2Dst = it.flow2.destination.jacksonCopy().tap { endpoint -> endpoint.vlanId = it.flow1.source.vlanId }
                 },
                 {
                     it.endpointsPart = "ports"
                     it.flow1Src = it.flow1.source.jacksonCopy().tap { endpoint -> endpoint.portNumber = it.flow2.destination.portNumber }
                     it.flow1Dst = it.flow1.destination.jacksonCopy().tap { endpoint -> endpoint.portNumber = it.flow2.source.portNumber }
                     it.flow2Src = it.flow2.source.jacksonCopy().tap { endpoint -> endpoint.portNumber = it.flow1.destination.portNumber }
                     it.flow2Dst = it.flow2.destination.jacksonCopy().tap { endpoint -> endpoint.portNumber = it.flow1.source.portNumber }
                 },
                 {
                     it.endpointsPart = "switches"
                     it.flow1Src = it.flow1.source.jacksonCopy().tap { endpoint -> endpoint.switchId = it.flow2.destination.switchId }
                     it.flow1Dst = it.flow1.destination.jacksonCopy().tap { endpoint -> endpoint.switchId = it.flow2.source.switchId }
                     it.flow2Src = it.flow2.source.jacksonCopy().tap { endpoint -> endpoint.switchId = it.flow1.destination.switchId }
                     it.flow2Dst = it.flow2.destination.jacksonCopy().tap { endpoint -> endpoint.switchId = it.flow1.source.switchId }
                 }
    ].collect { iterationData ->
            def switchPairs = getSwitchPairs().all().nonNeighbouring().getSwitchPairs().inject(null) { result, switchPair ->
                if (result) return result
                def halfDifferent = getHalfDifferentNotNeighboringSwitchPair(switchPair, "src")
                if (halfDifferent) result = [switchPair, halfDifferent]
                return result
            }
            FlowExtended flow1 = getFirstFlow(switchPairs?.get(0), switchPairs?.get(1))
            FlowExtended flow2 = getSecondFlow(switchPairs?.get(0), switchPairs?.get(1), flow1)
            [switchPairs: switchPairs, flow1: flow1, flow2: flow2].tap(iterationData)
        }
    }

    @IterationTag(tags = [LOW_PRIORITY], iterationNameRegex = /src1/)
    def "Able to swap endpoints (#data.description) for two flows with the same source and different destination \
switches"() {
        given: "Two flows with the same source and different destination switches"
        FlowExtended flow = data.flow1.create()
        FlowExtended additionalFlow = data.flow2.create()

        when: "Try to swap endpoints for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow.flowId, data.flow1Src, data.flow1Dst),
                new SwapFlowPayload(additionalFlow.flowId,data.flow2Src, data.flow2Dst))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)
        verifyEndpoints(flow, additionalFlow, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)


        and: "Flows validation doesn't show any discrepancies"
        flow.validateAndCollectDiscrepancies().isEmpty()
        additionalFlow.validateAndCollectDiscrepancies().isEmpty()

        and: "Switch validation doesn't show any missing/excess rules and meters"
        List<SwitchId> switches = [flow, additionalFlow].collectMany{ it.retrieveAllEntityPaths().getInvolvedSwitches()}.unique()
        Wrappers.wait(RULES_DELETION_TIME + RULES_INSTALLATION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(switches).isEmpty()
        }


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
        FlowExtended flow = flow1.create()
        FlowExtended additionalFlow = flow2.create()

        when: "Try to swap #endpointsPart for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow.flowId, flow1Src, flow1Dst),
                new SwapFlowPayload(additionalFlow.flowId, flow2Src, flow2Dst))

        then: "#endpointsPart.capitalize() are successfully swapped"
        verifyEndpoints(response, flow1Src, flow1Dst, flow2Src, flow2Dst)
        verifyEndpoints(flow, additionalFlow, flow1Src, flow1Dst, flow2Src, flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        flow.validateAndCollectDiscrepancies().isEmpty()
        additionalFlow.validateAndCollectDiscrepancies().isEmpty()

        and: "Switch validation doesn't show any missing/excess rules and meters"
        List<SwitchId> switches = [flow, additionalFlow].collectMany{it.retrieveAllEntityPaths().getInvolvedSwitches()}.unique()
        Wrappers.wait(RULES_DELETION_TIME + RULES_INSTALLATION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(switches).isEmpty()
        }

        where:
        endpointsPart << ["vlans", "ports", "switches"]
        proprtyName << ["vlanId", "portNumber", "switchId"]
        description = "src1 <-> dst2, dst1 <-> src2"
        swPairs = switchPairs.all().nonNeighbouring().getSwitchPairs().inject(null) { result, switchPair ->
            if (result) return result
            def halfDifferent = getHalfDifferentNotNeighboringSwitchPair(switchPair, "dst")
            if (halfDifferent) result = [switchPair, halfDifferent]
            return result
        }
        flow1 = getFirstFlow(swPairs?.get(0), swPairs?.get(1))
        flow2 = getSecondFlow(swPairs?.get(0), swPairs?.get(1), flow1)
        flow1Src = flow1.source.jacksonCopy().tap { endpoint -> endpoint."$proprtyName" = flow2.destination."$proprtyName" }
        flow1Dst = flow1.destination.jacksonCopy().tap { endpoint -> endpoint."$proprtyName" = flow2.source."$proprtyName" }
        flow2Src = flow2.source.jacksonCopy().tap { endpoint -> endpoint."$proprtyName" = flow1.destination."$proprtyName" }
        flow2Dst = flow2.destination.jacksonCopy().tap { endpoint -> endpoint."$proprtyName" = flow1.source."$proprtyName" }

    }

    @IterationTag(tags = [LOW_PRIORITY], iterationNameRegex = /dst1/)
    def "Able to swap #endpointsPart (#description) for two flows with different source and destination switches"() {
        given: "Two flows with different source and destination switches"
        FlowExtended flow = flow1.create()
        FlowExtended additionalFlow = flow2.create()

        when: "Try to swap #endpointsPart for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow.flowId, flow1Src, flow1Dst),
                new SwapFlowPayload(additionalFlow.flowId, flow2Src, flow2Dst))

        then: "#endpointsPart.capitalize() are successfully swapped"
        verifyEndpoints(response, flow1Src, flow1Dst, flow2Src, flow2Dst)
        verifyEndpoints(flow, additionalFlow, flow1Src, flow1Dst, flow2Src, flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        flow.validateAndCollectDiscrepancies().isEmpty()
        additionalFlow.validateAndCollectDiscrepancies().isEmpty()

        and: "Switch validation doesn't show any missing/excess rules and meters"
        List<SwitchId> switches = [flow, additionalFlow].collectMany{it.retrieveAllEntityPaths().getInvolvedSwitches()}.unique()
        Wrappers.wait(RULES_DELETION_TIME + RULES_INSTALLATION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(switches).isEmpty()
        }

        where:
        endpointsPart << ["vlans", "ports", "switches"]
        proprtyName << ["vlanId", "portNumber", "switchId"]
        description = "src1 <-> dst2, dst1 <-> src2"
        flow1SwitchPair = switchPairs.all().nonNeighbouring().random()
        flow2SwitchPair = getDifferentNotNeighboringSwitchPair(flow1SwitchPair)
        flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
        flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)

        flow1Src = flow1.source.jacksonCopy().tap { endpoint -> endpoint."$proprtyName" = flow2.destination."$proprtyName" }
        flow1Dst = flow1.destination.jacksonCopy().tap { endpoint -> endpoint."$proprtyName" = flow2.source."$proprtyName" }
        flow2Src = flow2.source.jacksonCopy().tap { endpoint -> endpoint."$proprtyName" = flow1.destination."$proprtyName" }
        flow2Dst = flow2.destination.jacksonCopy().tap { endpoint -> endpoint."$proprtyName" = flow1.source."$proprtyName" }
    }

    @Tags(LOW_PRIORITY)
    def "Able to swap endpoints (#data.description) for two flows with different source and destination switches"() {
        given: "Two flows with different source and destination switches"
        FlowExtended flow = flow1.create()
        FlowExtended additionalFlow = flow2.create()

        when: "Try to swap endpoints for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow.flowId, data.flow1Src, data.flow1Dst),
                new SwapFlowPayload(additionalFlow.flowId, data.flow2Src, data.flow2Dst))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)
        verifyEndpoints(flow, additionalFlow, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        flow.validateAndCollectDiscrepancies().isEmpty()
        additionalFlow.validateAndCollectDiscrepancies().isEmpty()

        and: "Switch validation doesn't show any missing/excess rules and meters"
        List<SwitchId> switches = [flow, additionalFlow].collectMany{it.retrieveAllEntityPaths().getInvolvedSwitches()}.unique()
        Wrappers.wait(RULES_DELETION_TIME + RULES_INSTALLATION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(switches).isEmpty()
        }

        where:
        data << [{
                     it.description = "src1 <-> src2"
                     it.flow2 = getFlowFactory().getBuilder(it.flow2SwitchPair)
                             .withSourcePort(it.flow1.source.portNumber)
                             .withSourceVlan(it.flow1.source.vlanId).build()

                     it.flow1Src = it.flow2.source
                     it.flow1Dst = it.flow1.destination
                     it.flow2Src = it.flow1.source
                     it.flow2Dst = it.flow2.destination
                 },
                 {
                     it.description = "dst1 <-> dst2"
                     it.flow2 = getFlowFactory().getBuilder(it.flow2SwitchPair)
                             .withDestinationPort(it.flow1.destination.portNumber)
                             .withDestinationVlan(it.flow1.destination.vlanId).build()

                     it.flow1Src = it.flow1.source
                     it.flow1Dst = it.flow2.destination
                     it.flow2Src = it.flow2.source
                     it.flow2Dst = it.flow1.destination
                 },
                 {
                     it.description = "src1 <-> dst2"
                     it.flow2 = getFlowFactory().getBuilder(it.flow2SwitchPair)
                             .withDestinationPort( it.flow1.source.portNumber)
                             .withDestinationVlan(it.flow1.source.vlanId).build()

                     it.flow1Src = it.flow2.destination
                     it.flow1Dst = it.flow1.destination
                     it.flow2Src = it.flow2.source
                     it.flow2Dst = it.flow1.source
                 },
                 {
                     it.description = "dst1 <-> src2"
                     it.flow2 = getFlowFactory().getBuilder(it.flow2SwitchPair).withSourcePort(it.flow1.destination.portNumber)
                             .withSourceVlan(it.flow1.destination.vlanId).build()

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
        flow1 = data.flow1 as FlowExtended
        flow2 = data.flow2 as FlowExtended
    }

    def "Unable to swap endpoints for existing flow and non-existing flow"() {
        given: "An active flow"
        def switchPair = switchPairs.all().neighbouring().random()
        def flow1 = flowFactory.getRandom(switchPair)
        def flow2 = flowFactory.getBuilder(switchPair).build()

        when: "Try to swap endpoints for existing flow and non-existing flow"
        northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.flowId, flow1.source, flow2.destination),
                new SwapFlowPayload(flow2.flowId, flow2.source, flow1.destination))

        then: "An error is received (404 code)"
        def exc = thrown(HttpClientErrorException)
        new FlowEndpointsNotSwappedExpectedError(HttpStatus.NOT_FOUND, ~/Flow ${flow2.flowId} not found/).matches(exc)
    }

    @Tags(LOW_PRIORITY)
    def "Unable to swap #data.endpointsPart for two flows: #data.description"() {
        given: "Three active flows"
        flow1.create()
        flow2.create()
        flow3.create()

        when: "Try to swap #endpointsPart for two flows"
        northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.flowId, data.flow1Src, data.flow1Dst),
                new SwapFlowPayload(flow2.flowId, data.flow2Src, data.flow2Dst))

        then: "An error is received (409 code)"
        def exc = thrown(HttpClientErrorException)
        new FlowEndpointsNotSwappedExpectedError(HttpStatus.CONFLICT,
                ~/Requested flow '${flow1.flowId}' conflicts with existing flow '${flow3.flowId}'./).matches(exc)

        where:
        data << [{
                     description = "the same ports and vlans on src switch"
                     endpointsPart = "ports and vlans"
                     flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
                     flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)
                     flow3 = getFlowFactory().getBuilder(flow1SwitchPair, false, flow1.occupiedEndpoints())
                             .withSourceSwitch(flow1.source.switchId)
                             .withSourcePort(flow2.destination.portNumber)
                             .withSourceVlan(flow2.destination.vlanId).build()

                     flow1Src = flow1.source.jacksonCopy().tap { endpoint ->
                         endpoint.portNumber = flow2.destination.portNumber
                         endpoint.vlanId = flow2.destination.vlanId
                     }
                     flow1Dst = flow1.destination
                     flow2Src = flow2.source
                     flow2Dst = flow2.destination.jacksonCopy().tap { endpoint ->
                         endpoint.portNumber = flow1.source.portNumber
                         endpoint.vlanId = flow1.source.vlanId
                     }
                 },
                 {
                     description = "the same ports and vlans on dst switch"
                     endpointsPart = "ports and vlans"
                     flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
                     flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)
                     flow3 = getFlowFactory().getBuilder(flow1SwitchPair, false, flow1.occupiedEndpoints())
                             .withDestinationSwitch(flow1.destination.switchId)
                             .withDestinationPort(flow2.source.portNumber)
                             .withDestinationVlan(flow2.source.vlanId).build()

                     flow1Src = flow1.source
                     flow1Dst = flow1.destination.jacksonCopy().tap { endpoint ->
                         endpoint.portNumber = flow2.source.portNumber
                         endpoint.vlanId = flow2.source.vlanId
                     }
                     flow2Src = flow2.source.jacksonCopy().tap { endpoint ->
                         endpoint.portNumber = flow1.destination.portNumber
                         endpoint.vlanId = flow1.destination.vlanId
                     }
                     flow2Dst = flow2.destination
                 },
                 {
                     description = "the same vlans on the same port on src switch"
                     endpointsPart = "vlans"
                     flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
                     flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)
                     flow3 = getFlowFactory().getBuilder(flow1SwitchPair, false, flow1.occupiedEndpoints())
                             .withSourceSwitch(flow1.source.switchId)
                             .withSourcePort(flow1.source.portNumber)
                             .withSourceVlan(flow2.destination.vlanId).build()

                     flow1Src = flow1.source.jacksonCopy().tap { endpoint ->
                         endpoint.vlanId = flow2.destination.vlanId
                     }
                     flow1Dst = flow1.destination
                     flow2Src = flow2.source
                     flow2Dst = flow2.destination.jacksonCopy().tap { endpoint ->
                         endpoint.vlanId = flow1.source.vlanId
                     }
                 },
                 {
                     description = "the same vlans on the same port on dst switch"
                     endpointsPart = "vlans"
                     flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
                     flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)
                     flow3 = getFlowFactory().getBuilder(flow1SwitchPair, false, flow1.occupiedEndpoints())
                             .withDestinationSwitch(flow1.destination.switchId)
                             .withDestinationPort(flow1.destination.portNumber)
                             .withDestinationVlan(flow2.source.vlanId).build()
                     flow1Src = flow1.source
                     flow1Dst = flow1.destination.jacksonCopy().tap { endpoint ->
                         endpoint.vlanId = flow2.source.vlanId
                     }
                     flow2Src = flow2.source.jacksonCopy().tap { endpoint ->
                         endpoint.vlanId = flow1.destination.vlanId
                     }
                     flow2Dst = flow2.destination
                 },
                 {
                     description = "no vlans, both flows are on the same port on src switch"
                     endpointsPart = "ports"
                     flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair, true)
                     flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1, true)
                     flow3 = getFlowFactory().getBuilder(flow1SwitchPair, false, flow1.occupiedEndpoints())
                             .withSourceSwitch(flow1.source.switchId)
                             .withSourcePort(flow2.destination.portNumber)
                             .withSourceVlan(flow1.source.vlanId).build()
                     flow1Src =  flow1.source.jacksonCopy().tap { endpoint ->
                         endpoint.portNumber = flow2.destination.portNumber
                     }
                     flow1Dst = flow1.destination
                     flow2Src = flow2.source
                     flow2Dst = flow2.destination.jacksonCopy().tap { endpoint ->
                         endpoint.portNumber = flow1.source.portNumber
                     }
                 },
                 {
                     description = "no vlans, both flows are on the same port on dst switch"
                     endpointsPart = "ports"
                     flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair, true)
                     flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1, true)
                     flow3 = getFlowFactory().getBuilder(flow1SwitchPair, false, flow1.occupiedEndpoints())
                             .withDestinationSwitch(flow1.destination.switchId)
                             .withDestinationPort(flow2.source.portNumber)
                             .withDestinationVlan(flow1.destination.vlanId).build()
                     flow1Src = flow1.source
                     flow1Dst = flow1.destination.jacksonCopy().tap { endpoint ->
                         endpoint.portNumber = flow2.source.portNumber
                     }
                     flow2Src =  flow2.source.jacksonCopy().tap { endpoint ->
                         endpoint.portNumber = flow1.destination.portNumber
                     }
                     flow2Dst = flow2.destination
                 }].collect { iterationData ->
            def flow1SwitchPair = switchPairs.all().nonNeighbouring().random()
            def flow2SwitchPair = getDifferentNotNeighboringSwitchPair(flow1SwitchPair)
            def flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
            [flow1SwitchPair: flow1SwitchPair, flow2SwitchPair: flow2SwitchPair, flow1: flow1].tap(iterationData)
        }
        flow1 = data.flow1 as FlowExtended
        flow2 = data.flow2 as FlowExtended
        flow3 = data.flow3 as FlowExtended
    }

    //start from here tomorrow
    @IterationTag(tags = [LOW_PRIORITY], iterationNameRegex = /the same src endpoint for flows/)
    def "Unable to swap endpoints for two flows (#data.description)"() {
        given: "Two active flows"
        flow1.create()
        flow2.create()

        when: "Try to swap endpoints for two flows"
        northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.flowId, data.flow1Src, data.flow1Dst),
                new SwapFlowPayload(flow2.flowId, data.flow2Src, data.flow2Dst))

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        new FlowEndpointsNotSwappedExpectedError(HttpStatus.BAD_REQUEST,
                ~/New requested endpoint for '${flow2.flowId}' conflicts with existing endpoint for '${flow1.flowId}'/).matches(exc)
        where:
        data << [{
                     description = "the same src endpoint for flows"
                     flow1Src = flow1.source
                     flow1Dst = flow1.destination.jacksonCopy().tap { endpoint -> endpoint.portNumber = flow2.destination.portNumber }
                     flow2Src = flow1.source
                     flow2Dst = flow2.destination.jacksonCopy().tap { endpoint -> endpoint.portNumber = flow1.destination.portNumber }
                 },
                 {
                     description = "the same dst endpoint for flows"
                     flow1Src = flow1.source.jacksonCopy().tap { endpoint -> endpoint.portNumber = flow2.source.portNumber }
                     flow1Dst = flow1.destination
                     flow2Src = flow2.source.jacksonCopy().tap { endpoint -> endpoint.portNumber = flow1.source.portNumber }
                     flow2Dst = flow1.destination
                 }].collect { iterationData ->
            def flow1SwitchPair = switchPairs.all().nonNeighbouring().random()
            def flow2SwitchPair = getDifferentNotNeighboringSwitchPair(flow1SwitchPair)
            def flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair)
            def flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1)
            [flow1SwitchPair: flow1SwitchPair, flow2SwitchPair: flow2SwitchPair, flow1: flow1, flow2: flow2].tap(iterationData)
        }
        flow1 = data.flow1 as FlowExtended
        flow2 = data.flow2 as FlowExtended
    }

    def "Unable to swap ports for two flows (port is occupied by ISL on src switch)"() {
        given: "Two active flows"
        def islPort
        def swPair = switchPairs.all().getSwitchPairs().find {
            def busyPorts = topology.getBusyPortsForSwitch(it.src)
            islPort = topology.getAllowedPortsForSwitch(it.dst).find { it in busyPorts }
        }
        assert islPort
        def flow1 = flowFactory.getRandom(swPair)
        def flow2 = flowFactory.getBuilder(swPair, false, flow1.occupiedEndpoints())
                .withDestinationPort(islPort).build()
                .create()

        when: "Try to swap ports for two flows"
        northbound.swapFlowEndpoint(
                new SwapFlowPayload(
                        flow1.flowId,
                        flow1.source.jacksonCopy().tap { it.portNumber = flow2.destination.portNumber },
                        flow1.destination),
                new SwapFlowPayload(
                        flow2.flowId,
                        flow2.source,
                        flow1.destination.jacksonCopy().tap { it.portNumber = flow2.source.portNumber }))

        then: "An error is received (400 code)"
        def exc = thrown(HttpClientErrorException)
        new FlowEndpointsNotSwappedExpectedError(HttpStatus.BAD_REQUEST,
                ~/The port $islPort on the switch \'${swPair.src.dpId}\' is occupied by an ISL \(source endpoint collision\)./).matches(exc)
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
        def flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair).create()
        def flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1).create()

        and: "Update the first flow so that it consumes all bandwidth on the link"
        def flow1Isl = flow1.retrieveAllEntityPaths().flowPath.getInvolvedIsls().first()
        def flow1IslMaxBw = islUtils.getIslInfo(flow1Isl).get().maxBandwidth

        flow1.update(flow1.tap { it.maximumBandwidth = flow1IslMaxBw })

        and: "Break all alternative paths for the first flow"
        def broughtDownIsls = topology.getRelatedIsls(flow1SwitchPair.src) - flow1Isl
        islHelper.breakIsls(broughtDownIsls)

        and: "Update max bandwidth for the second flow's link so that it is equal to max bandwidth of the first flow"
        def flow2Isl = flow2.retrieveAllEntityPaths().flowPath.getInvolvedIsls().first()
        islHelper.updateLinkMaxBandwidthUsingApi(flow2Isl, flow1IslMaxBw)

        and: "Break all alternative paths for the second flow"
        def flow2BroughtDownIsls = topology.getRelatedIsls(flow2SwitchPair.src) - flow2Isl - broughtDownIsls
        islHelper.breakIsls(flow2BroughtDownIsls)

        when: "Try to swap endpoints for two flows"
        def flow1Src = flow2.source
        def flow1Dst = flow1.destination.jacksonCopy().tap { it.portNumber = flow2.destination.portNumber}
        def flow2Src = flow1.source
        def flow2Dst = flow2.destination.jacksonCopy().tap { it.portNumber = flow1.destination.portNumber}
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.flowId, flow1Src, flow1Dst),
                new SwapFlowPayload(flow2.flowId, flow2Src, flow2Dst))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, flow1Src, flow1Dst, flow2Src, flow2Dst)
        verifyEndpoints(flow1, flow2, flow1Src, flow1Dst, flow2Src, flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        flow1.validateAndCollectDiscrepancies().isEmpty()
        flow2.validateAndCollectDiscrepancies().isEmpty()

        and: "Switch validation doesn't show any missing/excess rules and meters"
        List<SwitchId> switches = [flow1, flow2].collectMany{ it.retrieveAllEntityPaths().getInvolvedSwitches()}.unique()
        Wrappers.wait(RULES_DELETION_TIME + RULES_INSTALLATION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(switches).isEmpty()
        }
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
        def flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair).create()
        def flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1).create()

        and: "Update the first flow so that it consumes all bandwidth on the link"
        def flow1Isl = flow1.retrieveAllEntityPaths().flowPath.getInvolvedIsls().first()
        def flow1IslMaxBw = islUtils.getIslInfo(flow1Isl).get().maxBandwidth

        flow1.update(flow1.tap { it.maximumBandwidth = flow1IslMaxBw })

        and: "Break all alternative paths for the first flow"
        def broughtDownIsls = topology.getRelatedIsls(flow1SwitchPair.src) - flow1Isl
        islHelper.breakIsls(broughtDownIsls)

        and: "Update max bandwidth for the second flow's link so that it is not enough bandwidth for the first flow"
        def flow2Isl = flow2.retrieveAllEntityPaths().flowPath.getInvolvedIsls().first()
        islHelper.updateLinkMaxBandwidthUsingApi(flow2Isl, flow1IslMaxBw - 1)

        and: "Break all alternative paths for the second flow"
        def flow2BroughtDownIsls = topology.getRelatedIsls(flow2SwitchPair.src) - flow2Isl - broughtDownIsls
        islHelper.breakIsls(flow2BroughtDownIsls)

        when: "Try to swap endpoints for two flows"
        def flow1Src = flow2.source
        def flow1Dst = flow1.destination.jacksonCopy().tap { it.portNumber = flow2.destination.portNumber }
        def flow2Src = flow1.source
        def flow2Dst = flow2.destination.jacksonCopy().tap { it.portNumber = flow1.destination.portNumber}
        northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.flowId, flow1Src, flow1Dst),
                new SwapFlowPayload(flow2.flowId, flow2Src, flow2Dst))

        then: "An error is received (500 code)"
        def exc = thrown(HttpServerErrorException)
        new FlowEndpointsNotSwappedExpectedError(~/Not enough bandwidth or no path found/).matches(exc)
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
        def flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair).tap {
            it.ignoreBandwidth = true
        }.create()

        def flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1).create()

        and: "Update the first flow so that it consumes all bandwidth on the link"
        def flow1Isl = flow1.retrieveAllEntityPaths().flowPath.getInvolvedIsls().first()
        def flow1IslMaxBw = islUtils.getIslInfo(flow1Isl).get().maxBandwidth

        flow1.update(flow1.tap { it.maximumBandwidth = flow1IslMaxBw })

        and: "Break all alternative paths for the first flow"
        def broughtDownIsls = topology.getRelatedIsls(flow1SwitchPair.src) - flow1Isl
        islHelper.breakIsls(broughtDownIsls)

        and: "Update max bandwidth for the second flow's link so that it is not enough bandwidth for the first flow"
        def flow2Isl = flow2.retrieveAllEntityPaths().flowPath.getInvolvedIsls().first()
        islHelper.updateLinkMaxBandwidthUsingApi(flow2Isl, flow1IslMaxBw - 1)

        and: "Break all alternative paths for the second flow"
        def flow2BroughtDownIsls = topology.getRelatedIsls(flow2SwitchPair.src) - flow2Isl - broughtDownIsls
        islHelper.breakIsls(flow2BroughtDownIsls)

        when: "Try to swap endpoints for two flows"
        def flow1Src = flow2.source
        def flow1Dst = flow1.destination.jacksonCopy().tap { it.portNumber = flow2.destination.portNumber }
        def flow2Src = flow1.source
        def flow2Dst = flow2.destination.jacksonCopy().tap { it.portNumber = flow1.destination.portNumber }
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.flowId, flow1Src, flow1Dst),
                new SwapFlowPayload(flow2.flowId, flow2Src, flow2Dst))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, flow1Src, flow1Dst, flow2Src, flow2Dst)
        verifyEndpoints(flow1, flow2, flow1Src, flow1Dst, flow2Src, flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        flow1.validateAndCollectDiscrepancies().isEmpty()
        flow2.validateAndCollectDiscrepancies().isEmpty()

        and: "Switch validation doesn't show any missing/excess rules and meters"
        List<SwitchId> switches = [flow1, flow2].collectMany{ it.retrieveAllEntityPaths().getInvolvedSwitches()}.unique()
        Wrappers.wait(RULES_DELETION_TIME + RULES_INSTALLATION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(switches).isEmpty()
        }
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    @Ignore("https://github.com/telstra/open-kilda/issues/5635")
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
        def flow1 = getFirstFlow(flow1SwitchPair, flow2SwitchPair).create()
        def flow2 = getSecondFlow(flow1SwitchPair, flow2SwitchPair, flow1).create()

        List<SwitchId> involvedSwIds = [flow1, flow2].collectMany { it.retrieveAllEntityPaths().getInvolvedSwitches() }.unique()

        and: "Break all paths for the first flow"
        def broughtDownIsls = topology.getRelatedIsls(flow1SwitchPair.src)
        islHelper.breakIsls(broughtDownIsls)

        when: "Try to swap endpoints for two flows"
        northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.flowId, flow2.source, flow1.destination),
                new SwapFlowPayload(flow2.flowId, flow1.source, flow2.destination))

        then: "An error is received (500 code)"
        def exc = thrown(HttpServerErrorException)
        new FlowEndpointsNotSwappedExpectedError(~/Not enough bandwidth or no path found/).matches(exc)

        when: "Get actual data of flow1 and flow2"
        def actualFlow1Details = flow1.retrieveDetailsV1()
        def actualFlow2Details = flow2.retrieveDetailsV1()

        then: "Actual flow1, flow2 sources are different"
        assert actualFlow1Details.source != actualFlow2Details.source

        and: "All involved switches are valid"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwIds).isEmpty()
    }

    @Tags(LOW_PRIORITY)
    def "Able to swap endpoints (#data.description) for two protected flows"() {
        given: "Two protected flows with different source and destination switches"
        flow1.create()
        flow2.create()

        when: "Try to swap endpoints for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.flowId, data.flow1Src, data.flow1Dst),
                new SwapFlowPayload(flow2.flowId, data.flow2Src, data.flow2Dst))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)
        verifyEndpoints(flow1, flow2, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        flow1.validateAndCollectDiscrepancies().isEmpty()
        flow2.validateAndCollectDiscrepancies().isEmpty()

        and: "Switch validation doesn't show any missing/excess rules and meters"
        List<SwitchId> switches = [flow1, flow2].collectMany{ it.retrieveAllEntityPaths().getInvolvedSwitches()}.unique()
        Wrappers.wait(RULES_DELETION_TIME + RULES_INSTALLATION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(switches).isEmpty()
        }

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
            def flow1 = flowFactory.getBuilder(flow1SwitchPair)
                    .withProtectedPath(true).build()
            def flow2 = flowFactory.getBuilder(flow2SwitchPair, false, flow1.occupiedEndpoints())
                    .withProtectedPath(true)
                    .withSourcePort(getFreePort(flow2SwitchPair.src, [flow1SwitchPair.src, flow1SwitchPair.dst]))
                    .withDestinationPort(getFreePort(flow2SwitchPair.dst, [flow1SwitchPair.src, flow1SwitchPair.dst])).build()
            [flow1SwitchPair: flow1SwitchPair, flow2SwitchPair: flow2SwitchPair, flow1: flow1, flow2: flow2].tap(iterationData)
        }
        flow1 = data.flow1 as FlowExtended
        flow2 = data.flow2 as FlowExtended
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
        def flow1 = flowFactory.getBuilder(flow1SwitchPair)
                .withProtectedPath(true).build()
                .create()
        def flow2 = flowFactory.getBuilder(flow2SwitchPair)
                .withProtectedPath(true).build()
                .create()

        when: "Try to swap endpoints for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.flowId, flow2.source, flow1.destination),
                new SwapFlowPayload(flow2.flowId, flow1.source, flow2.destination))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, flow2.source, flow1.destination, flow1.source, flow2.destination)
        verifyEndpoints(flow1, flow2, flow2.source, flow1.destination, flow1.source, flow2.destination)
        def flow1AfterSwapEndpoints = flow1.retrieveDetails()

        and: "Flows validation doesn't show any discrepancies"
        flow1.validateAndCollectDiscrepancies().isEmpty()
        flow2.validateAndCollectDiscrepancies().isEmpty()

        and: "Switch validation doesn't show any missing/excess rules and meters"
        List<SwitchId> switches = [flow1, flow2].collectMany{ it.retrieveAllEntityPaths().getInvolvedSwitches()}.unique()
        Wrappers.wait(RULES_DELETION_TIME + RULES_INSTALLATION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(switches).isEmpty()
        }

        and: "The first flow allows traffic on the main path"
        def traffExam = traffExamProvider.get()
        def exam = flow1AfterSwapEndpoints.traffExam(traffExam, 0, 5)
        [exam.forward, exam.reverse].each { direction ->
            def resources = traffExam.startExam(direction)
            direction.setResources(resources)
            assert traffExam.waitExam(direction).hasTraffic()
        }

        and: "The first flow allows traffic on the protected path"
        flow1.swapFlowPath()
        Wrappers.wait(WAIT_OFFSET) { assert flow1AfterSwapEndpoints.retrieveFlowStatus().status == FlowState.UP }
        flow1.validateAndCollectDiscrepancies().isEmpty()

        def flow1AfterSwapPath = flow1.retrieveDetails()
        def newExam = flow1AfterSwapPath.traffExam(traffExam, 0, 5)
        [newExam.forward, newExam.reverse].each { direction ->
            def resources = traffExam.startExam(direction)
            direction.setResources(resources)
            assert traffExam.waitExam(direction).hasTraffic()
        }
    }

    @Tags(LOW_PRIORITY)
    def "Able to swap endpoints (#data.description) for two vxlan flows with the same source and destination switches"() {
        given: "Two flows with the same source and destination switches"
        flow1.tap { it.encapsulationType = FlowEncapsulationType.VXLAN }.create()
        flow2.tap { it.encapsulationType = FlowEncapsulationType.VXLAN }.create()

        when: "Try to swap endpoints for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.flowId, data.flow1Src, data.flow1Dst),
                new SwapFlowPayload(flow2.flowId, data.flow2Src, data.flow2Dst))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)
        verifyEndpoints(flow1, flow2, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        flow1.validateAndCollectDiscrepancies().isEmpty()
        flow2.validateAndCollectDiscrepancies().isEmpty()

        and: "Switch validation doesn't show any missing/excess rules and meters"
        List<SwitchId> switches = [flow1, flow2].collectMany{ it.retrieveAllEntityPaths().getInvolvedSwitches()}.unique()
        Wrappers.wait(RULES_DELETION_TIME + RULES_INSTALLATION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(switches).isEmpty()
        }

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
        flow1 = data.flow1 as FlowExtended
        flow2 = data.flow2 as FlowExtended
    }

    def "Able to swap endpoints (#data.description) for two qinq flows with the same source and destination switches"() {
        given: "Two flows with the same source and destination switches"
        flow1.source.innerVlanId = 300
        flow1.destination.innerVlanId = 400
        flow2.source.innerVlanId = 500
        flow2.destination.innerVlanId = 600
        flow1.createV1()
        flow2.createV1()

        when: "Try to swap endpoints for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.flowId, data.flow1Src, data.flow1Dst),
                new SwapFlowPayload(flow2.flowId, data.flow2Src, data.flow2Dst))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)
        verifyEndpoints(flow1, flow2, data.flow1Src, data.flow1Dst, data.flow2Src, data.flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        flow1.validateAndCollectDiscrepancies().isEmpty()
        flow2.validateAndCollectDiscrepancies().isEmpty()

        and: "Switch validation doesn't show any missing/excess rules and meters"
        List<SwitchId> switches = [flow1, flow2].collectMany{ it.retrieveAllEntityPaths().getInvolvedSwitches()}.unique()
        Wrappers.wait(RULES_DELETION_TIME + RULES_INSTALLATION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(switches).isEmpty()
        }
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
        flow1 = data.flow1 as FlowExtended
        flow2 = data.flow2 as FlowExtended
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
        def flow1 = flowFactory.getBuilder(swPair1)
                .withSourcePort(getFreePort(swPair1.src, [swPair2.src])).build()
                .create()

        def flow2 = flowFactory.getBuilder(swPair2)
                .withSourcePort(getFreePort(swPair2.src, [swPair1.src])).build()
                .create()

        when: "Try to swap flow src endoints, but flow1 src switch does not respond"
        switchHelper.knockoutSwitch(swPair1.src, RW)
        database.setSwitchStatus(swPair1.src.dpId, SwitchStatus.ACTIVE)
        northbound.swapFlowEndpoint(new SwapFlowPayload(flow1.flowId, flow2.source, flow1.destination),
                new SwapFlowPayload(flow2.flowId, flow1.source, flow2.destination))

        then: "Receive error response"
        def exc = thrown(HttpServerErrorException)
        new FlowEndpointsNotSwappedExpectedError(~/Reverted flows: \[${flow2.flowId}, ${flow1.flowId}\]/).matches(exc)

        and: "First flow is reverted to Down"
        Wrappers.wait(PATH_INSTALLATION_TIME + WAIT_OFFSET * 2) { // sometimes it takes more time on jenkins
            assert flow1.retrieveFlowStatus().status == FlowState.DOWN
            def flowHistory = flow1.retrieveFlowHistory().getEntriesByType(REROUTE)
            /* '||' due to instability on jenkins
                * locally: it always retry to reroute (reason of failed reroute: 'No bandwidth or path...')
                * jenkins: - reroute(ISL_1 become INACTIVE) + retry or reroute(ISL_1) + reroute(ISL_2);
                *          - or one reroute only. (reason of failed reroute: 'Failed to allocate flow resources...')
                */
            assert flowHistory.findAll {
                it.action == REROUTE.value && it.payload.last().action == REROUTE_FAILED.payloadLastAction
            }.size() > 1 || flowHistory.find {
                it.action == REROUTE.value && it.payload.last().action == REROUTE_FAILED.payloadLastAction &&
                        it.payload.last().details.contains("Failed to allocate flow resources.")
            }
        }
        with(flow1.retrieveDetails()) {
            source == flow1.source
            destination == flow1.destination
        }

        and: "Second flow is reverted to UP"
        Wrappers.wait(PATH_INSTALLATION_TIME) {
            assert flow2.retrieveFlowStatus().status == FlowState.UP
        }
        with(flow2.retrieveDetails()) {
            source == flow2.source
            destination == flow2.destination
        }

        when: "Delete both flows"
        List<SwitchId> switches = [flow1, flow2].collectMany{ it.retrieveAllEntityPaths().getInvolvedSwitches()}.unique()
                .findAll { it != swPair1.src.dpId }
        [flow1, flow2].collect {flow -> flow.delete() }

        then: "Related switches have no rule anomalies"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(switches).isEmpty()
    }

    def "Able to swap endpoints for a flow with flowLoop"() {
        setup: "Create two flows with the same src and different dst switches"
        def flow1SwitchPair = switchPairs.all().neighbouring().withTraffgensOnBothEnds().random().getReversed()
        def flow2SwitchPair = switchPairs.all().neighbouring()
                .includeSourceSwitch(flow1SwitchPair.getSrc())
                .excludeDestinationSwitches([flow1SwitchPair.getDst()])
                .random()
        def flow1 = flowFactory.getBuilder(flow1SwitchPair).build()
                .create()
        def flow2 = flowFactory.getBuilder(flow2SwitchPair, true, flow1.occupiedEndpoints()).build()
                .create()

        and: "FlowLoop is created for the second flow on the dst switch"
        flow2.createFlowLoop(flow2SwitchPair.dst.dpId)
        flow2.waitForBeingInState(FlowState.UP)
        assert flow2.retrieveDetails().loopSwitchId == flow2SwitchPair.dst.dpId

        when: "Try to swap dst endpoints for two flows"
        def flow1Dst = flow2.destination
        def flow1Src = flow1.source
        def flow2Dst = flow1.destination
        def flow2Src = flow2.source
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.flowId, flow1Src, flow1Dst),
                new SwapFlowPayload(flow2.flowId, flow2Src, flow2Dst))

        then: "Endpoints are successfully swapped"
        verifyEndpoints(response, flow1Src, flow1Dst, flow2Src, flow2Dst)
        verifyEndpoints(flow1, flow2, flow1Src, flow1Dst, flow2Src, flow2Dst)

        and: "Flows validation doesn't show any discrepancies"
        flow1.validateAndCollectDiscrepancies().isEmpty()
        flow2.validateAndCollectDiscrepancies().isEmpty()

        and: "FlowLoop is still created for the second flow but on the new dst switch"
        assert flow2.retrieveDetails().loopSwitchId == flow1SwitchPair.dst.dpId

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
        List<SwitchId> switches = [flow1SwitchPair.src.dpId, flow1SwitchPair.dst.dpId,
                                   flow2SwitchPair.src.dpId, flow2SwitchPair.dst.dpId].unique()
        Wrappers.wait(RULES_DELETION_TIME + RULES_INSTALLATION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(switches).isEmpty()
        }


        when: "Send traffic via flow2"
        def traffExam = traffExamProvider.get()
        def flowAfterSwap = flow2.retrieveDetails()
        def exam = flowAfterSwap.traffExam(traffExam, 1000, 5)

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

    void verifyEndpoints(SwapFlowEndpointPayload response, FlowEndpointV2 flow1SrcExpected, FlowEndpointV2 flow1DstExpected,
                         FlowEndpointV2 flow2SrcExpected, FlowEndpointV2 flow2DstExpected) {
        SoftAssertions assertions = new SoftAssertions()
        assertions.checkSucceeds { assert response.firstFlow.source == flow1SrcExpected }
        assertions.checkSucceeds { assert response.firstFlow.destination == flow1DstExpected }
        assertions.checkSucceeds { assert response.secondFlow.source == flow2SrcExpected }
        assertions.checkSucceeds { assert response.secondFlow.destination == flow2DstExpected }
        assertions.verify()
    }

    void verifyEndpoints(FlowExtended flow, FlowExtended additionalFlow, FlowEndpointV2 flow1SrcExpected, FlowEndpointV2 flow1DstExpected,
                         FlowEndpointV2 flow2SrcExpected, FlowEndpointV2 flow2DstExpected) {
        def flow1Updated = flow.retrieveDetailsV1()
        def flow2Updated = additionalFlow.retrieveDetailsV1()
        SoftAssertions assertions = new SoftAssertions()
        assertions.checkSucceeds { assert flow1Updated.source == flow1SrcExpected }
        assertions.checkSucceeds { assert flow1Updated.destination == flow1DstExpected }
        assertions.checkSucceeds { assert flow2Updated.source == flow2SrcExpected }
        assertions.checkSucceeds { assert flow2Updated.destination == flow2DstExpected }
        assertions.verify()
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
    def getFreeVlan(SwitchId swId, List<SwitchPortVlan> busyEndpoints = []) {
        def vlans = KILDA_ALLOWED_VLANS - busyEndpoints.findAll{ it.sw == swId }.vlan
        return vlans.shuffled().first()
    }

    /**
     * Get a FlowExtended instance for the flow. The instance is built considering ISL ports on source and
     * destination switches of the first and second switch pairs. So no ISL port conflicts should appear while creating
     * the flow and swapping flow endpoints.
     *
     * @param firstFlowSwitchPair Switch pair for the first flow
     * @param secondFlowSwitchPair Switch pair for the second flow
     * @return a FlowExtended instance for further creation
     */
    def getFirstFlow(SwitchPair firstFlowSwitchPair, SwitchPair secondFlowSwitchPair, Boolean noVlans = false) {
        assumeTrue(firstFlowSwitchPair && secondFlowSwitchPair, "Required conditions for switch-pairs for this test are not met")
        def firstFlow = flowFactory.getBuilder(firstFlowSwitchPair).tap {
            if (noVlans) {
                it.withSourceVlan(0)
                it.withDestinationVlan(0)
            }
        }.build()
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

        return firstFlow
    }

    private static <T> T pickRandom(List<T> c) {
        def r = new Random()
        c[r.nextInt(c.size())]
    }

    /**
     * Get a FlowExtendedinstance for the second flow. The instance is built considering ISL ports on source and
     * destination switches of the first and second switch pairs. Also ports of the first flow are considered as well.
     * So no conflicts should appear while creating the flow and swapping flow endpoints.
     *
     * @param firstFlowSwitchPair Switch pair for the first flow
     * @param secondFlowSwitchPair Switch pair for the second flow
     * @param firstFlow The first flow instance
     * @param noVlans Whether use vlans or not
     * @return a FlowCreatePayload instance
     */
    def getSecondFlow(SwitchPair firstFlowSwitchPair, SwitchPair secondFlowSwitchPair, firstFlow, Boolean noVlans = false) {
        assumeTrue(firstFlowSwitchPair && secondFlowSwitchPair, "Required conditions for switch-pairs for this test are not met")
        def secondFlow = flowFactory.getBuilder(secondFlowSwitchPair).tap{
            if (noVlans) {
                it.withSourceVlan(0)
                it.withDestinationVlan(0)
            }
        }.build()
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

        return secondFlow
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

    def getHalfDifferentNeighboringSwitchPair(switchPairToAvoid, equalEndpoint) {
        def differentEndpoint = (equalEndpoint == "src" ? "dst" : "src")
        switchPairs.all().neighbouring().getSwitchPairs().find {
            it."$equalEndpoint" == switchPairToAvoid."$equalEndpoint" &&
                    it."$differentEndpoint" != switchPairToAvoid."$differentEndpoint"
        }
    }

    def getFlowLoopRules(SwitchId switchId) {
        switchRulesFactory.get(switchId).getRules().findAll {
            def hexCookie = Long.toHexString(it.cookie)
            hexCookie.startsWith("20080000") || hexCookie.startsWith("40080000")
        }
    }
}
