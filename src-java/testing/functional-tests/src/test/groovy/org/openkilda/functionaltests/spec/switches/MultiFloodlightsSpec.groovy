package org.openkilda.functionaltests.spec.switches

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.SwitchNotFoundExpectedError
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

import static org.openkilda.functionaltests.extension.tags.Tag.LOCKKEEPER
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.SwitchChangeType.ACTIVATED
import static org.openkilda.messaging.info.event.SwitchChangeType.DEACTIVATED
import static org.openkilda.testing.Constants.DUMMY_SW_IP_1
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

@Narrative("""
Every switch can be connected to multiple floodlights. Floodlight can be either 'read-only'(stats floodlights)
or 'read-write'(management floodlights). System chooses one 'master' fl between all RW fls.
All switch floodlights can be checked via 'GET /api/v2/switches/{switchId}/connections'
""")
@Isolated

class MultiFloodlightsSpec extends HealthCheckSpecification {
    @Shared ExecutorService executor = Executors.newFixedThreadPool(2)

    @Tags([SWITCH_RECOVER_ON_FAIL])
    def "Switch remains online only if at least one of multiple RW floodlights is available"() {
        given: "Switch simultaneously connected to 2 management floodlights"
        def cleanupActions = []
        def sw = switches.all().withConnectedToExactlyNManagementFls(2).first()

        and: "Background observer monitoring the state of switch and its ISLs"
        def relatedIsls = topology.getRelatedIsls(sw.switchId).collectMany { [it, it.reversed] }

        def islObserver = new LoopTask({
            def soft = new SoftAssertions()
            relatedIsls.each { isl -> soft.checkSucceeds { assert northbound.getLink(isl).state == DISCOVERED } }
            soft.verify()
        })
        def swObserver = new LoopTask({ assert sw.getDetails().state == ACTIVATED })
        def islObserverFuture = executor.submit(islObserver, "ok")
        def swObserverFuture = executor.submit(swObserver, "ok")

        when: "Switch loses connection to one of the regions"
        def knockout1 = sw.knockout([flHelper.filterRegionsByMode(sw.regions, RW)[0]])
        cleanupActions << { sw.revive(knockout1) }

        then: "Switch can still return its rules"
        Wrappers.retry(2, 0.5) { !sw.rulesManager.getRules().isEmpty() }

        when: "Broken connection gets fixed, but the second floodlight loses connection to kafka"
        cleanupActions.pop().call() //lockKeeper.reviveSwitch(sw, knockout1)
        TimeUnit.SECONDS.sleep(discoveryInterval) //let discovery packets to come, don't want a timeout failure
        lockKeeper.knockoutFloodlight(sw.regions[1])
        cleanupActions << { lockKeeper.reviveFloodlight(sw.regions[1]) }

        then: "Switch can still return its rules"
        Wrappers.retry(2, 0.5) { !sw.rulesManager.getRules().isEmpty() }

        and: "All this time switch monitor saw switch as Active"
        swObserver.stop()
        swObserverFuture.get()

        when: "Switch loses connection to the last active region"
        def knockout2 = sw.knockout([flHelper.filterRegionsByMode(sw.regions, RW)[0]])
        cleanupActions << { sw.revive(knockout2) }

        then: "Switch is marked as inactive"
        wait(WAIT_OFFSET) {
            sw.getDetails().state == DEACTIVATED
        }

        when: "Try getting switch rules"
        sw.rulesManager.getRules()

        then: "Human readable error is returned"
        def e = thrown(HttpClientErrorException)
        new SwitchNotFoundExpectedError(
                "Switch $sw.switchId was not found", ~/The switch was not found when requesting a rules dump./).matches(e)

        when: "Broken region restores connection to kafka"
        cleanupActions.pop().call() //lockKeeper.reviveFloodlight(sw.regions[1])

        then: "Switch becomes activated and can respond"
        wait(WAIT_OFFSET) { assert sw.getDetails().state == ACTIVATED }
        !sw.rulesManager.getRules().isEmpty()

        when: "Switch restores connection to the other region"
        cleanupActions.pop().call() //lockKeeper.reviveSwitch(sw, knockout2)

        then: "Switch can still return its rules and remains active"
        !sw.rulesManager.getRules().isEmpty()
        sw.getDetails().state == ACTIVATED

        and: "ISL monitor reports that all switch-related ISLs have been Up all the time"
        islObserver.stop()
        islObserverFuture.get()

        cleanup:
        islObserver && islObserver.stop()
        cleanupActions.each { it() }
        wait(WAIT_OFFSET) { northbound.getAllSwitches().each { assert it.state == ACTIVATED } }
    }

    @Tags([LOCKKEEPER, SWITCH_RECOVER_ON_FAIL])
    def "System supports case when switch uses different networks to connect to FLs, i.e. has diff ips"() {
        given: "A switch with at least 2 regions available"
        def switchToInteract = switches.all().withConnectedToAtLeastNFls(2).first()

        and: "The switch's ip in first region is different from other"
        def originalSwIp = switchToInteract.nbFormat().getAddress()
        def region = switchToInteract.getRegions().first()
        lockKeeper.changeSwIp(region, originalSwIp, DUMMY_SW_IP_1)

        and: "The switch is currently disconnected"
        def blockData = lockKeeper.knockoutSwitch(switchToInteract.sw, RW)
        wait(WAIT_OFFSET) {
            assert switchToInteract.getDetails().state == DEACTIVATED
        }

        when: "Test switch gets connected to both regions"
        switchToInteract.revive(blockData)

        then: "Get switch connections API returns different ips for regions"
        def connections = switchToInteract.getConnectedFloodLights()
        connections.each {
            assert it.switchAddress.startsWith(DUMMY_SW_IP_1) == (it.regionName == region)
        }

        cleanup:
        region && lockKeeper.cleanupIpChanges(region)
        //this additional knockout is required to revive switch with the correct IP
        switchToInteract && switchToInteract.revive(lockKeeper.knockoutSwitch(switchToInteract.sw, RW))
    }
}
