package org.openkilda.functionaltests.spec.northbound.switches

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.tools.IslUtils

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

class SwitchPortConfigSpec extends BaseSpecification {

    @Autowired
    TopologyDefinition topology
    @Autowired
    NorthboundService northboundService
    @Autowired
    IslUtils islUtils

    @Value('${discovery.interval}')
    int discoveryInterval

    def "Bring switch port down/up (ISL-busy port)"() {
        given: "An ISL between active switches"
        def isl = topology.islsForActiveSwitches.first()

        when: "Bring port down on switch"
        northboundService.portDown(isl.srcSwitch.dpId, isl.srcPort)

        then: "ISL between switches becomes 'FAILED'"
        Wrappers.wait(discoveryInterval + 3) {
            islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED &&
                    islUtils.getIslInfo(islUtils.reverseIsl(isl)).get().state == IslChangeType.FAILED
        }

        when: "Bring port up on switch"
        northboundService.portUp(isl.srcSwitch.dpId, isl.srcPort)

        then: "ISL between switches becomes 'DISCOVERED'"
        Wrappers.wait(discoveryInterval + 3) {
            islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED &&
                    islUtils.getIslInfo(islUtils.reverseIsl(isl)).get().state == IslChangeType.DISCOVERED
        }
    }
}
