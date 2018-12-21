package org.openkilda.functionaltests.spec.northbound.switches

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.extension.rerun.Rerun
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.thread.PortBlinker
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.testing.model.topology.TopologyDefinition.Isl

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Narrative

import java.util.concurrent.TimeUnit

@Slf4j
@Narrative("""
Anti-flap feature is aimed to address the problem of blinking ports. Sometimes it happens that certain port on a switch
begins to blink for a very short period of time. If it is a short-time behavior, then we don't want to init a
port down event and reroute the flow. Otherwise we wait for 'antiflapWarmup' time and assign that blinking port a DOWN 
status. Then, when it comes back up it should remain in a stable 'up' state for at least 'antiflapCooldown' seconds to
actually become UP for the rest of the system.
Initially, port is considered 'flapping' if it changes status quicker than once in 'antiflap.min' seconds (port will
change status from UP to DOWN only after 'antiflap.min' in case of a single-time change of status)
""")
@Issue("https://github.com/telstra/open-kilda/issues/1729")
class PortAntiflapSpec extends BaseSpecification {

    @Value('${antiflap.min}')
    int antiflapMin

    @Value('${antiflap.warmup}')
    int antiflapWarmup

    @Value('${antiflap.cooldown}')
    int antiflapCooldown

    @Rerun(times = 10) //rerun is required to check the #1790 issue
    @Ignore("Due to https://github.com/telstra/open-kilda/issues/1790")
    def "Flapping port is brought down only after antiflap warmup and stable port is brought up only after cooldown \
timeout"() {
        given: "Switch, port and ISL related to that port"
        def sw = topology.activeSwitches.first()
        Isl isl = topology.islsForActiveSwitches.find { it.srcSwitch == sw || it.dstSwitch == sw }
        assert isl
        int islPort = isl.with { it.srcSwitch == sw ? it.srcPort : it.dstPort }

        when: "ISL port begins to blink"
        def blinker = new PortBlinker(northbound, sw, islPort)
        def untilWarmupEnds = { blinker.timeStarted.time + antiflapWarmup * 1000 - new Date().time }
        def untilCooldownEnds = { blinker.timeStopped.time + antiflapCooldown * 1000 - new Date().time }
        blinker.start()

        then: "Right before warmup timeout runs out the related ISL remains up"
        sleep(untilWarmupEnds() - 1000)
        islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED

        and: "After warmup timeout the related ISL goes down"
        Wrappers.wait(untilWarmupEnds() / 1000.0 + WAIT_OFFSET / 2) {
            islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED
        }

        and: "ISL remains down even after cooldown timeout"
        TimeUnit.SECONDS.sleep(antiflapCooldown + 1)
        islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED

        when: "Port stops flapping, ending in UP state"
        blinker.stop(true)

        then: "Right before cooldown timeout runs out the ISL remains down"
        sleep(untilCooldownEnds() - 1000)
        islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED

        and: "After cooldown timeout the ISL goes up"
        Wrappers.wait(untilCooldownEnds() / 1000.0 + WAIT_OFFSET / 2 + discoveryInterval) {
            islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED
        }

        cleanup:
        blinker?.isRunning() && blinker.stop(true)
    }

    def "Port goes down in 'antiflap.min' seconds if no flapping occurs"() {
        given: "Switch, port and ISL related to that port"
        def sw = topology.activeSwitches.first()
        Isl isl = topology.islsForActiveSwitches.find { it.srcSwitch == sw || it.dstSwitch == sw }
        int islPort = isl.with { it.srcSwitch == sw ? it.srcPort : it.dstPort }
        assert islPort

        when: "Port goes down"
        northbound.portDown(sw.dpId, islPort)

        then: "Related ISL goes down in about 'antiflapMin' seconds"
        Wrappers.wait(antiflapMin + 2) {
            islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED
        }

        and: "cleanup: Bring port up"
        northbound.portUp(sw.dpId, islPort)
        Wrappers.wait(antiflapCooldown + discoveryInterval + WAIT_OFFSET) {
            islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED
        }
    }
}
