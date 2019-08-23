package org.openkilda.functionaltests.spec.switches

import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.switches.PortHistoryResponse
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Unroll

@Narrative("Verify that port history is created for the port up/down actions.")
class PortHistorySpec extends HealthCheckSpecification {
    @Autowired
    NorthboundServiceV2 northboundV2

    @Unroll
    def "Port history are created for the port down/up actions when link is #islDescription"() {
        given: "A link"
        assumeTrue("Unable to find $islDescription ISL for this test", isl as boolean)
        def timestampBefore = System.currentTimeSeconds()

        when: "Execute port DOWN on the src switch"
        northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)

        then: "Port is really DOWN"
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getPort(isl.srcSwitch.dpId, isl.srcPort).state[0] == "LINK_DOWN"
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED
        }

        and: "Port history is created on the src switch"
        Long timestampAfterDown = System.currentTimeSeconds()
        with(northboundV2.getPortHistory(isl.srcSwitch.dpId, isl.srcPort, timestampBefore, timestampAfterDown)) {
            it.size() == 1
            checkHistoryPortDownAction(it[0], isl.srcSwitch.dpId, isl.srcPort)
        }

        when: "Execute port UP on the src switch"
        northbound.portUp(isl.srcSwitch.dpId, isl.srcPort)

        then: "Port is really UP"
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getPort(isl.srcSwitch.dpId, isl.srcPort).state[0] == "LIVE"
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED
        }

        and: "Port history is updated on the src switch"
        Long timestampAfterUp = System.currentTimeSeconds()
        with(northboundV2.getPortHistory(isl.srcSwitch.dpId, isl.srcPort, timestampBefore, timestampAfterUp)) {
            it.size() == 2
            checkHistoryPortUpAction(it[0], isl.srcSwitch.dpId, isl.srcPort)
        }

        and: "Port history is not empty when link is direct"
        northboundV2.getPortHistory(isl.dstSwitch.dpId, isl.dstPort, timestampBefore, timestampAfterUp).size() ==
                historySizeOnDstSw

        and: "Port history is also available using default timeline"
        northboundV2.getPortHistory(isl.srcSwitch.dpId, isl.srcPort).size() >= 2

        where:
        [islDescription, historySizeOnDstSw, isl] << [
                ["direct", 2, getTopology().islsForActiveSwitches.find { !it.aswitch }],
                ["a-switch", 0, getTopology().islsForActiveSwitches.find {
                    it.aswitch?.inPort && it.aswitch?.outPort
                }]
        ]
    }

    @Tags(SMOKE)
    def "Port history should not be returned in case timeline is incorrect (timeBefore > timeAfter)"() {
        given: "A direct link"
        def timestampBefore = System.currentTimeSeconds()
        def isl = getTopology().islsForActiveSwitches.find { !it.aswitch }

        when: "Execute port DOWN on the src switch"
        northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getPort(isl.srcSwitch.dpId, isl.srcPort).state[0] == "LINK_DOWN"
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED
        }

        and: "Execute port UP on the src switch"
        northbound.portUp(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getPort(isl.srcSwitch.dpId, isl.srcPort).state[0] == "LIVE"
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED
        }

        then: "Port history is created"
        Long timestampAfter = System.currentTimeSeconds()
        def portHistory = northboundV2.getPortHistory(isl.srcSwitch.dpId, isl.srcPort, timestampBefore, timestampAfter)
        assert portHistory.size() == 2

        when: "Get port history for incorrect timeline"
        def portH = northboundV2.getPortHistory(isl.srcSwitch.dpId, isl.srcPort, timestampAfter, timestampBefore)

        then: "Port history is NOT returned"
        portH.isEmpty()
    }

    def "Port history should not be returned in case port/switch were never exist"() {
        when: "Try to get port history for incorrect port and switch"
        def portHistory = northboundV2.getPortHistory(NON_EXISTENT_SWITCH_ID, 99999)

        then: "Port history is empty"
        portHistory.isEmpty()
    }

    def "Port history is available when switch is DEACTIVATED"() {
        given: "A direct link"
        def timestampBefore = System.currentTimeSeconds()
        def isl = getTopology().islsForActiveSwitches.find { !it.aswitch }

        when: "Execute port DOWN on the src switch"
        northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getPort(isl.srcSwitch.dpId, isl.srcPort).state[0] == "LINK_DOWN"
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED
        }

        and: "Execute port UP on the src switch"
        northbound.portUp(isl.srcSwitch.dpId, isl.srcPort)

        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getPort(isl.srcSwitch.dpId, isl.srcPort).state[0] == "LIVE"
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED
        }

        then: "Port history is created"
        Long timestampAfter = System.currentTimeSeconds()
        northboundV2.getPortHistory(isl.srcSwitch.dpId, isl.srcPort, timestampBefore, timestampAfter).size() == 2

        when: "Deactivate the src switch"
        def switchToDisconnect = isl.srcSwitch
        lockKeeper.knockoutSwitch(switchToDisconnect)
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getSwitch(switchToDisconnect.dpId).state == SwitchChangeType.DEACTIVATED
        }

        then: "Port history is still available"
        northboundV2.getPortHistory(isl.srcSwitch.dpId, isl.srcPort, timestampBefore, timestampAfter).size() == 2

        and: "Cleanup: Revive the src switch"
        lockKeeper.reviveSwitch(switchToDisconnect)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getSwitch(switchToDisconnect.dpId).state == SwitchChangeType.ACTIVATED
        }
    }

    void checkHistoryPortDownAction(PortHistoryResponse portHistory, SwitchId switchId, Integer port) {
        verifyAll(portHistory) {
            id != null
            switchId == switchId
            portNumber == port
            event == "PORT_DOWN"
            time != null
        }
    }

    void checkHistoryPortUpAction(PortHistoryResponse portHistory, SwitchId switchId, Integer port) {
        verifyAll(portHistory) {
            id != null
            switchId == switchId
            portNumber == port
            event == "PORT_UP"
            time != null
        }
    }
}