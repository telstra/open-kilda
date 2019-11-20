package org.openkilda.functionaltests.spec.switches

import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative

@Narrative("""Switch properties are created automatically once switch is connected to the controller
and deleted once switch is deleted.
Properties can be read/updated via API '/api/v1/switches/:switch-id/properties'.
Main purpose of that is to understand which feature is supported by a switch(encapsulation type, multi table)""")
class SwitchPropertiesSpec extends HealthCheckSpecification {
    def "Able to manipulate with switch properties"() {
        given: "A switch with switch feature on it"
        def sw = topology.activeSwitches.first()
        def initSwitchProperties = northbound.getSwitchProperties(sw.dpId)
        assert initSwitchProperties.multiTable != null
        assert !initSwitchProperties.supportedTransitEncapsulation.empty

        when: "Update switch properties"
        SwitchPropertiesDto switchProperties = new SwitchPropertiesDto()
        def newMultiTable = !initSwitchProperties.multiTable
        def newTransitEncapsulation = [FlowEncapsulationType.TRANSIT_VLAN.toString().toLowerCase()]
        switchProperties.multiTable = newMultiTable
        switchProperties.supportedTransitEncapsulation = newTransitEncapsulation
        def updateSwPropertiesResponse = northbound.updateSwitchProperties(sw.dpId, switchProperties)
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.sort() == sw.defaultCookies.sort()
        }

        then: "Correct response is returned"
        updateSwPropertiesResponse.multiTable == newMultiTable
        updateSwPropertiesResponse.supportedTransitEncapsulation.sort() == newTransitEncapsulation

        and: "Switch properties is really updated"
        with(northbound.getSwitchProperties(sw.dpId)) {
            it.multiTable == newMultiTable
            it.supportedTransitEncapsulation.sort() == newTransitEncapsulation
        }

        cleanup: "Restore init switch properties on the switch"
        northbound.updateSwitchProperties(sw.dpId, initSwitchProperties)
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.sort() == sw.defaultCookies.sort()
        }
    }

    def "Informative error is returned when trying to get/update switch properties with non-existing id"() {
        when: "Try to get switch properties info for non-existing switch"
        northbound.getSwitchProperties(NON_EXISTENT_SWITCH_ID)

        then: "Human readable error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.NOT_FOUND
        e.responseBodyAsString.to(MessageError).errorMessage ==
                "Switch properties for switch id '$NON_EXISTENT_SWITCH_ID' not found."

        when: "Try to update switch properties info for non-existing switch"
        def switchProperties = new SwitchPropertiesDto()
        switchProperties.multiTable = true
        switchProperties.supportedTransitEncapsulation = [FlowEncapsulationType.VXLAN.toString()]
        northbound.updateSwitchProperties(NON_EXISTENT_SWITCH_ID, switchProperties)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.NOT_FOUND
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Switch properties for switch id '$NON_EXISTENT_SWITCH_ID' not found."
    }

    def "Informative error is returned when trying to update switch properties with incorrect information"() {
        given: "A switch"
        def sw = topology.activeSwitches.first()

        when: "Try to update switch properties info for non-existing switch"
        def switchProperties = new SwitchPropertiesDto()
        switchProperties.supportedTransitEncapsulation = supportedTransitEncapsulation
        northbound.updateSwitchProperties(sw.dpId, switchProperties)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        exc.responseBodyAsString.to(MessageError).errorMessage == errorMessage

        where:
        supportedTransitEncapsulation | errorMessage
        ["test"]                      | "Unable to parse request payload"
        []                            | "Supported transit encapsulations should not be null or empty"
        null                          | "Supported transit encapsulations should not be null or empty"
    }

    def "Unable to create a transit_vlan flow in case switch property doesn't support this type of encapsulation"() {
        given: "A switch pair"
        def switchPair = topologyHelper.getNeighboringSwitchPair()

        and: "Switch properties on the src switch"
        def initSwitchProperties = northbound.getSwitchProperties(switchPair.src.dpId)

        and: "Disable TRANSIT_VLAN encapsulation on the src switch"
        def newSwitchProperties = new SwitchPropertiesDto()
        newSwitchProperties.supportedTransitEncapsulation = []
        northbound.updateSwitchProperties(switchPair.src.dpId, newSwitchProperties)
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert northbound.getSwitchRules(switchPair.src.dpId).flowEntries*.cookie.sort() ==
                    switchPair.src.defaultCookies.sort()
        }

        when: "Try to create a flow with TRANSIT_VLAN encapsulation"
        def flow = flowHelper.randomFlow(switchPair)
        flow.encapsulationType = FlowEncapsulationType.TRANSIT_VLAN
        flowHelper.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.NOT_FOUND
        exc.responseBodyAsString.to(MessageError).errorMessage.contains(
                "Could not create flow: Not enough bandwidth found or path not found.")

        cleanup: "Restore switch property on the switch"
        northbound.updateSwitchProperties(switchPair.src.dpId, initSwitchProperties)
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert northbound.getSwitchRules(switchPair.src.dpId).flowEntries*.cookie.sort() ==
                    switchPair.src.defaultCookies.sort()
        }
    }
}
