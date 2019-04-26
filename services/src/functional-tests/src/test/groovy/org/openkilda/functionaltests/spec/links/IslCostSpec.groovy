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
        given: "An active ISL with created link props"
        int islCost = islUtils.getIslInfo(isl).get().cost
        northbound.updateLinkProps([isl, isl.reversed].collect{ islUtils.toLinkProps(it, [cost: islCost.toString()]) })

        when: "Bring port down on the source switch"
        northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)

        then: "ISL status becomes 'FAILED'"
        Wrappers.wait(WAIT_OFFSET) { assert islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED }

        and: "Cost of forward and reverse ISLs after bringing port down is increased"
        def newCost = islCostWhenPortDown + islCost
        def isls = northbound.getAllLinks()
        islUtils.getIslInfo(isls, isl).get().cost == newCost
        islUtils.getIslInfo(isls, isl.reversed).get().cost == newCost

        and: "Cost on corresponding link props is increased as well"
        northbound.getAllLinkProps()*.props.cost == [newCost, newCost]*.toString()

        and: "Bring port up on the source switch and reset costs"
        northbound.portUp(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED
        }
        northbound.deleteLinkProps(northbound.getAllLinkProps())

        where:
        isl                                                                                    | description
        getTopology().islsForActiveSwitches.find { !it.aswitch }                               | "a direct"
        getTopology().islsForActiveSwitches.find { it.aswitch?.inPort && it.aswitch?.outPort } | "an a-switch"
    }

    @Unroll
    def "Cost of #data.description ISL is NOT increased due to bringing port down on a switch \
(ISL cost #data.condition isl.cost.when.port.down)"() {
        given: "An active ISL with created link props"
        //TODO(rtretiak): After #1954 is merged use only one-direction prop
        def linkProps = [data.isl, data.isl.reversed]
                .collect{ islUtils.toLinkProps(it, ["cost": data.cost.toString()]) }
        northbound.updateLinkProps(linkProps)
        int islCost = islUtils.getIslInfo(data.isl).get().cost

        when: "Bring port down on the source switch"
        northbound.portDown(data.isl.srcSwitch.dpId, data.isl.srcPort)

        then: "ISL status becomes 'FAILED'"
        Wrappers.wait(WAIT_OFFSET) { assert islUtils.getIslInfo(data.isl).get().state == IslChangeType.FAILED }

        and: "Cost of forward and reverse ISLs after bringing port down is NOT increased"
        def isls = northbound.getAllLinks()
        islUtils.getIslInfo(isls, data.isl).get().cost == islCost
        islUtils.getIslInfo(isls, data.isl.reversed).get().cost == islCost

        and: "Cost on corresponding link props is NOT increased as well"
        northbound.getAllLinkProps()*.props.cost == [islCost, islCost]*.toString()

        and: "Cleanup: Bring port up on the source switch, delete link props and reset costs"
        northbound.portUp(data.isl.srcSwitch.dpId, data.isl.srcPort)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert islUtils.getIslInfo(data.isl).get().state == IslChangeType.DISCOVERED
        }
        northbound.deleteLinkProps(northbound.getAllLinkProps())

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
        given: "ISL going through a-switch with link props created"
        def isl = topology.islsForActiveSwitches.find {
            it.aswitch?.inPort && it.aswitch?.outPort && !it.bfd
        } ?: assumeTrue("Wasn't able to find suitable ISL", false)
        def isls = northbound.getAllLinks()
        int islCost = islUtils.getIslInfo(isls, isl).get().cost
        assert islUtils.getIslInfo(isls, isl.reversed).get().cost == islCost
        northbound.updateLinkProps([isl, isl.reversed].collect{ islUtils.toLinkProps(it, [cost: islCost.toString()]) })

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
        def islsAfterFail = northbound.getAllLinks()
        islUtils.getIslInfo(islsAfterFail, isl).get().cost == islCost
        islUtils.getIslInfo(islsAfterFail, isl.reversed).get().cost == islCost

        and: "Cost on link props is not increased as well"
        northbound.getAllLinkProps()*.props.cost == [islCost, islCost]*.toString()

        and: "Add a-switch rules to restore connection"
        lockKeeper.addFlows(rulesToRemove)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == IslChangeType.DISCOVERED
        }
    }
}
