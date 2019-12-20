package org.openkilda.functionaltests.spec.links

import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.info.event.SwitchChangeType

import org.springframework.beans.factory.annotation.Value

import java.time.Instant

class UnstableIslSpec extends HealthCheckSpecification {

    @Value('${pce.isl.cost.when.unstable}')
    int islUnstableCost

    @Value('${isl.unstable.timeout.sec}')
    int islUnstableTimeoutSec

    def setupOnce() {
        database.resetCosts()  // reset cost on all links before tests
    }

    //'ISL with BFD session' case is covered in BfdSpec. Spoiler: it should act the same and don't change cost at all.
    def "ISL is NOT considered 'unstable' due to failing connection between switches (not port down)"() {
        given: "ISL going through a-switch with link props created"
        def isl = topology.islsForActiveSwitches.find {
            it.aswitch?.inPort && it.aswitch?.outPort
        } ?: assumeTrue("Wasn't able to find suitable ISL", false)

        when: "Remove a-switch rules to break link between switches"
        def rulesToRemove = [isl.aswitch, isl.aswitch.reversed]
        lockKeeper.removeFlows(rulesToRemove)

        then: "Status of forward and reverse ISLs becomes 'FAILED'"
        Wrappers.wait(discoveryTimeout * 1.5 + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == FAILED
        }

        and: "Isl is not being 'unstable'"
        [isl, isl.reversed].each { assert database.getIslTimeUnstable(it) == null }

        when: "Add a-switch rules to restore connection"
        lockKeeper.addFlows(rulesToRemove)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == DISCOVERED
        }

        then: "Isl is not being 'unstable'"
        [isl, isl.reversed].each { assert database.getIslTimeUnstable(it) == null }
    }

    @Tags(VIRTUAL)
    def "ISL is not considered unstable after deactivating/activating switch"() {
        given: "A switch"
        def sw = topology.getActiveSwitches().first()

        and: "Cost of related ISLs"
        def swIsls = topology.getRelatedIsls(sw)

        when: "Deactivate the switch"
        def blockData = lockKeeper.knockoutSwitch(sw, mgmtFlManager)
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            assert northbound.getSwitch(sw.dpId).state == SwitchChangeType.DEACTIVATED
            def links = northbound.getAllLinks()
            swIsls.each { assert islUtils.getIslInfo(links, it).get().state == FAILED }
        }

        then: "Switch ISL is not 'unstable'"
        [swIsls[0], swIsls[0].reversed].each { assert database.getIslTimeUnstable(it) == null }

        when: "Activate the switch"
        lockKeeper.reviveSwitch(sw, blockData)
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            assert northbound.getSwitch(sw.dpId).state == SwitchChangeType.ACTIVATED
            def links = northbound.getAllLinks()
            swIsls.each { assert islUtils.getIslInfo(links, it).get().state == DISCOVERED }
        }

        then: "Switch ISL is not 'unstable'"
        [swIsls[0], swIsls[0].reversed].each { assert database.getIslTimeUnstable(it) == null }
    }

    def "ISL is marked as 'unstable' after port down and system takes it into account during flow creation"() {
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
        [islToBreak, islToBreak.reversed].each { assert database.getIslTimeUnstable(it) == null }
        antiflap.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        Wrappers.wait(WAIT_OFFSET) { assert islUtils.getIslInfo(islToBreak).get().state == FAILED }
        [islToBreak, islToBreak.reversed].each { assert database.getIslTimeUnstable(it) != null }
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
        flowHelperV2.deleteFlow(flow.flowId)
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != FAILED }
        }
        database.resetCosts()
    }
}
