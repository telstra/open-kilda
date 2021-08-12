package org.openkilda.functionaltests.spec.flows

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.FlowHistoryConstants
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.info.rule.FlowEntry
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.FlowPathDirection
import org.openkilda.model.FlowPathStatus
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.FlowSegmentCookie
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto
import org.openkilda.northbound.dto.v2.flows.DetectConnectedDevicesV2
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointPayload
import org.openkilda.northbound.dto.v2.flows.FlowPatchEndpoint
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.Exam
import org.openkilda.testing.service.traffexam.model.FlowBidirectionalExam
import org.openkilda.testing.tools.FlowTrafficExamBuilder
import org.openkilda.testing.tools.TraffgenStats

import groovy.transform.Memoized
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.See
import spock.lang.Shared
import spock.lang.Unroll

import javax.inject.Provider

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/flow-traffic-mirroring")
class MirrorEndpointsSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    Provider<TraffExamService> traffExamProvider

    @Tidy
    @Tags([SMOKE, SMOKE_SWITCHES, TOPOLOGY_DEPENDENT])
    def "Able to CRUD a mirror endpoint on the src switch, mirror to the same switch diff port [#swPair.src.hwSwString, #mirrorDirection]#trafficDisclaimer"() {
        given: "A flow"
        assumeTrue(swPair as boolean, "Unable to find a switch pair")
        def flow = flowHelperV2.randomFlow(swPair).tap { maximumBandwidth = 100000 }
        flowHelperV2.addFlow(flow)

        when: "Create a mirror point on src switch, pointing to a different port, random vlan"
        def mirrorTg = swPair.src.traffGens ?[1]
        def mirrorPort = mirrorTg?.switchPort ?: (topology.getAllowedPortsForSwitch(swPair.src) - flow.source.portNumber)[0]
        def mirrorEndpoint = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(mirrorDirection.toString().toLowerCase())
                .mirrorPointSwitchId(swPair.src.dpId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(swPair.src.dpId).portNumber(mirrorPort)
                        .vlanId(flowHelperV2.randomVlan())
                        .build())
                .build()
        northboundV2.createMirrorPoint(flow.flowId, mirrorEndpoint)

        then: "Mirror status changes to Active"
        Wrappers.wait(WAIT_OFFSET) {
            assert northboundV2.getFlow(flow.flowId).mirrorPointStatuses[0].status ==
                    FlowPathStatus.ACTIVE.toString().toLowerCase()
        }

        and: "Flow history reports a successful mirror creation"
        with(northbound.getFlowHistory(flow.flowId).last()) {
            action == FlowHistoryConstants.CREATE_MIRROR_ACTION
            it.payload.last().action == FlowHistoryConstants.CREATE_MIRROR_SUCCESS
        }

        and: "Mirror endpoint is visible in 'get flows', 'get single flow' and 'get mirror endpoint' APIs"
        def allFlows = northboundV2.getAllFlows()
        def gotFlow = northboundV2.getFlow(flow.flowId)
        allFlows.size() == 1
        [allFlows[0], gotFlow].each {
            assert it.mirrorPointStatuses.size() == 1
            assert it.mirrorPointStatuses[0].status == FlowPathStatus.ACTIVE.toString().toLowerCase()
            assert it.mirrorPointStatuses[0].mirrorPointId == mirrorEndpoint.mirrorPointId
        }
        with(northboundV2.getMirrorPoints(flow.flowId)) {
            flowId == flow.flowId
            points.size() == 1
            expect points[0], sameBeanAs(mirrorEndpoint)
        }

        and: "Mirror flow rule has an OF group action and higher prio than flow rule"
        def rules = getFlowRules(swPair.src.dpId)
        def mirrorRule = findMirrorRule(rules, mirrorDirection)
        def flowRule = findFlowRule(rules, mirrorDirection)
        def groupId = mirrorRule.instructions.applyActions.group
        mirrorRule.priority > flowRule.priority
        !groupId.empty

        and: "Flow rule of opposite direction does not have an OF group action and there is no mirror flow rule"
        def oppositeDirection = mirrorDirection == FlowPathDirection.FORWARD ?
                FlowPathDirection.REVERSE : FlowPathDirection.FORWARD
        findFlowRule(rules, oppositeDirection).instructions.applyActions.group == null
        findMirrorRule(rules, oppositeDirection) == null

        and: "Related switches and flow pass validation"
        pathHelper.getInvolvedSwitches(flow.flowId).each {
            northbound.validateSwitch(it.dpId).verifyRuleSectionsAreEmpty(it.dpId,
                    ["missing", "misconfigured", "excess"])
        }
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        when: "Traffic briefly runs through the flow"
        def traffExam = traffExamProvider.get()
        def mirrorPortStats = mirrorTg ? new TraffgenStats(traffExam, mirrorTg, [mirrorEndpoint.sinkEndpoint.vlanId]) : null
        def rxPacketsBefore = mirrorPortStats?.get()?.rxPackets
        verifyTraffic(traffExam, flow, mirrorDirection)

        then: "OF group reports same amount of packets sent both to mirror and to main paths"
        def fl = flHelper.getFlsByRegions(swPair.src.getRegions())[0].floodlightService
        Wrappers.wait(2) { //leftover packets after traffexam may not be counted still, require a retry sometimes
            mirrorRule = findMirrorRule(getFlowRules(swPair.src.dpId), mirrorDirection)
            assert mirrorRule.packetCount > 0
            def mirrorGroup = fl.getGroupsStats(swPair.src.dpId).group.find { it.groupNumber == groupId }
            mirrorGroup.bucketCounters.each { assert it.packetCount.toLong() == mirrorRule.packetCount }
        }

        and: "Original flow rule counter is not increased"
        flowRule.packetCount == findFlowRule(getFlowRules(swPair.src.dpId), mirrorDirection).packetCount

        and: "Traffic is also received at the mirror point (check only if second tg available)"
        if (mirrorTg) {
            assert mirrorPortStats.get().rxPackets - rxPacketsBefore > 0
        }

        when: "Delete the mirror point"
        northboundV2.deleteMirrorPoint(flow.flowId, mirrorEndpoint.mirrorPointId)

        then: "'Mirror point delete' operation is present in flow history"
        Wrappers.wait(WAIT_OFFSET) {
            with(northbound.getFlowHistory(flow.flowId).last()) {
                action == FlowHistoryConstants.DELETE_MIRROR_ACTION
                payload.last().action == FlowHistoryConstants.DELETE_MIRROR_SUCCESS
            }
        }

        and: "Mirror point is no longer present in flow and mirror APIs"
        assert northboundV2.getFlow(flow.flowId).mirrorPointStatuses.empty
        northboundV2.getAllFlows()[0].mirrorPointStatuses.empty
        northboundV2.getMirrorPoints(flow.flowId).points.empty

        and: "Mirror flow rule is removed and flow rule is intact"
        def rulesAfterRemove = getFlowRules(swPair.src.dpId)
        !findMirrorRule(rulesAfterRemove, mirrorDirection)
        findFlowRule(rulesAfterRemove, mirrorDirection)

        and: "OF group is removed"
        !fl.getGroupsStats(swPair.src.dpId).group.find { it.groupNumber == groupId }

        and: "Src switch and flow pass validation"
        northbound.validateSwitch(swPair.src.dpId).verifyRuleSectionsAreEmpty(swPair.src.dpId,
                ["missing", "misconfigured", "excess"])
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        when: "Delete the flow"
        flowHelperV2.deleteFlow(flow.flowId)

        then: "Src switch pass validation"
        northbound.validateSwitch(swPair.src.dpId).verifyRuleSectionsAreEmpty(swPair.src.dpId)
        def testDone = true

        cleanup:
        !testDone && flow && flowHelperV2.deleteFlow(flow.flowId)
        mirrorPortStats && mirrorPortStats.close()

        where:
        [swPair, mirrorDirection] << [getUniqueSwitchPairs({ it.src.traffGens && it.dst.traffGens }),
                                      [FlowPathDirection.FORWARD, FlowPathDirection.REVERSE]].combinations()
        //means there is no second traffgen for target switch and we are not checking the counter on receiving interface
        trafficDisclaimer = swPair.src.traffGens.size() < 2 ? " !WARN: No mirrored traffic check!" : ""
    }

    @Tidy
    @Tags([LOW_PRIORITY])
    def "Can create mirror point on protected flow and survive path swap, #mirrorDirection"() {
        given: "A flow with protected path"
        SwitchPair swPair = topologyHelper.getSwitchPairs(true).find { SwitchPair pair ->
            pair.paths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size() >= 2 &&
                    [pair.src, pair.dst].every { it.traffGens } && pair.src.traffGens.size() > 1
        } ?: assumeTrue(false, "Unable to find a switch pair with 2+ diverse paths and 3+ traffgens")
        def flow = flowHelperV2.randomFlow(swPair).tap { it.allocateProtectedPath = true }
        flowHelperV2.addFlow(flow)

        when: "Create a mirror point"
        def mirrorTg = swPair.src.traffGens[1]
        def mirrorEndpoint = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(mirrorDirection.toString().toLowerCase())
                .mirrorPointSwitchId(swPair.src.dpId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(swPair.src.dpId).portNumber(mirrorTg.switchPort)
                        .vlanId(flowHelperV2.randomVlan())
                        .build())
                .build()
        flowHelperV2.createMirrorPoint(flow.flowId, mirrorEndpoint)

        then: "Mirror point is created and Active"
        and: "Flow and switch pass validation"
        northbound.validateSwitch(swPair.src.dpId).verifyRuleSectionsAreEmpty(swPair.src.dpId,
                ["missing", "misconfigured", "excess"])
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        when: "Swap flow paths"
        northbound.swapFlowPath(flow.flowId)
        Wrappers.wait(WAIT_OFFSET) {
            northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP
        }

        then: "Flow and switch both pass validation"
        northbound.validateSwitch(swPair.src.dpId).verifyRuleSectionsAreEmpty(swPair.src.dpId,
                ["missing", "misconfigured", "excess"])
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        and: "Flow passes main traffic"
        def traffExam = traffExamProvider.get()
        def mirrorPortStats = new TraffgenStats(traffExam, mirrorTg, [mirrorEndpoint.sinkEndpoint.vlanId])
        def rxPacketsBefore = mirrorPortStats.get().rxPackets
        verifyTraffic(traffExam, flow, mirrorDirection)

        and: "Flow passes mirrored traffic"
        mirrorPortStats.get().rxPackets - rxPacketsBefore > 0

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
        mirrorPortStats && mirrorPortStats.close()

        where:
        mirrorDirection << [FlowPathDirection.FORWARD, FlowPathDirection.REVERSE]
    }

    @Tidy
    @Tags([TOPOLOGY_DEPENDENT])
    def "Can create mirror point on a VXLAN flow [#swPair.src.hwSwString, #mirrorDirection]#trafficDisclaimer"() {
        given: "A VXLAN flow"
        assumeTrue(swPair as boolean, "Unable to find required vxlan-enabled switches with traffgens")
        def flow = flowHelperV2.randomFlow(swPair).tap { encapsulationType = FlowEncapsulationType.VXLAN }
        flowHelperV2.addFlow(flow)

        when: "Create a mirror point"
        def mirrorTg = swPair.src.traffGens[1]
        def mirrorPort = mirrorTg?.switchPort ?: (topology.getAllowedPortsForSwitch(swPair.src) - flow.source.portNumber)[0]
        def mirrorEndpoint = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(mirrorDirection.toString().toLowerCase())
                .mirrorPointSwitchId(swPair.src.dpId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(swPair.src.dpId).portNumber(mirrorPort)
                        .vlanId(flowHelperV2.randomVlan())
                        .build())
                .build()
        flowHelperV2.createMirrorPoint(flow.flowId, mirrorEndpoint)

        then: "Mirror point is created and Active"
        and: "Related switches and flow pass validation"
        pathHelper.getInvolvedSwitches(flow.flowId).each {
            northbound.validateSwitch(it.dpId).verifyRuleSectionsAreEmpty(it.dpId,
                    ["missing", "misconfigured", "excess"])
        }
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        and: "Flow passes traffic on main path as well as to the mirror (if possible to check)"
        def traffExam = traffExamProvider.get()
        def mirrorPortStats = mirrorTg ? new TraffgenStats(traffExam, mirrorTg, [mirrorEndpoint.sinkEndpoint.vlanId]) : null
        def rxPacketsBefore = mirrorPortStats?.get()?.rxPackets
        verifyTraffic(traffExam, flow, mirrorDirection)
        if (mirrorTg) {
            assert mirrorPortStats.get().rxPackets - rxPacketsBefore > 0
        }

        cleanup:
        mirrorPortStats && mirrorPortStats.close()
        flow && flowHelperV2.deleteFlow(flow.flowId)

        where:
        [swPair, mirrorDirection] << [getUniqueVxlanSwitchPairs(true),
                                      [FlowPathDirection.FORWARD, FlowPathDirection.REVERSE]].combinations()
        //means there is no second traffgen for target switch and we are not checking the counter on receiving interface
        trafficDisclaimer = swPair.src.traffGens.size() < 2 ? " !WARN: No mirrored traffic check!" : ""
    }

    @Tidy
    def "Flow with mirror point can survive flow sync, #data.encap, #mirrorDirection"() {
        given: "A flow with given encapsulation type and mirror point"
        assumeTrue(swPair as boolean, "Unable to find enough switches for a $data.encap flow")
        def flow = flowHelperV2.randomFlow(swPair).tap { encapsulationType = data.encap }
        flowHelperV2.addFlow(flow)
        def freePort = (topology.getAllowedPortsForSwitch(swPair.dst) - flow.destination.portNumber)[0]
        def mirrorEndpoint = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(mirrorDirection.toString().toLowerCase())
                .mirrorPointSwitchId(swPair.dst.dpId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(swPair.dst.dpId).portNumber(freePort)
                        .vlanId(flowHelperV2.randomVlan())
                        .build())
                .build()
        flowHelperV2.createMirrorPoint(flow.flowId, mirrorEndpoint)

        when: "Call flow sync for the flow"
        northbound.synchronizeFlow(flow.flowId)
        Wrappers.wait(WAIT_OFFSET) {
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP
        }

        then: "Related switches and flow pass validation"
        pathHelper.getInvolvedSwitches(flow.flowId).each {
            northbound.validateSwitch(it.dpId).verifyRuleSectionsAreEmpty(it.dpId,
                    ["missing", "misconfigured", "excess"])
        }
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)

        where:
        [data, mirrorDirection] << [
                [[
                         swPair: topologyHelper.getSwitchPairs(true).find { swP ->
                             swP.paths.find { pathHelper.getInvolvedSwitches(it).every { switchHelper.isVxlanEnabled(it.dpId) } }
                         },
                         encap : FlowEncapsulationType.VXLAN
                 ],
                 [
                         swPair: topologyHelper.switchPairs[0],
                         encap : FlowEncapsulationType.TRANSIT_VLAN
                 ]],
                //^run all direction combinations for above data
                [FlowPathDirection.FORWARD, FlowPathDirection.REVERSE]
        ].combinations()
        swPair = data.swPair as SwitchPair
    }

    @Tidy
    @Tags([LOW_PRIORITY])
    def "Can create mirror point on unmetered pinned flow, #mirrorDirection"() {
        given: "An unmetered pinned flow"
        def swPair = topologyHelper.switchPairs[0]
        def flow = flowHelperV2.randomFlow(swPair).tap {
            pinned = true
            maximumBandwidth = 0
            ignoreBandwidth = true
        }
        flowHelperV2.addFlow(flow)

        when: "Create a mirror point on src"
        def freePort = (topology.getAllowedPortsForSwitch(swPair.src) - flow.source.portNumber)[0]
        def mirrorEndpoint = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(mirrorDirection.toString().toLowerCase())
                .mirrorPointSwitchId(swPair.src.dpId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(swPair.src.dpId).portNumber(freePort)
                        .vlanId(flowHelperV2.randomVlan())
                        .build())
                .build()
        flowHelperV2.createMirrorPoint(flow.flowId, mirrorEndpoint)

        then: "Mirror point is created and Active"
        and: "Flow and src switch both pass validation"
        northbound.validateSwitch(swPair.src.dpId).verifyRuleSectionsAreEmpty(swPair.src.dpId,
                ["missing", "misconfigured", "excess"])
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        cleanup:
        flowHelperV2.deleteFlow(flow.flowId)

        where:
        mirrorDirection << [FlowPathDirection.FORWARD, FlowPathDirection.REVERSE]
    }

    @Tidy
    @Tags([TOPOLOGY_DEPENDENT])
    def "Can create a mirror point on the same port as flow, different vlan  [#swPair.dst.hwSwString, #encapType, #mirrorDirection]"() {
        given: "A flow"
        def flow = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow).tap { encapsulationType = encapType }

        when: "Add a mirror point on the same port with flow, different vlan"
        def mirrorPoint = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(mirrorDirection.toString())
                .mirrorPointSwitchId(swPair.dst.dpId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(flow.destination.switchId)
                        .portNumber(flow.destination.portNumber)
                        .vlanId(flow.destination.vlanId - 1)
                        .build())
                .build()
        flowHelperV2.createMirrorPoint(flow.flowId, mirrorPoint)

        then: "Mirror point is successfully created"
        northboundV2.getMirrorPoints(flow.flowId).points.size() == 1

        when: "Delete the flow without deleting its mirror point"
        flowHelperV2.deleteFlow(flow.flowId)

        then: "Mirror point is also deleted from db"
        database.getMirrorPoints().empty

        and: "Related switch pass validation"
        northbound.validateSwitch(swPair.dst.dpId).verifyRuleSectionsAreEmpty(swPair.dst.dpId)
        def testComplete = true

        cleanup:
        !testComplete && flowHelperV2.deleteFlow(flow.flowId)

        where:
        [swPair, mirrorDirection, encapType] <<
                //[swPair, reverse/forward, transit_vlan]
                ([getUniqueSwitchPairs().collect {
                    it.reversed //'reversed' since we test 'dst' here, but 'getUniqueSwitchPairs' method targets 'src'
                }, [FlowPathDirection.FORWARD, FlowPathDirection.REVERSE]].combinations()
                        .collect { it << FlowEncapsulationType.TRANSIT_VLAN; it } +

                //[swPair, reverse/forward, vxlan]
                [getUniqueVxlanSwitchPairs(false).collect { it.reversed },
                 [FlowPathDirection.FORWARD, FlowPathDirection.REVERSE]].combinations()
                        .collect { it << FlowEncapsulationType.VXLAN; it })

                        //https://github.com/telstra/open-kilda/issues/4374
                        .findAll { SwitchPair swPair, m, e ->
                            !swPair.dst.isWb5164()
                        }

    }

    @Tidy
    @Tags([LOW_PRIORITY])
    def "Can create multiple mirror points for the same flow and switch"() {
        given: "A flow"
        def swPair = topologyHelper.switchPairs[0]
        def flow = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow)

        when: "Add a Forward mirror point on the same port with flow, different vlan"
        def mirrorPointFw = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(FlowPathDirection.FORWARD.toString())
                .mirrorPointSwitchId(swPair.dst.dpId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(flow.destination.switchId)
                        .portNumber(flow.destination.portNumber)
                        .vlanId(flow.destination.vlanId - 1)
                        .build())
                .build()
        flowHelperV2.createMirrorPoint(flow.flowId, mirrorPointFw)

        then: "Mirror point is created"
        northboundV2.getMirrorPoints(flow.flowId).points.size() == 1

        when: "Add one more Forward mirror point on the same port with flow, different vlan"
        def mirrorPointFw2 = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(FlowPathDirection.FORWARD.toString())
                .mirrorPointSwitchId(swPair.dst.dpId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(flow.destination.switchId)
                        .portNumber(flow.destination.portNumber)
                        .vlanId(flow.destination.vlanId - 2)
                        .build())
                .build()
        flowHelperV2.createMirrorPoint(flow.flowId, mirrorPointFw2)

        then: "Mirror point is created"
        northboundV2.getMirrorPoints(flow.flowId).points.size() == 2

        and: "Mirrorring group for forward path has 3 buckets (main flow + 2 mirrors)"
        def fwGroupId = findMirrorRule(getFlowRules(swPair.dst.dpId), FlowPathDirection.FORWARD).instructions.applyActions.group
        def fl = flHelper.getFlsByRegions(swPair.dst.getRegions())[0].floodlightService
        def fwMirrorGroup = fl.getGroupsStats(swPair.dst.dpId).group.find { it.groupNumber == fwGroupId }
        fwMirrorGroup.bucketCounters.size() == 3

        when: "Add a Reverse mirror point on the different port with flow"
        def mirrorPointRv = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(FlowPathDirection.REVERSE.toString())
                .mirrorPointSwitchId(swPair.dst.dpId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(flow.destination.switchId)
                        .portNumber(flow.destination.portNumber)
                        .vlanId(0)
                        .build())
                .build()
        northboundV2.createMirrorPoint(flow.flowId, mirrorPointRv)

        then: "Mirror point is created"
        Wrappers.wait(WAIT_OFFSET) {
            assert northboundV2.getFlow(flow.flowId).mirrorPointStatuses.find {
                it.mirrorPointId == mirrorPointRv.mirrorPointId
            }.status == FlowPathStatus.ACTIVE.toString().toLowerCase()
        }

        and: "Mirrorring group for reverse path has 2 buckets (main flow + 1 mirror)"
        def rvGroupId = findMirrorRule(getFlowRules(swPair.dst.dpId), FlowPathDirection.REVERSE).instructions.applyActions.group
        def rvMirrorGroup = fl.getGroupsStats(swPair.dst.dpId).group.find { it.groupNumber == rvGroupId }
        rvMirrorGroup.bucketCounters.size() == 2

        cleanup:
        flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    def "System also updates mirror rule after flow partial update"() {
        given: "A flow with mirror point"
        def swPair = topologyHelper.switchPairs[0]
        def flow = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow)
        def freePort = (topology.getAllowedPortsForSwitch(swPair.dst) - flow.destination.portNumber)[0]
        def mirrorPointFw = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(FlowPathDirection.FORWARD.toString())
                .mirrorPointSwitchId(swPair.dst.dpId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(flow.destination.switchId)
                        .portNumber(freePort)
                        .vlanId(flow.destination.vlanId - 1)
                        .build())
                .build()
        flowHelperV2.createMirrorPoint(flow.flowId, mirrorPointFw)

        when: "Update flow port and vlan on the same endpoint where mirror is"
        def newFlowPort = (topology.getAllowedPortsForSwitch(swPair.dst) - flow.destination.portNumber - freePort)[0]
        def newFlowVlan = flow.destination.vlanId - 2
        flowHelperV2.partialUpdate(flow.flowId, new FlowPatchV2().tap {
            destination = new FlowPatchEndpoint().tap {
                portNumber = newFlowPort
                vlanId = newFlowVlan
            }
        })

        then: "Flow and affected switch are valid"
        northbound.validateSwitch(swPair.dst.dpId).verifyRuleSectionsAreEmpty(swPair.dst.dpId,
                ["missing", "misconfigured", "excess"])
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        and: "Mirror rule has updated port/vlan values"
        def mirrorRule = findMirrorRule(getFlowRules(swPair.dst.dpId), FlowPathDirection.FORWARD)
        def setVlanInstruction = mirrorRule.instructions.applyActions.setFieldActions[0]
        setVlanInstruction.fieldName == "vlan_vid"
        setVlanInstruction.fieldValue == "$newFlowVlan"
        def fl = flHelper.getFlsByRegions(swPair.dst.getRegions())[0].floodlightService
        def group = fl.getGroupsDesc(swPair.dst.dpId).groupDesc.find {
            it.groupNumber == mirrorRule.instructions.applyActions.group
        }
        group.buckets.find { it.actions == "output=$newFlowPort" }

        cleanup:
        flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    @Tags([LOW_PRIORITY])
    def "Mirror point can be created for a default flow (0 vlan), #mirrorDirection"() {
        given: "A default flow"
        def swPair = topologyHelper.getSwitchPairs(true).find { SwitchPair pair ->
            [pair.src, pair.dst].every { it.traffGens } && pair.src.traffGens.size() > 1
        }
        assumeTrue(swPair as boolean, "Unable to find a switch pair: both with traffgens, src with at least 2")
        def flow = flowHelperV2.randomFlow(swPair).tap { source.vlanId = 0 }
        flowHelperV2.addFlow(flow)

        when: "Create a mirror point"
        def mirrorTg = swPair.src.traffGens[1]
        def mirrorEndpoint = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(mirrorDirection.toString().toLowerCase())
                .mirrorPointSwitchId(swPair.src.dpId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(swPair.src.dpId).portNumber(mirrorTg.switchPort)
                        .vlanId(flowHelperV2.randomVlan())
                        .build())
                .build()
        flowHelperV2.createMirrorPoint(flow.flowId, mirrorEndpoint)

        then: "Mirror point is created and Active"
        and: "Related switches and flow pass validation"
        pathHelper.getInvolvedSwitches(flow.flowId).each {
            northbound.validateSwitch(it.dpId).verifyRuleSectionsAreEmpty(it.dpId,
                    ["missing", "misconfigured", "excess"])
        }
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        and: "Flow passes traffic on main path as well as to the mirror"
        def traffExam = traffExamProvider.get()
        def mirrorPortStats = new TraffgenStats(traffExam, mirrorTg, [mirrorEndpoint.sinkEndpoint.vlanId])
        def rxPacketsBefore = mirrorPortStats.get().rxPackets
        verifyTraffic(traffExam, flow, mirrorDirection)
        mirrorPortStats.get().rxPackets - rxPacketsBefore > 0

        cleanup:
        mirrorPortStats && mirrorPortStats.close()
        flow && flowHelperV2.deleteFlow(flow.flowId)

        where:
        mirrorDirection << [FlowPathDirection.FORWARD, FlowPathDirection.REVERSE]
    }

    @Tidy
    @Tags([LOW_PRIORITY])
    def "Flow mirror point works properly with a qinq flow, #mirrorDirection"() {
        given: "A qinq flow"
        def swPair = topologyHelper.getSwitchPairs(true).find {
            [it.src, it.dst].every { it.traffGens && isMultitable(it.dpId) } && it.src.traffGens.size() > 1
        } ?: assumeTrue(false, "Not able to find enough switches with traffgens and in multi-table mode")
        def flow = flowHelperV2.randomFlow(swPair).tap {
            source.innerVlanId = 100
            destination.innerVlanId = 200
        }
        flowHelperV2.addFlow(flow)

        when: "Create a mirror point"
        def mirrorTg = swPair.src.traffGens[1]
        def mirrorEndpoint = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(mirrorDirection.toString().toLowerCase())
                .mirrorPointSwitchId(swPair.src.dpId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(swPair.src.dpId).portNumber(mirrorTg.switchPort)
                        .vlanId(flowHelperV2.randomVlan([flow.source.vlanId, flow.source.innerVlanId]))
                        .build())
                .build()
        flowHelperV2.createMirrorPoint(flow.flowId, mirrorEndpoint)

        then: "Mirror point is created, flow and switches are valid"
        pathHelper.getInvolvedSwitches(flow.flowId).each {
            northbound.validateSwitch(it.dpId).verifyRuleSectionsAreEmpty(it.dpId,
                    ["missing", "misconfigured", "excess"])
        }
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        and: "Traffic examination reports packets on mirror point"
        def traffExam = traffExamProvider.get()
        def mirrorPortStats = new TraffgenStats(traffExam, mirrorTg, [mirrorEndpoint.sinkEndpoint.vlanId])
        def rxPacketsBefore = mirrorPortStats.get().rxPackets
        verifyTraffic(traffExam, flow, mirrorDirection)
        mirrorPortStats.get().rxPackets - rxPacketsBefore > 0

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
        mirrorPortStats && mirrorPortStats.close()

        where:
        mirrorDirection << [FlowPathDirection.REVERSE, FlowPathDirection.FORWARD]
    }

    @Tidy
    @Tags([LOW_PRIORITY])
    def "Unable to create a mirror endpoint with #data.testDescr on the transit switch"() {
        given: "A flow with transit switch"
        List<PathNode> path
        List<Switch> involvedSwitches
        def swPair = topologyHelper.switchPairs.find {
            path = it.paths.find {
                involvedSwitches = pathHelper.getInvolvedSwitches(it)
                involvedSwitches.size() > 2
            }
        }
        assumeTrue(swPair as boolean, "Unable to find a switch pair with path of 2+ switches")
        def flow = flowHelperV2.randomFlow(swPair)
        swPair.paths.findAll { it != path }.each { pathHelper.makePathMorePreferable(path, it) }
        flowHelperV2.addFlow(flow)

        when: "Try to add a mirror endpoint on the transit switch"
        def freePort = (topology.getAllowedPortsForSwitch(swPair.dst) - flow.destination.portNumber)[0]
        def mirrorPoint = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(FlowPathDirection.FORWARD.toString())
                .mirrorPointSwitchId(data.mirrorPointSwitch(involvedSwitches).dpId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(data.sinkEndpointSwitch(involvedSwitches).dpId)
                        .portNumber(freePort).vlanId(flowHelperV2.randomVlan()).build())
                .build()
        northboundV2.createMirrorPoint(flow.flowId, mirrorPoint)

        then: "Error is returned, cannot create mirror point on given sw"
        def error = thrown(HttpClientErrorException)
        error.statusCode == HttpStatus.BAD_REQUEST
        def errorDetails = error.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not create flow mirror point"
        errorDetails.errorDescription == data.errorDesc(involvedSwitches)

        cleanup:
        flowHelperV2.deleteFlow(flow.flowId)
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))

        where:
        data << [
                [
                        testDescr         : "mirrorEndpoint and sinkEndpoint",
                        mirrorPointSwitch : { List<Switch> involved -> involved[1] },
                        sinkEndpointSwitch: { List<Switch> involved -> involved[1] },
                        errorDesc         : { List<Switch> involved -> "Invalid mirror point switch id: ${involved[1].dpId}" }
                ],
                [
                        testDescr         : "sinkEndpoint",
                        mirrorPointSwitch : { List<Switch> involved -> involved[0] },
                        sinkEndpointSwitch: { List<Switch> involved -> involved[1] },
                        errorDesc         : { List<Switch> involved ->
                            "Invalid sink endpoint switch id: ${involved[1].dpId}. In the current " +
                                    "implementation, the sink switch id cannot differ from the mirror point switch id."
                        }
                ],
                [
                        testDescr         : "mirrorEndpoint",
                        mirrorPointSwitch : { List<Switch> involved -> involved[1] },
                        sinkEndpointSwitch: { List<Switch> involved -> involved[0] },
                        errorDesc         : { List<Switch> involved ->
                            "Invalid sink endpoint switch id: ${involved[0].dpId}. In the current " +
                                    "implementation, the sink switch id cannot differ from the mirror point switch id."
                        }
                ]
        ]
    }

    @Tidy
    @Tags([LOW_PRIORITY])
    @Unroll("#testData.testName, #testData.mirrorPoint.mirrorPointDirection")
    def "Test possible error scenarios during mirror point creation"(MirrorErrorTestData testData) {
        assumeTrue(testData.skipTestReason.empty, testData.skipTestReason)

        given: "A flow"
        def flow = testData.flow
        flowHelperV2.addFlow(flow)

        when: "Try adding a mirror point with conflict"
        northboundV2.createMirrorPoint(flow.flowId, testData.mirrorPoint)

        then: "Error is returned, cannot create mirror point with given params"
        def error = thrown(HttpClientErrorException)
        error.statusCode == testData.errorCode
        def errorDetails = error.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not create flow mirror point"
        errorDetails.errorDescription == testData.errorDescription

        cleanup:
        flowHelperV2.deleteFlow(flow.flowId)

        where:
        testData << [
                new MirrorErrorTestData("Unable to create a mirror point with parent flow conflict", {
                    it.flow = flowHelperV2.randomFlow(topologyHelper.switchPairs[0])
                    it.mirrorPoint = FlowMirrorPointPayload.builder()
                            .mirrorPointId(flowHelperV2.generateFlowId())
                            .mirrorPointSwitchId(flow.source.switchId)
                            .mirrorPointDirection(FlowPathDirection.FORWARD.toString())
                            .sinkEndpoint(FlowEndpointV2.builder().switchId(flow.source.switchId)
                                    .portNumber(flow.source.portNumber)
                                    .vlanId(flow.source.vlanId)
                                    .build())
                            .build()
                    it.errorCode = HttpStatus.CONFLICT
                    it.errorDescription = getEndpointConflictError(it.mirrorPoint, it.flow, "source")
                }),
                new MirrorErrorTestData("Unable to create a mirror endpoint on the src sw and sink back to dst sw", {
                    def swPair = topologyHelper.switchPairs[0]
                    it.flow = flowHelperV2.randomFlow(swPair)
                    def freePort = (topology.getAllowedPortsForSwitch(swPair.src) - flow.source.portNumber)[0]
                    it.mirrorPoint = FlowMirrorPointPayload.builder()
                            .mirrorPointId(flowHelperV2.generateFlowId())
                            .mirrorPointDirection(FlowPathDirection.FORWARD.toString())
                            .mirrorPointSwitchId(swPair.dst.dpId)
                            .sinkEndpoint(FlowEndpointV2.builder().switchId(swPair.src.dpId).portNumber(freePort)
                                    .vlanId(flowHelperV2.randomVlan())
                                    .build())
                            .build()
                    it.errorCode = HttpStatus.BAD_REQUEST
                    it.errorDescription = "Invalid sink endpoint switch id: $swPair.src.dpId. In the current " +
                            "implementation, the sink switch id cannot differ from the mirror point switch id."
                }),
                new MirrorErrorTestData("Unable to create a mirror point with isl conflict", {
                    def swPair = topologyHelper.switchPairs[0]
                    it.flow = flowHelperV2.randomFlow(swPair)
                    def islPort = topology.getBusyPortsForSwitch(swPair.dst)[0]
                    it.mirrorPoint = FlowMirrorPointPayload.builder()
                            .mirrorPointId(flowHelperV2.generateFlowId())
                            .mirrorPointDirection(FlowPathDirection.FORWARD.toString())
                            .mirrorPointSwitchId(swPair.dst.dpId)
                            .sinkEndpoint(FlowEndpointV2.builder().switchId(swPair.dst.dpId)
                                    .portNumber(islPort)
                                    .vlanId(flowHelperV2.randomVlan())
                                    .build())
                            .build()
                    it.errorCode = HttpStatus.BAD_REQUEST
                    it.errorDescription = "The port $islPort on the switch '$swPair.dst.dpId' is occupied by an ISL " +
                            "(destination endpoint collision)."
                }),
                new MirrorErrorTestData("Unable to create a mirror point with s42Port conflict", {
                    def s42Switches = topology.getActiveServer42Switches()*.dpId
                    def swPair = topologyHelper.getSwitchPairs(true).find {
                        it.dst.dpId in s42Switches
                    }
                    it.flow = flowHelperV2.randomFlow(swPair)
                    def s42Port = swPair.dst.prop.server42Port
                    it.mirrorPoint = FlowMirrorPointPayload.builder()
                            .mirrorPointId(flowHelperV2.generateFlowId())
                            .mirrorPointDirection(FlowPathDirection.FORWARD.toString())
                            .mirrorPointSwitchId(swPair.dst.dpId)
                            .sinkEndpoint(FlowEndpointV2.builder().switchId(swPair.dst.dpId)
                                    .portNumber(s42Port)
                                    .vlanId(flowHelperV2.randomVlan())
                                    .build())
                            .build()
                    it.errorCode = HttpStatus.BAD_REQUEST
                    it.errorDescription = "Server 42 port in the switch properties for switch '$swPair.dst.dpId' " +
                            "is set to '$s42Port'. It is not possible to create or update an endpoint " +
                            "with these parameters."
                })
        ].collectMany {
            [it.tap { mirrorPoint.mirrorPointDirection = FlowPathDirection.FORWARD.toString() },
             it.jacksonCopy().tap { mirrorPoint.mirrorPointDirection = FlowPathDirection.REVERSE.toString() }]
        }
    }

    @Tidy
    @Tags([LOW_PRIORITY])
    def "Unable to create a mirror point with existing flow conflict, #mirrorDirection"() {
        given: "A flow"
        def swPair = topologyHelper.switchPairs[0]
        def flow = flowHelperV2.randomFlow(swPair)
        def otherFlow = flowHelperV2.randomFlow(swPair, false, [flow])
        flowHelperV2.addFlow(flow)
        flowHelperV2.addFlow(otherFlow)

        when: "Try adding a mirror point that conflicts with other existing flow"
        def mirrorPoint = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(mirrorDirection.toString())
                .mirrorPointSwitchId(swPair.src.dpId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(otherFlow.source.switchId)
                        .portNumber(otherFlow.source.portNumber)
                        .vlanId(otherFlow.source.vlanId)
                        .build())
                .build()
        northboundV2.createMirrorPoint(flow.flowId, mirrorPoint)

        then: "Error is returned, cannot create mirror point with given params"
        def error = thrown(HttpClientErrorException)
        error.statusCode == HttpStatus.CONFLICT
        def errorDetails = error.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not create flow mirror point"
        errorDetails.errorDescription == getEndpointConflictError(mirrorPoint, otherFlow, "source")

        cleanup:
        [flow, otherFlow].each { flowHelperV2.deleteFlow(it.flowId) }

        where:
        mirrorDirection << [FlowPathDirection.FORWARD, FlowPathDirection.REVERSE]
    }

    @Tidy
    @Tags([LOW_PRIORITY])
    def "Unable to create a flow that conflicts with mirror point, #mirrorDirection"() {
        given: "A flow with mirror point"
        def swPair = topologyHelper.switchPairs[0]
        def flow = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow)
        def freePort = (topology.getAllowedPortsForSwitch(swPair.dst) - flow.destination.portNumber)[0]
        def mirrorPoint = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(mirrorDirection.toString())
                .mirrorPointSwitchId(swPair.dst.dpId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(flow.destination.switchId)
                        .portNumber(freePort)
                        .vlanId(flowHelperV2.randomVlan())
                        .build())
                .build()
        northboundV2.createMirrorPoint(flow.flowId, mirrorPoint)
        Wrappers.wait(WAIT_OFFSET) {
            assert northboundV2.getFlow(flow.flowId).mirrorPointStatuses[0].status ==
                    FlowPathStatus.ACTIVE.toString().toLowerCase()
        }

        when: "Try adding a flow that conflicts with existing mirror point"
        def otherFlow = flowHelperV2.randomFlow(swPair, false, [flow]).tap {
            it.destination.portNumber = mirrorPoint.sinkEndpoint.portNumber
            it.destination.vlanId = mirrorPoint.sinkEndpoint.vlanId
        }
        northboundV2.addFlow(otherFlow)

        then: "Error is returned, cannot create flow that conflicts with mirror point"
        def error = thrown(HttpClientErrorException)
        error.statusCode == HttpStatus.CONFLICT
        def errorDetails = error.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not create flow"
        errorDetails.errorDescription == getEndpointConflictError(otherFlow.destination, mirrorPoint)

        cleanup:
        flowHelperV2.deleteFlow(flow.flowId)
        !error && flowHelperV2.deleteFlow(otherFlow.flowId)

        where:
        mirrorDirection << [FlowPathDirection.FORWARD, FlowPathDirection.REVERSE]
    }

    @Tidy
    @Tags([LOW_PRIORITY])
    def "Unable to create mirror point with connected devices enabled, #mirrorDirection"() {
        given: "A flow with connected devices enabled"
        def swPair = topologyHelper.switchPairs[0]
        def initialSrcProps = enableMultiTableIfNeeded(true, swPair.src.dpId)
        def flow = flowHelperV2.randomFlow(swPair).tap {
            source.detectConnectedDevices = new DetectConnectedDevicesV2(true, true)
        }
        flowHelperV2.addFlow(flow)

        when: "Try to create a mirror for the flow (on the same endpoint)"
        def freePort = (topology.getAllowedPortsForSwitch(swPair.src) - flow.source.portNumber)[0]
        def mirrorPoint = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(mirrorDirection.toString())
                .mirrorPointSwitchId(swPair.src.dpId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(flow.source.switchId)
                        .portNumber(freePort)
                        .vlanId(flowHelperV2.randomVlan())
                        .build())
                .build()
        northboundV2.createMirrorPoint(flow.flowId, mirrorPoint)

        then: "Error is returned, cannot create create mirror for a flow with devices"
        def error = thrown(HttpClientErrorException)
        error.statusCode == HttpStatus.BAD_REQUEST
        def errorDetails = error.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not create flow mirror point"
        errorDetails.errorDescription == "Connected devices feature is active on the flow $flow.flowId" +
                " for endpoint switchId=\"$flow.source.switchId\" port=$flow.source.portNumber vlanId=$flow.source.vlanId, " +
                "flow mirror point cannot be created this flow"

        cleanup:
        flowHelperV2.deleteFlow(flow.flowId)
        initialSrcProps && restoreSwitchProperties(swPair.src.dpId, initialSrcProps)

        where:
        mirrorDirection << [FlowPathDirection.FORWARD, FlowPathDirection.REVERSE]
    }

    @Tidy
    @Tags([LOW_PRIORITY])
    def "Unable to update flow and enable connected devices if mirror is present, #mirrorDirection"() {
        given: "A flow with a mirror point"
        def swPair = topologyHelper.switchPairs[0]
        def initialSrcProps = enableMultiTableIfNeeded(true, swPair.src.dpId)
        def flow = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow)
        def freePort = (topology.getAllowedPortsForSwitch(swPair.src) - flow.source.portNumber)[0]
        def mirrorPoint = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(mirrorDirection.toString())
                .mirrorPointSwitchId(swPair.src.dpId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(flow.source.switchId)
                        .portNumber(freePort)
                        .vlanId(flowHelperV2.randomVlan())
                        .build())
                .build()
        flowHelperV2.createMirrorPoint(flow.flowId, mirrorPoint)

        when: "Try to partial update the flow and enable connected devices"
        northboundV2.partialUpdate(flow.flowId, new FlowPatchV2().tap {
            source = new FlowPatchEndpoint().tap {
                detectConnectedDevices = new DetectConnectedDevicesV2(true, true)
            }
        })

        then: "Error is returned, cannot enable devices on a flow with mirror"
        def error = thrown(HttpClientErrorException)
        error.statusCode == HttpStatus.BAD_REQUEST
        def errorDetails = error.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not update flow"
        errorDetails.errorDescription == "Flow mirror point is created for the flow $flow.flowId, lldp or arp can not be set to true."

        cleanup:
        flowHelperV2.deleteFlow(flow.flowId)
        initialSrcProps && restoreSwitchProperties(swPair.src.dpId, initialSrcProps)

        where:
        mirrorDirection << [FlowPathDirection.FORWARD, FlowPathDirection.REVERSE]
    }

    @Tidy
    @Tags([LOW_PRIORITY])
    def "Cannot enable connected devices on switch if mirror is present"() {
        given: "A flow with a mirror endpoint"
        assumeTrue(useMultitable, "Multi table is not enabled in kilda configuration")
        def swPair = topologyHelper.switchPairs[0]
        def flow = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow)
        def freePort = (topology.getAllowedPortsForSwitch(swPair.src) - flow.source.portNumber)[0]
        def mirrorPoint = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(FlowPathDirection.FORWARD.toString())
                .mirrorPointSwitchId(swPair.src.dpId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(flow.source.switchId)
                        .portNumber(freePort)
                        .vlanId(flowHelperV2.randomVlan())
                        .build())
                .build()
        flowHelperV2.createMirrorPoint(flow.flowId, mirrorPoint)

        when: "Try to enable connected devices for switch where mirror is created"
        def originalProps = northbound.getSwitchProperties(swPair.src.dpId)
        northbound.updateSwitchProperties(swPair.src.dpId, originalProps.jacksonCopy().tap {
            it.switchArp = true
            it.switchLldp = true
        })

        then: "Error is returned, cannot enable devices on a switch with mirror"
        def error = thrown(HttpClientErrorException)
        error.statusCode == HttpStatus.BAD_REQUEST
        def errorDetails = error.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Flow mirror point is created on the switch $swPair.src.dpId, switchLldp or " +
                "switchArp can not be set to true."
        errorDetails.errorDescription == "Failed to update switch properties."

        cleanup:
        flowHelperV2.deleteFlow(flow.flowId)
        !error && originalProps && restoreSwitchProperties(swPair.src.dpId, originalProps)
    }

    @Tidy
    @Tags([LOW_PRIORITY])
    def "Cannot create mirror on a switch with enabled connected devices"() {
        given: "A switch with enabled connected devices"
        assumeTrue(useMultitable, "Multi table is not enabled in kilda configuration")
        def swPair = topologyHelper.switchPairs[0]
        def originalProps = northbound.getSwitchProperties(swPair.src.dpId)
        northbound.updateSwitchProperties(swPair.src.dpId, originalProps.jacksonCopy().tap {
            it.switchArp = true
            it.switchLldp = true
        })

        when: "Try to create a mirror on the switch with devices"
        def flow = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow)
        def freePort = (topology.getAllowedPortsForSwitch(swPair.src) - flow.source.portNumber)[0]
        def mirrorPoint = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(FlowPathDirection.REVERSE.toString())
                .mirrorPointSwitchId(swPair.src.dpId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(flow.source.switchId)
                        .portNumber(freePort)
                        .vlanId(flowHelperV2.randomVlan())
                        .build())
                .build()
        northboundV2.createMirrorPoint(flow.flowId, mirrorPoint)

        then: "Error is returned, cannot create flow that conflicts with mirror point"
        def error = thrown(HttpClientErrorException)
        error.statusCode == HttpStatus.BAD_REQUEST
        def errorDetails = error.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not create flow mirror point"
        errorDetails.errorDescription == "Connected devices feature is active on the switch $swPair.src.dpId, flow " +
                "mirror point cannot be created on this switch."

        cleanup:
        flowHelperV2.deleteFlow(flow.flowId)
        restoreSwitchProperties(swPair.src.dpId, originalProps)
    }

    @Tidy
    @Tags([LOW_PRIORITY])
    def "Unable to create a mirror point with existing mirror point conflict, #mirrorDirection"() {
        given: "A flow with mirror point"
        def swPair = topologyHelper.switchPairs[0]
        def flow = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow)
        def freePort = (topology.getAllowedPortsForSwitch(swPair.dst) - flow.destination.portNumber)[0]
        def mirrorPoint = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(mirrorDirection.toString())
                .mirrorPointSwitchId(swPair.dst.dpId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(flow.destination.switchId)
                        .portNumber(freePort)
                        .vlanId(flowHelperV2.randomVlan())
                        .build())
                .build()
        northboundV2.createMirrorPoint(flow.flowId, mirrorPoint)
        Wrappers.wait(WAIT_OFFSET) {
            assert northboundV2.getFlow(flow.flowId).mirrorPointStatuses[0].status ==
                    FlowPathStatus.ACTIVE.toString().toLowerCase()
        }

        when: "Try adding one more mirror point that conflicts with existing mirror point"
        def mirrorPoint2 = FlowMirrorPointPayload.builder()
                .mirrorPointId(flowHelperV2.generateFlowId())
                .mirrorPointDirection(mirrorDirection.toString())
                .mirrorPointSwitchId(mirrorPoint.mirrorPointSwitchId)
                .sinkEndpoint(FlowEndpointV2.builder().switchId(mirrorPoint.mirrorPointSwitchId)
                        .portNumber(mirrorPoint.sinkEndpoint.portNumber)
                        .vlanId(mirrorPoint.sinkEndpoint.vlanId)
                        .build())
                .build()
        northboundV2.createMirrorPoint(flow.flowId, mirrorPoint2)

        then: "Error is returned, cannot create flow that conflicts with mirror point"
        def error = thrown(HttpClientErrorException)
        error.statusCode == HttpStatus.CONFLICT
        def errorDetails = error.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not create flow mirror point"
        errorDetails.errorDescription == getEndpointConflictError(mirrorPoint2.sinkEndpoint, mirrorPoint)

        cleanup:
        flowHelperV2.deleteFlow(flow.flowId)

        where:
        mirrorDirection << [FlowPathDirection.FORWARD, FlowPathDirection.REVERSE]
    }


    List<FlowEntry> getFlowRules(SwitchId swId) {
        northbound.getSwitchRules(swId).flowEntries
                .findAll { new FlowSegmentCookie(it.cookie).direction != FlowPathDirection.UNDEFINED }
    }

    static FlowEntry findRule(List<FlowEntry> rules, FlowPathDirection pathDirection, boolean isMirror) {
        rules.find {
            def cookie = new FlowSegmentCookie(it.cookie)
            cookie.direction == pathDirection && cookie.isMirror() == isMirror
        }
    }

    static FlowEntry findFlowRule(List<FlowEntry> rules, FlowPathDirection pathDirection) {
        findRule(rules, pathDirection, false)
    }

    static FlowEntry findMirrorRule(List<FlowEntry> rules, FlowPathDirection pathDirection) {
        findRule(rules, pathDirection, true)
    }

    static String getEndpointConflictError(FlowMirrorPointPayload mirrorEp, FlowRequestV2 existingFlow, String srcOrDst) {
        FlowEndpointV2 flowEndpoint = existingFlow."$srcOrDst"
        "Requested flow '$mirrorEp.mirrorPointId' conflicts with existing flow '$existingFlow.flowId'. Details: " +
                "requested flow '$mirrorEp.mirrorPointId' destination: " +
                "switchId=\"${mirrorEp.sinkEndpoint.switchId}\" port=${mirrorEp.sinkEndpoint.portNumber} " +
                "vlanId=${mirrorEp.sinkEndpoint.vlanId}, existing flow '$existingFlow.flowId' $srcOrDst: " +
                "switchId=\"${flowEndpoint.switchId}\" port=${flowEndpoint.portNumber} vlanId=${flowEndpoint.vlanId}"
    }

    static String getEndpointConflictError(FlowRequestV2 reqFlow, FlowMirrorPointPayload existingMirror, String srcOrDst) {
        FlowEndpointV2 flowEndpoint = reqFlow."$srcOrDst"
        "Requested flow '$reqFlow.flowId' conflicts with existing flow '$existingMirror.mirrorPointId'. Details: " +
                "requested flow '$reqFlow.flowId' $srcOrDst: switchId=\"${flowEndpoint.switchId}\" " +
                "port=${flowEndpoint.portNumber} vlanId=${flowEndpoint.vlanId}, existing flow " +
                "'$existingMirror.mirrorPointId' destination: switchId=\"${existingMirror.sinkEndpoint.switchId}\" " +
                "port=${existingMirror.sinkEndpoint.portNumber} vlanId=${existingMirror.sinkEndpoint.vlanId}"
    }

    static String getEndpointConflictError(FlowEndpointV2 flowEp, FlowMirrorPointPayload existingMirror) {
        "Requested endpoint 'switchId=\"$flowEp.switchId\" port=$flowEp.portNumber vlanId=$flowEp.vlanId' conflicts " +
                "with existing flow mirror point '$existingMirror.mirrorPointId'."
    }

    static Exam getExam(FlowBidirectionalExam biExam, FlowPathDirection direction) {
        direction == FlowPathDirection.FORWARD ? biExam.forward : biExam.reverse
    }

    private SwitchPropertiesDto enableMultiTableIfNeeded(boolean needDevices, SwitchId switchId) {
        def initialProps = northbound.getSwitchProperties(switchId)
        if (needDevices && !initialProps.multiTable) {
            def sw = topology.switches.find { it.dpId == switchId }
            switchHelper.updateSwitchProperties(sw, initialProps.jacksonCopy().tap {
                it.multiTable = true
            })
        }
        return initialProps
    }

    private void restoreSwitchProperties(SwitchId switchId, SwitchPropertiesDto initialProperties) {
        Switch sw = topology.switches.find { it.dpId == switchId }
        switchHelper.updateSwitchProperties(sw, initialProperties)
    }

    private void verifyTraffic(TraffExamService traffExam, FlowRequestV2 flow, FlowPathDirection mirrorDirection) {
        FlowBidirectionalExam biExam = new FlowTrafficExamBuilder(topology, traffExam)
                .buildBidirectionalExam(flowHelperV2.toV1(flow), 300, 1)
        getExam(biExam, mirrorDirection).with {
            udp = true
            bufferLength = 500 //due to MTU size issue on TG after mirror encapsulation
            resources = traffExam.startExam(it)
            assert traffExam.waitExam(it).hasTraffic()
        }
    }

    def isMultitable(SwitchId switchId) {
        return initialSwPropsCache(switchId).multiTable
    }

    @Memoized
    def initialSwPropsCache(SwitchId switchId) {
        return northbound.getSwitchProperties(switchId)
    }

    /**
     * Identify all unique tg switches that need to be tested. Return minimum amount of switch pairs, where switch pair
     * satisfies 'additional conditions' and src switch is a switch under test. Sources with 2+ traffgens have a higher
     * priority
     */
    @Memoized
    List<SwitchPair> getUniqueSwitchPairs(Closure additionalConditions = { true }) {
        def unpickedUniqueTgSwitches = topology.switches.findAll { it.traffGens }
                .unique(false) { it.hwSwString }
        def tgPairs = topologyHelper.getSwitchPairs(true).findAll {
            additionalConditions(it)
        }
        assumeTrue(tgPairs.size() > 0, "Unable to find any switchPairs with requested conditions")
        def result = []
        while (!unpickedUniqueTgSwitches.empty) {
            def pairs = tgPairs.findAll {
                it.src in unpickedUniqueTgSwitches
            }.sort(false) { swPair -> //switches that have 2+ traffgens go to the end of the list
                swPair.src.traffGens.size() > 1
            }
            if (pairs) {
                //pick a highest score pair, update list of unpicked switches, re-run
                def pair = pairs.last()
                result << pair
                unpickedUniqueTgSwitches = unpickedUniqueTgSwitches - pair.src
            } else {
                //if there is an untested src left, but there is no dst that matches 'additional conditions'
                log.warn("Switches left untested: ${unpickedUniqueTgSwitches*.hwSwString}")
                break
            }
        }
        return result
    }

    List<SwitchPair> getUniqueVxlanSwitchPairs(boolean needTraffgens) {
        getUniqueSwitchPairs({ SwitchPair swP ->
            def vxlanCheck = swP.paths.find {
                pathHelper.getInvolvedSwitches(it).every { switchHelper.isVxlanEnabled(it.dpId) }
            }
            def tgCheck = needTraffgens ? swP.src.traffGens && swP.dst.traffGens : true
            vxlanCheck && tgCheck
        })
    }

    private static class MirrorErrorTestData {
        String testName
        FlowRequestV2 flow
        FlowMirrorPointPayload mirrorPoint
        HttpStatus errorCode
        String errorDescription
        String skipTestReason = ""

        MirrorErrorTestData() {} //required for jacksonCopy()

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
