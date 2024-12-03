package org.openkilda.functionaltests.spec.switches

import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.helpers.model.FlowActionType.REROUTE_FAILED
import static org.openkilda.messaging.command.switches.DeleteRulesAction.DROP_ALL_ADD_DEFAULTS
import static org.openkilda.messaging.command.switches.InstallRulesAction.INSTALL_DEFAULTS
import static org.openkilda.messaging.payload.flow.FlowState.DOWN
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.SwitchNotFoundExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowActionType
import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.command.switches.InstallRulesAction
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.switches.SwitchPatchDto

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Shared

class SwitchesSpec extends HealthCheckSpecification {
    @Shared
    SwitchNotFoundExpectedError switchNotFoundExpectedError = new SwitchNotFoundExpectedError(
            "Switch $NON_EXISTENT_SWITCH_ID not found", ~/Switch $NON_EXISTENT_SWITCH_ID not found/)

    @Autowired
    @Shared
    FlowFactory flowFactory

    def "System is able to return a list of all switches"() {
        expect: "System can return list of all switches"
        !northbound.getAllSwitches().empty
    }

    @Tags([SMOKE, SMOKE_SWITCHES])
    def "System is able to return a certain switch info by its id"() {
        when: "Request info about certain switch from Northbound"
        def sw = switches.all().random()
        def response = sw.getDetails()

        then: "Switch information is returned"
        response.switchId == sw.switchId
        !response.hostname.empty
        !response.address.empty
        !response.description.empty
        !response.ofVersion.empty
        !response.hardware.empty
        !response.software.empty
        !response.serialNumber.empty
        !response.manufacturer.empty
        response.state == SwitchChangeType.ACTIVATED
    }

    def "Informative error is returned when requesting switch info with non-existing id"() {
        when: "Request info about non-existing switch from Northbound"
        northbound.getSwitch(NON_EXISTENT_SWITCH_ID)

        then: "Not Found error is returned"
        def error = thrown(HttpClientErrorException)
        new SwitchNotFoundExpectedError(NON_EXISTENT_SWITCH_ID).matches(error)
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Systems allows to get a flow that goes through a switch"() {
        given: "Two active not neighboring switches with two diverse paths at least"
        def switchPair = switchPairs.all().nonNeighbouring()
                .withAtLeastNNonOverlappingPaths(2).random()

        and: "A protected flow"
        def protectedFlow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(true)
                .build().create()

        and: "A single switch flow"
        def srcSwitch = switches.all().findSpecific(switchPair.src.dpId)
        def allowedPorts = srcSwitch.getPorts().findAll {
            it != protectedFlow.source.portNumber
        }
        def r = new Random()
        def singleFlow = flowFactory.getBuilder(switchPair.src, switchPair.src)
                .withSourcePort(allowedPorts[r.nextInt(allowedPorts.size())])
                .withDestinationPort(allowedPorts[r.nextInt(allowedPorts.size())])
                .build().create()

        when: "Get all flows going through the involved switches"
        def flowPathInfo = protectedFlow.retrieveAllEntityPaths()
        def mainPath = flowPathInfo.getPathNodes(Direction.FORWARD, false)
        def protectedPath = flowPathInfo.getPathNodes(Direction.FORWARD, true)
        def involvedSwitchIds = flowPathInfo.getInvolvedSwitches()

        then: "The created flows are in the response list from the src switch"
        def switchFlowsResponseSrcSwitch = srcSwitch.getFlows()
        switchFlowsResponseSrcSwitch*.id.sort() == [protectedFlow.flowId, singleFlow.flowId].sort()

        and: "Only the protectedFlow is in the response list from the involved switch(except the src switch)"
        involvedSwitchIds.findAll { it != switchPair.src.dpId }.each { switchId ->
            def getSwitchFlowsResponse = switches.all().findSpecific(switchId).getFlows()
            assert getSwitchFlowsResponse.size() == 1
            assert getSwitchFlowsResponse[0].id == protectedFlow.flowId
        }

        when: "Get all flows going through the src switch based on the port of the main path"
        def getSwitchFlowsResponse1 = srcSwitch.getFlows(mainPath[0].portNo)

        then: "Only the protected flow is in the response list"
        getSwitchFlowsResponse1.size() == 1
        getSwitchFlowsResponse1[0].id == protectedFlow.flowId

        when: "Get all flows going through the src switch based on the port of the protected path"
        def getSwitchFlowsResponse2 = srcSwitch.getFlows(protectedPath[0].portNo)

        then: "Only the protected flow is in the response list"
        getSwitchFlowsResponse2.size() == 1
        getSwitchFlowsResponse2[0].id == protectedFlow.flowId

        when: "Get all flows going through the src switch based on the dstPort of the single switch flow"
        def getSwitchFlowsResponse3 = srcSwitch.getFlows(singleFlow.destination.portNumber)

        then: "Only the single switch flow is in the response list"
        getSwitchFlowsResponse3.size() == 1
        getSwitchFlowsResponse3[0].id == singleFlow.flowId

        when: "Get all flows going through the dst switch based on the dstPort of the protected flow"
        def dstSwitch = switches.all().findSpecific(switchPair.dst.dpId)
        def getSwitchFlowsResponse4 = dstSwitch.getFlows(protectedFlow.destination.portNumber)

        then: "Only the protected flow is in the response list"
        getSwitchFlowsResponse4.size() == 1
        getSwitchFlowsResponse4[0].id == protectedFlow.flowId

        when: "Create default flow on the same switches"
        def defaultFlow = flowFactory.getBuilder(switchPair)
                .withSourceVlan(0)
                .withDestinationVlan(0)
                .build().create()

        and: "Get all flows going through the src switch"
        def getSwitchFlowsResponse5 = srcSwitch.getFlows()

        then: "The created flows are in the response list"
        getSwitchFlowsResponse5.size() == 3
        getSwitchFlowsResponse5*.id.sort() == [protectedFlow.flowId, singleFlow.flowId, defaultFlow.flowId].sort()

        when: "Bring down all ports on src switch to make flow DOWN"
        def switchIsls = topology.getRelatedIsls(switchPair.src)
        islHelper.breakIsls(switchIsls)

        and: "Get all flows going through the src switch"
        Wrappers.wait(WAIT_OFFSET * 2) {
            assert protectedFlow.retrieveFlowStatus().status == DOWN
            assert protectedFlow.retrieveFlowHistory().getEntriesByType(REROUTE_FAILED).last()
                    .payload.find { it.action == REROUTE_FAILED.payloadLastAction }
            assert defaultFlow.retrieveFlowStatus().status == DOWN
            assert defaultFlow.retrieveFlowHistory().getEntriesByType(REROUTE_FAILED).last()
                    .payload.find { it.action == REROUTE_FAILED.payloadLastAction }
        }
        def getSwitchFlowsResponse6 = srcSwitch.getFlows()

        then: "The created flows are in the response list"
        getSwitchFlowsResponse6*.id.sort() == [protectedFlow.flowId, singleFlow.flowId, defaultFlow.flowId].sort()
    }

    def "Informative error is returned when requesting all flows going through non-existing switch"() {
        when: "Get all flows going through non-existing switch"
        northbound.getSwitchFlows(NON_EXISTENT_SWITCH_ID)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new SwitchNotFoundExpectedError(NON_EXISTENT_SWITCH_ID).matches(exc)    }

    @Tags(SWITCH_RECOVER_ON_FAIL)
    def "Systems allows to get all flows that goes through a DEACTIVATED switch"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all().nonNeighbouring().random()

        and: "A simple flow"
        def simpleFlow = flowFactory.getRandom(switchPair)

        and: "A single switch flow"
        def singleFlow = flowFactory.getRandom(switchPair.src, switchPair.src)

        when: "Deactivate the src switch"
        def switchToDisconnect = switches.all().findSpecific(switchPair.src.dpId)
        switchToDisconnect.knockout(RW)

        and: "Get all flows going through the deactivated src switch"
        def switchFlowsResponseSrcSwitch = switchToDisconnect.getFlows()

        then: "The created flows are in the response list from the deactivated src switch"
        switchFlowsResponseSrcSwitch*.id.sort() == [simpleFlow.flowId, singleFlow.flowId].sort()
    }

    @Tags(LOW_PRIORITY)
    def "System returns human readable error when requesting switch ports from non-existing switch"() {
        when: "Request all ports info from non-existing switch"
        northbound.getPorts(NON_EXISTENT_SWITCH_ID)

        then: "Not Found error is returned"
        def e = thrown(HttpClientErrorException)
        switchNotFoundExpectedError.matches(e)    }

    @Tags(LOW_PRIORITY)
    def "System returns human readable error when requesting switch rules from non-existing switch"() {
        when: "Request all rules from non-existing switch"
        northbound.getSwitchRules(NON_EXISTENT_SWITCH_ID)

        then: "Not Found error is returned"
        def e = thrown(HttpClientErrorException)
        switchNotFoundExpectedError.matches(e)    }

    @Tags(LOW_PRIORITY)
    def "System returns human readable error when installing switch rules on non-existing switch"() {
        when: "Install switch rules on non-existing switch"
        northbound.installSwitchRules(NON_EXISTENT_SWITCH_ID, INSTALL_DEFAULTS)

        then: "Not Found error is returned"
        def e = thrown(HttpClientErrorException)
        new SwitchNotFoundExpectedError("Switch $NON_EXISTENT_SWITCH_ID not found",
                ~/Error when installing switch rules/).matches(e)    }

    @Tags(LOW_PRIORITY)
    def "System returns human readable error when deleting switch rules on non-existing switch"() {
        when: "Delete switch rules on non-existing switch"
        northbound.deleteSwitchRules(NON_EXISTENT_SWITCH_ID, DROP_ALL_ADD_DEFAULTS)

        then: "Not Found error is returned"
        def e = thrown(HttpClientErrorException)
        new SwitchNotFoundExpectedError("Switch $NON_EXISTENT_SWITCH_ID not found",
                ~/Error when deleting switch rules/).matches(e)    }

    @Tags(LOW_PRIORITY)
    def "System returns human readable error when setting under maintenance non-existing switch"() {
        when: "set under maintenance non-existing switch"
        northbound.setSwitchMaintenance(NON_EXISTENT_SWITCH_ID, true, true)

        then: "Not Found error is returned"
        def e = thrown(HttpClientErrorException)
        new SwitchNotFoundExpectedError(NON_EXISTENT_SWITCH_ID).matches(e)    }

    @Tags(LOW_PRIORITY)
    def "System returns human readable error when requesting all meters from non-existing switch"() {
        when: "Request all meters from non-existing switch"
        northbound.getAllMeters(NON_EXISTENT_SWITCH_ID)

        then: "Not Found error is returned"
        def e = thrown(HttpClientErrorException)
        switchNotFoundExpectedError.matches(e)    }

    @Tags(LOW_PRIORITY)
    def "System returns human readable error when deleting meter on non-existing switch"() {
        when: "Delete meter on non-existing switch"
        northbound.deleteMeter(NON_EXISTENT_SWITCH_ID, 33)

        then: "Not Found error is returned"
        def e = thrown(HttpClientErrorException)
        switchNotFoundExpectedError.matches(e)    }

    @Tags(LOW_PRIORITY)
    def "System returns human readable error when configuring port on non-existing switch"() {
        when: "Configure port on non-existing switch"
        northbound.portUp(NON_EXISTENT_SWITCH_ID, 33)

        then: "Not Found error is returned"
        def e = thrown(HttpClientErrorException)
        switchNotFoundExpectedError.matches(e)    }

    @Tags(LOW_PRIORITY)
    def "System returns human readable error when #data.descr non-existing switch"() {
        when: "Make action from description on non-existing switch"
        data.operation()

        then: "Not Found error is returned"
        def e = thrown(HttpClientErrorException)
        new SwitchNotFoundExpectedError("Switch '$NON_EXISTENT_SWITCH_ID' not found",
        ~/Error in switch validation/)

        where:
        data << [
                [descr    : "synchronizing rules on",
                 operation: { getNorthbound().synchronizeSwitchRules(NON_EXISTENT_SWITCH_ID) }],
                [descr    : "synchronizing",
                 operation: { switchHelper.synchronize(NON_EXISTENT_SWITCH_ID) }],
                [descr    : "validating rules on",
                 operation: { getNorthbound().validateSwitchRules(NON_EXISTENT_SWITCH_ID) }],
                [descr    : "validating",
                 operation: { switchHelper.validate(NON_EXISTENT_SWITCH_ID) }]
        ]
    }

    @Tags(LOW_PRIORITY)
    def "Able to partially update switch a 'location.#data.field' field"() {
        given: "A switch"
        def sw = switches.all().random()

        when: "Request a switch partial update for a #data.field field"
        SwitchPatchDto updateRequest = [location: [(data.field): data.newValue]] as SwitchPatchDto
        def response = sw.partialUpdate(updateRequest)

        then: "Update response reflects the changes"
        response.location."$data.field" == data.newValue

        and: "Changes actually took place"
        sw.getDetails().location."$data.field" == data.newValue

        where:
        data << [
                [
                        field   : "latitude",
                        newValue: 654
                ],
                [
                        field   : "longitude",
                        newValue: 456
                ],
                [
                        field   : "street",
                        newValue: "testStreet"
                ],
                [
                        field   : "city",
                        newValue: "testCity"
                ],
                [
                        field   : "country",
                        newValue: "testCountry"
                ]
        ]
    }

    def "Able to partially update switch a 'pop' field"() {
        given: "A switch"
        def sw = switches.all().random()

        when: "Request a switch partial update for a 'pop' field"
        def newPopValue = "test_POP"
        def response = sw.partialUpdate(new SwitchPatchDto().tap { it.pop = newPopValue })

        then: "Update response reflects the changes"
        response.pop == newPopValue

        and: "Changes actually took place"
        sw.getDetails().pop == newPopValue
    }
}
