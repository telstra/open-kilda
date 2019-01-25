package org.openkilda.functionaltests.spec.links

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow

class IslCostSpec extends BaseSpecification {

    def "ISL cost is NOT increased due to connection loss between switches (not port down)"() {
        given: "ISL going through a-switch"
        def isl = topology.islsForActiveSwitches.find {
            it.aswitch
        } ?: assumeTrue("Wasn't able to find suitable ISL", false)

        when: "Remove a-switch rules to break link between switches"
        int islCost = islUtils.getIslCost(isl)
        def rulesToRemove = [new ASwitchFlow(isl.aswitch.inPort, isl.aswitch.outPort),
                             new ASwitchFlow(isl.aswitch.outPort, isl.aswitch.inPort)]

        lockKeeper.removeFlows(rulesToRemove)

        then: "ISL status becomes 'FAILED'"
        Wrappers.wait(discoveryTimeout * 1.5 + WAIT_OFFSET) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(islUtils.reverseIsl(isl)).get().state == IslChangeType.FAILED
        }

        and: "ISL cost after connection loss is not increased"
        islUtils.getIslCost(isl) == islCost
        islUtils.getIslCost(islUtils.reverseIsl(isl)) == islCost

        and: "Add a-switch rules to restore connection"
        lockKeeper.addFlows(rulesToRemove)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED
        }
    }
}
