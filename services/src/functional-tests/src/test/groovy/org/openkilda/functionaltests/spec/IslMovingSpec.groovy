package org.openkilda.functionaltests.spec

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.tools.IslUtils
import org.springframework.beans.factory.annotation.Autowired

import static org.openkilda.messaging.info.event.IslChangeType.*

class IslMovingSpec extends BaseSpecification {
    @Autowired
    TopologyDefinition topology

    @Autowired
    IslUtils islUtils

    @Autowired
    NorthboundService northbound

    def "ISL status changes to MOVED when replugging"() {
        given: "A connected a-switch link"
        def isl = topology.islsForActiveSwitches.find {
            it.getAswitch()?.inPort && it.getAswitch()?.outPort
        }
        assert isl

        and: "A non-connected a-switch link"
        def notConnectedIsl = topology.notConnectedIsls.first()

        when: "Replug one end of connected link to the not connected one"
        def newIsl = islUtils.replug(isl, false, notConnectedIsl, true)

        then: "New ISL becomes Discovered"
        islUtils.waitForIslStatus([newIsl, islUtils.reverseIsl(newIsl)], DISCOVERED)

        and: "Replugged ISL status changes to MOVED"
        islUtils.waitForIslStatus([isl, islUtils.reverseIsl(isl)], MOVED)

        when: "Replug the link back where it was"
        islUtils.replug(newIsl, true, isl, false)

        then: "Original ISL becomes Discovered again"
        islUtils.waitForIslStatus([isl, islUtils.reverseIsl(isl)], DISCOVERED)

        and: "Replugged ISL status changes to MOVED"
        islUtils.waitForIslStatus([newIsl, islUtils.reverseIsl(newIsl)], MOVED)
    }

    def "New ISL is not getting discovered when replugging into a self-loop (same port)"() {
        given: "A connected a-switch link"
        def isl = topology.islsForActiveSwitches.find {
            it.getAswitch()?.inPort && it.getAswitch()?.outPort
        }
        assert isl

        when: "Replug one end of link into 'itself'"
        def loopedIsl = islUtils.replug(isl, false, isl, true)

        then: "Replugged ISL status changes to FAILED"
        islUtils.waitForIslStatus([isl, islUtils.reverseIsl(isl)], FAILED)

        and: "The potential self-loop ISL is not present in list of isls"
        def allLinks = northbound.getAllLinks()
        !islUtils.getIslInfo(allLinks, loopedIsl).present
        !islUtils.getIslInfo(allLinks, islUtils.reverseIsl(loopedIsl)).present

        when: "Replug the link back where it was"
        islUtils.replug(loopedIsl, true, isl, false)

        then: "Original ISL becomes Discovered again"
        islUtils.waitForIslStatus([isl, islUtils.reverseIsl(isl)], DISCOVERED)
    }
}
