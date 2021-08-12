package org.openkilda.functionaltests.helpers

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.helpers.model.PortHistoryEvent
import org.openkilda.model.SwitchId
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * This class helps to avoid getting into anti-flap system when manipulating switch ports. It remembers time a certain
 * port was brought down and forces sleep for required 'cooldown' amount of time when one wants to bring that port 'up'.
 */
@Component
class PortAntiflapHelper {
    @Autowired @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundV2

    @Value('${antiflap.min}')
    int antiflapMin
    @Value('${antiflap.cooldown}')
    int antiflapCooldown

    Map<Tuple2<SwitchId, Integer>, Long> history = [:]

    def portUp(SwitchId swId, int portNo) {
        def swPort = new Tuple2(swId, portNo)
        def lastEvent = history.get(swPort)
        if (lastEvent) {
            waitPortIsStable(swId, portNo, lastEvent)
            history.remove(swPort)
        }
        northbound.portUp(swId, portNo)
    }

    def portDown(SwitchId swId, int portNo) {
        def response = northbound.portDown(swId, portNo)
        sleep(antiflapMin * 1000)
        history.put(new Tuple2(swId, portNo), System.currentTimeMillis())
        response
    }

    /**
     * Wait till the current port is in a stable state (deactivated antiflap) by analyzing its history.
     */
    void waitPortIsStable(SwitchId swId, int portNo, Long since = 0) {
        // '* 2' it takes more time on a hardware env for link via 'a-switch'
        Wrappers.wait(antiflapCooldown + WAIT_OFFSET * 2) {
            def history = northboundV2.getPortHistory(swId, portNo, since, Long.MAX_VALUE)

            if (!history.empty) {
                def antiflapEvents = history.collect { PortHistoryEvent.valueOf(it.event) }.findAll {
                    it in [PortHistoryEvent.ANTI_FLAP_ACTIVATED, PortHistoryEvent.ANTI_FLAP_DEACTIVATED]
                }

                if (!antiflapEvents.empty) {
                    assert antiflapEvents.last() == PortHistoryEvent.ANTI_FLAP_DEACTIVATED
                } else {
                    false
                }
            } else {
                false
            }
        }
    }
}
