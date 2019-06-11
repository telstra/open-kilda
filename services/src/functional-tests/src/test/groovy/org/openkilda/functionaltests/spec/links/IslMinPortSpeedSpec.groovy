package org.openkilda.functionaltests.spec.links

import static org.junit.Assume.assumeTrue
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.MOVED
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers

import spock.lang.Narrative

@Narrative("""Minimal port speed value is chosen for ISL capacity.
Sometimes an ISL have different port speed on its edges.
In that case, we need to set ISL capacity and all bandwidth parameters according to minimal speed value.
Eg. 10G on one side, and 1G on another side, the ISL should have a 1G capacity.""")
class IslMinPortSpeedSpec extends BaseSpecification {

    def "System sets min port speed for isl capacity"() {
        given: "Two ports with different port speed"
        def isl = topology.islsForActiveSwitches.find { it.getAswitch()?.inPort && it.getAswitch()?.outPort }
        def port = northbound.getPort(isl.srcSwitch.dpId, isl.srcPort)
        assumeTrue("Wasn't able to find required a-switch links", isl as boolean)

        def notConnectedIsls = topology.notConnectedIsls
        def newDst = notConnectedIsls.find {
            it.srcSwitch.dpId != isl.srcSwitch.dpId &&
                    northbound.getPort(it.srcSwitch.dpId, it.srcPort).maxSpeed != port.maxSpeed
        }
        assumeTrue("Wasn't able to find a port with other port speed", newDst as boolean)
        def newDstPort = northbound.getPort(newDst.srcSwitch.dpId, newDst.srcPort)

        when: "Replug one end of the connected link to the destination switch(isl.srcSwitchId -> newDst.srcSwitchId)"
        def newIsl = islUtils.replug(isl, false, newDst, true)

        islUtils.waitForIslStatus([newIsl, newIsl.reversed], DISCOVERED)
        islUtils.waitForIslStatus([isl, isl.reversed], MOVED)

        then: "Max bandwidth of new ISL is equal to the minimal port speed"
        Wrappers.wait(WAIT_OFFSET) {
            islUtils.getIslInfo(newIsl).get().maxBandwidth == [port.maxSpeed, newDstPort.maxSpeed].min()
        }

        and: "Cleanup: Replug the link back and delete the moved ISL"
        islUtils.replug(newIsl, true, isl, false)
        islUtils.waitForIslStatus([isl, isl.reversed], DISCOVERED)
        islUtils.waitForIslStatus([newIsl, newIsl.reversed], MOVED)
        northbound.deleteLink(islUtils.toLinkParameters(newIsl))
        Wrappers.wait(WAIT_OFFSET) { assert !islUtils.getIslInfo(newIsl).isPresent() }
    }
}
