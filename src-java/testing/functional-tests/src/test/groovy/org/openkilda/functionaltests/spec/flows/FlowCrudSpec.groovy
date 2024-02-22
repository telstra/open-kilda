package org.openkilda.functionaltests.spec.flows

import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL

import groovy.util.logging.Slf4j
import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowNotCreatedExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotCreatedWithConflictExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotCreatedWithMissingPathExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotDeletedExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotUpdatedExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotUpdatedWithMissingPathExpectedError
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.IterationTags
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.DetectConnectedDevicesPayload
import org.openkilda.messaging.payload.flow.FlowEndpointPayload
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.model.PathComputationStrategy
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.northbound.dto.v1.flows.PingInput
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.flows.FlowPatchEndpoint
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2
import org.openkilda.northbound.dto.v2.flows.FlowResponseV2
import org.openkilda.northbound.dto.v2.flows.FlowStatistics
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.FlowNotApplicableException
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.tools.FlowTrafficExamBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared
import spock.lang.Unroll

import javax.inject.Provider

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.Wrappers.timedLoop
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.messaging.info.event.IslChangeType.MOVED
import static org.openkilda.messaging.payload.flow.FlowState.IN_PROGRESS
import static org.openkilda.messaging.payload.flow.FlowState.UP
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN
import static org.openkilda.model.FlowEncapsulationType.VXLAN
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

@Slf4j
@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/hub-and-spoke/crud")
@Narrative(""""Verify CRUD operations and health of basic vlan flows on different types of switches.
More specific cases like partialUpdate/protected/diverse etc. are covered in separate specifications
""")
class FlowCrudSpec extends HealthCheckSpecification {

    final static Integer IMPOSSIBLY_LOW_LATENCY = 1
    final static Long IMPOSSIBLY_HIGH_BANDWIDTH = Long.MAX_VALUE
    final static FlowStatistics FLOW_STATISTICS_CAUSING_ERROR =
            new FlowStatistics([[4096, 0].shuffled().first(), 2001] as Set)
    @Autowired
    @Shared
    Provider<TraffExamService> traffExamProvider

    @Shared
    def getPortViolationErrorDescriptionPattern = { String endpoint, int port, SwitchId swId ->
        ~/The port $port on the switch \'$swId\' is occupied by an ISL \($endpoint endpoint collision\)./
    }

    @Tags([TOPOLOGY_DEPENDENT])
    @IterationTags([
            @IterationTag(tags = [SMOKE], iterationNameRegex = /vlan /),
            @IterationTag(tags = [SMOKE_SWITCHES],
                    iterationNameRegex = /random vlans|no vlans|single-switch flow with vlans/),
            @IterationTag(tags = [LOW_PRIORITY], iterationNameRegex = /and vlan only on/)
    ])
    def "Valid #data.description has traffic and no rule discrepancies [#srcDstStr]"() {
        given: "A flow"
        assumeTrue(topology.activeTraffGens.size() >= 2,
                "There should be at least two active traffgens for test execution")
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
        switchHelper.synchronizeAndCollectFixedDiscrepancies(switches*.getDpId()).isEmpty()


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
        flowHelperV2.deleteFlow(flow.flowId)

        then: "The flow is not present in NB"
        !northboundV2.getAllFlows().find { it.flowId == flow.flowId }
        def flowIsDeleted = true

        and: "ISL bandwidth is restored"
        wait(WAIT_OFFSET) {
            def allLinksInfoAfter = northbound.getAllLinks().collectEntries { [it.id, it.availableBandwidth] }.sort()
            assert allLinksInfoBefore == allLinksInfoAfter
        }

        and: "No rule discrepancies on every switch of the flow"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(switches*.getDpId()).isEmpty()

        cleanup:
        !flowIsDeleted && flow && flowHelperV2.deleteFlow(flow.flowId)

        where:
        /*Some permutations may be missed, since at current implementation we only take 'direct' possible flows
        * without modifying the costs of ISLs.
        * I.e. if potential test case with transit switch between certain pair of unique switches will require change of
        * costs on ISLs (those switches are neighbors, but we want a path with transit switch between them), then
        * we will not test such case
        */
        data << flowsWithoutTransitSwitch + flowsWithTransitSwitch + singleSwitchFlows
        flow = data.flow as FlowRequestV2
        srcDstStr = "src:${topology.find(flow.source.switchId).hwSwString}->dst:${topology.find(flow.destination.switchId).hwSwString}"
    }

    @Unroll("Able to create a second flow if #data.description")
    def "Able to create multiple flows on certain combinations of switch-port-vlans"() {
        given: "Two potential flows that should not conflict"
        Tuple2<FlowRequestV2, FlowRequestV2> flows = data.getNotConflictingFlows()

        when: "Create the first flow"
        flowHelperV2.addFlow(flows.v1)

        and: "Try creating a second flow with #data.description"
        flowHelperV2.addFlow(flows.v2)

        then: "Both flows are successfully created"
        northboundV2.getAllFlows()*.flowId.containsAll(flows*.flowId)

        cleanup: "Delete flows"
        flows.each { it && flowHelperV2.deleteFlow(it.flowId) }

        where:
        data << [
                [
                        description           : "same switch-port but vlans on src and dst are swapped",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def flow1 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch).tap {
                                it.source.vlanId == it.destination.vlanId && it.destination.vlanId--
                            }
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
                        description           : "same switch-port but vlans on src and dst are different",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def flow1 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch)
                            def flow2 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch).tap {
                                it.source.portNumber = flow1.source.portNumber
                                it.source.vlanId = flow1.source.vlanId - 1
                                it.destination.portNumber = flow1.destination.portNumber
                                it.destination.vlanId = flow1.destination.vlanId - 1
                            }
                            return new Tuple2<FlowRequestV2, FlowRequestV2>(flow1, flow2)
                        }
                ],
                [
                        description           : "vlan-port of new src = vlan-port of existing dst (+ different src)",
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
                                it.destination.vlanId = flow1.destination.vlanId - 1 //ensure no conflict on dst
                            }
                            return new Tuple2<FlowRequestV2, FlowRequestV2>(flow1, flow2)
                        }
                ],
                [
                        description           : "vlan-port of new dst = vlan-port of existing src (but different switches)",
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
                        description           : "vlan of new dst = vlan of existing src and port of new dst = port of " +
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
                ],
                [
                        description           : "default and tagged flows on the same port on dst switch",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def flow1 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch)
                            def flow2 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch).tap {
                                it.destination.vlanId = 0
                                it.destination.portNumber = flow1.destination.portNumber
                            }
                            return new Tuple2<FlowRequestV2, FlowRequestV2>(flow1, flow2)
                        }
                ],
                [
                        description           : "default and tagged flows on the same port on src switch",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def flow1 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch)
                            def flow2 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch).tap {
                                it.source.vlanId = 0
                                it.source.portNumber = flow1.source.portNumber
                            }
                            return new Tuple2<FlowRequestV2, FlowRequestV2>(flow1, flow2)
                        }
                ],
                [
                        description           : "tagged and default flows on the same port on dst switch",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def flow1 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch).tap {
                                it.destination.vlanId = 0
                            }
                            def flow2 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch).tap {
                                it.destination.portNumber = flow1.destination.portNumber
                            }
                            return new Tuple2<FlowRequestV2, FlowRequestV2>(flow1, flow2)
                        }
                ],
                [
                        description           : "tagged and default flows on the same port on src switch",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def flow1 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch).tap {
                                it.source.vlanId = 0
                            }
                            def flow2 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch).tap {
                                it.source.portNumber = flow1.source.portNumber
                            }
                            return new Tuple2<FlowRequestV2, FlowRequestV2>(flow1, flow2)
                        }
                ],
                [
                        description           : "default and tagged flows on the same ports on src and dst switches",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def flow1 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch)
                            def flow2 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch).tap {
                                it.source.vlanId = 0
                                it.source.portNumber = flow1.source.portNumber
                                it.destination.vlanId = 0
                                it.destination.portNumber = flow1.destination.portNumber
                            }
                            return new Tuple2<FlowRequestV2, FlowRequestV2>(flow1, flow2)
                        }
                ],
                [
                        description           : "tagged and default flows on the same ports on src and dst switches",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def flow1 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch).tap {
                                it.source.vlanId = 0
                                it.destination.vlanId = 0
                            }
                            def flow2 = getFlowHelperV2().randomFlow(srcSwitch, dstSwitch).tap {
                                it.source.portNumber = flow1.source.portNumber
                                it.destination.portNumber = flow1.destination.portNumber
                            }
                            return new Tuple2<FlowRequestV2, FlowRequestV2>(flow1, flow2)
                        }
                ]
        ]
    }

    @Tags([TOPOLOGY_DEPENDENT, SMOKE_SWITCHES])
    def "Able to create single switch single port flow with different vlan (#flow.source.switchId)"(
            FlowRequestV2 flow) {
        given: "A flow"
        flowHelperV2.addFlow(flow)

        expect: "No rule discrepancies on the switch"
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(flow.source.switchId).isPresent()

        and: "No discrepancies when doing flow validation"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        when: "Remove the flow"
        flowHelperV2.deleteFlow(flow.flowId)

        then: "The flow is not present in NB"
        !northboundV2.getAllFlows().find { it.flowId == flow.flowId }
        def flowIsDeleted = true

        and: "No rule discrepancies on the switch after delete"
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(flow.source.switchId).isPresent()

        cleanup:
        !flowIsDeleted && flowHelperV2.deleteFlow(flow.flowId)

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

        cleanup: "Delete the flow"
        flowHelperV2.deleteFlow(flow.flowId)
    }

    def "Unable to create single-switch flow with the same ports and vlans on both sides"() {
        given: "Potential single-switch flow with the same ports and vlans on both sides"
        def flow = flowHelperV2.singleSwitchSinglePortFlow(topology.activeSwitches.first())
        flow.destination.vlanId = flow.source.vlanId

        when: "Try creating such flow"
        flowHelperV2.addFlow(flow)

        then: "Error is returned, stating a readable reason"
        def error = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(
                ~/It is not allowed to create one-switch flow for the same ports and VLANs/).matches(error)

        cleanup:
        !error && flowHelperV2.deleteFlow(flow.flowId)
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
        new FlowNotCreatedWithConflictExpectedError(~/${data.getErrorDescription(flow, conflictingFlow)}/).matches(error)

        cleanup: "Delete the dominant flow"
        flowHelperV2.deleteFlow(flow.flowId)
        !error && flowHelperV2.deleteFlow(conflictingFlow.flowId)

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
        }.flatten() ?: assumeTrue(false, "No suiting active neighboring switches " +
                "with two possible flow paths at least and different number of hops found")

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

        cleanup: "Delete the flow and reset costs"
        flow && flowHelperV2.deleteFlow(flow.flowId)
        database.resetCosts(topology.isls)
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Error is returned if there is no available path to #data.isolatedSwitchType switch"() {
        given: "A switch that has no connection to other switches"
        def isolatedSwitch = switchPairs.all().nonNeighbouring().random().src
        def flow = data.getFlow(isolatedSwitch)
        topology.getBusyPortsForSwitch(isolatedSwitch).each { port ->
            antiflap.portDown(isolatedSwitch.dpId, port)
        }
        //wait until ISLs are actually got failed
        wait(WAIT_OFFSET) {
            def islData = northbound.getAllLinks()
            topology.getRelatedIsls(isolatedSwitch).each {
                assert islUtils.getIslInfo(islData, it).get().state == FAILED
            }
        }

        when: "Try building a flow using the isolated switch"
        flowHelperV2.addFlow(flow)

        then: "Error is returned, stating that there is no path found for such flow"
        def error = thrown(HttpClientErrorException)
        new FlowNotCreatedWithMissingPathExpectedError(
                ~/Switch ${isolatedSwitch.getDpId()} doesn\'t have links with enough bandwidth/).matches(error)

        cleanup: "Restore connection to the isolated switch and reset costs"
        !error && flowHelperV2.deleteFlow(flow.flowId)
        topology.getBusyPortsForSwitch(isolatedSwitch).each { port ->
            antiflap.portUp(isolatedSwitch.dpId, port)
        }
        wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state == DISCOVERED }
        }
        database.resetCosts(topology.isls)

        where:
        data << [
                [
                        isolatedSwitchType: "source",
                        getFlow           : { Switch theSwitch ->
                            getFlowHelperV2().randomFlow(switchPairs.all()
                                    .nonNeighbouring()
                                    .includeSourceSwitch(theSwitch)
                                    .random())
                        }
                ],
                [
                        isolatedSwitchType: "destination",
                        getFlow           : { Switch theSwitch ->
                            getFlowHelperV2().randomFlow(switchPairs.all()
                                    .nonNeighbouring()
                                    .includeSourceSwitch(theSwitch)
                                    .random()
                                    .getReversed())
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
        flowHelperV2.addFlow(flow, IN_PROGRESS)

        and: "Immediately remove the flow"
        northboundV2.deleteFlow(flow.flowId)

        then: "System returns error as being unable to remove in progress flow"
        def e = thrown(HttpClientErrorException)
        new FlowNotDeletedExpectedError(~/Flow ${flow.getFlowId()} is in progress now/).matches(e)
        and: "Flow is not removed"
        northboundV2.getAllFlows()*.flowId.contains(flow.flowId)

        and: "Flow eventually gets into UP state"
        wait(WAIT_OFFSET) {
            assert northboundV2.getFlowStatus(flow.flowId).status == UP
        }


        and: "All related switches have no discrepancies in rules"
        switches.each {
            def syncResult = switchHelper.synchronize(it.getDpId())
            with (syncResult) {
                assert [rules.installed, rules.removed, meters.installed, meters.removed].every {it.empty}
            }
            def swProps = switchHelper.getCachedSwProps(it.dpId)
            def amountOfServer42Rules = (swProps.server42FlowRtt && it.dpId in [srcSwitch.dpId, dstSwitch.dpId]) ? 1 : 0
            if (swProps.server42FlowRtt) {
                if ((flow.destination.getSwitchId() == it.dpId && flow.destination.vlanId) || (flow.source.getSwitchId() == it.dpId && flow.source.vlanId))
                    amountOfServer42Rules += 1
            }
            def amountOfFlowRules = 3 + amountOfServer42Rules
            assert syncResult.rules.proper.findAll { !new Cookie(it).serviceFlag }
                    .size() == amountOfFlowRules
        }

        cleanup: "Remove the flow"
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }

    def "Unable to create a flow with #problem"() {
        given: "A flow with #problem"
        def switchPair = switchPairs.all().nonNeighbouring().random()
        def flow = flowHelperV2.randomFlow(switchPair, false)
        flow = update(flow)
        when: "Try to create a flow"
        flowHelperV2.addFlow(flow)

        then: "Flow is not created"
        def actualException = thrown(HttpClientErrorException)
        expectedException.matches(actualException)
        cleanup:
        !actualException && flowHelperV2.deleteFlow(flow.flowId)

        where:
        problem                      | update                                                              | expectedException
        "invalid encapsulation type" |
                { FlowRequestV2 flowToSpoil ->
                    flowToSpoil.setEncapsulationType("fake")
                    return flowToSpoil
                }                                                                                          |
                new FlowNotCreatedExpectedError(
                        "No enum constant org.openkilda.messaging.payload.flow.FlowEncapsulationType.FAKE",
                        ~/Can not parse arguments of the create flow request/)
        "unavailable latency"        |
                { FlowRequestV2 flowToSpoil ->
                    flowToSpoil.setMaxLatency(IMPOSSIBLY_LOW_LATENCY)
                    flowToSpoil.setPathComputationStrategy(PathComputationStrategy.MAX_LATENCY.toString())
                    return flowToSpoil
                }                                                                                          |
                new FlowNotCreatedWithMissingPathExpectedError(
                        ~/Latency limit\: Requested path must have latency ${IMPOSSIBLY_LOW_LATENCY}ms or lower/)
        "invalid statistics vlan number" |
                { FlowRequestV2 flowToSpoil ->
                    flowToSpoil.setStatistics(FLOW_STATISTICS_CAUSING_ERROR)
                    def source = flowToSpoil.getSource()
                    source.setVlanId(0)
                    flowToSpoil.setSource(source)
                    return flowToSpoil
                }                                                                                          |
                new FlowNotCreatedExpectedError(~/To collect vlan statistics, the vlan IDs must be from 1 up to 4095/)
    }

    @Tags([LOW_PRIORITY])
    def "Unable to update to a flow with #problem"() {
        given: "A flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelperV2.addFlow(flowHelperV2.randomFlow(srcSwitch, dstSwitch))

        when: "Try to update the flow "
        northboundV2.updateFlow(flow.getFlowId(),
                flowHelperV2.toRequest(flow.tap(update)))

        then: "Flow is not updated"
        def actualError = thrown(HttpClientErrorException)
        expectedError.matches(actualError)

        cleanup: "Remove the flow"
        Wrappers.silent { flowHelperV2.deleteFlow(flow.flowId) }

        where:
        problem | update | expectedError
        "unavailable bandwidth" |
                { FlowResponseV2 flowResponseV2 -> flowResponseV2.maximumBandwidth = IMPOSSIBLY_HIGH_BANDWIDTH } |
                new FlowNotUpdatedWithMissingPathExpectedError(~/Not enough bandwidth or no path found. \
Switch .* doesn't have links with enough bandwidth, \
Failed to find path with requested bandwidth=${IMPOSSIBLY_HIGH_BANDWIDTH}/)
        "vlan id is above 4095" |
                {FlowResponseV2 flowResponseV2 -> flowResponseV2.source =
                        flowResponseV2.getSource().tap {it.vlanId = 4096}}|
        new FlowNotUpdatedExpectedError(~/Errors: VlanId must be less than 4096/)

    }

    @Tags([LOW_PRIORITY])
    def "Unable to partially update to a flow with statistics vlan set to 0 or above 4095"() {
        given: "A flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flowRequest = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flowRequest.destination.vlanId = 0
        def flow = flowHelperV2.addFlow(flowRequest)

        when: "Try to partially update the flow"
        def partialUpdateRequest =
        northboundV2.partialUpdate(flow.getFlowId(),
                new FlowPatchV2().tap {it.statistics = FLOW_STATISTICS_CAUSING_ERROR})

        then: "Flow is not updated"
        def actualException = thrown(HttpClientErrorException)
        new FlowNotUpdatedExpectedError(~/To collect vlan statistics, the vlan IDs must be from 1 up to 4095/)
                .matches(actualException)

        cleanup: "Remove the flow"
        Wrappers.silent { flowHelperV2.deleteFlow(flow.flowId) }
    }

    def "Unable to create a flow on an isl port in case port is occupied on a #data.switchType switch"() {
        given: "An isl"
        Isl isl = topology.islsForActiveSwitches.find { it.aswitch && it.dstSwitch }
        assumeTrue(isl as boolean, "Unable to find required isl")

        when: "Try to create a flow using isl port"
        def flow = flowHelperV2.randomFlow(isl.srcSwitch, isl.dstSwitch)
        flow."$data.switchType".portNumber = isl."$data.port"
        flowHelperV2.addFlow(flow)

        then: "Flow is not created"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(data.errorDescription(isl)).matches(exc)
        cleanup:
        !exc && flowHelperV2.deleteFlow(flow.flowId)

        where:
        data << [
                [
                        switchType      : "source",
                        port            : "srcPort",
                        errorDescription: { Isl violatedIsl ->
                            getPortViolationErrorDescriptionPattern("source", violatedIsl.srcPort, violatedIsl.srcSwitch.dpId)
                        }
                ],
                [
                        switchType      : "destination",
                        port            : "dstPort",
                        errorDescription: { Isl violatedIsl ->
                            getPortViolationErrorDescriptionPattern("destination", violatedIsl.dstPort, violatedIsl.dstSwitch.dpId)
                        }
                ]
        ]
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Unable to create a flow on an isl port when ISL status is FAILED"() {
        given: "An inactive isl with failed state"
        Isl isl = topology.islsForActiveSwitches.find { it.aswitch && it.dstSwitch }
        assumeTrue(isl as boolean, "Unable to find required isl")
        antiflap.portDown(isl.srcSwitch.dpId, isl.srcPort)
        islUtils.waitForIslStatus([isl, isl.reversed], FAILED)

        when: "Try to create a flow using ISL src port"
        def flow = flowHelperV2.randomFlow(isl.srcSwitch, isl.dstSwitch)
        flow.source.portNumber = isl.srcPort
        flowHelperV2.addFlow(flow)

        then: "Flow is not created"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(
                getPortViolationErrorDescriptionPattern("source", isl.srcPort, isl.srcSwitch.dpId)).matches(exc)

        cleanup: "Restore state of the ISL"
        !exc && flow && flowHelperV2.deleteFlow(flow.flowId)
        antiflap.portUp(isl.srcSwitch.dpId, isl.srcPort)
        islUtils.waitForIslStatus([isl, isl.reversed], DISCOVERED)
        database.resetCosts(topology.isls)
    }

    def "Unable to create a flow on an isl port when ISL status is MOVED"() {
        given: "An inactive isl with moved state"
        Isl isl = topology.islsForActiveSwitches.find { it.aswitch && it.dstSwitch }
        assumeTrue(isl as boolean, "Unable to find required isl")
        def notConnectedIsls = topology.notConnectedIsls
        assumeTrue(notConnectedIsls.size() > 0, "Unable to find non-connected isl")
        def notConnectedIsl = notConnectedIsls.first()
        def newIsl = islUtils.replug(isl, false, notConnectedIsl, true, false)

        islUtils.waitForIslStatus([isl, isl.reversed], MOVED)
        wait(discoveryExhaustedInterval + WAIT_OFFSET) {
            [newIsl, newIsl.reversed].each { assert northbound.getLink(it).state == DISCOVERED }
        }
        def islIsMoved = true

        when: "Try to create a flow using ISL src port"
        def flow = flowHelperV2.randomFlow(isl.srcSwitch, isl.dstSwitch)
        flow.source.portNumber = isl.srcPort
        flowHelperV2.addFlow(flow)

        then: "Flow is not created"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(
                getPortViolationErrorDescriptionPattern("source", isl.srcPort, isl.srcSwitch.dpId)).matches(exc)
        cleanup: "Restore status of the ISL and delete new created ISL"
        if (islIsMoved) {
            islUtils.replug(newIsl, true, isl, false, false)
            islUtils.waitForIslStatus([isl, isl.reversed], DISCOVERED)
            islUtils.waitForIslStatus([newIsl, newIsl.reversed], MOVED)
            northbound.deleteLink(islUtils.toLinkParameters(newIsl))
            wait(WAIT_OFFSET) { assert !islUtils.getIslInfo(newIsl).isPresent() }
        }
        database.resetCosts(topology.isls)
    }

    @Tags(SWITCH_RECOVER_ON_FAIL)
    def "System doesn't allow to create a one-switch flow on a DEACTIVATED switch"() {
        given: "Disconnected switch"
        def sw = topology.getActiveSwitches()[0]
        def blockData = switchHelper.knockoutSwitch(sw, RW)

        when: "Try to create a one-switch flow on a deactivated switch"
        def flow = flowHelperV2.singleSwitchFlow(sw)
        flowHelperV2.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(
                ~/Source switch $sw.dpId and Destination switch $sw.dpId are not connected to the controller/).matches(exc)
        cleanup: "Connect switch back to the controller"
        blockData && switchHelper.reviveSwitch(sw, blockData, true)
        !exc && flowHelperV2.deleteFlow(flow.flowId)
    }

    def "System allows to CRUD protected flow"() {
        given: "Two active not neighboring switches with two diverse paths at least"
        def switchPair = switchPairs.all().nonNeighbouring().withAtLeastNNonOverlappingPaths(2).random()

        when: "Create flow with protected path"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.allocateProtectedPath = true
        flowHelperV2.addFlow(flow)

        then: "Flow is created with protected path"
        def flowPathInfo = northbound.getFlowPath(flow.flowId)
        flowPathInfo.protectedPath
        def flowInfo = northboundV2.getFlow(flow.flowId)
        flowInfo.statusDetails

        and: "Rules for main and protected paths are created"
        wait(WAIT_OFFSET) { flowHelper.verifyRulesOnProtectedFlow(flow.flowId) }

        and: "Validation of flow must be successful"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.discrepancies.empty }

        when: "Update flow: disable protected path(allocateProtectedPath=false)"
        def flowInfoFromDb = database.getFlow(flow.flowId)
        def protectedForwardCookie = flowInfoFromDb.protectedForwardPath.cookie.value
        def protectedReverseCookie = flowInfoFromDb.protectedReversePath.cookie.value
        def protectedFlowPath = northbound.getFlowPath(flow.flowId).protectedPath.forwardPath
        northboundV2.updateFlow(flowInfo.flowId, flowHelperV2.toRequest(flowInfo.tap { it.allocateProtectedPath = false }))

        then: "Protected path is disabled"
        !northbound.getFlowPath(flow.flowId).protectedPath
        !northboundV2.getFlow(flow.flowId).statusDetails

        and: "Rules for protected path are deleted"
        wait(WAIT_OFFSET) {
            protectedFlowPath.each { sw ->
                def rules = northbound.getSwitchRules(sw.switchId).flowEntries.findAll {
                    !new Cookie(it.cookie).serviceFlag
                }
                assert rules.every { it != protectedForwardCookie && it != protectedReverseCookie }
            }
        }

        cleanup: "Delete the flow"
        flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tags(LOW_PRIORITY)
    def "System allows to set/update description/priority/max-latency for a flow"() {
        given: "Two active neighboring switches"
        def switchPair = switchPairs.all().neighbouring().random()

        and: "Value for each field"
        def initPriority = 100
        def initMaxLatency = 200
        def initMaxLatencyTier2 = 300
        def initDescription = "test description"
        def initPeriodicPing = true

        when: "Create a flow with predefined values"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.priority = initPriority
        flow.maxLatency = initMaxLatency
        flow.maxLatencyTier2 = initMaxLatencyTier2
        flow.description = initDescription
        flow.periodicPings = initPeriodicPing
        flowHelperV2.addFlow(flow)

        then: "Flow is created with needed values"
        def flowInfo = northboundV2.getFlow(flow.flowId)
        flowInfo.priority == initPriority
        flowInfo.maxLatency == initMaxLatency
        flowInfo.maxLatencyTier2 == initMaxLatencyTier2
        flowInfo.description == initDescription
        flowInfo.periodicPings == initPeriodicPing

        when: "Update predefined values"
        def newPriority = 200
        def newMaxLatency = 300
        def newMaxLatencyTier2 = 400
        def newDescription = "test description updated"
        def newPeriodicPing = false
        flowInfo.priority = newPriority
        flowInfo.maxLatency = newMaxLatency
        flowInfo.maxLatencyTier2 = newMaxLatencyTier2
        flowInfo.description = newDescription
        flowInfo.periodicPings = newPeriodicPing
        northboundV2.updateFlow(flowInfo.flowId, flowHelperV2.toRequest(flowInfo))

        then: "Flow is updated correctly"
        def newFlowInfo = northboundV2.getFlow(flow.flowId)
        newFlowInfo.priority == newPriority
        newFlowInfo.maxLatency == newMaxLatency
        newFlowInfo.maxLatencyTier2 == newMaxLatencyTier2
        newFlowInfo.description == newDescription
        newFlowInfo.periodicPings == newPeriodicPing

        cleanup: "Delete the flow"
        flowHelperV2.deleteFlow(flow.flowId)
    }

    def "System doesn't ignore encapsulationType when flow is created with ignoreBandwidth = true"() {
        given: "Two active switches"
        def swPair = switchPairs.all().neighbouring().withBothSwitchesVxLanEnabled().random()

        def initialSrcProps = switchHelper.getCachedSwProps(swPair.src.dpId)
        def initialSupportedEncapsulations = initialSrcProps.getSupportedTransitEncapsulation().collect()
        switchHelper.updateSwitchProperties(swPair.getSrc(), initialSrcProps.tap {
            it.supportedTransitEncapsulation = [TRANSIT_VLAN.toString()]})


        when: "Create a flow with not supported encapsulation type on the switches"
        def flow = flowHelperV2.randomFlow(swPair)
        flow.ignoreBandwidth = true
        flow.maximumBandwidth = 0
        flow.encapsulationType = VXLAN
        flowHelperV2.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(~/Flow\'s source endpoint ${swPair.getSrc().getDpId()} doesn\'t support \
requested encapsulation type $VXLAN. Choose one of the supported encapsulation \
types .* or update switch properties and add needed encapsulation type./).matches(exc)

        cleanup:
        !exc && flowHelperV2.deleteFlow(flow.flowId)
        initialSrcProps && switchHelper.updateSwitchProperties(swPair.getSrc(), initialSrcProps.tap {
            it.supportedTransitEncapsulation = initialSupportedEncapsulations
        })
    }

    def "Flow status accurately represents the actual state of the flow and flow rules"() {
        when: "Create a flow on a long path"
        def swPair = switchPairs.all().random()
        def longPath = swPair.paths.max { it.size() }
        swPair.paths.findAll { it != longPath }.each { pathHelper.makePathMorePreferable(longPath, it) }
        def flow = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow, IN_PROGRESS)

        then: "Flow status is changed to UP only when all rules are actually installed"
        northboundV2.getFlowStatus(flow.flowId).status == IN_PROGRESS
        wait(PATH_INSTALLATION_TIME) {
            assert northboundV2.getFlowStatus(flow.flowId).status == UP
        }
        def flowInfo = database.getFlow(flow.flowId)
        def flowCookies = [flowInfo.forwardPath.cookie.value, flowInfo.reversePath.cookie.value]
        def switches = pathHelper.getInvolvedSwitches(flow.flowId)
        withPool(switches.size()) {
            switches.eachParallel { Switch sw ->
                assert northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.containsAll(flowCookies)
            }
        }

        when: "Delete flow"
        def deleteResponse = northboundV2.deleteFlow(flow.flowId)

        then: "Flow is actually removed from flows dump only after all rules are removed"
        wait(RULES_DELETION_TIME) {
            assert !northboundV2.getFlowStatus(flow.flowId)
        }
        withPool(switches.size()) {
            switches.eachParallel { Switch sw ->
                assert northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.findAll {
                    def cookie = new Cookie(it)
                    cookie.type == CookieType.MULTI_TABLE_INGRESS_RULES || !cookie.serviceFlag
                }.empty
            }
        }
        northboundV2.getAllFlows().empty

        cleanup:
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
        flow && !deleteResponse && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tags(LOW_PRIORITY)
    def "Able to update a flow endpoint"() {
        given: "Three active switches"
        def allSwitches = topology.activeSwitches
        assumeTrue(allSwitches.size() >= 3, "Unable to find three active switches")
        def srcSwitch = allSwitches[0]
        def dstSwitch = allSwitches[1]

        and: "A vlan flow"
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch, false)
        flowHelperV2.addFlow(flow)

        when: "Update the flow: port number and vlan id on the src endpoint"
        def updatedFlow = flow.jacksonCopy().tap {
            it.source.portNumber = topology.getAllowedPortsForSwitch(topology.activeSwitches.find {
                it.dpId == flow.source.switchId
            }).last()
            it.source.vlanId = flow.destination.vlanId + 1
        }
        flowHelperV2.updateFlow(flow.flowId, updatedFlow)

        then: "Flow is really updated"
        with(northboundV2.getFlow(flow.flowId)) {
            it.source.portNumber == updatedFlow.source.portNumber
            it.source.vlanId == updatedFlow.source.vlanId
        }

        and: "Flow history shows actual info into stateBefore and stateAfter sections"
        def flowHistoryEntry = flowHelper.getLatestHistoryEntry(flow.flowId)
        with(flowHistoryEntry.dumps.find { it.type == "stateBefore" }) {
            it.sourcePort == flow.source.portNumber
            it.sourceVlan == flow.source.vlanId
        }
        with(flowHistoryEntry.dumps.find { it.type == "stateAfter" }) {
            it.sourcePort == updatedFlow.source.portNumber
            it.sourceVlan == updatedFlow.source.vlanId
        }

        and: "Flow rules are recreated"
        def flowInfoFromDb2 = database.getFlow(flow.flowId)
        wait(RULES_INSTALLATION_TIME) {
            with(northbound.getSwitchRules(srcSwitch.dpId).flowEntries.findAll {
                !new Cookie(it.cookie).serviceFlag
            }) { rules ->
                rules.findAll {
                    it.cookie in [flowInfoFromDb2.forwardPath.cookie.value, flowInfoFromDb2.reversePath.cookie.value]
                }.size() == 2
                def ingressRule = rules.find { it.cookie == flowInfoFromDb2.forwardPath.cookie.value }
                ingressRule.match.inPort == updatedFlow.source.portNumber.toString()
                //vlan is matched in shared rule
                !ingressRule.match.vlanVid
            }
        }

        and: "Flow is valid and pingable"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }
        with(northbound.pingFlow(flow.flowId, new PingInput())) {
            it.forward.pingSuccess
            it.reverse.pingSuccess
        }

        and: "The src switch passes switch validation"
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(srcSwitch.getDpId()).isPresent()

        when: "Update the flow: switch id on the dst endpoint"
        def newDstSwitch = allSwitches[2]
        flowHelperV2.updateFlow(flow.flowId, flow.tap {
            it.destination.switchId = newDstSwitch.dpId
            it.destination.portNumber = updatedFlow.source.portNumber
        })

        then: "Flow is really updated"
        with(northboundV2.getFlow(flow.flowId)) {
            it.destination.switchId == newDstSwitch.dpId
        }

        and: "Flow history shows actual info into stateBefore and stateAfter sections"
        def flowHistory2 = flowHelper.getLatestHistoryEntry(flow.flowId)
        with(flowHistory2.dumps.find { it.type == "stateBefore" }) {
            it.destinationSwitch == dstSwitch.dpId.toString()
        }
        with(flowHistory2.dumps.find { it.type == "stateAfter" }) {
            it.destinationSwitch == newDstSwitch.dpId.toString()
        }

        and: "Flow rules are removed from the old dst switch"
        def flowInfoFromDb3 = database.getFlow(flow.flowId)
        wait(RULES_DELETION_TIME) {
            with(northbound.getSwitchRules(dstSwitch.dpId).flowEntries.findAll {
                !new Cookie(it.cookie).serviceFlag
            }) { rules ->
                rules.findAll {
                    it.cookie in [flowInfoFromDb2.forwardPath.cookie.value, flowInfoFromDb2.reversePath.cookie.value]
                }.empty
            }
        }

        and: "Flow rules are installed on the new dst switch"
        wait(RULES_INSTALLATION_TIME) {
            with(northbound.getSwitchRules(newDstSwitch.dpId).flowEntries.findAll {
                !new Cookie(it.cookie).serviceFlag
            }) { rules ->
                rules.findAll {
                    it.cookie in [flowInfoFromDb3.forwardPath.cookie.value, flowInfoFromDb3.reversePath.cookie.value]
                }.size() == 2
            }
        }

        and: "Flow is valid and pingable"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }
        with(northbound.pingFlow(flow.flowId, new PingInput())) {
            it.forward.pingSuccess
            it.reverse.pingSuccess
        }

        and: "The new and old dst switches pass switch validation"
        wait(RULES_DELETION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies([dstSwitch.getDpId(), newDstSwitch.getDpId()]).isEmpty()
        }

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tags(LOW_PRIORITY)
    def "System reroutes flow to more preferable path while updating"() {
        given: "Two active not neighboring switches with two possible paths at least"
        def switchPair = switchPairs.all().nonNeighbouring().withAtLeastNPaths(2).random()

        and: "A flow"
        def flow = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(flow)

        when: "Make the current path less preferable than alternatives"
        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.flowId))
        def alternativePaths = switchPair.paths.findAll { it != currentPath }
        alternativePaths.each { pathHelper.makePathMorePreferable(it, currentPath) }

        and: "Update the flow"
        def newFlowDescr = flow.description + " updated"
        flowHelperV2.updateFlow(flow.flowId, flow.tap { it.description = newFlowDescr })

        then: "Flow is rerouted"
        def newCurrentPath
        wait(rerouteDelay + WAIT_OFFSET) {
            newCurrentPath = pathHelper.convert(northbound.getFlowPath(flow.flowId))
            assert newCurrentPath != currentPath
        }

        and: "Flow is updated"
        northboundV2.getFlow(flow.flowId).description == newFlowDescr

        and: "All involved switches pass switch validation"
        def involvedSwitchIds = (currentPath*.switchId + newCurrentPath*.switchId).unique()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitchIds).isEmpty()

        cleanup: "Revert system to original state"
        flow && flowHelperV2.deleteFlow(flow.flowId)
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
    }

    def "System doesn't rebuild path for a flow to more preferable path while updating portNumber/vlanId"() {
        given: "Two active switches connected to traffgens with two possible paths at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()

        and: "A flow"
        def flow = flowHelperV2.randomFlow(switchPair, false)
        flowHelperV2.addFlow(flow)

        when: "Make the current path less preferable than alternatives"
        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.flowId))
        def alternativePaths = switchPair.paths.findAll { it != currentPath }
        alternativePaths.each { pathHelper.makePathMorePreferable(it, currentPath) }

        and: "Update the flow: vlanId on the src endpoint"
        def updatedFlowSrcEndpoint = flow.jacksonCopy().tap {
            it.source.vlanId = flow.destination.vlanId - 1
        }
        flowHelperV2.updateFlow(flow.flowId, updatedFlowSrcEndpoint)

        then: "Flow is really updated"
        with(northboundV2.getFlow(flow.flowId)) {
            it.source.portNumber == updatedFlowSrcEndpoint.source.portNumber
            it.source.vlanId == updatedFlowSrcEndpoint.source.vlanId
        }

        and: "Flow path is not rebuild"
        timedLoop(rerouteDelay) {
            assert pathHelper.convert(northbound.getFlowPath(flow.flowId)) == currentPath
        }

        when: "Update the flow: vlanId on the dst endpoint"
        def updatedFlowDstEndpoint = flow.jacksonCopy().tap {
            it.destination.vlanId = flow.source.vlanId - 1
        }
        flowHelperV2.updateFlow(flow.flowId, updatedFlowDstEndpoint)

        then: "Flow is really updated"
        with(northboundV2.getFlow(flow.flowId)) {
            it.destination.portNumber == updatedFlowDstEndpoint.destination.portNumber
            it.destination.vlanId == updatedFlowDstEndpoint.destination.vlanId
        }

        and: "Flow path is not rebuild"
        timedLoop(rerouteDelay) {
            assert pathHelper.convert(northbound.getFlowPath(flow.flowId)) == currentPath
        }

        then: "Update the flow: port number and vlanId on the src/dst endpoints"
        def updatedFlow = flow.jacksonCopy().tap {
            it.source.portNumber = switchPair.getSrc().getTraffGens().first().getSwitchPort()
            it.source.vlanId = updatedFlowDstEndpoint.source.vlanId - 1
            it.destination.portNumber = switchPair.getDst().getTraffGens().first().getSwitchPort()
            it.destination.vlanId = updatedFlowDstEndpoint.destination.vlanId - 1
        }
        flowHelperV2.updateFlow(flow.flowId, updatedFlow)

        then: "Flow is really updated"
        with(northboundV2.getFlow(flow.flowId)) {
            it.source.portNumber == updatedFlow.source.portNumber
            it.source.vlanId == updatedFlow.source.vlanId
            it.destination.portNumber == updatedFlow.destination.portNumber
            it.destination.vlanId == updatedFlow.destination.vlanId
        }

        and: "Flow path is not rebuild"
        timedLoop(rerouteDelay + WAIT_OFFSET / 2) {
            assert pathHelper.convert(northbound.getFlowPath(flow.flowId)) == currentPath
        }

        and: "Flow is valid"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        and: "System allows traffic on the flow"
        def traffExam = traffExamProvider.get()
        def examFlow = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(
                flowHelperV2.toV1(updatedFlow), 100, 5
        )
        withPool {
            [examFlow.forward, examFlow.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        and: "All involved switches pass switch validation"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(currentPath*.switchId).isEmpty()

        cleanup: "Revert system to original state"
        flow && flowHelperV2.deleteFlow(flow.flowId)
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
    }

    @Tags([TOPOLOGY_DEPENDENT, LOW_PRIORITY])
    def "System allows to update single switch flow to multi switch flow"() {
        given: "A single switch flow with enabled lldp/arp on the dst side"
        def swPair = switchPairs.all().neighbouring().random()
        def flow = flowHelperV2.singleSwitchFlow(swPair.src)
        flow.destination.detectConnectedDevices.lldp = true
        flow.destination.detectConnectedDevices.arp = true
        flowHelperV2.addFlow(flow)

        when: "Update the dst endpoint to make this flow as multi switch flow"
        def newPortNumber = topology.getAllowedPortsForSwitch(topology.activeSwitches.find {
            it.dpId == swPair.dst.dpId
        }
        ).first()
        flowHelperV2.updateFlow(flow.flowId, flow.tap {
            it.destination.switchId = swPair.dst.dpId
            it.destination.portNumber = newPortNumber
        })

        then: "Flow is really updated"
        with(northboundV2.getFlow(flow.flowId)) {
            it.destination.switchId == swPair.dst.dpId
            it.destination.portNumber == newPortNumber
        }

        and: "Flow is valid and pingable"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }
        with(northbound.pingFlow(flow.flowId, new PingInput())) {
            it.forward.pingSuccess
            it.reverse.pingSuccess
        }

        and: "Involved switches pass switch validation"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(swPair.toList()*.dpId).isEmpty()

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }

    def "Unable to create a flow with both strict_bandwidth and ignore_bandwidth flags"() {
        when: "Try to create a flow with strict_bandwidth:true and ignore_bandwidth:true"
        def flow = flowHelperV2.randomFlow(switchPairs.all().random()).tap {
            strictBandwidth = true
            ignoreBandwidth = true
        }
        flowHelperV2.addFlow(flow)

        then: "Bad Request response is returned"
        def error = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(
                ~/Can not turn on ignore bandwidth flag and strict bandwidth flag at the same time/).matches(error)

        cleanup:
        !error && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tags([LOW_PRIORITY])
    def "Unable to update flow with incorrect id in request body"() {
        given:"A flow"
        def flow = flowHelperV2.randomFlow(switchPairs.all().random())
        def oldFlowId = flowHelperV2.addFlow(flow).getFlowId()
        def newFlowId = "new_flow_id"

        when: "Try to update flow with incorrect flow id in request body"
        northboundV2.updateFlow(flow.flowId, flow.tap {flowId = newFlowId})

        then: "Bad Request response is returned"
        def error = thrown(HttpClientErrorException)
        new FlowNotUpdatedExpectedError("flow_id from body and from path are different",
        ~/Body flow_id: ${newFlowId}, path flow_id: ${oldFlowId}/).matches(error)

        cleanup:
        Wrappers.silent {
            flowHelperV2.deleteFlow(oldFlowId)
        }
    }

    @Tags([LOW_PRIORITY])
    def "Unable to update flow with incorrect id in request path"() {
        given: "A flow"
        def flow = flowHelperV2.randomFlow(switchPairs.all().random())
        flowHelperV2.addFlow(flow)
        def newFlowId = "new_flow_id"

        when: "Try to update flow with incorrect flow id in request path"
        northboundV2.updateFlow(newFlowId, flow.tap { maximumBandwidth = maximumBandwidth + 1 })

        then: "Bad Request response is returned"
        def error = thrown(HttpClientErrorException)
        new FlowNotUpdatedExpectedError("flow_id from body and from path are different",
                ~/Body flow_id: ${flow.getFlowId()}, path flow_id: ${newFlowId}/).matches(error)

        cleanup:
        Wrappers.silent {
            flowHelperV2.deleteFlow(flow.flowId)
        }
    }

    @Tags(LOW_PRIORITY)
    def "Able to #method update with empty VLAN stats and non-zero VLANs (#5063)"() {
        given: "A flow with non empty vlans stats and with src and dst vlans set to '0'"
        def switches = switchPairs.all().random()
        def flowRequest = flowHelperV2.randomFlow(switches, false).tap {
            it.source.tap { it.vlanId = 0 }
            it.destination.tap { it.vlanId = 0 }
            it.statistics = new FlowStatistics([1, 2, 3] as Set)
        }
        def flow = flowHelperV2.addFlow(flowRequest)
        def UNUSED_VLAN = 1909

        when: "Try to #method update flow with empty VLAN stats and non-zero VLANs"
        def updatedFlow = updateCall(flow.getFlowId(), flowRequest, UNUSED_VLAN)

        then: "Flow is really updated"
        def actualFlow = northboundV2.getFlow(flow.getFlowId())
        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.getFlowId()).collect{it.getDpId()}
        actualFlow.getSource() == updatedFlow.getSource()
        actualFlow.getDestination() == updatedFlow.getDestination()
        actualFlow.getStatistics() == updatedFlow.getStatistics()
        switchHelper.validate(involvedSwitches).isEmpty()

        when: "Delete the flow"
        flowHelperV2.deleteFlow(flow.getFlowId())
        def flowIsDeleted = true

        then: "No excess rules left on the switches (#5141)"
        switchHelper.validate(involvedSwitches).isEmpty()

        cleanup:
        flowIsDeleted || flowHelperV2.deleteFlow(flow.getFlowId())

        where:
        method           | updateCall
        "partial" | { String flowId, FlowRequestV2 originalFlow, Integer newVlan ->
            flowHelperV2.partialUpdate(flowId, new FlowPatchV2().tap {
                it.source = new FlowPatchEndpoint().tap {it.vlanId = newVlan}
                it.destination = new FlowPatchEndpoint().tap {it.vlanId = newVlan}
                it.statistics = new FlowStatistics([] as Set)
            })
        }
        ""|  { String flowId, FlowRequestV2 originalFlow, Integer newVlan  ->
            flowHelperV2.updateFlow(flowId, originalFlow.tap {
                it.source = originalFlow.source.tap{it.vlanId = newVlan}
                it.destination = originalFlow.destination.tap{it.vlanId = newVlan}
                it.statistics = new FlowStatistics([] as Set)
            })

        }
    }

    @Tags(LOW_PRIORITY)
    def "Unable to update to a flow with maxLatencyTier2 higher as maxLatency)"() {
        given: "A flow"
        def swPair = switchPairs.singleSwitch().random()
        def flow = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow)

        when: "Try to update the flow"
        flow.maxLatency = 2
        flow.maxLatencyTier2 = flow.maxLatency - 1
        northboundV2.updateFlow(flow.flowId, flow)

        then: "Bad Request response is returned"
        def expectedException = new FlowNotUpdatedExpectedError(
                ~/The maxLatency \d+ms is higher than maxLatencyTier2 \d+ms/)
        def actualException = thrown(HttpClientErrorException)
        expectedException.matches(actualException)

        cleanup: "Remove the flow"
        flowHelperV2.deleteFlow(flow.flowId)
    }

    @Shared
    def errorDescription = { String operation, FlowRequestV2 flow, String endpoint, FlowRequestV2 conflictingFlow,
                             String conflictingEndpoint ->
        def message = "Requested flow '$conflictingFlow.flowId' " +
                "conflicts with existing flow '$flow.flowId'. " +
                "Details: requested flow '$conflictingFlow.flowId' $conflictingEndpoint: " +
                "switchId=\"${conflictingFlow."$conflictingEndpoint".switchId}\" " +
                "port=${conflictingFlow."$conflictingEndpoint".portNumber}"
        if (0 < conflictingFlow."$conflictingEndpoint".vlanId) {
            message += " vlanId=${conflictingFlow."$conflictingEndpoint".vlanId}"
        }
        message += ", existing flow '$flow.flowId' $endpoint: " +
                "switchId=\"${flow."$endpoint".switchId}\" " +
                "port=${flow."$endpoint".portNumber}"
        if (0 < flow."$endpoint".vlanId) {
            message += " vlanId=${flow."$endpoint".vlanId}"
        }
        return message
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
        def switchPairs = switchPairs.all(false).neighbouring().getSwitchPairs().sort(traffgensPrioritized)
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
        def switchPairs = switchPairs.all().nonNeighbouring().getSwitchPairs().sort(traffgensPrioritized)
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

    boolean isFlowPingable(FlowRequestV2 flow) {
        if (flow.source.switchId == flow.destination.switchId) {
            return false
        } else return !(topology.find(flow.source.switchId).ofVersion == "OF_12" ||
                topology.find(flow.destination.switchId).ofVersion == "OF_12")
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
                        getErrorMessage     : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict,
                                                String operation = "create" ->
                            "Could not $operation flow"
                        },
                        getErrorDescription : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict,
                                                String operation = "create" ->
                            errorDescription(operation, dominantFlow, "source", flowToConflict, "source")
                        }
                ],
                [
                        conflict            : "the same vlans on the same port on dst switch",
                        makeFlowsConflicting: { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict ->
                            flowToConflict.destination.portNumber = dominantFlow.destination.portNumber
                            flowToConflict.destination.vlanId = dominantFlow.destination.vlanId
                        },
                        getErrorMessage     : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict,
                                                String operation = "create" ->
                            "Could not $operation flow"
                        },
                        getErrorDescription : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict,
                                                String operation = "create" ->
                            errorDescription(operation, dominantFlow, "destination", flowToConflict, "destination")
                        }
                ],
                [
                        conflict            : "no vlan, both flows are on the same port on src switch",
                        makeFlowsConflicting: { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict ->
                            flowToConflict.source.portNumber = dominantFlow.source.portNumber
                            flowToConflict.source.vlanId = 0
                            dominantFlow.source.vlanId = 0
                        },
                        getErrorMessage     : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict,
                                                String operation = "create" ->
                            "Could not $operation flow"
                        },
                        getErrorDescription : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict,
                                                String operation = "create" ->
                            errorDescription(operation, dominantFlow, "source", flowToConflict, "source")
                        }
                ],
                [
                        conflict            : "no vlan, both flows are on the same port on dst switch",
                        makeFlowsConflicting: { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict ->
                            flowToConflict.destination.portNumber = dominantFlow.destination.portNumber
                            flowToConflict.destination.vlanId = 0
                            dominantFlow.destination.vlanId = 0
                        },
                        getErrorMessage     : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict,
                                                String operation = "create" ->
                            "Could not $operation flow"
                        },
                        getErrorDescription : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict,
                                                String operation = "create" ->
                            errorDescription(operation, dominantFlow, "destination", flowToConflict, "destination")
                        }
                ],
                [
                conflict            : "the same flow ID",
                makeFlowsConflicting: { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict ->
                    flowToConflict.flowId = dominantFlow.flowId
                },
                getErrorDescription : { FlowRequestV2 dominantFlow, FlowRequestV2 flowToConflict ->
                    "Flow $dominantFlow.flowId already exists"
                }
        ]
        ]
    }
}
