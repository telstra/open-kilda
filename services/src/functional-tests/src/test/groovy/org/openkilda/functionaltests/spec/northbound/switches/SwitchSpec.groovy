package org.openkilda.functionaltests.spec.northbound.switches

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

class SwitchSpec extends BaseSpecification {

    def "Delete meter with invalid ID"() {
        given: "A switch"
        def switchId = topology.getActiveSwitches()[0].dpId

        when: "Try to delete meter with invalid ID"
        northbound.deleteMeter(switchId, -1)

        then: "Got BadRequest because meter ID is invalid"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
    }

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
        exc.responseBodyAsString.contains("Switch must be deactivated before delete.")
    }

    def "Cannot delete inactive switch with active ISL"() {
        requireProfiles("virtual")

        given: "An inactive switch with active ISL"
        def isl = northbound.activeLinks[0]
        def switchId = isl.source.switchId
        lockKeeper.knockoutSwitch(switchId)
        assert Wrappers.wait(WAIT_OFFSET) {
            !northbound.activeSwitches.any { it.switchId == switchId}
        }

        when: "Try to delete inactive switch with active ISL"
        northbound.deleteSwitch(switchId, false)

        then: "Got 400 BadRequest because switch has active ISL"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.matches(".*Switch has \\d+ active links\\. Unplug and remove them first.*")

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
        assert Wrappers.wait(discoveryTimeout) {
            northbound.getAllSwitchLinks(switchId).every { it.state != IslChangeType.DISCOVERED }
        }

        // deactivate switch
        lockKeeper.knockoutSwitch(switchId)
        assert Wrappers.wait(WAIT_OFFSET) {
            !northbound.activeSwitches.any { it.switchId == switchId }
        }

        when: "Try to delete inactive switch with inactive ISLs"
        northbound.deleteSwitch(switchId, false)

        then: "Got 400 BadRequest because switch has inactive ISLs"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.matches(".*Switch has \\d+ inactive links\\. Remove them first.*")

        and: "Cleanup: Activate the switch back"
        lockKeeper.reviveSwitch(switchId)
        Wrappers.wait(WAIT_OFFSET) {
            northbound.activeSwitches.any { it.switchId == switchId }
        }
        upLinkPorts(activeSwitchLinks)
        Wrappers.wait(discoveryTimeout) {
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
        assert Wrappers.wait(WAIT_OFFSET) { northbound.getFlowStatus(flow.id).status == FlowState.UP }

        when: "Deactivate this switch"
        lockKeeper.knockoutSwitch(sw.dpId)
        assert Wrappers.wait(WAIT_OFFSET) {
            !northbound.activeSwitches.any { it.switchId == sw.dpId}
        }

        and: "Try to delete inactive switch with flow"
        northbound.deleteSwitch(sw.dpId, false)

        then: "Got 400 BadRequest because switch has flow"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.matches(".*Switch has \\d+ assigned flows.*")

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
        assert Wrappers.wait(WAIT_OFFSET) { northbound.getFlowStatus(flow.id).status == FlowState.UP }

        when: "Deactivate this switch"
        lockKeeper.knockoutSwitch(srcSwitch.getDpId())
        assert Wrappers.wait(WAIT_OFFSET) {
            !northbound.activeSwitches.any { it.switchId == srcSwitch.dpId}
        }

        and: "Try to delete inactive switch with flow"
        northbound.deleteSwitch(srcSwitch.getDpId(), false)

        then: "Got 400 BadRequest because switch has flow"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.matches(".*Switch has \\d+ assigned flows.*")

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
        def activeSwitchLinks = northbound
                .getAllSwitchLinks(switchId)
                .findAll { it.state == IslChangeType.DISCOVERED }
        // deactivate all active ISLs on switch
        downLinkPorts(activeSwitchLinks)
        assert Wrappers.wait(discoveryTimeout) {
            northbound.getAllSwitchLinks(switchId).every { it.state != IslChangeType.DISCOVERED }
        }

        // delete all ISLs on switch
        northbound.getAllSwitchLinks(switchId).each {
            northbound.deleteLink(new LinkParametersDto(
                    it.source.switchId.toString(), it.source.portNo,
                    it.destination.switchId.toString(), it.destination.portNo))
        }
        assert Wrappers.wait(WAIT_OFFSET) {
            northbound.getAllSwitchLinks(switchId).empty
        }

        // deactivate switch
        lockKeeper.knockoutSwitch(switchId)
        assert Wrappers.wait(WAIT_OFFSET) {
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
        Wrappers.wait(WAIT_OFFSET) {discoveryTimeout
            northbound.activeSwitches.any { it.switchId == switchId }
        }

        // restore ISLs
        upLinkPorts(activeSwitchLinks)
        Wrappers.wait(discoveryTimeout) {
            northbound
                    .getAllSwitchLinks(switchId)
                    .findAll { isl -> activeSwitchLinks.any {
                it.source == isl.source && it.destination == isl.destination } }
                    .every{ it.state == IslChangeType.DISCOVERED }
        }
    }

    def "Can delete active switch if using force delete"() {
        requireProfiles("virtual")

        given: "An active switch"
        def switchId = topology.getActiveSwitches()[1].dpId
        def activeSwitchLinks = northbound
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
        Wrappers.wait(discoveryTimeout) {
            northbound
                    .getAllSwitchLinks(switchId)
                    .findAll { isl -> activeSwitchLinks.any {
                it.source == isl.source && it.destination == isl.destination } }
                    .every{ it.state == IslChangeType.DISCOVERED }
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
}
