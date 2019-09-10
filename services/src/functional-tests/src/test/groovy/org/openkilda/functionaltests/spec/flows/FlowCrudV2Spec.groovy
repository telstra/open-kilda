package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.messaging.info.event.IslChangeType.MOVED
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.IterationTags
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.FlowHelperV2
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.messaging.payload.flow.DetectConnectedDevicesPayload
import org.openkilda.messaging.payload.flow.FlowEndpointPayload
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.Cookie
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.service.traffexam.FlowNotApplicableException
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.tools.FlowTrafficExamBuilder

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared
import spock.lang.Unroll

import javax.inject.Provider

@Slf4j
@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/hub-and-spoke/crud")
@Narrative("Verify CRUD operations and health of most typical types of flows on different types of switches.")
class FlowCrudV2Spec extends HealthCheckSpecification {

    @Autowired
    Provider<TraffExamService> traffExamProvider

    @Autowired
    NorthboundServiceV2 northboundV2

    @Autowired
    FlowHelperV2 flowHelperV2

    @Shared
    def getPortViolationError = { String action, int port, SwitchId swId ->
        "Could not $action flow: The port $port on the switch '$swId' is occupied by an ISL."
    }

    def "System marks flow as UP when and only when all the rules are actually set on all involved switches"() {
        given: "Two active not neighbouring switches"
        def switchPair = topologyHelper.getNotNeighboringSwitchPair()

        when: "Create a flow"
        def flow = flowHelperV2.randomFlow(switchPair)
        northboundV2.addFlow(flow)

        then: "Flow is created"
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP }

        and: "All needed rules are installed on all involved switches"
        flowHelperV2.checkRulesOnSwitches(flow.flowId, RULES_INSTALLATION_TIME, true)

        and: "Cleanup: Delete the flow"
        flowHelper.deleteFlow(flow.flowId)
    }

    @Tags([TOPOLOGY_DEPENDENT])
    @IterationTags([
            @IterationTag(tags = [SMOKE], iterationNameRegex = /vlan /),
            @IterationTag(tags = [LOW_PRIORITY], iterationNameRegex = /and vlan only on/)
    ])
    @Unroll("Valid #data.description has traffic and no rule discrepancies \
(#flow.source.switchId - #flow.destination.switchId)")
    def "Valid flow has no rule discrepancies"() {
        given: "A flow"
        assumeTrue("There should be at least two active traffgens for test execution",
                topology.activeTraffGens.size() >= 2)
        def traffExam = traffExamProvider.get()
        def allLinksInfoBefore = northbound.getAllLinks().collectEntries { [it.id, it.availableBandwidth] }.sort()
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
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        and: "The flow allows traffic (only applicable flows are checked)"
        try {
            def exam = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(toFlowPayload(flow), 1000, 3)
            withPool {
                [exam.forward, exam.reverse].eachParallel { direction ->
                    def resources = traffExam.startExam(direction)
                    direction.setResources(resources)
                    assert traffExam.waitExam(direction).hasTraffic()
                }
            }
        } catch (FlowNotApplicableException e) {
            //flow is not applicable for traff exam. That's fine, just inform
            log.warn(e.message)
        }

        when: "Remove the flow"
        flowHelper.deleteFlow(flow.flowId)

        then: "The flow is not present in NB"
        !northbound.getAllFlows().find { it.flowId == flow.flowId }

        and: "ISL bandwidth is restored"
        Wrappers.wait(WAIT_OFFSET) {
            def allLinksInfoAfter = northbound.getAllLinks().collectEntries { [it.id, it.availableBandwidth] }.sort()
            assert allLinksInfoBefore == allLinksInfoAfter
        }

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
        given: "Two potential flows that should not conflict"
        Tuple2<FlowRequestV2, FlowRequestV2> flows = data.getNotConflictingFlows()

        when: "Create the first flow"
        flowHelperV2.addFlow(flows.first)

        and: "Try creating a second flow with #data.description"
        flowHelperV2.addFlow(flows.second)

        then: "Both flows are successfully created"
        northbound.getAllFlows()*.id.containsAll(flows*.flowId)

        and: "Cleanup: delete flows"
        flows.each { flowHelper.deleteFlow(it.flowId) }

        where:
        data << [
                [
                        description  : "same switch-port but vlans on src and dst are swapped",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def flow1 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch)
                            def flow2 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch).tap {
                                it.source.portNumber = flow1.source.portNumber
                                it.source.vlanId = flow1.destination.vlanId
                                it.destination.portNumber = flow1.destination.portNumber
                                it.destination.vlanId = flow1.source.vlanId
                            }
                            return new Tuple2<FlowRequestV2, FlowRequestV2>(flow1, flow2)
                        }
                ],
                [
                        description  : "same switch-port but vlans on src and dst are different",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def flow1 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch)
                            def flow2 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch).tap {
                                it.source.portNumber = flow1.source.portNumber
                                it.source.vlanId = flow1.source.vlanId + 1
                                it.destination.portNumber = flow1.destination.portNumber
                                it.destination.vlanId = flow1.destination.vlanId + 1
                            }
                            return new Tuple2<FlowRequestV2, FlowRequestV2>(flow1, flow2)
                        }
                ],
                [
                        description  : "vlan-port of new src = vlan-port of existing dst (+ different src)",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def flow1 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch)
                            //src for new flow will be on different switch not related to existing flow
                            //thus two flows will have same dst but different src
                            def newSrc = getTopology().activeSwitches.find {
                                ![flow1.source.switchId, flow1.destination.switchId].contains(it.dpId) &&
                                        getTopology().getAllowedPortsForSwitch(it).contains(flow1.destination.portNumber)
                            }
                            def flow2 = getFlowHelperV2().randomFlow(newSrc, dstSwitch).tap {
                                it.source.vlanId = flow1.destination.vlanId
                                it.source.portNumber = flow1.destination.portNumber
                                it.destination.vlanId = flow1.destination.vlanId + 1 //ensure no conflict on dst
                            }
                            return new Tuple2<FlowRequestV2, FlowRequestV2>(flow1, flow2)
                        }
                ],
                [
                        description  : "vlan-port of new dst = vlan-port of existing src (but different switches)",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def flow1 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch).tap {
                                def srcPort = getTopology().getAllowedPortsForSwitch(srcSwitch)
                                        .intersect(getTopology().getAllowedPortsForSwitch(dstSwitch))[0]
                                it.source.portNumber = srcPort
                            }
                            def flow2 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch).tap {
                                it.destination.vlanId = flow1.source.vlanId
                                it.destination.portNumber = flow1.source.portNumber
                            }
                            return new Tuple2<FlowRequestV2, FlowRequestV2>(flow1, flow2)
                        }
                ],
                [
                        description  : "vlan of new dst = vlan of existing src and port of new dst = port of " +
                                "existing dst",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def flow1 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch)
                            def flow2 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch).tap {
                                it.destination.vlanId = flow1.source.vlanId
                                it.destination.portNumber = flow1.destination.portNumber
                            }
                            return new Tuple2<FlowRequestV2, FlowRequestV2>(flow1, flow2)
                        }
                ]
        ]
    }

    @Unroll
    @Tags([TOPOLOGY_DEPENDENT])
    def "Able to create single switch single port flow with different vlan (#flow.source.switchId)"(
            FlowRequestV2 flow) {
        given: "A flow"
        flowHelperV2.addFlow(flow)

        expect: "No rule discrepancies on the switch"
        verifySwitchRules(flow.source.switchId)

        and: "No discrepancies when doing flow validation"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        when: "Remove the flow"
        flowHelper.deleteFlow(flow.flowId)

        then: "The flow is not present in NB"
        !northbound.getAllFlows().find { it.id == flow.flowId }

        and: "No rule discrepancies on the switch after delete"
        Wrappers.wait(WAIT_OFFSET) { verifySwitchRules(flow.source.switchId) }

        where:
        flow << getSingleSwitchSinglePortFlows()
    }

    def "Able to validate flow with zero bandwidth"() {
        given: "A flow with zero bandwidth"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = 0

        when: "Create a flow with zero bandwidth"
        flowHelperV2.addFlow(flow)

        then: "Validation of flow with zero bandwidth must be succeed"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        and: "Cleanup: delete the flow"
        flowHelper.deleteFlow(flow.flowId)
    }

    def "Unable to create single-switch flow with the same ports and vlans on both sides"() {
        given: "Potential single-switch flow with the same ports and vlans on both sides"
        def flow = flowHelperV2.singleSwitchSinglePortFlow(topology.activeSwitches.first())
        flow.destination.vlanId = flow.source.vlanId

        when: "Try creating such flow"
        flowHelperV2.addFlow(flow)

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
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)

        and: "Another potential flow with #data.conflict"
        def conflictingFlow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        data.makeFlowsConflicting(flow, conflictingFlow)

        when: "Create the first flow"
        flowHelperV2.addFlow(flow)

        and: "Try creating the second flow which conflicts"
        flowHelperV2.addFlow(conflictingFlow)

        then: "Error is returned, stating a readable reason of conflict"
        def error = thrown(HttpClientErrorException)
        error.statusCode == HttpStatus.CONFLICT
        error.responseBodyAsString.to(MessageError).errorMessage == data.getError(flow, conflictingFlow)

        and: "Cleanup: delete the dominant flow"
        flowHelper.deleteFlow(flow.flowId)

        where:
        data << getConflictingData() + [
                conflict            : "the same flow ID",
                makeFlowsConflicting: { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict ->
                    flowToConflict.flowId = dominantFlow.flowId
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
            antiflap.portDown(isolatedSwitch.dpId, port)
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
            antiflap.portUp(isolatedSwitch.dpId, port)
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
                            getFlowHelperV2().randomFlow(theSwitch, getTopology()
                                    .activeSwitches.find { it != theSwitch })
                        }
                ],
                [
                        isolatedSwitchType: "destination",
                        getFlow           : { Switch theSwitch ->
                            getFlowHelperV2().randomFlow(getTopology()
                                    .activeSwitches.find { it != theSwitch }, theSwitch)
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
        flowHelperV2.addFlow(flow)

        and: "Immediately remove the flow"
        northbound.deleteFlow(flow.flowId)

        then: "All related switches have no discrepancies in rules"
        Wrappers.wait(WAIT_OFFSET) {
            switches.each {
                def rules = northbound.validateSwitchRules(it.dpId)
                assert rules.excessRules.empty, it
                assert rules.missingRules.empty, it
                assert rules.properRules.findAll { !Cookie.isDefaultRule(it) }.empty, it
            }
        }
    }

    @Unroll
    def "Unable to create a flow on an isl port in case port is occupied on a #data.switchType switch"() {
        given: "An isl"
        Isl isl = topology.islsForActiveSwitches.find { it.aswitch && it.dstSwitch }
        assumeTrue("Unable to find required isl", isl as boolean)

        when: "Try to create a flow using isl port"
        def flow = flowHelperV2.randomFlow(isl.srcSwitch, isl.dstSwitch)
        flow."$data.switchType".portNumber = isl."$data.port"
        flowHelperV2.addFlow(flow)

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

    def "Unable to create a flow on an isl port when ISL status is FAILED"() {
        given: "An inactive isl with failed state"
        Isl isl = topology.islsForActiveSwitches.find { it.aswitch && it.dstSwitch }
        assumeTrue("Unable to find required isl", isl as boolean)
        antiflap.portDown(isl.srcSwitch.dpId, isl.srcPort)
        islUtils.waitForIslStatus([isl, isl.reversed], FAILED)

        when: "Try to create a flow using ISL src port"
        def flow = flowHelperV2.randomFlow(isl.srcSwitch, isl.dstSwitch)
        flow.source.portNumber = isl.srcPort
        flowHelperV2.addFlow(flow)

        then: "Flow is not created"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                getPortViolationError("create", isl.srcPort, isl.srcSwitch.dpId)

        and: "Cleanup: Restore state of the ISL"
        antiflap.portUp(isl.srcSwitch.dpId, isl.srcPort)
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
        def flow = flowHelperV2.randomFlow(isl.srcSwitch, isl.dstSwitch)
        flow.source.portNumber = isl.srcPort
        flowHelperV2.addFlow(flow)

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

    def "Able to CRUD unmetered one-switch pinned flow"() {
        when: "Create a flow"
        def sw = topology.getActiveSwitches().first()
        def flow = flowHelperV2.singleSwitchFlow(sw)
        flow.maximumBandwidth = 0
        flow.ignoreBandwidth = true
        flow.pinned = true
        flowHelperV2.addFlow(flow)

        then: "Pinned flow is created"
        def flowInfo = northbound.getFlow(flow.flowId)
        flowInfo.pinned

        when: "Update the flow (pinned=false)"
        northbound.updateFlow(flowInfo.id, flowInfo.tap { it.pinned = false })

        then: "The pinned option is disabled"
        def newFlowInfo = northbound.getFlow(flow.flowId)
        !newFlowInfo.pinned
        flowInfo.lastUpdated < newFlowInfo.lastUpdated

        and: "Cleanup: Delete the flow"
        flowHelper.deleteFlow(flow.flowId)
    }

    @Unroll
    def "Able to CRUD pinned flow"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flow.pinned = true
        flowHelperV2.addFlow(flow)

        then: "Pinned flow is created"
        def flowInfo = northbound.getFlow(flow.flowId)
        flowInfo.pinned

        when: "Update the flow (pinned=false)"
        northbound.updateFlow(flowInfo.id, flowInfo.tap { it.pinned = false })

        then: "The pinned option is disabled"
        def newFlowInfo = northbound.getFlow(flow.flowId)
        !newFlowInfo.pinned
        flowInfo.lastUpdated < newFlowInfo.lastUpdated

        and: "Cleanup: Delete the flow"
        flowHelper.deleteFlow(flow.flowId)
    }

    def "System doesn't allow to create a one-switch flow on a DEACTIVATED switch"() {
        given: "Disconnected switch"
        def sw = topology.getActiveSwitches()[0]
        def swIsls = topology.getRelatedIsls(sw)
        lockKeeper.knockoutSwitch(sw)
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getSwitch(sw.dpId).state == SwitchChangeType.DEACTIVATED
        }

        when: "Try to create a one-switch flow on a deactivated switch"
        def flow = flowHelperV2.singleSwitchFlow(sw)
        northboundV2.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage == "Could not create flow: Source switch $sw.dpId and " +
                "Destination switch $sw.dpId are not connected to the controller"

        and: "Cleanup: Connect switch back to the controller"
        lockKeeper.reviveSwitch(sw)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getSwitch(sw.dpId).state == SwitchChangeType.ACTIVATED
            def links = northbound.getAllLinks()
            swIsls.each { assert islUtils.getIslInfo(links, it).get().state == IslChangeType.DISCOVERED }
        }
    }

    def "System allows to CRUD protected flow"() {
        given: "Two active not neighboring switches with two diverse paths at least"
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().find {
            it.paths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size() >= 2
        } ?: assumeTrue("No suiting switches found", false)

        when: "Create flow with protected path"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.allocateProtectedPath = true
        flowHelperV2.addFlow(flow)

        then: "Flow is created with protected path"
        def flowPathInfo = northbound.getFlowPath(flow.flowId)
        flowPathInfo.protectedPath
        def flowInfo = northbound.getFlow(flow.flowId)
        flowInfo.flowStatusDetails

        and: "Rules for main and protected paths are created"
        Wrappers.wait(WAIT_OFFSET) { flowHelper.verifyRulesOnProtectedFlow(flow.flowId) }

        and: "Validation of flow must be successful"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.discrepancies.empty }

        when: "Update flow: disable protected path(allocateProtectedPath=false)"
        def flowInfoFromDb = database.getFlow(flow.flowId)
        def protectedForwardCookie = flowInfoFromDb.protectedForwardPath.cookie.value
        def protectedReverseCookie = flowInfoFromDb.protectedReversePath.cookie.value
        def protectedFlowPath = northbound.getFlowPath(flow.flowId).protectedPath.forwardPath
        northbound.updateFlow(flowInfo.id, flowInfo.tap { it.allocateProtectedPath = false })

        then: "Protected path is disabled"
        !northbound.getFlowPath(flow.flowId).protectedPath
        !northbound.getFlow(flow.flowId).flowStatusDetails

        and: "Rules for protected path are deleted"
        Wrappers.wait(WAIT_OFFSET) {
            protectedFlowPath.each { sw ->
                def rules = northbound.getSwitchRules(sw.switchId).flowEntries.findAll {
                    !Cookie.isDefaultRule(it.cookie)
                }
                assert rules.every { it != protectedForwardCookie && it != protectedReverseCookie }
            }
        }

        and: "Cleanup: delete the flow"
        flowHelper.deleteFlow(flow.flowId)
    }

    def "System allows to create diverse flows"() {
        given: "Two active not neighboring switches with three diverse paths at least"
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().find {
            it.paths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size() >= 3
        } ?: assumeTrue("No suiting switches found", false)

        when: "Create three flows with diversity enabled"
        def flow1 = flowHelperV2.randomFlow(switchPair, false)
        def flow2 = flowHelperV2.randomFlow(switchPair, false, [flow1]).tap { it.diverseFlowId = flow1.flowId }
        def flow3 = flowHelperV2.randomFlow(switchPair, false, [flow1, flow2]).tap { it.diverseFlowId = flow2.flowId }
        [flow1, flow2, flow3].each { flowHelperV2.addFlow(it) }

        then: "All flows have diverse flow IDs in response"
        northbound.getFlow(flow1.flowId).diverseWith.sort() == [flow2.flowId, flow3.flowId].sort()
        northbound.getFlow(flow2.flowId).diverseWith.sort() == [flow1.flowId, flow3.flowId].sort()
        northbound.getFlow(flow3.flowId).diverseWith.sort() == [flow1.flowId, flow2.flowId].sort()

        and: "All flows have different paths"
        def allInvolvedIsls = [flow1, flow2, flow3].collectMany {
            pathHelper.getInvolvedIsls(PathHelper.convert(northbound.getFlowPath(it.flowId)))
        }
        allInvolvedIsls.unique(false) == allInvolvedIsls

        and: "Delete flows"
        [flow1, flow2, flow3].each { flowHelper.deleteFlow(it.flowId) }
    }

    def "System allows to set/update description/priority/max-latency for a flow"(){
        given: "Two active neighboring switches"
        def switchPair = topologyHelper.getNeighboringSwitchPair()

        and: "Value for each field"
        def initPriority = 100
        def initMaxLatency = 200
        def initDescription = "test description"
        def initPeriodicPing = true

        when: "Create a flow with predefined values"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.priority = initPriority
        flow.maxLatency = initMaxLatency
        flow.description = initDescription
        flow.periodicPings = initPeriodicPing
        flowHelperV2.addFlow(flow)

        then: "Flow is created with needed values"
        def flowInfo = northbound.getFlow(flow.flowId)
        flowInfo.priority == initPriority
        flowInfo.maxLatency == initMaxLatency
        flowInfo.description == initDescription
        flowInfo.periodicPings == initPeriodicPing

        when: "Update predefined values"
        def newPriority = 200
        def newMaxLatency = 300
        def newDescription = "test description updated"
        def newPeriodicPing = false
        flowInfo.priority = newPriority
        flowInfo.maxLatency = newMaxLatency
        flowInfo.description = newDescription
        flowInfo.periodicPings = newPeriodicPing
        northbound.updateFlow(flowInfo.id, flowInfo)

        then: "Flow is updated correctly"
        def newFlowInfo = northbound.getFlow(flow.flowId)
        newFlowInfo.priority == newPriority
        newFlowInfo.maxLatency == newMaxLatency
        newFlowInfo.description == newDescription
        newFlowInfo.periodicPings == newPeriodicPing

        and: "Cleanup: Delete the flow"
        flowHelper.deleteFlow(flow.flowId)
    }

    def "Systems allows to pass traffic via default and vlan flow when they are on the same port"() {
        given: "At least 3 traffGen switches"
        def allTraffGenSwitches = topology.activeTraffGens*.switchConnected
        assumeTrue("Unable to find required switches in topology", (allTraffGenSwitches.size() > 2) as boolean)

        when: "Create a vlan flow"
        def (Switch srcSwitch, Switch dstSwitch) = allTraffGenSwitches
        def bandwidth = 100
        def vlanFlow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        vlanFlow.maximumBandwidth = bandwidth
        vlanFlow.allocateProtectedPath = true
        flowHelperV2.addFlow(vlanFlow)

        and: "Create a default flow with the same srcSwitch and different dstSwitch"
        Switch newDstSwitch = allTraffGenSwitches.find { it != dstSwitch && it != srcSwitch }
        def defaultFlow = flowHelperV2.randomFlow(srcSwitch, newDstSwitch)
        defaultFlow.maximumBandwidth = bandwidth
        defaultFlow.source.vlanId = 0
        defaultFlow.destination.vlanId = 0
        defaultFlow.allocateProtectedPath = true
        flowHelperV2.addFlow(defaultFlow)

        then: "The default flow has less priority than the vlan flow"
        def flowVlanPortInfo = database.getFlow(vlanFlow.flowId)
        def flowFullPortInfo = database.getFlow(defaultFlow.flowId)

        def rules = [srcSwitch.dpId, dstSwitch.dpId, newDstSwitch.dpId].collectEntries {
            [(it): northbound.getSwitchRules(it).flowEntries]
        }

        // can't be imported safely org.openkilda.floodlight.switchmanager.SwitchManager.DEFAULT_FLOW_PRIORITY
        def FLOW_PRIORITY = 24576
        def DEFAULT_FLOW_PRIORITY = FLOW_PRIORITY - 1

        [srcSwitch.dpId, dstSwitch.dpId].each { sw ->
            [flowVlanPortInfo.forwardPath.cookie.value, flowVlanPortInfo.reversePath.cookie.value].each { cookie ->
                assert rules[sw].find { it.cookie == cookie }.priority == FLOW_PRIORITY
            }
        }
        // DEFAULT_FLOW_PRIORITY sets on an ingress rule only
        rules[srcSwitch.dpId].find { it.cookie == flowFullPortInfo.reversePath.cookie.value }.priority == FLOW_PRIORITY
        rules[newDstSwitch.dpId].find {
            it.cookie == flowFullPortInfo.forwardPath.cookie.value
        }.priority == FLOW_PRIORITY

        rules[srcSwitch.dpId].find {
            it.cookie == flowFullPortInfo.forwardPath.cookie.value
        }.priority == DEFAULT_FLOW_PRIORITY
        rules[newDstSwitch.dpId].find {
            it.cookie == flowFullPortInfo.reversePath.cookie.value
        }.priority == DEFAULT_FLOW_PRIORITY

        and: "System allows traffic on the vlan flow"
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(
                toFlowPayload(vlanFlow), bandwidth
        )
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        and: "System allows traffic on the default flow"
        def exam2 = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(
                toFlowPayload(defaultFlow), 1000
        )
        withPool {
            [exam2.forward, exam2.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        and: "Cleanup: Delete the flows"
        [vlanFlow, defaultFlow].each { flow -> flowHelper.deleteFlow(flow.flowId) }
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
                    flow       : flowHelperV2.randomFlow(switchPair)
            ]
            r << [
                    description: "flow without transit switch and without vlans",
                    flow       : flowHelperV2.randomFlow(switchPair).tap {
                        it.source.vlanId = 0
                        it.destination.vlanId = 0
                    }
            ]
            r << [
                    description: "flow without transit switch and vlan only on src",
                    flow       : flowHelperV2.randomFlow(switchPair).tap { it.destination.vlanId = 0 }
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
                    flow       : flowHelperV2.randomFlow(switchPair)
            ]
            r << [
                    description: "flow with transit switch and no vlans",
                    flow       : flowHelperV2.randomFlow(switchPair).tap {
                        it.source.vlanId = 0
                        it.destination.vlanId = 0
                    }
            ]
            r << [
                    description: "flow with transit switch and vlan only on dst",
                    flow       : flowHelperV2.randomFlow(switchPair).tap { it.source.vlanId = 0 }
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
                    flow       : flowHelperV2.singleSwitchFlow(sw).tap {
                        it.source.vlanId = 0
                        it.destination.vlanId = 0
                    }
            ]
            r << [
                    description: "single-switch flow with vlan only on dst",
                    flow       : flowHelperV2.singleSwitchFlow(sw).tap {
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
                .source(new FlowEndpointPayload(source.switchId, source.portNumber, source.vlanId,
                        new DetectConnectedDevicesPayload(false, false)))
                .destination(new FlowEndpointPayload(destination.switchId, destination.portNumber, destination.vlanId,
                        new DetectConnectedDevicesPayload(false, false)))
                .maximumBandwidth(flow.maximumBandwidth)
                .ignoreBandwidth(flow.ignoreBandwidth)
                .build()
    }

    def getConflictingData() {
        [
                [
                        conflict            : "the same vlans on the same port on src switch",
                        makeFlowsConflicting: { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict ->
                            flowToConflict.source.portNumber = dominantFlow.source.portNumber
                            flowToConflict.source.vlanId = dominantFlow.source.vlanId
                        },
                        getError            : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict,
                                                String operation = "create" ->
                            errorMessage(operation, dominantFlow, "source", flowToConflict, "source")
                        }
                ],
                [
                        conflict            : "the same vlans on the same port on dst switch",
                        makeFlowsConflicting: { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict ->
                            flowToConflict.destination.portNumber = dominantFlow.destination.portNumber
                            flowToConflict.destination.vlanId = dominantFlow.destination.vlanId
                        },
                        getError            : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict,
                                                String operation = "create" ->
                            errorMessage(operation, dominantFlow, "destination", flowToConflict, "destination")
                        }
                ],
                [
                        conflict            : "no vlan, both flows are on the same port on src switch",
                        makeFlowsConflicting: { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict ->
                            flowToConflict.source.portNumber = dominantFlow.source.portNumber
                            flowToConflict.source.vlanId = 0
                            dominantFlow.source.vlanId = 0
                        },
                        getError            : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict,
                                                String operation = "create" ->
                            errorMessage(operation, dominantFlow, "source", flowToConflict, "source")
                        }
                ],
                [
                        conflict            : "no vlan, both flows are on the same port on dst switch",
                        makeFlowsConflicting: { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict ->
                            flowToConflict.destination.portNumber = dominantFlow.destination.portNumber
                            flowToConflict.destination.vlanId = 0
                            dominantFlow.destination.vlanId = 0
                        },
                        getError            : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict,
                                                String operation = "create" ->
                            errorMessage(operation, dominantFlow, "destination", flowToConflict, "destination")
                        }
                ]
        ]
    }
}
