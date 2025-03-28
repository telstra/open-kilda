package org.openkilda.functionaltests.spec.links

import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.helpers.model.Isls.breakIsls
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory

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
        isls.all().resetCostsInDb()
    }

    //'ISL with BFD session' case is covered in BfdSpec. Spoiler: it should act the same and don't change cost at all.
    def "ISL is NOT considered 'unstable' due to failing connection between switches (not port down)"() {
        given: "ISL going through a-switch with link props created"
        def isl = isls.all().withASwitch().first()
        when: "Remove a-switch rules to break link between switches"
        def rulesToRemove = [isl.getASwitch(), isl.getASwitch().reversed]
        aSwitchFlows.removeFlows(rulesToRemove)

        then: "Status of forward and reverse ISLs becomes 'FAILED'"
        isl.waitForStatus(FAILED, discoveryTimeout * 1.5 + WAIT_OFFSET)

        and: "Isl is not being 'unstable'"
        [isl, isl.reversed].each { assert it.getTimeUnstableFromDb() == null }

        when: "Add a-switch rules to restore connection"
        aSwitchFlows.addFlows(rulesToRemove)
        isl.waitForStatus(DISCOVERED, discoveryInterval + WAIT_OFFSET)

        then: "Isl is not being 'unstable'"
        [isl, isl.reversed].each { assert it.getTimeUnstableFromDb() == null }
    }

    @Tags(SWITCH_RECOVER_ON_FAIL)
    def "ISL is not considered unstable after deactivating/activating switch"() {
        //Switches with roundtrip isl latency will not have ISLs failed given that round trip rules remain installed
        given: "A switch that does not support round trip isl latency"
        def sw = switches.all().withoutRtlSupport().random()

        and: "Cost of related ISLs"
        def swIsls = isls.all().relatedTo(sw).getListOfIsls()

        when: "Deactivate the switch"
        def blockData = sw.knockout(RW, true)

        then: "Switch ISL is not 'unstable'"
        [swIsls[0], swIsls[0].reversed].each { assert it.getTimeUnstableFromDb() == null }

        when: "Activate the switch"
        sw.revive(blockData, true)

        then: "Switch ISL is not 'unstable'"
        [swIsls[0], swIsls[0].reversed].each { assert it.getTimeUnstableFromDb() == null }
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "ISL is marked as 'unstable' after port down and system takes it into account during flow creation"() {
        given: "Two active neighboring switches with two parallel links"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNIslsBetweenNeighbouringSwitches(2).random()

        and: "Two possible paths for further manipulation with them"
        //2 nodes for neighbouring switches, min 2 available direct paths(defined by selected swPair)
        def availableShortestPaths = switchPair.retrievePathsWithNodesCount(2)
        def firstPathIsls = isls.all().findInPath(availableShortestPaths.first())
        def secondPathIsls = isls.all().findInPath(availableShortestPaths.last())

        and: "All alternative paths are unavailable (bring ports down on the srcSwitch)"
        def altPathsIsls = isls.all().relatedTo(switchPair.src)
                .excludeIsls(firstPathIsls + secondPathIsls).getListOfIsls()
        breakIsls(altPathsIsls)

        and: "First path is unstable (due to bringing port down/up)"
        // after bringing port down/up, the isl will be marked as unstable by updating the 'time_unstable' field in DB
        def islToBreak = firstPathIsls.first()
        islToBreak.breakIt()

        [islToBreak, islToBreak.reversed].each { assert it.getTimeUnstableFromDb() != null }

        islToBreak.restore()

        and: "Cost of stable path is more preferable than the cost of unstable path (before penalties)"
        def costOfUnstablePath = firstPathIsls.sum { it.getNbDetails().cost ?: 700 } + islUnstableCost
        def costOfStablePath = secondPathIsls.sum { it.getNbDetails().cost ?: 700 }
        // result after performing 'if' condition: costOfStablePath - costOfUnstablePath = 1
        def islToUpdate = secondPathIsls[0]
        def currentCostOfIsl = islToUpdate.getNbDetails().cost
        def addition = (costOfUnstablePath - costOfStablePath) > 0 ? costOfUnstablePath - costOfStablePath - 1 :
                costOfStablePath - costOfUnstablePath + 1
        Integer newCost = addition + currentCostOfIsl
        islToUpdate.updateCost(newCost)
        Wrappers.wait(WAIT_OFFSET) { assert islToUpdate.getNbDetails().cost == newCost.toInteger() }

        when: "Create a flow"
        def flow = flowFactory.getRandom(switchPair)

        then: "Flow is created on the stable path(secondPath)"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert isls.all().findInPath(flow.retrieveAllEntityPaths()) == secondPathIsls
        }

        when: "Mark first path as stable(update the 'time_unstable' field in db)"
        def newTimeUnstable = Instant.now() - (islUnstableTimeoutSec + WAIT_OFFSET)
        [islToBreak, islToBreak.reversed].each { it.updateTimeUnstable(newTimeUnstable) }

        and: "Reroute the flow"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            with(flow.reroute()) {
                it.rerouted
            }
        }

        then: "Flow is rerouted"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert isls.all().findInPath(flow.retrieveAllEntityPaths()) == firstPathIsls
        }
    }
}
