package org.openkilda.functionaltests.spec.switches

import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.switches.PortHistoryResponse
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared
import spock.lang.Unroll

@See(["https://github.com/telstra/open-kilda/blob/issue/port-history-stats/docs/design/network-discovery/port-FSM.png",
        "https://github.com/telstra/open-kilda/blob/issue/port-history-stats/docs/design/network-discovery/AF-FSM.png"])
@Narrative("Verify that port history is created for the port up/down actions.")
class PortHistorySpec extends HealthCheckSpecification {
    @Autowired
    NorthboundServiceV2 northboundV2

    @Shared
    //confd/templates/wfm/topology.properties.tmpl => port.antiflap.stats.dumping.interval.seconds = 60
    //it means how often the 'ANTI_FLAP_PERIODIC_STATS' is logged in port history
    def antiflapDumpingInterval = 60

    @Unroll
    @IterationTag(tags = [SMOKE], iterationNameRegex = /direct/)
    def "Port history are created for the port down/up actions when link is #islDescription"() {
        given: "A link"
        assumeTrue("Unable to find $islDescription ISL for this test", isl as boolean)
        def timestampBefore = System.currentTimeMillis()

        when: "Execute port DOWN on the src switch"
        antiflap.portDown(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED
        }

        then: "Port history is created on the src switch"
        Wrappers.wait(WAIT_OFFSET) {
            Long timestampAfterDown = System.currentTimeMillis()
            with(northboundV2.getPortHistory(isl.srcSwitch.dpId, isl.srcPort, timestampBefore, timestampAfterDown)) {
                it.size() == 2
                checkPortHistory(it.find { it.event == PortHistoryEvent.ANTI_FLAP_ACTIVATED.toString() },
                        isl.srcSwitch.dpId, isl.srcPort, PortHistoryEvent.ANTI_FLAP_ACTIVATED)
                checkPortHistory(it.find { it.event == PortHistoryEvent.PORT_DOWN.toString() }, isl.srcSwitch.dpId,
                        isl.srcPort, PortHistoryEvent.PORT_DOWN)
            }
        }

        when: "Execute port UP on the src switch"
        antiflap.portUp(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED
        }

        then: "Port history is updated on the src switch"
        Long timestampAfterUp
        Wrappers.wait(WAIT_OFFSET) {
            timestampAfterUp = System.currentTimeMillis()
            with(northboundV2.getPortHistory(isl.srcSwitch.dpId, isl.srcPort, timestampBefore, timestampAfterUp)) {
                checkPortHistory(it.find { it.event == PortHistoryEvent.PORT_UP.toString() },
                        isl.srcSwitch.dpId, isl.srcPort, PortHistoryEvent.PORT_UP)
                def deactivateEvent = it.find { it.event == PortHistoryEvent.ANTI_FLAP_DEACTIVATED.toString() }
                checkPortHistory(deactivateEvent, isl.srcSwitch.dpId, isl.srcPort,
                        PortHistoryEvent.ANTI_FLAP_DEACTIVATED)
                // no flapping occurs during cooldown, so antiflap stat doesn't exist in the ANTI_FLAP_DEACTIVATED event
                !deactivateEvent.downCount
                !deactivateEvent.upCount
            }
        }

        and: "Port history on the dst switch is not empty when link is direct"
        with(northboundV2.getPortHistory(isl.dstSwitch.dpId, isl.dstPort, timestampBefore, timestampAfterUp)) {
            it.size() == historySizeOnDstSw
            if (historySizeOnDstSw as boolean) {
                checkPortHistory(it.find { it.event == PortHistoryEvent.PORT_UP.toString() },
                        isl.dstSwitch.dpId, isl.dstPort, PortHistoryEvent.PORT_UP)
                def deactivateEvent = it.find { it.event == PortHistoryEvent.ANTI_FLAP_DEACTIVATED.toString() }
                checkPortHistory(deactivateEvent, isl.dstSwitch.dpId, isl.dstPort,
                        PortHistoryEvent.ANTI_FLAP_DEACTIVATED)
                // no flapping occurs during cooldown, so antiflap stat doesn't exist in the ANTI_FLAP_DEACTIVATED event
                !deactivateEvent.downCount
                !deactivateEvent.upCount
            }
        }

        and: "Port history on the src switch is also available using default timeline"
        northboundV2.getPortHistory(isl.srcSwitch.dpId, isl.srcPort).size() >= 4

        where:
        [islDescription, historySizeOnDstSw, isl] << [
                ["direct", 4, getTopology().islsForActiveSwitches.find { !it.aswitch }],
                ["a-switch", 0, getTopology().islsForActiveSwitches.find {
                    it.aswitch?.inPort && it.aswitch?.outPort
                }]
        ]
    }

    def "Port history should not be returned in case timeline is incorrect (timeBefore > timeAfter)"() {
        given: "A direct link with port history"
        def timestampBefore = System.currentTimeMillis()
        def isl = getTopology().islsForActiveSwitches.find { !it.aswitch }

        antiflap.portDown(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED
        }

        antiflap.portUp(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED
        }

        def timestampAfter = System.currentTimeMillis()
        def portHistory = northboundV2.getPortHistory(isl.srcSwitch.dpId, isl.srcPort, timestampBefore, timestampAfter)
        assert portHistory.size() == 4 // PORT_DOWN, ANTI_FLAP_ACTIVATED, PORT_UP, ANTI_FLAP_DEACTIVATED

        when: "Get port history on the src switch for incorrect timeline"
        def portH = northboundV2.getPortHistory(isl.srcSwitch.dpId, isl.srcPort, timestampAfter, timestampBefore)

        then: "Port history is NOT returned"
        portH.isEmpty()
    }

    def "Port history should not be returned in case port/switch have never existed"() {
        when: "Try to get port history for incorrect port and switch"
        def portHistory = northboundV2.getPortHistory(NON_EXISTENT_SWITCH_ID, 99999)

        then: "Port history is empty"
        portHistory.isEmpty()
    }

    @Tags(VIRTUAL)
    def "Port history is available when switch is DEACTIVATED"() {
        given: "A direct link"
        def timestampBefore = System.currentTimeMillis()
        def isl = getTopology().islsForActiveSwitches.find { !it.aswitch }

        when: "Execute port DOWN/UP on the src switch"
        antiflap.portDown(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED
        }

        antiflap.portUp(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED
        }
        def timestampAfter = System.currentTimeMillis()
        northboundV2.getPortHistory(isl.srcSwitch.dpId, isl.srcPort, timestampBefore, timestampAfter).size() == 4

        and: "Deactivate the src switch"
        def switchToDisconnect = isl.srcSwitch
        lockKeeper.knockoutSwitch(switchToDisconnect)
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getSwitch(switchToDisconnect.dpId).state == SwitchChangeType.DEACTIVATED
        }

        then: "Port history on the src switch is still available"
        northboundV2.getPortHistory(isl.srcSwitch.dpId, isl.srcPort, timestampBefore, timestampAfter).size() == 4

        and: "Cleanup: Revive the src switch"
        lockKeeper.reviveSwitch(switchToDisconnect)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getSwitch(switchToDisconnect.dpId).state == SwitchChangeType.ACTIVATED
        }
    }

    def "Port history is able to show ANTI_FLAP statistic"() {
        given: "A direct link"
        def isl = getTopology().islsForActiveSwitches.find { !it.aswitch }
        assumeTrue("Unable to find ISL for this test", isl as boolean)
        def timestampBefore = System.currentTimeMillis()

        when: "Execute port DOWN on the src switch"
        northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED
        }

        then: "Port history is created on the src switch"
        Wrappers.wait(WAIT_OFFSET) {
            Long timestampAfterDown = System.currentTimeMillis()
            with(northboundV2.getPortHistory(isl.srcSwitch.dpId, isl.srcPort, timestampBefore, timestampAfterDown)) {
                it.size() == 2 // PORT_DOWN, ANTI_FLAP_ACTIVATED
            }
        }

        when: "Generate antiflap statistic"
        def count = 0
        Integer intervalBetweenPortStateManipulation = (antiflapCooldown / 10).toInteger()
        Wrappers.timedLoop(antiflapDumpingInterval * 0.9) {
            northbound.portUp(isl.srcSwitch.dpId, isl.srcPort)
            sleep(intervalBetweenPortStateManipulation)
            northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)
            sleep(intervalBetweenPortStateManipulation)
            count += 1
        }

        then: "Antiflap statistic is available in port history"
        Wrappers.wait(antiflapDumpingInterval * 0.1 + WAIT_OFFSET) {
            Long timestampAfterStat = System.currentTimeMillis()
            with(northboundV2.getPortHistory(isl.srcSwitch.dpId, isl.srcPort, timestampBefore, timestampAfterStat)) {
                it.size() == 3 // PORT_DOWN, ANTI_FLAP_ACTIVATED, ANTI_FLAP_PERIODIC_STATS
                def antiflapStat = it[-1]
                checkPortHistory(antiflapStat, isl.srcSwitch.dpId, isl.srcPort,
                        PortHistoryEvent.ANTI_FLAP_PERIODIC_STATS)
                count == antiflapStat.downCount
                count == antiflapStat.upCount
            }
        }

        and: "Cleanup: revert system to original state"
        northbound.portUp(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(WAIT_OFFSET + discoveryInterval + antiflapCooldown) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED
        }
    }

    def "System shows antiflap statistic in the ANTI_FLAP_DEACTIVATED event when antiflap is deactivated\
 before collecting ANTI_FLAP_PERIODIC_STATS"() {
        assumeTrue("It can't be run when antiflap.cooldown + flap_duration > port.antiflap.stats.dumping.interval.seconds",
                antiflapCooldown + 3 < antiflapDumpingInterval)
        //port up/down procedure is done once in this test, so it can't take more than 3 seconds
        assumeTrue("At least 10 seconds should be available for changing port status", antiflapCooldown >= 10)
        // assume that portDown/portUp can take some time on hardware env(no more than 10 seconds)

        given: "A direct link"
        def isl = getTopology().islsForActiveSwitches.find { !it.aswitch }
        assumeTrue("Unable to find ISL for this test", isl as boolean)
        def timestampBefore = System.currentTimeMillis()

        when: "Execute port DOWN on the src switch for activating antiflap"
        northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(WAIT_OFFSET) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED
            Long timestampAfterDown = System.currentTimeMillis()
            with(northboundV2.getPortHistory(isl.srcSwitch.dpId, isl.srcPort, timestampBefore, timestampAfterDown)) {
                it.size() == 2 // PORT_DOWN, ANTI_FLAP_ACTIVATED
            }
        }

        and: "Generate antiflap statistic"
        northbound.portUp(isl.srcSwitch.dpId, isl.srcPort)
        northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)

        then: "Antiflap statistic is available in port history inside the ANTI_FLAP_DEACTIVATED event"
        Wrappers.wait(antiflapCooldown + WAIT_OFFSET) {
            Long timestampAfterStat = System.currentTimeMillis()
            with(northboundV2.getPortHistory(isl.srcSwitch.dpId, isl.srcPort, timestampBefore, timestampAfterStat)) {
                it.findAll { it.event == PortHistoryEvent.ANTI_FLAP_PERIODIC_STATS.toString() }.empty
                it.size() == 3 // PORT_DOWN, ANTI_FLAP_ACTIVATED, ANTI_FLAP_DEACTIVATED
                def antiflapStat = it[-1]
                checkPortHistory(antiflapStat, isl.srcSwitch.dpId, isl.srcPort,
                        PortHistoryEvent.ANTI_FLAP_DEACTIVATED)
                antiflapStat.downCount == 1
                antiflapStat.upCount == 1
            }
        }

        and: "Cleanup: revert system to original state"
        northbound.portUp(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(WAIT_OFFSET + discoveryInterval + antiflapCooldown) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED
        }
    }

    void checkPortHistory(PortHistoryResponse portHistory, SwitchId switchId, Integer port, PortHistoryEvent event) {
        verifyAll(portHistory) {
            id != null
            switchId == switchId
            portNumber == port
            it.event == event.toString()
            date != null
        }
    }

    enum PortHistoryEvent {
        PORT_UP,
        PORT_DOWN,
        ANTI_FLAP_ACTIVATED,
        ANTI_FLAP_DEACTIVATED,
        ANTI_FLAP_PERIODIC_STATS
    }
}
