package org.openkilda.functionaltests.spec.links

import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.model.system.FeatureTogglesDto
import org.openkilda.model.IslStatus
import org.openkilda.model.SwitchFeature

import spock.lang.Narrative
import spock.lang.See

import java.util.concurrent.TimeUnit

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/network-discovery")
@Narrative("""BFD stands for Bidirectional Forwarding Detection. For now tested only on Noviflow switches. 
Main purpose is to detect ISL failure on switch level, which should be times faster than a regular 
controller-involved discovery mechanism""")
@Tags([HARDWARE])
class BfdSpec extends HealthCheckSpecification {
    @Tidy
    @Tags([SMOKE_SWITCHES])
    def "Able to create a valid BFD session between two Noviflow switches"() {
        given: "An a-switch ISL between two Noviflow switches with BFD and RTL"
        def isl = topology.islsForActiveSwitches.find { it.srcSwitch.noviflow && it.dstSwitch.noviflow &&
                it.aswitch?.inPort && it.aswitch?.outPort &&
                it.srcSwitch.features.contains(SwitchFeature.NOVIFLOW_COPY_FIELD) &&
                it.dstSwitch.features.contains(SwitchFeature.NOVIFLOW_COPY_FIELD)
        }
        assumeTrue("The test requires at least one a-switch BFD and RTL ISL between Noviflow switches",
                isl as boolean)

        when: "Create a BFD session on the ISL"
        def createBfdResponse = northbound.setLinkBfd(islUtils.toLinkEnableBfd(isl, true))

        then: "Response reports successful installation of the session"
        createBfdResponse.size() == 2
        createBfdResponse.each {
            assert it.enableBfd
        }

        when: "Interrupt ISL connection by breaking rule on a-switch"
        def costBeforeFailure = islUtils.getIslInfo(isl).get().cost
        lockKeeper.removeFlows([isl.aswitch])

        then: "ISL immediately gets failed because bfd has higher priority than RTL"
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert northbound.getLink(isl).state == IslChangeType.FAILED
            assert northbound.getLink(isl.reversed).state == IslChangeType.FAILED
        }

        and: "Cost of ISL is unchanged"
        islUtils.getIslInfo(isl).get().cost == costBeforeFailure

        and: "Round trip latency status is ACTIVE"
        [isl, isl.reversed].each { assert database.getIslRoundTripStatus(it) == IslStatus.ACTIVE }

        when: "Restore connection"
        lockKeeper.addFlows([isl.aswitch])

        then: "ISL is rediscovered"
        Wrappers.wait(discoveryAuxiliaryInterval + WAIT_OFFSET) {
            assert northbound.getLink(isl).state == IslChangeType.DISCOVERED
            assert northbound.getLink(isl.reversed).state == IslChangeType.DISCOVERED
        }

        when: "Remove existing BFD session"
        def removeBfdResponse = northbound.setLinkBfd(islUtils.toLinkEnableBfd(isl, false))

        then: "Response reports successful deletion of the session"
        removeBfdResponse.size() == 2
        removeBfdResponse.each {
            assert !it.enableBfd
        }

        when: "Interrupt ISL connection by breaking rule on a-switch"
        lockKeeper.removeFlows([isl.aswitch])

        then: "ISL does not get failed immediately"
        Wrappers.timedLoop(discoveryTimeout * 0.8) {
            assert northbound.getLink(isl).state == IslChangeType.DISCOVERED
            assert northbound.getLink(isl.reversed).state == IslChangeType.DISCOVERED
        }

        and: "ISL fails after discovery timeout"
        Wrappers.wait(discoveryTimeout * 0.2 + WAIT_OFFSET) {
            assert northbound.getLink(isl).state == IslChangeType.FAILED
            assert northbound.getLink(isl.reversed).state == IslChangeType.FAILED
        }

        cleanup: "Restore broken ISL"
        createBfdResponse && !removeBfdResponse && northbound.setLinkBfd(islUtils.toLinkEnableBfd(isl, false))
        lockKeeper.addFlows([isl.aswitch])
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getLink(isl).state == IslChangeType.DISCOVERED
            assert northbound.getLink(isl.reversed).state == IslChangeType.DISCOVERED
        }
    }

    @Tidy
    def "Reacting on BFD events can be turned on/off by a feature toggle"() {
        given: "An a-switch ISL between two Noviflow switches with BFD enabled"
        def isl = topology.islsForActiveSwitches.find { it.srcSwitch.noviflow && it.dstSwitch.noviflow &&
                it.aswitch?.inPort && it.aswitch?.outPort }
        assumeTrue("Require at least one a-switch BFD ISL between Noviflow switches", isl as boolean)
        northbound.setLinkBfd(islUtils.toLinkEnableBfd(isl, true))

        when: "Set BFD toggle to 'off' state"
        def toggleOff = northbound.toggleFeature(FeatureTogglesDto.builder().useBfdForIslIntegrityCheck(false).build())

        and: "Interrupt ISL connection by breaking rule on a-switch"
        lockKeeper.removeFlows([isl.aswitch])

        then: "ISL does not get FAILED immediately"
        Wrappers.timedLoop(discoveryTimeout * 0.8) {
            assert northbound.getLink(isl).state == IslChangeType.DISCOVERED
            assert northbound.getLink(isl.reversed).state == IslChangeType.DISCOVERED
        }

        and: "ISL fails after discovery timeout"
        Wrappers.wait(discoveryTimeout * 0.2 + WAIT_OFFSET) {
            assert northbound.getLink(isl).state == IslChangeType.FAILED
            assert northbound.getLink(isl.reversed).state == IslChangeType.FAILED
        }

        when: "Set BFD toggle back to 'on' state and restore the ISL"
        lockKeeper.addFlows([isl.aswitch])
        def toggleOn = northbound.toggleFeature(FeatureTogglesDto.builder().useBfdForIslIntegrityCheck(true).build())
        Wrappers.wait(discoveryAuxiliaryInterval + WAIT_OFFSET) {
            assert northbound.getLink(isl).state == IslChangeType.DISCOVERED
            assert northbound.getLink(isl.reversed).state == IslChangeType.DISCOVERED
        }

        and: "Again interrupt ISL connection by breaking rule on a-switch"
        lockKeeper.removeFlows([isl.aswitch])

        then: "ISL immediately gets failed"
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert northbound.getLink(isl).state == IslChangeType.FAILED
            assert northbound.getLink(isl.reversed).state == IslChangeType.FAILED
        }

        cleanup: "Restore ISL and remove BFD session"
        toggleOff && !toggleOn && northbound.toggleFeature(FeatureTogglesDto.builder().useBfdForIslIntegrityCheck(true).build())
        lockKeeper.addFlows([isl.aswitch])
        northbound.setLinkBfd(islUtils.toLinkEnableBfd(isl, false))
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getLink(isl).state == IslChangeType.DISCOVERED
            assert northbound.getLink(isl.reversed).state == IslChangeType.DISCOVERED
        }
    }

    def "Deleting a failed BFD link also removes the BFD session from it"() {
        given: "An inactive a-switch link with BFD session"
        def isl = topology.islsForActiveSwitches.find { it.srcSwitch.noviflow && it.dstSwitch.noviflow &&
                it.aswitch?.inPort && it.aswitch?.outPort }
        assumeTrue("Require at least one a-switch BFD ISL between Noviflow switches", isl as boolean)
        antiflap.portDown(isl.srcSwitch.dpId, isl.srcPort)
        TimeUnit.SECONDS.sleep(2) //receive any in-progress disco packets
        northbound.setLinkBfd(islUtils.toLinkEnableBfd(isl, true))
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getLink(isl).actualState == IslChangeType.FAILED
        }

        when: "Delete the link"
        northbound.deleteLink(islUtils.toLinkParameters(isl))
        !islUtils.getIslInfo(isl)
        !islUtils.getIslInfo(isl.reversed)

        and: "Discover the removed link again"
        antiflap.portUp(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getLink(isl).state == IslChangeType.DISCOVERED
            assert northbound.getLink(isl.reversed).state == IslChangeType.DISCOVERED
        }

        then: "Discovered link shows no bfd session"
        !northbound.getLink(isl).enableBfd
        !northbound.getLink(isl.reversed).enableBfd

        and: "Acts like there is no BFD session (fails only after discovery timeout)"
        lockKeeper.removeFlows([isl.aswitch])
        Wrappers.timedLoop(discoveryTimeout * 0.8) {
            assert northbound.getLink(isl).state == IslChangeType.DISCOVERED
            assert northbound.getLink(isl.reversed).state == IslChangeType.DISCOVERED
        }
        Wrappers.wait(discoveryTimeout * 0.2 + WAIT_OFFSET) {
            assert northbound.getLink(isl).state == IslChangeType.FAILED
            assert northbound.getLink(isl.reversed).state == IslChangeType.FAILED
        }

        and: "Cleanup: restore ISL"
        lockKeeper.addFlows([isl.aswitch])
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getLink(isl).state == IslChangeType.DISCOVERED
            assert northbound.getLink(isl.reversed).state == IslChangeType.DISCOVERED
        }
    }

    @Tidy
    @Tags([SMOKE_SWITCHES])
    def "System is able to rediscover failed link after deleting BFD session"() {
        given: "An interrupted a-switch ISL with BFD session"
        def isl = topology.islsForActiveSwitches.find { it.srcSwitch.noviflow && it.dstSwitch.noviflow &&
                it.aswitch?.inPort && it.aswitch?.outPort
        }
        assumeTrue("The test requires at least one a-switch BFD ISL", isl as boolean)
        northbound.setLinkBfd(islUtils.toLinkEnableBfd(isl, true))
        def isBfdEnabled = true
        lockKeeper.removeFlows([isl.aswitch])
        def isAswitchRuleDeleted = true
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert northbound.getLink(isl).state == IslChangeType.FAILED
            assert northbound.getLink(isl.reversed).state == IslChangeType.FAILED
        }

        when: "Remove existing BFD session"
        northbound.setLinkBfd(islUtils.toLinkEnableBfd(isl, false))
        isBfdEnabled = false

        and: "Restore connection"
        lockKeeper.addFlows([isl.aswitch])
        isAswitchRuleDeleted = false

        then: "ISL is rediscovered"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getLink(isl).state == IslChangeType.DISCOVERED
            assert northbound.getLink(isl.reversed).state == IslChangeType.DISCOVERED
        }

        cleanup:
        isBfdEnabled && northbound.setLinkBfd(islUtils.toLinkEnableBfd(isl, false))
        if(isAswitchRuleDeleted) {
            lockKeeper.addFlows([isl.aswitch])
            Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
                assert northbound.getLink(isl).state == IslChangeType.DISCOVERED
                assert northbound.getLink(isl.reversed).state == IslChangeType.DISCOVERED
            }
        }
    }
}
