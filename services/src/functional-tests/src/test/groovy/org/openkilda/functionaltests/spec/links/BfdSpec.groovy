package org.openkilda.functionaltests.spec.links

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tag
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.model.system.FeatureTogglesDto

import spock.lang.Narrative

@Narrative("""BFD stands for Bidirectional Forwarding Detection. For now tested only on Noviflow switches. 
Main purpose is to detect ISL failure on switch level, which should be times faster than a regular 
controller-involved discovery mechanism""")
@Tags(Tag.HARDWARE)
class BfdSpec extends HealthCheckSpecification {

    def "Able to create a valid BFD session between two Noviflow switches"() {
        given: "An a-switch ISL between two Noviflow switches"
        def isl = topology.islsForActiveSwitches.find { it.srcSwitch.noviflow && it.dstSwitch.noviflow &&
                it.aswitch?.inPort && it.aswitch?.outPort }
        assumeTrue("Require at least one a-switch BFD ISL between Noviflow switches", isl as boolean)

        when: "Create a BFD session on the ISL"
        def createBfdResponse = northbound.setLinkBfd(islUtils.toLinkEnableBfd(isl, true))

        then: "Response reports successful installation of the session"
        createBfdResponse.size() == 1 //TODO(rtretiak): should be '2'. See #2342
        createBfdResponse.each {
            assert it.enableBfd
        }

        when: "Interrupt ISL connection by breaking rule on a-switch"
        def costBeforeFailure = islUtils.getIslInfo(isl).get().cost
        lockKeeper.removeFlows([isl.aswitch])

        then: "ISL immediately gets failed"
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert northbound.getLink(isl).state == IslChangeType.FAILED
            assert northbound.getLink(isl.reversed).state == IslChangeType.FAILED
        }

        and: "Cost of ISL is unchanged"
        islUtils.getIslInfo(isl).get().cost == costBeforeFailure

        when: "Restore connection"
        lockKeeper.addFlows([isl.aswitch])

        then: "ISL is rediscovered"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getLink(isl).state == IslChangeType.DISCOVERED
            assert northbound.getLink(isl.reversed).state == IslChangeType.DISCOVERED
        }

        when: "Remove existing BFD session"
        def removeBfdResponse = northbound.setLinkBfd(islUtils.toLinkEnableBfd(isl, false))

        then: "Response reports successful deletion of the session"
        removeBfdResponse.size() == 1 //TODO(rtretiak): should be '2'. See #2342
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

        and: "Cleanup: restore broken ISL"
        lockKeeper.addFlows([isl.aswitch])
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getLink(isl).state == IslChangeType.DISCOVERED
            assert northbound.getLink(isl.reversed).state == IslChangeType.DISCOVERED
        }
    }

    def "Reacting on BFD events can be turned on/off by a feature toggle"() {
        given: "An a-switch ISL between two Noviflow switches with BFD enabled"
        def isl = topology.islsForActiveSwitches.find { it.srcSwitch.noviflow && it.dstSwitch.noviflow &&
                it.aswitch?.inPort && it.aswitch?.outPort }
        assumeTrue("Require at least one a-switch BFD ISL between Noviflow switches", isl as boolean)
        northbound.setLinkBfd(islUtils.toLinkEnableBfd(isl, true))

        when: "Set BFD toggle to 'off' state"
        northbound.toggleFeature(FeatureTogglesDto.builder().useBfdForIslIntegrityCheck(false).build())

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
        northbound.toggleFeature(FeatureTogglesDto.builder().useBfdForIslIntegrityCheck(true).build())
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
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

        and: "Cleanup: restore ISL and remove BFD session"
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
        northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)
        northbound.setLinkBfd(islUtils.toLinkEnableBfd(isl, true))
        Wrappers.wait(WAIT_OFFSET) { assert islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED }

        when: "Delete the link"
        northbound.deleteLink(islUtils.toLinkParameters(isl))

        and: "Discover the removed link again"
        northbound.portUp(isl.srcSwitch.dpId, isl.srcPort)
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
}
