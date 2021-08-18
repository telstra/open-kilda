package org.openkilda.functionaltests.spec.flows

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.cookie.Cookie
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative

import java.time.Instant
import java.util.concurrent.TimeUnit

@Narrative("""A new flag of flow that indicates that flow shouldn't be rerouted in case of auto-reroute.
- In case of isl down such flow should be marked as DOWN.
- On Isl up event such flow shouldn't be re-routed as well.
  Instead kilda should verify that it's path is online and mark flow as UP.""")
class PinnedFlowSpec extends HealthCheckSpecification {

    @Tidy
    def "Able to CRUD pinned flow"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flow.pinned = true
        flowHelperV2.addFlow(flow)

        then: "Pinned flow is created"
        def flowInfo = northboundV2.getFlow(flow.flowId)
        flowInfo.pinned

        when: "Update the flow (pinned=false)"
        northboundV2.updateFlow(flowInfo.flowId, flowHelperV2.toRequest(flowInfo.tap { it.pinned = false }))

        then: "The pinned option is disabled"
        def newFlowInfo = northboundV2.getFlow(flow.flowId)
        !newFlowInfo.pinned
        Instant.parse(flowInfo.lastUpdated) < Instant.parse(newFlowInfo.lastUpdated)

        cleanup: "Delete the flow"
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    def "Able to CRUD unmetered one-switch pinned flow"() {
        when: "Create a flow"
        def sw = topology.getActiveSwitches().first()
        def flow = flowHelperV2.singleSwitchFlow(sw)
        flow.maximumBandwidth = 0
        flow.ignoreBandwidth = true
        flow.pinned = true
        flowHelperV2.addFlow(flow)

        then: "Pinned flow is created"
        def flowInfo = northboundV2.getFlow(flow.flowId)
        flowInfo.pinned

        when: "Update the flow (pinned=false)"
        northboundV2.updateFlow(flowInfo.flowId, flowHelperV2.toRequest(flowInfo.tap { it.pinned = false }))

        then: "The pinned option is disabled"
        def newFlowInfo = northboundV2.getFlow(flow.flowId)
        !newFlowInfo.pinned
        Instant.parse(flowInfo.lastUpdated) < Instant.parse(newFlowInfo.lastUpdated)

        cleanup: "Delete the flow"
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    def "System doesn't reroute(automatically) pinned flow when flow path is partially broken"() {
        given: "A pinned flow going through a long not preferable path"
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue(false, "No suiting switches found")
        List<List<PathNode>> allPaths = database.getPaths(switchPair.src.dpId, switchPair.dst.dpId)*.path
        def longestPath = allPaths.max { it.size() }
        allPaths.findAll { it != longestPath }.collect { pathHelper.makePathMorePreferable(longestPath, it) }
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.pinned = true
        flowHelperV2.addFlow(flow)

        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.flowId))
        def altPath = switchPair.paths.findAll { it != currentPath }.min { it.size() }
        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.flowId)

        when: "Make alt path more preferable than current path"
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
        switchPair.paths.findAll { it != altPath }.each { pathHelper.makePathMorePreferable(altPath, it) }

        and: "Init reroute by bringing current path's ISL down one by one"
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def newIsls = pathHelper.getInvolvedIsls(altPath)
        def islsToBreak = currentIsls.findAll { !newIsls.contains(it) }

        def cookiesMap = involvedSwitches.collectEntries { sw ->
            [sw.dpId, northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                !new Cookie(it.cookie).serviceFlag
            }*.cookie]
        }
        def metersMap = involvedSwitches.findAll { it.ofVersion != "OF_12" }.collectEntries { sw ->
            [sw.dpId, northbound.getAllMeters(sw.dpId).meterEntries.findAll {
                it.meterId > MAX_SYSTEM_RULE_METER_ID
            }*.meterId]
        }

        antiflap.portDown(islsToBreak[0].srcSwitch.dpId, islsToBreak[0].srcPort)

        then: "Flow is not rerouted and marked as DOWN when the first ISL is broken"
        Wrappers.wait(WAIT_OFFSET) {
            Wrappers.timedLoop(2) {
                assert northboundV2.getFlow(flow.flowId).status == FlowState.DOWN.toString()
                //do not check history here. In parallel environment it may be overriden by 'up' event on another island
                assert pathHelper.convert(northbound.getFlowPath(flow.flowId)) == currentPath
            }
        }
        islsToBreak[1..-1].each { islToBreak ->
            antiflap.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        }

        and: "Rules and meters are not changed"
        def cookiesMapAfterReroute = involvedSwitches.collectEntries { sw ->
            [sw.dpId, northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                !new Cookie(it.cookie).serviceFlag
            }*.cookie]
        }
        def metersMapAfterReroute = involvedSwitches.findAll { it.ofVersion != "OF_12" }.collectEntries { sw ->
            [sw.dpId, northbound.getAllMeters(sw.dpId).meterEntries.findAll {
                it.meterId > MAX_SYSTEM_RULE_METER_ID
            }*.meterId]
        }

        cookiesMap.sort() == cookiesMapAfterReroute.sort()
        metersMap.sort() == metersMapAfterReroute.sort()

        when: "The broken ISLs are restored one by one"
        islsToBreak[0..-2].each { islToBreak ->
            antiflap.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        }
        TimeUnit.SECONDS.sleep(rerouteDelay)
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            islsToBreak[0..-2].each { assert islUtils.getIslInfo(it).get().state == IslChangeType.DISCOVERED }
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.DOWN
            assert pathHelper.convert(northbound.getFlowPath(flow.flowId)) == currentPath
        }

        then: "Flow is marked as UP when the last ISL is restored"
        antiflap.portUp(islsToBreak[-1].srcSwitch.dpId, islsToBreak[-1].srcPort)
        Wrappers.wait(WAIT_OFFSET * 2) {
            assert islUtils.getIslInfo(islsToBreak[-1]).get().state == IslChangeType.DISCOVERED
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP
            assert pathHelper.convert(northbound.getFlowPath(flow.flowId)) == currentPath
        }
        def islsAreUp = true

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
        if (islsToBreak && !islsAreUp) {
            islsToBreak.each { antiflap.portUp(it.srcSwitch.dpId, it.srcPort) }
            Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
                assert northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2
            }
        }
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
        database.resetCosts(topology.isls)
    }

    @Tidy
    def "System is not rerouting pinned flow when 'reroute link flows' is called"() {
        given: "A pinned flow with alt path available"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue(false, "No suiting switches found")
        def flow = flowHelperV2.randomFlow(switchPair).tap { it.pinned = true }
        flowHelperV2.addFlow(flow)
        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.flowId))

        when: "Make another path more preferable"
        def newPath = switchPair.paths.find { it != currentPath }
        switchPair.paths.findAll { it != newPath }.each { pathHelper.makePathMorePreferable(newPath, it) }

        and: "Init reroute of all flows that go through pinned flow's isl"
        def isl = pathHelper.getInvolvedIsls(currentPath).first()
        def affectedFlows = northbound.rerouteLinkFlows(isl.srcSwitch.dpId, isl.srcPort, isl.dstSwitch.dpId, isl.dstPort)

        then: "Flow is not rerouted (but still present in reroute response)"
        affectedFlows == [flow.flowId]
        Wrappers.timedLoop(4) {
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP
            assert pathHelper.convert(northbound.getFlowPath(flow.flowId)) == currentPath
        }

        cleanup: "Revert system to original state"
        flow && flowHelperV2.deleteFlow(flow.flowId)
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
        database.resetCosts(topology.isls)
    }

    @Tidy
    def "System returns error if trying to intentionally reroute a pinned flow"() {
        given: "A pinned flow with alt path available"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue(false, "No suiting switches found")
        def flow = flowHelperV2.randomFlow(switchPair).tap { it.pinned = true }
        flowHelperV2.addFlow(flow)
        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.flowId))

        when: "Make another path more preferable"
        def newPath = switchPair.paths.find { it != currentPath }
        switchPair.paths.findAll { it != newPath }.each { pathHelper.makePathMorePreferable(newPath, it) }

        and: "Init manual reroute"
        northboundV2.rerouteFlow(flow.flowId)

        then: "Error is returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.UNPROCESSABLE_ENTITY
        with(e.responseBodyAsString.to(MessageError)) {
            errorMessage == "Could not reroute flow"
            errorDescription == "Can't reroute pinned flow"
        }

        cleanup: "Revert system to original state"
        flow && flowHelperV2.deleteFlow(flow.flowId)
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
    }

    @Tidy
    def "System doesn't allow to create pinned and protected flow at the same time"() {
        when: "Try to create pinned and protected flow"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue(false, "No suiting switches found")
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.pinned = true
        flow.allocateProtectedPath = true
        flowHelperV2.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not create flow"
        errorDetails.errorDescription == "Flow flags are not valid, unable to process pinned protected flow"

        cleanup:
        !exc && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    def "System doesn't allow to enable the protected path flag on a pinned flow"() {
        given: "A pinned flow"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue(false, "No suiting switches found")
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.pinned = true
        flowHelperV2.addFlow(flow)

        when: "Update flow: enable the allocateProtectedPath flag(allocateProtectedPath=true)"
        northboundV2.updateFlow(flow.flowId, flow.tap { it.allocateProtectedPath = true })

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not update flow"
        errorDetails.errorDescription == "Flow flags are not valid, unable to process pinned protected flow"

        cleanup: "Delete the flow"
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    @Tags([LOW_PRIORITY])
    def "System doesn't allow to create pinned and protected flow at the same time [v1 api]"() {
        when: "Try to create pinned and protected flow"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue(false, "No suiting switches found")
        def flow = flowHelper.randomFlow(switchPair)
        flow.pinned = true
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not create flow"
        errorDetails.errorDescription == "Flow flags are not valid, unable to process pinned protected flow"

        cleanup:
        !exc && flowHelper.deleteFlow(flow.id)
    }

    @Tidy
    @Tags([LOW_PRIORITY])
    def "System doesn't allow to enable the protected path flag on a pinned flow [v1 api]"() {
        given: "A pinned flow"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue(false, "No suiting switches found")
        def flow = flowHelper.randomFlow(switchPair)
        flow.pinned = true
        flowHelper.addFlow(flow)

        when: "Update flow: enable the allocateProtectedPath flag(allocateProtectedPath=true)"
        northbound.updateFlow(flow.id, flow.tap { it.allocateProtectedPath = true })

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not update flow"
        errorDetails.errorDescription == "Flow flags are not valid, unable to process pinned protected flow"

        cleanup:
        flow && flowHelper.deleteFlow(flow.id)
    }
}
