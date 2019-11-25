package org.openkilda.functionaltests.spec.links

import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.info.event.SwitchChangeType

import org.springframework.beans.factory.annotation.Value
import spock.lang.Unroll

import java.time.Instant

@Tags([LOW_PRIORITY])
class IslCostSpec extends HealthCheckSpecification {

    @Value('${pce.isl.cost.when.unstable}')
    int islUnstableCost

    @Value('${isl.unstable.timeout.sec}')
    int islUnstableTimeoutSec

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
        antiflap.portDown(isl.srcSwitch.dpId, isl.srcPort)

        then: "ISL status becomes 'FAILED'"
        Wrappers.wait(WAIT_OFFSET) { assert islUtils.getIslInfo(isl).get().state == FAILED }

        and: "Cost of forward and reverse ISLs after bringing port down is increased"
        def isls = northbound.getAllLinks()
        islUtils.getIslInfo(isls, isl).get().cost == islCost
        islUtils.getIslInfo(isls, isl.reversed).get().cost == islCost

        and: "Cost on corresponding link props is not increased as well"
        northbound.getAllLinkProps()*.props.cost == [islCost, islCost]*.toString()

        and: "Bring port up on the source switch and reset costs"
        antiflap.portUp(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert islUtils.getIslInfo(isl).get().state == DISCOVERED
        }
        northbound.deleteLinkProps(northbound.getAllLinkProps())

        cleanup:
        database.resetCosts()

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
            assert islUtils.getIslInfo(links, isl).get().state == FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == FAILED
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
            assert islUtils.getIslInfo(links, isl).get().state == DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == DISCOVERED
        }

        cleanup:
        database.resetCosts()
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
            swIsls.each { assert islUtils.getIslInfo(links, it).get().state == FAILED }
        }

        and: "Activate the switch"
        lockKeeper.reviveSwitch(sw)
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            assert northbound.getSwitch(sw.dpId).state == SwitchChangeType.ACTIVATED
            def links = northbound.getAllLinks()
            swIsls.each { assert islUtils.getIslInfo(links, it).get().state == DISCOVERED }
        }

        then: "ISL cost is not increased"
        swIsls.each { isl ->
            assert islUtils.getIslInfo(isl).get().cost == swIslsCostMap[isl]
        }

        cleanup:
        database.resetCosts()
    }

    def "System takes isl time_unstable info into account while creating a flow"() {
        given: "Two active neighboring switches with two parallel links"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            it.paths.findAll { it.size() == 2 }.size() > 1
        } ?: assumeTrue("No suiting switches found", false)

        and: "Two possible paths for further manipulation with them"
        def firstPath = switchPair.paths.min { it.size() }
        def secondPath = switchPair.paths.findAll { it != firstPath }.min { it.size() }
        def altPaths = switchPair.paths.findAll { it != firstPath && it != secondPath }

        and: "All alternative paths are unavailable (bring ports down on the srcSwitch)"
        List<PathNode> broughtDownPorts = []
        altPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            antiflap.portDown(src.switchId, src.portNo)
        }
        Wrappers.wait(antiflapMin + WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == FAILED
            }.size() == broughtDownPorts.size() * 2
        }

        and: "First path is unstable (due to bringing port down/up)"
        // after bringing port down/up, the isl will be marked as unstable by updating the 'time_unstable' field in DB
        def islToBreak = pathHelper.getInvolvedIsls(firstPath).first()
        antiflap.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        Wrappers.wait(WAIT_OFFSET) { assert islUtils.getIslInfo(islToBreak).get().state == FAILED }
        antiflap.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        Wrappers.wait(WAIT_OFFSET) { assert islUtils.getIslInfo(islToBreak).get().state == DISCOVERED }

        and: "Cost of stable path more preferable than the cost of unstable path"
        def involvedIslsInUnstablePath = pathHelper.getInvolvedIsls(firstPath)
        def costOfUnstablePath = involvedIslsInUnstablePath.sum {
            northbound.getLink(it).cost ?: 700
        } + islUnstableCost
        def involvedIslsInStablePath = pathHelper.getInvolvedIsls(secondPath)
        def costOfStablePath = involvedIslsInStablePath.sum { northbound.getLink(it).cost ?: 700 }
        // result after performing 'if' condition: costOfStablePath - costOfUnstablePath = 1
        if ((costOfUnstablePath - costOfStablePath) > 0) {
            def islToUpdate = involvedIslsInStablePath[0]
            def currentCostOfIsl = northbound.getLink(islToUpdate).cost
            def newCost = ((costOfUnstablePath - costOfStablePath - 1) + currentCostOfIsl).toString()
            northbound.updateLinkProps([islUtils.toLinkProps(islToUpdate, ["cost": newCost])])
        } else {
            def islToUpdate = involvedIslsInUnstablePath[0]
            def currentCostOfIsl = northbound.getLink(islToUpdate).cost
            def newCost = ((costOfStablePath - costOfUnstablePath + 1) + currentCostOfIsl).toString()
            northbound.updateLinkProps([islUtils.toLinkProps(islToUpdate, ["cost": newCost])])
        }

        when: "Create a flow"
        def flow = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(flow)

        then: "Flow is created on the stable path(secondPath)"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert PathHelper.convert(northbound.getFlowPath(flow.flowId)) == secondPath
        }

        when: "Mark first path as stable(update the 'time_unstable' field in db)"
        def newTimeUnstable = Instant.now() - (islUnstableTimeoutSec + WAIT_OFFSET)
        [islToBreak, islToBreak.reversed].each { database.updateIslTimeUnstable(it, newTimeUnstable) }

        and: "Reroute the flow"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            with(northboundV2.rerouteFlow(flow.flowId)) {
                it.rerouted
            }
        }

        then: "Flow is rerouted"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert PathHelper.convert(northbound.getFlowPath(flow.flowId)) == firstPath
        }

        and: "Restore topology, delete the flow and reset costs"
        broughtDownPorts.each { antiflap.portUp(it.switchId, it.portNo) }
        flowHelper.deleteFlow(flow.flowId)
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != FAILED }
        }
        database.resetCosts()
    }
}
