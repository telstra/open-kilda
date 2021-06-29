package org.openkilda.functionaltests.spec.server42

import static org.junit.Assume.assumeTrue
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.STATS_FROM_SERVER42_LOGGING_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.helpers.SwitchHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.model.system.FeatureTogglesDto
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import spock.lang.Shared

import java.util.concurrent.TimeUnit

@Slf4j
class Server42IslRttSpec extends HealthCheckSpecification {
    @Shared
    @Value('${opentsdb.metric.prefix}')
    String metricPrefix

    int islSyncWaitSeconds = 60
    int statsWaitSeconds = 4

    @Tidy
    def "Server42 ISL RTT rules are installed"() {
        given: "An active ISL with both switches having server42"
        def server42switchesDpIds = topology.getActiveServer42Switches()*.dpId
        def isl = topology.islsForActiveSwitches.find {
            it.srcSwitch.dpId in server42switchesDpIds && it.dstSwitch.dpId in server42switchesDpIds
        }
        assumeTrue("Was not able to find an ISL with a server42 connected", isl != null)

        when: "server42IslRtt feature toggle is set to true"
        def islRttFeatureStartState = changeIslRttToggle(true)
        and: "server42IslRtt is enabled on src and dst switches"
        def initialSwitchRtt = [isl.srcSwitch, isl.dstSwitch].collectEntries {
            [it, changeIslRttSwitch(it, true)]
        }

        then: "Server42 ISL RTT rules are installed"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            [isl.srcSwitch, isl.dstSwitch].each {
                assert northbound.getSwitchRules(it.dpId).flowEntries.findAll {
                    (it.cookie in [Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE, Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE]) ||
                            (new Cookie(it.cookie).getType() in [CookieType.SERVER_42_ISL_RTT_INPUT])
                }.size() == (northbound.getLinks(it.dpId, null, null, null).size() + 2)
            }
        }

        and: "Involved switches pass switch validation"
        [isl.srcSwitch, isl.dstSwitch].each { sw ->
            verifyAll(northbound.validateSwitch(sw.dpId)) {
                rules.missing.empty
                rules.excess.empty
                rules.misconfigured.empty
                meters.missing.empty
                meters.excess.empty
                meters.misconfigured.empty
            }
        }

        cleanup: "Revert system to original state"
        revertToOrigin(islRttFeatureStartState, initialSwitchRtt)
    }

    @Tidy
    def "ISL RTT stats is available for a new link"() {
        given: "An active ISL with both switches having server42"
        def server42switchesDpIds = topology.getActiveServer42Switches()*.dpId
        def isl = topology.islsForActiveSwitches.find {
            it.srcSwitch.dpId in server42switchesDpIds && it.dstSwitch.dpId in server42switchesDpIds
        }
        assumeTrue("Was not able to find an ISL with a server42 connected", isl != null)

        and: "server42IslRtt feature toggle is set to true"
        def islRttFeatureStartState = changeIslRttToggle(true)
        and: "server42IslRtt is enabled on src and dst switches"
        def initialSwitchRtt = [isl.srcSwitch, isl.dstSwitch].collectEntries {
            [it, changeIslRttSwitch(it, true)]
        }

        when: "Put the port down and delete the link"
        antiflap.portDown(isl.srcSwitch.dpId, isl.srcPort)
        TimeUnit.SECONDS.sleep(2) //receive any in-progress disco packets
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getLink(isl).actualState == FAILED
        }
        def response = northbound.deleteLink(islUtils.toLinkParameters(isl))

        then: "The link is actually deleted"
        response.size() == 2
        !islUtils.getIslInfo(isl)
        !islUtils.getIslInfo(isl.reversed)

        and: "Server42 ISL RTT rules of the deleted link are actually removed"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            [isl.srcSwitch, isl.dstSwitch].each {
                assert northbound.getSwitchRules(it.dpId).flowEntries.findAll {
                    (it.cookie in [Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE, Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE]) ||
                            (new Cookie(it.cookie).getType() in [CookieType.SERVER_42_ISL_RTT_INPUT])
                }.size() == (northbound.getLinks(it.dpId, null, null, null).size() + 2)
            }
        }

        and: "Involved switches pass switch validation"
        [isl.srcSwitch, isl.dstSwitch].each { sw ->
            verifyAll(northbound.validateSwitch(sw.dpId)) {
                rules.missing.empty
                rules.excess.empty
                rules.misconfigured.empty
                meters.missing.empty
                meters.excess.empty
                meters.misconfigured.empty
            }
        }

        when: "Wait for several seconds"
        TimeUnit.SECONDS.sleep(islSyncWaitSeconds)
        def checkpointTime = new Date()
        TimeUnit.SECONDS.sleep(statsWaitSeconds)

        then: "Expect no ISL RTT forward stats"
        otsdb.query(checkpointTime, metricPrefix + "isl.rtt",
                [src_switch: isl.srcSwitch.dpId.toOtsdFormat(),
                 src_port  : String.valueOf(isl.srcPort),
                 origin    : "server42"]).dps.isEmpty()

        and: "Expect no ISL RTT reverse stats"
        otsdb.query(checkpointTime, metricPrefix + "isl.rtt",
                [dst_switch: isl.dstSwitch.dpId.toOtsdFormat(),
                 dst_port  : String.valueOf(isl.dstPort),
                 origin    : "server42"]).dps.isEmpty()

        when: "Removed link becomes active again (port brought UP)"
        antiflap.portUp(isl.srcSwitch.dpId, isl.srcPort)

        then: "The link is rediscovered in both directions"
        Wrappers.wait(discoveryExhaustedInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl.reversed).get().state == DISCOVERED
            assert islUtils.getIslInfo(links, isl).get().state == DISCOVERED
        }

        when: "Wait for several seconds"
        TimeUnit.SECONDS.sleep(islSyncWaitSeconds)
        def portUpTime = new Date()
        TimeUnit.SECONDS.sleep(statsWaitSeconds)

        then: "ISL RTT forward stats is available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            def statsData = otsdb.query(portUpTime, metricPrefix + "isl.rtt",
                    [src_switch: isl.srcSwitch.dpId.toOtsdFormat(),
                     src_port  : String.valueOf(isl.srcPort),
                     origin    : "server42"]).dps
            assert statsData && !statsData.empty
        }

        and: "ISL RTT reverse stats is available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            def statsData = otsdb.query(portUpTime, metricPrefix + "isl.rtt",
                    [dst_switch: isl.dstSwitch.dpId.toOtsdFormat(),
                     dst_port  : String.valueOf(isl.dstPort),
                     origin    : "server42"]).dps
            assert statsData && !statsData.empty
        }

        and: "Server42 ISL RTT rules are installed"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            [isl.srcSwitch, isl.dstSwitch].each {
                assert northbound.getSwitchRules(it.dpId).flowEntries.findAll {
                    (it.cookie in [Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE, Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE]) ||
                            (new Cookie(it.cookie).getType() in [CookieType.SERVER_42_ISL_RTT_INPUT])
                }.size() == (northbound.getLinks(it.dpId, null, null, null).size() + 2)
            }
        }

        and: "Involved switches pass switch validation"
        [isl.srcSwitch, isl.dstSwitch].each { sw ->
            verifyAll(northbound.validateSwitch(sw.dpId)) {
                rules.missing.empty
                rules.excess.empty
                rules.misconfigured.empty
                meters.missing.empty
                meters.excess.empty
                meters.misconfigured.empty
            }
        }

        cleanup: "Revert system to original state"
        revertToOrigin(islRttFeatureStartState, initialSwitchRtt)
    }

    @Tidy
    def "ISL RTT stats is available only if both global and switch toggles are 'on'"() {
        given: "An active ISL with both switches having server42"
        def server42switchesDpIds = topology.getActiveServer42Switches()*.dpId
        def isl = topology.islsForActiveSwitches.find {
            it.srcSwitch.dpId in server42switchesDpIds && it.dstSwitch.dpId in server42switchesDpIds
        }
        assumeTrue("Was not able to find an ISL with a server42 connected", isl != null)

        when: "server42IslRtt feature toggle is turned off"
        def islRttFeatureStartState = changeIslRttToggle(false)

        and: "server42IslRtt is turned off on src and dst switches"
        def initialSwitchRtt = [isl.srcSwitch, isl.dstSwitch].collectEntries {
            [it, changeIslRttSwitch(it, false)]
        }

        and: "Wait for several seconds"
        TimeUnit.SECONDS.sleep(RULES_INSTALLATION_TIME)
        def checkpointTime = new Date()
        TimeUnit.SECONDS.sleep(statsWaitSeconds)

        then: "Expect no ISL RTT forward stats"
        otsdb.query(checkpointTime, metricPrefix + "isl.rtt",
                [src_switch: isl.srcSwitch.dpId.toOtsdFormat(),
                 src_port  : String.valueOf(isl.srcPort),
                 origin    : "server42"]).dps.isEmpty()

        and: "Expect no ISL RTT reverse stats"
        otsdb.query(checkpointTime, metricPrefix + "isl.rtt",
                [dst_switch: isl.dstSwitch.dpId.toOtsdFormat(),
                 dst_port  : String.valueOf(isl.dstPort),
                 origin    : "server42"]).dps.isEmpty()

        when: "Enable global server42IslRtt feature toggle"
        changeIslRttToggle(true)

        and: "Wait for several seconds"
        TimeUnit.SECONDS.sleep(RULES_INSTALLATION_TIME)
        checkpointTime = new Date()
        TimeUnit.SECONDS.sleep(statsWaitSeconds)

        then: "Expect no ISL RTT forward stats"
        otsdb.query(checkpointTime, metricPrefix + "isl.rtt",
                [src_switch: isl.srcSwitch.dpId.toOtsdFormat(),
                 src_port  : String.valueOf(isl.srcPort),
                 origin    : "server42"]).dps.isEmpty()

        and: "Expect no ISL RTT reverse stats"
        otsdb.query(checkpointTime, metricPrefix + "isl.rtt",
                [dst_switch: isl.dstSwitch.dpId.toOtsdFormat(),
                 dst_port  : String.valueOf(isl.dstPort),
                 origin    : "server42"]).dps.isEmpty()

        when: "Enable switch server42IslRtt toggle on src and dst"
        changeIslRttSwitch(isl.srcSwitch, true)
        changeIslRttSwitch(isl.dstSwitch, true)

        and: "Wait for several seconds"
        TimeUnit.SECONDS.sleep(RULES_INSTALLATION_TIME)
        checkpointTime = new Date()
        TimeUnit.SECONDS.sleep(statsWaitSeconds)

        then: "ISL RTT forward stats is available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            def statsData = otsdb.query(checkpointTime, metricPrefix + "isl.rtt",
                    [src_switch: isl.srcSwitch.dpId.toOtsdFormat(),
                     src_port  : String.valueOf(isl.srcPort),
                     origin    : "server42"]).dps
            assert statsData && !statsData.empty
        }

        and: "ISL RTT reverse stats is available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            def statsData = otsdb.query(checkpointTime, metricPrefix + "isl.rtt",
                    [dst_switch: isl.dstSwitch.dpId.toOtsdFormat(),
                     dst_port  : String.valueOf(isl.dstPort),
                     origin    : "server42"]).dps
            assert statsData && !statsData.empty
        }

        when: "Disable global server42IslRtt feature toggle"
        changeIslRttToggle(false)

        and: "Wait for several seconds"
        TimeUnit.SECONDS.sleep(RULES_INSTALLATION_TIME)
        checkpointTime = new Date()
        TimeUnit.SECONDS.sleep(statsWaitSeconds)

        then: "Expect no ISL RTT forward stats"
        otsdb.query(checkpointTime, metricPrefix + "isl.rtt",
                [src_switch: isl.srcSwitch.dpId.toOtsdFormat(),
                 src_port  : String.valueOf(isl.srcPort),
                 origin    : "server42"]).dps.isEmpty()

        and: "Expect no ISL RTT reverse stats"
        otsdb.query(checkpointTime, metricPrefix + "isl.rtt",
                [dst_switch: isl.dstSwitch.dpId.toOtsdFormat(),
                 dst_port  : String.valueOf(isl.dstPort),
                 origin    : "server42"]).dps.isEmpty()

        cleanup: "Revert system to original state"
        revertToOrigin(islRttFeatureStartState, initialSwitchRtt)
    }

    @Tidy
    def "ISL RTT stats is available if both endpoints are connected to the same server42 (same pop)"() {
        given: "An active ISL with both switches connected to the same server42 instance"
        def server42switchesDpIds = topology.getActiveServer42Switches()*.dpId
        def isl = topology.islsForActiveSwitches.find {
            it.srcSwitch.dpId in server42switchesDpIds && it.dstSwitch.dpId in server42switchesDpIds &&
                    it.srcSwitch.prop?.server42MacAddress != null &&
                    it.srcSwitch.prop?.server42MacAddress == it.dstSwitch.prop?.server42MacAddress
        }
        assumeTrue("Was not able to find an ISL with both endpoints on the same server42", isl != null)

        when: "server42IslRtt feature toggle is set to true"
        def islRttFeatureStartState = changeIslRttToggle(true)
        and: "server42IslRtt is enabled on src and dst switches"
        def initialSwitchRtt = [isl.srcSwitch, isl.dstSwitch].collectEntries {
            [it, changeIslRttSwitch(it, true)]
        }

        and: "Wait for several seconds"
        TimeUnit.SECONDS.sleep(RULES_INSTALLATION_TIME)
        def checkpointTime = new Date()
        TimeUnit.SECONDS.sleep(statsWaitSeconds)

        then: "ISL RTT forward stats is available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            def statsData = otsdb.query(checkpointTime, metricPrefix + "isl.rtt",
                    [src_switch: isl.srcSwitch.dpId.toOtsdFormat(),
                     src_port  : String.valueOf(isl.srcPort),
                     origin    : "server42"]).dps

            assert statsData && !statsData.empty
        }

        and: "ISL RTT reverse stats is available"
        Wrappers.wait(STATS_FROM_SERVER42_LOGGING_TIMEOUT, 1) {
            def statsData = otsdb.query(checkpointTime, metricPrefix + "isl.rtt",
                    [dst_switch: isl.dstSwitch.dpId.toOtsdFormat(),
                     dst_port  : String.valueOf(isl.dstPort),
                     origin    : "server42"]).dps
            assert statsData && !statsData.empty
        }

        cleanup: "Revert system to original state"
        revertToOrigin(islRttFeatureStartState, initialSwitchRtt)
    }

    def changeIslRttSwitch(Switch sw, boolean islRttEnabled) {
        return changeIslRttSwitch(sw, islRttEnabled ? "ENABLED" : "DISABLED")
    }

    def changeIslRttSwitch(Switch sw, String requiredState) {
        def originalProps = northbound.getSwitchProperties(sw.dpId)
        if (originalProps.server42IslRtt != requiredState) {
            northbound.updateSwitchProperties(sw.dpId, originalProps.jacksonCopy().tap {
                server42IslRtt = requiredState
                def props = sw.prop ?: SwitchHelper.dummyServer42Props
                server42MacAddress = props.server42MacAddress
                server42Port = props.server42Port
                server42Vlan = props.server42Vlan
            })
        }
        return originalProps.server42IslRtt
    }

    def changeIslRttToggle(boolean requiredState) {
        def originalState = northbound.featureToggles.server42IslRtt
        if (originalState != requiredState) {
            northbound.toggleFeature(FeatureTogglesDto.builder().server42IslRtt(requiredState).build())
        }
        //not going to check rules on every switch in the system. sleep does the trick fine
        sleep(3000)
        return originalState
    }

    def revertToOrigin(islRttFeatureStartState, initialSwitchRtt) {
        islRttFeatureStartState != null && changeIslRttToggle(islRttFeatureStartState)
        initialSwitchRtt.each { sw, state -> changeIslRttSwitch(sw, state) }
        initialSwitchRtt.keySet().each { sw ->
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                assert northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.sort() == sw.defaultCookies.sort()
            }
        }
    }
}
