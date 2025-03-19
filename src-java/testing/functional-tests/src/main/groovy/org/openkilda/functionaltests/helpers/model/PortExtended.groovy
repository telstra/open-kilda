package org.openkilda.functionaltests.helpers.model

import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.PORT_UP
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_ISL
import static org.openkilda.functionaltests.model.cleanup.CleanupAfter.TEST
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.helpers.KildaProperties
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.thread.PortBlinker
import org.openkilda.functionaltests.model.cleanup.CleanupAfter
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.info.switches.PortDescription
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.switches.PortPropertiesDto
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.util.logging.Slf4j

@Slf4j
@EqualsAndHashCode(excludes = 'northbound, northboundV2, cleanupManager')
@ToString(includeNames = true, excludes = 'northbound, northboundV2, cleanupManager')
class PortExtended {

    Switch sw
    Integer port

    @JsonIgnore
    NorthboundService northbound
    @JsonIgnore
    NorthboundServiceV2 northboundV2
    @JsonIgnore
    CleanupManager cleanupManager

    Map<Tuple2<SwitchId, Integer>, Long> history = [:]

    PortExtended(Switch sw,
                 Integer portNumber,
                 NorthboundService northbound,
                 NorthboundServiceV2 northboundV2,
                 CleanupManager cleanupManager) {
        this.sw = sw
        this.port = portNumber
        this.northbound = northbound
        this.northboundV2 = northboundV2
        this.cleanupManager = cleanupManager
    }

    def up() {
        def swPort = new Tuple2(sw.dpId, port)
        def lastEvent = history.get(swPort)
        if (lastEvent) {
            Wrappers.silent { //Don't fail hard on this check. In rare cases we may miss the history entry
                waitForStabilization(lastEvent)
            }
            history.remove(swPort)
        }
        northbound.portUp(sw.dpId, port)
    }

    def safeUp() {
        if (northbound.getPort(sw.dpId, port).getState().first() != "LIVE") {
            up()
        }
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getActiveLinks().findAll {
                it.source.switchId == sw.dpId && it.source.portNo == port ||
                        it.destination.switchId == sw.dpId && it.destination.portNo == port
            }.size() == 2
        }
    }

    def down(CleanupAfter cleanupAfter = TEST, boolean isNotInScopeOfIslBreak = true) {
        if (isNotInScopeOfIslBreak) {
            cleanupManager.addAction(PORT_UP, { safeUp() }, cleanupAfter)
        }
        def response = northbound.portDown(sw.dpId, port)
        sleep(KildaProperties.ANTIFLAP_MIN * 1000)
        history.put(new Tuple2(sw.dpId, port), System.currentTimeMillis())
        response
    }

    /**
     * Wait till the current port is in a stable state (deactivated antiflap) by analyzing its history.
     */
    void waitForStabilization(Long since = 0) {
        // '* 2' it takes more time on a hardware env for link via 'a-switch'
        Wrappers.wait(KildaProperties.ANTIFLAP_COOLDOWN + WAIT_OFFSET * 2) {
            def history = northboundV2.getPortHistory(sw.dpId, port, since, null)

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

    def setDiscovery(boolean expectedStatus) {
        if (!expectedStatus) {
            cleanupManager.addAction(RESTORE_ISL, { setDiscovery(true) })
        }
        return northboundV2.updatePortProperties(sw.dpId, port, new PortPropertiesDto(discoveryEnabled: expectedStatus))
    }

    PortBlinker getBlinker(long interval, Properties producerProps) {
        new PortBlinker(KildaProperties.PRODUCER_PROPS, KildaProperties.TOPO_DISCO_TOPIC, sw, port, interval)
    }

    static def closeBlinker(PortBlinker blinker) {
        blinker?.isRunning() && blinker.stop(true)
    }

    PortDescription retrieveDetails() {
        northbound.getPort(sw.dpId, port)
    }
}
