package org.openkilda.functionaltests.spec.links

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import spock.lang.Narrative

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.MOVED
import static org.openkilda.testing.Constants.WAIT_OFFSET

@Narrative("""Minimal port speed value is chosen for ISL capacity.
Sometimes an ISL have different port speed on its edges.
In that case, we need to set ISL capacity and all bandwidth parameters according to minimal speed value.
Eg. 10G on one side, and 1G on another side, the ISL should have a 1G capacity.""")
class IslMinPortSpeedSpec extends HealthCheckSpecification {
    @Tags([SMOKE, HARDWARE])
    def "System sets min port speed for isl capacity"() {
        given: "Two ports with different port speed"
        def isl = isls.all().withASwitch().random()
        def port = isl.srcEndpoint.retrieveDetails()
        def newDst = isls.allNotConnected().getListOfIsls().find {
            it.srcSwId != isl.srcSwId && it.srcEndpoint.retrieveDetails().maxSpeed != port.maxSpeed
        }
        assumeTrue(newDst as boolean, "Wasn't able to find a port with other port speed")
        def newDstPort = newDst.srcEndpoint.retrieveDetails()

        when: "Replug one end of the connected link to the destination switch(isl.srcSwitchId -> newDst.srcSwitchId)"
        def newIsl = isl.replugDestination(newDst, true, false)
        newIsl.waitForStatus(DISCOVERED)
        isl.waitForStatus(MOVED)

        then: "Max bandwidth of new ISL is equal to the minimal port speed"
        Wrappers.wait(WAIT_OFFSET) {
           assert newIsl.getNbDetails().maxBandwidth == [port.maxSpeed, newDstPort.maxSpeed].min()
        }
    }
}
