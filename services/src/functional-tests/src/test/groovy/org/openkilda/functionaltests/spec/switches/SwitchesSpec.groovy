package org.openkilda.functionaltests.spec.switches

import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.command.switches.InstallRulesAction
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.messaging.payload.flow.FlowState

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Ignore

class SwitchesSpec extends HealthCheckSpecification {
    @Tidy
    def "System is able to return a list of all switches"() {
        expect: "System can return list of all switches"
        !northbound.getAllSwitches().empty
    }

    @Tidy
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

    @Tidy
    def "Informative error is returned when requesting switch info with non-existing id"() {
        when: "Request info about non-existing switch from Northbound"
        northbound.getSwitch(NON_EXISTENT_SWITCH_ID)

        then: "Not Found error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.NOT_FOUND
        e.responseBodyAsString.to(MessageError).errorMessage == "Switch $NON_EXISTENT_SWITCH_ID not found."
    }

    @Tidy
    @Ignore("https://github.com/telstra/open-kilda/issues/2885")
    def "Systems allows to get a flow that goes through a switch"() {
        given: "Two active not neighboring switches with two diverse paths at least"
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().find {
            it.paths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size() >= 3
        } ?: assumeTrue("No suiting switches found", false)

        and: "A protected flow"
        def protectedFlow = flowHelperV2.randomFlow(switchPair)
        protectedFlow.allocateProtectedPath = true
        flowHelperV2.addFlow(protectedFlow)

        and: "A single switch flow"
        def allowedPorts = topology.getAllowedPortsForSwitch(switchPair.src).findAll {
            it != protectedFlow.source.portNumber
        }
        def r = new Random()
        def singleFlow = flowHelperV2.singleSwitchFlow(switchPair.src)
        singleFlow.source.portNumber = allowedPorts[r.nextInt(allowedPorts.size())]
        singleFlow.destination.portNumber = allowedPorts[r.nextInt(allowedPorts.size())]
        flowHelperV2.addFlow(singleFlow)

        when: "Get all flows going through the involved switches"
        def flowPathInfo = northbound.getFlowPath(protectedFlow.flowId)
        def mainPath = pathHelper.convert(flowPathInfo)
        def protectedPath = pathHelper.convert(flowPathInfo.protectedPath)

        def mainSwitches = pathHelper.getInvolvedSwitches(mainPath)*.dpId
        def protectedSwitches = pathHelper.getInvolvedSwitches(protectedPath)*.dpId
        def involvedSwitchIds = (mainSwitches + protectedSwitches).unique()

        then: "The created flows are in the response list from the src switch"
        def switchFlowsResponseSrcSwitch = northbound.getSwitchFlows(switchPair.src.dpId)
        switchFlowsResponseSrcSwitch*.id.sort() == [protectedFlow.flowId, singleFlow.flowId].sort()

        and: "Only the protectedFlow is in the response list from the involved switch(except the src switch)"
        involvedSwitchIds.findAll { it != switchPair.src.dpId }.each { switchId ->
            def getSwitchFlowsResponse = northbound.getSwitchFlows(switchId)
            assert getSwitchFlowsResponse.size() == 1
            assert getSwitchFlowsResponse[0].id == protectedFlow.flowId
        }

        when: "Get all flows going through the src switch based on the port of the main path"
        def getSwitchFlowsResponse1 = northbound.getSwitchFlows(switchPair.src.dpId, mainPath[0].portNo)

        then: "Only the protected flow is in the response list"
        getSwitchFlowsResponse1.size() == 1
        getSwitchFlowsResponse1[0].id == protectedFlow.flowId

        when: "Get all flows going through the src switch based on the port of the protected path"
        def getSwitchFlowsResponse2 = northbound.getSwitchFlows(switchPair.src.dpId, protectedPath[0].portNo)

        then: "Only the protected flow is in the response list"
        getSwitchFlowsResponse2.size() == 1
        getSwitchFlowsResponse2[0].id == protectedFlow.flowId

        when: "Get all flows going through the src switch based on the dstPort of the single switch flow"
        def getSwitchFlowsResponse3 = northbound.getSwitchFlows(switchPair.src.dpId, singleFlow.destination.portNumber)

        then: "Only the single switch flow is in the response list"
        getSwitchFlowsResponse3.size() == 1
        getSwitchFlowsResponse3[0].id == singleFlow.flowId

        when: "Get all flows going through the dst switch based on the dstPort of the protected flow"
        def getSwitchFlowsResponse4 = northbound.getSwitchFlows(switchPair.dst.dpId,
                protectedFlow.destination.portNumber)

        then: "Only the protected flow is in the response list"
        getSwitchFlowsResponse4.size() == 1
        getSwitchFlowsResponse4[0].id == protectedFlow.flowId

        when: "Create default flow on the same switches"
        def defaultFlow = flowHelperV2.randomFlow(switchPair)
        defaultFlow.source.vlanId = 0
        defaultFlow.destination.vlanId = 0
        flowHelperV2.addFlow(defaultFlow)

        and: "Get all flows going through the src switch"
        def getSwitchFlowsResponse5 = northbound.getSwitchFlows(switchPair.src.dpId)

        then: "The created flows are in the response list"
        getSwitchFlowsResponse5.size() == 3
        getSwitchFlowsResponse5*.id.sort() == [protectedFlow.flowId, singleFlow.flowId, defaultFlow.flowId].sort()

        when: "Bring down all ports on src switch to make flow DOWN"
        def doPortDowns = true //helper var for cleanup
        topology.getBusyPortsForSwitch(switchPair.src).each {
            antiflap.portDown(switchPair.src.dpId, it)
        }
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == IslChangeType.FAILED
            }.size() == topology.getBusyPortsForSwitch(switchPair.src).size() * 2
        }

        and: "Get all flows going through the src switch"
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(protectedFlow.flowId).status == FlowState.DOWN }
        def getSwitchFlowsResponse6 = northbound.getSwitchFlows(switchPair.src.dpId)

        then: "The created flows are in the response list"
        getSwitchFlowsResponse6*.id.sort() == [protectedFlow.flowId, singleFlow.flowId, defaultFlow.flowId].sort()

        cleanup: "Delete the flows"
        doPortDowns && topology.getBusyPortsForSwitch(switchPair.src).each { antiflap.portUp(switchPair.src.dpId, it) }
        [protectedFlow, singleFlow, defaultFlow].each { flowHelperV2.deleteFlow(it.flowId) }
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts()
    }

    @Tidy
    def "Informative error is returned when requesting all flows going through non-existing switch"() {
        when: "Get all flows going through non-existing switch"
        northbound.getSwitchFlows(NON_EXISTENT_SWITCH_ID)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorMessage == "Switch $NON_EXISTENT_SWITCH_ID not found."
    }

    @Tidy
    @Tags(VIRTUAL)
    def "Systems allows to get all flows that goes through a DEACTIVATED switch"() {
        given: "Two active not neighboring switches"
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().first() ?:
                assumeTrue("No suiting switches found", false)

        and: "A simple flow"
        def simpleFlow = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(simpleFlow)

        and: "A single switch flow"
        def singleFlow = flowHelperV2.singleSwitchFlow(switchPair.src)
        flowHelperV2.addFlow(singleFlow)

        when: "Deactivate the src switch"
        def switchToDisconnect = topology.switches.find { it.dpId == switchPair.src.dpId }
        lockKeeper.knockoutSwitch(switchToDisconnect)
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getSwitch(switchToDisconnect.dpId).state == SwitchChangeType.DEACTIVATED
        }

        and: "Get all flows going through the deactivated src switch"
        def switchFlowsResponseSrcSwitch = northbound.getSwitchFlows(switchPair.src.dpId)

        then: "The created flows are in the response list from the deactivated src switch"
        switchFlowsResponseSrcSwitch*.id.sort() == [simpleFlow.flowId, singleFlow.flowId].sort()

        cleanup: "Revive the src switch and delete the flows"
        lockKeeper.reviveSwitch(switchToDisconnect)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getSwitch(switchToDisconnect.dpId).state == SwitchChangeType.ACTIVATED
        }
        [simpleFlow, singleFlow].each { flowHelperV2.deleteFlow(it.flowId) }
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "System returns human readable error when requesting switch ports from non-existing switch"() {
        when: "Request all ports info from non-existing switch"
        northbound.getPorts(NON_EXISTENT_SWITCH_ID)

        then: "Not Found error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.NOT_FOUND
        e.responseBodyAsString.to(MessageError).errorMessage == "Switch $NON_EXISTENT_SWITCH_ID not found"
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "System returns human readable error when requesting switch rules from non-existing switch"() {
        when: "Request all rules from non-existing switch"
        northbound.getSwitchRules(NON_EXISTENT_SWITCH_ID)

        then: "Not Found error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.NOT_FOUND
        e.responseBodyAsString.to(MessageError).errorMessage == "Switch $NON_EXISTENT_SWITCH_ID not found"
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "System returns human readable error when installing switch rules on non-existing switch"() {
        when: "Install switch rules on non-existing switch"
        northbound.installSwitchRules(NON_EXISTENT_SWITCH_ID, InstallRulesAction.INSTALL_DEFAULTS)

        then: "Not Found error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.NOT_FOUND
        e.responseBodyAsString.to(MessageError).errorMessage == "Switch $NON_EXISTENT_SWITCH_ID not found"
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "System returns human readable error when deleting switch rules on non-existing switch"() {
        when: "Delete switch rules on non-existing switch"
        northbound.deleteSwitchRules(NON_EXISTENT_SWITCH_ID, DeleteRulesAction.DROP_ALL_ADD_DEFAULTS)

        then: "Not Found error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.NOT_FOUND
        e.responseBodyAsString.to(MessageError).errorMessage == "Switch $NON_EXISTENT_SWITCH_ID not found"
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "System returns human readable error when setting under maintenance non-existing switch"() {
        when: "set under maintenance non-existing switch"
        northbound.setSwitchMaintenance(NON_EXISTENT_SWITCH_ID, true, true)

        then: "Not Found error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.NOT_FOUND
        e.responseBodyAsString.to(MessageError).errorMessage == "Switch $NON_EXISTENT_SWITCH_ID not found."
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "System returns human readable error when requesting all meters from non-existing switch"() {
        when: "Request all meters from non-existing switch"
        northbound.getAllMeters(NON_EXISTENT_SWITCH_ID)

        then: "Not Found error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.NOT_FOUND
        e.responseBodyAsString.to(MessageError).errorMessage == "Switch $NON_EXISTENT_SWITCH_ID not found"
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "System returns human readable error when deleting meter on non-existing switch"() {
        when: "Delete meter on non-existing switch"
        northbound.deleteMeter(NON_EXISTENT_SWITCH_ID, 33)

        then: "Not Found error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.NOT_FOUND
        e.responseBodyAsString.to(MessageError).errorMessage == "Switch $NON_EXISTENT_SWITCH_ID not found"
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "System returns human readable error when configuring port on non-existing switch"() {
        when: "Configure port on non-existing switch"
        northbound.portUp(NON_EXISTENT_SWITCH_ID, 33)

        then: "Not Found error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.NOT_FOUND
        e.responseBodyAsString.to(MessageError).errorMessage == "Switch $NON_EXISTENT_SWITCH_ID not found"
    }
}
