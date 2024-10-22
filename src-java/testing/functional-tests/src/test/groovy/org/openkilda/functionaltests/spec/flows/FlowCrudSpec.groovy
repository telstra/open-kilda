package org.openkilda.functionaltests.spec.flows

import org.openkilda.functionaltests.helpers.model.FlowRuleEntity

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
import org.openkilda.functionaltests.helpers.builder.FlowBuilder
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowActionType
import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.PathComputationStrategy
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.functionaltests.helpers.model.SwitchRulesFactory
import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.northbound.dto.v2.flows.FlowPatchEndpoint
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2
import org.openkilda.northbound.dto.v2.flows.FlowStatistics
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.ExamReport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared

import javax.inject.Provider

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.Wrappers.timedLoop
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.MOVED
import static org.openkilda.messaging.payload.flow.FlowState.IN_PROGRESS
import static org.openkilda.messaging.payload.flow.FlowState.UP
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
    @Autowired
    @Shared
    FlowFactory flowFactory
    @Autowired
    @Shared
    SwitchRulesFactory switchRulesFactory


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
    def "Valid #data.description has been created (#verifyTraffic) and no rule discrepancies [#srcDstStr]"() {
        given: "A flow"
        assumeTrue(topology.activeTraffGens.size() >= 2,
                "There should be at least two active traffgens for test execution")
        def allLinksInfoBefore = northbound.getAllLinks().collectEntries { [it.id, it.availableBandwidth] }.sort()

        def flow = expectedFlowEntity.create()
        def switches = flow.retrieveAllEntityPaths().getInvolvedSwitches()

        expect: "No rule discrepancies on every switch of the flow"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(switches).isEmpty()

        and: "No discrepancies when doing flow validation"
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "The flow allows traffic (only applicable flows are checked)"
        if (isTrafficCheckAvailable) {
            def traffExam = traffExamProvider.get()
            def exam = flow.traffExam(traffExam, 1000, 3)
            withPool {
                [exam.forward, exam.reverse].eachParallel { direction ->
                    def resources = traffExam.startExam(direction)
                    direction.setResources(resources)
                    assert traffExam.waitExam(direction).hasTraffic()
                }
            }
        }

        when: "Remove the flow"
        flow.delete()

        then: "The flow is not present in NB"
        !northboundV2.getAllFlows().find { it.flowId == flow.flowId }

        and: "ISL bandwidth is restored"
        wait(WAIT_OFFSET) {
            def allLinksInfoAfter = northbound.getAllLinks().collectEntries { [it.id, it.availableBandwidth] }.sort()
            assert allLinksInfoBefore == allLinksInfoAfter
        }

        and: "No rule discrepancies on every switch of the flow"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(switches).isEmpty()

        where:
        /*Some permutations may be missed, since at current implementation we only take 'direct' possible flows
        * without modifying the costs of ISLs.
        * I.e. if potential test case with transit switch between certain pair of unique switches will require change of
        * costs on ISLs (those switches are neighbors, but we want a path with transit switch between them), then
        * we will not test such case
        */
        data << flowsWithoutTransitSwitch + flowsWithTransitSwitch + singleSwitchFlows
        expectedFlowEntity = data.expectedFlowEntity as FlowExtended
        isTrafficCheckAvailable = data.isTrafficCheckAvailable as Boolean
        verifyTraffic = isTrafficCheckAvailable ? "with traffic verification" : " [!NO TRAFFIC CHECK!]"
        srcDstStr = "src:${topology.find(expectedFlowEntity.source.switchId).hwSwString}->dst:${topology.find(expectedFlowEntity.destination.switchId).hwSwString}"
    }

    def "Able to create multiple flows with #data.description"() {
        given: "Two potential flows that should not conflict"
        Tuple2<FlowExtended, FlowExtended> flows = data.getNotConflictingFlows()

        when: "Create the first flow"
        flows.v1.create()

        and: "Try creating a second flow with #data.description"
        flows.v2.create()

        then: "Both flows are successfully created"
        northboundV2.getAllFlows()*.flowId.containsAll(flows*.flowId)

        where:
        data << [
                [
                        description           : "same switch-port but vlans on src and dst are swapped",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def expectedFlow1Entity = flowFactory.getBuilder(srcSwitch, dstSwitch).build().tap {
                                it.source.vlanId == it.destination.vlanId && it.destination.vlanId--
                            }
                            def expectedFlow2Entity = flowFactory.getBuilder(srcSwitch, dstSwitch)
                                    .withSourcePort(expectedFlow1Entity.source.portNumber)
                                    .withSourceVlan(expectedFlow1Entity.destination.vlanId)
                                    .withDestinationPort(expectedFlow1Entity.destination.portNumber)
                                    .withDestinationVlan(expectedFlow1Entity.source.vlanId)
                                    .build()
                            return new Tuple2<FlowExtended, FlowExtended>(expectedFlow1Entity, expectedFlow2Entity)
                        }
                ],
                [
                        description           : "same switch-port but vlans on src and dst are different",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def expectedFlow1Entity = flowFactory.getBuilder(srcSwitch, dstSwitch).build()
                            def expectedFlow2Entity = flowFactory.getBuilder(srcSwitch, dstSwitch)
                                    .withSourcePort(expectedFlow1Entity.source.portNumber)
                                    .withSourceVlan(expectedFlow1Entity.source.vlanId - 1)
                                    .withDestinationPort(expectedFlow1Entity.destination.portNumber)
                                    .withDestinationVlan(expectedFlow1Entity.destination.vlanId - 1)
                                    .build()
                            return new Tuple2<FlowExtended, FlowExtended>(expectedFlow1Entity, expectedFlow2Entity)
                        }
                ],
                [
                        description           : "vlan-port of new src = vlan-port of existing dst (+ different src)",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def expectedFlow1Entity = flowFactory.getBuilder(srcSwitch, dstSwitch).build()
                            //src for new flow will be on different switch not related to existing flow
                            //thus two flows will have same dst but different src
                            def newSrc = getTopology().activeSwitches.find {
                                ![expectedFlow1Entity.source.switchId, expectedFlow1Entity.destination.switchId].contains(it.dpId) &&
                                        getTopology().getAllowedPortsForSwitch(it).contains(expectedFlow1Entity.destination.portNumber)
                            }
                            def expectedFlow2Entity = flowFactory.getBuilder(newSrc, dstSwitch)
                                    .withSourceVlan(expectedFlow1Entity.destination.vlanId)
                                    .withSourcePort(expectedFlow1Entity.destination.portNumber)
                                    .withDestinationVlan(expectedFlow1Entity.destination.vlanId - 1) //ensure no conflict on dst
                                    .build()
                            return new Tuple2<FlowExtended, FlowExtended>(expectedFlow1Entity, expectedFlow2Entity)
                        }
                ],
                [
                        description           : "vlan-port of new dst = vlan-port of existing src (but different switches)",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def srcPort = getTopology().getAllowedPortsForSwitch(srcSwitch)
                                    .intersect(getTopology().getAllowedPortsForSwitch(dstSwitch))[0]

                            def expectedFlow1Entity = flowFactory.getBuilder(srcSwitch, dstSwitch)
                                    .withSourcePort(srcPort)
                                    .build()

                            def expectedFlow2Entity = flowFactory.getBuilder(srcSwitch, dstSwitch)
                                    .withDestinationVlan(expectedFlow1Entity.source.vlanId)
                                    .withDestinationPort(expectedFlow1Entity.source.portNumber)
                                    .build()

                            return new Tuple2<FlowExtended, FlowExtended>(expectedFlow1Entity, expectedFlow2Entity)
                        }
                ],
                [
                        description           : "vlan of new dst = vlan of existing src and port of new dst = port of " +
                                "existing dst",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def expectedFlow1Entity = flowFactory.getBuilder(srcSwitch, dstSwitch).build()
                            def expectedFlow2Entity = flowFactory.getBuilder(srcSwitch, dstSwitch)
                                    .withDestinationVlan(expectedFlow1Entity.source.vlanId)
                                    .withDestinationPort(expectedFlow1Entity.destination.portNumber)
                                    .build()
                            return new Tuple2<FlowExtended, FlowExtended>(expectedFlow1Entity, expectedFlow2Entity)
                        }
                ],
                [
                        description           : "default and tagged flows on the same port on dst switch",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def expectedFlow1Entity = flowFactory.getBuilder(srcSwitch, dstSwitch).build()
                            def expectedFlow2Entity = flowFactory.getBuilder(srcSwitch, dstSwitch)
                                    .withDestinationVlan(0)
                                    .withDestinationPort(expectedFlow1Entity.destination.portNumber)
                                    .build()
                            return new Tuple2<FlowExtended, FlowExtended>(expectedFlow1Entity, expectedFlow2Entity)
                        }
                ],
                [
                        description           : "default and tagged flows on the same port on src switch",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def expectedFlow1Entity = flowFactory.getBuilder(srcSwitch, dstSwitch).build()
                            def expectedFlow2Entity = flowFactory.getBuilder(srcSwitch, dstSwitch)
                                    .withSourceVlan(0).withSourcePort(expectedFlow1Entity.source.portNumber)
                                    .build()
                            return new Tuple2<FlowExtended, FlowExtended>(expectedFlow1Entity, expectedFlow2Entity)
                        }
                ],
                [
                        description           : "tagged and default flows on the same port on dst switch",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def expectedFlow1Entity = flowFactory.getBuilder(srcSwitch, dstSwitch)
                                    .withDestinationVlan(0)
                                    .build()
                            def expectedFlow2Entity = flowFactory.getBuilder(srcSwitch, dstSwitch)
                                    .withDestinationPort(expectedFlow1Entity.destination.portNumber)
                                    .build()
                            return new Tuple2<FlowExtended, FlowExtended>(expectedFlow1Entity, expectedFlow2Entity)
                        }
                ],
                [
                        description           : "tagged and default flows on the same port on src switch",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def expectedFlow1Entity = flowFactory.getBuilder(srcSwitch, dstSwitch)
                                    .withSourceVlan(0)
                                    .build()
                            def expectedFlow2Entity = flowFactory.getBuilder(srcSwitch, dstSwitch)
                                    .withSourcePort(expectedFlow1Entity.source.portNumber)
                                    .build()
                            return new Tuple2<FlowExtended, FlowExtended>(expectedFlow1Entity, expectedFlow2Entity)
                        }
                ],
                [
                        description           : "default and tagged flows on the same ports on src and dst switches",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def expectedFlow1Entity = flowFactory.getBuilder(srcSwitch, dstSwitch).build()
                            def expectedFlow2Entity = flowFactory.getBuilder(srcSwitch, dstSwitch)
                                    .withSourceVlan(0)
                                    .withSourcePort(expectedFlow1Entity.source.portNumber)
                                    .withDestinationVlan(0)
                                    .withDestinationPort(expectedFlow1Entity.destination.portNumber)
                                    .build()
                            return new Tuple2<FlowExtended, FlowExtended>(expectedFlow1Entity, expectedFlow2Entity)
                        }
                ],
                [
                        description           : "tagged and default flows on the same ports on src and dst switches",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def expectedFlow1Entity = flowFactory.getBuilder(srcSwitch, dstSwitch)
                                    .withSourceVlan(0)
                                    .withDestinationVlan(0)
                                    .build()
                            def expectedFlow2Entity = flowFactory.getBuilder(srcSwitch, dstSwitch)
                                    .withSourcePort(expectedFlow1Entity.source.portNumber)
                                    .withDestinationPort(expectedFlow1Entity.destination.portNumber)
                                    .build()
                            return new Tuple2<FlowExtended, FlowExtended>(expectedFlow1Entity, expectedFlow2Entity)
                        }
                ]
        ]
    }

    @Tags([TOPOLOGY_DEPENDENT, SMOKE_SWITCHES])
    def "Able to create single switch single port flow with different vlan (#expectedFlowEntity.source.switchId.description)"(FlowExtended expectedFlowEntity) {
        given: "A flow"
        def flow = expectedFlowEntity.create()

        expect: "No rule discrepancies on the switch"
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(flow.source.switchId).isPresent()

        and: "No discrepancies when doing flow validation"
        flow.validateAndCollectDiscrepancies().isEmpty()

        when: "Remove the flow"
        flow.delete()

        then: "The flow is not present in NB"
        !northboundV2.getAllFlows().find { it.flowId == flow.flowId }

        and: "No rule discrepancies on the switch after delete"
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(flow.source.switchId).isPresent()

        where:
        expectedFlowEntity << getSingleSwitchSinglePortFlows()
    }

    def "Able to validate flow with zero bandwidth"() {
        given: "A flow with zero bandwidth"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def expectedFlowEntity = flowFactory.getBuilder(srcSwitch, dstSwitch).withBandwidth(0).build()

        when: "Create a flow with zero bandwidth"
        def flow = expectedFlowEntity.create()

        then: "Validation of flow with zero bandwidth must be succeed"
        flow.maximumBandwidth == 0
        flow.validateAndCollectDiscrepancies().isEmpty()
    }

    def "Unable to create single-switch flow with the same ports and vlans on both sides"() {
        given: "Potential single-switch flow with the same ports and vlans on both sides"
        def singleSwitch = topology.activeSwitches.first()
        def invalidFlowEntity = flowFactory.getBuilder(singleSwitch, singleSwitch)
                .withSamePortOnSourceAndDestination()
                .withSameVlanOnSourceAndDestination().build()

        when: "Try creating such flow"
        invalidFlowEntity.create()

        then: "Error is returned, stating a readable reason"
        def error = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(
                ~/It is not allowed to create one-switch flow for the same ports and VLANs/).matches(error)
    }

    def "Unable to create flow with conflict data (#data.conflict)"() {
        given: "A potential flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def expectedFlowEntity = flowFactory.getBuilder(srcSwitch, dstSwitch).build()

        and: "Another potential flow with #data.conflict"
        FlowExtended conflictingFlow =  data.makeFlowsConflicting(expectedFlowEntity, flowFactory.getBuilder(srcSwitch, dstSwitch))

        when: "Create the first flow"
        def flow = expectedFlowEntity.create()

        and: "Try creating the second flow which conflicts"
        conflictingFlow.create()

        then: "Error is returned, stating a readable reason of conflict"
        def error = thrown(HttpClientErrorException)
        new FlowNotCreatedWithConflictExpectedError(~/${data.getErrorDescription(flow, conflictingFlow)}/).matches(error)

        and: "Main flow has been created successfully and in the UP state"
        flow.waitForBeingInState(UP)

        and: "Conflict flow does not exist in the system"
        if(flow.flowId != conflictingFlow.flowId) {
            !northboundV2.getAllFlows().find { it.flowId == conflictingFlow.flowId }
        }

        where:
        data << getConflictingData()
    }

    def "A flow cannot be created with asymmetric forward and reverse paths"() {
        given: "Two active neighboring switches with two possible flow paths at least and different number of hops"
        List<List<Isl>> availablePathsIsls
        //the shortest path between neighboring switches is 1 ISL(2 nodes: src_sw-port<--->port-dst_sw)
        int shortestIslCountPath = 1
        def swPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(2).getSwitchPairs().find {
            availablePathsIsls = it.retrieveAvailablePaths().collect { it.getInvolvedIsls() }
            availablePathsIsls.size() > 1  && availablePathsIsls.find { it.size() > shortestIslCountPath }
        } ?: assumeTrue(false, "No suiting active neighboring switches with two possible flow paths at least and " +
                "different number of hops found")

        and: "Make all shorter(one isl) forward paths not preferable. Shorter reverse paths are still preferable"
        List<Isl> modifiedIsls = availablePathsIsls.findAll { it.size() == shortestIslCountPath }.flatten().unique() as List<Isl>
        islHelper.updateIslsCostInDatabase(modifiedIsls, Integer.MAX_VALUE)

        when: "Create a flow"
        def flow = flowFactory.getRandom(swPair)

        then: "The flow is built through one of the long paths"
        def fullFlowPath = flow.retrieveAllEntityPaths()
        def forwardIsls = fullFlowPath.getInvolvedIsls(Direction.FORWARD)
        def reverseIsls = fullFlowPath.getInvolvedIsls(Direction.REVERSE)
        assert forwardIsls.intersect(modifiedIsls).isEmpty()
        assert forwardIsls.size() > shortestIslCountPath

        and: "The flow has symmetric forward and reverse paths even though there is a more preferable reverse path"
        forwardIsls.collect { it.reversed }.reverse() == reverseIsls
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Error is returned if there is no available path to #data.isolatedSwitchType switch"() {
        given: "A switch that has no connection to other switches"
        def isolatedSwitch = switchPairs.all().nonNeighbouring().random().src
        SwitchPair switchPair = data.switchPair(isolatedSwitch)
        def connectedIsls = topology.getRelatedIsls(isolatedSwitch)
        islHelper.breakIsls(connectedIsls)

        when: "Try building a flow using the isolated switch"
        def invalidFlow = flowFactory.getBuilder(switchPair).build()
        invalidFlow.create()

        then: "Error is returned, stating that there is no path found for such flow"
        def error = thrown(HttpClientErrorException)
        new FlowNotCreatedWithMissingPathExpectedError(
                ~/Switch ${isolatedSwitch.getDpId()} doesn\'t have links with enough bandwidth/).matches(error)

        and: "Flow does not exist in the system"
        !northboundV2.getAllFlows().find { it.flowId == invalidFlow.flowId }

        where:
        data << [
                [
                        isolatedSwitchType: "source",
                        switchPair: { Switch theSwitch ->
                            return switchPairs.all()
                                    .nonNeighbouring()
                                    .includeSourceSwitch(theSwitch)
                                    .random()
                        }
                ],
                [
                        isolatedSwitchType: "destination",
                        switchPair: { Switch theSwitch ->
                            return switchPairs.all()
                                    .nonNeighbouring()
                                    .includeSourceSwitch(theSwitch)
                                    .random()
                                    .getReversed()
                        }
                ]
        ]
    }

    def "Removing flow while it is still in progress of being set up should not cause rule discrepancies"() {
        given: "A potential flow"
        def switchPair = switchPairs.all().neighbouring().random()
        def flowExpectedPath = switchPair.retrieveAvailablePaths().min { islHelper.getCost(it.getInvolvedIsls()) }
        def switchesId = flowExpectedPath.getInvolvedSwitches()

        when: "Init creation of a new flow"
        def flow = flowFactory.getBuilder(switchPair).build().sendCreateRequest()

        and: "Immediately remove the flow"
        flow.sendDeleteRequest()

        then: "System returns error as being unable to remove in progress flow"
        def e = thrown(HttpClientErrorException)
        new FlowNotDeletedExpectedError(~/Flow ${flow.getFlowId()} is in progress now/).matches(e)
        and: "Flow is not removed"
        northboundV2.getAllFlows()*.flowId.contains(flow.flowId)

        and: "Flow eventually gets into UP state"
        flow.waitForBeingInState(UP)
        flow.retrieveAllEntityPaths().getInvolvedSwitches().sort() == switchesId.sort()

        and: "All related switches have no discrepancies in rules"
        switchesId.each {
            def syncResult = switchHelper.synchronize(it)
            assert syncResult.rules.installed.isEmpty() && syncResult.rules.removed.isEmpty()
            assert syncResult.meters.installed.isEmpty() && syncResult.meters.removed.isEmpty()

            def swProps = switchHelper.getCachedSwProps(it)
            def amountOfServer42Rules = 0

            if(swProps.server42FlowRtt && it in [switchPair.src.dpId, switchPair.dst.dpId]) {
                amountOfServer42Rules +=1
                it == switchPair.src.dpId && flow.source.vlanId && ++amountOfServer42Rules
                it == switchPair.dst.dpId && flow.destination.vlanId && ++amountOfServer42Rules
            }

            def amountOfFlowRules = 3 + amountOfServer42Rules
            assert syncResult.rules.proper.findAll { !new Cookie(it).serviceFlag }.size() == amountOfFlowRules
        }
    }

    def "Unable to create a flow with #problem"() {
        given: "A flow with #problem"
        def switchPair = switchPairs.all().nonNeighbouring().random()
        FlowExtended invalidFlowEntity = update(flowFactory.getBuilder(switchPair, false))

        when: "Try to create a flow"
        invalidFlowEntity.create()

        then: "Flow is not created"
        def actualException = thrown(HttpClientErrorException)
        expectedException.matches(actualException)

        where:
        problem                      | update                                                              | expectedException
        "invalid encapsulation type" |
                { FlowBuilder flowToSpoil ->
                    return flowToSpoil.withEncapsulationType(FlowEncapsulationType.FAKE).build()
                }                                                                                          |
                new FlowNotCreatedExpectedError(
                        "No enum constant org.openkilda.messaging.payload.flow.FlowEncapsulationType.FAKE",
                        ~/Can not parse arguments of the create flow request/)
        "unavailable latency"        |
                { FlowBuilder flowToSpoil ->
                    return flowToSpoil.withMaxLatency(IMPOSSIBLY_LOW_LATENCY)
                            .withPathComputationStrategy(PathComputationStrategy.MAX_LATENCY).build()
                }                                                                                          |
                new FlowNotCreatedWithMissingPathExpectedError(
                        ~/Latency limit\: Requested path must have latency ${IMPOSSIBLY_LOW_LATENCY}ms or lower/)
        "invalid statistics vlan number" |
                { FlowBuilder flowToSpoil ->
                    return flowToSpoil.withStatistics(FLOW_STATISTICS_CAUSING_ERROR)
                            .withSourceVlan(0).build()
                }                                                                                          |
                new FlowNotCreatedExpectedError(~/To collect vlan statistics, the vlan IDs must be from 1 up to 4095/)
    }

    @Tags([LOW_PRIORITY])
    def "Unable to update to a flow with #problem"() {
        given: "A flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowFactory.getBuilder(srcSwitch, dstSwitch).build().create()

        when: "Try to update the flow"
        FlowExtended invalidFlowEntity = invalidUpdateParam(flow)
        flow.update(invalidFlowEntity)

        then: "Flow is not updated"
        def actualError = thrown(HttpClientErrorException)
        expectedError.matches(actualError)

        where:
        problem                 | invalidUpdateParam       | expectedError
        "unavailable bandwidth" | { FlowExtended flowExtended ->
            return flowExtended.deepCopy().tap { it.maximumBandwidth = IMPOSSIBLY_HIGH_BANDWIDTH }
        } |
                new FlowNotUpdatedWithMissingPathExpectedError(~/Not enough bandwidth or no path found. \
Switch .* doesn't have links with enough bandwidth, \
Failed to find path with requested bandwidth=${IMPOSSIBLY_HIGH_BANDWIDTH}/)

        "vlan id is above 4095" | { FlowExtended flowExtended ->
            return flowExtended.deepCopy().tap { it.source.vlanId = 4096 }
        } |
                new FlowNotUpdatedExpectedError(~/Errors: VlanId must be less than 4096/)

    }

    @Tags([LOW_PRIORITY])
    def "Unable to partially update to a flow with statistics vlan set to 0 or above 4095"() {
        given: "A flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowFactory.getBuilder(srcSwitch, dstSwitch)
                .withDestinationVlan(0).build().create()

        when: "Try to partially update the flow"
        flow.partialUpdate(new FlowPatchV2().tap { it.statistics = FLOW_STATISTICS_CAUSING_ERROR })

        then: "Flow is not updated"
        def actualException = thrown(HttpClientErrorException)
        new FlowNotUpdatedExpectedError(~/To collect vlan statistics, the vlan IDs must be from 1 up to 4095/)
                .matches(actualException)
    }

    def "Unable to create a flow on an isl port in case port is occupied on a #data.switchType switch"() {
        given: "An isl"
        Isl isl = topology.islsForActiveSwitches.find { it.aswitch && it.dstSwitch }
        assumeTrue(isl as boolean, "Unable to find required isl")

        when: "Try to create a flow using isl port"
        def invalidFlowEntity = flowFactory.getBuilder(isl.srcSwitch, isl.dstSwitch).build()
        invalidFlowEntity."$data.switchType".portNumber = isl."$data.port"
        invalidFlowEntity.create()

        then: "Flow is not created"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(data.errorDescription(isl)).matches(exc)

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
        islHelper.breakIsl(isl)

        when: "Try to create a flow using ISL src port"
        def invalidFlowEntity = flowFactory.getBuilder(isl.srcSwitch, isl.dstSwitch).withSourcePort(isl.srcPort).build()
        invalidFlowEntity.create()

        then: "Flow is not created"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(
                getPortViolationErrorDescriptionPattern("source", isl.srcPort, isl.srcSwitch.dpId)).matches(exc)
    }

    def "Unable to create a flow on an isl port when ISL status is MOVED"() {
        given: "An inactive isl with moved state"
        Isl isl = topology.islsForActiveSwitches.find { it.aswitch && it.dstSwitch }
        assumeTrue(isl as boolean, "Unable to find required isl")
        def notConnectedIsls = topology.notConnectedIsls
        assumeTrue(notConnectedIsls.size() > 0, "Unable to find non-connected isl")
        def notConnectedIsl = notConnectedIsls.first()
        def newIsl = islHelper.replugDestination(isl, notConnectedIsl, true, false)

        islUtils.waitForIslStatus([isl, isl.reversed], MOVED)
        wait(discoveryExhaustedInterval + WAIT_OFFSET) {
            [newIsl, newIsl.reversed].each { assert northbound.getLink(it).state == DISCOVERED }
        }

        when: "Try to create a flow using ISL src port"
        def invalidFlowEntity = flowFactory.getBuilder(isl.srcSwitch, isl.dstSwitch).withSourcePort(isl.srcPort).build()
        invalidFlowEntity.create()

        then: "Flow is not created"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(
                getPortViolationErrorDescriptionPattern("source", isl.srcPort, isl.srcSwitch.dpId)).matches(exc)
    }

    @Tags(SWITCH_RECOVER_ON_FAIL)
    def "System doesn't allow to create a one-switch flow on a DEACTIVATED switch"() {
        given: "Disconnected switch"
        def sw = topology.getActiveSwitches()[0]
        switchHelper.knockoutSwitch(sw, RW)

        when: "Try to create a one-switch flow on a deactivated switch"
        flowFactory.getBuilder(sw, sw).build().create()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(
                ~/Source switch $sw.dpId and Destination switch $sw.dpId are not connected to the controller/).matches(exc)
    }

    def "System allows to CRUD protected flow"() {
        given: "Two active not neighboring switches with two diverse paths at least"
        def switchPair = switchPairs.all().nonNeighbouring().withAtLeastNNonOverlappingPaths(2).random()

        when: "Create flow with protected path"
        def flow = flowFactory.getBuilder(switchPair).withProtectedPath(true).build().create()

        then: "Flow is created with protected path"
        def flowPathInfo = flow.retrieveAllEntityPaths()
        !flowPathInfo.flowPath.protectedPath.isPathAbsent()

        def flowInfo = flow.retrieveDetails()
        flowInfo.allocateProtectedPath
        flowInfo.statusDetails

        and: "Rules for main and protected paths are created"
        wait(WAIT_OFFSET) {
            HashMap<SwitchId, List<FlowRuleEntity>> flowInvolvedSwitchesWithRules = flowPathInfo.getInvolvedSwitches()
                    .collectEntries{ [(it): switchRulesFactory.get(it).getRules()] } as HashMap<SwitchId, List<FlowRuleEntity>>
            flow.verifyRulesForProtectedFlowOnSwitches(flowInvolvedSwitchesWithRules)
        }

        and: "Validation of flow must be successful"
        flow.validateAndCollectDiscrepancies().isEmpty()

        def flowInfoFromDb = database.getFlow(flow.flowId)
        def protectedForwardCookie = flowInfoFromDb.protectedForwardPath.cookie.value
        def protectedReverseCookie = flowInfoFromDb.protectedReversePath.cookie.value
        def protectedFlowPath = flowPathInfo.flowPath.protectedPath.forward

        when: "Update flow: disable protected path(allocateProtectedPath=false)"
        def flowWithoutProtectedPath = flow.deepCopy().tap { it.allocateProtectedPath = false}
        flow.update(flowWithoutProtectedPath)

        then: "Protected path is disabled"
        !flow.retrieveAllEntityPaths().flowPath.protectedPath
        def flowInfoAfterUpdating = flow.retrieveDetails()
        !flowInfoAfterUpdating.allocateProtectedPath
        !flowInfoAfterUpdating.statusDetails

        and: "Rules for protected path are deleted"
        wait(WAIT_OFFSET) {
            def cookies = protectedFlowPath.getInvolvedSwitches().unique().collectMany { sw ->
                switchRulesFactory.get(sw).getRules().findAll {
                    !new Cookie(it.cookie).serviceFlag
                }.cookie
            }
            assert !cookies.contains(protectedForwardCookie) && !cookies.contains(protectedReverseCookie)
        }
    }

    @Tags(LOW_PRIORITY)
    def "System allows to set/update description/priority/max-latency for a flow"() {
        given: "Two active neighboring switches"
        def switchPair = switchPairs.all().neighbouring().random()

        and: "Value for each field"
        def expectedFlowEntity = flowFactory.getBuilder(switchPair)
                .withPriority(100)
                .withMaxLatency(100)
                .withMaxLatencyTier2(200)
                .withDescription("test description")
                .withPeriodicPing(true)
                .build()

        when: "Create a flow with predefined values"
        def flow = expectedFlowEntity.create()

        then: "Flow is created with needed values"
       verifyAll {
           flow.priority == expectedFlowEntity.priority
           flow.maxLatency == expectedFlowEntity.maxLatency
           flow.maxLatencyTier2 == expectedFlowEntity.maxLatencyTier2
           flow.description == expectedFlowEntity.description
           flow.periodicPings == expectedFlowEntity.periodicPings
       }

        when: "Update predefined values"
        expectedFlowEntity = flow.deepCopy().tap {
            it.priority = 200
            it.maxLatency = 300
            it.maxLatencyTier2 = 400
            it.description = "test description updated"
            it.periodicPings = false
        }

        def updatedFlow = flow.update(expectedFlowEntity)

        then: "Flow is updated correctly"
        updatedFlow.hasTheSamePropertiesAs(expectedFlowEntity)
    }

    def "System doesn't ignore encapsulationType when flow is created with ignoreBandwidth = true"() {
        given: "Two active switches"
        def swPair = switchPairs.all().neighbouring().withBothSwitchesVxLanEnabled().random()

        def initialSrcProps = switchHelper.getCachedSwProps(swPair.src.dpId)
        switchHelper.updateSwitchProperties(swPair.getSrc(), initialSrcProps.jacksonCopy().tap {
            it.supportedTransitEncapsulation = [FlowEncapsulationType.TRANSIT_VLAN.toString()]})

        when: "Create a flow with not supported encapsulation type on the switches"
        flowFactory.getBuilder(swPair)
                .withIgnoreBandwidth(true)
                .withBandwidth(0)
                .withEncapsulationType(FlowEncapsulationType.VXLAN).build()
                .create()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(~/Flow\'s source endpoint ${swPair.getSrc().getDpId()} doesn\'t support \
requested encapsulation type ${FlowEncapsulationType.VXLAN.name()}. Choose one of the supported encapsulation \
types .* or update switch properties and add needed encapsulation type./).matches(exc)
    }

    def "Flow status accurately represents the actual state of the flow and flow rules"() {
        when: "Create a flow on a long path"
        def swPair = switchPairs.all().random()
        def availablePaths = swPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }
        def longPath = availablePaths.max { it.size() }
        availablePaths.findAll { !it.containsAll(longPath) }.each { islHelper.makePathIslsMorePreferable(longPath, it) }
        def flow = flowFactory.getRandom(swPair, false, IN_PROGRESS)

        then: "Flow status is changed to UP only when all rules are actually installed"
        flow.status == IN_PROGRESS
        wait(PATH_INSTALLATION_TIME) {
            assert flow.retrieveFlowStatus().status == UP
        }
        def flowInfo = database.getFlow(flow.flowId)
        def flowCookies = [flowInfo.forwardPath.cookie.value, flowInfo.reversePath.cookie.value]
        def switches = flow.retrieveAllEntityPaths().getInvolvedSwitches()
        withPool(switches.size()) {
            switches.eachParallel { SwitchId swId ->
                assert switchRulesFactory.get(swId).getRules().cookie.containsAll(flowCookies)
            }
        }

        when: "Delete flow"
        flow.delete()

        then: "Flow is actually removed from flows dump only after all rules are removed"
        wait(RULES_DELETION_TIME) {
            assert !flow.retrieveFlowStatus()
        }
        withPool(switches.size()) {
            switches.eachParallel { SwitchId swId ->
                assert switchRulesFactory.get(swId).getRules().cookie.findAll {
                    def cookie = new Cookie(it)
                    cookie.type == CookieType.MULTI_TABLE_INGRESS_RULES || !cookie.serviceFlag
                }.empty
            }
        }
        northboundV2.getAllFlows().empty
    }

    @Tags(LOW_PRIORITY)
    def "Able to update a flow endpoint"() {
        given: "Three active switches"
        def allSwitches = topology.activeSwitches
        assumeTrue(allSwitches.size() >= 3, "Unable to find three active switches")
        def srcSwitch = allSwitches[0]
        def dstSwitch = allSwitches[1]

        and: "A vlan flow"
        def flow = flowFactory.getRandom(srcSwitch, dstSwitch, false)

        when: "Update the flow: port number and vlan id on the src endpoint"
        def flowExpectedEntity = flow.deepCopy().tap {
            it.source.portNumber = topology.getAllowedPortsForSwitch(srcSwitch).find { it != flow.source.portNumber }
            it.source.vlanId = flow.source.vlanId + 1
        }
       def updatedFlow = flow.update(flowExpectedEntity)

        then: "Flow is really updated"
        updatedFlow.hasTheSamePropertiesAs(flowExpectedEntity)

        and: "Flow history shows actual info into stateBefore and stateAfter sections"
        def flowHistoryEntry = updatedFlow.waitForHistoryEvent(FlowActionType.UPDATE)
        with(flowHistoryEntry.dumps.find { it.type == "stateBefore" }) {
            it.sourcePort == flow.source.portNumber
            it.sourceVlan == flow.source.vlanId
        }
        with(flowHistoryEntry.dumps.find { it.type == "stateAfter" }) {
            it.sourcePort == flowExpectedEntity.source.portNumber
            it.sourceVlan == flowExpectedEntity.source.vlanId
        }

        and: "Flow rules are recreated"
        def flowInfoFromDb2 = database.getFlow(flow.flowId)
        wait(RULES_INSTALLATION_TIME) {
            def rules = switchRulesFactory.get(srcSwitch.dpId).getRules()
                    .findAll {!new Cookie(it.cookie).serviceFlag }
            rules.cookie.findAll { it == flowInfoFromDb2.forwardPath.cookie.value || it == flowInfoFromDb2.reversePath.cookie.value }.size() == 2

            def ingressRule = rules.find { it.cookie == flowInfoFromDb2.forwardPath.cookie.value }
            ingressRule.match.inPort == flowExpectedEntity.source.portNumber.toString()
            //vlan is matched in shared rule
            !ingressRule.match.vlanVid
        }

        and: "Flow is valid and pingable"
        updatedFlow.validateAndCollectDiscrepancies().isEmpty()
        updatedFlow.pingAndCollectDiscrepancies().isEmpty()

        and: "The src switch passes switch validation"
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(srcSwitch.getDpId()).isPresent()

        when: "Update the flow: switch id on the dst endpoint"
        def newDstSwitch = allSwitches[2]
        flowExpectedEntity.destination.switchId = newDstSwitch.dpId

        updatedFlow = updatedFlow.update(flowExpectedEntity)

        then: "Flow is really updated"
        updatedFlow.destination.switchId == newDstSwitch.dpId

        and: "Flow history shows actual info into stateBefore and stateAfter sections"
        def flowHistory2 = updatedFlow.waitForHistoryEvent(FlowActionType.UPDATE)
        with(flowHistory2.dumps.find { it.type == "stateBefore" }) {
            it.destinationSwitch == dstSwitch.dpId.toString()
        }
        with(flowHistory2.dumps.find { it.type == "stateAfter" }) {
            it.destinationSwitch == newDstSwitch.dpId.toString()
        }

        and: "Flow rules are removed from the old dst switch"
        def flowInfoFromDb3 = database.getFlow(flow.flowId)
        wait(RULES_DELETION_TIME) {
            def cookies = switchRulesFactory.get(dstSwitch.dpId).getRules()
                    .findAll {!new Cookie(it.cookie).serviceFlag }.cookie
            !cookies.findAll { it == flowInfoFromDb2.forwardPath.cookie.value || it == flowInfoFromDb2.reversePath.cookie.value}
        }

        and: "Flow rules are installed on the new dst switch"
        wait(RULES_INSTALLATION_TIME) {
            def cookies = switchRulesFactory.get(newDstSwitch.dpId).getRules()
                    .findAll {!new Cookie(it.cookie).serviceFlag }.cookie
            cookies.findAll { it == flowInfoFromDb3.forwardPath.cookie.value || it == flowInfoFromDb3.reversePath.cookie.value}.size() == 2
        }

        and: "Flow is valid and pingable"
        updatedFlow.validateAndCollectDiscrepancies().isEmpty()
        updatedFlow.pingAndCollectDiscrepancies().isEmpty()

        and: "The new and old dst switches pass switch validation"
        wait(RULES_DELETION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies([dstSwitch.getDpId(), newDstSwitch.getDpId()]).isEmpty()
        }
    }

    @Tags(LOW_PRIORITY)
    def "System reroutes flow to more preferable path while updating"() {
        given: "Two active not neighboring switches with two possible paths at least"
        def switchPair = switchPairs.all().nonNeighbouring().withAtLeastNPaths(2).random()

        and: "A flow"
        def flow = flowFactory.getRandom(switchPair)

        when: "Make the current path less preferable than alternatives"
        def initialFlowIsls = flow.retrieveAllEntityPaths().getInvolvedIsls()
        switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }.findAll { !it.containsAll(initialFlowIsls) }
                .each { islHelper.makePathIslsMorePreferable(it, initialFlowIsls) }

        and: "Update the flow"
        def newFlowDesc = flow.description + " updated"
        def flowExpectedEntity = flow.tap { it.description = newFlowDesc }
        flow.update(flowExpectedEntity)

        then: "Flow is rerouted"
        def newFlowPath
        wait(rerouteDelay + WAIT_OFFSET) {
            newFlowPath = flow.retrieveAllEntityPaths()
            assert newFlowPath.getInvolvedIsls() != initialFlowIsls
        }

        and: "Flow is updated"
        flow.retrieveDetails().description == newFlowDesc

        and: "All involved switches pass switch validation"
        def involvedSwitchIds = newFlowPath.getInvolvedSwitches()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitchIds).isEmpty()
    }

    def "System doesn't rebuild path for a flow to more preferable path while updating portNumber/vlanId"() {
        given: "Two active switches connected to traffgens with two possible paths at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()

        and: "A flow"
        def flow = flowFactory.getRandom(switchPair)

        when: "Make the current path less preferable than alternatives"
        def initialFlowPath = flow.retrieveAllEntityPaths()
        def initialFlowIsls = initialFlowPath.getInvolvedIsls()
        switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }.findAll { !it.containsAll(initialFlowIsls) }
                .each { islHelper.makePathIslsMorePreferable(it, initialFlowIsls) }

        and: "Update the flow: vlanId on the src endpoint"
        def flowExpectedEntity = flow.deepCopy().tap { it.source.vlanId = flow.destination.vlanId - 1 }
        def updatedFlow = flow.update(flowExpectedEntity)

        then: "Flow is really updated"
        updatedFlow.hasTheSamePropertiesAs(flowExpectedEntity)
        flow.waitForHistoryEvent(FlowActionType.UPDATE)

        and: "Flow path is not rebuild"
        timedLoop(rerouteDelay) {
            assert flow.retrieveAllEntityPaths().getInvolvedIsls() == initialFlowIsls
        }

        when: "Update the flow: vlanId on the dst endpoint"
        flowExpectedEntity.tap { it.destination.vlanId = flow.source.vlanId - 1 }
        updatedFlow = flow.update(flowExpectedEntity)

        then: "Flow is really updated"
        updatedFlow.hasTheSamePropertiesAs(flowExpectedEntity)
        flow.waitForHistoryEvent(FlowActionType.UPDATE)

        and: "Flow path is not rebuild"
        timedLoop(rerouteDelay) {
            assert flow.retrieveAllEntityPaths().getInvolvedIsls() == initialFlowIsls
        }

        when: "Update the flow: port number and vlanId on the src/dst endpoints"
        flowExpectedEntity.tap {
            it.source.portNumber = switchPair.getSrc().getTraffGens().first().getSwitchPort()
            it.source.vlanId = updatedFlow.source.vlanId - 1
            it.destination.portNumber = switchPair.getDst().getTraffGens().first().getSwitchPort()
            it.destination.vlanId = updatedFlow.destination.vlanId - 1
        }

        updatedFlow = flow.update(flowExpectedEntity)

        then: "Flow is really updated"
        updatedFlow.hasTheSamePropertiesAs(flowExpectedEntity)

        and: "Flow path is not rebuild"
        timedLoop(rerouteDelay + WAIT_OFFSET / 2) {
            assert updatedFlow.retrieveAllEntityPaths().getInvolvedIsls() == initialFlowIsls
        }

        and: "Flow is valid"
        updatedFlow.validateAndCollectDiscrepancies().isEmpty()

        when: "Traffic starts to flow in both direction"
        def traffExam = traffExamProvider.get()
        List<ExamReport> examReports
        def exam = updatedFlow.traffExam(traffExam, 100, 5)
        examReports = withPool {
            [exam.forward, exam.reverse].collectParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                traffExam.waitExam(direction)
            }
        }

        then: "Traffic flows in both direction"
        verifyAll {
            assert examReports.size() == 2
            assert examReports.first().hasTraffic(), examReports.first().exam
            assert examReports.last().hasTraffic(), examReports.last().exam
        }

        and: "All involved switches pass switch validation"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(initialFlowPath.getInvolvedSwitches()).isEmpty()
    }

    @Tags([TOPOLOGY_DEPENDENT, LOW_PRIORITY])
    def "System allows to update single switch flow to multi switch flow"() {
        given: "A single switch flow with enabled lldp/arp on the dst side"
        def swPair = switchPairs.all().neighbouring().random()
        def flow = flowFactory.getBuilder(swPair.src, swPair.src)
                .withDetectedDevicesOnDst(true, true).build().create()

        when: "Update the dst endpoint to make this flow as multi switch flow"
        def newPortNumber = topology.getAllowedPortsForSwitch(topology.activeSwitches
                .find {it.dpId == swPair.dst.dpId }).first()
        def flowExpectedEntity = flow.deepCopy().tap {
            it.destination.switchId = swPair.dst.dpId
            it.destination.portNumber = newPortNumber
        }
        def updatedFlow = flow.update(flowExpectedEntity)

        then: "Flow is really updated"
        updatedFlow.hasTheSamePropertiesAs(flowExpectedEntity)

        and: "Flow is valid and pingable"
        updatedFlow.validateAndCollectDiscrepancies().isEmpty()
        updatedFlow.pingAndCollectDiscrepancies().isEmpty()

        and: "Involved switches pass switch validation"
        def involvedSwitches = updatedFlow.retrieveAllEntityPaths().getInvolvedSwitches()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitches).isEmpty()
    }

    def "Unable to create a flow with both strict_bandwidth and ignore_bandwidth flags"() {
        when: "Try to create a flow with strict_bandwidth:true and ignore_bandwidth:true"
        def invalidFlowEntity = flowFactory.getBuilder(switchPairs.all().random())
                .withStrictBandwidth(true)
                .withIgnoreBandwidth(true)
                .build()
        invalidFlowEntity.create()

        then: "Bad Request response is returned"
        def error = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(
                ~/Can not turn on ignore bandwidth flag and strict bandwidth flag at the same time/).matches(error)
    }

    @Tags([LOW_PRIORITY])
    def "Unable to update flow with incorrect id in request body"() {
        given:"A flow"
        def flow = flowFactory.getRandom(switchPairs.all().random())
        def newFlowId = "new_flow_id"

        when: "Try to update flow with incorrect flow id in request body"
        def invalidFlowEntity = flow.deepCopy().tap { it.flowId = newFlowId }
        flow.update(invalidFlowEntity)

        then: "Bad Request response is returned"
        def error = thrown(HttpClientErrorException)
        new FlowNotUpdatedExpectedError("flow_id from body and from path are different",
        ~/Body flow_id: ${newFlowId}, path flow_id: ${flow.flowId}/).matches(error)
    }

    @Tags([LOW_PRIORITY])
    def "Unable to update flow with incorrect id in request path"() {
        given: "A flow"
        def flow = flowFactory.getRandom(switchPairs.all().random())
        def newFlowId = "new_flow_id"
        def invalidFlowEntity  = flow.retrieveDetails().tap { it.flowId = newFlowId }

        when: "Try to update flow with incorrect flow id in request path"
        invalidFlowEntity.update(flow.tap { maximumBandwidth = maximumBandwidth + 1 })

        then: "Bad Request response is returned"
        def error = thrown(HttpClientErrorException)
        new FlowNotUpdatedExpectedError("flow_id from body and from path are different",
                ~/Body flow_id: ${flow.getFlowId()}, path flow_id: ${newFlowId}/).matches(error)
    }

    @Tags(LOW_PRIORITY)
    def "Able to #method update with empty VLAN stats and non-zero VLANs (#5063)"() {
        given: "A flow with non empty vlans stats and with src and dst vlans set to '0'"
        def switches = switchPairs.all().random()
        def flow = flowFactory.getBuilder(switches, false)
                .withSourceVlan(0)
                .withDestinationVlan(0)
                .withStatistics( new FlowStatistics([1, 2, 3] as Set)).build()
                .create()

        when: "Try to #method update flow with empty VLAN stats and non-zero VLANs"
        def UNUSED_VLAN = 1909
        def expectedFlowEntity = flow.deepCopy().tap {
            it.source.vlanId = UNUSED_VLAN
            it.destination.vlanId = UNUSED_VLAN
            it.statistics = new FlowStatistics([] as Set)
        }
        updateCall(expectedFlowEntity)

        then: "Flow is really updated"
        def actualFlow = flow.retrieveDetails()
        actualFlow.hasTheSamePropertiesAs(expectedFlowEntity)

        def involvedSwitches = flow.retrieveAllEntityPaths().getInvolvedSwitches()
        switchHelper.validate(involvedSwitches).isEmpty()

        when: "Delete the flow"
        flow.delete()

        then: "No excess rules left on the switches (#5141)"
        switchHelper.validate(involvedSwitches).isEmpty()

        where:
        method    | updateCall
        "partial" | { FlowExtended flowExtended ->
            def invalidUpdateRequest = new FlowPatchV2().tap {
                it.source = new FlowPatchEndpoint().tap { it.vlanId = flowExtended.source.vlanId }
                it.destination = new FlowPatchEndpoint().tap { it.vlanId = flowExtended.destination.vlanId }
                it.statistics = flowExtended.statistics
            }
            flowExtended.partialUpdate(invalidUpdateRequest)
        }
        ""        | { FlowExtended flowExtended ->
            flowExtended.update(flowExtended)

        }
    }

    @Tags(LOW_PRIORITY)
    def "Unable to update to a flow with maxLatencyTier2 higher as maxLatency)"() {
        given: "A flow"
        def swPair = switchPairs.singleSwitch().random()
        def flow = flowFactory.getRandom(swPair)

        when: "Try to update the flow"
        def invalidFlowEntity = flow.tap {
            it.maxLatency = 200
            it.maxLatencyTier2 = 100
        }
        flow.update(invalidFlowEntity)

        then: "Bad Request response is returned"
        def actualException = thrown(HttpClientErrorException)
        def expectedException = new FlowNotUpdatedExpectedError(
                ~/The maxLatency \d+ms is higher than maxLatencyTier2 \d+ms/)
        expectedException.matches(actualException)
    }

    @Shared
    def errorDescription = { String operation, FlowExtended flow, String endpoint, FlowExtended conflictingFlow,
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
            boolean isTrafficAvailable = switchPair.src in topology.getActiveTraffGens().switchConnected && switchPair.dst in topology.getActiveTraffGens().switchConnected
            r << [
                    description: "flow without transit switch and with random vlans",
                    expectedFlowEntity       : flowFactory.getBuilder(switchPair).build(),
                    isTrafficCheckAvailable : isTrafficAvailable
            ]
            r << [
                    description: "flow without transit switch and without vlans(full port)",
                    expectedFlowEntity       : flowFactory.getBuilder(switchPair).withSourceVlan(0).withDestinationVlan(0).build(),
                    isTrafficCheckAvailable : isTrafficAvailable
            ]
            r << [
                    description: "flow without transit switch and vlan only on src",
                    expectedFlowEntity       : flowFactory.getBuilder(switchPair).withDestinationVlan(0).build(),
                    isTrafficCheckAvailable : isTrafficAvailable
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
            boolean isTrafficAvailable = switchPair.src in topology.getActiveTraffGens().switchConnected && switchPair.dst in topology.getActiveTraffGens().switchConnected
            r << [
                    description: "flow with transit switch and random vlans",
                    expectedFlowEntity       : flowFactory.getBuilder(switchPair).build(),
                    isTrafficCheckAvailable : isTrafficAvailable
            ]
            r << [
                    description: "flow with transit switch and no vlans(full port)",
                    expectedFlowEntity       : flowFactory.getBuilder(switchPair).withSourceVlan(0).withDestinationVlan(0).build(),
                    isTrafficCheckAvailable : isTrafficAvailable
            ]
            r << [
                    description: "flow with transit switch and vlan only on dst",
                    expectedFlowEntity       : flowFactory.getBuilder(switchPair).withSourceVlan(0).build(),
                    isTrafficCheckAvailable : isTrafficAvailable
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
                    boolean isTrafficAvailable = topology.getActiveTraffGens().findAll { it.switchConnected == sw }.size() == 2

                    r << [
                            description: "single-switch flow with vlans",
                            expectedFlowEntity       : flowFactory.getBuilder(sw, sw).build(),
                            isTrafficCheckAvailable : isTrafficAvailable
                    ]
                    r << [
                            description: "single-switch flow without vlans",
                            // if sw has only one tg than full port flow cannot be created on the same tg_port(conflict)
                            expectedFlowEntity       : isTrafficAvailable ?
                                    flowFactory.getBuilder(sw, sw).withSourceVlan(0).withDestinationVlan(0).build() :
                                    flowFactory.getBuilder(sw, sw, false).withSourceVlan(0).withDestinationVlan(0).build(),
                            isTrafficCheckAvailable : isTrafficAvailable

                    ]
                    r << [
                            description: "single-switch flow with vlan only on dst",
                            expectedFlowEntity       : flowFactory.getBuilder(sw, sw).withSourceVlan(0).build(),
                            isTrafficCheckAvailable : isTrafficAvailable
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
                .collect {singleSwitch ->
                    flowFactory.getBuilder(singleSwitch, singleSwitch).withSamePortOnSourceAndDestination().build()
                }
    }

    def getConflictingData() {
        [
                [
                        conflict            : "the same vlans on the same port on src switch",
                        makeFlowsConflicting: { FlowExtended dominantFlow, FlowBuilder flowToConflict ->
                            return flowToConflict.withSourcePort(dominantFlow.source.portNumber)
                                    .withSourceVlan(dominantFlow.source.vlanId).build()
                        },
                        getErrorDescription : { FlowExtended dominantFlow, FlowExtended flowToConflict,
                                                String operation = "create" ->
                            errorDescription(operation, dominantFlow, "source", flowToConflict, "source")
                        }
                ],
                [
                        conflict            : "the same vlans on the same port on dst switch",
                        makeFlowsConflicting: { FlowExtended dominantFlow, FlowBuilder flowToConflict ->
                            return flowToConflict.withDestinationPort(dominantFlow.destination.portNumber)
                                    .withDestinationVlan(dominantFlow.destination.vlanId).build()
                        },
                        getErrorDescription : { FlowExtended dominantFlow, FlowExtended flowToConflict,
                                                String operation = "create" ->
                            errorDescription(operation, dominantFlow, "destination", flowToConflict, "destination")
                        }
                ],
                [
                        conflict            : "no vlan, both flows are on the same port on src switch",
                        makeFlowsConflicting: { FlowExtended dominantFlow, FlowBuilder flowToConflict ->
                            dominantFlow.tap { source.vlanId = 0 }
                            return flowToConflict.withSourcePort(dominantFlow.source.portNumber)
                                    .withSourceVlan(0).build()

                        },
                        getErrorDescription : { FlowExtended dominantFlow, FlowExtended flowToConflict,
                                                String operation = "create" ->
                            errorDescription(operation, dominantFlow, "source", flowToConflict, "source")
                        }
                ],
                [
                        conflict            : "no vlan, both flows are on the same port on dst switch",
                        makeFlowsConflicting: { FlowExtended dominantFlow, FlowBuilder flowToConflict ->
                            dominantFlow.tap { it.destination.vlanId = 0 }
                            return flowToConflict.withDestinationPort(dominantFlow.destination.portNumber)
                                    .withDestinationVlan(0).build()
                        },
                        getErrorDescription : { FlowExtended dominantFlow, FlowExtended flowToConflict,
                                                String operation = "create" ->
                            errorDescription(operation, dominantFlow, "destination", flowToConflict, "destination")
                        }
                ],
                [
                        conflict            : "the same flow ID",
                        makeFlowsConflicting: { FlowExtended dominantFlow, FlowBuilder flowToConflict ->
                            return flowToConflict.withFlowId(dominantFlow.flowId).build()
                        },
                        getErrorDescription : { FlowExtended dominantFlow, FlowExtended flowToConflict ->
                            "Flow $dominantFlow.flowId already exists"
                        }
                ]
        ]
    }
}
