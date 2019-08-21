package org.openkilda.functionaltests.helpers

import org.openkilda.model.SwitchId
import org.openkilda.testing.service.northbound.NorthboundService

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * This class helps to avoid getting into anti-flap system when manipulating switch ports. It remembers time a certain
 * port was brought down and forces sleep for required 'cooldown' amount of time when one wants to bring that port 'up'.
 */
@Component
class PortAntiflapHelper {
    @Autowired
    NorthboundService northbound

    @Value('${antiflap.min}')
    int antiflapMin
    @Value('${antiflap.cooldown}')
    int antiflapCooldown

    Map<Tuple2<SwitchId, Integer>, Long> history = [:]

    def portUp(SwitchId swId, int portNo) {
        def swPort = new Tuple2(swId, portNo)
        def lastEvent = history.get(swPort)
        if (lastEvent) {
            sleep(lastEvent + antiflapCooldown * 1000 - System.currentTimeMillis())
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
}
