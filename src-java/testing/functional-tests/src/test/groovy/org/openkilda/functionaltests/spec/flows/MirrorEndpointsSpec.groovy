package org.openkilda.functionaltests.spec.flows


import static org.junit.jupiter.api.Assumptions.assumeFalse
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.FlowNameGenerator.FLOW
import static org.openkilda.functionaltests.helpers.model.FlowEncapsulationType.TRANSIT_VLAN
import static org.openkilda.functionaltests.helpers.model.FlowEncapsulationType.VXLAN
import static org.openkilda.functionaltests.helpers.model.SwitchExtended.randomVlan
import static org.openkilda.functionaltests.helpers.model.Switches.synchronizeAndCollectFixedDiscrepancies
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.OTHER
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_RAW_BYTES
import static org.openkilda.model.FlowPathDirection.FORWARD
import static org.openkilda.model.FlowPathDirection.REVERSE
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.AbstractExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotCreatedWithConflictExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotUpdatedExpectedError
import org.openkilda.functionaltests.error.flowmirror.FlowMirrorPointNotCreatedExpectedError
import org.openkilda.functionaltests.error.flowmirror.FlowMirrorPointNotCreatedWithConflictExpectedError
import org.openkilda.functionaltests.error.switchproperties.SwitchPropertiesNotUpdatedExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowActionType
import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.functionaltests.model.stats.FlowStats
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.FlowPathDirection
import org.openkilda.model.FlowPathStatus
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.flows.DetectConnectedDevicesV2
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointPayload
import org.openkilda.northbound.dto.v2.flows.FlowPatchEndpoint
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.Exam
import org.openkilda.testing.service.traffexam.model.FlowBidirectionalExam
import org.openkilda.testing.tools.TraffgenStats

import groovy.transform.AutoClone
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.See
import spock.lang.Shared

import java.util.regex.Pattern
import javax.inject.Provider

@Slf4j
@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/flow-traffic-mirroring")

class MirrorEndpointsSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    Provider<TraffExamService> traffExamProvider
    @Autowired
    @Shared
    FlowStats flowStats
    @Autowired
    @Shared
    CleanupManager cleanupManager
    @Autowired
    @Shared
    FlowFactory flowFactory

    def setupSpec() {
        deleteAnyFlowsLeftoversIssue5480()
    }

    @Tags([SMOKE, SMOKE_SWITCHES, TOPOLOGY_DEPENDENT])
    def "Able to CRUD a mirror endpoint on the src switch, mirror to the same switch diff port #description"() {
        given: "A flow"
        assumeTrue(swPair as boolean, "Unable to find a switch pair")
        def flowEntity = flowFactory.getBuilder(swPair).withBandwidth(100000)
        if (profile == 'virtual') {
            // ovs switch doesn't support mirroring for the vxlan flows
            flowEntity.withEncapsulationType(TRANSIT_VLAN)
        }
        def flow = flowEntity.build().create()

        when: "Create a mirror point on src switch, pointing to a different port, random vlan"
        def mirrorPort = swPair.src.getTgPortIfPresentOrRandom([flow.source.portNumber])
        def mirrorTg = topology.traffGens.find { it.switchConnected.dpId == swPair.src.switchId && it.switchPort == mirrorPort }

        def mirrorPointPayload = flow.buildMirrorPointPayload(
                flow.source.switchId, mirrorPort, randomVlan(), mirrorDirection as FlowPathDirection
        )
        flow.createMirrorPointWithPayload(mirrorPointPayload)

        then: "Mirror status changes to Active"
        Wrappers.wait(WAIT_OFFSET) {
            assert flow.retrieveDetails().mirrorPointStatuses[0].status ==
                    FlowPathStatus.ACTIVE.toString().toLowerCase()
        }

        and: "Flow history reports a successful mirror creation"
        flow.waitForHistoryEvent(FlowActionType.CREATE_MIRROR)

        and: "Mirror endpoint is visible in 'get flows', 'get single flow' and 'get mirror endpoint' APIs"
        def allFlows = northboundV2.getAllFlows()
        def gotFlow = flow.retrieveDetails()
        allFlows.size() == 1
        [allFlows[0].mirrorPointStatuses, gotFlow.mirrorPointStatuses].each { mirrorPointsDetails ->
            assert mirrorPointsDetails.size() == 1
            assert mirrorPointsDetails[0].status == FlowPathStatus.ACTIVE.toString().toLowerCase()
            assert mirrorPointsDetails[0].mirrorPointId == mirrorPointPayload.mirrorPointId
        }
        def flowMirrorDetails = flow.retrieveMirrorPoints()
        assert flowMirrorDetails.points.size() == 1
        verifyAll(flowMirrorDetails.points.first()) {
            assert mirrorPointId == mirrorPointPayload.mirrorPointId
            assert mirrorPointDirection == mirrorPointPayload.mirrorPointDirection
            assert mirrorPointSwitchId == mirrorPointPayload.mirrorPointSwitchId
            assert sinkEndpoint == mirrorPointPayload.sinkEndpoint
        }

        and: "Mirror flow rule has an OF group action and higher prio than flow rule"
        def mirrorRule = swPair.src.rulesManager.getFlowRules(mirrorDirection, true)
        def flowRule = swPair.src.rulesManager.getFlowRules(mirrorDirection)
        def groupId = mirrorRule.instructions.applyActions.group
        mirrorRule.priority > flowRule.priority
        !groupId.empty

        and: "Flow rule of opposite direction does not have an OF group action and there is no mirror flow rule"
        def oppositeDirection = mirrorDirection == FORWARD ? REVERSE : FORWARD
        swPair.src.rulesManager.getFlowRules(oppositeDirection).instructions.applyActions.group == null
        swPair.src.rulesManager.getFlowRules(oppositeDirection, true) == null

        and: "Related switches and flow pass validation"
        def flowInvolvedSwitches = switches.all().findSwitchesInPath(flow.retrieveAllEntityPaths())
        synchronizeAndCollectFixedDiscrepancies(flowInvolvedSwitches).isEmpty()
        flow.validateAndCollectDiscrepancies().isEmpty()

        when: "Traffic briefly runs through the flow"
        def traffExam = traffExamProvider.get()
        def mirrorPortStats = mirrorTg ? new TraffgenStats(traffExam, mirrorTg, [mirrorPointPayload.sinkEndpoint.vlanId]) : null
        if (mirrorPortStats) {
            cleanupManager.addAction(OTHER, {mirrorPortStats.close()})
        }
        def rxPacketsBefore = mirrorPortStats?.get()?.rxPackets
        if (!trafficDisclaimer) {
            sendTrafficAndVerifyOnMainFlow(traffExam, flow, mirrorDirection)
            statsHelper."force kilda to collect stats"()
        }

        then: "OF group reports same amount of packets sent both to mirror and to main paths"
        def fl = flHelper.getFlsByRegions(swPair.src.getRegions())[0].floodlightService
        if (!trafficDisclaimer) {
            Wrappers.wait(2) { //leftover packets after traffexam may not be counted still, require a retry sometimes
                mirrorRule = swPair.src.rulesManager.getFlowRules(mirrorDirection, true)
                assert mirrorRule.packetCount > 0
                def mirrorGroup = fl.getGroupsStats(swPair.src.switchId).group.find { it.groupNumber == groupId }
                mirrorGroup.bucketCounters.each { assert it.packetCount.toLong() == mirrorRule.packetCount }
            }
        }

        and: "Original flow rule counter is not increased"
        flowRule.packetCount == swPair.src.rulesManager.getFlowRules(mirrorDirection).packetCount

        and: "System collects stat for mirror cookie in tsdb"
        if (!trafficDisclaimer) {
            Wrappers.wait(statsRouterRequestInterval) {
                flowStats.of(flow.flowId).get(FLOW_RAW_BYTES, mirrorRule.cookie).hasNonZeroValues()
            }
        }

        and: "Traffic is also received at the mirror point (check only if second tg available)"
        //https://github.com/telstra/open-kilda/issues/5420
        if (mirrorTg && !swPair.src.isWb5164()) {
            assert mirrorPortStats.get().rxPackets - rxPacketsBefore > 0
        }

        when: "Delete the mirror point"
        flow.deleteMirrorPoint(mirrorPointPayload.mirrorPointId)

        then: "'Mirror point delete' operation is present in flow history"
        flow.waitForHistoryEvent(FlowActionType.DELETE_MIRROR)

        and: "Mirror point is no longer present in flow and mirror APIs"
        assert flow.retrieveDetails().mirrorPointStatuses.empty
        northboundV2.getAllFlows()[0].mirrorPointStatuses.empty
        flow.retrieveMirrorPoints().points.empty

        and: "Mirror flow rule is removed and flow rule is intact"
        !swPair.src.rulesManager.getFlowRules(mirrorDirection, true)
        swPair.src.rulesManager.getFlowRules(mirrorDirection)

        and: "OF group is removed"
        !fl.getGroupsStats(swPair.src.switchId).group.find { it.groupNumber == groupId }

        and: "Src switch and flow pass validation"
        !swPair.src.synchronizeAndCollectFixedDiscrepancies().isPresent()
        flow.validateAndCollectDiscrepancies().isEmpty()

        when: "Delete the flow"
        flow.delete()

        then: "Src switch pass validation"
        !swPair.src.synchronizeAndCollectFixedDiscrepancies().isPresent()

        where:
        [swPair, mirrorDirection] << [getUniqueSwitchPairs({ !it.src.traffGenPorts.isEmpty() && !it.dst.traffGenPorts.isEmpty() }),
                                      [FORWARD, REVERSE]].combinations()
        //means there is no second traffgen for target switch and we are not checking the counter on receiving interface
        trafficDisclaimer = swPair.src.traffGenPorts.size() < 2 ? " !WARN: No mirrored traffic check!" : ""
        description = "[${swPair.src.hwSwString()}] $mirrorDirection $trafficDisclaimer"
    }

    @Tags([LOW_PRIORITY])
    def "Can create mirror point on protected flow and survive path swap, #mirrorDirection"() {
        given: "A flow with protected path"
        SwitchPair swPair = switchPairs.all()
                .withTraffgensOnBothEnds()
                .withAtLeastNTraffgensOnSource(2)
                .withAtLeastNNonOverlappingPaths(2)
                .random()
        def flow = flowFactory.getBuilder(swPair)
                .withProtectedPath(true)
                .build().create()

        when: "Create a mirror point"
        def mirrorTg = topology.getTraffGen(swPair.src.switchId, swPair.src.getTgPortIfPresentOrRandom([flow.source.portNumber]))
        def mirrorPointPayload = flow.buildMirrorPointPayload(
                flow.source.switchId, mirrorTg.switchPort, randomVlan(), mirrorDirection)
        flow.createMirrorPointWithPayload(mirrorPointPayload)

        then: "Mirror point is created and Active"
        and: "Flow and switch pass validation"
        !swPair.src.synchronizeAndCollectFixedDiscrepancies().isPresent()
        flow.validateAndCollectDiscrepancies().isEmpty()

        when: "Swap flow paths"
        northbound.swapFlowPath(flow.flowId)
        Wrappers.wait(WAIT_OFFSET) {
            flow.retrieveFlowStatus().status == FlowState.UP
        }

        then: "Flow and switch both pass validation"
        !swPair.src.synchronizeAndCollectFixedDiscrepancies().isPresent()
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "Flow passes main traffic"
        def traffExam = traffExamProvider.get()
        def mirrorPortStats = new TraffgenStats(traffExam, mirrorTg, [mirrorPointPayload.sinkEndpoint.vlanId])
        if (mirrorPortStats) {
            cleanupManager.addAction(OTHER, {mirrorPortStats.close()})
        }
        def rxPacketsBefore = mirrorPortStats.get().rxPackets
        sendTrafficAndVerifyOnMainFlow(traffExam, flow, mirrorDirection)

        and: "Flow passes mirrored traffic"
        mirrorPortStats.get().rxPackets - rxPacketsBefore > 0

        where:
        mirrorDirection << [FORWARD, REVERSE]
    }

    // ovs switch doesn't support mirroring for the vxlan flows
    @Tags([TOPOLOGY_DEPENDENT, HARDWARE])
    def "Can create mirror point on a VXLAN flow #description"() {
        given: "A VXLAN flow"
        assumeTrue(swPair as boolean, "Unable to find required vxlan-enabled switches with traffgens")
        def flow = flowFactory.getBuilder(swPair)
                .withEncapsulationType(VXLAN)
                .build().create()

        when: "Create a mirror point"
        def mirrorPort = swPair.src.getTgPortIfPresentOrRandom([flow.source.portNumber])
        def mirrorTg = topology.traffGens.find { it.switchConnected.dpId == swPair.src.switchId && it.switchPort == mirrorPort }

        def mirrorEpVlan = randomVlan()
        flow.createMirrorPoint(flow.source.switchId, mirrorPort, mirrorEpVlan, mirrorDirection as FlowPathDirection)

        then: "Mirror point is created and Active"
        and: "Related switches and flow pass validation"
        def flowInvolvedSwitches = switches.all().findSwitchesInPath(flow.retrieveAllEntityPaths())
        synchronizeAndCollectFixedDiscrepancies(flowInvolvedSwitches).isEmpty()
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "Flow passes traffic on main path as well as to the mirror (if possible to check)"
        def traffExam = traffExamProvider.get()
        def mirrorPortStats = mirrorTg ? new TraffgenStats(traffExam, mirrorTg, [mirrorEpVlan]) : null
        if (mirrorPortStats) {
            cleanupManager.addAction(OTHER, {mirrorPortStats.close()})
        }
        def rxPacketsBefore = mirrorPortStats?.get()?.rxPackets
        sendTrafficAndVerifyOnMainFlow(traffExam, flow, mirrorDirection)
        //https://github.com/telstra/open-kilda/issues/5420
        if (mirrorTg && !swPair.src.isWb5164()) {
            assert mirrorPortStats.get().rxPackets - rxPacketsBefore > 0
        }

        where:
        [swPair, mirrorDirection] << [getUniqueVxlanSwitchPairs(true),
                                      [FORWARD, REVERSE]].combinations()
        //means there is no second traffgen for target switch and we are not checking the counter on receiving interface
        trafficDisclaimer = swPair.src.traffGenPorts.size() < 2 ? " !WARN: No mirrored traffic check!" : ""
        description = "[${swPair.src.hwSwString()}] $mirrorDirection $trafficDisclaimer"
    }

    def "Flow with mirror point can survive flow sync, #encap, #mirrorDirection"() {
        given: "A flow with given encapsulation type and mirror point"
        // ovs switch doesn't support mirroring for the vxlan flows
        assumeFalse("virtual" == profile && encap == VXLAN)
        assumeTrue(swPair as boolean, "Unable to find enough switches for a $encap flow")
        def flow = flowFactory.getBuilder(swPair)
                .withEncapsulationType(encap)
                .build().create()
        def freePort = (swPair.dst.getPorts() - flow.destination.portNumber)[0]
        flow.createMirrorPoint(flow.destination.switchId, freePort, randomVlan(), mirrorDirection as FlowPathDirection)

        when: "Call flow sync for the flow"
        flow.sync()
        Wrappers.wait(WAIT_OFFSET) {
            assert flow.retrieveFlowStatus().status == FlowState.UP
        }

        then: "Related switches and flow pass validation"
        def flowInvolvedSwitches = switches.all().findSwitchesInPath(flow.retrieveAllEntityPaths())
        synchronizeAndCollectFixedDiscrepancies(flowInvolvedSwitches).isEmpty()
        flow.validateAndCollectDiscrepancies().isEmpty()

        where:
        [data, mirrorDirection] << [
                [[
                         swPair: switchPairs.all().withBothSwitchesVxLanEnabled().random(),
                         encap : VXLAN
                 ],
                 [
                         swPair: switchPairs.all().random(),
                         encap : TRANSIT_VLAN
                 ]],
                //^run all direction combinations for above data
                [FORWARD, REVERSE]
        ].combinations()
        swPair = data.swPair as SwitchPair
        encap = data.encap as FlowEncapsulationType
    }

    @Tags([LOW_PRIORITY])
    def "Can create mirror point on unmetered pinned flow, #mirrorDirection"() {
        given: "An unmetered pinned flow"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getBuilder(swPair)
                .withPinned(true)
                .withBandwidth(0)
                .withIgnoreBandwidth(true)
                .build().create()

        when: "Create a mirror point on src"
        def freePort = (swPair.src.getPorts() - flow.source.portNumber)[0]
        flow.createMirrorPoint(flow.source.switchId, freePort, randomVlan(), mirrorDirection)

        then: "Mirror point is created and Active"
        and: "Flow and src switch both pass validation"
        !swPair.src.synchronizeAndCollectFixedDiscrepancies().isPresent()
        flow.validateAndCollectDiscrepancies().isEmpty()

        where:
        mirrorDirection << [FORWARD, REVERSE]
    }

    @Tags([TOPOLOGY_DEPENDENT])
    def "Can create a mirror point on the same port as flow, different vlan #description"() {
        given: "A flow"
        def flow = flowFactory.getBuilder(swPair)
                .withEncapsulationType(encapType)
                .build().create()

        when: "Add a mirror point on the same port with flow, different vlan"
        flow.createMirrorPoint(
                flow.destination.switchId, flow.destination.portNumber,
                flow.destination.vlanId - 1, mirrorDirection as FlowPathDirection
        )

        then: "Mirror point is successfully created"
        flow.retrieveMirrorPoints().points.size() == 1

        when: "Delete the flow without deleting its mirror point"
        flow.delete()

        then: "Mirror point is also deleted from db"
        database.getMirrorPoints().empty

        and: "Related switch pass validation"
        !swPair.dst.synchronizeAndCollectFixedDiscrepancies().isPresent()

        where:
        [swPair, mirrorDirection, encapType] <<
                //[swPair, reverse/forward, transit_vlan]
                ([getUniqueSwitchPairs().collect {
                    it.reversed //'reversed' since we test 'dst' here, but 'getUniqueSwitchPairs' method targets 'src'
                }, [FORWARD, REVERSE]].combinations()
                        .collect { it << TRANSIT_VLAN; it } +

                //[swPair, reverse/forward, vxlan]
                [getUniqueVxlanSwitchPairs(false).collect { it.reversed },
                 [FORWARD, REVERSE]].combinations()
                        .collect { it << VXLAN; it })

                        //https://github.com/telstra/open-kilda/issues/4374
                        .findAll { SwitchPair swPair, m, e ->
                            !swPair.dst.isWb5164()
                        }
        description = "[${swPair.dst.hwSwString()} $encapType $mirrorDirection]"

    }

    @Tags([LOW_PRIORITY])
    def "Can create multiple mirror points for the same flow and switch"() {
        given: "A flow"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getRandom(swPair)

        when: "Add a Forward mirror point on the same port with flow, different vlan"
        flow.createMirrorPoint(flow.destination.switchId, flow.destination.portNumber, flow.destination.vlanId - 1)

        then: "Mirror point is created"
        flow.retrieveMirrorPoints().points.size() == 1

        when: "Add one more Forward mirror point on the same port with flow, different vlan"
        flow.createMirrorPoint(flow.destination.switchId, flow.destination.portNumber, flow.destination.vlanId - 2)

        then: "Mirror point is created"
        flow.retrieveMirrorPoints().points.size() == 2

        and: "Mirrorring group for forward path has 3 buckets (main flow + 2 mirrors)"
        def fwGroupId = swPair.dst.rulesManager.getFlowRules(FORWARD, true).instructions.applyActions.group
        def fl = flHelper.getFlsByRegions(swPair.dst.getRegions())[0].floodlightService

        def fwMirrorGroup = fl.getGroupsStats(swPair.dst.switchId).group.find { it.groupNumber == fwGroupId }
        fwMirrorGroup.bucketCounters.size() == 3

        when: "Add a Reverse mirror point on the different port with flow"
        def mirrorPointRvPayload = flow.buildMirrorPointPayload(
                flow.destination.switchId, flow.destination.portNumber, 0, REVERSE
        )
        flow.createMirrorPointWithPayload(mirrorPointRvPayload)

        then: "Mirror point is created"
        Wrappers.wait(WAIT_OFFSET) {
            assert flow.retrieveDetails().mirrorPointStatuses.find {
                it.mirrorPointId == mirrorPointRvPayload.mirrorPointId
            }.status == FlowPathStatus.ACTIVE.toString().toLowerCase()
        }

        and: "Mirrorring group for reverse path has 2 buckets (main flow + 1 mirror)"
        def rvGroupId = swPair.dst.rulesManager.getFlowRules(REVERSE, true).instructions.applyActions.group
        def rvMirrorGroup = fl.getGroupsStats(swPair.dst.switchId).group.find { it.groupNumber == rvGroupId }
        rvMirrorGroup.bucketCounters.size() == 2
    }

    def "System also updates mirror rule after flow partial update"() {
        given: "A flow with mirror point"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getRandom(swPair)
        def freePort = (swPair.dst.getPorts() - flow.destination.portNumber)[0]
        flow.createMirrorPoint(flow.destination.switchId, freePort, flow.destination.vlanId - 1)

        when: "Update flow port and vlan on the same endpoint where mirror is"
        def newFlowPort = (swPair.dst.getPorts() - flow.destination.portNumber - freePort)[0]
        def newFlowVlan = flow.destination.vlanId - 2
        flow.partialUpdate(new FlowPatchV2().tap {
            destination = new FlowPatchEndpoint().tap {
                portNumber = newFlowPort
                vlanId = newFlowVlan
            }
        })

        then: "Flow and affected switch are valid"
        !swPair.dst.synchronizeAndCollectFixedDiscrepancies().isPresent()
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "Mirror rule has updated port/vlan values"
        def mirrorRule = swPair.dst.rulesManager.getFlowRules(FORWARD, true)
        def setVlanInstruction = mirrorRule.instructions.applyActions.setFieldActions[0]
        setVlanInstruction.fieldName == "vlan_vid"
        setVlanInstruction.fieldValue == "$newFlowVlan"
        def fl = flHelper.getFlsByRegions(swPair.dst.getRegions())[0].floodlightService
        def group = fl.getGroupsDesc(swPair.dst.switchId).groupDesc.find {
            it.groupNumber == mirrorRule.instructions.applyActions.group
        }
        group.buckets.find { it.actions == "output=$newFlowPort" }
    }

    @Tags([LOW_PRIORITY])
    def "Mirror point can be created for a default flow (0 vlan), #mirrorDirection"() {
        given: "A default flow"
        def swPair = switchPairs.all()
                .withTraffgensOnBothEnds()
                .withAtLeastNTraffgensOnSource(2).random()
        def flow = flowFactory.getBuilder(swPair)
                .withSourceVlan(0)
                .build().create()

        when: "Create a mirror point"
        def tgPort = swPair.src.traffGenPorts.find { it != flow.source.portNumber}
        def mirrorTg = topology.getTraffGen(swPair.src.switchId, tgPort)
        def mirrorVlanId = randomVlan()
        flow.createMirrorPoint(flow.source.switchId, mirrorTg.switchPort, mirrorVlanId, mirrorDirection)

        then: "Mirror point is created and Active"
        and: "Related switches and flow pass validation"
        def flowInvolvedSwitches = switches.all().findSwitchesInPath(flow.retrieveAllEntityPaths())
        synchronizeAndCollectFixedDiscrepancies(flowInvolvedSwitches).isEmpty()
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "Flow passes traffic on main path as well as to the mirror"
        def traffExam = traffExamProvider.get()
        def mirrorPortStats = new TraffgenStats(traffExam, mirrorTg, [mirrorVlanId])
        if (mirrorPortStats) {
            cleanupManager.addAction(OTHER, {mirrorPortStats.close()})
        }
        def rxPacketsBefore = mirrorPortStats.get().rxPackets
        sendTrafficAndVerifyOnMainFlow(traffExam, flow, mirrorDirection)
        mirrorPortStats.get().rxPackets - rxPacketsBefore > 0

        where:
        mirrorDirection << [FORWARD, REVERSE]
    }

    @Tags([LOW_PRIORITY])
    def "Flow mirror point works properly with a qinq flow, #mirrorDirection"() {
        given: "A qinq flow"
        def swPair = switchPairs.all()
                .withTraffgensOnBothEnds()
                .withAtLeastNTraffgensOnSource(2)
                .random()
        def flow = flowFactory.getBuilder(swPair)
                .withSourceInnerVlan(100)
                .withDestinationInnerVlan(200)
                .build().create()

        when: "Create a mirror point"
        def tgPort = swPair.src.traffGenPorts.find { it != flow.source.portNumber}
        def mirrorTg = topology.getTraffGen(swPair.src.switchId, tgPort)
        def mirrorVlanId = randomVlan([flow.source.vlanId, flow.source.innerVlanId])
        flow.createMirrorPoint(flow.source.switchId, mirrorTg.switchPort, mirrorVlanId, mirrorDirection)

        then: "Mirror point is created, flow and switches are valid"
        def flowInvolvedSwitches = switches.all().findSwitchesInPath(flow.retrieveAllEntityPaths())
        synchronizeAndCollectFixedDiscrepancies(flowInvolvedSwitches).isEmpty()
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "Traffic examination reports packets on mirror point"
        def traffExam = traffExamProvider.get()
        def mirrorPortStats = new TraffgenStats(traffExam, mirrorTg, [mirrorVlanId])
        if (mirrorPortStats) {
            cleanupManager.addAction(OTHER, {mirrorPortStats.close()})
        }
        def rxPacketsBefore = mirrorPortStats.get().rxPackets
        sendTrafficAndVerifyOnMainFlow(traffExam, flow, mirrorDirection)
        mirrorPortStats.get().rxPackets - rxPacketsBefore > 0

        where:
        mirrorDirection << [REVERSE, FORWARD]
    }

    @Tags([LOW_PRIORITY])
    def "Unable to create a mirror endpoint with #data.testDescr on the transit switch"() {
        given: "A flow with transit switch"
        def swPair = switchPairs.all().nonNeighbouring().random()
        def availablePaths = swPair.retrieveAvailablePaths().collect { isls.all().findInPath(it)}
        //2 isls == 3 switches in a path
        def pathIsls = availablePaths.find { it.size() == 2 }
        // Sometimes a pair has >3 involvedSwitches and the required path cannot be found
        assumeTrue(pathIsls != null, "Could not find a path with 1 transit switch.")
        List<SwitchId> involvedSwitches = pathIsls.collectMany { it.involvedSwIds }

        availablePaths.findAll { it != pathIsls }.each { isls.all().makePathIslsMorePreferable(pathIsls, it) }
        def flow = flowFactory.getBuilder(swPair).build().create()
        assert isls.all().findInPath(flow.retrieveAllEntityPaths()) == pathIsls

        when: "Try to add a mirror endpoint on the transit switch"
        def freePort = (swPair.dst.getPorts() - flow.destination.portNumber)[0]
        SwitchId mirrorEpSinkSwitch = data.sinkEndpointSwitch(involvedSwitches)
        SwitchId mirrorPointSwitch = data.mirrorPointSwitch(involvedSwitches)
        flow.createMirrorPoint(mirrorEpSinkSwitch, freePort, randomVlan(), FORWARD, mirrorPointSwitch, false)

        then: "Error is returned, cannot create mirror point on given sw"
        def error = thrown(HttpClientErrorException)
        new FlowMirrorPointNotCreatedExpectedError(~/${data.errorDesc(involvedSwitches)}/).matches(error)

        where:
        data << [
                [
                        testDescr         : "mirrorEndpoint and sinkEndpoint",
                        mirrorPointSwitch : { List<SwitchId> involved -> involved[1] },
                        sinkEndpointSwitch: { List<SwitchId> involved -> involved[1] },
                        errorDesc         : { List<SwitchId> involved -> "Invalid mirror point switch id: ${involved[1]}" }
                ],
                [
                        testDescr         : "sinkEndpoint",
                        mirrorPointSwitch : { List<SwitchId> involved -> involved[0] },
                        sinkEndpointSwitch: { List<SwitchId> involved -> involved[1] },
                        errorDesc         : { List<SwitchId> involved ->
                            "Invalid sink endpoint switch id: ${involved[1]}. In the current " +
                                    "implementation, the sink switch id cannot differ from the mirror point switch id."
                        }
                ],
                [
                        testDescr         : "mirrorEndpoint",
                        mirrorPointSwitch : { List<SwitchId> involved -> involved[1] },
                        sinkEndpointSwitch: { List<SwitchId> involved -> involved[0] },
                        errorDesc         : { List<SwitchId> involved ->
                            "Invalid sink endpoint switch id: ${involved[0]}. In the current " +
                                    "implementation, the sink switch id cannot differ from the mirror point switch id."
                        }
                ]
        ]
    }

    @Tags([LOW_PRIORITY])
    def "Test possible error scenarios during mirror point creation: [#testData.testName, #testData.mirrorPointDirection]"(MirrorErrorTestData testData) {
        given: "A flow"
        def flow = testData.flow.create()

        when: "Try adding a mirror point with conflict"
        flow.createMirrorPoint(
                testData.mirrorSinkEndpointSwitchId, testData.port, randomVlan(),
                testData.mirrorPointDirection, testData.mirrorPointSwitchId, false
        )

        then: "Error is returned, cannot create mirror point with given params"
        def error = thrown(HttpClientErrorException)
        testData.expectedError.matches(error)

        where:
        testData << [
                new MirrorErrorTestData("Unable to create a mirror endpoint on the src sw and sink back to dst sw", {
                    def swPair = switchPairs.all().random()
                    it.flow = flowFactory.getBuilder(swPair).build()
                    it.port = (swPair.src.getPorts() - flow.source.portNumber)[0]
                    it.mirrorSinkEndpointSwitchId = flow.source.switchId
                    it.mirrorPointDirection = FORWARD
                    it.mirrorPointSwitchId = swPair.dst.switchId
                    it.expectedError = new FlowMirrorPointNotCreatedExpectedError(
                            ~/Invalid sink endpoint switch id: $swPair.src.switchId. In the current implementation, \
the sink switch id cannot differ from the mirror point switch id./)
                }),
                new MirrorErrorTestData("Unable to create a mirror point with isl conflict", {
                    def swPair = switchPairs.all().random()
                    it.flow = flowFactory.getBuilder(swPair).build()
                    it.port = topology.getBusyPortsForSwitch(swPair.dst.sw)[0]
                    it.mirrorSinkEndpointSwitchId = flow.destination.switchId
                    it.mirrorPointDirection = FORWARD
                    it.mirrorPointSwitchId = swPair.dst.switchId
                    it.expectedError = new FlowMirrorPointNotCreatedExpectedError(~/The port $it.port on the switch \
\'$swPair.dst.switchId\' is occupied by an ISL \(destination endpoint collision\)./)
                }),
                new MirrorErrorTestData("Unable to create a mirror point with s42Port conflict", {
                    def swPair = switchPairs.all().withDestinationSwitchConnectedToServer42().random()
                    it.flow = flowFactory.getBuilder(swPair).build()
                    def s42Port = swPair.dst.sw.prop.server42Port
                    it.port = s42Port
                    it.mirrorSinkEndpointSwitchId = flow.destination.switchId
                    it.mirrorPointDirection = FORWARD
                    it.mirrorPointSwitchId = swPair.dst.switchId
                    it.expectedError = new FlowMirrorPointNotCreatedExpectedError(~/Server 42 port in the switch \
properties for switch \'$swPair.dst.switchId\' is set to \'$s42Port\'. It is not possible to create or update an endpoint \
with these parameters./)
                })
        ].collectMany {
            [it.tap { mirrorPointDirection = FORWARD },
             it.clone().tap { mirrorPointDirection = REVERSE }]
        }
    }

    @Tags([LOW_PRIORITY])
    def "Unable to create a mirror point with existing flow conflict, #mirrorDirection"() {
        given: "A flow"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getRandom(swPair)
        def otherFlow = flowFactory.getBuilder(swPair, false, flow.occupiedEndpoints()).build().create()

        when: "Try adding a mirror point that conflicts with other existing flow"
        def mirrorPointPayload = flow.buildMirrorPointPayload(
                otherFlow.source.switchId, otherFlow.source.portNumber, otherFlow.source.vlanId, mirrorDirection)
        flow.createMirrorPointWithPayload(mirrorPointPayload, false)

        then: "Error is returned, cannot create mirror point with given params"
        def error = thrown(HttpClientErrorException)
        new FlowMirrorPointNotCreatedWithConflictExpectedError(
                getEndpointConflictError(mirrorPointPayload, otherFlow, "source")).matches(error)

        where:
        mirrorDirection << [FORWARD, REVERSE]
    }

    @Tags([LOW_PRIORITY])
    def "Unable to create a flow that conflicts with mirror point, #mirrorDirection"() {
        given: "A flow with mirror point"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getRandom(swPair)
        def freePort = (swPair.dst.getPorts() - flow.destination.portNumber)[0]
        def mirrorPointPayload = flow.buildMirrorPointPayload(
                flow.destination.switchId, freePort, randomVlan(), mirrorDirection, swPair.dst.switchId)
        flow.createMirrorPointWithPayload(mirrorPointPayload)

        Wrappers.wait(WAIT_OFFSET) {
            assert flow.retrieveDetails().mirrorPointStatuses[0].status ==
                    FlowPathStatus.ACTIVE.toString().toLowerCase()
        }

        when: "Try adding a flow that conflicts with existing mirror point"
        def busyEndpoints = []
        busyEndpoints.addAll(flow.occupiedEndpoints())
        def otherFlow = flowFactory.getBuilder(swPair, false, busyEndpoints)
                .withDestinationPort(mirrorPointPayload.sinkEndpoint.portNumber)
                .withDestinationVlan(mirrorPointPayload.sinkEndpoint.vlanId)
                .build()
        otherFlow.create()

        then: "Error is returned, cannot create flow that conflicts with mirror point"
        def error = thrown(HttpClientErrorException)
        new FlowNotCreatedWithConflictExpectedError(
                getEndpointConflictError(otherFlow.destination, mirrorPointPayload)).matches(error)

        where:
        mirrorDirection << [FORWARD, REVERSE]
    }

    @Tags([LOW_PRIORITY])
    def "Unable to create mirror point with connected devices enabled, #mirrorDirection"() {
        given: "A flow with connected devices enabled"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getBuilder(swPair)
                .withDetectedDevicesOnSrc(true, true)
                .build().create()

        when: "Try to create a mirror for the flow (on the same endpoint)"
        def freePort = (swPair.src.getPorts() - flow.source.portNumber)[0]
        flow.createMirrorPoint(
                flow.source.switchId, freePort, randomVlan(),
                mirrorDirection, flow.source.switchId, false)

        then: "Error is returned, cannot create create mirror for a flow with devices"
        def error = thrown(HttpClientErrorException)
        new FlowMirrorPointNotCreatedExpectedError(~/Connected devices feature is active on the flow $flow.flowId \
for endpoint switchId=\"$flow.source.switchId\" port=$flow.source.portNumber vlanId=$flow.source.vlanId, \
flow mirror point cannot be created this flow/).matches(error)

        where:
        mirrorDirection << [FORWARD, REVERSE]
    }

    @Tags([LOW_PRIORITY])
    def "Unable to update flow and enable connected devices if mirror is present, #mirrorDirection"() {
        given: "A flow with a mirror point"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getRandom(swPair)
        def freePort = (swPair.src.getPorts() - flow.source.portNumber)[0]
        flow.createMirrorPoint(
                flow.source.switchId, freePort, randomVlan(),
                mirrorDirection, flow.source.switchId, false)

        when: "Try to partial update the flow and enable connected devices"
        def flowPatch = new FlowPatchV2().tap {
            source = new FlowPatchEndpoint().tap {
                detectConnectedDevices = new DetectConnectedDevicesV2(true, true)
            }
        }
        flow.partialUpdate(flowPatch)

        then: "Error is returned, cannot enable devices on a flow with mirror"
        def error = thrown(HttpClientErrorException)
        new FlowNotUpdatedExpectedError(
                ~/Flow mirror point is created for the flow $flow.flowId, LLDP or ARP can not be set to true./).matches(error)

        where:
        mirrorDirection << [FORWARD, REVERSE]
    }

    @Tags([LOW_PRIORITY])
    def "Cannot enable connected devices on switch if mirror is present"() {
        given: "A flow with a mirror endpoint"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getRandom(swPair)
        def freePort = (swPair.src.getPorts() - flow.source.portNumber)[0]
        flow.createMirrorPoint(flow.source.switchId, freePort)

        when: "Try to enable connected devices for switch where mirror is created"
        def originalProps = swPair.src.getCachedProps()
        swPair.src.updateProperties(originalProps.jacksonCopy().tap {
            it.switchArp = true
            it.switchLldp = true
        })

        then: "Error is returned, cannot enable devices on a switch with mirror"
        def error = thrown(HttpClientErrorException)
        new SwitchPropertiesNotUpdatedExpectedError("Flow mirror point is created on the switch $swPair.src.switchId, " +
                "switchLldp or switchArp can not be set to true.").matches(error)
    }

    @Tags([LOW_PRIORITY])
    def "Cannot create mirror on a switch with enabled connected devices"() {
        given: "A switch with enabled connected devices"
        def swPair = switchPairs.all().random()
        def originalProps = swPair.src.getCachedProps()
        swPair.src.updateProperties(originalProps.jacksonCopy().tap {
            it.switchArp = true
            it.switchLldp = true
        })

        when: "Try to create a mirror on the switch with devices"
        def flow = flowFactory.getRandom(swPair)
        def freePort = (swPair.src.getPorts() - flow.source.portNumber)[0]
        flow.createMirrorPoint(flow.source.switchId, freePort, randomVlan(),
                REVERSE, flow.source.switchId, false)

        then: "Error is returned, cannot create flow that conflicts with mirror point"
        def error = thrown(HttpClientErrorException)
        new FlowMirrorPointNotCreatedExpectedError(~/Connected devices feature is active on the switch $swPair.src.switchId\
, flow mirror point cannot be created on this switch./).matches(error)
    }

    @Tags([LOW_PRIORITY])
    def "Unable to create a mirror point with existing mirror point conflict, #mirrorDirection"() {
        given: "A flow with mirror point"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getRandom(swPair)
        def freePort = (swPair.dst.getPorts() - flow.destination.portNumber)[0]
        def mirrorPoint = flow.deepCopy().destination.tap {
            it.vlanId = randomVlan()
            it.portNumber = freePort
        }
        def mirrorPointPayload = flow.buildMirrorPointPayload(
                mirrorPoint.switchId, mirrorPoint.portNumber, mirrorPoint.vlanId, mirrorDirection)
        flow.createMirrorPointWithPayload(mirrorPointPayload)
        Wrappers.wait(WAIT_OFFSET) {
            assert flow.retrieveDetails().mirrorPointStatuses[0].status ==
                    FlowPathStatus.ACTIVE.toString().toLowerCase()
        }

        when: "Try adding one more mirror point that conflicts with existing mirror point"
        def mirrorPoint2 = mirrorPoint.jacksonCopy()
        def mirrorPoint2Payload = mirrorPointPayload.jacksonCopy().tap {
            it.mirrorPointId = FLOW.generateId()
        }
        flow.createMirrorPointWithPayload(mirrorPoint2Payload, false)

        then: "Error is returned, cannot create flow that conflicts with mirror point"
        def error = thrown(HttpClientErrorException)
        new FlowMirrorPointNotCreatedWithConflictExpectedError(
                getEndpointConflictError(mirrorPoint2, mirrorPointPayload)).matches(error)

        where:
        mirrorDirection << [FORWARD, REVERSE]
    }


    static Pattern getEndpointConflictError(FlowMirrorPointPayload mirrorEp, FlowExtended existingFlow, String srcOrDst) {
        FlowEndpointV2 flowEndpoint = existingFlow."$srcOrDst"
        ~/Requested flow \'$mirrorEp.mirrorPointId\' conflicts with existing flow \'$existingFlow.flowId\'. Details: \
requested flow \'$mirrorEp.mirrorPointId\' destination: switchId=\"${mirrorEp.sinkEndpoint.switchId}\"\
 port=${mirrorEp.sinkEndpoint.portNumber} vlanId=${mirrorEp.sinkEndpoint.vlanId}, existing flow \'$existingFlow.flowId\'\
 $srcOrDst: switchId=\"${flowEndpoint.switchId}\" port=${flowEndpoint.portNumber} vlanId=${flowEndpoint.vlanId}/
    }

    static Pattern getEndpointConflictError(FlowEndpointV2 flowEp, FlowMirrorPointPayload existingMirror) {
        ~/Requested endpoint \'switchId=\"$flowEp.switchId\" port=$flowEp.portNumber vlanId=$flowEp.vlanId\' conflicts \
with existing flow mirror point \'$existingMirror.mirrorPointId\'./
    }

    static Exam getExam(FlowBidirectionalExam biExam, FlowPathDirection direction) {
        direction == FORWARD ? biExam.forward : biExam.reverse
    }

    private static void sendTrafficAndVerifyOnMainFlow(TraffExamService traffExam, FlowExtended flow, FlowPathDirection mirrorDirection) {
        def biExam = flow.traffExam(traffExam, 300, 1)
        getExam(biExam, mirrorDirection).with {
            udp = true
            bufferLength = 500 //due to MTU size issue on TG after mirror encapsulation
            resources = traffExam.startExam(it)
            assert traffExam.waitExam(it).hasTraffic()
        }
    }

    /**
     * Identify all unique tg switches that need to be tested. Return minimum amount of switch pairs, where switch pair
     * satisfies 'additional conditions' and src switch is a switch under test. Sources with 2+ traffgens have a higher
     * priority
     */
    @Memoized
    List<SwitchPair> getUniqueSwitchPairs(Closure additionalConditions = { true }) {
        def allTgSwitches = switches.all().withTraffGens().getListOfSwitches()
                //switches that have 2+ traffgens go to the beginning of the list
                .sort { a, b -> b.traffGenPorts.size() <=> a.traffGenPorts.size() }
        def unpickedUniqueTgSwitches = allTgSwitches.unique(false) { it.hwSwString() }
        def tgPairs = switchPairs.all().getSwitchPairs().findAll {
            additionalConditions(it)
        }
        assumeTrue(tgPairs.size() > 0, "Unable to find any switchPairs with requested conditions")
        def result = []
        while (!unpickedUniqueTgSwitches.empty) {
            def pairs = tgPairs.findAll {
                it.src in unpickedUniqueTgSwitches
            }
            if (pairs) {
                //pick a highest score pair, update list of unpicked switches, re-run
                def pair = pairs.last()
                result << pair
                unpickedUniqueTgSwitches = unpickedUniqueTgSwitches - pair.src
            } else {
                //if there is an untested src left, but there is no dst that matches 'additional conditions'
                log.warn("Switches left untested: ${unpickedUniqueTgSwitches*.hwSwString()}")
                break
            }
        }
        return result
    }

    List<SwitchPair> getUniqueVxlanSwitchPairs(boolean needTraffgens) {
        def vxlanSupportedSwIds = switches.all().withVxlanEnabled().getListOfSwitches().switchId
        getUniqueSwitchPairs({ SwitchPair swP ->
            def vxlanCheck = swP.paths.find {
                it*.switchId.every { it in vxlanSupportedSwIds }
            }
            def tgCheck = needTraffgens ? !swP.src.traffGenPorts.isEmpty() && !swP.dst.traffGenPorts.isEmpty() : true
            vxlanCheck && tgCheck
        })
    }

    @AutoClone
    private static class MirrorErrorTestData {
        String testName
        FlowExtended flow
        Integer port
        SwitchId mirrorPointSwitchId
        SwitchId mirrorSinkEndpointSwitchId
        FlowPathDirection mirrorPointDirection
        AbstractExpectedError expectedError

        MirrorErrorTestData(String testName, Closure init) {
            this.testName = testName
            this.tap(init)
            this.properties.each {
                if (it.value == null) {
                    throw new IllegalArgumentException("$it.key cannot be null")
                }
            }
        }
    }
}
