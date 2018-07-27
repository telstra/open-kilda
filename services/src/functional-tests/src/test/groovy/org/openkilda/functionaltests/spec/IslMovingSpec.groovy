package org.openkilda.functionaltests.spec

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.tools.IslUtils
import org.springframework.beans.factory.annotation.Autowired

class IslMovingSpec extends BaseSpecification {
    @Autowired
    TopologyDefinition topology

    @Autowired
    IslUtils islUtils

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
        islUtils.waitForIslStatus([newIsl, islUtils.reverseIsl(newIsl)], "DISCOVERED")

        and: "Replugged ISL status changes to MOVED"
        islUtils.waitForIslStatus([isl, islUtils.reverseIsl(isl)], "MOVED")

        when: "Replug the link back where it was"
        islUtils.replug(newIsl, true, isl, false)

        then: "Original ISL becomes Discovered again"
        islUtils.waitForIslStatus([isl, islUtils.reverseIsl(isl)], "DISCOVERED")

        and: "Replugged ISL status changes to MOVED"
        islUtils.waitForIslStatus([newIsl, islUtils.reverseIsl(newIsl)], "MOVED")
    }
}
