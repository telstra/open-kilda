package org.openkilda.functionaltests.spec.server42

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.ResourceLockConstants.S42_TOGGLE
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.Wrappers.timedLoop
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.messaging.info.event.IslChangeType.MOVED
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.SwitchHelper
import org.openkilda.messaging.model.SwitchPropertiesDto.RttState
import org.openkilda.messaging.model.system.FeatureTogglesDto
import org.openkilda.model.SwitchFeature
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import spock.lang.Isolated
import spock.lang.ResourceLock
import spock.lang.Shared

@Slf4j
@ResourceLock(S42_TOGGLE)
@Isolated //s42 toggle affects all switches in the system, may lead to excess rules during sw validation in other tests
class Server42IslRttSpec extends HealthCheckSpecification {
    @Shared
    @Value('${opentsdb.metric.prefix}')
    String metricPrefix

    @Shared
    @Value('${latency.update.interval}')
    Integer latencyUpdateInterval

    int islSyncWaitSeconds = 60 //server42.control.rtt.sync.interval.seconds
    int statsWaitSeconds = 4

    @Tidy
    @Tags([LOW_PRIORITY])
    def "ISL RTT stats are available only if both global and switch toggles are 'on'"() {
        given: "An active ISL with both switches having server42"
        def server42switchesDpIds = topology.getActiveServer42Switches()*.dpId
        def isl = topology.islsForActiveSwitches.find {
            it.srcSwitch.dpId in server42switchesDpIds && it.dstSwitch.dpId in server42switchesDpIds
        }
        assumeTrue(isl != null, "Was not able to find an ISL with a server42 connected")

        when: "server42IslRtt feature toggle is turned off"
        def islRttFeatureStartState = changeIslRttToggle(false)

        and: "server42IslRtt is turned off on src and dst switches"
        def initialSwitchRtt = [isl.srcSwitch, isl.dstSwitch].collectEntries {
            [it, changeIslRttSwitch(it, false)]
        }
        def checkpointTime = new Date()

        then: "Expect no ISL RTT forward stats"
        timedLoop(statsWaitSeconds) {
            checkIslRttStats(isl, checkpointTime, false)
            sleep(1000)
        }

        and: "Expect no ISL RTT reverse stats"
        checkIslRttStats(isl.reversed, checkpointTime, false)

        when: "Enable global server42IslRtt feature toggle"
        changeIslRttToggle(true)
        checkpointTime = new Date()

        then: "Expect no ISL RTT forward stats"
        timedLoop(statsWaitSeconds) {
            checkIslRttStats(isl, checkpointTime, false)
            sleep(1000)
        }

        and: "Expect no ISL RTT reverse stats"
        checkIslRttStats(isl.reversed, checkpointTime, false)

        when: "Enable switch server42IslRtt toggle on src and dst"
        changeIslRttSwitch(isl.srcSwitch, true)
        changeIslRttSwitch(isl.dstSwitch, true)
        checkpointTime = new Date()
        withPool {
            wait(RULES_INSTALLATION_TIME) {
                [isl.srcSwitch, isl.dstSwitch].eachParallel { checkIslRttRules(it.dpId, true) }
            }
        }

        then: "ISL RTT forward stats are available"
        and: "ISL RTT reverse stats are available"
        wait(islSyncWaitSeconds, 2) {
            checkIslRttStats(isl, checkpointTime, true)
            checkIslRttStats(isl.reversed, checkpointTime, true)
        }

        when: "Disable global server42IslRtt feature toggle"
        changeIslRttToggle(false)
        withPool {
            wait(RULES_DELETION_TIME) {
                [isl.srcSwitch, isl.dstSwitch].eachParallel { checkIslRttRules(it.dpId, false) }
            }
        }
        checkpointTime = new Date()

        then: "Expect no ISL RTT forward stats"
        timedLoop(statsWaitSeconds) {
            checkIslRttStats(isl, checkpointTime, false)
            sleep(1000)
        }

        and: "Expect no ISL RTT reverse stats"
        checkIslRttStats(isl.reversed, checkpointTime, false)

        cleanup: "Revert system to original state"
        revertToOrigin(islRttFeatureStartState, initialSwitchRtt)
    }

    @Tidy
    @Tags([TOPOLOGY_DEPENDENT,
    HARDWARE //Temporarily disable for virtual. wait for real virtual s42
    ])
    def "ISL RTT stats are available if both endpoints are connected to the same server42 (same pop)"() {
        given: "An active ISL with both switches connected to the same server42 instance"
        def server42switchesDpIds = topology.getActiveServer42Switches()*.dpId
        def isl = topology.islsForActiveSwitches.find {
            it.srcSwitch.dpId in server42switchesDpIds && it.dstSwitch.dpId in server42switchesDpIds &&
                    it.srcSwitch.prop?.server42MacAddress != null &&
                    it.srcSwitch.prop?.server42MacAddress == it.dstSwitch.prop?.server42MacAddress
        }
        assumeTrue(isl != null, "Was not able to find an ISL with both endpoints on the same server42")

        when: "server42IslRtt feature toggle is set to true"
        def islRttFeatureStartState = changeIslRttToggle(true)

        and: "server42IslRtt is enabled on src and dst switches"
        def initialSwitchRtt = [isl.srcSwitch, isl.dstSwitch].collectEntries {
            [it, changeIslRttSwitch(it, true)]
        }

        def checkpointTime = new Date()
        withPool {
            wait(RULES_INSTALLATION_TIME) {
                [isl.srcSwitch, isl.dstSwitch].eachParallel { checkIslRttRules(it.dpId, true) }
            }
        }

        then: "ISL RTT forward stats are available"
        and: "ISL RTT reverse stats are available"
        wait(islSyncWaitSeconds + WAIT_OFFSET, 2) {
            checkIslRttStats(isl, checkpointTime, true)
            checkIslRttStats(isl.reversed, checkpointTime, true)
        }

        and: "ISL latency value is updated in db in both direction from s42"
        wait(latencyUpdateInterval + WAIT_OFFSET, 2) {
            verifyLatencyValueIsCorrect(isl)
            verifyLatencyValueIsCorrect(isl.reversed)
        }

        cleanup: "Revert system to original state"
        revertToOrigin(islRttFeatureStartState, initialSwitchRtt)
    }

    @Tidy
    @Tags([LOW_PRIORITY])
    def "ISL RTT stats are not available for a moved link and available for a new link"() {
        given: "An active a-switch ISL with both switches having server42"
        def server42switchesDpIds = topology.getActiveServer42Switches()*.dpId
        def isl = topology.islsForActiveSwitches.find {
            it.getAswitch()?.inPort && it.getAswitch()?.outPort &&
                    it.srcSwitch.dpId in server42switchesDpIds && it.dstSwitch.dpId in server42switchesDpIds
        }
        assumeTrue(isl.asBoolean(), "Wasn't able to find required a-switch link")

        and: "A non-connected a-switch link with server42"
        def notConnectedIsl = topology.notConnectedIsls.find {
            it.srcSwitch != isl.srcSwitch && it.srcSwitch != isl.dstSwitch && it.srcSwitch.dpId in server42switchesDpIds
        }
        assumeTrue(notConnectedIsl.asBoolean(), "Wasn't able to find required non-connected a-switch link")

        and: "Replug one end of the connected link to the not connected one"
        def newIsl = islUtils.replug(isl, false, notConnectedIsl, true, true)
        islUtils.waitForIslStatus([isl, isl.reversed], MOVED)
        wait(discoveryExhaustedInterval + WAIT_OFFSET) {
            [newIsl, newIsl.reversed].each { assert northbound.getLink(it).state == DISCOVERED }
        }
        def checkpointTime = new Date()

        when: "server42IslRtt feature toggle is set to true"
        def islRttFeatureStartState = changeIslRttToggle(true)

        and: "server42IslRtt is enabled on src and dst switches"
        def initialSwitchRtt = [isl.srcSwitch, isl.dstSwitch, notConnectedIsl.srcSwitch].collectEntries {
            [it, changeIslRttSwitch(it, true)]
        }

        then: "ISL RTT rules are not deleted on the src switch for the moved link"
        and: "ISL RTT rules are not installed for the new link because it is the same as moved(portNumber)"
        wait(RULES_INSTALLATION_TIME) {
            // newIsl.srcSwitch == isl.srcSwitch
            assert northbound.getSwitchRules(newIsl.srcSwitch.dpId).flowEntries.findAll {
                (it.cookie in [Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE, Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE]) ||
                        (new Cookie(it.cookie).getType() in [CookieType.SERVER_42_ISL_RTT_INPUT])
            }.size() == (northbound.getLinks(newIsl.srcSwitch.dpId, null, null, null).size() - 1 + 2)
            // -1 = moved link, 2 = SERVER_42_ISL_RTT_TURNING_COOKIE + SERVER_42_ISL_RTT_OUTPUT_COOKIE
        }

        and: "ISL RTT rules are installed on the new dst switch for the new link"
        wait(RULES_INSTALLATION_TIME) { checkIslRttRules(newIsl.dstSwitch.dpId, true) }

        and: "ISL RTT rules are not deleted on the origin dst switch for the moved link"
        timedLoop(3) { checkIslRttRules(isl.dstSwitch.dpId, true) }

        and: "Involved switches pass the switch validation"
        [isl.srcSwitch.dpId, isl.dstSwitch.dpId, newIsl.dstSwitch.dpId].each { swId ->
            with(northbound.validateSwitch(swId)) { validationResponse ->
                validationResponse.verifyRuleSectionsAreEmpty(swId, ["missing", "excess", "misconfigured"])
                validationResponse.verifyMeterSectionsAreEmpty(swId, ["missing", "excess", "misconfigured"])
            }
        }

        and: "Expect ISL RTT for new ISL in forward/reverse directions"
        wait(islSyncWaitSeconds, 2) {
            checkIslRttStats(newIsl, checkpointTime, true)
            checkIslRttStats(newIsl.reversed, checkpointTime, true)
        }

        and: "Expect no ISL RTT for MOVED ISL in forward/reverse directions"
        checkIslRttStats(isl, checkpointTime, false)
        checkIslRttStats(isl.reversed, checkpointTime, false)

        when: "Replug the link back where it was"
        islUtils.replug(newIsl, true, isl, false, false)
        islUtils.waitForIslStatus([isl, isl.reversed], DISCOVERED)
        def originIslIsUp = true
        islUtils.waitForIslStatus([newIsl, newIsl.reversed], MOVED)

        and: "Remove the MOVED ISL"
        assert northbound.deleteLink(islUtils.toLinkParameters(newIsl)).size() == 2
        def newIslIsRemoved = true

        then: "Server42 ISL RTT rules are deleted on the dst switch of the moved link"
        wait(RULES_DELETION_TIME) { checkIslRttRules(newIsl.dstSwitch.dpId, true) }

        and: "All involved switches pass switch validation"
        [isl.srcSwitch.dpId, isl.dstSwitch.dpId, newIsl.dstSwitch.dpId].each { swId ->
            with(northbound.validateSwitch(swId)) { validationResponse ->
                validationResponse.verifyRuleSectionsAreEmpty(swId, ["missing", "excess", "misconfigured"])
                validationResponse.verifyMeterSectionsAreEmpty(swId, ["missing", "excess", "misconfigured"])
            }
        }

        cleanup:
        revertToOrigin(islRttFeatureStartState, initialSwitchRtt)
        if (!originIslIsUp) {
            islUtils.replug(newIsl, true, isl, false, true)
            islUtils.waitForIslStatus([isl, isl.reversed], DISCOVERED)
        }
        if (newIsl && !newIslIsRemoved) {
            northbound.deleteLink(islUtils.toLinkParameters(newIsl))
            wait(WAIT_OFFSET) {
                assert !islUtils.getIslInfo(newIsl).isPresent()
                assert !islUtils.getIslInfo(newIsl.reversed).isPresent()
            }
        }
        database.resetCosts(topology.isls)
    }

    @Tidy
    @Tags([HARDWARE])
    def "No ISL RTT stats in both directions in case link is UP in forward direction only"() {
        given: "An active a-switch ISL with both switches having server42 and with broken reverse direction"
        def server42switchesDpIds = topology.getActiveServer42Switches()*.dpId
        def isl = topology.islsForActiveSwitches.find {
            it.getAswitch()?.inPort && it.getAswitch()?.outPort &&
                    [it.srcSwitch.dpId, it.dstSwitch.dpId].every { it in server42switchesDpIds }
        }
        assumeTrue(isl.asBoolean(), "Wasn't able to find required a-switch link")


        lockKeeper.removeFlows([isl.aswitch.reversed])
        wait(discoveryTimeout + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == FAILED
            assert islUtils.getIslInfo(links, isl).get().actualState == DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().actualState == FAILED
        }
        def checkpointTime = new Date()
        def islRvIsBroken = true

        when: "server42IslRtt feature toggle is set to true"
        def islRttFeatureStartState = changeIslRttToggle(true)

        and: "server42IslRtt is enabled on src and dst switches"
        def initialSwitchRtt = [isl.srcSwitch, isl.dstSwitch].collectEntries {
            [it, changeIslRttSwitch(it, true)]
        }

        then: "No ISL RTT stats in both directions because reverse direction is broken"
        timedLoop(statsWaitSeconds) {
            checkIslRttStats(isl, checkpointTime, false)
            checkIslRttStats(isl.reversed, checkpointTime, false)
            sleep(1000)
        }

        when: "Restore link in reverse direction"
        lockKeeper.addFlows([isl.aswitch.reversed])
        wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == DISCOVERED
            assert islUtils.getIslInfo(links, isl).get().actualState == DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().actualState == DISCOVERED
        }
        islRvIsBroken = false

        then: "ISL RTT stats for ISL in forward/reverse directions are available"
        wait(islSyncWaitSeconds, 2) {
            checkIslRttStats(isl, checkpointTime, true)
            checkIslRttStats(isl.reversed, checkpointTime, true)
        }

        cleanup:
        revertToOrigin(islRttFeatureStartState, initialSwitchRtt)
        if (islRvIsBroken) {
            lockKeeper.addFlows([isl.aswitch.reversed])
            wait(discoveryInterval + WAIT_OFFSET) {
                def links = northbound.getAllLinks()
                assert islUtils.getIslInfo(links, isl).get().state == DISCOVERED
                assert islUtils.getIslInfo(links, isl).get().actualState == DISCOVERED
                assert islUtils.getIslInfo(links, isl.reversed).get().state == DISCOVERED
                assert islUtils.getIslInfo(links, isl.reversed).get().actualState == DISCOVERED
            }
        }
    }

    @Tidy
    def "SERVER_42_ISL_RTT rules are updated according to changes in swProps"() {
        def server42switchIds = topology.getActiveServer42Switches()*.dpId
        def sw = topology.getActiveServer42Switches().find { it.wb5164 && it.dpId in server42switchIds }
        assumeTrue(sw.asBoolean(), "Wasn't able to find a WB switch connected to server42")

        when: "server42IslRtt feature toggle is set to false"
        def islRttFeatureStartState = changeIslRttToggle(false)

        and: "server42IslRtt is disabled on the switch"
        def originSwProps = northbound.getSwitchProperties(sw.dpId)
        changeIslRttSwitch(sw, false)

        then: "No IslRtt rules on the switch"
        wait(RULES_DELETION_TIME) { checkIslRttRules(sw.dpId, false) }

        when: "server42IslRtt feature toggle is set to true"
        changeIslRttToggle(true)

        then: "No IslRtt rules on the switch"
        checkIslRttRules(sw.dpId, false)

        when: "server42IslRtt feature toggle is set to false"
        changeIslRttToggle(false)

        and: "server42IslRtt is enabled on the switch"
        changeIslRttSwitch(sw, true)

        then: "No IslRtt rules on the switch"
        timedLoop(3) {
            checkIslRttRules(sw.dpId, false)
            sleep(1000)
        }

        when: "server42IslRtt feature toggle is set to true and enabled on the switch(previous step)"
        changeIslRttToggle(true)

        then: "IslRtt rules are installed on the switch"
        def s42IslRttTurningRule
        wait(RULES_INSTALLATION_TIME) {
            def s42IslRttRules = northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                (it.cookie in [Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE, Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE]) ||
                        (new Cookie(it.cookie).getType() in [CookieType.SERVER_42_ISL_RTT_INPUT])
            }
            assert s42IslRttRules.size() == (northbound.getLinks(sw.dpId, null, null, null).size() + 2)
            s42IslRttTurningRule = s42IslRttRules.find { it.cookie == Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE }
        }

        when: "Update server42Port on the switch"
        def newS42Port = originSwProps.server42Port + 1
        northbound.updateSwitchProperties(sw.dpId, northbound.getSwitchProperties(sw.dpId).jacksonCopy().tap({
            it.server42Port = newS42Port
        }))

        then: "SERVER_42_ISL_RTT_OUTPUT_COOKIE and SERVER_42_ISL_RTT_INPUT rules updated according to the changes"
        and: "SERVER_42_ISL_RTT_TURNING_COOKIE is not changed"
        wait(RULES_INSTALLATION_TIME) {
            def rules = northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                (it.cookie in [Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE, Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE]) ||
                        (new Cookie(it.cookie).getType() in [CookieType.SERVER_42_ISL_RTT_INPUT])
            }
            assert rules.size() == northbound.getLinks(sw.dpId, null, null, null).size() + 2
            assert rules.findAll {
                new Cookie(it.cookie).getType() == CookieType.SERVER_42_ISL_RTT_INPUT
            }*.match.inPort.unique() == [newS42Port.toString()]
            assert rules.find {
                it.cookie == Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE
            }.instructions.applyActions.flowOutput == newS42Port.toString()
            assert expect(rules.find { it.cookie == Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE }, sameBeanAs(s42IslRttTurningRule)
                    .ignoring("byteCount")
                    .ignoring("packetCount")
                    .ignoring("durationSeconds")
                    .ignoring("durationNanoSeconds"))
        }

        when: "server42IslRtt feature toggle is set to false"
        changeIslRttToggle(false)

        and: "server42IslRtt is set to AUTO on the switch"
        northbound.updateSwitchProperties(sw.dpId, northbound.getSwitchProperties(sw.dpId).jacksonCopy()
                .tap({ it.server42IslRtt = RttState.AUTO.toString() }))

        then: "No IslRtt rules on the switch"
        wait(RULES_DELETION_TIME) { checkIslRttRules(sw.dpId, false) }

        when: "server42IslRtt feature toggle is set true"
        changeIslRttToggle(true)

        then: "IslRtt rules are installed"
        wait(RULES_INSTALLATION_TIME) { checkIslRttRules(sw.dpId, true) }

        when: "Update server42Port on the switch(revert to origin)"
        northbound.updateSwitchProperties(sw.dpId, northbound.getSwitchProperties(sw.dpId).jacksonCopy()
                .tap({ it.server42Port = originSwProps.server42Port }))

        then: "SERVER_42_ISL_RTT_OUTPUT_COOKIE and SERVER_42_ISL_RTT_INPUT rules updated according to the changes"
        and: "SERVER_42_ISL_RTT_TURNING_COOKIE is not changed"
        wait(RULES_INSTALLATION_TIME) {
            def rules = northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                (it.cookie in [Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE, Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE]) ||
                        (new Cookie(it.cookie).getType() in [CookieType.SERVER_42_ISL_RTT_INPUT])
            }
            assert rules.size() == northbound.getLinks(sw.dpId, null, null, null).size() + 2
            assert rules.findAll {
                new Cookie(it.cookie).getType() == CookieType.SERVER_42_ISL_RTT_INPUT
            }*.match.inPort.unique() == [originSwProps.server42Port.toString()]
            assert rules.find {
                it.cookie == Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE
            }.instructions.applyActions.flowOutput == originSwProps.server42Port.toString()
            assert expect(rules.find { it.cookie == Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE }, sameBeanAs(s42IslRttTurningRule)
                    .ignoring("byteCount")
                    .ignoring("packetCount")
                    .ignoring("durationSeconds")
                    .ignoring("durationNanoSeconds"))
        }

        cleanup:
        originSwProps && northbound.updateSwitchProperties(sw.dpId, originSwProps)
        changeIslRttToggle(islRttFeatureStartState)
    }

    @Tidy
    @Tags([LOW_PRIORITY])
    def "ISL Rtt stats are available in case link and switch are under maintenance"() {
        given: "An active ISL under maintenance with both switches having server42, dst switch is under maintenance"
        def server42switchesDpIds = topology.getActiveServer42Switches()*.dpId
        def isl = topology.islsForActiveSwitches.find {
            it.srcSwitch.dpId in server42switchesDpIds && it.dstSwitch.dpId in server42switchesDpIds
        }
        assumeTrue(isl != null, "Was not able to find an ISL with a server42 connected")
        northbound.setLinkMaintenance(islUtils.toLinkUnderMaintenance(isl, true, false))
        northbound.setSwitchMaintenance(isl.dstSwitch.dpId, true, false)

        when: "server42IslRtt feature toggle is turned on"
        def islRttFeatureStartState = changeIslRttToggle(true)

        and: "Enable server42IslRtt on the src and dst switches"
        def initialSwitchRtt = [isl.srcSwitch, isl.dstSwitch].collectEntries {
            [it, changeIslRttSwitch(it, true)]
        }
        withPool {
            [isl.srcSwitch, isl.dstSwitch].eachParallel { Switch sw ->
                wait(RULES_INSTALLATION_TIME) { checkIslRttRules(sw.dpId, true) }
            }
        }
        def checkpointTime = new Date()

        then: "Expect ISL RTT for ISL in forward/reverse directions"
        wait(islSyncWaitSeconds, 2) {
            checkIslRttStats(isl, checkpointTime, true)
            checkIslRttStats(isl.reversed, checkpointTime, true)
        }

        cleanup:
        if (isl) {
            northbound.setLinkMaintenance(islUtils.toLinkUnderMaintenance(isl, false, false))
            northbound.setSwitchMaintenance(isl.dstSwitch.dpId, false, false)
        }
        revertToOrigin(islRttFeatureStartState, initialSwitchRtt)
    }

    @Tidy
    @Tags([HARDWARE])
    def "ISL Rtt stats are available in case link is RTL and a switch is disconnected"() {
        given: "An active RTL ISL with both switches having server42"
        def server42switchIds = topology.getActiveServer42Switches()*.dpId
        Isl isl = topology.islsForActiveSwitches.find {
            [it.srcSwitch, it.dstSwitch].every {
                it.dpId in server42switchIds && it.features.contains(SwitchFeature.NOVIFLOW_COPY_FIELD)
            }
        } ?: assumeTrue(false, "Wasn't able to find RTL ISL link")
        assumeTrue(isl != null, "Was not able to find an ISL with a server42 connected")

        when: "server42IslRtt feature toggle is turned on"
        def islRttFeatureStartState = changeIslRttToggle(true)

        and: "server42IslRtt is enabled on src and dst switches"
        def initialSwitchRtt = [isl.srcSwitch, isl.dstSwitch].collectEntries {
            [it, changeIslRttSwitch(it, true)]
        }
        withPool {
            [isl.srcSwitch, isl.dstSwitch].eachParallel { Switch sw ->
                wait(RULES_INSTALLATION_TIME) { checkIslRttRules(sw.dpId, true) }
            }
        }

        and: "Deactivate the src switch"
        def blockData = switchHelper.knockoutSwitch(isl.srcSwitch, RW, false)
        wait(discoveryTimeout + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == DISCOVERED
            assert islUtils.getIslInfo(links, isl).get().actualState == FAILED
            assert islUtils.getIslInfo(links, isl).get().roundTripStatus == FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().actualState == FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().roundTripStatus == DISCOVERED
        }
        def checkpointTime = new Date()

        then: "ISL RTT stats are available in both directions because RTL link is UP"
        wait(islSyncWaitSeconds, 2) {
            checkIslRttStats(isl, checkpointTime, true)
            checkIslRttStats(isl.reversed, checkpointTime, true)
        }

        when: "Connect back the src switch"
        switchHelper.reviveSwitch(isl.srcSwitch, blockData, true)
        checkpointTime = new Date()
        def swIsConnected = true

        then: "ISL Rtt rules still exist on the src switch"
        checkIslRttRules(isl.srcSwitch.dpId, true)

        and: "Switch is valid"
        with(northbound.validateSwitch(isl.srcSwitch.dpId)) {
            it.verifyRuleSectionsAreEmpty(isl.srcSwitch.dpId, ["missing", "excess", "misconfigured"])
            it.verifyMeterSectionsAreEmpty(isl.srcSwitch.dpId)
        }

        and: "ISL RTT stats in both directions are available"
        wait(islSyncWaitSeconds, 2) {
            checkIslRttStats(isl, checkpointTime, true)
            checkIslRttStats(isl.reversed, checkpointTime, true)
        }

        cleanup:
        isl && blockData && !swIsConnected && switchHelper.reviveSwitch(isl.srcSwitch, blockData, true)
        revertToOrigin(islRttFeatureStartState, initialSwitchRtt)
        database.resetCosts(topology.isls)
    }

    @Tidy
    @Tags([HARDWARE])
    def "System is able to detect and sync missing ISL Rtt rules"() {
        given: "An active ISL with both switches having server42"
        def server42switchesDpIds = topology.getActiveServer42Switches()*.dpId
        def isl = topology.islsForActiveSwitches.find {
            it.srcSwitch.dpId in server42switchesDpIds && it.dstSwitch.dpId in server42switchesDpIds
        }
        assumeTrue(isl != null, "Was not able to find an ISL with a server42 connected")
        def islRttFeatureStartState = changeIslRttToggle(true)
        def initialSwitchRtt = [isl.srcSwitch, isl.dstSwitch].collectEntries {
            [it, changeIslRttSwitch(it, true)]
        }
        wait(RULES_INSTALLATION_TIME) { checkIslRttRules(isl.srcSwitch.dpId, true) }

        when: "Delete ISL Rtt rules on the src switch"
        def rulesToDelete = northbound.getSwitchRules(isl.srcSwitch.dpId).flowEntries.findAll {
            (it.cookie in [Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE, Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE]) ||
                    (new Cookie(it.cookie).getType() in [CookieType.SERVER_42_ISL_RTT_INPUT])
        }
        withPool {
            rulesToDelete.eachParallel { northbound.deleteSwitchRules(isl.srcSwitch.dpId, it.cookie) }
        }

        then: "Rules are really deleted"
        wait(RULES_DELETION_TIME) { checkIslRttRules(isl.srcSwitch.dpId, false) }
        def checkpointTime = new Date()

        and: "Switch validation shows deleted rules as missing"
        def validateInfo = northbound.validateSwitch(isl.srcSwitch.dpId)
        validateInfo.verifyRuleSectionsAreEmpty(isl.srcSwitch.dpId, ["misconfigured", "excess"])
        northbound.validateSwitch(isl.srcSwitch.dpId).rules.missing.sort() == rulesToDelete*.cookie.sort()

        and: "No ISL Rtt stats in forward/reverse directions"
        wait(islSyncWaitSeconds, 2) {
            checkIslRttStats(isl, checkpointTime, false)
            checkIslRttStats(isl.reversed, checkpointTime, false)
            sleep(1000)
        }

        when: "Sync the src switch"
        def syncResponse = northbound.synchronizeSwitch(isl.srcSwitch.dpId, false)
        checkpointTime = new Date()

        then: "Sync response contains ISL Rtt rules into the installed section"
        syncResponse.rules.installed.sort() == rulesToDelete*.cookie.sort()

        and: "ISL Rtt rules are really installed"
        wait(RULES_INSTALLATION_TIME) {
            def installedRules = northbound.getSwitchRules(isl.srcSwitch.dpId).flowEntries.findAll {
                (it.cookie in [Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE, Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE]) ||
                        (new Cookie(it.cookie).getType() in [CookieType.SERVER_42_ISL_RTT_INPUT])
            }
            assert expect(installedRules.sort { it.cookie }, sameBeanAs(rulesToDelete.sort { it.cookie })
                    .ignoring("byteCount")
                    .ignoring("packetCount")
                    .ignoring("durationNanoSeconds")
                    .ignoring("durationSeconds"))
        }

        and: "ISL Rtt stats are available in both directions"
        wait(islSyncWaitSeconds, 2) {
            checkIslRttStats(isl, checkpointTime, true)
            checkIslRttStats(isl.reversed, checkpointTime, true)
        }

        cleanup: "Revert system to original state"
        revertToOrigin(islRttFeatureStartState, initialSwitchRtt)
    }

    def changeIslRttSwitch(Switch sw, boolean islRttEnabled) {
        return changeIslRttSwitch(sw, islRttEnabled ? RttState.ENABLED.toString() : RttState.DISABLED.toString())
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
            wait(RULES_INSTALLATION_TIME) {
                assert northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.sort() == sw.defaultCookies.sort()
            }
        }
    }

    void checkIslRttRules(SwitchId switchId, Boolean rulesExist) {
        def countOfRules = rulesExist ? (northbound.getLinks(switchId, null, null, null).size() + 2) : 0
        assert northbound.getSwitchRules(switchId).flowEntries.findAll {
            (it.cookie in [Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE, Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE]) ||
                    (new Cookie(it.cookie).getType() in [CookieType.SERVER_42_ISL_RTT_INPUT])
        }.size() == countOfRules
    }

    void checkIslRttStats(Isl isl, Date checkpointTime, Boolean statExist) {
        def stats = otsdb.query(checkpointTime, metricPrefix + "isl.rtt",
                [src_switch: isl.srcSwitch.dpId.toOtsdFormat(),
                 src_port  : String.valueOf(isl.srcPort),
                 dst_switch: isl.dstSwitch.dpId.toOtsdFormat(),
                 dst_port  : String.valueOf(isl.dstPort),
                 origin    : "server42"]).dps
        assert statExist ? !stats.isEmpty() : stats.isEmpty()
    }

    void verifyLatencyValueIsCorrect(Isl isl) {
        def t = new Date()
        t.setSeconds(t.getSeconds() - 600) //kilda_latency_update_time_range: 600
        def stats = otsdb.query(t, new Date(), metricPrefix + "isl.rtt",
                [src_switch: isl.srcSwitch.dpId.toOtsdFormat(),
                 src_port  : String.valueOf(isl.srcPort),
                 dst_switch: isl.dstSwitch.dpId.toOtsdFormat(),
                 dst_port  : String.valueOf(isl.dstPort),
                 origin    : "server42"]).dps
        def expected = stats*.getValue().average()
        def actual = northbound.getLink(isl).latency
        assert Math.abs(expected - actual) <= expected * 0.25
    }
}
