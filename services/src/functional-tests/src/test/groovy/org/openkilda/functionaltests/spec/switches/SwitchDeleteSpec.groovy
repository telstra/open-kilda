package org.openkilda.functionaltests.spec.switches

import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType

import org.springframework.web.client.HttpClientErrorException
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

class SwitchDeleteSpec extends BaseSpecification {

    def "Unable to delete a nonexistent switch"() {
        when: "Try to delete a nonexistent switch"
        northbound.deleteSwitch(NON_EXISTENT_SWITCH_ID, false)

        then: "Get 404 NotFound error"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
    }

    def "Unable to delete an active switch"() {
        given: "An active switch"
        def switchId = topology.getActiveSwitches()[0].dpId

        when: "Try to delete the switch"
        northbound.deleteSwitch(switchId, false)

        then: "Get 400 BadRequest error because the switch must be deactivated first"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.contains("Switch '$switchId' is in 'Active' state")
    }

    @Tags(VIRTUAL)
    def "Unable to delete an inactive switch with active ISLs"() {
        given: "An inactive switch with ISLs"
        def sw = topology.getActiveSwitches()[0]
        def swIsls = topology.getRelatedIsls(sw)

        // deactivate switch
        lockKeeper.knockoutSwitch(sw.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert !northbound.activeSwitches.any { it.switchId == sw.dpId } }

        when: "Try to delete the switch"
        northbound.deleteSwitch(sw.dpId, false)

        then: "Get 400 BadRequest error because the switch has ISLs"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.matches(".*Switch '$sw.dpId' has ${swIsls.size() * 2} active links\\. " +
                "Unplug and remove them first.*")

        and: "Cleanup: activate the switch back"
        lockKeeper.reviveSwitch(sw.dpId)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.activeSwitches.any { it.switchId == sw.dpId }

            def links = northbound.getAllLinks()
            swIsls.each { assert islUtils.getIslInfo(links, it).get().state == IslChangeType.DISCOVERED }
        }
    }

    @Tags(VIRTUAL)
    def "Unable to delete an inactive switch with inactive ISLs (ISL ports are down)"() {
        given: "An inactive switch with ISLs"
        def sw = topology.getActiveSwitches()[0]
        def swIsls = topology.getRelatedIsls(sw)

        // deactivate all active ISLs on switch
        swIsls.each { northbound.portDown(sw.dpId, it.srcPort) }
        Wrappers.wait(WAIT_OFFSET) {
            swIsls.each { assert islUtils.getIslInfo(it).get().state == IslChangeType.FAILED }
        }

        // deactivate switch
        lockKeeper.knockoutSwitch(sw.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert !northbound.activeSwitches.any { it.switchId == sw.dpId } }

        when: "Try to delete the switch"
        northbound.deleteSwitch(sw.dpId, false)

        then: "Get 400 BadRequest error because the switch has ISLs"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.matches(".*Switch '$sw.dpId' has ${swIsls.size() * 2} inactive links\\. " +
                "Remove them first.*")

        and: "Cleanup: activate the switch back and reset costs"
        lockKeeper.reviveSwitch(sw.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert northbound.activeSwitches.any { it.switchId == sw.dpId } }

        swIsls.each { northbound.portUp(sw.dpId, it.srcPort) }
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            swIsls.each { assert islUtils.getIslInfo(links, it).get().state == IslChangeType.DISCOVERED }
        }
        database.resetCosts()
    }

    @Unroll
    @Tags(VIRTUAL)
    def "Unable to delete an inactive switch with a #flowType flow assigned"() {
        given: "A flow going through a switch"
        flowHelper.addFlow(flow)

        when: "Deactivate the switch"
        lockKeeper.knockoutSwitch(flow.source.datapath)
        Wrappers.wait(WAIT_OFFSET) { assert !northbound.activeSwitches.any { it.switchId == flow.source.datapath } }

        and: "Try to delete the switch"
        northbound.deleteSwitch(flow.source.datapath, false)

        then: "Got 400 BadRequest error because the switch has the flow assigned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.matches(".*Switch '${flow.source.datapath}' has 1 assigned flows: \\[${flow.id}\\].*")

        and: "Cleanup: activate the switch back and remove the flow"
        lockKeeper.reviveSwitch(flow.source.datapath)
        Wrappers.wait(WAIT_OFFSET) { assert northbound.activeSwitches.any { it.switchId == flow.source.datapath } }
        flowHelper.deleteFlow(flow.id)

        where:
        flowType        | flow
        "single-switch" | getFlowHelper().singleSwitchFlow(getTopology().getActiveSwitches()[0])
        "casual"        | getFlowHelper().randomFlow(*getTopology().getActiveSwitches()[0..1])
    }

    @Tags(VIRTUAL)
    def "Able to delete an inactive switch without any ISLs"() {
        given: "An inactive switch without any ISLs"
        def sw = topology.getActiveSwitches()[0]
        def swIsls = topology.getRelatedIsls(sw)

        // deactivate all active ISLs on switch
        swIsls.each { northbound.portDown(sw.dpId, it.srcPort) }
        Wrappers.wait(WAIT_OFFSET) {
            swIsls.each { assert islUtils.getIslInfo(it).get().state == IslChangeType.FAILED }
        }

        // delete all ISLs on switch
        swIsls.each {
            northbound.deleteLink(islUtils.toLinkParameters(it))
        }

        // deactivate switch
        lockKeeper.knockoutSwitch(sw.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert !northbound.activeSwitches.any { it.switchId == sw.dpId } }

        when: "Try to delete the switch"
        def response = northbound.deleteSwitch(sw.dpId, false)

        then: "The switch is actually deleted"
        response.deleted
        Wrappers.wait(WAIT_OFFSET) { assert !northbound.allSwitches.any { it.switchId == sw.dpId } }

        and: "Cleanup: activate the switch back, restore ISLs and reset costs"
        // restore switch
        lockKeeper.reviveSwitch(sw.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert northbound.activeSwitches.any { it.switchId == sw.dpId } }

        // restore ISLs
        swIsls.each { northbound.portUp(sw.dpId, it.srcPort) }
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            swIsls.each { assert islUtils.getIslInfo(links, it).get().state == IslChangeType.DISCOVERED }
        }
        database.resetCosts()
    }

    @Tags(VIRTUAL)
    def "Able to delete an active switch with active ISLs if using force delete"() {
        given: "An active switch with active ISLs"
        def sw = topology.getActiveSwitches()[0]
        def swIsls = topology.getRelatedIsls(sw)

        when: "Try to force delete the switch"
        def response = northbound.deleteSwitch(sw.dpId, true)

        then: "The switch is actually deleted"
        response.deleted
        Wrappers.wait(WAIT_OFFSET) {
            assert !northbound.allSwitches.any { it.switchId == sw.dpId }

            def links = northbound.getAllLinks()
            swIsls.each {
                assert !islUtils.getIslInfo(links, it)
                assert !islUtils.getIslInfo(links, it.reversed)
            }
        }

        and: "Cleanup: restore the switch, ISLs and reset costs"
        // restore switch
        lockKeeper.knockoutSwitch(sw.dpId)
        lockKeeper.reviveSwitch(sw.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert northbound.activeSwitches.any { it.switchId == sw.dpId } }

        // restore ISLs
        swIsls.each { northbound.portDown(it.srcSwitch.dpId, it.srcPort) }
        TimeUnit.SECONDS.sleep(antiflapMin)
        swIsls.each { northbound.portUp(it.srcSwitch.dpId, it.srcPort) }
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            swIsls.collectMany { [it, it.reversed] }
                  .each { assert islUtils.getIslInfo(links, it).get().state == IslChangeType.DISCOVERED }
        }
        database.resetCosts()
    }
}
