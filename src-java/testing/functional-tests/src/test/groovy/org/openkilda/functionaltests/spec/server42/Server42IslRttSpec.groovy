package org.openkilda.functionaltests.spec.server42

import static groovyx.gpars.GParsPool.withPool
import static org.assertj.core.api.Assertions.assertThat
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.ResourceLockConstants.S42_TOGGLE
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.Wrappers.timedLoop
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.functionaltests.model.stats.IslStatsMetric.ISL_RTT
import static org.openkilda.functionaltests.model.stats.Origin.SERVER_42
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.messaging.info.event.IslChangeType.MOVED
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.model.FlowRuleEntity
import org.openkilda.functionaltests.helpers.model.SwitchRulesFactory
import org.openkilda.functionaltests.model.stats.IslStats
import org.openkilda.messaging.model.SwitchPropertiesDto.RttState
import org.openkilda.model.SwitchFeature
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.testing.model.topology.TopologyDefinition.Isl

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Ignore
import spock.lang.Isolated
import spock.lang.ResourceLock
import spock.lang.Shared

@Slf4j
@ResourceLock(S42_TOGGLE)
@Isolated //s42 toggle affects all switches in the system, may lead to excess rules during sw validation in other tests

class Server42IslRttSpec extends HealthCheckSpecification {
    @Shared
    @Autowired
    IslStats islStats
    @Shared
    @Value('${latency.update.interval}')
    Integer latencyUpdateInterval
    @Autowired
    @Shared
    SwitchRulesFactory switchRulesFactory

    @Shared
    List<SwitchId> server42SwitchesDpIds

    int islSyncWaitSeconds = 60 //server42.control.rtt.sync.interval.seconds
    int statsWaitSeconds = 4

    def setupSpec() {
        server42SwitchesDpIds = topology.getActiveServer42Switches()*.dpId
    }

    @Tags([LOW_PRIORITY])
    def "ISL RTT stats are ONLY available if both server42_isl_rtt feature toggle is ON and server42_isl_rtt is enabled for switches"() {
        given: "An active ISL with both switches having server42"
        def isl = topology.islsForActiveSwitches.find { server42SwitchesDpIds.containsAll([ it.srcSwitch.dpId, it.dstSwitch.dpId]) }
        assumeTrue(isl != null, "Was not able to find an ISL with a server42 connected")

        when: "Enable server42IslRtt features toggle"
        featureToggles.getFeatureToggles().server42IslRtt == true ?: featureToggles.server42IslRtt(true)

        and: "server42IslRtt is enabled for both switches(props)"
        switchHelper.setServer42IslRttAndWaitForRulesInstallation(isl.srcSwitch, true, true)
        switchHelper.setServer42IslRttAndWaitForRulesInstallation(isl.dstSwitch, true, true)

        then: "ISL RTT stats are available for both forward and reverse direction"
        def checkpointTime = new Date().getTime()
        wait(islSyncWaitSeconds + WAIT_OFFSET, 2) {
            assert islStats.of(isl).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
            assert islStats.of(isl.reversed).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
        }
    }

    @Tags([LOW_PRIORITY])
    def "ISL RTT stats are NOT available if featureToggle: #featureToggle and switchToggle: #switchToggle"() {
        given: "An active ISL with both switches having server42"
        def isl = topology.islsForActiveSwitches.find { server42SwitchesDpIds.containsAll([ it.srcSwitch.dpId, it.dstSwitch.dpId]) }

        assumeTrue(isl != null, "Was not able to find an ISL with a server42 connected")

        when: "server42IslRtt feature toggle is set #featureToggle"
        featureToggles.getFeatureToggles().server42IslRtt == featureToggle ?: featureToggles.server42IslRtt(featureToggle)

        and: "server42IslRtt is set #switchToggle on src and dst switches"
        switchHelper.setServer42IslRttAndWaitForRulesInstallation(isl.srcSwitch, switchToggle, featureToggle)
        switchHelper.setServer42IslRttAndWaitForRulesInstallation(isl.dstSwitch, switchToggle, featureToggle)

        then: "ISL RTT forward stats are NOT available for both forward and reverse directions"
        def checkpointTime = new Date().getTime()
        timedLoop(statsWaitSeconds) {
            assert !islStats.of(isl).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime + 1000)
            assert !islStats.of(isl.reversed).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime + 1000)
            sleep(statsWaitSeconds)
        }

        where:
        featureToggle | switchToggle
        false         | true
        true          | false
        false         | false
    }

    @Tags([TOPOLOGY_DEPENDENT,
    HARDWARE //Temporarily disable for virtual. wait for real virtual s42
    ])
    def "ISL RTT stats are available if both endpoints are connected to the same server42 (same pop)"() {
        given: "An active ISL with both switches connected to the same server42 instance"
        def isl = topology.islsForActiveSwitches.find {
                    server42SwitchesDpIds.containsAll([ it.srcSwitch.dpId, it.dstSwitch.dpId]) &&
                    it.srcSwitch.prop?.server42MacAddress != null &&
                    it.srcSwitch.prop?.server42MacAddress == it.dstSwitch.prop?.server42MacAddress
        }
        assumeTrue(isl != null, "Was not able to find an ISL with both endpoints on the same server42")

        when: "server42IslRtt feature toggle is set to true"
        featureToggles.getFeatureToggles().server42IslRtt == true ?: featureToggles.server42IslRtt(true)

        and: "server42IslRtt is enabled on src and dst switches"
        switchHelper.setServer42IslRttAndWaitForRulesInstallation(isl.srcSwitch, true, true)
        switchHelper.setServer42IslRttAndWaitForRulesInstallation(isl.dstSwitch, true, true)

        then: "ISL RTT for both forward and reverse stats are available"
        def checkpointTime = new Date().getTime()
        wait(islSyncWaitSeconds + WAIT_OFFSET, 2) {
            assert islStats.of(isl).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
            assert islStats.of(isl.reversed).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
        }

        and: "ISL latency value is updated in db in both direction from s42"
        wait(latencyUpdateInterval + WAIT_OFFSET, 2) {
            [isl, isl.reversed].each {
                Long expected  =  islStats.of(it).get(ISL_RTT, SERVER_42).getDataPoints().values().average()
                Long actual = northbound.getLink(it).latency
                assert Math.abs(expected - actual) <= expected * 0.25
            }
        }
    }

    @Tags([LOW_PRIORITY])
    def "ISL RTT stats are not available for a moved link and available for a new link"() {
        given: "An active a-switch ISL with both switches having server42"
        def isl = topology.islsForActiveSwitches.find {
            it.getAswitch()?.inPort && it.getAswitch()?.outPort &&
                    server42SwitchesDpIds.containsAll([it.srcSwitch.dpId, it.dstSwitch.dpId])
        }
        assumeTrue(isl.asBoolean(), "Wasn't able to find required a-switch link")

        and: "A non-connected a-switch link with server42"
        def notConnectedIsl = topology.notConnectedIsls.find {
            !(it.srcSwitch.dpId in [isl.srcSwitch.dpId, isl.dstSwitch.dpId]) && it.srcSwitch.dpId in server42SwitchesDpIds
        }
        assumeTrue(notConnectedIsl.asBoolean(), "Wasn't able to find required non-connected a-switch link")

        and: "Replug one end of the connected link to the not connected one"
        def newIsl = islHelper.replugDestination(isl, notConnectedIsl, true, true)
        islUtils.waitForIslStatus([isl, isl.reversed], MOVED)
        wait(discoveryExhaustedInterval + WAIT_OFFSET) {
            [newIsl, newIsl.reversed].each { assert northbound.getLink(it).state == DISCOVERED }
        }
        def checkpointTime = new Date().getTime()

        when: "server42IslRtt feature toggle is set to true"
        featureToggles.getFeatureToggles().server42IslRtt == true ?: featureToggles.server42IslRtt(true)

        and: "server42IslRtt is enabled on src and dst switches"
        [isl.srcSwitch, isl.dstSwitch, notConnectedIsl.srcSwitch].each {
            switchHelper.setServer42IslRttForSwitch(it, true)
        }

        then: "ISL RTT rules are not deleted on the src switch for the moved link"
        and: "ISL RTT rules are not installed for the new link because it is the same as moved(portNumber)"
        wait(RULES_INSTALLATION_TIME) {
            // newIsl.srcSwitch == isl.srcSwitch
            assert switchRulesFactory.get(newIsl.srcSwitch.dpId).getServer42ISLRules().size() ==
                    (northbound.getLinks(newIsl.srcSwitch.dpId, null, null, null).size() - 1 + 2)
            // -1 = moved link, 2 = SERVER_42_ISL_RTT_TURNING_COOKIE + SERVER_42_ISL_RTT_OUTPUT_COOKIE
        }

        and: "ISL RTT rules are installed on the new dst switch for the new link"
        switchHelper.waitForS42IslRulesSetUp(newIsl.dstSwitch, true, true)

        and: "ISL RTT rules are not deleted on the origin dst switch for the moved link"
        timedLoop(3) {
            switchHelper.waitForS42IslRulesSetUp(isl.dstSwitch, true, true)
        }

        and: "Involved switches pass the switch validation"
        switchHelper.synchronizeAndCollectFixedDiscrepancies([isl.srcSwitch.dpId, isl.dstSwitch.dpId, newIsl.dstSwitch.dpId])
                .isEmpty()

        and: "Expect ISL RTT for new ISL in forward/reverse directions"
        wait(islSyncWaitSeconds, 2) {
            assert islStats.of(newIsl).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
            assert islStats.of(newIsl.reversed).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
        }

        and: "Expect no ISL RTT for MOVED ISL in forward/reverse directions"
        timedLoop(WAIT_OFFSET) {
            assert !islStats.of(isl).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime + 1000)
            assert !islStats.of(isl.reversed).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime + 1000)
            sleep(statsWaitSeconds)
        }

        when: "Replug the link back where it was"
        islUtils.replug(newIsl, true, isl, false, false)
        islUtils.waitForIslStatus([isl, isl.reversed], DISCOVERED)
        islUtils.waitForIslStatus([newIsl, newIsl.reversed], MOVED)

        and: "Remove the MOVED ISL"
        assert northbound.deleteLink(islUtils.toLinkParameters(newIsl)).size() == 2

        then: "Server42 ISL RTT rules are deleted on the dst switch of the moved link"
        switchHelper.waitForS42IslRulesSetUp(newIsl.dstSwitch, true, true)

        and: "All involved switches pass switch validation"
        switchHelper.synchronizeAndCollectFixedDiscrepancies([isl.srcSwitch.dpId, isl.dstSwitch.dpId, newIsl.dstSwitch.dpId])
                .isEmpty()
    }

    @Tags([HARDWARE])
    def "No ISL RTT stats in both directions in case link is UP in forward direction only"() {
        given: "An active a-switch ISL with both switches having server42 and with broken reverse direction"
        def isl = topology.islsForActiveSwitches.find {
            it.getAswitch()?.inPort && it.getAswitch()?.outPort &&
                    [it.srcSwitch.dpId, it.dstSwitch.dpId].every { it in server42SwitchesDpIds }
        }
        assumeTrue(isl.asBoolean(), "Wasn't able to find required a-switch link")


        aSwitchFlows.removeFlows([isl.aswitch.reversed])
        wait(discoveryTimeout + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == FAILED
            assert islUtils.getIslInfo(links, isl).get().actualState == DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == FAILED
            assert islUtils.getIslInfo(links, isl.reversed).get().actualState == FAILED
        }

        when: "server42IslRtt feature toggle is set to true"
        featureToggles.getFeatureToggles().server42IslRtt == true ?: featureToggles.server42IslRtt(true)

        and: "server42IslRtt is enabled on src and dst switches"
        switchHelper.setServer42IslRttAndWaitForRulesInstallation(isl.srcSwitch, true, true)
        switchHelper.setServer42IslRttAndWaitForRulesInstallation(isl.dstSwitch, true, true)

        then: "No ISL RTT stats in both directions because reverse direction is broken"
        def checkpointTime = new Date().getTime()
        timedLoop(statsWaitSeconds) {
            assert !islStats.of(isl).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime + 1000)
            assert !islStats.of(isl.reversed).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime + 1000)
            sleep(statsWaitSeconds)
        }

        when: "Restore link in reverse direction"
        aSwitchFlows.addFlows([isl.aswitch.reversed])
        wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            assert islUtils.getIslInfo(links, isl).get().state == DISCOVERED
            assert islUtils.getIslInfo(links, isl).get().actualState == DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().state == DISCOVERED
            assert islUtils.getIslInfo(links, isl.reversed).get().actualState == DISCOVERED
        }

        then: "ISL RTT stats for ISL in forward/reverse directions are available"
        wait(islSyncWaitSeconds, 2) {
            assert islStats.of(isl).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
            assert islStats.of(isl.reversed).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
        }
    }

    @Ignore("https://github.com/telstra/open-kilda/issues/5557")
    @Tags([HARDWARE])
    def "SERVER_42_ISL_RTT rules are updated according to changes in swProps"() {
        def sw = topology.getActiveServer42Switches().find { it.wb5164 && it.dpId in server42SwitchesDpIds }
        assumeTrue(sw.asBoolean(), "Wasn't able to find a WB switch connected to server42")

        when: "server42IslRtt feature toggle is set to false"
        featureToggles.getFeatureToggles().server42IslRtt == false ?: featureToggles.server42IslRtt(false)

        and: "server42IslRtt is disabled on the switch"
        def originSwProps = switchHelper.getCachedSwProps(sw.dpId)
        switchHelper.setServer42IslRttForSwitch(sw, false)

        then: "No IslRtt rules on the switch"
        wait(RULES_DELETION_TIME) { switchRulesFactory.get(sw.dpId).getServer42ISLRules().isEmpty() }

        when: "server42IslRtt feature toggle is set to true"
        featureToggles.server42IslRtt(true)

        then: "No IslRtt rules on the switch"
        timedLoop(3) {
            switchRulesFactory.get(sw.dpId).getServer42ISLRules().isEmpty()
            sleep(1000)
        }

        when: "server42IslRtt feature toggle is set to false"
        featureToggles.server42IslRtt(false)

        and: "server42IslRtt is enabled on the switch"
        switchHelper.setServer42IslRttForSwitch(sw, true)

        then: "No IslRtt rules on the switch"
        timedLoop(3) {
            switchRulesFactory.get(sw.dpId).getServer42ISLRules().isEmpty()
            sleep(1000)
        }

        when: "server42IslRtt feature toggle is set to true and enabled on the switch(previous step)"
        featureToggles.server42IslRtt(true)

        then: "IslRtt rules are installed on the switch"
        FlowRuleEntity s42IslRttTurningRule
        wait(RULES_INSTALLATION_TIME) {
            def s42IslRttRules = switchRulesFactory.get(sw.dpId).getServer42ISLRules()
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
            def rules = switchRulesFactory.get(sw.dpId).getServer42ISLRules()
            assert rules.size() == northbound.getLinks(sw.dpId, null, null, null).size() + 2
            assert rules.findAll {
                new Cookie(it.cookie).getType() == CookieType.SERVER_42_ISL_RTT_INPUT
            }*.match.inPort.unique() == [newS42Port.toString()]
            assert rules.find {
                it.cookie == Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE
            }.instructions.applyActions.flowOutput == newS42Port.toString()
            assert rules.find { it.cookie == Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE } == s42IslRttTurningRule
        }

        when: "server42IslRtt feature toggle is set to false"
        featureToggles.server42IslRtt(false)

        and: "server42IslRtt is set to AUTO on the switch"
        northbound.updateSwitchProperties(sw.dpId, northbound.getSwitchProperties(sw.dpId).jacksonCopy()
                .tap({ it.server42IslRtt = RttState.AUTO.toString() }))

        then: "No IslRtt rules on the switch"
        wait(RULES_DELETION_TIME) { switchRulesFactory.get(sw.dpId).getServer42ISLRules().isEmpty() }

        when: "server42IslRtt feature toggle is set true"
        featureToggles.server42IslRtt(true)

        then: "IslRtt rules are installed"
        switchHelper.waitForS42IslRulesSetUp(sw, true, true)

        when: "Update server42Port on the switch(revert to origin)"
        northbound.updateSwitchProperties(sw.dpId, northbound.getSwitchProperties(sw.dpId).jacksonCopy()
                .tap({ it.server42Port = originSwProps.server42Port }))

        then: "SERVER_42_ISL_RTT_OUTPUT_COOKIE and SERVER_42_ISL_RTT_INPUT rules updated according to the changes"
        and: "SERVER_42_ISL_RTT_TURNING_COOKIE is not changed"
        wait(RULES_INSTALLATION_TIME) {
            def rules = switchRulesFactory.get(sw.dpId).getServer42ISLRules()
            assert rules.size() == northbound.getLinks(sw.dpId, null, null, null).size() + 2
            assert rules.findAll {
                new Cookie(it.cookie).getType() == CookieType.SERVER_42_ISL_RTT_INPUT
            }*.match.inPort.unique() == [originSwProps.server42Port.toString()]
            assert rules.find {
                it.cookie == Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE
            }.instructions.applyActions.flowOutput == originSwProps.server42Port.toString()
            assert  rules.find { it.cookie == Cookie.SERVER_42_ISL_RTT_TURNING_COOKIE } == s42IslRttTurningRule
        }
    }

    @Tags([LOW_PRIORITY])
    def "ISL Rtt stats are available in case link and switch are under maintenance"() {
        given: "An active ISL under maintenance with both switches having server42, dst switch is under maintenance"
        def isl = topology.islsForActiveSwitches.find { server42SwitchesDpIds.containsAll([it.srcSwitch.dpId, it.dstSwitch.dpId]) }
        assumeTrue(isl != null, "Was not able to find an ISL with a server42 connected")
        islHelper.setLinkMaintenance(isl, true, false)
        switchHelper.setSwitchMaintenance(isl.dstSwitch.dpId, true, false)

        when: "server42IslRtt feature toggle is turned on"
        featureToggles.getFeatureToggles().server42IslRtt == true ?: featureToggles.server42IslRtt(true)

        and: "Enable server42IslRtt on the src and dst switches"
        switchHelper.setServer42IslRttAndWaitForRulesInstallation(isl.srcSwitch, true, true)
        switchHelper.setServer42IslRttAndWaitForRulesInstallation(isl.dstSwitch, true, true)

        then: "Expect ISL RTT for ISL in forward/reverse directions"
        def checkpointTime = new Date().getTime()
        wait(islSyncWaitSeconds, 2) {
            assert islStats.of(isl).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
            assert islStats.of(isl.reversed).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
        }
    }

    @Tags([HARDWARE, SWITCH_RECOVER_ON_FAIL])
    def "ISL Rtt stats are available in case link is RTL and a switch is disconnected"() {
        given: "An active RTL ISL with both switches having server42"
        Isl isl = topology.islsForActiveSwitches.find {
            [it.srcSwitch, it.dstSwitch].every {
                it.dpId in server42SwitchesDpIds && it.features.contains(SwitchFeature.NOVIFLOW_COPY_FIELD)
            }
        } ?: assumeTrue(false, "Wasn't able to find RTL ISL link")
        assumeTrue(isl != null, "Was not able to find an ISL with a server42 connected")

        when: "server42IslRtt feature toggle is turned on"
        featureToggles.getFeatureToggles().server42IslRtt == true ?: featureToggles.server42IslRtt(true)

        and: "server42IslRtt is enabled on src and dst switches"
        switchHelper.setServer42IslRttAndWaitForRulesInstallation(isl.srcSwitch, true, true)
        switchHelper.setServer42IslRttAndWaitForRulesInstallation(isl.dstSwitch, true, true)

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

        then: "ISL RTT stats are available in both directions because RTL link is UP"
        def checkpointTime = new Date().getTime()
        wait(islSyncWaitSeconds, 2) {
            assert islStats.of(isl).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
            assert islStats.of(isl.reversed).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
        }

        when: "Connect back the src switch"
        switchHelper.reviveSwitch(isl.srcSwitch, blockData, true)
        checkpointTime = new Date().getTime()

        then: "ISL Rtt rules still exist on the src switch"
        switchHelper.waitForS42IslRulesSetUp(isl.srcSwitch, true, true)

        and: "Switch is valid"
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(isl.srcSwitch.dpId).isPresent()

        and: "ISL RTT stats in both directions are available"
        wait(islSyncWaitSeconds, 2) {
            assert islStats.of(isl).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
            assert islStats.of(isl.reversed).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
        }
    }

    @Tags([HARDWARE])
    def "System is able to detect and sync missing ISL Rtt rules"() {
        given: "An active ISL with both switches having server42"
        def isl = topology.islsForActiveSwitches.find { server42SwitchesDpIds.containsAll([it.srcSwitch.dpId, it.dstSwitch.dpId]) }
        assumeTrue(isl != null, "Was not able to find an ISL with a server42 connected")

        featureToggles.getFeatureToggles().server42IslRtt == true ?: featureToggles.server42IslRtt(true)

        switchHelper.setServer42IslRttAndWaitForRulesInstallation(isl.srcSwitch, true, true)
        switchHelper.setServer42IslRttAndWaitForRulesInstallation(isl.dstSwitch, true, true)

        when: "Delete ISL Rtt rules on the src switch"
        def rulesToDelete = switchRulesFactory.get(isl.srcSwitch.dpId).getServer42ISLRules()
        withPool {
            rulesToDelete.eachParallel { switchHelper.deleteSwitchRules(isl.srcSwitch.dpId, it.cookie) }
        }

        then: "Rules are really deleted"
        switchHelper.waitForS42IslRulesSetUp(isl.srcSwitch, false, true)
        def checkpointTime = new Date().getTime()

        and: "Switch validation shows deleted rules as missing"
        def validateInfo = switchHelper.validateAndCollectFoundDiscrepancies(isl.srcSwitch.dpId).get()
        validateInfo.rules.missing*.getCookie().sort() == rulesToDelete*.cookie.sort()

        and: "No ISL Rtt stats in forward/reverse directions"
        wait(islSyncWaitSeconds, 2) {
            assert !islStats.of(isl).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime + 1000)
            assert !islStats.of(isl.reversed).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime + 1000)
        }

        when: "Sync the src switch"
        def syncResponse = switchHelper.synchronizeAndCollectFixedDiscrepancies(isl.srcSwitch.dpId).get()
        checkpointTime = new Date().getTime()

        then: "Sync response contains ISL Rtt rules into the installed section"
        syncResponse.rules.installed.sort() == rulesToDelete*.cookie.sort()

        and: "ISL Rtt rules are really installed"
        wait(RULES_INSTALLATION_TIME) {
            def installedRules = switchRulesFactory.get(isl.srcSwitch.dpId).getServer42ISLRules()
            assertThat(installedRules).containsExactlyInAnyOrder(*rulesToDelete)
        }

        and: "ISL Rtt stats are available in both directions"
        wait(islSyncWaitSeconds, 2) {
            assert islStats.of(isl).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
            assert islStats.of(isl.reversed).get(ISL_RTT, SERVER_42).hasNonZeroValuesAfter(checkpointTime)
        }
    }
}
