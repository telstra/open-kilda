package org.openkilda.functionaltests.spec.resilience

import static groovyx.gpars.dataflow.Dataflow.task
import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.helpers.Wrappers.timedLoop
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.model.SwitchFeature

import org.springframework.beans.factory.annotation.Value

import java.util.concurrent.TimeUnit

class FloodlightKafkaConnectionSpec extends HealthCheckSpecification {
    static final int PERIODIC_SYNC_TIME = 60

    @Value('${floodlight.alive.timeout}')
    int floodlightAliveTimeout
    @Value('${floodlight.alive.interval}')
    int floodlightAliveInterval
    @Value('${antiflap.cooldown}')
    int antiflapCooldown

    def "System survives temporary connection outage between Floodlight and Kafka"() {
        setup: "Pick a region to break, find which isls are between regions"
        assumeTrue("This test requires at least 2 floodlight regions", mgmtFlManager.regions.size() > 1)
        def regionToBreak = mgmtFlManager.regions.first()
        def islsBetweenRegions = topology.islsForActiveSwitches.findAll {
            [it.srcSwitch, it.dstSwitch].any { it.region == regionToBreak } && it.srcSwitch.region != it.dstSwitch.region
        }

        when: "Region 1 controller loses connection to Kafka"
        lockKeeper.knockoutFloodlight(regionToBreak)
        def flOut = true

        then: "Non-rtl links between failed region and alive regions fail due to discovery timeout"
        def nonRtlTransitIsls = islsBetweenRegions.findAll { isl ->
            [isl.srcSwitch, isl.dstSwitch].any { !it.features.contains(SwitchFeature.NOVIFLOW_COPY_FIELD) }
        }
        def asyncWait = task {
            wait(WAIT_OFFSET + discoveryTimeout) {
                nonRtlTransitIsls.each { assert northbound.getLink(it).state == IslChangeType.FAILED }
            }
        }

        and: "Right before controller alive timeout: switches are still active"
        and: "links inside regions are discovered"
        and: "rtl links between regions are discovered"
        double interval = floodlightAliveTimeout * 0.4
        def linksToRemainAlive = topology.islsForActiveSwitches.findAll { !nonRtlTransitIsls.contains(it) }
        timedLoop(floodlightAliveTimeout - interval) {
            assert northbound.activeSwitches.size() == topology.activeSwitches.size()
            def isls = northbound.getAllLinks()
            linksToRemainAlive.each { assert islUtils.getIslInfo(isls, it).get().state == IslChangeType.DISCOVERED }
            sleep(500)
        }

        and: "After controller alive timeout switches in broken region become inactive but links are still discovered"
        wait(interval + WAIT_OFFSET) {
            assert northbound.activeSwitches.size() == topology.activeSwitches.findAll { it.region != regionToBreak }.size()
        }
        linksToRemainAlive.each { assert northbound.getLink(it).state == IslChangeType.DISCOVERED }

        when: "System remains in this state for discovery timeout for ISLs"
        TimeUnit.SECONDS.sleep(discoveryTimeout + 1)
        asyncWait.get()

        then: "All links except for non-rtl transit ones are still discovered"
        linksToRemainAlive.each { assert northbound.getLink(it).state == IslChangeType.DISCOVERED }

        when: "Controller restores connection to Kafka"
        lockKeeper.reviveFloodlight(regionToBreak)
        flOut = false

        then: "All links are discovered and switches become active"
        wait(PERIODIC_SYNC_TIME) {
            assert northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2
            assert northbound.activeSwitches.size() == topology.activeSwitches.size()
        }

        and: "System is able to successfully create a valid flow between regions"
        def swPair = topologyHelper.switchPairs.find { pair ->
            [pair.src, pair.dst].any { it.region == regionToBreak }  && pair.src.region != pair.dst.region
        }
        def flow = flowHelperV2.randomFlow(swPair)
        flowHelperV2.addFlow(flow)
        northbound.validateFlow(flow.flowId).each { assert it.asExpected }

        cleanup:
        asyncWait?.join()
        flow && flowHelperV2.deleteFlow(flow.flowId)
        if(flOut) {
            lockKeeper.reviveFloodlight(regionToBreak)
            wait(PERIODIC_SYNC_TIME) {
                assert northbound.activeSwitches.size() == topology.activeSwitches.size()
                assert northbound.getAllLinks().size() == topology.islsForActiveSwitches.size() * 2
            }
        }
    }

    def "System can detect switch changes if they happen while Floodlight was disconnected after it reconnects"() {
        when: "Controller loses connection to kafka"
        regions.each { lockKeeper.knockoutFloodlight(it) }
        wait(floodlightAliveTimeout + WAIT_OFFSET) { assert northbound.activeSwitches.size() == 0 }

        and: "Switch port for certain ISL goes down"
        def isl = topology.islsForActiveSwitches.find { it.aswitch?.inPort && it.aswitch?.outPort }
        //port down on A-switch will lead to a port down on a connected Kilda switch
        lockKeeper.portsDown([isl.aswitch.inPort])

        and: "Controller restores connection to kafka"
        regions.each { lockKeeper.reviveFloodlight(it) }

        then: "System detects that certain port has been brought down and fails the related link"
        wait(WAIT_OFFSET) {
            def isls = northbound.getAllLinks()
            assert islUtils.getIslInfo(isls, isl).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(isls, isl.reversed).get().state == IslChangeType.FAILED
        }

        and: "Cleanup: restore the broken link"
        lockKeeper.portsUp([isl.aswitch.inPort])
        wait(WAIT_OFFSET + discoveryInterval + antiflapCooldown) {
            def isls = northbound.getAllLinks()
            assert islUtils.getIslInfo(isls, isl).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(isls, isl.reversed).get().state == IslChangeType.DISCOVERED
        }
        database.resetCosts()
    }
}
