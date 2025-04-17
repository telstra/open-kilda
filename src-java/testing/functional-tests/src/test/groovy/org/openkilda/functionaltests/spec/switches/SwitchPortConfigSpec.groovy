package org.openkilda.functionaltests.spec.switches

import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.model.PortStatus.LINK_DOWN
import static org.openkilda.functionaltests.helpers.model.PortStatus.PORT_DOWN
import static org.openkilda.functionaltests.model.stats.SwitchStatsMetric.STATE
import static org.openkilda.testing.Constants.STATS_LOGGING_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.model.stats.SwitchStats

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("Verify that Kilda allows to properly control port state on switches (bring ports up or down).")
@Tags([SMOKE_SWITCHES])
class SwitchPortConfigSpec extends HealthCheckSpecification {

    @Autowired @Shared
    SwitchStats switchStats

    @Tags([TOPOLOGY_DEPENDENT, SMOKE, ISL_RECOVER_ON_FAIL])
    def "Able to bring ISL-busy port down/up on an #sw.getOfVersion() switch #sw.hwSwString()"() {
        when: "Bring port down on the switch"
        def isl = isls.all().relatedTo(sw).first()
        isl.breakIt()

        then: "Port failure is logged in TSDB"
        Wrappers.wait(STATS_LOGGING_TIMEOUT) {
            switchStats.of(isl.srcSwId).get(STATE, isl.srcPort).hasValue(0)
        }

        when: "Bring port up on the switch"
        isl.restore()

        then: "Port UP event is logged in TSDB"
        Wrappers.wait(STATS_LOGGING_TIMEOUT) {
            switchStats.of(isl.srcSwId).get(STATE, isl.srcPort).hasValue(1)
        }

        where:
        sw << switches.all().uniqueByHw()
    }

    @Tags([HARDWARE, TOPOLOGY_DEPENDENT])
    def "Able to bring ISL-free port down/up on #sw.hwSwString()"() {
        // Not checking OTSDB here, since Kilda won't log into OTSDB for isl-free ports, this is expected.
        when: "Bring port down on the switch"
        def port = sw.getPortInStatus(LINK_DOWN)

        port.down()

        then: "Port is really DOWN"
        Wrappers.wait(WAIT_OFFSET) { assert PORT_DOWN.toString() in port.retrieveDetails().config }

        when: "Bring port up on the switch"
        port.up()

        then: "Port is really UP"
        Wrappers.wait(WAIT_OFFSET) { assert !(PORT_DOWN.toString() in port.retrieveDetails().config) }

        where:
        // It is impossible to understand whether ISL-free port is UP/DOWN on OF_12 switches.
        // Such switches always have 'config: []'.
        //also, ban WB5164 due to #2636
        sw << switches.all().uniqueByHw().findAll { !it.isOf12Version() && !it.wb5164 }
    }
}
