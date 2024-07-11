package org.openkilda.functionaltests.helpers

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.helpers.model.PortHistoryEvent
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.PORT_UP
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESET_ISLS_COST
import static org.openkilda.functionaltests.model.cleanup.CleanupAfter.TEST
import static org.openkilda.testing.Constants.WAIT_OFFSET

/**
 * This class helps to avoid getting into anti-flap system when manipulating switch ports. It remembers time a certain
 * port was brought down and forces sleep for required 'cooldown' amount of time when one wants to bring that port 'up'.
 */
@Component
@Scope(SCOPE_PROTOTYPE)
class PortAntiflapHelper {
    @Autowired @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundV2
    @Autowired
    CleanupManager cleanupManager
    @Autowired
    Database database
    @Autowired
    TopologyDefinition topology

    @Value('${antiflap.min}')
    int antiflapMin
    @Value('${antiflap.cooldown}')
    int antiflapCooldown

    Map<Tuple2<SwitchId, Integer>, Long> history = [:]

    def portUp(SwitchId swId, int portNo) {
        def swPort = new Tuple2(swId, portNo)
        def lastEvent = history.get(swPort)
        if (lastEvent) {
            Wrappers.silent { //Don't fail hard on this check. In rare cases we may miss the history entry
                waitPortIsStable(swId, portNo, lastEvent)
            }
            history.remove(swPort)
        }
        northbound.portUp(swId, portNo)
    }

    def safePortUp(SwitchId swId, int portNo) {
        if (northbound.getPort(swId, portNo).getState().first() != "LIVE") {
            portUp(swId, portNo)
        }
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getActiveLinks().findAll {
                it.getSource().getSwitchId() == swId && it.getSource().getPortNo() == portNo ||
                        it.getDestination().getSwitchId() == swId && it.getDestination().getPortNo() == portNo
            }.size() == 2
        }
    }

    def portDown(SwitchId swId, int portNo, cleanupAfter = TEST) {
        cleanupManager.addAction(PORT_UP, {safePortUp(swId, portNo)}, cleanupAfter)
        cleanupManager.addAction(RESET_ISLS_COST, {database.resetCosts(topology.isls)})
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
            def history = northboundV2.getPortHistory(swId, portNo, since, null)

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
