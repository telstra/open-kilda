package org.openkilda.functionaltests.spec.flows

import static org.junit.Assume.assumeTrue
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.Cookie

import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative

@Narrative("""A new flag of flow that indicates that flow shouldn't be rerouted in case of auto-reroute.
- In case of isl down such flow should be marked as DOWN.
- On Isl up event such flow shouldn't be re-routed as well.
  Instead kilda should verify that it's path is online and mark flow as UP.""")
class PinnedFlowSpec extends BaseSpecification {
    def "System doesn't reroute(automatically) pinned flow"() {
        given: "A pinned flow with alt path available"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue("No suiting switches found", false)
        def flow = flowHelper.randomFlow(switchPair)
        flow.pinned = true
        flowHelper.addFlow(flow)

        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.id))
        def altPath = switchPair.paths.find { it != currentPath }
        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.id)

        when: "Make alt path more preferable than current path"
        switchPair.paths.findAll { it != altPath }.each { pathHelper.makePathMorePreferable(altPath, it) }

        and: "Init reroute by bringing current path's ISL down"
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def newIsls = pathHelper.getInvolvedIsls(altPath)
        def islToBreak = currentIsls.find { !newIsls.contains(it) }

        def cookiesMap = involvedSwitches.collectEntries { sw ->
            [sw.dpId, northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                !Cookie.isDefaultRule(it.cookie)
            }*.cookie]
        }
        def metersMap = involvedSwitches.collectEntries { sw ->
            [sw.dpId, northbound.getAllMeters(sw.dpId).meterEntries.findAll {
                it.meterId > MAX_SYSTEM_RULE_METER_ID
            }*.meterId]
        }

        northbound.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)

        then: "Flow is not rerouted and marked as DOWN"
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getFlowStatus(flow.id).status == FlowState.DOWN
            assert pathHelper.convert(northbound.getFlowPath(flow.id)) == currentPath
        }

        and: "Rules and meters are not changed"
        def cookiesMapAfterReroute = involvedSwitches.collectEntries { sw ->
            [sw.dpId, northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                !Cookie.isDefaultRule(it.cookie)
            }*.cookie]
        }
        def metersMapAfterReroute = involvedSwitches.collectEntries { sw ->
            [sw.dpId, northbound.getAllMeters(sw.dpId).meterEntries.findAll {
                it.meterId > MAX_SYSTEM_RULE_METER_ID
            }*.meterId]
        }

        cookiesMap.sort() == cookiesMapAfterReroute.sort()
        metersMap.sort() == metersMapAfterReroute.sort()

        when: "The broken ISL is restored"
        northbound.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)

        then: "The flow is marked as UP and not rerouted"
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(islToBreak).get().state == IslChangeType.DISCOVERED
            assert northbound.getFlowStatus(flow.id).status == FlowState.UP
            assert pathHelper.convert(northbound.getFlowPath(flow.id)) == currentPath
        }

        and: "Cleanup: revert system to original state"
        flowHelper.deleteFlow(flow.id)
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        database.resetCosts()
    }

    def "System is able to reroute(intentional) pinned flow"() {
        given: "A pinned flow with alt path available"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue("No suiting switches found", false)
        def flow = flowHelper.randomFlow(switchPair)
        flow.pinned = true
        flowHelper.addFlow(flow)

        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.id))

        when: "Make another path more preferable"
        def newPath = switchPair.paths.find { it != currentPath }
        switchPair.paths.findAll { it != newPath }.each { pathHelper.makePathMorePreferable(newPath, it) }

        and: "Init reroute(manually)"
        def isl = pathHelper.getInvolvedIsls(currentPath).first()
        northbound.rerouteLinkFlows(isl.srcSwitch.dpId, isl.srcPort, isl.dstSwitch.dpId, isl.dstPort)

        then: "Flow is rerouted"
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getFlowStatus(flow.id).status == FlowState.UP
            assert pathHelper.convert(northbound.getFlowPath(flow.id)) == newPath
        }

        and: "Cleanup: revert system to original state"
        flowHelper.deleteFlow(flow.id)
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        database.resetCosts()
    }

    def "System doesn't allow to create pinned and protected flow at the same time"() {
        when: "Try to create pinned and protected flow"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue("No suiting switches found", false)
        def flow = flowHelper.randomFlow(switchPair)
        flow.pinned = true
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not create flow: Flow flags are not valid, unable to create pinned protected flow"
    }

    def "System doesn't allow to enable the protected path flag on a pinned flow"() {
        given: "A pinned flow"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue("No suiting switches found", false)
        def flow = flowHelper.randomFlow(switchPair)
        flow.pinned = true
        flowHelper.addFlow(flow)

        when: "Update flow: enable the allocateProtectedPath flag(allocateProtectedPath=true)"
        northbound.updateFlow(flow.id, flow.tap { it.allocateProtectedPath = true })

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not update flow: Flow flags are not valid, unable to update pinned protected flow"

        and: "Cleanup: Delete the flow"
        flowHelper.deleteFlow(flow.id)
    }
}
