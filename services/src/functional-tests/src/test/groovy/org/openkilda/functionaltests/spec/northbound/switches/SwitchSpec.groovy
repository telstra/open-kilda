package org.openkilda.functionaltests.spec.northbound.switches

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException

class SwitchSpec extends BaseSpecification {

    @Autowired
    TopologyDefinition topology
    @Autowired
    NorthboundService northboundService

    def "Delete meter with invalid ID"() {
        given: "A switch"
        def sw = topology.getActiveSwitches()[0]

        when: "Try to delete meter with invalid ID"
        northboundService.deleteMeter(sw.getDpId(), -1)

        then: "Got BadRequest because meter ID is invalid"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
    }
}
