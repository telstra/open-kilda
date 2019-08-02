package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.model.Cookie
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.northbound.dto.v1.flows.PingInput
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
class VxlanFlowSpec extends HealthCheckSpecification {
    @Autowired
    Provider<TraffExamService> traffExamProvider

    @Unroll
    @Tags(HARDWARE)
    def "System allows to create/update encapsulation type for a flow\
(#encapsulationCreate.toString() -> #encapsulationUpdate.toString())"() {
        given: "Two active neighboring Noviflow switches with traffgens"
        def allTraffgenSwitchIds = topology.activeTraffGens*.switchConnected.findAll {
            it.noviflow
        }*.dpId ?: assumeTrue("Should be at least two active traffgens connected to NoviFlow switches",
                false)

        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            allTraffgenSwitchIds.contains(it.src.dpId) && allTraffgenSwitchIds.contains(it.dst.dpId)
        } ?: assumeTrue("Unable to find required switches in topology",false)

        when: "Create a flow with #encapsulationCreate.toString() encapsulation type"
        def flow = flowHelper.randomFlow(switchPair)
        flow.encapsulationType = encapsulationCreate
        flowHelper.addFlow(flow)

        then: "Flow is created with the #encapsulationCreate.toString() encapsulation type"
        def flowInfo = northbound.getFlow(flow.id)
        flowInfo.encapsulationType == encapsulationCreate.toString().toLowerCase()

        //TODO(andriidovhan) check rules(tunnel-id) when pr2503 is merged

        //TODO(andriidovhan) uncomment when pr2530 is merged
//        and: "Flow is valid"
//        northbound.validateFlow(flow.id).each { direction -> assert direction.asExpected }

        and: "The flow allows traffic"
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(flow, 0)
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        and: "Flow is pingable"
        def responsePing1 = northbound.pingFlow(flow.id, new PingInput())
        responsePing1.forward.pingSuccess
        responsePing1.reverse.pingSuccess

        when: "Try to update the encapsulation type to #encapsulationUpdate.toString()"
        northbound.updateFlow(flow.id, flow.tap { it.encapsulationType = encapsulationUpdate })

        then: "The encapsulation type is changed to #encapsulationUpdate.toString()"
        def flowInfo2 = northbound.getFlow(flow.id)
        flowInfo2.encapsulationType == encapsulationUpdate.toString().toLowerCase()

        //TODO(andriidovhan) uncomment when pr2530 is merged
//        and: "Flow is valid"
//        northbound.validateFlow(flow.id).each { direction -> assert direction.asExpected }

        and: "The flow allows traffic"
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        and: "Flow is pingable"
        def responsePing2 = northbound.pingFlow(flow.id, new PingInput())
        responsePing2.forward.pingSuccess
        responsePing2.reverse.pingSuccess

        //TODO(andriidovhan) check rules(tunnel-id) when pr2503 is merged

        and: "Cleanup: Delete the flow"
        flowHelper.deleteFlow(flow.id)

        where:
        encapsulationCreate | encapsulationUpdate
        FlowEncapsulationType.TRANSIT_VLAN | FlowEncapsulationType.VXLAN
        FlowEncapsulationType.VXLAN | FlowEncapsulationType.TRANSIT_VLAN
    }

    @Tags(HARDWARE)
    def "Able to CRUD a metered pinned flow with 'VXLAN' encapsulation"() {
        when: "Create a flow"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.src.noviflow && it.dst.noviflow }
        assumeTrue("Unable to find required switches in topology", switchPair as boolean)

        def flow = flowHelper.randomFlow(switchPair)
        flow.encapsulationType = FlowEncapsulationType.VXLAN
        flow.pinned = true
        flowHelper.addFlow(flow)

        then: "Flow is created"
        def flowInfo = northbound.getFlow(flow.id)
        flowInfo.pinned

        when: "Update the flow (pinned=false)"
        northbound.updateFlow(flow.id, flow.tap { it.pinned = false })

        then: "The pinned option is disabled"
        def newFlowInfo = northbound.getFlow(flow.id)
        !newFlowInfo.pinned
        flowInfo.lastUpdated < newFlowInfo.lastUpdated

        and: "Cleanup: Delete the flow"
        flowHelper.deleteFlow(flow.id)
    }

    @Tags(HARDWARE)
    def "Able to CRUD a vxlan flow with protected path"() {
        given: "Two active Noviflow switches with two available path at least"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            it.src.noviflow && it.dst.noviflow
        }
        assumeTrue("Unable to find required switches in topology", switchPair as boolean)

        def availablePaths = switchPair.paths.findAll { path ->
            pathHelper.getInvolvedSwitches(path).every { it.noviflow }
        }
        assumeTrue("Unable to find required paths beetwen switches", availablePaths.size() >= 2)

        when: "Create a flow with protected path"
        def flow = flowHelper.randomFlow(switchPair)
        flow.allocateProtectedPath = true
        flow.encapsulationType = FlowEncapsulationType.VXLAN
        flowHelper.addFlow(flow)

        then: "Flow is created with protected path"
        def flowPathInfo = northbound.getFlowPath(flow.id)
        flowPathInfo.protectedPath
        northbound.getFlow(flow.id).flowStatusDetails
        def flowInfoFromDb = database.getFlow(flow.id)
        def protectedForwardCookie = flowInfoFromDb.protectedForwardPath.cookie.value
        def protectedReverseCookie = flowInfoFromDb.protectedReversePath.cookie.value

        and: "Rules for main and protected paths are created"
        Wrappers.wait(WAIT_OFFSET) { flowHelper.verifyRulesOnProtectedFlow(flow.id) }

        //TODO(andriidovhan) uncomment when pr2530 is merged
//        and: "Validation of flow must be successful"
//        northbound.validateFlow(flow.id).each { direction ->
//            assert direction.discrepancies.empty
//        }

        when: "Update flow: disable protected path(allocateProtectedPath=false)"
        def protectedFlowPath = northbound.getFlowPath(flow.id).protectedPath.forwardPath
        northbound.updateFlow(flow.id, flow.tap { it.allocateProtectedPath = false })

        then: "Protected path is disabled"
        !northbound.getFlowPath(flow.id).protectedPath
        !northbound.getFlow(flow.id).flowStatusDetails

        and: "Rules for protected path are deleted"
        Wrappers.wait(WAIT_OFFSET) {
            protectedFlowPath.each { sw ->
                def rules = northbound.getSwitchRules(sw.switchId).flowEntries.findAll {
                    !Cookie.isDefaultRule(it.cookie)
                }
                assert rules.every { it != protectedForwardCookie && it != protectedReverseCookie }
            }
        }

          //TODO(andriidovhan) uncomment when pr2530 is merged
//        and: "Validation of flow must be successful"
//        northbound.validateFlow(flow.id).each { direction ->
//            assert direction.discrepancies.empty
//        }

        and: "Cleanup: Delete the flow and reset costs"
        flowHelper.deleteFlow(flow.id)
    }

    @Tags(HARDWARE)
    def "System allows tagged traffic via default flow(0<->0) with 'VXLAN' encapsulation"() {
        // we can't test (0<->20, 20<->0) because iperf is not able to establish a connection
        given: "Noviflow switches"
        def allTraffgenSwitchIds = topology.activeTraffGens*.switchConnected.findAll {
            it.noviflow
        }*.dpId ?: assumeTrue("Should be at least two active traffgens connected to NoviFlow switches for test execution",
                false)
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            allTraffgenSwitchIds.contains(it.src.dpId) && allTraffgenSwitchIds.contains(it.dst.dpId)
        } ?: assumeTrue("Unable to find required switches in topology",false)

        when: "Create a default flow"
        def defaultFlow = flowHelper.randomFlow(switchPair)
        defaultFlow.source.vlanId = 0
        defaultFlow.destination.vlanId = 0
        defaultFlow.encapsulationType = FlowEncapsulationType.VXLAN
        flowHelper.addFlow(defaultFlow)

        def flow = flowHelper.randomFlow(switchPair)
        flow.source.vlanId = 10
        flow.destination.vlanId = 10

        then: "System allows tagged traffic on the default flow"
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(flow, 0)
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        and: "Cleanup: Delete the flow"
        flowHelper.deleteFlow(defaultFlow.id)
    }

    def "System doesn't allow to create a flow with 'VXLAN' encapsulation on a non-noviflow switches"() {
        setup:
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { !it.src.noviflow &&  !it.dst.noviflow }
        assumeTrue("Unable to find required switches in topology", switchPair as boolean)

        when: "Try to create a flow"
        def flow = flowHelper.randomFlow(switchPair)
        flow.encapsulationType = FlowEncapsulationType.VXLAN
        northbound.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        // TODO(andriidovhan)fix errorMessage when the 2587 issue is fixed
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not create flow: Not enough bandwidth found or path not found. " +
                "Failed to find path with requested bandwidth=$flow.maximumBandwidth: Switch $switchPair.src.dpId" +
                " doesn't have links with enough bandwidth"
    }

    @Tags(HARDWARE)
    def "System doesn't allow to create a vxlan flow when transit switch is not Noviflow"() {
        setup:
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().find { swP ->
            swP.src.noviflow && swP.dst.noviflow && swP.paths.find { path ->
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
            northbound.portDown(src.switchId, src.portNo)
        }
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == IslChangeType.FAILED
            }.size() == broughtDownPorts.size() * 2
        }

        when: "Try to create a flow"
        def flow = flowHelper.randomFlow(switchPair)
        flow.encapsulationType = FlowEncapsulationType.VXLAN
        northbound.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        //TODO(andriidovhan)add errorMessage when the 2587 issue is fixed

        and: "Cleanup: Reset costs"
        broughtDownPorts.every { northbound.portUp(it.switchId, it.portNo) }
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts()
    }

    @Tags(HARDWARE)
    def "System doesn't allow to create a vxlan flow when dst switch is not Noviflow"() {
        given: "Noviflow and non-Noviflow switches"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.src.noviflow &&  !it.dst.noviflow }
        assumeTrue("Unable to find required switches in topology", switchPair as boolean)

        when: "Try to create a flow"
        def flow = flowHelper.randomFlow(switchPair)
        flow.encapsulationType = FlowEncapsulationType.VXLAN
        northbound.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        // TODO(andriidovhan) fix errorMessage when the 2587 issue is fixed
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not create flow: Not enough bandwidth found or path not found. Failed to find path with " +
                "requested bandwidth=$flow.maximumBandwidth: " +
                "Switch $switchPair.dst.dpId doesn't have links with enough bandwidth"
    }

    @Unroll
    @Tags(HARDWARE)
    def "System allows to create/update encapsulation type for a one-switch flow\
(#encapsulationCreate.toString() -> #encapsulationUpdate.toString())"() {
        when: "Try to create a one-switch flow"
        def sw = topology.activeTraffGens*.switchConnected.find {
            it.noviflow
        } ?: assumeTrue("Should be at least one active traffgen connected to NoviFlow switch",false)
        def flow = flowHelper.singleSwitchFlow(sw)
        flow.encapsulationType = encapsulationCreate
        northbound.addFlow(flow)

        then: "Flow is created with the #encapsulationCreate.toString() encapsulation type"
        def flowInfo1 = northbound.getFlow(flow.id)
        flowInfo1.encapsulationType == encapsulationCreate.toString().toLowerCase()

        //TODO(andriidovhan) check rules (tunnel-id) when pr2503 is merged

        and: "Flow is valid"
        northbound.validateFlow(flow.id).each { direction -> assert direction.asExpected }

        and: "Flow is pingable"
        def responsePing1 = northbound.pingFlow(flow.id, new PingInput())
        responsePing1.forward.pingSuccess
        responsePing1.reverse.pingSuccess

        when: "Try to update the encapsulation type to #encapsulationUpdate.toString()"
        northbound.updateFlow(flow.id, flow.tap { it.encapsulationType = encapsulationUpdate })

        then: "The encapsulation type is changed to #encapsulationUpdate.toString()"
        def flowInfo2 = northbound.getFlow(flow.id)
        flowInfo2.encapsulationType == encapsulationUpdate.toString().toLowerCase()

        and: "Flow is valid"
        northbound.validateFlow(flow.id).each { direction -> assert direction.asExpected }

        and: "Flow is pingable"
        def responsePing2 = northbound.pingFlow(flow.id, new PingInput())
        responsePing2.forward.pingSuccess
        responsePing2.reverse.pingSuccess

        //TODO(andriidovhan) check rules(tunnel-id) when pr2503 is merged

        and: "Cleanup: Delete the flow"
        flowHelper.deleteFlow(flow.id)

        where:
        encapsulationCreate | encapsulationUpdate
        FlowEncapsulationType.TRANSIT_VLAN | FlowEncapsulationType.VXLAN
        FlowEncapsulationType.VXLAN | FlowEncapsulationType.TRANSIT_VLAN
    }

    def "System doesn't allow to enable a flow with 'VXLAN' encapsulation on a non-noviflow switch"() {
        given: "A flow with 'TRANSIT_VLAN' encapsulation"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { !it.src.noviflow &&  !it.dst.noviflow }
        def flow = flowHelper.randomFlow(switchPair)
        flow.encapsulationType = FlowEncapsulationType.TRANSIT_VLAN
        flowHelper.addFlow(flow)

        when: "Try to change the encapsulation type to VXLAN"
        flowHelper.updateFlow(flow.id, flow.tap { it.encapsulationType = FlowEncapsulationType.VXLAN })

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        //TODO(andriidovhan) fix errorMessage when the 2587 issue is fixed
        exc.responseBodyAsString.to(MessageError).errorMessage == "Could not update flow: \
Not enough bandwidth found or path not found. Failed to find path with requested bandwidth=$flow.maximumBandwidth: \
Switch $switchPair.src.dpId doesn't have links with enough bandwidth"

        and: "Cleanup: Delete the flow"
        flowHelper.deleteFlow(flow.id)
    }
}
