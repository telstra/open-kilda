package org.openkilda.functionaltests.spec.links

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.model.SwitchFeature.NOVIFLOW_COPY_FIELD
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.model.SwitchFeature

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Shared

import java.time.Instant

class UnstableIslSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    FlowFactory flowFactory

    @Value('${pce.isl.cost.when.unstable}')
    int islUnstableCost

    @Value('${isl.unstable.timeout.sec}')
    int islUnstableTimeoutSec

    def setupSpec() {
        database.resetCosts(topology.isls)  // reset cost on all links before tests
    }

    //'ISL with BFD session' case is covered in BfdSpec. Spoiler: it should act the same and don't change cost at all.
    def "ISL is NOT considered 'unstable' due to failing connection between switches (not port down)"() {
        given: "ISL going through a-switch with link props created"
        def isl = topology.islsForActiveSwitches.find {
            it.aswitch?.inPort && it.aswitch?.outPort
        } ?: assumeTrue(false, "Wasn't able to find suitable ISL")

        when: "Remove a-switch rules to break link between switches"
        def rulesToRemove = [isl.aswitch, isl.aswitch.reversed]
        aSwitchFlows.removeFlows(rulesToRemove)

        then: "Status of forward and reverse ISLs becomes 'FAILED'"
        Wrappers.wait(discoveryTimeout * 1.5 + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == FAILED
        }

        and: "Isl is not being 'unstable'"
        [isl, isl.reversed].each { assert database.getIslTimeUnstable(it) == null }

        when: "Add a-switch rules to restore connection"
        aSwitchFlows.addFlows(rulesToRemove)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == DISCOVERED
        }

        then: "Isl is not being 'unstable'"
        [isl, isl.reversed].each { assert database.getIslTimeUnstable(it) == null }
    }

    @Tags(SWITCH_RECOVER_ON_FAIL)
    def "ISL is not considered unstable after deactivating/activating switch"() {
        //Switches with roundtrip isl latency will not have ISLs failed given that round trip rules remain installed
        given: "A switch that does not support round trip isl latency"
        def sw = switches.all().getListOfSwitches().find { !it.getDbFeatures().contains(NOVIFLOW_COPY_FIELD)}
        assumeTrue(sw as boolean, "This test cannot be run if all switches support roundtrip is latency")

        and: "Cost of related ISLs"
        def swIsls = topology.getRelatedIsls(sw.switchId)

        when: "Deactivate the switch"
        def blockData = sw.knockout(RW, true)

        then: "Switch ISL is not 'unstable'"
        [swIsls[0], swIsls[0].reversed].each { assert database.getIslTimeUnstable(it) == null }

        when: "Activate the switch"
        sw.revive(blockData, true)

        then: "Switch ISL is not 'unstable'"
        [swIsls[0], swIsls[0].reversed].each { assert database.getIslTimeUnstable(it) == null }
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "ISL is marked as 'unstable' after port down and system takes it into account during flow creation"() {
        given: "Two active neighboring switches with two parallel links"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNIslsBetweenNeighbouringSwitches(2).random()
        def availablePaths = switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }

        and: "Two possible paths for further manipulation with them"
        def firstPathIsls = availablePaths.min { it.size() }
        def secondPathIsls = availablePaths.findAll { it != firstPathIsls }.min { it.size() }

        and: "All alternative paths are unavailable (bring ports down on the srcSwitch)"
        def altPathsIsls = topology.getRelatedIsls(switchPair.src.switchId) - firstPathIsls.first() - secondPathIsls.first()
        islHelper.breakIsls(altPathsIsls)

        and: "First path is unstable (due to bringing port down/up)"
        // after bringing port down/up, the isl will be marked as unstable by updating the 'time_unstable' field in DB
        def islToBreak = firstPathIsls.first()
        islHelper.breakIsl(islToBreak)
        [islToBreak, islToBreak.reversed].each { assert database.getIslTimeUnstable(it) != null }
        islHelper.restoreIsl(islToBreak)

        and: "Cost of stable path is more preferable than the cost of unstable path (before penalties)"
        def costOfUnstablePath = firstPathIsls.sum {
            northbound.getLink(it).cost ?: 700
        } + islUnstableCost
        def costOfStablePath = secondPathIsls.sum { northbound.getLink(it).cost ?: 700 }
        // result after performing 'if' condition: costOfStablePath - costOfUnstablePath = 1
        def islToUpdate = secondPathIsls[0]
        def currentCostOfIsl = northbound.getLink(islToUpdate).cost
        def addition = (costOfUnstablePath - costOfStablePath) > 0 ? costOfUnstablePath - costOfStablePath - 1 :
                costOfStablePath - costOfUnstablePath + 1
        def newCost = addition + currentCostOfIsl
        islHelper.updateIslsCost([islToUpdate], newCost)
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getLink(islToUpdate).cost == newCost.toInteger() }

        when: "Create a flow"
        def flow = flowFactory.getRandom(switchPair)

        then: "Flow is created on the stable path(secondPath)"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert flow.retrieveAllEntityPaths().getInvolvedIsls() == secondPathIsls
        }

        when: "Mark first path as stable(update the 'time_unstable' field in db)"
        def newTimeUnstable = Instant.now() - (islUnstableTimeoutSec + WAIT_OFFSET)
        [islToBreak, islToBreak.reversed].each { database.updateIslTimeUnstable(it, newTimeUnstable) }

        and: "Reroute the flow"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            with(flow.reroute()) {
                it.rerouted
            }
        }

        then: "Flow is rerouted"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert flow.retrieveAllEntityPaths().getInvolvedIsls() == firstPathIsls
        }
    }
}
