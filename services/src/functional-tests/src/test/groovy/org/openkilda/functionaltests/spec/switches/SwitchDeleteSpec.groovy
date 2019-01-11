package org.openkilda.functionaltests.spec.switches

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.IslInfoData
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.links.LinkParametersDto
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.springframework.web.client.HttpClientErrorException

class SwitchDeleteSpec extends BaseSpecification {
    def "Cannot delete nonexistent switch"() {
        given: "Nonexistent switch ID"
        def switchId = new SwitchId("de:ad:be:ef:99:99:99:99")

        when: "Try to delete nonexistent switch"
        northbound.deleteSwitch(switchId, false)

        then: "Got 404 NotFound"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
    }

    def "Cannot delete active switch"() {
        given: "An active switch"
        def switchId = topology.getActiveSwitches()[0].dpId

        when: "Try to delete active switch"
        northbound.deleteSwitch(switchId, false)

        then: "Got 400 BadRequest because switch must be deactivated"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.contains("Switch '$switchId' is in 'Active' state")
    }

    def "Cannot delete inactive switch with active ISL"() {
        requireProfiles("virtual")

        given: "An inactive switch with active ISL"
        def isl = northbound.activeLinks[0]
        def switchId = isl.source.switchId
        lockKeeper.knockoutSwitch(switchId)
        Wrappers.wait(WAIT_OFFSET) {
            !northbound.activeSwitches.any { it.switchId == switchId}
        }

        when: "Try to delete inactive switch with active ISL"
        northbound.deleteSwitch(switchId, false)

        then: "Got 400 BadRequest because switch has active ISL"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.matches(".*Switch '$switchId' has \\d+ active links\\. Unplug and remove them first.*")

        and: "Cleanup: Activate the switch back"
        lockKeeper.reviveSwitch(switchId)
        Wrappers.wait(WAIT_OFFSET) {
            northbound.activeSwitches.any { it.switchId == switchId }
        }
    }

    def "Cannot delete inactive switch with inactive ISLs"() {
        requireProfiles("virtual")

        given: "An inactive switch with inactive ISLs"
        def switchId = topology.getActiveSwitches()[0].dpId
        // get all active ISLs on switch
        def activeSwitchLinks = northbound
                .getAllSwitchLinks(switchId)
                .findAll { it.state == IslChangeType.DISCOVERED }
        // deactivate all active ISLs on switch
        downLinkPorts(activeSwitchLinks)
        Wrappers.wait(WAIT_OFFSET) {
            northbound.getAllSwitchLinks(switchId).every { it.state != IslChangeType.DISCOVERED }
        }

        // deactivate switch
        lockKeeper.knockoutSwitch(switchId)
        Wrappers.wait(WAIT_OFFSET) {
            !northbound.activeSwitches.any { it.switchId == switchId }
        }

        when: "Try to delete inactive switch with inactive ISLs"
        northbound.deleteSwitch(switchId, false)

        then: "Got 400 BadRequest because switch has inactive ISLs"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.matches(".*Switch '$switchId' has \\d+ inactive links\\. Remove them first.*")

        and: "Cleanup: Activate the switch back"
        lockKeeper.reviveSwitch(switchId)
        Wrappers.wait(WAIT_OFFSET) {
            northbound.activeSwitches.any { it.switchId == switchId }
        }
        upLinkPorts(activeSwitchLinks)
        Wrappers.wait(antiflapCooldown + discoveryInterval + WAIT_OFFSET) {
            northbound
                    .getAllSwitchLinks(switchId)
                    .findAll { isl -> activeSwitchLinks.any {
                it.source == isl.source && it.destination == isl.destination } }
                    .every{ it.state == IslChangeType.DISCOVERED }
        }
    }

    def "Cannot delete inactive switch with single flow"() {
        requireProfiles("virtual")

        given: "An active switch with flow"
        def sw = topology.getActiveSwitches()[0]
        def flow = flowHelper.singleSwitchFlow(sw)
        northbound.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { northbound.getFlowStatus(flow.id).status == FlowState.UP }

        when: "Deactivate this switch"
        lockKeeper.knockoutSwitch(sw.dpId)
        Wrappers.wait(WAIT_OFFSET) {
            !northbound.activeSwitches.any { it.switchId == sw.dpId}
        }

        and: "Try to delete inactive switch with flow"
        northbound.deleteSwitch(sw.dpId, false)

        then: "Got 400 BadRequest because switch has flow"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.matches(".*Switch '$sw.dpId' has \\d+ assigned flows.*")

        and: "Cleanup: Activate the switch back and remove flow"
        lockKeeper.reviveSwitch(sw.dpId)
        Wrappers.wait(WAIT_OFFSET) {
            northbound.activeSwitches.any { it.switchId == sw.dpId }
        }
        northbound.deleteFlow(flow.id)
    }

    def "Cannot delete inactive switch with flow"() {
        requireProfiles("virtual")

        given: "An active switch with flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.getActiveSwitches()[0..1]
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        northbound.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { northbound.getFlowStatus(flow.id).status == FlowState.UP }

        when: "Deactivate this switch"
        lockKeeper.knockoutSwitch(srcSwitch.getDpId())
        Wrappers.wait(WAIT_OFFSET) {
            !northbound.activeSwitches.any { it.switchId == srcSwitch.dpId}
        }

        and: "Try to delete inactive switch with flow"
        northbound.deleteSwitch(srcSwitch.getDpId(), false)

        then: "Got 400 BadRequest because switch has flow"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.matches(".*Switch '$srcSwitch.dpId' has \\d+ assigned flows.*")

        and: "Cleanup: Activate the switch back and remove flow"
        lockKeeper.reviveSwitch(srcSwitch.getDpId())
        Wrappers.wait(WAIT_OFFSET) {
            northbound.activeSwitches.any { it.switchId == srcSwitch.dpId }
        }
        northbound.deleteFlow(flow.id)
    }

    def "Can delete inactive switch without any ISLs"() {
        requireProfiles("virtual")

        given: "An inactive switch without ISLs"
        def switchId = topology.getActiveSwitches()[0].dpId
        // get all active ISLs on switch
        def expectedSwitchLinks = northbound
                .getAllSwitchLinks(switchId)
                .findAll { it.state == IslChangeType.DISCOVERED }
        // deactivate all active ISLs on switch
        downLinkPorts(expectedSwitchLinks)
        Wrappers.wait(WAIT_OFFSET) {
            northbound.getAllSwitchLinks(switchId).every { it.state != IslChangeType.DISCOVERED }
        }

        // delete all ISLs on switch
        northbound.getAllSwitchLinks(switchId).each {
            northbound.deleteLink(new LinkParametersDto(
                    it.source.switchId.toString(), it.source.portNo,
                    it.destination.switchId.toString(), it.destination.portNo))
        }
        Wrappers.wait(WAIT_OFFSET) {
            northbound.getAllSwitchLinks(switchId).empty
        }

        // deactivate switch
        lockKeeper.knockoutSwitch(switchId)
        Wrappers.wait(WAIT_OFFSET) {
            !northbound.activeSwitches.any { it.switchId == switchId }
        }

        when: "Try to delete inactive switch with inactive ISLs"
        def res = northbound.deleteSwitch(switchId, false)

        then: "Check that switch is actually deleted"
        res.deleted
        Wrappers.wait(WAIT_OFFSET) {
            !northbound.allSwitches.any { it.switchId == switchId }
        }

        and: "Cleanup: Activate the switch back and restore ISLs"
        // restore switch
        lockKeeper.reviveSwitch(switchId)
        Wrappers.wait(WAIT_OFFSET) {
            northbound.activeSwitches.any { it.switchId == switchId }
        }

        // restore ISLs
        upLinkPorts(expectedSwitchLinks)
        Wrappers.wait(antiflapCooldown + discoveryInterval + WAIT_OFFSET) {
            isLinksWereRestored(switchId, expectedSwitchLinks)
        }
    }

    def "Can delete active switch if using force delete"() {
        requireProfiles("virtual")

        given: "An active switch"
        def switchId = topology.getActiveSwitches()[0].dpId
        def expectedSwitchLinks = northbound
                .getAllSwitchLinks(switchId)
                .findAll { it.state == IslChangeType.DISCOVERED }

        when: "Try to force delete active switch with ISLs"
        def response = northbound.deleteSwitch(switchId, true)

        then: "Check that switch is actually deleted"
        response.deleted
        Wrappers.wait(WAIT_OFFSET) {
            !northbound.allSwitches.any { it.switchId == switchId }
        }

        and: "Cleanup: Restore the switch"
        lockKeeper.knockoutSwitch(switchId)
        lockKeeper.reviveSwitch(switchId)
        Wrappers.wait(WAIT_OFFSET) {
            northbound.activeSwitches.any { it.switchId == switchId }
        }

        downLinkPorts(expectedSwitchLinks)
        upLinkPorts(expectedSwitchLinks)
        Wrappers.wait(antiflapCooldown + discoveryInterval + WAIT_OFFSET) {
            isLinksWereRestored(switchId, expectedSwitchLinks)
        }
    }

    def downLinkPorts(List<IslInfoData> links) {
        links.collectMany { link -> [link.source, link.destination] }
                .each { northbound.portDown(it.switchId, it.portNo) }
    }

    def upLinkPorts(List<IslInfoData> links) {
        links.collectMany { link -> [link.source, link.destination] }
                .each { northbound.portUp(it.switchId, it.portNo) }
    }

    boolean isLinksWereRestored(SwitchId switchId, List<IslInfoData> expectedSwitchLinks) {
        def actualSwitchLinks = northbound.getAllSwitchLinks(switchId)

        return expectedSwitchLinks.every {
            def expectedIsl = it
            def actualIsl = actualSwitchLinks.findAll {
                it.source == expectedIsl.source && it.destination == expectedIsl.destination
            }

            actualIsl.size() == 1 && actualIsl.every { it.state == IslChangeType.DISCOVERED }
        }
    }
}
