package org.openkilda.functionaltests.spec.northbound.switches

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.otsdb.OtsdbQueryService
import org.openkilda.testing.tools.IslUtils

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Narrative
import spock.lang.Unroll

@Narrative("Verify that Kilda allows to properly control port state on switches (bring ports up or down).")
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

    @Unroll
    def "Able to bring ISL-busy port down/up on switch(#isl.srcSwitch.dpId)"() {
        when: "Bring port down on the switch"
        def portDownTime = new Date()
        northboundService.portDown(isl.srcSwitch.dpId, isl.srcPort)

        then: "ISL between switches becomes 'FAILED'"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(islUtils.reverseIsl(isl)).get().state == IslChangeType.FAILED
        }

        and: "Port failure is logged in OpenTSDB"
        def statsData = [:]
        Wrappers.wait(WAIT_OFFSET) {
            statsData = otsdb.query(portDownTime, "pen.switch.state",
                    [switchid: isl.srcSwitch.dpId.toOtsdFormat(), port: isl.srcPort]).dps
            assert statsData.size() == 1
        }
        statsData.values().first() == otsdbPortDown

        when: "Bring port up on the switch"
        def portUpTime = new Date()
        northboundService.portUp(isl.srcSwitch.dpId, isl.srcPort)

        then: "ISL between switches becomes 'DISCOVERED'"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(islUtils.reverseIsl(isl)).get().state == IslChangeType.DISCOVERED
        }

        and: "Port UP event is logged in OpenTSDB"
        Wrappers.wait(WAIT_OFFSET) {
            statsData = otsdb.query(portUpTime, "pen.switch.state",
                    [switchid: isl.srcSwitch.dpId.toOtsdFormat(), port: isl.srcPort]).dps
            assert statsData.size() == 1
        }
        statsData.values().first() == otsdbPortUp

        where:
        isl << uniqueIsls
    }

    @Unroll
    def "Able to bring ISL-free port down/up on switch(#sw.dpId)"() {
        requireProfiles("hardware")
        // Not checking OTSDB here, since Kilda won't log into OTSDB for isl-free ports, this is expected.

        when: "Bring port down on the switch"
        def port = topology.getAllowedPortsForSwitch(sw).find {
            "LINK_DOWN" in northboundService.getPort(sw.dpId, it).state
        }
        northboundService.portDown(sw.dpId, port)

        then: "Port is really DOWN"
        "PORT_DOWN" in northboundService.getPort(sw.dpId, port).config

        when: "Bring port up on the switch"
        northboundService.portUp(sw.dpId, port)

        then: "Port is really UP"
        !("PORT_DOWN" in northboundService.getPort(sw.dpId, port).config)

        where:
        // It is impossible to understand whether ISL-free port is UP/DOWN on OF_12 switches.
        // Such switches always have 'config: []'.
        sw << uniqueSwitches.findAll { it.ofVersion != "OF_12" }
    }

    List<Isl> getUniqueIsls() {
        def uniqueSwitches = getUniqueSwitches()*.dpId
        def isls = topology.islsForActiveSwitches.collect { [it, islUtils.reverseIsl(it)] }.flatten()
        return isls.unique { it.srcSwitch.dpId }.findAll { it.srcSwitch.dpId in uniqueSwitches }
    }

    List<Switch> getUniqueSwitches() {
        def nbSwitches = northbound.getAllSwitches()
        return topology.getActiveSwitches().unique { sw ->
            [nbSwitches.find { it.switchId == sw.dpId }.description, sw.ofVersion].sort()
        }
    }
}
