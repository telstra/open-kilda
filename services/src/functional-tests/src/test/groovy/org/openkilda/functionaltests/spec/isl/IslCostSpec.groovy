package org.openkilda.functionaltests.spec.isl

import static org.junit.Assume.assumeTrue

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow
import org.openkilda.testing.tools.IslUtils

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

class IslCostSpec extends BaseSpecification {
    @Autowired
    TopologyDefinition topology
    @Autowired
    IslUtils islUtils
    @Autowired
    LockKeeperService lockKeeper

    @Value('${discovery.interval}')
    int discoveryInterval
    @Value('${discovery.timeout}')
    int discoveryTimeout

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

        then: "Link status becomes 'FAILED'"
        Wrappers.wait(discoveryTimeout * 1.5) { islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED }

        and: "ISL cost after connection loss is not increased"
        islUtils.getIslCost(isl) == islCost

        and: "Add a-switch rules to restore connection"
        lockKeeper.addFlows(rulesToRemove)
        Wrappers.wait(discoveryInterval + 3) { islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED }
    }
}
