package org.openkilda.functionaltests.spec.flows

import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowNotCreatedExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotUpdatedExpectedError
import org.openkilda.functionaltests.error.PinnedFlowNotReroutedExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.SwitchRulesFactory
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.cookie.Cookie
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

import java.time.Instant
import java.util.concurrent.TimeUnit


@Narrative("""A new flag of flow that indicates that flow shouldn't be rerouted in case of auto-reroute.
- In case of isl down such flow should be marked as DOWN.
- On Isl up event such flow shouldn't be re-routed as well.
  Instead kilda should verify that it's path is online and mark flow as UP.""")

class PinnedFlowSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    FlowFactory flowFactory
    @Autowired
    @Shared
    SwitchRulesFactory switchRulesFactory


    def "Able to CRUD pinned flow"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowFactory.getBuilder(srcSwitch, dstSwitch)
                .withPinned(true).build().create()

        then: "Pinned flow is created"
        def flowInfo = flow.retrieveDetails()
        flowInfo.pinned

        when: "Update the flow (pinned=false)"
        flow.update(flow.deepCopy().tap { it.pinned = false})

        then: "The pinned option is disabled"
        def newFlowInfo = flow.retrieveDetails()
        !newFlowInfo.pinned
        Instant.parse(flowInfo.lastUpdated) < Instant.parse(newFlowInfo.lastUpdated)
    }

    def "Able to CRUD unmetered one-switch pinned flow"() {
        when: "Create a flow"
        def sw = topology.getActiveSwitches().first()
        def flow = flowFactory.getBuilder(sw, sw)
        .withBandwidth(0)
        .withIgnoreBandwidth(true)
        .withPinned(true).build().create()

        then: "Pinned flow is created"
        def flowInfo = flow.retrieveDetails()
        flowInfo.pinned

        when: "Update the flow (pinned=false)"
        def flowNotPinned = flow.deepCopy().tap { it.pinned = false}
        flow.update(flowNotPinned)

        then: "The pinned option is disabled"
        def newFlowInfo = flow.retrieveDetails()
        !newFlowInfo.pinned
        Instant.parse(flowInfo.lastUpdated) < Instant.parse(newFlowInfo.lastUpdated)
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "System doesn't reroute(automatically) pinned flow when flow path is partially broken"() {
        given: "A pinned flow going through a long not preferable path"
        def switchPair = switchPairs.all().nonNeighbouring().withAtLeastNPaths(2).random()
        List<List<PathNode>> allPaths = database.getPaths(switchPair.src.dpId, switchPair.dst.dpId)*.path
        def longestPath = allPaths.max { it.size() }
        allPaths.findAll { it != longestPath }.collect { pathHelper.makePathMorePreferable(longestPath, it) }
        def flow = flowFactory.getBuilder(switchPair)
                .withPinned(true)
                .build().create()

        def allEntityPath = flow.retrieveAllEntityPaths()
        def currentPath = allEntityPath.getPathNodes()
        def altPath = switchPair.paths.findAll { it != currentPath }.min { it.size() }
        def involvedSwitches = allEntityPath.getInvolvedSwitches()

        when: "Make alt path more preferable than current path"
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
        switchPair.paths.findAll { it != altPath }.each { pathHelper.makePathMorePreferable(altPath, it) }

        and: "Init reroute by bringing current path's ISL down one by one"
        def currentIsls = flow.retrieveAllEntityPaths().flowPath.getInvolvedIsls()
        def newIsls = pathHelper.getInvolvedIsls(altPath)
        def islsToBreak = currentIsls.findAll { !newIsls.contains(it) }

        def cookiesMap = involvedSwitches.collectEntries { sw ->
            [sw.id, switchRulesFactory.get(sw).getRules().findAll {
                !new Cookie(it.cookie).serviceFlag
            }*.cookie]
        }
        def metersMap = involvedSwitches
                .findAll { northbound.getSwitch(it).ofVersion != "OF_12" }.collectEntries { sw ->
            [sw.id, northbound.getAllMeters(sw).meterEntries.findAll {
                it.meterId > MAX_SYSTEM_RULE_METER_ID
            }*.meterId]
        }

        islHelper.breakIsl(islsToBreak[0])

        then: "Flow is not rerouted and marked as DOWN when the first ISL is broken"
        Wrappers.wait(WAIT_OFFSET) {
            Wrappers.timedLoop(2) {
                assert flow.retrieveFlowStatus().status == FlowState.DOWN
                //do not check history here. In parallel environment it may be overriden by 'up' event on another island
                assert flow.retrieveAllEntityPaths().getPathNodes() == currentPath
            }
        }
        islHelper.breakIsls(islsToBreak[1..-1])

        and: "Rules and meters are not changed"
        def cookiesMapAfterReroute = involvedSwitches.collectEntries { sw ->
            [sw.id, northbound.getSwitchRules(sw).flowEntries.findAll {
                !new Cookie(it.cookie).serviceFlag
            }*.cookie]
        }

        def metersMapAfterReroute = involvedSwitches.findAll {
            northbound.getSwitch(it).ofVersion != "OF_12" }.collectEntries { sw ->
            [sw.id, northbound.getAllMeters(sw).meterEntries.findAll {
                it.meterId > MAX_SYSTEM_RULE_METER_ID
            }*.meterId]
        }

        cookiesMap.sort() == cookiesMapAfterReroute.sort()
        metersMap.sort() == metersMapAfterReroute.sort()

        when: "The broken ISLs are restored one by one"
        islHelper.restoreIsls(islsToBreak[0..-2])
        TimeUnit.SECONDS.sleep(rerouteDelay)
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            islsToBreak[0..-2].each { assert islUtils.getIslInfo(it).get().state == IslChangeType.DISCOVERED }
            assert flow.retrieveFlowStatus().status == FlowState.DOWN
            assert flow.retrieveAllEntityPaths().getPathNodes() == currentPath
        }

        and: "Restore the last ISL"
        islHelper.restoreIsl(islsToBreak[-1])

        then: "Flow is marked as UP when the last ISL is restored"
        Wrappers.wait(WAIT_OFFSET * 2) {
            assert flow.retrieveFlowStatus().status == FlowState.UP
            assert flow.retrieveAllEntityPaths().getPathNodes() == currentPath
        }
    }

    def "System is not rerouting pinned flow when 'reroute link flows' is called"() {
        given: "A pinned flow with alt path available"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        def flow = flowFactory.getBuilder(switchPair)
                .withPinned(true)
                .build().create()
        def initialPath =  flow.retrieveAllEntityPaths()

        when: "Make another path more preferable"
        def newPath = switchPair.paths.find { it != initialPath.getPathNodes() }
        switchPair.paths.findAll { it != newPath }.each { pathHelper.makePathMorePreferable(newPath, it) }

        and: "Init reroute of all flows that go through pinned flow's isl"
        def isl = initialPath.flowPath.getInvolvedIsls().first()
        def affectedFlows = northbound.rerouteLinkFlows(isl.srcSwitch.dpId, isl.srcPort, isl.dstSwitch.dpId, isl.dstPort)

        then: "Flow is not rerouted (but still present in reroute response)"
        affectedFlows == [flow.flowId]
        Wrappers.timedLoop(4) {
            assert flow.retrieveFlowStatus().status == FlowState.UP
            assert flow.retrieveAllEntityPaths().getPathNodes() == initialPath.getPathNodes()
        }
    }

    def "System returns error if trying to intentionally reroute a pinned flow"() {
        given: "A pinned flow with alt path available"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        def flow = flowFactory.getBuilder(switchPair)
                .withPinned(true)
                .build().create()
        def currentPath = flow.retrieveAllEntityPaths().getPathNodes()

        when: "Make another path more preferable"
        def newPath = switchPair.paths.find { it != currentPath }
        switchPair.paths.findAll { it != newPath }.each { pathHelper.makePathMorePreferable(newPath, it) }

        and: "Init manual reroute"
        flow.reroute()

        then: "Error is returned"
        def e = thrown(HttpClientErrorException)
        new PinnedFlowNotReroutedExpectedError().matches(e)
    }

    def "System doesn't allow to create pinned and protected flow at the same time"() {
        when: "Try to create pinned and protected flow"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(true)
                .withPinned(true)
                .build().create()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(~/Flow flags are not valid, unable to process pinned protected flow/).matches(exc)
    }

    def "System doesn't allow to enable the protected path flag on a pinned flow"() {
        given: "A pinned flow"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        def flow = flowFactory.getBuilder(switchPair)
                .withPinned(true)
                .build().create()

        when: "Update flow: enable the allocateProtectedPath flag(allocateProtectedPath=true)"
        flow.update(flow.tap{ it.allocateProtectedPath = true })

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotUpdatedExpectedError(~/Flow flags are not valid, unable to process pinned protected flow/).matches(exc)
    }

    @Tags([LOW_PRIORITY])
    def "System doesn't allow to create pinned and protected flow at the same time [v1 api]"() {
        when: "Try to create pinned and protected flow"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        def flow = flowFactory.getBuilder(switchPair)
                .withPinned(true)
                .withProtectedPath(true)
                .build().create()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(~/Flow flags are not valid, unable to process pinned protected flow/).matches(exc)
    }

    @Tags([LOW_PRIORITY])
    def "System doesn't allow to enable the protected path flag on a pinned flow [v1 api]"() {
        given: "A pinned flow"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        def flow = flowFactory.getBuilder(switchPair)
                .withPinned(true)
                .build().create()

        when: "Update flow: enable the allocateProtectedPath flag(allocateProtectedPath=true)"
        flow.update(flow.tap { it.allocateProtectedPath = true})

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotUpdatedExpectedError(~/Flow flags are not valid, unable to process pinned protected flow/).matches(exc)
    }
}
