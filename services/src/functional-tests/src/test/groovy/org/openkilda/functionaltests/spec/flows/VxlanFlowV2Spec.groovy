package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.FlowHelperV2
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.DetectConnectedDevicesPayload
import org.openkilda.messaging.payload.flow.FlowEndpointPayload
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.Cookie
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.northbound.dto.v1.flows.PingInput
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.tools.FlowTrafficExamBuilder

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Unroll

import javax.inject.Provider

@Narrative("""This spec checks basic functionality(simple flow(rules, ping, traffic, validate), pinned flow,
flow with protected path, default flow) for a flow with VXLAN encapsulation.

NOTE: A flow with the 'VXLAN' encapsulation is supported on a Noviflow switches.
So, flow can be created on a Noviflow(src/dst/transit) switches only.""")
class VxlanFlowV2Spec extends HealthCheckSpecification {
    @Autowired
    Provider<TraffExamService> traffExamProvider

    @Autowired
    NorthboundServiceV2 northboundV2

    @Autowired
    FlowHelperV2 flowHelperV2

    @Unroll
    @Tags(HARDWARE)
    def "System allows to create/update encapsulation type for a flow\
(#encapsulationCreate.toString() -> #encapsulationUpdate.toString())"() {
        given: "Two active neighboring Noviflow switches with traffgens"
        def allTraffgenSwitchIds = topology.activeTraffGens*.switchConnected.findAll {
            it.noviflow && !it.wb5164
        }*.dpId ?: assumeTrue("Should be at least two active traffgens connected to NoviFlow switches",
                false)

        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            allTraffgenSwitchIds.contains(it.src.dpId) && allTraffgenSwitchIds.contains(it.dst.dpId)
        } ?: assumeTrue("Unable to find required switches in topology", false)

        when: "Create a flow with #encapsulationCreate.toString() encapsulation type"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.encapsulationType = encapsulationCreate
        flowHelperV2.addFlow(flow)

        then: "Flow is created with the #encapsulationCreate.toString() encapsulation type"
        def flowInfo = northbound.getFlow(flow.flowId)
        flowInfo.encapsulationType == encapsulationCreate.toString().toLowerCase()

        and: "Correct rules are installed"
        def vxlanRule = (flowInfo.encapsulationType == FlowEncapsulationType.VXLAN.toString().toLowerCase())
        def flowInfoFromDb = database.getFlow(flow.flowId)
        // ingressRule should contain "pushVxlan"
        // egressRule should contain "tunnel-id"
        verifyAll(northbound.getSwitchRules(switchPair.src.dpId).flowEntries) { rules ->
            rules.find {
                it.cookie == flowInfoFromDb.forwardPath.cookie.value
            }.instructions.applyActions.pushVxlan as boolean == vxlanRule
            rules.find {
                it.cookie == flowInfoFromDb.reversePath.cookie.value
            }.match.tunnelId as boolean == vxlanRule
        }

        verifyAll(northbound.getSwitchRules(switchPair.dst.dpId).flowEntries) { rules ->
            rules.find {
                it.cookie == flowInfoFromDb.forwardPath.cookie.value
            }.match.tunnelId as boolean == vxlanRule
            rules.find {
                it.cookie == flowInfoFromDb.reversePath.cookie.value
            }.instructions.applyActions.pushVxlan as boolean == vxlanRule
        }

        and: "Flow is valid"
        Wrappers.wait(PATH_INSTALLATION_TIME) {
            northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }
        }

        and: "The flow allows traffic"
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(toFlowPayload(flow), 1000)
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        and: "Flow is pingable"
        verifyAll(northbound.pingFlow(flow.flowId, new PingInput())) {
            forward.pingSuccess
            reverse.pingSuccess
        }

        when: "Try to update the encapsulation type to #encapsulationUpdate.toString()"
        northbound.updateFlow(flowInfo.id, flowInfo.tap { it.encapsulationType = encapsulationUpdate })

        then: "The encapsulation type is changed to #encapsulationUpdate.toString()"
        def flowInfo2 = northbound.getFlow(flow.flowId)
        flowInfo2.encapsulationType == encapsulationUpdate.toString().toLowerCase()

        and: "Flow is valid"
        Wrappers.wait(PATH_INSTALLATION_TIME) {
            northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }
        }

        and: "The flow allows traffic"
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        and: "Flow is pingable"
        verifyAll(northbound.pingFlow(flow.flowId, new PingInput())) {
            forward.pingSuccess
            reverse.pingSuccess
        }

        and: "Rules are recreated"
        def flowInfoFromDb2 = database.getFlow(flow.flowId)
        [flowInfoFromDb.forwardPath.cookie.value, flowInfoFromDb.reversePath.cookie.value].sort() !=
                [flowInfoFromDb2.forwardPath.cookie.value, flowInfoFromDb2.reversePath.cookie.value].sort()

        and: "New rules are installed correctly"
        verifyAll(northbound.getSwitchRules(switchPair.src.dpId).flowEntries) { rules ->
            rules.find {
                it.cookie == flowInfoFromDb2.forwardPath.cookie.value
            }.instructions.applyActions.pushVxlan as boolean == !vxlanRule
            rules.find {
                it.cookie == flowInfoFromDb2.reversePath.cookie.value
            }.match.tunnelId as boolean == !vxlanRule
        }

        verifyAll(northbound.getSwitchRules(switchPair.dst.dpId).flowEntries) { rules ->
            rules.find {
                it.cookie == flowInfoFromDb2.forwardPath.cookie.value
            }.match.tunnelId as boolean == !vxlanRule
            rules.find {
                it.cookie == flowInfoFromDb2.reversePath.cookie.value
            }.instructions.applyActions.pushVxlan as boolean == !vxlanRule
        }

        and: "Cleanup: Delete the flow"
        flowHelper.deleteFlow(flow.flowId)

        where:
        encapsulationCreate                | encapsulationUpdate
        FlowEncapsulationType.TRANSIT_VLAN | FlowEncapsulationType.VXLAN
        FlowEncapsulationType.VXLAN        | FlowEncapsulationType.TRANSIT_VLAN
    }

    @Tags(HARDWARE)
    def "Able to CRUD a pinned flow with 'VXLAN' encapsulation"() {
        when: "Create a flow"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            it.src.noviflow && !it.src.wb5164 && it.dst.noviflow && !it.dst.wb5164
        }
        assumeTrue("Unable to find required switches in topology", switchPair as boolean)

        def flow = flowHelperV2.randomFlow(switchPair)
        flow.encapsulationType = FlowEncapsulationType.VXLAN
        flow.pinned = true
        flowHelperV2.addFlow(flow)

        then: "Flow is created"
        def flowInfo = northbound.getFlow(flow.flowId)
        flowInfo.pinned

        when: "Update the flow (pinned=false)"
        northbound.updateFlow(flowInfo.id, flowInfo.tap { it.pinned = false })

        then: "The pinned option is disabled"
        def newFlowInfo = northbound.getFlow(flow.flowId)
        !newFlowInfo.pinned
        flowInfo.lastUpdated < newFlowInfo.lastUpdated
        Wrappers.wait(PATH_INSTALLATION_TIME) {
            assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP
        }

        and: "Cleanup: Delete the flow"
        flowHelper.deleteFlow(flow.flowId)
    }

    @Tags(HARDWARE)
    def "Able to CRUD a vxlan flow with protected path"() {
        given: "Two active Noviflow switches with two available path at least"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            it.src.noviflow && !it.src.wb5164 && it.dst.noviflow && !it.dst.wb5164 &&
                    it.paths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size() >= 2
        } ?: assumeTrue("No suiting switches found", false)

        def availablePaths = switchPair.paths.findAll { path ->
            pathHelper.getInvolvedSwitches(path).every { it.noviflow && !it.wb5164 }
        }
        assumeTrue("Unable to find required paths between switches", availablePaths.size() >= 2)

        when: "Create a flow with protected path"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.allocateProtectedPath = true
        flow.encapsulationType = FlowEncapsulationType.VXLAN
        flowHelperV2.addFlow(flow)

        then: "Flow is created with protected path"
        def flowPathInfo = northbound.getFlowPath(flow.flowId)
        flowPathInfo.protectedPath
        northbound.getFlow(flow.flowId).flowStatusDetails

        and: "Rules for main and protected paths are created"
        Wrappers.wait(WAIT_OFFSET) { flowHelper.verifyRulesOnProtectedFlow(flow.flowId) }

        def flowInfoFromDb = database.getFlow(flow.flowId)
        // ingressRule should contain "pushVxlan"
        // egressRule should contain "tunnel-id"
        // protected path creates engressRule
        def protectedForwardCookie = flowInfoFromDb.protectedForwardPath.cookie.value
        def protectedReverseCookie = flowInfoFromDb.protectedReversePath.cookie.value
        verifyAll(northbound.getSwitchRules(switchPair.src.dpId).flowEntries) { rules ->
            rules.find {
                it.cookie == flowInfoFromDb.forwardPath.cookie.value
            }.instructions.applyActions.pushVxlan
            rules.find {
                it.cookie == flowInfoFromDb.reversePath.cookie.value
            }.match.tunnelId
            rules.find {
                it.cookie == flowInfoFromDb.protectedReversePath.cookie.value
            }.match.tunnelId
        }

        verifyAll(northbound.getSwitchRules(switchPair.dst.dpId).flowEntries) { rules ->
            rules.find {
                it.cookie == flowInfoFromDb.forwardPath.cookie.value
            }.match.tunnelId
            rules.find {
                it.cookie == flowInfoFromDb.reversePath.cookie.value
            }.instructions.applyActions.pushVxlan
            rules.find {
                it.cookie == flowInfoFromDb.protectedForwardPath.cookie.value
            }.match.tunnelId
        }

        and: "Validation of flow must be successful"
        northbound.validateFlow(flow.flowId).each { direction ->
            assert direction.discrepancies.empty
        }

        when: "Update flow: disable protected path(allocateProtectedPath=false)"
        def flowData = northbound.getFlow(flow.flowId)
        def protectedFlowPath = northbound.getFlowPath(flow.flowId).protectedPath.forwardPath
        northbound.updateFlow(flowData.id, flowData.tap { it.allocateProtectedPath = false })

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

        and: "And rules for main path are recreacted"
        def flowInfoFromDb2 = database.getFlow(flow.flowId)
        [flowInfoFromDb.forwardPath.cookie.value, flowInfoFromDb.reversePath.cookie.value].sort() !=
                [flowInfoFromDb2.forwardPath.cookie.value, flowInfoFromDb2.reversePath.cookie.value].sort()

        verifyAll(northbound.getSwitchRules(switchPair.src.dpId).flowEntries) { rules ->
            rules.find {
                it.cookie == flowInfoFromDb2.forwardPath.cookie.value
            }.instructions.applyActions.pushVxlan
            rules.find {
                it.cookie == flowInfoFromDb2.reversePath.cookie.value
            }.match.tunnelId
        }

        verifyAll(northbound.getSwitchRules(switchPair.dst.dpId).flowEntries) { rules ->
            rules.find {
                it.cookie == flowInfoFromDb2.forwardPath.cookie.value
            }.match.tunnelId
            rules.find {
                it.cookie == flowInfoFromDb2.reversePath.cookie.value
            }.instructions.applyActions.pushVxlan
        }

        and: "Validation of flow must be successful"
        northbound.validateFlow(flow.flowId).each { direction ->
            assert direction.discrepancies.empty
        }

        and: "Cleanup: Delete the flow and reset costs"
        flowHelper.deleteFlow(flow.flowId)
    }

    @Tags(HARDWARE)
    def "System allows tagged traffic via default flow(0<->0) with 'VXLAN' encapsulation"() {
        // we can't test (0<->20, 20<->0) because iperf is not able to establish a connection
        given: "Noviflow switches"
        def allTraffgenSwitchIds = topology.activeTraffGens*.switchConnected.findAll {
            it.noviflow && !it.wb5164
        }*.dpId ?: assumeTrue("Should be at least two active traffgens connected to NoviFlow switches for test execution",
                false)
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            allTraffgenSwitchIds.contains(it.src.dpId) && allTraffgenSwitchIds.contains(it.dst.dpId)
        } ?: assumeTrue("Unable to find required switches in topology", false)

        when: "Create a default flow"
        def defaultFlow = flowHelperV2.randomFlow(switchPair)
        defaultFlow.source.vlanId = 0
        defaultFlow.destination.vlanId = 0
        defaultFlow.encapsulationType = FlowEncapsulationType.VXLAN
        flowHelperV2.addFlow(defaultFlow)

        def flow = flowHelper.randomFlow(switchPair)
        flow.source.vlanId = 10
        flow.destination.vlanId = 10

        then: "System allows tagged traffic on the default flow"
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(flow, 1000)
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        and: "Cleanup: Delete the flow"
        flowHelper.deleteFlow(defaultFlow.flowId)
    }

    def "System doesn't allow to create a flow with 'VXLAN' encapsulation on a non-noviflow switches"() {
        setup:
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { !it.src.noviflow && !it.dst.noviflow }
        assumeTrue("Unable to find required switches in topology", switchPair as boolean)

        when: "Try to create a flow"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.encapsulationType = FlowEncapsulationType.VXLAN
        northboundV2.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        // TODO(andriidovhan)fix errorMessage when the 2587 issue is fixed
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not create flow: Not enough bandwidth found or path not found : " +
                "Failed to find path with requested bandwidth=$flow.maximumBandwidth: Switch $switchPair.src.dpId" +
                " doesn't have links with enough bandwidth"
    }

    @Tags(HARDWARE)
    def "System doesn't allow to create a vxlan flow when transit switch is not Noviflow"() {
        setup:
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().find { swP ->
            swP.src.noviflow && !swP.src.wb5164 && swP.dst.noviflow && !swP.dst.wb5164 && swP.paths.find { path ->
                pathHelper.getInvolvedSwitches(path).find { !it.noviflow }
            }
        } ?: assumeTrue("Unable to find required switches in topology", false)
        // find path with needed transit switch
        def requiredPath = switchPair.paths.find { pathHelper.getInvolvedSwitches(it).find { !it.noviflow } }
        // make all alternative paths are unavailable (bring ports down on the srcSwitch)
        List<PathNode> broughtDownPorts = []
        switchPair.paths.findAll { it != requiredPath }.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            antiflap.portDown(src.switchId, src.portNo)
        }
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == IslChangeType.FAILED
            }.size() == broughtDownPorts.size() * 2
        }

        when: "Try to create a flow"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.encapsulationType = FlowEncapsulationType.VXLAN
        northboundV2.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        //TODO(andriidovhan)add errorMessage when the 2587 issue is fixed

        and: "Cleanup: Reset costs"
        broughtDownPorts.every { antiflap.portUp(it.switchId, it.portNo) }
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts()
    }

    @Tags(HARDWARE)
    def "System doesn't allow to create a vxlan flow when dst switch is not Noviflow"() {
        given: "Noviflow and non-Noviflow switches"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            it.src.noviflow && !it.src.wb5164 && !it.dst.noviflow
        }
        assumeTrue("Unable to find required switches in topology", switchPair as boolean)

        when: "Try to create a flow"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.encapsulationType = FlowEncapsulationType.VXLAN
        northboundV2.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        // TODO(andriidovhan) fix errorMessage when the 2587 issue is fixed
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not create flow: Not enough bandwidth found or path not found : Failed to find path with " +
                "requested bandwidth=$flow.maximumBandwidth: " +
                "Switch $switchPair.dst.dpId doesn't have links with enough bandwidth"
    }

    @Unroll
    @Tags(HARDWARE)
    def "System allows to create/update encapsulation type for a one-switch flow\
(#encapsulationCreate.toString() -> #encapsulationUpdate.toString())"() {
        when: "Try to create a one-switch flow"
        def sw = topology.activeTraffGens*.switchConnected.find {
            it.noviflow && !it.wb5164
        } ?: assumeTrue("Should be at least one active traffgen connected to NoviFlow switch", false)
        def flow = flowHelperV2.singleSwitchFlow(sw)
        flow.encapsulationType = encapsulationCreate
        northboundV2.addFlow(flow)

        then: "Flow is created with the #encapsulationCreate.toString() encapsulation type"
        def flowInfo1 = northbound.getFlow(flow.flowId)
        flowInfo1.encapsulationType == encapsulationCreate.toString().toLowerCase()

        and: "Correct rules are installed"
        def flowInfoFromDb = database.getFlow(flow.flowId)
        // vxlan rules are not creating for a one-switch flow
        verifyAll(northbound.getSwitchRules(sw.dpId).flowEntries) { rules ->
            !rules.find {
                it.cookie == flowInfoFromDb.forwardPath.cookie.value
            }.instructions.applyActions.pushVxlan
            !rules.find {
                it.cookie == flowInfoFromDb.reversePath.cookie.value
            }.match.tunnelId
        }

        and: "Flow is valid"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        and: "Flow is pingable"
        verifyAll(northbound.pingFlow(flow.flowId, new PingInput())) {
            forward.pingSuccess
            reverse.pingSuccess
        }

        when: "Try to update the encapsulation type to #encapsulationUpdate.toString()"
        northbound.updateFlow(flowInfo1.id, flowInfo1.tap { it.encapsulationType = encapsulationUpdate })

        then: "The encapsulation type is changed to #encapsulationUpdate.toString()"
        def flowInfo2 = northbound.getFlow(flow.flowId)
        flowInfo2.encapsulationType == encapsulationUpdate.toString().toLowerCase()

        and: "Flow is valid"
        Wrappers.wait(PATH_INSTALLATION_TIME) {
            northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }
        }

        and: "Flow is pingable"
        verifyAll(northbound.pingFlow(flow.flowId, new PingInput())) {
            forward.pingSuccess
            reverse.pingSuccess
        }

        and: "Rules are recreated"
        def flowInfoFromDb2 = database.getFlow(flow.flowId)
        [flowInfoFromDb.forwardPath.cookie.value, flowInfoFromDb.reversePath.cookie.value].sort() !=
                [flowInfoFromDb2.forwardPath.cookie.value, flowInfoFromDb2.reversePath.cookie.value].sort()

        and: "New rules are installed correctly"
        verifyAll(northbound.getSwitchRules(sw.dpId).flowEntries) { rules ->
            !rules.find {
                it.cookie == flowInfoFromDb2.forwardPath.cookie.value
            }.instructions.applyActions.pushVxlan
            !rules.find {
                it.cookie == flowInfoFromDb2.reversePath.cookie.value
            }.match.tunnelId
        }

        and: "Cleanup: Delete the flow"
        flowHelper.deleteFlow(flow.flowId)

        where:
        encapsulationCreate                | encapsulationUpdate
        FlowEncapsulationType.TRANSIT_VLAN | FlowEncapsulationType.VXLAN
        FlowEncapsulationType.VXLAN        | FlowEncapsulationType.TRANSIT_VLAN

    }

    def "System doesn't allow to enable a flow with 'VXLAN' encapsulation on a non-noviflow switch"() {
        given: "A flow with 'TRANSIT_VLAN' encapsulation"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { !it.src.noviflow && !it.dst.noviflow }
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.encapsulationType = FlowEncapsulationType.TRANSIT_VLAN
        flowHelperV2.addFlow(flow)

        when: "Try to change the encapsulation type to VXLAN"
        def flowData = northbound.getFlow(flow.flowId)
        flowHelper.updateFlow(flowData.id, flowData.tap { it.encapsulationType = FlowEncapsulationType.VXLAN })

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        //TODO(andriidovhan) fix errorMessage when the 2587 issue is fixed
        exc.responseBodyAsString.to(MessageError).errorMessage == "Could not update flow: \
Not enough bandwidth found or path not found. Failed to find path with requested bandwidth=$flow.maximumBandwidth: \
Switch $switchPair.src.dpId doesn't have links with enough bandwidth"

        and: "Cleanup: Delete the flow"
        flowHelper.deleteFlow(flow.flowId)
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
}
