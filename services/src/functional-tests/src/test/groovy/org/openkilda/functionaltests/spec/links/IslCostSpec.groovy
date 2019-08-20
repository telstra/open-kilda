package org.openkilda.functionaltests.spec.links

import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.SwitchChangeType

import spock.lang.Unroll

class IslCostSpec extends HealthCheckSpecification {

    def setupOnce() {
        database.resetCosts()  // reset cost on all links before tests
    }

    @Unroll
    @IterationTag(tags = [SMOKE], iterationNameRegex = /a direct/)
    def "Cost of #description ISL is not increased due to bringing port down on a switch"() {
        given: "An active ISL with created link props"
        assumeTrue("$description isl is not found for the test", isl.asBoolean())
        int islCost = islUtils.getIslInfo(isl).get().cost
        northbound.updateLinkProps([isl, isl.reversed].collect { islUtils.toLinkProps(it, [cost: islCost.toString()]) })

        when: "Bring port down on the source switch"
        northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)

        then: "ISL status becomes 'FAILED'"
        Wrappers.wait(WAIT_OFFSET) { assert islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED }

        and: "Cost of forward and reverse ISLs after bringing port down is increased"
        def isls = northbound.getAllLinks()
        islUtils.getIslInfo(isls, isl).get().cost == islCost
        islUtils.getIslInfo(isls, isl.reversed).get().cost == islCost

        and: "Cost on corresponding link props is not increased as well"
        northbound.getAllLinkProps()*.props.cost == [islCost, islCost]*.toString()

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

    //'ISL with BFD session' case is covered in BfdSpec. Spoiler: it should act the same and don't change cost at all.
    def "ISL cost is NOT increased due to failing connection between switches (not port down)"() {
        given: "ISL going through a-switch with link props created"
        def isl = topology.islsForActiveSwitches.find {
            it.aswitch?.inPort && it.aswitch?.outPort
        } ?: assumeTrue("Wasn't able to find suitable ISL", false)
        def isls = northbound.getAllLinks()
        int islCost = islUtils.getIslInfo(isls, isl).get().cost
        assert islUtils.getIslInfo(isls, isl.reversed).get().cost == islCost
        northbound.updateLinkProps([isl, isl.reversed].collect { islUtils.toLinkProps(it, [cost: islCost.toString()]) })

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

    @Tags(VIRTUAL)
    def "ISL cost is NOT increased due to deactivating/activating switch"() {
        given: "A switch"
        def sw = topology.getActiveSwitches().first()

        and: "Cost of related ISLs"
        def swIsls = topology.getRelatedIsls(sw)
        def swIslsCostMap = swIsls.collectEntries { isl ->
            [isl, islUtils.getIslInfo(isl).get().cost]
        }

        when: "Deactivate the switch"
        lockKeeper.knockoutSwitch(sw)
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            assert northbound.getSwitch(sw.dpId).state == SwitchChangeType.DEACTIVATED
            def links = northbound.getAllLinks()
            swIsls.each { assert islUtils.getIslInfo(links, it).get().state == IslChangeType.FAILED }
        }

        and: "Activate the switch"
        lockKeeper.reviveSwitch(sw)
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            assert northbound.getSwitch(sw.dpId).state == SwitchChangeType.ACTIVATED
            def links = northbound.getAllLinks()
            swIsls.each { assert islUtils.getIslInfo(links, it).get().state == IslChangeType.DISCOVERED }
        }

        then: "ISL cost is not increased"
        swIsls.each { isl ->
            assert islUtils.getIslInfo(isl).get().cost == swIslsCostMap[isl]
        }
    }
}
