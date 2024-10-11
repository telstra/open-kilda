package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_PROPS_DB_RESET
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.MOVED
import static org.openkilda.messaging.payload.flow.FlowState.UP
import static org.openkilda.model.MeterId.MIN_FLOW_METER_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW
import static org.openkilda.testing.tools.KafkaUtils.buildMessage

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowNotCreatedExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotCreatedWithConflictExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotCreatedWithMissingPathExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotDeletedExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotUpdatedExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotUpdatedWithConflictExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.builder.FlowBuilder
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.functionaltests.model.stats.Direction

import org.openkilda.model.MeterId
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.rulemanager.MeterFlag
import org.openkilda.rulemanager.MeterSpeakerData
import org.openkilda.rulemanager.OfVersion
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.TraffExamService

import com.google.common.collect.Sets
import groovy.util.logging.Slf4j
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared

import java.time.Instant
import jakarta.inject.Provider

@Slf4j
@See(["https://github.com/telstra/open-kilda/blob/develop/docs/design/usecase/flow-crud-create-full.png",
        "https://github.com/telstra/open-kilda/blob/develop/docs/design/usecase/flow-crud-delete-full.png"])
@Narrative("Verify CRUD operations and health of most typical types of flows on different types of switches.")
@Tags([LOW_PRIORITY])
class FlowCrudV1Spec extends HealthCheckSpecification {
    @Autowired
    @Shared
    FlowFactory flowFactory
    @Autowired
    @Shared
    Provider<TraffExamService> traffExamProvider

    @Value("#{kafkaTopicsConfig.getSpeakerSwitchManagerTopic()}")
    @Shared
    String speakerTopic

    @Autowired
    @Shared
    @Qualifier("kafkaProducerProperties")
    Properties producerProps

    //pure v1
    @Shared
    def getPortViolationError = { String endpoint, int port, SwitchId swId ->
        ~/The port $port on the switch \'$swId\' is occupied by an ISL \($endpoint endpoint collision\)./
    }

    @Tags([TOPOLOGY_DEPENDENT])
    def "Valid #data.description has been created (#verifyTraffic) and no rule discrepancies [#srcDstStr]"() {
        given: "A flow"
        assumeTrue(topology.activeTraffGens.size() >= 2,
"There should be at least two active traffgens for test execution")
        def allLinksInfoBefore = northbound.getAllLinks().collectEntries { [it.id, it.availableBandwidth] }.sort()

        def flow = expectedFlowEntity.createV1()
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
        flow.deleteV1()

        then: "The flow is not present in NB"
        !northbound.getAllFlows().find { it.id == flow.flowId }

        and: "ISL bandwidth is restored"
        Wrappers.wait(WAIT_OFFSET) {
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
        flows.v1.createV1()

        and: "Try creating a second flow with #data.description"
        flows.v2.createV1()

        then: "Both flows are successfully created"
        northbound.getAllFlows()*.id.containsAll(flows*.flowId)

        where:
        data << [
                [
                        description  : "same switch-port but vlans on src and dst are swapped",
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
                        description  : "same switch-port but vlans on src and dst are different",
                        getNotConflictingFlows: {
                            def (Switch srcSwitch, Switch dstSwitch) = getTopology().activeSwitches
                            def expectedFlow1Entity = flowFactory.getBuilder(srcSwitch, dstSwitch).build()
                            def expectedFlow2Entity = flowFactory.getBuilder(srcSwitch, dstSwitch)
                                    .withSourcePort(expectedFlow1Entity.source.portNumber)
                                    .withSourceVlan(expectedFlow1Entity.source.vlanId + 1)
                                    .withDestinationPort(expectedFlow1Entity.destination.portNumber)
                                    .withDestinationVlan(expectedFlow1Entity.destination.vlanId + 1)
                                    .build()
                            return new Tuple2<FlowExtended, FlowExtended>(expectedFlow1Entity, expectedFlow2Entity)
                        }
                ],
                [
                        description  : "vlan-port of new src = vlan-port of existing dst (+ different src)",
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
                                    .withDestinationVlan(expectedFlow1Entity.destination.vlanId + 1) //ensure no conflict on dst
                                    .build()
                            return new Tuple2<FlowExtended, FlowExtended>(expectedFlow1Entity, expectedFlow2Entity)
                        }
                ],
                [
                        description  : "vlan-port of new dst = vlan-port of existing src (but different switches)",
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
                        description  : "vlan of new dst = vlan of existing src and port of new dst = port of " +
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
                        description  : "default and tagged flows on the same port on dst switch",
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
                        description  : "default and tagged flows on the same port on src switch",
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
                        description  : "tagged and default flows on the same port on dst switch",
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
                        description  : "tagged and default flows on the same port on src switch",
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
                        description  : "default and tagged flows on the same ports on src and dst switches",
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
                        description  : "tagged and default flows on the same ports on src and dst switches",
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

    @Tags([TOPOLOGY_DEPENDENT])
    def "Able to create single switch single port flow with different vlan (#expectedFlowEntity.source.switchId.description)"(FlowExtended expectedFlowEntity) {
        given: "A flow"
        def flow = expectedFlowEntity.createV1()

        expect: "No rule discrepancies on the switch"
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(flow.source.switchId).isPresent()

        and: "No discrepancies when doing flow validation"
        flow.validateAndCollectDiscrepancies().isEmpty()

        when: "Remove the flow"
        flow.deleteV1()

        then: "The flow is not present in NB"
        !northbound.getAllFlows().find { it.id == flow.flowId }

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
        def flow = expectedFlowEntity.createV1()

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
        invalidFlowEntity.createV1()

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
        FlowExtended conflictingFlow = data.makeFlowsConflicting(expectedFlowEntity, flowFactory.getBuilder(srcSwitch, dstSwitch))

        when: "Create the first flow"
        def flow = expectedFlowEntity.createV1()

        and: "Try creating the second flow which conflicts"
        conflictingFlow.createV1()

        then: "Error is returned, stating a readable reason of conflict"
        def error = thrown(HttpClientErrorException)
        new FlowNotCreatedWithConflictExpectedError(~/${data.getErrorDescription(flow, conflictingFlow)}/).matches(error)

        where:
        data << getConflictingData() + [
                [
                        conflict            : "the same flow ID",
                        makeFlowsConflicting: { FlowExtended dominantFlow, FlowBuilder flowToConflict ->
                            flowToConflict.withFlowId(dominantFlow.flowId).build()
                        },
                        getErrorDescription            : { FlowExtended dominantFlow, FlowExtended flowToConflict ->
                            ~/Flow $dominantFlow.flowId already exists/
                        }
                ]
        ]
    }

    def "Unable to update flow when there are conflicting vlans((#data.conflict))"() {
        given: "Two potential flows"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches

        def expectedFlowEntity = flowFactory.getBuilder(srcSwitch, dstSwitch, false).build()
        FlowExtended conflictingFlow = data.makeFlowsConflicting(expectedFlowEntity,
                flowFactory.getBuilder(srcSwitch, dstSwitch, false, expectedFlowEntity.occupiedEndpoints()))

        when: "Create two flows"
        def flow = expectedFlowEntity.createV1()
        def flow2 = flowFactory.getRandomV1(srcSwitch, dstSwitch, false, UP, flow.occupiedEndpoints())

        and: "Try updating the second flow which should conflict with the first one"
        flow2.updateV1(conflictingFlow.tap {it.flowId = flow2.flowId })

        then: "Error is returned, stating a readable reason of conflict"
        def error = thrown(HttpClientErrorException)
        new FlowNotUpdatedWithConflictExpectedError(data.getErrorDescription(flow, conflictingFlow, "update")).matches(error)

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
            availablePathsIsls.size() > 1 && availablePathsIsls.find { it.size() > shortestIslCountPath }
        } ?: assumeTrue(false, "No suiting active neighboring switches with two possible flow paths at least and " +
                "different number of hops found")

        and: "Make all shorter forward paths not preferable. Shorter reverse paths are still preferable"
        List<Isl> modifiedIsls = availablePathsIsls.findAll { it.size() == shortestIslCountPath }.flatten().unique() as List<Isl>
        islHelper.updateIslsCostInDatabase(modifiedIsls, Integer.MAX_VALUE)

        when: "Create a flow"
        def flow = flowFactory.getRandomV1(swPair)

        then: "The flow is built through one of the long paths"
        def fullFlowPath = flow.retrieveAllEntityPaths()
        def forwardIsls = fullFlowPath.flowPath.getInvolvedIsls(Direction.FORWARD)
        def reverseIsls = fullFlowPath.flowPath.getInvolvedIsls(Direction.REVERSE)
        assert forwardIsls.intersect(modifiedIsls).isEmpty()


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
        invalidFlow.createV1()

        then: "Error is returned, stating that there is no path found for such flow"
        def error = thrown(HttpClientErrorException)
        new FlowNotCreatedWithMissingPathExpectedError(~/Not enough bandwidth or no path found. Switch \
${isolatedSwitch.dpId.toString()} doesn\'t have links with enough bandwidth, \
Failed to find path with requested bandwidth=$invalidFlow.maximumBandwidth/).matches(error)

        where:
        data << [
                [
                        isolatedSwitchType: "source",
                        switchPair           : { Switch theSwitch ->
                           switchPairs.all()
                                    .nonNeighbouring()
                                    .includeSourceSwitch(theSwitch)
                                    .random()
                        }
                ],
                [
                        isolatedSwitchType: "destination",
                        switchPair           : { Switch theSwitch ->
                           switchPairs.all()
                                    .nonNeighbouring()
                                    .includeSourceSwitch(theSwitch)
                                    .random()
                                    .getReversed()
                        }
                ]
        ]
    }

    def "Removing flow while it is still in progress of being created should not cause rule discrepancies"() {
        given: "A potential flow"
        def switchPair = switchPairs.all().neighbouring().random()
        def availablePaths = switchPair.retrieveAvailablePaths()
        def flowExpectedPath = availablePaths.min { islHelper.getCost(it.getInvolvedIsls()) }
        def switchesId = flowExpectedPath.getInvolvedSwitches()

        when: "Init creation of a new flow"
        def flow = flowFactory.getBuilder(switchPair).build().sendCreateRequestV1()

        and: "Immediately remove the flow"
        flow.sendDeleteRequestV1()

        then: "System returns error as being unable to remove in progress flow"
        def e = thrown(HttpClientErrorException)
        new FlowNotDeletedExpectedError(~/Flow ${flow.flowId} is in progress now/).matches(e)

        and: "Flow is not removed"
        northbound.getAllFlows()*.id.contains(flow.flowId)

        and: "Flow eventually gets into UP state"
        flow.waitForBeingInState(UP)
        flow.retrieveAllEntityPaths().flowPath.getInvolvedIsls() == flowExpectedPath.getInvolvedIsls()

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

    def "Unable to create a flow on an isl port in case port is occupied on a #data.switchType switch"() {
        given: "An isl"
        Isl isl = topology.islsForActiveSwitches.find { it.aswitch && it.dstSwitch }
        assumeTrue(isl as boolean, "Unable to find required isl")

        when: "Try to create a flow using isl port"
        def invalidFlowEntity = flowFactory.getBuilder(isl.srcSwitch, isl.dstSwitch).build()
        invalidFlowEntity."$data.switchType".portNumber = isl."$data.port"
        invalidFlowEntity.createV1()

        then: "Flow is not created"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(data.errorDescription(isl)).matches(exc)

        where:
        data << [
                [
                        switchType: "source",
                        port      : "srcPort",
                        errorDescription   : { Isl violatedIsl ->
                            getPortViolationError("source", violatedIsl.srcPort, violatedIsl.srcSwitch.dpId)
                        }
                ],
                [
                        switchType: "destination",
                        port      : "dstPort",
                        errorDescription   : { Isl violatedIsl ->
                            getPortViolationError("destination", violatedIsl.dstPort, violatedIsl.dstSwitch.dpId)
                        }
                ]
        ]
    }

    def "Unable to update a flow in case new port is an isl port on a #data.switchType switch"() {
        given: "An isl"
        Isl isl = topology.islsForActiveSwitches.find { it.aswitch && it.dstSwitch }
        assumeTrue(isl as boolean, "Unable to find required isl")

        and: "A flow has been created"
        def flow = flowFactory.getBuilder(isl.srcSwitch, isl.dstSwitch).build().createV1()

        when: "Try to edit port to isl port"
        flow.updateV1(flow.tap {it."$data.switchType".portNumber = isl."$data.port" })

        then:
        def exc = thrown(HttpClientErrorException)
        new FlowNotUpdatedExpectedError(data.message(isl)).matches(exc)

        where:
        data << [
                [
                        switchType: "source",
                        port      : "srcPort",
                        message   : { Isl violatedIsl ->
                            getPortViolationError("source", violatedIsl.srcPort, violatedIsl.srcSwitch.dpId)
                        }
                ],
                [
                        switchType: "destination",
                        port      : "dstPort",
                        message   : { Isl violatedIsl ->
                            getPortViolationError("destination", violatedIsl.dstPort, violatedIsl.dstSwitch.dpId)
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
        invalidFlowEntity.createV1()

        then: "Flow is not created"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(getPortViolationError("source", isl.srcPort, isl.srcSwitch.dpId)).matches(exc)
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
        Wrappers.wait(discoveryExhaustedInterval + WAIT_OFFSET) {
            [newIsl, newIsl.reversed].each { assert northbound.getLink(it).state == DISCOVERED }
        }
        def islIsMoved = true

        when: "Try to create a flow using ISL src port"
        def invalidFlowEntity = flowFactory.getBuilder(isl.srcSwitch, isl.dstSwitch).withSourcePort(isl.srcPort).build()
        invalidFlowEntity.createV1()

        then: "Flow is not created"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(getPortViolationError("source", isl.srcPort, isl.srcSwitch.dpId)).matches(exc)
    }

    def "Able to CRUD #flowDescription pinned flow"() {
        when: "Create a flow(pinned=true)"
        FlowExtended flow = flowBuilder().withPinned(true).build()
                .createV1()

        then: "Pinned flow is created"
        def flowInfo = flow.retrieveDetailsV1()
        flowInfo.pinned

        when: "Update the flow (pinned=false)"
        flow.updateV1(flow.tap{ it.pinned = false })

        then: "The pinned option is disabled"
        def newFlowInfo = flow.retrieveDetailsV1()
        !newFlowInfo.pinned
        Instant.parse(flowInfo.lastUpdated) < Instant.parse(newFlowInfo.lastUpdated)

        where:
        flowDescription               | flowBuilder
        "a metered(single switch)"    | {
            def sw = topology.activeSwitches.first()
            return flowFactory.getBuilder(sw, sw, false).withBandwidth(1000).withIgnoreBandwidth(false)
        }
        "an unmetered(single switch)" | {
            def sw = topology.activeSwitches.first()
            return flowFactory.getBuilder(sw, sw, false).withBandwidth(0).withIgnoreBandwidth(true)
        }
        "a metered"                   | {
            def (srcSwitch, dstSwitch) = topology.activeSwitches
            return flowFactory.getBuilder(srcSwitch, dstSwitch, false).withBandwidth(1000).withIgnoreBandwidth(false)
        }
        "an unmetered"                | {
            def (srcSwitch, dstSwitch) = topology.activeSwitches
            return flowFactory.getBuilder(srcSwitch, dstSwitch, false).withBandwidth(0).withIgnoreBandwidth(true)
        }
    }

    @Tags([VIRTUAL, SWITCH_RECOVER_ON_FAIL])
    def "System doesn't allow to create a one-switch flow on a DEACTIVATED switch"() {
        given: "A deactivated switch"
        def sw = topology.getActiveSwitches().first()
        switchHelper.knockoutSwitch(sw, RW)

        when: "Create a flow"
        flowFactory.getBuilder(sw, sw).build().createV1()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(~/Source switch ${sw.getDpId()} and Destination switch ${sw.getDpId()} \
are not connected to the controller/).matches(exc)
    }

    @Ignore("https://github.com/telstra/open-kilda/issues/2625")
    def "System recreates excess meter when flow is created with the same meterId"() {
        given: "A Noviflow switch"
        def sw = topology.activeSwitches.find { it.noviflow || it.virtual } ?:
                assumeTrue(false, "No suiting switch found")

        and: "Create excess meters on the given switch"
        def fakeBandwidth = 333
        def amountOfExcessMeters = 10
        def producer = new KafkaProducer(producerProps)
        def excessMeterIds = ((MIN_FLOW_METER_ID..100) - northbound.getAllMeters(sw.dpId)
                .meterEntries*.meterId).take(amountOfExcessMeters)
        excessMeterIds.each { meterId ->
            producer.send(new ProducerRecord(speakerTopic, sw.dpId.toString(), buildMessage(
                    MeterSpeakerData.builder()
                        .switchId(sw.dpId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .meterId(new MeterId(meterId))
                        .rate(fakeBandwidth)
                        .burst(fakeBandwidth)
                        .flags(Sets.newHashSet(MeterFlag.PKTPS, MeterFlag.BURST, MeterFlag.STATS))
                        .build()).toJson())).get()
        }
        producer.close()

        assert switchHelper.validate(sw.dpId).meters.excess.size() == amountOfExcessMeters

        when: "Create several flows"
        List<FlowExtended> flows = []
        def amountOfFlows = 5
        amountOfFlows.times {
            List<SwitchPortVlan> occupiedEndpoints = flows.collectMany { it.occupiedEndpoints()}
            def flow = flowFactory.getBuilder(sw, sw, false, occupiedEndpoints).build().createV1()
            flows << flow
        }

        then: "Needed amount of flow was created"
        northbound.getAllFlows().size() == amountOfFlows

        and: "Needed amount of meter was recreated"
        /* system knows nothing about excess meters
         while creating one-switch flow system should create two meters
         system checks database and assumes that there is no meter which is used by flow
         as a result system creates flow with first available meters (32 and 33)
         in reality we've already created excess meters 32..42
         excess meters should NOT be just allocated to the created flow
         they should be recreated(burst size should be recalculated) */
        def validateSwitchInfo = switchHelper.validate(sw.dpId)
        validateSwitchInfo.isAsExpected()
        validateSwitchInfo.meters.proper.size() == amountOfFlows * 2 // one flow creates two meters
    }

    @Tags([ISL_RECOVER_ON_FAIL, ISL_PROPS_DB_RESET])
    def "System doesn't create flow when reverse path has different bandwidth than forward path on the second link"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all().nonNeighbouring().withPathHavingAtLeastNSwitches(4).random()

        and: "Select path for further manipulation with it"
        def availablePathsIsls = switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }
        def desiredPathMaxIsls = availablePathsIsls.max { it.size() }

        and: "Make all alternative paths unavailable (bring links down on the src/intermediate switches)"
        def untouchableIsls = desiredPathMaxIsls.collectMany { [it, it.reversed] }
        def altPaths = availablePathsIsls.findAll { !it.containsAll(desiredPathMaxIsls)}
        def islsToBreak = altPaths.flatten().unique().collectMany { [it, it.reversed] }
                .findAll { !untouchableIsls.contains(it) }.unique { [it, it.reversed].sort() } as List<Isl>
        islHelper.breakIsls(islsToBreak)

        and: "Update reverse path to have not enough bandwidth to handle the flow"
        //Forward path is still have enough bandwidth
        def flowBandwidth = 500
        Isl islsToModify = desiredPathMaxIsls[1]
        def newIslBandwidth = flowBandwidth - 1
        islHelper.setAvailableAndMaxBandwidth([islsToModify.reversed], newIslBandwidth)

        when: "Create a flow"
        flowFactory.getBuilder(switchPair).withBandwidth(flowBandwidth).build().createV1()

        then: "Flow is not created"
        def e = thrown(HttpClientErrorException)
        new FlowNotCreatedWithMissingPathExpectedError(~/Not enough bandwidth or no path found./).matches(e)
    }

    //this is for v1 mapped to h&s
    @Shared
    def errorDescription = { String operation, FlowExtended flow, String endpoint, FlowExtended conflictingFlow,
            String conflictingEndpoint ->
        def message = "Requested flow '$conflictingFlow.flowId' " +
                "conflicts with existing flow '$flow.flowId'. " +
                "Details: requested flow '$conflictingFlow.flowId' $conflictingEndpoint: " +
                "switchId=\"${conflictingFlow."$conflictingEndpoint".switchId}\" " +
                "port=${conflictingFlow."$conflictingEndpoint".portNumber}"
        if (0 < conflictingFlow."$conflictingEndpoint".vlanId) {
            message += " vlanId=${conflictingFlow."$conflictingEndpoint".vlanId}";
        }
        message += ", existing flow '$flow.flowId' $endpoint: " +
                "switchId=\"${flow."$endpoint".switchId}\" " +
                "port=${flow."$endpoint".portNumber}"
        if (0 < flow."$endpoint".vlanId) {
            message += " vlanId=${flow."$endpoint".vlanId}"
        }
        return ~/${message.replaceAll('"', '.').replaceAll('\'', '.')}/
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
                    description: "flow with transit switch and no vlans",
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
                .collect { singleSwitch ->
                    flowFactory.getBuilder(singleSwitch, singleSwitch).withSamePortOnSourceAndDestination().build() }
    }

    def getConflictingData() {
        [
                [
                        conflict            : "the same vlans on the same port on src switch",
                        makeFlowsConflicting: { FlowExtended dominantFlow, FlowBuilder flowToConflict ->
                            return flowToConflict.withSourcePort(dominantFlow.source.portNumber)
                                    .withSourceVlan(dominantFlow.source.vlanId).build()
                        },
                        getErrorDescription        : { FlowExtended dominantFlow, FlowExtended flowToConflict,
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
                        getErrorDescription        : { FlowExtended dominantFlow, FlowExtended flowToConflict,
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
                        getErrorDescription        : { FlowExtended dominantFlow, FlowExtended flowToConflict,
                                                       String operation = "create" ->
                            errorDescription(operation, dominantFlow, "source", flowToConflict, "source")
                        }
                ],
                [
                        conflict            : "no vlan, both flows are on the same port on dst switch",
                        makeFlowsConflicting : { FlowExtended dominantFlow, FlowBuilder flowToConflict ->
                            dominantFlow.tap { it.destination.vlanId = 0 }
                            return flowToConflict.withDestinationPort(dominantFlow.destination.portNumber)
                                    .withDestinationVlan(0).build()
                        },
                        getErrorDescription        : { FlowExtended dominantFlow, FlowExtended flowToConflict,
                                                       String operation = "create" ->
                            errorDescription(operation, dominantFlow, "destination", flowToConflict, "destination")
                        }
                ]
        ]
    }
}
