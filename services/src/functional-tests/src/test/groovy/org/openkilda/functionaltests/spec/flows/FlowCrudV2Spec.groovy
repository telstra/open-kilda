package org.openkilda.functionaltests.spec.flows

import groovy.util.logging.Slf4j
import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.FlowHelperV2
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.PotentialFlow
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowEndpointPayload
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.service.traffexam.FlowNotApplicableException
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.tools.FlowTrafficExamBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Unroll

import javax.inject.Provider

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.WAIT_OFFSET

@Slf4j
@Narrative("Verify CRUD operations and health of most typical types of flows on different types of switches.")
class FlowCrudV2Spec extends BaseSpecification {

    @Autowired
    Provider<TraffExamService> traffExamProvider

    @Autowired
    NorthboundServiceV2 northboundV2
    
    @Autowired
    FlowHelperV2 flowHelperV2

    @Unroll("Valid #data.description has traffic and no rule discrepancies \
(#flow.source.switchId - #flow.destination.switchId)")
    def "Valid flow has no rule discrepancies"() {
        given: "A flow"
        def traffExam = traffExamProvider.get()
        flowHelperV2.addFlow(flow)
        def path = PathHelper.convert(northbound.getFlowPath(flow.flowId))
        def switches = pathHelper.getInvolvedSwitches(path)
        //for single-flow cases need to add switch manually here, since PathHelper.convert will return an empty path
        if (flow.source.switchId == flow.destination.switchId) {
            switches << topology.activeSwitches.find { it.dpId == flow.source.switchId }
        }

        expect: "No rule discrepancies on every switch of the flow"
        switches.each { verifySwitchRules(it.dpId) }

        and: "No discrepancies when doing flow validation"
        northbound.validateFlow(flow.flowId).each { direction ->
            def discrepancies = profile == "virtual" ?
                    direction.discrepancies.findAll { it.field != "meterId" } : //unable to validate meters for virtual
                    direction.discrepancies
            assert discrepancies.empty
        }

        and: "The flow allows traffic (only applicable flows are checked)"
        try {
            def exam = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(toFlowPayload(flow), 0)
            [exam.forward, exam.reverse].each { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        } catch (FlowNotApplicableException e) {
            //flow is not applicable for traff exam. That's fine, just inform
            log.warn(e.message)
        }

        when: "Remove the flow"
        flowHelper.deleteFlow(flow.flowId)

        then: "The flow is not present in NB"
        !northbound.getAllFlows().find { it.id == flow.flowId }

        and: "ISL bandwidth is restored"
        Wrappers.wait(WAIT_OFFSET) { northbound.getAllLinks().each { assert it.availableBandwidth == it.speed } }

        and: "No rule discrepancies on every switch of the flow"
        switches.each { sw -> Wrappers.wait(WAIT_OFFSET) { verifySwitchRules(sw.dpId) } }

        where:
        /*Some permutations may be missed, since at current implementation we only take 'direct' possible flows
        * without modifying the costs of ISLs.
        * I.e. if potential test case with transit switch between certain pair of unique switches will require change of
        * costs on ISLs (those switches are neighbors, but we want a path with transit switch between them), then
        * we will not test such case
        */
        data << flowsWithoutTransitSwitch + flowsWithTransitSwitch + singleSwitchFlows
        flow = data.flow as FlowRequestV2
    }

    @Unroll("Able to create a second flow if #data.description")
    def "Able to create multiple flows on certain combinations of switch-port-vlans"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow1 = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flowHelperV2.addFlow(flow1)

        and: "Try creating a second flow with #data.description"
        FlowRequestV2 flow2 = data.getSecondFlow(flow1)
        flowHelperV2.addFlow(flow2)

        then: "Both flows are successfully created"
        northbound.getAllFlows()*.id.containsAll([flow1.flowId, flow2.flowId])

        and: "Cleanup: delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.flowId) }

        where:
        data << [
                [
                        description  : "same switch-port but vlans on src and dst are swapped",
                        getSecondFlow: { FlowRequestV2 existingFlow ->
                            return getFlowHelperV2().randomFlow(findSwitch(existingFlow.source.switchId),
                                    findSwitch(existingFlow.destination.switchId)).with {
                                it.toBuilder()
                                        .source(it.source.toBuilder()
                                                .portNumber(existingFlow.source.portNumber)
                                                .vlanId(existingFlow.destination.vlanId)
                                                .build())
                                        .destination(it.destination.toBuilder()
                                                .portNumber(existingFlow.destination.portNumber)
                                                .vlanId(existingFlow.source.vlanId)
                                                .build())
                                        .build()
                            }
                        }
                ],
                [
                        description  : "same switch-port but vlans on src and dst are different",
                        getSecondFlow: { FlowRequestV2 existingFlow ->
                            return getFlowHelperV2().randomFlow(findSwitch(existingFlow.source.switchId),
                                    findSwitch(existingFlow.destination.switchId)).with {
                                it.toBuilder()
                                        .source(it.source.toBuilder()
                                                .portNumber(existingFlow.source.portNumber)
                                                .vlanId(existingFlow.source.vlanId + 1)
                                                .build())
                                        .destination(it.destination.toBuilder()
                                                .portNumber(existingFlow.destination.portNumber)
                                                .vlanId(existingFlow.destination.vlanId + 1)
                                                .build())
                                        .build()
                            }
                        }
                ],
                [
                        description  : "vlan-port of new src = vlan-port of existing dst (but different switches)",
                        getSecondFlow: { FlowRequestV2 existingFlow ->
                            //src for new flow will be on different switch not related to existing flow
                            //thus two flows will have same dst but different src
                            def newSrcSwitch = getTopology().activeSwitches.find {
                                ![existingFlow.source.switchId, existingFlow.destination.switchId].contains(it.dpId)
                            }
                            return getFlowHelperV2().randomFlow(newSrcSwitch, findSwitch(
                                    existingFlow.destination.switchId)).with {
                                it.toBuilder()
                                        .source(it.source.toBuilder()
                                                .vlanId(existingFlow.destination.vlanId)
                                                .portNumber(existingFlow.destination.portNumber)
                                                .build())
                                        .destination(it.destination.toBuilder()
                                                .vlanId(existingFlow.destination.vlanId + 1)
                                                .build())
                                        .build()
                            }
                        }
                ],
                [
                        description  : "vlan-port of new dst = vlan-port of existing src (but different switches)",
                        getSecondFlow: { FlowRequestV2 existingFlow ->
                            def newSrcSwitch = getTopology().activeSwitches.find {
                                ![existingFlow.source.switchId, existingFlow.destination.switchId].contains(it.dpId)
                            }
                            return getFlowHelperV2().randomFlow(newSrcSwitch, findSwitch(
                                    existingFlow.destination.switchId)).with {
                                it.toBuilder()
                                        .destination(it.destination.toBuilder()
                                                .vlanId(existingFlow.source.vlanId)
                                                .portNumber(existingFlow.source.portNumber)
                                                .build())
                                        .build()
                            }
                        }
                ],
                [
                        description  : "vlan of new dst = vlan of existing src and port of new dst = port of " +
                                "existing dst",
                        getSecondFlow: { FlowRequestV2 existingFlow ->
                            def newSrcSwitch = getTopology().activeSwitches.find {
                                ![existingFlow.source.switchId, existingFlow.destination.switchId].contains(it.dpId)
                            }
                            return getFlowHelperV2().randomFlow(newSrcSwitch, findSwitch(
                                    existingFlow.destination.switchId)).with {
                                it.toBuilder()
                                        .destination(it.destination.toBuilder()
                                                .vlanId(existingFlow.source.vlanId)
                                                .portNumber(existingFlow.destination.portNumber)
                                                .build())
                                        .build()
                            }
                        }
                ]
        ]
    }

    @Unroll
    def "Able to create single switch single port flow with different vlan (#flow.source.datapath)"(
            FlowRequestV2 flow) {
        given: "A flow"
        flowHelperV2.addFlow(flow)

        expect: "No rule discrepancies on the switch"
        verifySwitchRules(flow.source.switchId)

        and: "No discrepancies when doing flow validation"
        northbound.validateFlow(flow.flowId).each { direction ->
            def discrepancies = profile == "virtual" ?
                    direction.discrepancies.findAll { it.field != "meterId" } : //unable to validate meters for virtual
                    direction.discrepancies
            assert discrepancies.empty
        }

        when: "Remove the flow"
        flowHelper.deleteFlow(flow.flowId)

        then: "The flow is not present in NB"
        !northbound.getAllFlows().find { it.id == flow.flowId }

        and: "ISL bandwidth is restored"
        Wrappers.wait(WAIT_OFFSET) { northbound.getAllLinks().each { assert it.availableBandwidth == it.speed } }

        and: "No rule discrepancies on the switch after delete"
        Wrappers.wait(WAIT_OFFSET) { verifySwitchRules(flow.source.switchId) }

        where:
        flow << getSingleSwitchSinglePortFlows()
    }

    def "Able to validate flow with zero bandwidth"() {
        given: "A flow with zero bandwidth"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flow = flow.toBuilder()
                .maximumBandwidth(0)
                .build()

        when: "Create a flow with zero bandwidth"
        flowHelperV2.addFlow(flow)

        then: "Validation of flow with zero bandwidth must be succeed"
        northbound.validateFlow(flow.flowId).each { direction ->
            assert direction.discrepancies.empty
        }

        and: "Cleanup: delete the flow"
        flowHelper.deleteFlow(flow.flowId)
    }

    def "Unable to create single-switch flow with the same ports and vlans on both sides"() {
        given: "Potential single-switch flow with the same ports and vlans on both sides"
        def flow = flowHelperV2.singleSwitchSinglePortFlow(topology.activeSwitches.first())
        flow = flow.toBuilder()
                .destination(flow.getDestination().toBuilder().vlanId(flow.source.vlanId).build())
                .build()

        when: "Try creating such flow"
        northboundV2.addFlow(flow)

        then: "Error is returned, stating a readable reason"
        def error = thrown(HttpClientErrorException)
        error.statusCode == HttpStatus.BAD_REQUEST
        error.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not create flow: It is not allowed to create one-switch flow for the same ports and vlans"
    }

    @Unroll("Unable to create flow with #data.conflict")
    def "Unable to create flow with conflicting vlans or flow IDs"() {
        given: "Two flows with #data.conflict"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches

        def (FlowRequestV2 mainFlow, FlowRequestV2 conflictingFlow) = data.makeFlowsConflicting(
                flowHelperV2.randomFlow(srcSwitch, dstSwitch), flowHelperV2.randomFlow(srcSwitch, dstSwitch))

        when: "Create the first flow"
        flowHelperV2.addFlow(mainFlow)

        and: "Try creating the second flow which conflicts"
        flowHelperV2.addFlow(conflictingFlow)

        then: "Error is returned, stating a readable reason of conflict"
        def error = thrown(HttpClientErrorException)
        error.statusCode == HttpStatus.CONFLICT
        error.responseBodyAsString.to(MessageError).errorMessage == data.getError(mainFlow, conflictingFlow)

        and: "Cleanup: delete the dominant flow"
        flowHelper.deleteFlow(mainFlow.flowId)

        where:
        data << getConflictingData() + [
                conflict            : "the same flow ID",
                makeFlowsConflicting: { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict ->
                    flowToConflict.toBuilder().flowId(dominantFlow.flowId).build()
                },
                getError            : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict ->
                    "Could not create flow: Flow $dominantFlow.flowId already exists"
                }
        ]
    }

    def "A flow cannot be created with asymmetric forward and reverse paths"() {
        given: "Two active neighboring switches with two possible flow paths at least and different number of hops"
        List<List<PathNode>> possibleFlowPaths = []
        int pathNodeCount = 2
        def (Switch srcSwitch, Switch dstSwitch) = topology.getIslsForActiveSwitches().find {
            possibleFlowPaths = database.getPaths(it.srcSwitch.dpId, it.dstSwitch.dpId)*.path.sort { it.size() }
            possibleFlowPaths.size() > 1 && possibleFlowPaths.max { it.size() }.size() > pathNodeCount
        }.collect {
            [it.srcSwitch, it.dstSwitch]
        }.flatten() ?: assumeTrue("No suiting active neighboring switches with two possible flow paths at least and " +
                "different number of hops found", false)

        and: "Make all shorter forward paths not preferable. Shorter reverse paths are still preferable"
        possibleFlowPaths.findAll { it.size() == pathNodeCount }.each {
            pathHelper.getInvolvedIsls(it).each { database.updateIslCost(it, Integer.MAX_VALUE) }
        }

        when: "Create a flow"
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flowHelperV2.addFlow(flow)

        then: "The flow is built through one of the long paths"
        def flowPath = northbound.getFlowPath(flow.flowId)
        !(PathHelper.convert(flowPath) in possibleFlowPaths.findAll { it.size() == pathNodeCount })

        and: "The flow has symmetric forward and reverse paths even though there is a more preferable reverse path"
        def forwardIsls = pathHelper.getInvolvedIsls(PathHelper.convert(flowPath))
        def reverseIsls = pathHelper.getInvolvedIsls(PathHelper.convert(flowPath, "reversePath"))
        forwardIsls.collect { it.reversed }.reverse() == reverseIsls

        and: "Delete the flow and reset costs"
        flowHelper.deleteFlow(flow.flowId)
        database.resetCosts()
    }

    @Unroll
    def "Error is returned if there is no available path to #data.isolatedSwitchType switch"() {
        given: "A switch that has no connection to other switches"
        def isolatedSwitch = topology.activeSwitches[1]
        topology.getBusyPortsForSwitch(isolatedSwitch).each { port ->
            northbound.portDown(isolatedSwitch.dpId, port)
        }
        //wait until ISLs are actually got failed
        Wrappers.wait(WAIT_OFFSET) {
            def islData = northbound.getAllLinks()
            topology.getRelatedIsls(isolatedSwitch).each {
                assert islUtils.getIslInfo(islData, it).get().state == IslChangeType.FAILED
            }
        }

        when: "Try building a flow using the isolated switch"
        def flow = data.getFlow(isolatedSwitch)
        northboundV2.addFlow(flow)

        then: "Error is returned, stating that there is no path found for such flow"
        def error = thrown(HttpClientErrorException)
        error.statusCode == HttpStatus.NOT_FOUND
        error.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not create flow: Not enough bandwidth found or path not found : Failed to find path with " +
                "requested bandwidth=$flow.maximumBandwidth: Switch ${isolatedSwitch.dpId.toString()} doesn't have " +
                "links with enough bandwidth"

        and: "Cleanup: restore connection to the isolated switch and reset costs"
        topology.getBusyPortsForSwitch(isolatedSwitch).each { port ->
            northbound.portUp(isolatedSwitch.dpId, port)
        }
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state == IslChangeType.DISCOVERED }
        }
        database.resetCosts()

        where:
        data << [
                [
                        isolatedSwitchType: "source",
                        getFlow           : { Switch theSwitch ->
                            getFlowHelper().randomFlow(theSwitch, getTopology().activeSwitches.find { it != theSwitch })
                        }
                ],
                [
                        isolatedSwitchType: "destination",
                        getFlow           : { Switch theSwitch ->
                            getFlowHelper().randomFlow(getTopology().activeSwitches.find { it != theSwitch }, theSwitch)
                        }
                ]
        ]
    }

    def "Removing flow while it is still in progress of being set up should not cause rule discrepancies"() {
        given: "A potential flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        def paths = database.getPaths(srcSwitch.dpId, dstSwitch.dpId)*.path
        def switches = pathHelper.getInvolvedSwitches(paths.min { pathHelper.getCost(it) })

        when: "Init creation of a new flow"
        northboundV2.addFlow(flow)

        and: "Immediately remove the flow"
        northbound.deleteFlow(flow.flowId)

        then: "All related switches have no discrepancies in rules"
        Wrappers.wait(WAIT_OFFSET) {
            switches.each {
                def rules = northbound.validateSwitchRules(it.dpId)
                assert rules.excessRules.empty
                assert rules.missingRules.empty
                assert rules.properRules.empty
            }
        }
    }

    @Shared
    def errorMessage = { String operation, FlowRequestV2 flow, String endpoint, FlowRequestV2 conflictingFlow,
                         String conflictingEndpoint ->
        "Could not $operation flow: Requested flow '$conflictingFlow.flowId' " +
                "conflicts with existing flow '$flow.flowId'. " +
                "Details: requested flow '$conflictingFlow.flowId' $conflictingEndpoint: " +
                "switch=${conflictingFlow."$conflictingEndpoint".switchId} " +
                "port=${conflictingFlow."$conflictingEndpoint".portNumber} " +
                "vlan=${conflictingFlow."$conflictingEndpoint".vlanId}, " +
                "existing flow '$flow.flowId' $endpoint: " +
                "switch=${flow."$endpoint".switchId} " +
                "port=${flow."$endpoint".portNumber} " +
                "vlan=${flow."$endpoint".vlanId}"
    }


    /**
     * Potential flows with more traffgen-available switches will go first. Then the less tg-available switches there is
     * in the pair the lower score that pair will get.
     * During the subsequent 'unique' call the higher scored pairs will have priority over lower scored ones in case
     * if their uniqueness criteria will be equal.
     */
    @Shared
    def taffgensPrioritized = { PotentialFlow potentialFlow ->
        [potentialFlow.src, potentialFlow.dst].count { Switch sw ->
            !topology.activeTraffGens.find { it.switchConnected == sw }
        }
    }

    /**
     * Get list of all unique flows without transit switch (neighboring switches), permute by vlan presence.
     * By unique flows it considers combinations of unique src/dst switch descriptions and OF versions.
     */
    def getFlowsWithoutTransitSwitch() {
        def potentialFlows = topologyHelper.findAllNeighbors().sort(taffgensPrioritized)
                .unique { [it.src, it.dst]*.description.sort() }

        return potentialFlows.inject([]) { r, potentialFlow ->
            r << [
                    description: "flow without transit switch and with random vlans",
                    flow       : flowHelperV2.randomFlow(potentialFlow)
            ]
            r << [
                    description: "flow without transit switch and without vlans",
                    flow       : flowHelperV2.randomFlow(potentialFlow).tap {
                        it.toBuilder()
                                .source(it.source.toBuilder().vlanId(0).build())
                                .destination(it.destination.toBuilder().vlanId(0).build())
                                .build()
                    }
            ]
            r << [
                    description: "flow without transit switch and vlan only on src",
                    flow       : flowHelperV2.randomFlow(potentialFlow).tap { it.toBuilder()
                            .source(it.destination.toBuilder().vlanId(0).build())
                            .build()
                    }
            ]
            r
        }
    }

    /**
     * Get list of all unique flows with transit switch (not neighboring switches), permute by vlan presence.
     * By unique flows it considers combinations of unique src/dst switch descriptions and OF versions.
     */
    def getFlowsWithTransitSwitch() {
        def potentialFlows = topologyHelper.findAllNonNeighbors().sort(taffgensPrioritized)
                .unique { [it.src, it.dst]*.description.sort() }

        return potentialFlows.inject([]) { r, potentialFlow ->
            r << [
                    description: "flow with transit switch and random vlans",
                    flow       : flowHelperV2.randomFlow(potentialFlow)
            ]
            r << [
                    description: "flow with transit switch and no vlans",
                    flow       : flowHelperV2.randomFlow(potentialFlow).tap {
                        it.toBuilder()
                                .source(it.source.toBuilder().vlanId(0).build())
                                .destination(it.destination.toBuilder().vlanId(0).build())
                                .build()
                    }
            ]
            r << [
                    description: "flow with transit switch and vlan only on dst",
                    flow       : flowHelperV2.randomFlow(potentialFlow).tap {
                        it.toBuilder()
                                .source(it.source.toBuilder().vlanId(0).build())
                                .build()
                    }
            ]
            r
        }
    }

    /**
     * Get list of all unique single-switch flows, permute by vlan presence. By unique flows it considers
     * using all unique switch descriptions and OF versions.
     */
    def getSingleSwitchFlows() {
        topology.getActiveSwitches()
                .sort { sw -> topology.activeTraffGens.findAll { it.switchConnected == sw }.size() }.reverse()
                .unique { it.description }
                .inject([]) { r, sw ->
            r << [
                    description: "single-switch flow with vlans",
                    flow       : flowHelperV2.singleSwitchFlow(sw)
            ]
            r << [
                    description: "single-switch flow without vlans",
                    flow       : flowHelperV2.singleSwitchFlow(sw).with {
                        it.toBuilder()
                                .source(it.getSource().toBuilder().vlanId(0).build())
                                .destination(it.getDestination().toBuilder().vlanId(0).build())
                                .build()
                    }
            ]
            r << [
                    description: "single-switch flow with vlan only on dst",
                    flow       : flowHelperV2.singleSwitchFlow(sw).tap {
                        it.toBuilder().source(it.getSource().toBuilder().vlanId(0).build()).build()
                    }
            ]
            r
        }
    }

    /**
     * Get list of all unique single-switch flows. By unique flows it considers
     * using all unique switch descriptions and OF versions.
     */
    def getSingleSwitchSinglePortFlows() {
        topology.getActiveSwitches()
                .unique { it.description }
                .collect { flowHelperV2.singleSwitchSinglePortFlow(it) }
    }

    Switch findSwitch(SwitchId swId) {
        topology.activeSwitches.find { it.dpId == swId }
    }

    FlowPayload toFlowPayload(FlowRequestV2 flow) {
        FlowEndpointV2 source = flow.source
        FlowEndpointV2 destination = flow.destination

        FlowPayload.builder()
                .id(flow.flowId)
                .source(new FlowEndpointPayload(source.switchId, source.portNumber, source.vlanId))
                .destination(new FlowEndpointPayload(destination.switchId, destination.portNumber, destination.vlanId))
                .maximumBandwidth(flow.maximumBandwidth)
                .ignoreBandwidth(flow.ignoreBandwidth)
                .build()
    }

    def getConflictingData() {
        [
                [
                        conflict            : "the same vlans on the same port on src switch",
                        makeFlowsConflicting: { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict ->
                            FlowRequestV2 conflictedFlow = flowToConflict.toBuilder()
                                    .source(flowToConflict.source.toBuilder()
                                            .portNumber(dominantFlow.source.portNumber)
                                            .vlanId(dominantFlow.source.vlanId)
                                            .build())
                                    .build()
                            return [dominantFlow, conflictedFlow]
                        },
                        getError            : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict,
                                                String operation = "create" ->
                            errorMessage(operation, dominantFlow, "source", flowToConflict, "source")
                        }
                ],
                [
                        conflict            : "the same vlans on the same port on dst switch",
                        makeFlowsConflicting: { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict ->
                            def destinationBuilder = flowToConflict.destination.toBuilder()
                            return [dominantFlow, flowToConflict.toBuilder()
                                    .destination(destinationBuilder
                                            .portNumber(dominantFlow.destination.portNumber)
                                            .vlanId(dominantFlow.destination.vlanId)
                                            .build())
                                    .build()]
                        },
                        getError            : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict,
                                                String operation = "create" ->
                            errorMessage(operation, dominantFlow, "destination", flowToConflict, "destination")
                        }
                ],
                [
                        conflict            : "no vlan vs vlan on the same port on src switch",
                        makeFlowsConflicting: { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict ->
                            def sourceBuilder = flowToConflict.source.toBuilder()
                            return [dominantFlow, flowToConflict .toBuilder()
                                    .source(sourceBuilder
                                            .portNumber(dominantFlow.source.portNumber)
                                            .vlanId(0)
                                            .build())
                                    .build()]
                        },
                        getError            : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict,
                                                String operation = "create" ->
                            errorMessage(operation, dominantFlow, "source", flowToConflict, "source")
                        }
                ],
                [
                        conflict            : "no vlan vs vlan on the same port on dst switch",
                        makeFlowsConflicting: { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict ->
                            def destinationBuilder = flowToConflict.destination.toBuilder()
                            return [dominantFlow, flowToConflict.toBuilder()
                                    .destination(destinationBuilder
                                            .portNumber(dominantFlow.destination.portNumber)
                                            .vlanId(0)
                                            .build())
                                    .build()]
                        },
                        getError            : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict,
                                                String operation = "create" ->
                            errorMessage(operation, dominantFlow, "destination", flowToConflict, "destination")
                        }
                ],
                [
                        conflict            : "vlan vs no vlan on the same port on src switch",
                        makeFlowsConflicting: { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict ->
                            def mainFlow = dominantFlow.toBuilder()
                                    .source(dominantFlow.source.toBuilder()
                                            .vlanId(0)
                                            .build())
                                    .build()
                            def conflictedFlow = flowToConflict.toBuilder()
                                    .source(flowToConflict.source.toBuilder()
                                            .portNumber(dominantFlow.source.portNumber)
                                            .build())
                                    .build()
                            return [mainFlow, conflictedFlow]
                        },
                        getError            : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict,
                                                String operation = "create" ->
                            errorMessage(operation, dominantFlow, "source", flowToConflict, "source")
                        }
                ],
                [
                        conflict            : "vlan vs no vlan on the same port on dst switch",
                        makeFlowsConflicting: { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict ->
                            def conflictedFlow = flowToConflict.toBuilder()
                                    .destination(flowToConflict.destination.toBuilder()
                                            .portNumber(dominantFlow.destination.portNumber)
                                            .vlanId(0)
                                            .build())
                                    .build()
                            return [dominantFlow, conflictedFlow]
                        },
                        getError            : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict,
                                                String operation = "create" ->
                            errorMessage(operation, dominantFlow, "destination", flowToConflict, "destination")
                        }
                ],
                [
                        conflict            : "no vlan, both flows are on the same port on src switch",
                        makeFlowsConflicting: { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict ->
                            def mainFlow = dominantFlow.toBuilder()
                                    .source(dominantFlow.source.toBuilder()
                                            .vlanId(0)
                                            .build())
                                    .build()
                            def conflictedFlow = flowToConflict.toBuilder()
                                    .source(flowToConflict.source.toBuilder()
                                            .portNumber(dominantFlow.source.portNumber)
                                            .vlanId(0)
                                            .build())
                                    .build()
                            return [mainFlow, conflictedFlow]
                        },
                        getError            : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict,
                                                String operation = "create" ->
                            errorMessage(operation, dominantFlow, "source", flowToConflict, "source")
                        }
                ],
                [
                        conflict            : "no vlan, both flows are on the same port on dst switch",
                        makeFlowsConflicting: { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict ->
                            def mainFlow = dominantFlow.toBuilder()
                                    .destination(dominantFlow.destination.toBuilder()
                                            .vlanId(0)
                                            .build())
                                    .build()
                            def conflictedFlow = flowToConflict.toBuilder()
                                    .destination(flowToConflict.destination.toBuilder()
                                            .portNumber(dominantFlow.destination.portNumber)
                                            .vlanId(0)
                                            .build())
                                    .build()
                            return [mainFlow, conflictedFlow]
                        },
                        getError            : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict,
                                                String operation = "create" ->
                            errorMessage(operation, dominantFlow, "destination", flowToConflict, "destination")
                        }
                ]
        ]
    }
}
