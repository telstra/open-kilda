package org.openkilda.functionaltests.spec.switches

import org.openkilda.functionaltests.error.SwitchNotFoundExpectedError

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOCKKEEPER
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.SwitchChangeType.ACTIVATED
import static org.openkilda.messaging.info.event.SwitchChangeType.DEACTIVATED
import static org.openkilda.testing.Constants.DUMMY_SW_IP_1
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.thread.LoopTask
import org.openkilda.testing.tools.SoftAssertions
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Isolated
import spock.lang.Narrative
import spock.lang.Shared

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Narrative("""
Every switch can be connected to multiple floodlights. Floodlight can be either 'read-only'(stats floodlights)
or 'read-write'(management floodlights). System chooses one 'master' fl between all RW fls.
All switch floodlights can be checked via 'GET /api/v2/switches/{switchId}/connections'
""")
@Isolated
class MultiFloodlightsSpec extends HealthCheckSpecification {
    @Shared ExecutorService executor = Executors.newFixedThreadPool(2)

    @Tidy
    def "Switch remains online only if at least one of multiple RW floodlights is available"() {
        given: "Switch simultaneously connected to 2 management floodlights"
        def cleanupActions = []
        def sw = topology.switches.find { flHelper.filterRegionsByMode(it.regions, RW).size() == 2 }
        assumeTrue(sw.asBoolean(), "Require a switch with 2 active regions")

        and: "Background observer monitoring the state of switch and its ISLs"
        def relatedIsls = topology.getRelatedIsls(sw).collectMany { [it, it.reversed] }
        def islObserver = new LoopTask({
            def soft = new SoftAssertions()
            relatedIsls.each { isl -> soft.checkSucceeds { assert northbound.getLink(isl).state == DISCOVERED } }
            soft.verify()
        })
        def swObserver = new LoopTask({ assert northbound.getSwitch(sw.dpId).state == ACTIVATED })
        def islObserverFuture = executor.submit(islObserver, "ok")
        def swObserverFuture = executor.submit(swObserver, "ok")

        when: "Switch loses connection to one of the regions"
        def knockout1 = lockKeeper.knockoutSwitch(sw, [flHelper.filterRegionsByMode(sw.regions, RW)[0]])
        cleanupActions << { lockKeeper.reviveSwitch(sw, knockout1) }

        then: "Switch can still return its rules"
        Wrappers.retry(2, 0.5) { !northbound.getSwitchRules(sw.dpId).flowEntries.empty }

        when: "Broken connection gets fixed, but the second floodlight loses connection to kafka"
        cleanupActions.pop().call() //lockKeeper.reviveSwitch(sw, knockout1)
        TimeUnit.SECONDS.sleep(discoveryInterval) //let discovery packets to come, don't want a timeout failure
        lockKeeper.knockoutFloodlight(sw.regions[1])
        cleanupActions << { lockKeeper.reviveFloodlight(sw.regions[1]) }

        then: "Switch can still return its rules"
        Wrappers.retry(2, 0.5) { !northbound.getSwitchRules(sw.dpId).flowEntries.empty }

        and: "All this time switch monitor saw switch as Active"
        swObserver.stop()
        swObserverFuture.get()

        when: "Switch loses connection to the last active region"
        def knockout2 = lockKeeper.knockoutSwitch(sw, [flHelper.filterRegionsByMode(sw.regions, RW)[0]])
        cleanupActions << { lockKeeper.reviveSwitch(sw, knockout2) }

        then: "Switch is marked as inactive"
        wait(WAIT_OFFSET) {
            northbound.getSwitch(sw.dpId).state == DEACTIVATED
        }

        when: "Try getting switch rules"
        northbound.getSwitchRules(sw.dpId)

        then: "Human readable error is returned"
        def e = thrown(HttpClientErrorException)
        new SwitchNotFoundExpectedError(
                "Switch $sw.dpId was not found", ~/The switch was not found when requesting a rules dump./).matches(e)

        when: "Broken region restores connection to kafka"
        cleanupActions.pop().call() //lockKeeper.reviveFloodlight(sw.regions[1])

        then: "Switch becomes activated and can respond"
        wait(WAIT_OFFSET) { assert northbound.getSwitch(sw.dpId).state == ACTIVATED }
        !northbound.getSwitchRules(sw.dpId).flowEntries.empty

        when: "Switch restores connection to the other region"
        cleanupActions.pop().call() //lockKeeper.reviveSwitch(sw, knockout2)

        then: "Switch can still return its rules and remains active"
        !northbound.getSwitchRules(sw.dpId).flowEntries.empty
        northbound.getSwitch(sw.dpId).state == ACTIVATED

        and: "ISL monitor reports that all switch-related ISLs have been Up all the time"
        islObserver.stop()
        islObserverFuture.get()

        cleanup:
        islObserver && islObserver.stop()
        cleanupActions.each { it() }
        wait(WAIT_OFFSET) { northbound.getAllSwitches().each { assert it.state == ACTIVATED } }
    }

    @Tidy
    @Tags([LOCKKEEPER])
    def "System supports case when switch uses different networks to connect to FLs, i.e. has diff ips"() {
        given: "A switch with at least 2 regions available"
        def sw = topology.activeSwitches.find { it.regions.size() >= 2 }
        assumeTrue(sw.asBoolean(), "Couldn't find a switch with at least 2 regions available")

        and: "The switch's ip in first region is different from other"
        def originalSwIp = sw.nbFormat().getAddress()
        def region = sw.getRegions().first()
        lockKeeper.changeSwIp(region, originalSwIp, DUMMY_SW_IP_1)

        and: "The switch is currently disconnected"
        def blockData = switchHelper.knockoutSwitch(sw, RW)

        when: "Test switch gets connected to both regions"
        switchHelper.reviveSwitch(sw, blockData)

        then: "Get switch connections API returns different ips for regions"
        def connections = northboundV2.getSwitchConnections(sw.dpId)
        connections.connections.each {
            assert it.switchAddress.startsWith(DUMMY_SW_IP_1) == (it.regionName == region)
        }

        cleanup:
        region && lockKeeper.cleanupIpChanges(region)
        sw && switchHelper.reviveSwitch(sw, switchHelper.knockoutSwitch(sw, RW))
    }
}
