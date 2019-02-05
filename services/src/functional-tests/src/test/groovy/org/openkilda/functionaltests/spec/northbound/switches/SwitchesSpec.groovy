package org.openkilda.functionaltests.spec.northbound.switches

import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.SwitchChangeType

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

class SwitchesSpec extends BaseSpecification {
    def "System can return list of all switches"() {
        expect: "System can return list of all switches"
        !northbound.getAllSwitches().empty
    }

    def "System can return certain switch info by its id"() {
        when: "Request info about certain switch from Northbound"
        def sw = topology.activeSwitches[0]
        def response = northbound.getSwitch(sw.dpId)

        then: "Switch information is returned"
        response.switchId == sw.dpId
        !response.hostname.empty
        !response.address.empty
        !response.description.empty
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
