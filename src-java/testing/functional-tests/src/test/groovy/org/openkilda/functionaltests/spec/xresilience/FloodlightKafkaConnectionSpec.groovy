package org.openkilda.functionaltests.spec.xresilience

import static groovyx.gpars.dataflow.Dataflow.task
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.helpers.Wrappers.timedLoop
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.messaging.payload.flow.FlowState

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Isolated
import spock.lang.Shared

import java.util.concurrent.TimeUnit

@Isolated
class FloodlightKafkaConnectionSpec extends HealthCheckSpecification {
    static final int PERIODIC_SYNC_TIME = 60

    @Autowired
    @Shared
    FlowFactory flowFactory

    @Value('${floodlight.alive.timeout}')
    int floodlightAliveTimeout
    @Value('${antiflap.cooldown}')
    int antiflapCooldown

    def "System properly handles ISL statuses during connection problems between Floodlights and Kafka"() {
        setup: "All switches that have multiple management floodlights now remain with only 1"
        def activeSwitches = switches.all().getListOfSwitches()
        def updatedRegions = activeSwitches.collectEntries{ [(it.switchId): it.regions] }
        def knockoutData = []
        activeSwitches.eachWithIndex { switchExtended, i ->
            def rwRegions = flHelper.filterRegionsByMode(switchExtended.regions, RW)
            def otherRegions = switchExtended.regions - rwRegions
            def regionToStay = rwRegions[i % rwRegions.size()]
            def regionsToDc = rwRegions - regionToStay
            knockoutData << [(switchExtended.sw): switchExtended.knockout(regionsToDc)]
            updatedRegions[switchExtended.switchId] = [regionToStay] + otherRegions
        }
        assumeTrue(updatedRegions.values().flatten().unique().size() > 1,
"Can be run only if there are switches in 2+ regions")

        and: "Pick a region to break, find which isls are between regions"
        def regionToBreak = flHelper.fls.findAll{ it.mode == RW }*.region.first()
        def islsBetweenRegions = isls.all().getListOfIsls().findAll {
            [it.srcSwId, it.dstSwId].any { regionToBreak in updatedRegions[it] } &&
                    updatedRegions[it.srcSwId] != updatedRegions[it.dstSwId]
        }

        when: "Region 1 controller loses connection to Kafka"
        lockKeeper.knockoutFloodlight(regionToBreak)
        def flOut = true

        then: "Non-rtl links between failed region and alive regions fail due to discovery timeout"
        def rtlSupportedSws = switches.all().withRtlSupport().getListOfSwitches()
        def nonRtlTransitIsls = islsBetweenRegions.findAll { isl ->
            [isl.srcSwId, isl.dstSwId].any { swId -> swId !in rtlSupportedSws.switchId }
        }
        def nonRtlShouldFail = task {
            wait(WAIT_OFFSET + discoveryTimeout) {
                nonRtlTransitIsls.forEach { assert it.getNbDetails().state == FAILED }
            }
        }

        and: "Right before controller alive timeout: switches are still active"
        and: "links inside regions are discovered"
        and: "rtl links between regions are discovered"
        double interval = floodlightAliveTimeout * 0.4
        def linksToRemainAlive = isls.all().excludeIsls(nonRtlTransitIsls).getListOfIsls()
        timedLoop(floodlightAliveTimeout - interval) {
            assert northbound.activeSwitches.size() == activeSwitches.size()
            linksToRemainAlive.each { assert it.getNbDetails().state == DISCOVERED }
            sleep(500)
        }

        and: "After controller alive timeout switches in broken region become inactive but links are still discovered"
        wait(interval + WAIT_OFFSET) {
            assert northbound.activeSwitches.size() == activeSwitches.findAll {
                regionToBreak !in updatedRegions[it.switchId]}.size()
        }
        linksToRemainAlive.each { assert it.getNbDetails().state == DISCOVERED }

        when: "System remains in this state for discovery timeout for ISLs"
        TimeUnit.SECONDS.sleep(discoveryTimeout + 1)
        nonRtlShouldFail.get()

        then: "All links except for non-rtl transit ones are still discovered"
        linksToRemainAlive.each { assert it.getNbDetails().state == DISCOVERED }

        when: "Controller restores connection to Kafka"
        lockKeeper.reviveFloodlight(regionToBreak)
        flOut = false

        then: "All links are discovered and switches become active"
        wait(PERIODIC_SYNC_TIME) {
            assert northbound.getActiveLinks().size() == isls.all().getListOfIsls().size() * 2
            assert northbound.activeSwitches.size() == activeSwitches.size()
        }

        and: "System is able to successfully create a valid flow between regions"
        def swPair = switchPairs.all().getSwitchPairs().find { pair ->
            [pair.src, pair.dst].any { regionToBreak in updatedRegions[it.switchId] }  &&
                    updatedRegions[pair.src.switchId] != updatedRegions[pair.dst.switchId]
        }
        def flow = flowFactory.getBuilder(swPair).build().sendCreateRequest()
        wait(WAIT_OFFSET * 2) {
            //FL may be a bit laggy right after comming up, so this may take a bit longer than usual
            assert flow.retrieveFlowStatus().status == FlowState.UP }
        flow.validateAndCollectDiscrepancies().isEmpty()

        cleanup:
        nonRtlShouldFail?.join()
        knockoutData.each { it.each { sw, data -> lockKeeper.reviveSwitch(sw, data) } }
        if(flOut) {
            lockKeeper.reviveFloodlight(regionToBreak)
            wait(PERIODIC_SYNC_TIME) {
                assert northbound.activeSwitches.size() == switches.all().getListOfSwitches().size()
                assert northbound.getAllLinks().size() == isls.all().getListOfIsls().size() * 2
            }
        }
    }

    def "System can detect switch port changes if they happen while Floodlight was disconnected after it reconnects"() {
        when: "Controllers lose connection to kafka"
        def regions = flHelper.fls*.region
        regions.each { lockKeeper.knockoutFloodlight(it) }
        def regionsOut = true
        wait(floodlightAliveTimeout + WAIT_OFFSET) { assert northbound.activeSwitches.size() == 0 }

        and: "Switch port for certain ISL goes down"
        def isl = isls.all().withASwitch().first()
        //port down on A-switch will lead to a port down on a connected Kilda switch
        lockKeeper.portsDown([isl.getASwitch().inPort])

        and: "Controllers restore connection to kafka"
        regions.each { lockKeeper.reviveFloodlight(it) }
        regionsOut = false

        then: "System detects that certain port has been brought down and fails the related link"
        isl.waitForStatus(FAILED, WAIT_OFFSET)

        cleanup:
        regionsOut && regions.each { lockKeeper.reviveFloodlight(it) }
        lockKeeper.portsUp([isl.getASwitch().inPort])
        wait(WAIT_OFFSET) { assert northbound.activeSwitches.size() == switches.all().getListOfSwitches().size() }
        isl.waitForStatus(DISCOVERED)
        isls.all().resetCostsInDb()
    }

    def "System can detect switch state changes if they happen while Floodlight was disconnected after it reconnects"() {
        when: "Controllers lose connection to kafka"
        def regions = flHelper.fls*.region
        regions.each { lockKeeper.knockoutFloodlight(it) }
        def regionsOut = true
        wait(floodlightAliveTimeout + WAIT_OFFSET) { assert northbound.activeSwitches.size() == 0 }

        and: "Switch loses connection to mgmt controllers"
        def swToManipulate = switches.all().first()
        def knockoutData = lockKeeper.knockoutSwitch(swToManipulate.sw, RW)

        and: "Controllers restore connection to kafka"
        regions.each { lockKeeper.reviveFloodlight(it) }
        regionsOut = false

        then: "System detects that disconnected switch is no longer active"
        def otherSwitches = switches.all().getListOfSwitches().findAll { it.switchId != swToManipulate.switchId }
        wait(WAIT_OFFSET) {
            assert northbound.activeSwitches*.switchId.sort { it.toLong() } == otherSwitches*.switchId.sort { it.toLong() }
        }
        swToManipulate.getDetails().state == SwitchChangeType.DEACTIVATED

        when: "Reconnect the switch back"
        lockKeeper.reviveSwitch(swToManipulate.sw, knockoutData)
        knockoutData = null

        then: "Switch is Active again"
        wait(WAIT_OFFSET) {
            assert swToManipulate.getDetails().state == SwitchChangeType.ACTIVATED
        }

        cleanup:
        regionsOut && regions.each { lockKeeper.reviveFloodlight(it) }
        knockoutData && lockKeeper.reviveSwitch(swToManipulate.sw, knockoutData)
        wait(WAIT_OFFSET) { assert northbound.activeSwitches.size() == switches.all().getListOfSwitches().size() }
        wait(WAIT_OFFSET + discoveryInterval + antiflapCooldown) {
            northbound.getAllLinks().findAll { it.state == FAILED }.isEmpty()
        }
        isls.all().resetCostsInDb()
    }
}
