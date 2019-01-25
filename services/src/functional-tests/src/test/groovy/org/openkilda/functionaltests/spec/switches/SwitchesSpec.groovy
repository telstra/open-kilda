package org.openkilda.functionaltests.spec.switches

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.model.SwitchId

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

class SwitchesSpec extends BaseSpecification {
    def "System is able to return a list of all switches"() {
        expect: "System can return list of all switches"
        !northbound.getAllSwitches().empty
    }

    def "System is able to return a certain switch info by its id"() {
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
        def nonExistingId = new SwitchId("de:ad:be:ef:de:ad:be:ef")
        northbound.getSwitch(nonExistingId)

        then: "Not Found error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.NOT_FOUND
        e.responseBodyAsString.to(MessageError).errorMessage == "Switch $nonExistingId not found."
    }
}
