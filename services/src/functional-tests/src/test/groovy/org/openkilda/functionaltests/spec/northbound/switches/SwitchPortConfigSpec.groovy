package org.openkilda.functionaltests.spec.northbound.switches

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.otsdb.OtsdbQueryService
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
    @Autowired
    OtsdbQueryService otsdb

    def otsdbPortUp = 1
    def otsdbPortDown = 0

    @Value('${discovery.interval}')
    int discoveryInterval

    def "Able to bring switch port down/up (ISL-busy port)"() {
        given: "An ISL between active switches"
        def isl = topology.islsForActiveSwitches.first()

        when: "Bring port down on switch"
        def portDownTime = new Date()
        northboundService.portDown(isl.srcSwitch.dpId, isl.srcPort)

        then: "ISL between switches becomes 'FAILED'"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED &&
                    islUtils.getIslInfo(islUtils.reverseIsl(isl)).get().state == IslChangeType.FAILED
        }

        and: "Port failure is logged in OTSDB"
        def statsData = [:]
        Wrappers.wait(WAIT_OFFSET) {
            statsData = otsdb.query(portDownTime, "pen.switch.state",
                    [switchid: isl.srcSwitch.dpId.toOtsdFormat(), port: isl.srcPort]).dps
            statsData.size() == 1
        }
        statsData.values().first() == otsdbPortDown

        when: "Bring port up on switch"
        def portUpTime = new Date()
        northboundService.portUp(isl.srcSwitch.dpId, isl.srcPort)

        then: "ISL between switches becomes 'DISCOVERED'"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED &&
                    islUtils.getIslInfo(islUtils.reverseIsl(isl)).get().state == IslChangeType.DISCOVERED
        }

        and: "Port Up event is logged in OTSDB"
        Wrappers.wait(WAIT_OFFSET) {
            statsData = otsdb.query(portUpTime, "pen.switch.state",
                    [switchid: isl.srcSwitch.dpId.toOtsdFormat(), port: isl.srcPort]).dps
            statsData.size() == 1
        }
        statsData.values().first() == otsdbPortUp
    }

    //Not checking OTSDB here, since Kilda won't log into OTSDB for isl-free ports, this is expected.
    def "Able to bring switch port down/up (ISL-free port)"() {
        requireProfiles("hardware")

        given: "An active switch and ISL-free port"
        def sw = topology.getActiveSwitches().first()
        def port = topology.getAllowedPortsForSwitch(sw).first()
        assert "LINK_DOWN" in northboundService.getPort(sw.dpId, port).state

        when: "Bring port down on switch"
        northboundService.portDown(sw.dpId, port)

        then: "Port is really 'down'"
        "PORT_DOWN" in northboundService.getPort(sw.dpId, port).config

        when: "Bring port up on switch"
        northboundService.portUp(sw.dpId, port)

        then: "Port is really 'up'"
        !("PORT_DOWN" in northboundService.getPort(sw.dpId, port).config)
    }
}
