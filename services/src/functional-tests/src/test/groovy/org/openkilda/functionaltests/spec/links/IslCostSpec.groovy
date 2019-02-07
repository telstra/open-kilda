package org.openkilda.functionaltests.spec.links

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.northbound.dto.links.LinkPropsDto

import org.springframework.beans.factory.annotation.Value
import spock.lang.Shared
import spock.lang.Unroll

class IslCostSpec extends BaseSpecification {

    @Shared
    @Value('${isl.cost.when.port.down}')
    int islCostWhenPortDown

    def "ISL cost is increased due to bringing port down on a switch (ISL cost < isl.cost.when.port.down)"() {
        given: "Active forward and reverse ISLs with equal cost"
        def isl = topology.islsForActiveSwitches.first()
        def reverseIsl = islUtils.reverseIsl(isl)

        int islCost = islUtils.getIslCost(isl)
        assert islUtils.getIslCost(reverseIsl) == islCost

        when: "Bring port down on the source switch"
        northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)

        then: "Status of forward and reverse ISLs becomes 'FAILED'"
        Wrappers.wait(WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, reverseIsl).get().state == IslChangeType.FAILED
        }

        and: "Cost of forward and reverse ISLs after bringing port down is increased"
        islUtils.getIslCost(isl) == islCostWhenPortDown + islCost
        islUtils.getIslCost(reverseIsl) == islCostWhenPortDown + islCost

        and: "Bring port up on the source switch and reset costs"
        northbound.portUp(isl.srcSwitch.dpId, isl.srcPort)
        database.resetCosts()
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, reverseIsl).get().state == IslChangeType.DISCOVERED
        }
    }

    @Unroll
    def "ISL cost is NOT increased due to bringing port down on a switch \
(ISL cost #condition isl.cost.when.port.down)"() {
        given: "Active forward and reverse ISLs"
        def isl = topology.islsForActiveSwitches.first()
        def reverseIsl = islUtils.reverseIsl(isl)

        and: "Forward ISL has cost #condition isl.cost.when.port.down"
        def linkProps = [new LinkPropsDto(isl.srcSwitch.dpId.toString(), isl.srcPort, isl.dstSwitch.dpId.toString(),
                isl.dstPort, ["cost": cost.toString()])]
        northbound.updateLinkProps(linkProps)

        int islCost = islUtils.getIslCost(isl)
        int reverseIslCost = islUtils.getIslCost(reverseIsl)
        assert islCost != reverseIslCost

        when: "Bring port down on the source switch"
        northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)

        then: "Status of forward and reverse ISLs becomes 'FAILED'"
        Wrappers.wait(WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, reverseIsl).get().state == IslChangeType.FAILED
        }

        and: "Cost of forward ISL after bringing port down is NOT increased"
        islUtils.getIslCost(isl) == islCost

        and: "Cost of reverse ISL after bringing port down is increased"
        islUtils.getIslCost(reverseIsl) == islCostWhenPortDown + reverseIslCost

        and: "Bring port up on the source switch, delete link props and reset costs"
        northbound.portUp(isl.srcSwitch.dpId, isl.srcPort)
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        database.resetCosts()
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, reverseIsl).get().state == IslChangeType.DISCOVERED
        }

        where:
        cost                    | condition
        islCostWhenPortDown     | "=="
        islCostWhenPortDown + 1 | ">"
    }

    def "ISL cost is NOT increased due to failing connection between switches (not port down)"() {
        given: "ISL going through a-switch"
        def isl = topology.islsForActiveSwitches.find {
            it.aswitch?.inPort && it.aswitch?.outPort && !it.bfd
        } ?: assumeTrue("Wasn't able to find suitable ISL", false)
        def reverseIsl = islUtils.reverseIsl(isl)

        int islCost = islUtils.getIslCost(isl)
        assert islUtils.getIslCost(reverseIsl) == islCost

        when: "Remove a-switch rules to break link between switches"
        def rulesToRemove = [isl.aswitch, isl.aswitch.reversed]
        lockKeeper.removeFlows(rulesToRemove)

        then: "Status of forward and reverse ISLs becomes 'FAILED'"
        Wrappers.wait(discoveryTimeout * 1.5 + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, reverseIsl).get().state == IslChangeType.FAILED
        }

        and: "Cost of forward and reverse ISLs after failing connection is not increased"
        islUtils.getIslCost(isl) == islCost
        islUtils.getIslCost(reverseIsl) == islCost

        and: "Add a-switch rules to restore connection"
        lockKeeper.addFlows(rulesToRemove)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, reverseIsl).get().state == IslChangeType.DISCOVERED
        }
    }
}
