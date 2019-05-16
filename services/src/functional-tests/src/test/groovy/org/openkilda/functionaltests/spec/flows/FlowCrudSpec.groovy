package org.openkilda.functionaltests.spec.flows

import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.messaging.info.event.IslChangeType.MOVED
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.FlowNotApplicableException
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.tools.FlowTrafficExamBuilder

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Unroll

import javax.inject.Provider

@Slf4j
@Narrative("Verify CRUD operations and health of most typical types of flows on different types of switches.")
class FlowCrudSpec extends BaseSpecification {

    @Autowired
    Provider<TraffExamService> traffExamProvider

    @Shared
    def getPortViolationError = { String action, int port, SwitchId swId ->
        "Could not $action flow: The port $port on the switch '$swId' is occupied by an ISL."
    }

    @Unroll("Valid #data.description has traffic and no rule discrepancies \
(#flow.source.datapath - #flow.destination.datapath)")
    @Tags([TOPOLOGY_DEPENDENT])
    def "Valid flow has no rule discrepancies"() {
        given: "A flow"
        def traffExam = traffExamProvider.get()
        flowHelper.addFlow(flow)
        def path = PathHelper.convert(northbound.getFlowPath(flow.id))
        def switches = pathHelper.getInvolvedSwitches(path)
        //for single-flow cases need to add switch manually here, since PathHelper.convert will return an empty path
        if (flow.source.datapath == flow.destination.datapath) {
            switches << topology.activeSwitches.find { it.dpId == flow.source.datapath }
        }

        expect: "No rule discrepancies on every switch of the flow"
        switches.each { verifySwitchRules(it.dpId) }

        and: "No discrepancies when doing flow validation"
        northbound.validateFlow(flow.id).each { direction -> assert direction.asExpected }

        and: "The flow allows traffic (only applicable flows are checked)"
        try {
            def exam = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(flow, 0)
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
        flowHelper.deleteFlow(flow.id)

        then: "The flow is not present in NB"
        !northbound.getAllFlows().find { it.id == flow.id }

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
        flow = data.flow as FlowPayload
    }

    @Unroll("Able to create a second flow if #data.description")
    def "Able to create multiple flows on certain combinations of switch-port-vlans"() {
        given: "Two potential flows that should not conflict"
        Tuple2<FlowPayload, FlowPayload> flows = data.getNotConflictingFlows()

        when: "Create the first flow"
        flowHelper.addFlow(flows.first)

        and: "Try creating a second flow with #data.description"
        flowHelper.addFlow(flows.second)

        then: "Both flows are successfully created"
        northbound.getAllFlows()*.id.containsAll(flows*.id)

        and: "Cleanup: delete flows"
        flows.each { flowHelper.deleteFlow(it.id) }

        where:
        data << [
                [
                        description  : "same switch-port but vlans on src and dst are swapped",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def flow1 = getFlowHelper().randomFlow(srcSwitch, dstSwitch)
                            def flow2 = getFlowHelper().randomFlow(srcSwitch, dstSwitch).tap {
                                it.source.portNumber = flow1.source.portNumber
                                it.source.vlanId = flow1.destination.vlanId
                                it.destination.portNumber = flow1.destination.portNumber
                                it.destination.vlanId = flow1.source.vlanId
                            }
                            return new Tuple2<FlowPayload, FlowPayload>(flow1, flow2)
                        }
                ],
                [
                        description  : "same switch-port but vlans on src and dst are different",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def flow1 = getFlowHelper().randomFlow(srcSwitch, dstSwitch)
                            def flow2 = getFlowHelper().randomFlow(srcSwitch, dstSwitch).tap {
                                it.source.portNumber = flow1.source.portNumber
                                it.source.vlanId = flow1.source.vlanId + 1
                                it.destination.portNumber = flow1.destination.portNumber
                                it.destination.vlanId = flow1.destination.vlanId + 1
                            }
                            return new Tuple2<FlowPayload, FlowPayload>(flow1, flow2)
                        }
                ],
                [
                        description  : "vlan-port of new src = vlan-port of existing dst (+ different src)",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def flow1 = getFlowHelper().randomFlow(srcSwitch, dstSwitch)
                            //src for new flow will be on different switch not related to existing flow
                            //thus two flows will have same dst but different src
                            def newSrc = getTopology().activeSwitches.find {
                                ![flow1.source.datapath, flow1.destination.datapath].contains(it.dpId) &&
                                    getTopology().getAllowedPortsForSwitch(it).contains(flow1.destination.portNumber)
                            }
                            def flow2 = getFlowHelper().randomFlow(newSrc, dstSwitch).tap {
                                it.source.vlanId = flow1.destination.vlanId
                                it.source.portNumber = flow1.destination.portNumber
                                it.destination.vlanId = flow1.destination.vlanId + 1 //ensure no conflict on dst
                            }
                            return new Tuple2<FlowPayload, FlowPayload>(flow1, flow2)
                        }
                ],
                [
                        description  : "vlan-port of new dst = vlan-port of existing src (but different switches)",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def flow1 = getFlowHelper().randomFlow(srcSwitch, dstSwitch).tap {
                                def srcPort = getTopology().getAllowedPortsForSwitch(srcSwitch)
                                    .intersect(getTopology().getAllowedPortsForSwitch(dstSwitch))[0]
                                it.source.portNumber = srcPort
                            }
                            def flow2 = getFlowHelper().randomFlow(srcSwitch, dstSwitch).tap {
                                it.destination.vlanId = flow1.source.vlanId
                                it.destination.portNumber = flow1.source.portNumber
                            }
                            return new Tuple2<FlowPayload, FlowPayload>(flow1, flow2)
                        }
                ],
                [
                        description  : "vlan of new dst = vlan of existing src and port of new dst = port of " +
                                "existing dst",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def flow1 = getFlowHelper().randomFlow(srcSwitch, dstSwitch)
                            def flow2 = getFlowHelper().randomFlow(srcSwitch, dstSwitch).tap {
                                it.destination.vlanId = flow1.source.vlanId
                                it.destination.portNumber = flow1.destination.portNumber
                            }
                            return new Tuple2<FlowPayload, FlowPayload>(flow1, flow2)
                        }
                ]
        ]
    }

    @Unroll
    @Tags([TOPOLOGY_DEPENDENT])
    def "Able to create single switch single port flow with different vlan (#flow.source.datapath)"(FlowPayload flow) {
        given: "A flow"
        flowHelper.addFlow(flow)

        expect: "No rule discrepancies on the switch"
        verifySwitchRules(flow.source.datapath)

        and: "No discrepancies when doing flow validation"
        northbound.validateFlow(flow.id).each { direction -> assert direction.asExpected }

        when: "Remove the flow"
        flowHelper.deleteFlow(flow.id)

        then: "The flow is not present in NB"
        !northbound.getAllFlows().find { it.id == flow.id }

        and: "ISL bandwidth is restored"
        Wrappers.wait(WAIT_OFFSET) { northbound.getAllLinks().each { assert it.availableBandwidth == it.speed } }

        and: "No rule discrepancies on the switch after delete"
        Wrappers.wait(WAIT_OFFSET) { verifySwitchRules(flow.source.datapath) }

        where:
        flow << getSingleSwitchSinglePortFlows()
    }

    def "Able to validate flow with zero bandwidth"() {
        given: "A flow with zero bandwidth"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = 0

        when: "Create a flow with zero bandwidth"
        flowHelper.addFlow(flow)

        then: "Validation of flow with zero bandwidth must be succeed"
        northbound.validateFlow(flow.id).each { direction -> assert direction.asExpected }

        and: "Cleanup: delete the flow"
        flowHelper.deleteFlow(flow.id)
    }

    def "Unable to create single-switch flow with the same ports and vlans on both sides"() {
        given: "Potential single-switch flow with the same ports and vlans on both sides"
        def flow = flowHelper.singleSwitchSinglePortFlow(topology.activeSwitches.first())
        flow.destination.vlanId = flow.source.vlanId

        when: "Try creating such flow"
        northbound.addFlow(flow)

        then: "Error is returned, stating a readable reason"
        def error = thrown(HttpClientErrorException)
        error.statusCode == HttpStatus.BAD_REQUEST
        error.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not create flow: It is not allowed to create one-switch flow for the same ports and vlans"
    }

    @Unroll("Unable to create flow with #data.conflict")
    def "Unable to create flow with conflicting vlans or flow IDs"() {
        given: "A potential flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)

        and: "Another potential flow with #data.conflict"
        def conflictingFlow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        data.makeFlowsConflicting(flow, conflictingFlow)

        when: "Create the first flow"
        flowHelper.addFlow(flow)

        and: "Try creating the second flow which conflicts"
        northbound.addFlow(conflictingFlow)

        then: "Error is returned, stating a readable reason of conflict"
        def error = thrown(HttpClientErrorException)
        error.statusCode == HttpStatus.CONFLICT
        error.responseBodyAsString.to(MessageError).errorMessage == data.getError(flow, conflictingFlow)

        and: "Cleanup: delete the dominant flow"
        flowHelper.deleteFlow(flow.id)

        where:
        data << getConflictingData() + [
                conflict            : "the same flow ID",
                makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                    flowToConflict.id = dominantFlow.id
                },
                getError            : { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                    "Could not create flow: Flow $dominantFlow.id already exists"
                }
        ]
    }

    @Unroll("Unable to update flow (#data.conflict)")
    def "Unable to update flow when there are conflicting vlans"() {
        given: "Two potential flows"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow1 = flowHelper.randomFlow(srcSwitch, dstSwitch, false)
        def conflictingFlow = flowHelper.randomFlow(srcSwitch, dstSwitch, false, [flow1])

        data.makeFlowsConflicting(flow1, conflictingFlow)

        when: "Create two flows"
        flowHelper.addFlow(flow1)

        def flow2 = flowHelper.randomFlow(srcSwitch, dstSwitch, false, [flow1])
        flowHelper.addFlow(flow2)

        and: "Try updating the second flow which should conflict with the first one"
        northbound.updateFlow(flow2.id, conflictingFlow.tap { it.id = flow2.id })

        then: "Error is returned, stating a readable reason of conflict"
        def error = thrown(HttpClientErrorException)
        error.statusCode == HttpStatus.CONFLICT
        error.responseBodyAsString.to(MessageError).errorMessage == data.getError(flow1, conflictingFlow, "update")

        and: "Cleanup: delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        data << getConflictingData()
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
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        then: "The flow is built through one of the long paths"
        def flowPath = northbound.getFlowPath(flow.id)
        !(PathHelper.convert(flowPath) in possibleFlowPaths.findAll { it.size() == pathNodeCount })

        and: "The flow has symmetric forward and reverse paths even though there is a more preferable reverse path"
        def forwardIsls = pathHelper.getInvolvedIsls(PathHelper.convert(flowPath))
        def reverseIsls = pathHelper.getInvolvedIsls(PathHelper.convert(flowPath, "reversePath"))
        forwardIsls.collect { it.reversed }.reverse() == reverseIsls

        and: "Delete the flow and reset costs"
        flowHelper.deleteFlow(flow.id)
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
        northbound.addFlow(flow)

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
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        def paths = database.getPaths(srcSwitch.dpId, dstSwitch.dpId)*.path
        def switches = pathHelper.getInvolvedSwitches(paths.min { pathHelper.getCost(it) })

        when: "Init creation of a new flow"
        northbound.addFlow(flow)

        and: "Immediately remove the flow"
        northbound.deleteFlow(flow.id)

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

    @Unroll
    def "Unable to create a flow on an isl port in case port is occupied on a #data.switchType switch"() {
        given: "An isl"
        Isl isl = topology.islsForActiveSwitches.find { it.aswitch && it.dstSwitch }
        assumeTrue("Unable to find required isl", isl as boolean)

        when: "Try to create a flow using isl port"
        def flow = flowHelper.randomFlow(isl.srcSwitch, isl.dstSwitch)
        flow."$data.switchType".portNumber = isl."$data.port"
        flowHelper.addFlow(flow)

        then: "Flow is not created"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage == data.message(isl)

        where:
        data << [
                [
                        switchType: "source",
                        port      : "srcPort",
                        message   : { Isl violatedIsl ->
                            getPortViolationError("create", violatedIsl.srcPort, violatedIsl.srcSwitch.dpId)
                        }
                ],
                [
                        switchType: "destination",
                        port      : "dstPort",
                        message   : { Isl violatedIsl ->
                            getPortViolationError("create", violatedIsl.dstPort, violatedIsl.dstSwitch.dpId)
                        }
                ]
        ]
    }

    @Unroll
    def "Unable to update a flow in case new port is an isl port on a #data.switchType switch"() {
        given: "An isl"
        Isl isl = topology.islsForActiveSwitches.find { it.aswitch && it.dstSwitch }
        assumeTrue("Unable to find required isl", isl as boolean)

        and: "A flow"
        def flow = flowHelper.randomFlow(isl.srcSwitch, isl.dstSwitch)
        flowHelper.addFlow(flow)

        when: "Try to edit port to isl port"
        flowHelper.updateFlow(flow.id, flow.tap { it."$data.switchType".portNumber = isl."$data.port" })

        then:
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage == data.message(isl)

        and: "Cleanup: delete the flow"
        flowHelper.deleteFlow(flow.id)

        where:
        data << [
                [
                        switchType: "source",
                        port      : "srcPort",
                        message   : { Isl violatedIsl ->
                            getPortViolationError("update", violatedIsl.srcPort, violatedIsl.srcSwitch.dpId)
                        }
                ],
                [
                        switchType: "destination",
                        port      : "dstPort",
                        message   : { Isl violatedIsl ->
                            getPortViolationError("update", violatedIsl.dstPort, violatedIsl.dstSwitch.dpId)
                        }
                ]
        ]
    }

    def "Unable to create a flow on an isl port when ISL status is FAILED"() {
        given: "An inactive isl with failed state"
        Isl isl = topology.islsForActiveSwitches.find { it.aswitch && it.dstSwitch }
        assumeTrue("Unable to find required isl", isl as boolean)
        northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)
        islUtils.waitForIslStatus([isl, isl.reversed], FAILED)

        when: "Try to create a flow using ISL src port"
        def flow = flowHelper.randomFlow(isl.srcSwitch, isl.dstSwitch)
        flow.source.portNumber = isl.srcPort
        flowHelper.addFlow(flow)

        then: "Flow is not created"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                getPortViolationError("create", isl.srcPort, isl.srcSwitch.dpId)

        and: "Cleanup: Restore state of the ISL"
        northbound.portUp(isl.srcSwitch.dpId, isl.srcPort)
        islUtils.waitForIslStatus([isl, isl.reversed], DISCOVERED)
    }

    def "Unable to create a flow on an isl port when ISL status is MOVED"() {
        given: "An inactive isl with moved state"
        Isl isl = topology.islsForActiveSwitches.find { it.aswitch && it.dstSwitch }
        assumeTrue("Unable to find required isl", isl as boolean)
        def notConnectedIsl = topology.notConnectedIsls.first()
        def newIsl = islUtils.replug(isl, false, notConnectedIsl, true)

        islUtils.waitForIslStatus([isl, isl.reversed], MOVED)
        islUtils.waitForIslStatus([newIsl, newIsl.reversed], DISCOVERED)

        when: "Try to create a flow using ISL src port"
        def flow = flowHelper.randomFlow(isl.srcSwitch, isl.dstSwitch)
        flow.source.portNumber = isl.srcPort
        flowHelper.addFlow(flow)

        then: "Flow is not created"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                getPortViolationError("create", isl.srcPort, isl.srcSwitch.dpId)

        and: "Cleanup: Restore status of the ISL and delete new created ISL"
        islUtils.replug(newIsl, true, isl, false)
        islUtils.waitForIslStatus([isl, isl.reversed], DISCOVERED)
        islUtils.waitForIslStatus([newIsl, newIsl.reversed], MOVED)
        northbound.deleteLink(islUtils.toLinkParameters(newIsl))
        Wrappers.wait(WAIT_OFFSET) { assert !islUtils.getIslInfo(newIsl).isPresent() }
    }

    @Shared
    def errorMessage = { String operation, FlowPayload flow, String endpoint, FlowPayload conflictingFlow,
                         String conflictingEndpoint ->
        "Could not $operation flow: Requested flow '$conflictingFlow.id' conflicts with existing flow '$flow.id'. " +
                "Details: requested flow '$conflictingFlow.id' $conflictingEndpoint: " +
                "switch=${conflictingFlow."$conflictingEndpoint".datapath} " +
                "port=${conflictingFlow."$conflictingEndpoint".portNumber} " +
                "vlan=${conflictingFlow."$conflictingEndpoint".vlanId}, " +
                "existing flow '$flow.id' $endpoint: " +
                "switch=${flow."$endpoint".datapath} " +
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
    def traffgensPrioritized = { SwitchPair switchPair ->
        [switchPair.src, switchPair.dst].count { Switch sw ->
            !topology.activeTraffGens.find { it.switchConnected == sw }
        }
    }

    /**
     * Get list of all unique flows without transit switch (neighboring switches), permute by vlan presence. 
     * By unique flows it considers combinations of unique src/dst switch descriptions and OF versions.
     */
    def getFlowsWithoutTransitSwitch() {
        def switchPairs = topologyHelper.getAllNeighboringSwitchPairs().sort(traffgensPrioritized)
                .unique { [it.src, it.dst]*.description.sort() }

        return switchPairs.inject([]) { r, switchPair ->
            r << [
                    description: "flow without transit switch and with random vlans",
                    flow       : flowHelper.randomFlow(switchPair)
            ]
            r << [
                    description: "flow without transit switch and without vlans",
                    flow       : flowHelper.randomFlow(switchPair).tap {
                        it.source.vlanId = 0
                        it.destination.vlanId = 0
                    }
            ]
            r << [
                    description: "flow without transit switch and vlan only on src",
                    flow       : flowHelper.randomFlow(switchPair).tap { it.destination.vlanId = 0 }
            ]
            r
        }
    }

    /**
     * Get list of all unique flows with transit switch (not neighboring switches), permute by vlan presence. 
     * By unique flows it considers combinations of unique src/dst switch descriptions and OF versions.
     */
    def getFlowsWithTransitSwitch() {
        def switchPairs = topologyHelper.getAllNotNeighboringSwitchPairs().sort(traffgensPrioritized)
                .unique { [it.src, it.dst]*.description.sort() }

        return switchPairs.inject([]) { r, switchPair ->
            r << [
                    description: "flow with transit switch and random vlans",
                    flow       : flowHelper.randomFlow(switchPair)
            ]
            r << [
                    description: "flow with transit switch and no vlans",
                    flow       : flowHelper.randomFlow(switchPair).tap {
                        it.source.vlanId = 0
                        it.destination.vlanId = 0
                    }
            ]
            r << [
                    description: "flow with transit switch and vlan only on dst",
                    flow       : flowHelper.randomFlow(switchPair).tap { it.source.vlanId = 0 }
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
                    flow       : flowHelper.singleSwitchFlow(sw)
            ]
            r << [
                    description: "single-switch flow without vlans",
                    flow       : flowHelper.singleSwitchFlow(sw).tap {
                        it.source.vlanId = 0
                        it.destination.vlanId = 0
                    }
            ]
            r << [
                    description: "single-switch flow with vlan only on dst",
                    flow       : flowHelper.singleSwitchFlow(sw).tap {
                        it.source.vlanId = 0
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
                .collect { flowHelper.singleSwitchSinglePortFlow(it) }
    }

    Switch findSwitch(SwitchId swId) {
        topology.activeSwitches.find { it.dpId == swId }
    }

    def getConflictingData() {
        [
                [
                        conflict            : "the same vlans on the same port on src switch",
                        makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                            flowToConflict.source.portNumber = dominantFlow.source.portNumber
                            flowToConflict.source.vlanId = dominantFlow.source.vlanId
                        },
                        getError            : { FlowPayload dominantFlow, FlowPayload flowToConflict,
                                                String operation = "create" ->
                            errorMessage(operation, dominantFlow, "source", flowToConflict, "source")
                        }
                ],
                [
                        conflict            : "the same vlans on the same port on dst switch",
                        makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                            flowToConflict.destination.portNumber = dominantFlow.destination.portNumber
                            flowToConflict.destination.vlanId = dominantFlow.destination.vlanId
                        },
                        getError            : { FlowPayload dominantFlow, FlowPayload flowToConflict,
                                                String operation = "create" ->
                            errorMessage(operation, dominantFlow, "destination", flowToConflict, "destination")
                        }
                ],
                [
                        conflict            : "no vlan vs vlan on the same port on src switch",
                        makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                            flowToConflict.source.portNumber = dominantFlow.source.portNumber
                            flowToConflict.source.vlanId = 0
                        },
                        getError            : { FlowPayload dominantFlow, FlowPayload flowToConflict,
                                                String operation = "create" ->
                            errorMessage(operation, dominantFlow, "source", flowToConflict, "source")
                        }
                ],
                [
                        conflict            : "no vlan vs vlan on the same port on dst switch",
                        makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                            flowToConflict.destination.portNumber = dominantFlow.destination.portNumber
                            flowToConflict.destination.vlanId = 0
                        },
                        getError            : { FlowPayload dominantFlow, FlowPayload flowToConflict,
                                                String operation = "create" ->
                            errorMessage(operation, dominantFlow, "destination", flowToConflict, "destination")
                        }
                ],
                [
                        conflict            : "vlan vs no vlan on the same port on src switch",
                        makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                            flowToConflict.source.portNumber = dominantFlow.source.portNumber
                            dominantFlow.source.vlanId = 0
                        },
                        getError            : { FlowPayload dominantFlow, FlowPayload flowToConflict,
                                                String operation = "create" ->
                            errorMessage(operation, dominantFlow, "source", flowToConflict, "source")
                        }
                ],
                [
                        conflict            : "vlan vs no vlan on the same port on dst switch",
                        makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                            flowToConflict.destination.portNumber = dominantFlow.destination.portNumber
                            dominantFlow.destination.vlanId = 0
                        },
                        getError            : { FlowPayload dominantFlow, FlowPayload flowToConflict,
                                                String operation = "create" ->
                            errorMessage(operation, dominantFlow, "destination", flowToConflict, "destination")
                        }
                ],
                [
                        conflict            : "no vlan, both flows are on the same port on src switch",
                        makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                            flowToConflict.source.portNumber = dominantFlow.source.portNumber
                            flowToConflict.source.vlanId = 0
                            dominantFlow.source.vlanId = 0
                        },
                        getError            : { FlowPayload dominantFlow, FlowPayload flowToConflict,
                                                String operation = "create" ->
                            errorMessage(operation, dominantFlow, "source", flowToConflict, "source")
                        }
                ],
                [
                        conflict            : "no vlan, both flows are on the same port on dst switch",
                        makeFlowsConflicting: { FlowPayload dominantFlow, FlowPayload flowToConflict ->
                            flowToConflict.destination.portNumber = dominantFlow.destination.portNumber
                            flowToConflict.destination.vlanId = 0
                            dominantFlow.destination.vlanId = 0
                        },
                        getError            : { FlowPayload dominantFlow, FlowPayload flowToConflict,
                                                String operation = "create" ->
                            errorMessage(operation, dominantFlow, "destination", flowToConflict, "destination")
                        }
                ]
        ]
    }
}
