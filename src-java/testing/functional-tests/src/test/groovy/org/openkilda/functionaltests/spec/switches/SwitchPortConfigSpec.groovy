package org.openkilda.functionaltests.spec.switches

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.model.stats.SwitchStats
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.model.stats.SwitchStatsMetric.STATE
import static org.openkilda.testing.Constants.STATS_LOGGING_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

@Narrative("Verify that Kilda allows to properly control port state on switches (bring ports up or down).")
@Tags([SMOKE_SWITCHES])
class SwitchPortConfigSpec extends HealthCheckSpecification {

    @Autowired @Shared
    SwitchStats switchStats;

    @Tags([TOPOLOGY_DEPENDENT, SMOKE, ISL_RECOVER_ON_FAIL])
    def "Able to bring ISL-busy port down/up on an #isl.srcSwitch.ofVersion switch #isl.srcSwitch.hwSwString"() {
        when: "Bring port down on the switch"
        islHelper.breakIsl(isl)

        then: "Port failure is logged in TSDB"
        Wrappers.wait(STATS_LOGGING_TIMEOUT) {
            switchStats.of(isl.getSrcSwitch().getDpId()).get(STATE, isl.getSrcPort()).hasValue(0)
        }

        when: "Bring port up on the switch"
        islHelper.restoreIsl(isl)

        then: "Port UP event is logged in TSDB"
        Wrappers.wait(STATS_LOGGING_TIMEOUT) {
            switchStats.of(isl.getSrcSwitch().getDpId()).get(STATE, isl.getSrcPort()).hasValue(1)
        }

        where:
        isl << uniqueIsls
    }

    @Tags([HARDWARE, TOPOLOGY_DEPENDENT])
    def "Able to bring ISL-free port down/up on #sw.hwSwString"() {
        // Not checking OTSDB here, since Kilda won't log into OTSDB for isl-free ports, this is expected.

        when: "Bring port down on the switch"
        def port = topology.getAllowedPortsForSwitch(sw).find { "LINK_DOWN" in northbound.getPort(sw.dpId, it).state }
        antiflap.portDown(sw.dpId, port)

        then: "Port is really DOWN"
        Wrappers.wait(WAIT_OFFSET) { assert "PORT_DOWN" in northbound.getPort(sw.dpId, port).config }

        when: "Bring port up on the switch"
        northbound.portUp(sw.dpId, port)

        then: "Port is really UP"
        Wrappers.wait(WAIT_OFFSET) { assert !("PORT_DOWN" in northbound.getPort(sw.dpId, port).config) }

        where:
        // It is impossible to understand whether ISL-free port is UP/DOWN on OF_12 switches.
        // Such switches always have 'config: []'.
        //also, ban WB5164 due to #2636
        sw << getTopology().getActiveSwitches().findAll { it.ofVersion != "OF_12" && !it.wb5164 }
                .unique { it.nbFormat().with { [it.hardware, it.software] } }

    }

    List<Isl> getUniqueIsls() {
        def uniqueSwitches = getTopology().getActiveSwitches()
                .unique { it.nbFormat().with { [it.hardware, it.software] } }*.dpId
        def isls = topology.islsForActiveSwitches.collect { [it, it.reversed] }.flatten()
        return isls.unique { it.srcSwitch.dpId }.findAll { it.srcSwitch.dpId in uniqueSwitches }
    }
}
