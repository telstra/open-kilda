package org.openkilda.functionaltests.spec.switches

import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.SwitchChangeType

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

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
}
