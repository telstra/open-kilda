package org.openkilda.functionaltests.spec.switches

import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.messaging.error.MessageError
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

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
        def newTransitEncapsulation = (initSwitchProperties.supportedTransitEncapsulation.size() == 1)
                ? [FlowEncapsulationType.TRANSIT_VLAN.toString().toLowerCase(),
                   FlowEncapsulationType.VXLAN.toString().toLowerCase()].sort()
                : [FlowEncapsulationType.VXLAN.toString().toLowerCase()]
        switchProperties.multiTable = newMultiTable
        switchProperties.supportedTransitEncapsulation = newTransitEncapsulation
        def updateSwPropertiesResponse = northbound.updateSwitchProperties(sw.dpId, switchProperties)

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
    }

    def "Informative error is returned when trying to get/update switch properties with non-existing id"() {
        when: "Try to get switch properties info for non-existing switch"
        northbound.getSwitchProperties(NON_EXISTENT_SWITCH_ID)

        then: "Human readable error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.NOT_FOUND
        e.responseBodyAsString.to(MessageError).errorMessage ==
                "Failed to found properties for switch '$NON_EXISTENT_SWITCH_ID'."

        when: "Try to update switch properties info for non-existing switch"
        def switchProperties = new SwitchPropertiesDto()
        switchProperties.multiTable = true
        switchProperties.supportedTransitEncapsulation = [FlowEncapsulationType.VXLAN.toString()]
        northbound.updateSwitchProperties(NON_EXISTENT_SWITCH_ID, switchProperties)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.NOT_FOUND
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Failed to update switch properties for switch '$NON_EXISTENT_SWITCH_ID'"
    }

    def "Informative error is returned when trying to update switch properties with incorrect information"() {
        given: "A switch"
        def sw = topology.activeSwitches.first()

        when: "Try to update switch properties info for non-existing switch"
        def switchProperties = new SwitchPropertiesDto()
        switchProperties.supportedTransitEncapsulation = ["test"]
        northbound.updateSwitchProperties(sw.dpId, switchProperties)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.statusCode == HttpStatus.BAD_REQUEST
        exc.responseBodyAsString.to(MessageError).errorMessage == "Unable to parse request payload"
    }

    def "Unable to create a transit_vlan flow in case switch property doesn't support this type of encapsulation"() {
        given: "A switch pair"
        def switchPair = topologyHelper.getNeighboringSwitchPair()

        and: "Switch properties on the src switch"
        def initSwitchProperties = northbound.getSwitchProperties(switchPair.src.dpId)

        and: "Disable TRANSIT_VLAN encapsulation on the src switch"
        def newSwitchProperties = new SwitchPropertiesDto()
        newSwitchProperties.supportedTransitEncapsulation = [FlowEncapsulationType.VXLAN.toString()]
        northbound.updateSwitchProperties(switchPair.src.dpId, newSwitchProperties)

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
    }
}
