package org.openkilda.functionaltests.spec.links

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType

import org.springframework.beans.factory.annotation.Value
import spock.lang.Unroll

class IslCostSpec extends BaseSpecification {

    @Value('${isl.cost.when.port.down}')
    int islCostWhenPortDown

    def setupOnce() {
        database.resetCosts()  // reset cost on all links before tests
    }

    @Unroll
    def "Cost of #description ISL is increased due to bringing port down on a switch \
(ISL cost < isl.cost.when.port.down)"() {
        given: "An active ISL"
        int islCost = database.getIslCost(isl)

        when: "Bring port down on the source switch"
        northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)

        then: "ISL status becomes 'FAILED'"
        Wrappers.wait(WAIT_OFFSET) { assert islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED }

        and: "Cost of forward and reverse ISLs after bringing port down is increased"
        database.getIslCost(isl) == islCostWhenPortDown + islCost
        database.getIslCost(isl.reversed) == islCostWhenPortDown + islCost

        and: "Bring port up on the source switch and reset costs"
        northbound.portUp(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED
        }
        database.resetCosts()

        where:
        isl                                                                                    | description
        getTopology().islsForActiveSwitches.find { !it.aswitch }                               | "a direct"
        getTopology().islsForActiveSwitches.find { it.aswitch?.inPort && it.aswitch?.outPort } | "an a-switch"
    }

    @Unroll
    def "Cost of #data.description ISL is NOT increased due to bringing port down on a switch \
(ISL cost #data.condition isl.cost.when.port.down)"() {
        given: "An active ISL"
        def linkProps = [islUtils.toLinkProps(data.isl, ["cost": data.cost.toString()])]
        northbound.updateLinkProps(linkProps)

        int islCost = database.getIslCost(data.isl)

        when: "Bring port down on the source switch"
        northbound.portDown(data.isl.srcSwitch.dpId, data.isl.srcPort)

        then: "ISL status becomes 'FAILED'"
        Wrappers.wait(WAIT_OFFSET) { assert islUtils.getIslInfo(data.isl).get().state == IslChangeType.FAILED }

        and: "Cost of forward and reverse ISLs after bringing port down is NOT increased"
        database.getIslCost(data.isl) == islCost
        //TODO(ylobankov): Uncomment the check once issue #1954 is merged.
        //database.getIslCost(data.isl.reversed) == islCost

        and: "Bring port up on the source switch, delete link props and reset costs"
        northbound.portUp(data.isl.srcSwitch.dpId, data.isl.srcPort)
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert islUtils.getIslInfo(data.isl).get().state == IslChangeType.DISCOVERED
        }
        database.resetCosts()

        where:
        data << [
                [
                        isl        : getTopology().islsForActiveSwitches.find { !it.aswitch },
                        description: "a direct",
                        cost       : getIslCostWhenPortDown(),
                        condition  : "="
                ],
                [
                        isl        : getTopology().islsForActiveSwitches.find {
                            it.aswitch?.inPort && it.aswitch?.outPort
                        },
                        description: "an a-switch",
                        cost       : getIslCostWhenPortDown(),
                        condition  : "="
                ],
                [
                        isl        : getTopology().islsForActiveSwitches.find { !it.aswitch },
                        description: "a direct",
                        cost       : getIslCostWhenPortDown() + 1,
                        condition  : ">"
                ],
                [
                        isl        : getTopology().islsForActiveSwitches.find {
                            it.aswitch?.inPort && it.aswitch?.outPort
                        },
                        description: "an a-switch",
                        cost       : getIslCostWhenPortDown() + 1,
                        condition  : ">"
                ]
        ]
    }

    def "ISL cost is NOT increased due to failing connection between switches (not port down)"() {
        given: "ISL going through a-switch"
        def isl = topology.islsForActiveSwitches.find {
            it.aswitch?.inPort && it.aswitch?.outPort && !it.bfd
        } ?: assumeTrue("Wasn't able to find suitable ISL", false)

        int islCost = database.getIslCost(isl)
        assert database.getIslCost(isl.reversed) == islCost

        when: "Remove a-switch rules to break link between switches"
        def rulesToRemove = [isl.aswitch, isl.aswitch.reversed]
        lockKeeper.removeFlows(rulesToRemove)

        then: "Status of forward and reverse ISLs becomes 'FAILED'"
        Wrappers.wait(discoveryTimeout * 1.5 + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == IslChangeType.FAILED
        }

        and: "Cost of forward and reverse ISLs after failing connection is not increased"
        database.getIslCost(isl) == islCost
        database.getIslCost(isl.reversed) == islCost

        and: "Add a-switch rules to restore connection"
        lockKeeper.addFlows(rulesToRemove)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == IslChangeType.DISCOVERED
        }
    }
}
