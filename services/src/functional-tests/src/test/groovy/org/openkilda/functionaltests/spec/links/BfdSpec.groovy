package org.openkilda.functionaltests.spec.links

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType

class BfdSpec extends BaseSpecification {
    def "Interruption in BFD connection instantly leads to ISL failure"() {
        given: "An ISL through a-switch with a BFD session up"
        def isl = topology.islsForActiveSwitches.find { it.aswitch?.inPort && it.aswitch?.outPort && it.bfd }
        assumeTrue("Require at least one a-switch BFD ISL", isl as boolean)

        when: "Interrupt ISL connection by breaking rule on a-switch"
        lockKeeper.removeFlows([isl.aswitch])

        then: "ISL immediately gets FAILED"
        Wrappers.wait(WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, isl).get().actualState == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().actualState == IslChangeType.FAILED
        }

        when: "Restore connection"
        lockKeeper.addFlows([isl.aswitch])

        then: "ISL is rediscovered"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, isl).get().actualState == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().actualState == IslChangeType.DISCOVERED
        }
        database.resetCosts()
    }
}
