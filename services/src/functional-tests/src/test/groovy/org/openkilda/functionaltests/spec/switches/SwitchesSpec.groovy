package org.openkilda.functionaltests.spec.switches

import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.messaging.payload.flow.FlowState

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Ignore

class SwitchesSpec extends HealthCheckSpecification {
    def "System is able to return a list of all switches"() {
        expect: "System can return list of all switches"
        !northbound.getAllSwitches().empty
    }

    @Tags(SMOKE)
    def "System is able to return a certain switch info by its id"() {
        when: "Request info about certain switch from Northbound"
        def sw = topology.activeSwitches[0]
        def response = northbound.getSwitch(sw.dpId)

        then: "Switch information is returned"
        response.switchId == sw.dpId
        !response.hostname.empty
        !response.address.empty
        !response.description.empty
        !response.switchView.ofVersion.empty
        !response.switchView.description.hardware.empty
        !response.switchView.description.software.empty
        !response.switchView.description.serialNumber.empty
        !response.switchView.description.manufacturer.empty
        response.state == SwitchChangeType.ACTIVATED
    }

    def "Informative error is returned when requesting switch info with non-existing id"() {
        when: "Request info about non-existing switch from Northbound"
        northbound.getSwitch(NON_EXISTENT_SWITCH_ID)

        then: "Not Found error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.NOT_FOUND
        e.responseBodyAsString.to(MessageError).errorMessage == "Switch $NON_EXISTENT_SWITCH_ID not found."
    }

    def "Systems allows to get a flow that goes through a switch"() {
        given: "Two active not neighboring switches with two diverse paths at least"
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().find {
            it.paths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size() >= 3
        } ?: assumeTrue("No suiting switches found", false)

        and: "A protected flow"
        def protectedFlow = flowHelper.randomFlow(switchPair)
        protectedFlow.allocateProtectedPath = true
        flowHelper.addFlow(protectedFlow)

        and: "A single switch flow"
        def allowedPorts = topology.getAllowedPortsForSwitch(switchPair.src).findAll {
            it != protectedFlow.source.portId
        }
        def r = new Random()
        def singleFlow = flowHelper.singleSwitchFlow(switchPair.src)
        singleFlow.source.portNumber = allowedPorts[r.nextInt(allowedPorts.size())]
        singleFlow.destination.portNumber = allowedPorts[r.nextInt(allowedPorts.size())]
        flowHelper.addFlow(singleFlow)

        when: "Get all flows going through the involved switches"
        def flowPathInfo = northbound.getFlowPath(protectedFlow.id)
        def mainPath = pathHelper.convert(flowPathInfo)
        def protectedPath = pathHelper.convert(flowPathInfo.protectedPath)

        def mainSwitches = pathHelper.getInvolvedSwitches(mainPath)*.dpId
        def protectedSwitches = pathHelper.getInvolvedSwitches(protectedPath)*.dpId
        def involvedSwitchIds = (mainSwitches + protectedSwitches).unique()

        then: "The created flows are in the response list from the src switch"
        def switchFlowsResponseSrcSwitch = northbound.getSwitchFlows(switchPair.src.dpId)
        switchFlowsResponseSrcSwitch*.id.sort() == [protectedFlow.id, singleFlow.id].sort()

        and: "Only the protectedFlow is in the response list from the involved switch(except the src switch)"
        involvedSwitchIds.findAll { it != switchPair.src.dpId }.each { switchId ->
            def getSwitchFlowsResponse = northbound.getSwitchFlows(switchId)
            assert getSwitchFlowsResponse.size() == 1
            assert getSwitchFlowsResponse[0].id == protectedFlow.id
        }

        when: "Get all flows going through the src switch based on the port of the main path"
        def getSwitchFlowsResponse1 = northbound.getSwitchFlows(switchPair.src.dpId, mainPath[0].portNo)

        then: "Only the protected flow is in the response list"
        getSwitchFlowsResponse1.size() == 1
        getSwitchFlowsResponse1[0].id == protectedFlow.id

        when: "Get all flows going through the src switch based on the port of the protected path"
        def getSwitchFlowsResponse2 = northbound.getSwitchFlows(switchPair.src.dpId, protectedPath[0].portNo)

        then: "Only the protected flow is in the response list"
        getSwitchFlowsResponse2.size() == 1
        getSwitchFlowsResponse2[0].id == protectedFlow.id

        when: "Get all flows going through the src switch based on the dstPort of the single switch flow"
        def getSwitchFlowsResponse3 = northbound.getSwitchFlows(switchPair.src.dpId, singleFlow.destination.portNumber)

        then: "Only the single switch flow is in the response list"
        getSwitchFlowsResponse3.size() == 1
        getSwitchFlowsResponse3[0].id == singleFlow.id

        when: "Get all flows going through the dst switch based on the dstPort of the protected flow"
        def getSwitchFlowsResponse4 = northbound.getSwitchFlows(switchPair.dst.dpId,
                protectedFlow.destination.portNumber)

        then: "Only the protected flow is in the response list"
        getSwitchFlowsResponse4.size() == 1
        getSwitchFlowsResponse4[0].id == protectedFlow.id

        when: "All alternative paths are unavailable (bring ports down on the srcSwitch)"
        List<PathNode> broughtDownPorts = []
        switchPair.paths.findAll { it != pathHelper.convert(northbound.getFlowPath(protectedFlow.id)) }.unique {
            it.first()
        }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            antiflap.portDown(src.switchId, src.portNo)
        }
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == IslChangeType.FAILED
            }.size() == broughtDownPorts.size() * 2
        }

        and: "Get all flows going through the src switch"
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(protectedFlow.id).status == FlowState.DOWN }
        def getSwitchFlowsResponse5 = northbound.getSwitchFlows(switchPair.src.dpId)

        then: "The created flows are in the response list"
        getSwitchFlowsResponse5.size() == 2
        getSwitchFlowsResponse5*.id.sort() == [protectedFlow.id, singleFlow.id].sort()

        and: "Cleanup: Delete the flows"
        broughtDownPorts.every { antiflap.portUp(it.switchId, it.portNo) }
        [protectedFlow, singleFlow].each { flowHelper.deleteFlow(it.id) }
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts()
    }

    def "Informative error is returned when requesting all flows going through non-existing switch"() {
        when: "Get all flows going through non-existing switch"
        northbound.getSwitchFlows(NON_EXISTENT_SWITCH_ID)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorMessage == "Switch $NON_EXISTENT_SWITCH_ID not found."
    }

    @Tags(VIRTUAL)
    def "Systems allows to get all flows that goes through a DEACTIVATED switch"() {
        given: "Two active not neighboring switches"
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().first() ?:
                assumeTrue("No suiting switches found", false)

        and: "A simple flow"
        def simpleFlow = flowHelper.randomFlow(switchPair)
        flowHelper.addFlow(simpleFlow)

        and: "A single switch flow"
        def singleFlow = flowHelper.singleSwitchFlow(switchPair.src)
        flowHelper.addFlow(singleFlow)

        when: "Deactivate the src switch"
        def switchToDisconnect = topology.switches.find { it.dpId == switchPair.src.dpId }
        lockKeeper.knockoutSwitch(switchToDisconnect)
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getSwitch(switchToDisconnect.dpId).state == SwitchChangeType.DEACTIVATED
        }

        and: "Get all flows going through the deactivated src switch"
        def switchFlowsResponseSrcSwitch = northbound.getSwitchFlows(switchPair.src.dpId)

        then: "The created flows are in the response list from the deactivated src switch"
        switchFlowsResponseSrcSwitch*.id.sort() == [simpleFlow.id, singleFlow.id].sort()

        and: "Cleanup: Revive the src switch and delete the flows"
        lockKeeper.reviveSwitch(switchToDisconnect)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getSwitch(switchToDisconnect.dpId).state == SwitchChangeType.ACTIVATED
        }
        [simpleFlow, singleFlow].each { flowHelper.deleteFlow(it.id) }
    }
}
