package org.openkilda.functionaltests.spec.flows

import groovy.util.logging.Slf4j
import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.StatusInfo
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2
import org.openkilda.northbound.dto.v2.switches.SwitchPatchDto
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.tools.FlowTrafficExamBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared

import javax.inject.Provider

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_PROPS_DB_RESET
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_ACTION
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_FAIL
import static org.openkilda.functionaltests.helpers.SwitchHelper.isDefaultMeter
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.model.cookie.CookieBase.CookieType.SERVICE_OR_FLOW_SEGMENT
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.PROTECTED_PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

@Slf4j
@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/solutions/protected-paths")
@Narrative("""Protected path - it is pre-calculated, reserved, and deployed (except ingress rule),
so we can switch traffic fast.

- flow object is extended with a boolean parameter 'allocate_protected_path' with values false(default)
- /flows/{flow-id}/path returns 'main_path' + 'protected_path'.

System can start to use protected path in two case:
- main path is down;
- we send the 'swap' request for a flow with protected path('/v1/flows/{flow_id}/swap')

A flow has the status degraded in case when the main path is up and the protected path is down.

Main and protected paths can't use the same link.""")
class ProtectedPathSpec extends HealthCheckSpecification {

    @Autowired @Shared
    Provider<TraffExamService> traffExamProvider

    @Tags(LOW_PRIORITY)
    def "Able to create a flow with protected path when maximumBandwidth=#bandwidth, vlan=#vlanId"() {
        given: "Two active not neighboring switches with two diverse paths at least"
        def switchPair = switchPairs.all().nonNeighbouring().withAtLeastNNonOverlappingPaths(2).random()

        when: "Create flow with protected path"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.allocateProtectedPath = true
        flow.maximumBandwidth = bandwidth
        flow.ignoreBandwidth = bandwidth == 0
        flow.source.vlanId = vlanId
        flowHelperV2.addFlow(flow)

        then: "Flow is created with protected path"
        def flowPathInfo = northbound.getFlowPath(flow.flowId)
        flowPathInfo.protectedPath

        and: "Rules for main and protected paths are created"
        Wrappers.wait(WAIT_OFFSET) { flowHelper.verifyRulesOnProtectedFlow(flow.flowId) }

        and: "Validation of flow must be successful"
        northbound.validateFlow(flow.flowId).each { direction ->
            assert direction.discrepancies.empty
        }

        where:
        bandwidth | vlanId
        1000      | 3378
        0         | 3378
        1000      | 0
        0         | 0
    }

    @Tags([SMOKE_SWITCHES, SMOKE])
    def "Able to enable/disable protected path on a flow"() {
        given: "Two active not neighboring switches with two diverse paths at least"
        def switchPair = switchPairs.all().nonNeighbouring().withAtLeastNNonOverlappingPaths(2).random()

        when: "Create flow without protected path"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.allocateProtectedPath = false
        flowHelperV2.addFlow(flow)

        then: "Flow is created without protected path"
        !northbound.getFlowPath(flow.flowId).protectedPath
        def flowInfo = northboundV2.getFlow(flow.flowId)
        !flowInfo.statusDetails

        when: "Update flow: enable protected path(allocateProtectedPath=true)"
        def currentLastUpdate = flowInfo.lastUpdated
        flowHelperV2.updateFlow(flow.flowId, flow.tap { it.allocateProtectedPath = true })

        then: "Protected path is enabled"
        def flowPathInfoAfterUpdating = northbound.getFlowPath(flow.flowId)
        flowPathInfoAfterUpdating.protectedPath
        northboundV2.getFlow(flow.flowId).statusDetails
        def flowInfoFromDb = database.getFlow(flow.flowId)
        def protectedForwardCookie = flowInfoFromDb.protectedForwardPath.cookie.value
        def protectedReverseCookie = flowInfoFromDb.protectedReversePath.cookie.value

        currentLastUpdate < northboundV2.getFlow(flow.flowId).lastUpdated

        and: "Rules for main and protected paths are created"
        Wrappers.wait(WAIT_OFFSET) { flowHelper.verifyRulesOnProtectedFlow(flow.flowId) }

        when: "Update flow: disable protected path(allocateProtectedPath=false)"
        def protectedFlowPath = northbound.getFlowPath(flow.flowId).protectedPath.forwardPath
        flowHelperV2.updateFlow(flow.flowId, flow.tap { it.allocateProtectedPath = false })

        then: "Protected path is disabled"
        !northbound.getFlowPath(flow.flowId).protectedPath
        !northboundV2.getFlow(flow.flowId).statusDetails

        and: "Rules for protected path are deleted"
        Wrappers.wait(WAIT_OFFSET) {
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP
            protectedFlowPath.each { sw ->
                def rules = northbound.getSwitchRules(sw.switchId).flowEntries.findAll {
                    !new Cookie(it.cookie).serviceFlag
                }
                assert rules.every { it != protectedForwardCookie && it != protectedReverseCookie }
            }
        }
    }

    @Tags(SMOKE)
    def "Able to swap main and protected paths manually"() {
        given: "A simple flow"
        def switchPair = switchPairs.all()
                .nonNeighbouring()
                .withTraffgensOnBothEnds()
                .withPathHavingAtLeastNSwitches(4)
                .random()
        def flow = flowHelperV2.randomFlow(switchPair, true)
        flow.allocateProtectedPath = false
        flowHelperV2.addFlow(flow)
        assert !northbound.getFlowPath(flow.flowId).protectedPath

        and: "Cookies are created by flow"
        def createdCookiesSrcSw = northbound.getSwitchRules(switchPair.src.dpId).flowEntries.findAll {
            !new Cookie(it.cookie).serviceFlag
        }*.cookie
        def createdCookiesDstSw = northbound.getSwitchRules(switchPair.dst.dpId).flowEntries.findAll {
            !new Cookie(it.cookie).serviceFlag
        }*.cookie
        def srcSwProps = switchHelper.getCachedSwProps(switchPair.src.dpId)
        def amountOfserver42Rules = srcSwProps.server42FlowRtt ? 1 : 0
        def amountOfFlowRulesSrcSw = 3 + amountOfserver42Rules
        if (srcSwProps.server42FlowRtt && flow.source.vlanId) {
            amountOfFlowRulesSrcSw += 1
        }
        assert createdCookiesSrcSw.size() == amountOfFlowRulesSrcSw
        def dstSwProps = switchHelper.getCachedSwProps(switchPair.dst.dpId)
        def amountOfserver42RulesDstSw = dstSwProps.server42FlowRtt ? 1 : 0
        def amountOfFlowRulesDstSw = 3 + amountOfserver42RulesDstSw
        if (dstSwProps.server42FlowRtt && flow.destination.vlanId) {
            amountOfFlowRulesDstSw += 1
        }
        assert createdCookiesDstSw.size() == amountOfFlowRulesDstSw

        when: "Update flow: enable protected path(allocateProtectedPath=true)"
        flowHelperV2.updateFlow(flow.flowId, flow.tap { it.allocateProtectedPath = true })

        then: "Protected path is enabled"
        def flowPathInfo = northbound.getFlowPath(flow.flowId)
        flowPathInfo.protectedPath
        def currentPath = pathHelper.convert(flowPathInfo)
        def currentProtectedPath = pathHelper.convert(flowPathInfo.protectedPath)
        currentPath != currentProtectedPath

        and: "Rules for main and protected paths are created"
        Wrappers.wait(WAIT_OFFSET) {
            flowHelper.verifyRulesOnProtectedFlow(flow.flowId)
            def cookiesAfterEnablingProtectedPath = northbound.getSwitchRules(switchPair.src.dpId).flowEntries.findAll {
                !new Cookie(it.cookie).serviceFlag
            }*.cookie
            // amountOfFlowRules for main path + one for protected path
            assert cookiesAfterEnablingProtectedPath.size() == amountOfFlowRulesSrcSw + 1
        }

        and: "No rule discrepancies on every switch of the flow on the main path"
        def mainSwitches = pathHelper.getInvolvedSwitches(currentPath)
        switchHelper.synchronizeAndCollectFixedDiscrepancies(mainSwitches*.getDpId()).isEmpty()

        and: "No rule discrepancies on every switch of the flow on the protected path)"
        def protectedSwitches = pathHelper.getInvolvedSwitches(currentProtectedPath)
        switchHelper.synchronizeAndCollectFixedDiscrepancies(protectedSwitches*.getDpId()).isEmpty()

        and: "The flow allows traffic(on the main path)"
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam).buildBidirectionalExam(flowHelperV2.toV1(flow), 1000, 5)
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        when: "Swap flow paths"
        def srcSwitchCreatedMeterIds = getCreatedMeterIds(switchPair.src.dpId)
        def dstSwitchCreatedMeterIds = getCreatedMeterIds(switchPair.dst.dpId)
        def currentLastUpdate = northboundV2.getFlow(flow.flowId).lastUpdated
        northbound.swapFlowPath(flow.flowId)

        then: "Flow paths are swapped"
        Wrappers.wait(WAIT_OFFSET) { assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP }
        def flowPathInfoAfterSwapping = northbound.getFlowPath(flow.flowId)
        def newCurrentPath = pathHelper.convert(flowPathInfoAfterSwapping)
        def newCurrentProtectedPath = pathHelper.convert(flowPathInfoAfterSwapping.protectedPath)
        newCurrentPath != currentPath
        newCurrentPath == currentProtectedPath
        newCurrentProtectedPath != currentProtectedPath
        newCurrentProtectedPath == currentPath

        currentLastUpdate < northboundV2.getFlow(flow.flowId).lastUpdated

        and: "New meter is created on the src and dst switches"
        def newSrcSwitchCreatedMeterIds = getCreatedMeterIds(switchPair.src.dpId)
        def newDstSwitchCreatedMeterIds = getCreatedMeterIds(switchPair.dst.dpId)
        //added || x.empty to allow situation when meters are not available on src or dst
        newSrcSwitchCreatedMeterIds.sort() != srcSwitchCreatedMeterIds.sort() || srcSwitchCreatedMeterIds.empty
        newDstSwitchCreatedMeterIds.sort() != dstSwitchCreatedMeterIds.sort() || dstSwitchCreatedMeterIds.empty

        and: "Rules are updated"
        Wrappers.wait(WAIT_OFFSET) { flowHelper.verifyRulesOnProtectedFlow(flow.flowId) }

        and: "Old meter is deleted on the src and dst switches"
        Wrappers.wait(WAIT_OFFSET) {
            [switchPair.src.dpId, switchPair.dst.dpId].each { switchId ->
                def switchValidateInfo = switchHelper.validate(switchId)
                if(switchValidateInfo.meters) {
                    assert switchValidateInfo.meters.proper.findAll({dto -> !isDefaultMeter(dto)}).size() == 1
                }
                assert switchValidateInfo.rules.proper.findAll { def cookie = new Cookie(it.getCookie())
                    !cookie.serviceFlag && cookie.type == SERVICE_OR_FLOW_SEGMENT }.size() ==
                        (switchId == switchPair.src.dpId) ? amountOfFlowRulesSrcSw + 1 : amountOfFlowRulesDstSw + 1
                switchValidateInfo.isAsExpected()
            }
        }

        and: "Transit switches store the correct info about rules and meters"
        def involvedTransitSwitches = (currentPath[1..-2].switchId + currentProtectedPath[1..-2].switchId).unique()
        Wrappers.wait(WAIT_OFFSET) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(involvedTransitSwitches).isEmpty()
        }

        and: "No rule discrepancies when doing flow validation"
        northbound.validateFlow(flow.flowId).each { assert it.discrepancies.empty }

        and: "All rules for main and protected paths are updated"
        Wrappers.wait(WAIT_OFFSET) { flowHelper.verifyRulesOnProtectedFlow(flow.flowId) }

        and: "No rule discrepancies on every switch of the flow on the main path"
        def newMainSwitches = pathHelper.getInvolvedSwitches(newCurrentPath)
        switchHelper.synchronizeAndCollectFixedDiscrepancies(newMainSwitches*.getDpId()).isEmpty()

        and: "No rule discrepancies on every switch of the flow on the protected path)"
        def newProtectedSwitches = pathHelper.getInvolvedSwitches(newCurrentProtectedPath)
        switchHelper.synchronizeAndCollectFixedDiscrepancies(newProtectedSwitches*.getDpId()).isEmpty()

        and: "The flow allows traffic(on the protected path)"
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "System is able to switch #flowDescription flows to protected paths"() {
        given: "Two active not neighboring switches with three diverse paths at least"
        def initialIsls = northbound.getAllLinks()
        def switchPair = switchPairs.all().nonNeighbouring().withAtLeastNNonOverlappingPaths(3).random()
        def uniquePathCount = switchPair.paths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size()

        when: "Create 5 flows with protected paths"
        List<FlowRequestV2> flows = []
        5.times {
            flows << flowHelperV2.randomFlow(switchPair, false, flows).tap {
                maximumBandwidth = bandwidth
                ignoreBandwidth = bandwidth == 0
                allocateProtectedPath = true
            }
        }
        flows.each { flowHelperV2.addFlow(it) }

        then: "Flows are created with protected path"
        def flowPathsInfo = flows.collect { northbound.getFlowPath(it.flowId) }
        flowPathsInfo.each { assert it.protectedPath }

        and: "Current paths are not equal to protected paths"
        def currentPath = pathHelper.convert(flowPathsInfo[0])
        def currentProtectedPath = pathHelper.convert(flowPathsInfo[0].protectedPath)
        currentPath != currentProtectedPath
        //check that all other flows use the same paths, so above verification applies to all of them
        flowPathsInfo.each { flowPathInfo ->
            assert pathHelper.convert(flowPathInfo) == currentPath
            assert pathHelper.convert(flowPathInfo.protectedPath) == currentProtectedPath
        }

        and: "Bandwidth is reserved for protected paths on involved ISLs"
        def protectedIsls = pathHelper.getInvolvedIsls(currentPath)
        def protectedIslsInfo = protectedIsls.collect { islUtils.getIslInfo(it).get() }
        initialIsls.each { initialIsl ->
            protectedIslsInfo.each { currentIsl ->
                if (initialIsl.id == currentIsl.id) {
                    assert initialIsl.availableBandwidth - currentIsl.availableBandwidth == flows.sum { it.maximumBandwidth }
                }
            }
        }

        when: "Break ISL on the main path (bring port down) to init auto swap"
        def islToBreak = pathHelper.getInvolvedIsls(currentPath)[0]
        islHelper.breakIsl(islToBreak)

        then: "Flows are switched to protected paths"
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            flows.each { flow ->
                assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP
                def flowPathInfoAfterRerouting = northbound.getFlowPath(flow.flowId)

                assert pathHelper.convert(flowPathInfoAfterRerouting) == currentProtectedPath
                if (4 <= uniquePathCount) {
                    assert pathHelper.convert(flowPathInfoAfterRerouting.protectedPath) != currentPath
                    assert pathHelper.convert(flowPathInfoAfterRerouting.protectedPath) != currentProtectedPath
                }
            }
        }

        when: "Restore port status"
        islHelper.restoreIsl(islToBreak)

        then: "Path of the flow is not changed"
        flows.each { assert pathHelper.convert(northbound.getFlowPath(it.flowId)) == currentProtectedPath }

        cleanup: "Revert system to original state"
        islHelper.restoreIsl(islToBreak)
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
        database.resetCosts(topology.isls)

        where:
        flowDescription | bandwidth
        "a metered"     | 1000
        "an unmetered"  | 0
    }

    @Tags([ISL_RECOVER_ON_FAIL, ISL_PROPS_DB_RESET])
    def "Flow swaps to protected path when main path gets broken, becomes DEGRADED if protected path is unable to reroute(no bw)"() {
        given: "Two switches with 2 diverse paths at least"
        //def switchPair = switchPairs.all().withAtLeastNNonOverlappingPaths(2).random()
        //https://github.com/telstra/open-kilda/issues/5608
        def switchesWhere5608IsReproducible = topology.activeSwitches.findAll {it.dpId.toString().endsWith("08")
        ||it.dpId.toString().endsWith("09")}
        def switchPair = switchPairs.all()
                .excludeSwitches(switchesWhere5608IsReproducible)
                .withAtLeastNNonOverlappingPaths(2).random()

        when: "Create flow with protected path"
        def flow = flowHelperV2.randomFlow(switchPair).tap { allocateProtectedPath = true }
        flowHelperV2.addFlow(flow)
        def path = northbound.getFlowPath(flow.flowId)

        and: "Other paths have not enough bandwidth to host the flow in case of reroute"
        def otherIsls = switchPair.paths.findAll { it != pathHelper.convert(path.protectedPath) &&
                it != pathHelper.convert(path) }.collectMany { pathHelper.getInvolvedIsls(it) }
                .unique { a, b -> a == b || a == b.reversed ? 0 : 1 }
        otherIsls.collectMany{[it, it.reversed]}.each {
            database.updateIslMaxBandwidth(it, flow.maximumBandwidth - 1)
            database.updateIslAvailableBandwidth(it, flow.maximumBandwidth - 1)
        }

        and: "Main flow path breaks"
        def mainIsl = pathHelper.getInvolvedIsls(path).first()
        islHelper.breakIsl(mainIsl)

        then: "Main path swaps to protected, flow becomes degraded, main path UP, protected DOWN"
        Wrappers.wait(WAIT_OFFSET) {
            def newPath = northbound.getFlowPath(flow.flowId)
            assert pathHelper.convert(newPath) == pathHelper.convert(path.protectedPath)
            verifyAll(northbound.getFlow(flow.flowId)) {
                status == FlowState.DEGRADED.toString()
                flowStatusDetails.mainFlowPathStatus == "Up"
                flowStatusDetails.protectedFlowPathStatus == "Down"
                statusInfo =~ ~/Not enough bandwidth or no path found. Switch \
(${switchPair.getSrc().getDpId()}|${switchPair.getDst().getDpId()}) doesn't have links with enough bandwidth, \
Failed to find path with requested bandwidth=$flow.maximumBandwidth/
            }
        }

        when: "ISL gets back up"
        islHelper.restoreIsl(mainIsl)

        then: "Main path remains the same, flow becomes UP, main path UP, protected UP"
        Wrappers.wait(WAIT_OFFSET) {
            def newPath = northbound.getFlowPath(flow.flowId)
            assert pathHelper.convert(newPath) == pathHelper.convert(path.protectedPath)
            verifyAll(northbound.getFlow(flow.flowId)) {
                status == FlowState.UP.toString()
                flowStatusDetails.mainFlowPathStatus == "Up"
                flowStatusDetails.protectedFlowPathStatus == "Up"
            }
        }

        cleanup:
        islHelper.restoreIsl(mainIsl)
        otherIsls && otherIsls.collectMany{[it, it.reversed]}.each { database.resetIslBandwidth(it) }
        database.resetCosts(topology.isls)
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Flow swaps to protected path when main path gets broken, becomes DEGRADED if protected path is unable to reroute(no path)"() {
        given: "Two switches with 2 diverse paths at least"
        //def switchPair = switchPairs.all().withAtLeastNNonOverlappingPaths(2).random()
        //https://github.com/telstra/open-kilda/issues/5608
        def switchesWhere5608IsReproducible = topology.activeSwitches.findAll {it.dpId.toString().endsWith("08")
                ||it.dpId.toString().endsWith("09")}
        def switchPair = switchPairs.all()
                .excludeSwitches(switchesWhere5608IsReproducible)
                .withAtLeastNNonOverlappingPaths(2).random()


        when: "Create flow with protected path"
        def flow = flowHelperV2.randomFlow(switchPair).tap { allocateProtectedPath = true }
        flowHelperV2.addFlow(flow)
        def path = northbound.getFlowPath(flow.flowId)

        and: "Other paths are not available (ISLs are down)"
        def originalMainPath = pathHelper.convert(path)
        def originalProtectedPath = pathHelper.convert(path.protectedPath)
        def usedIsls = pathHelper.getInvolvedIsls(originalMainPath) + pathHelper.getInvolvedIsls(originalProtectedPath)
        def otherIsls = switchPair.paths.findAll { it != originalMainPath &&
                it != originalProtectedPath }.collectMany { pathHelper.getInvolvedIsls(it) }
                .findAll {!usedIsls.contains(it) }
                .unique { a, b -> a == b || a == b.reversed ? 0 : 1 }
        islHelper.breakIsls(otherIsls)

        and: "Main flow path breaks"
        def mainIsl = pathHelper.getInvolvedIsls(path).first()
        islHelper.breakIsl(mainIsl)

        then: "Main path swaps to protected, flow becomes degraded, main path UP, protected DOWN"
        Wrappers.wait(WAIT_OFFSET) {
            def newPath = northbound.getFlowPath(flow.flowId)
            assert pathHelper.convert(newPath) == originalProtectedPath
            verifyAll(northbound.getFlow(flow.flowId)) {
                status == FlowState.DEGRADED.toString()
                flowStatusDetails.mainFlowPathStatus == "Up"
                flowStatusDetails.protectedFlowPathStatus == "Down"
                statusInfo == StatusInfo.OVERLAPPING_PROTECTED_PATH
            }
        }

        when: "ISL on broken path gets back up"
        islHelper.restoreIsl(mainIsl)

        then: "Main path remains the same (no swap), flow becomes UP, main path remains UP, protected path becomes UP"
        Wrappers.wait(WAIT_OFFSET) {
            def newPath = northbound.getFlowPath(flow.flowId)
            assert pathHelper.convert(newPath) == originalProtectedPath
            assert pathHelper.convert(newPath.protectedPath) == originalMainPath
            verifyAll(northbound.getFlow(flow.flowId)) {
                status == FlowState.UP.toString()
                flowStatusDetails.mainFlowPathStatus == "Up"
                flowStatusDetails.protectedFlowPathStatus == "Up"
            }
        }

        cleanup:
        islHelper.restoreIsls(otherIsls + mainIsl)
        database.resetCosts(topology.isls)
    }

    def "System reroutes #flowDescription flow to more preferable path and ignores protected path when reroute\
 is intentional"() {
        // 'and ignores protected path' means that the main path won't changed to protected
        given: "Two active neighboring switches with four diverse paths at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(4).random()

        and: "A flow with protected path"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.maximumBandwidth = bandwidth
        flow.ignoreBandwidth = bandwidth == 0
        flow.allocateProtectedPath = true
        flowHelperV2.addFlow(flow)

        def flowPathInfo = northbound.getFlowPath(flow.flowId)
        assert flowPathInfo.protectedPath

        def currentPath = pathHelper.convert(flowPathInfo)
        def currentProtectedPath = pathHelper.convert(flowPathInfo.protectedPath)
        assert currentPath != currentProtectedPath

        when: "Make the current and protected path less preferable than alternatives"
        def alternativePaths = switchPair.paths.findAll { it != currentPath && it != currentProtectedPath }
        alternativePaths.each { pathHelper.makePathMorePreferable(it, currentPath) }
        alternativePaths.each { pathHelper.makePathMorePreferable(it, currentProtectedPath) }

        and: "Init intentional reroute"
        def rerouteResponse = northboundV2.rerouteFlow(flow.flowId)

        then: "Flow is rerouted"
        rerouteResponse.rerouted
        Wrappers.wait(WAIT_OFFSET) {
            northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP
        }

        and: "Path is not changed to protected path"
        def flowPathInfoAfterRerouting = northbound.getFlowPath(flow.flowId)
        def newCurrentPath = pathHelper.convert(flowPathInfoAfterRerouting)
        newCurrentPath != currentPath
        newCurrentPath != currentProtectedPath
        //protected path is rerouted too, because more preferable path is exist
        def newCurrentProtectedPath = pathHelper.convert(flowPathInfoAfterRerouting.protectedPath)
        newCurrentProtectedPath != currentPath
        newCurrentProtectedPath != currentProtectedPath
        Wrappers.wait(WAIT_OFFSET) { assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP }

        cleanup: "Revert system to original state"
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))

        where:
        flowDescription | bandwidth
        "a metered"     | 1000
        "an unmetered"  | 0
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "System is able to switch #flowDescription flow to protected path and ignores more preferable path when reroute\
 is automatical"() {
        given: "Two active not neighboring switches with three diverse paths at least"
        def switchPair = switchPairs.all().nonNeighbouring().withAtLeastNNonOverlappingPaths(3).random()

        def uniquePathCount = switchPair.paths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size()

        and: "A flow with protected path"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.maximumBandwidth = bandwidth
        flow.ignoreBandwidth = bandwidth == 0
        flow.allocateProtectedPath = true
        flowHelperV2.addFlow(flow)

        def flowPathInfo = northbound.getFlowPath(flow.flowId)
        assert flowPathInfo.protectedPath

        def currentPath = pathHelper.convert(flowPathInfo)
        def currentProtectedPath = pathHelper.convert(flowPathInfo.protectedPath)
        assert currentPath != currentProtectedPath

        when: "Make the current and protected path less preferable than alternatives"
        def alternativePaths = switchPair.paths.findAll { it != currentPath && it != currentProtectedPath }
        alternativePaths.each { pathHelper.makePathMorePreferable(it, currentPath) }
        alternativePaths.each { pathHelper.makePathMorePreferable(it, currentProtectedPath) }

        and: "Break ISL on the main path (bring port down) to init auto swap"
        def islToBreak = pathHelper.getInvolvedIsls(currentPath)[0]
        islHelper.breakIsl(islToBreak)

        then: "Flow is switched to protected path"
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            def newPathInfo = northbound.getFlowPath(flow.flowId)
            def newCurrentPath = pathHelper.convert(newPathInfo)
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP
            assert newCurrentPath != currentPath

            def newCurrentProtectedPath = pathHelper.convert(newPathInfo.protectedPath)
            assert newCurrentPath == currentProtectedPath
            if (4 <= uniquePathCount) {
                assert newCurrentProtectedPath != currentPath
                assert newCurrentProtectedPath != currentProtectedPath
            }
        }

        when: "Restore port status"
        islHelper.restoreIsl(islToBreak)

        then: "Path of the flow is not changed"
        pathHelper.convert(northbound.getFlowPath(flow.flowId)) == currentProtectedPath

        cleanup: "Revert system to original state"
        islHelper.restoreIsl(islToBreak)
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
        database.resetCosts(topology.isls)

        where:
        flowDescription | bandwidth
        "a metered"     | 1000
        "an unmetered"  | 0
    }

    def "A flow with protected path does not get rerouted if already on the best path"() {
        given: "Two active neighboring switches and a flow"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = true
        flowHelperV2.addFlow(flow)

        def flowPathInfo = northbound.getFlowPath(flow.flowId)
        def currentPath = pathHelper.convert(flowPathInfo)
        def currentProtectedPath = pathHelper.convert(flowPathInfo.protectedPath)
        assert flowPathInfo.protectedPath

        when: "Init intentional reroute"
        def rerouteResponse = northboundV2.rerouteFlow(flow.flowId)

        then: "Flow is not rerouted"
        !rerouteResponse.rerouted

        def newFlowPathInfo = northbound.getFlowPath(flow.flowId)
        pathHelper.convert(newFlowPathInfo) == currentPath
        pathHelper.convert(newFlowPathInfo.protectedPath) == currentProtectedPath
    }

    @Tags([LOW_PRIORITY, ISL_PROPS_DB_RESET])
    def "Able to update a flow to enable protected path when there is not enough bandwidth"() {
        given: "Two active neighboring switches"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        and: "Update all ISLs which can be used by protected path"
        def bandwidth = 100
        isls[1..-1].each { database.updateIslAvailableBandwidth(it, 90) }

        when: "Create flow without protected path"
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = bandwidth
        flow.allocateProtectedPath = false
        flowHelperV2.addFlow(flow)

        then: "Flow is created without protected path"
        !northbound.getFlowPath(flow.flowId).protectedPath

        when: "Update flow: enable protected path"
        northboundV2.updateFlow(flow.flowId, flow.tap { it.allocateProtectedPath = true })

        then: "Flow state is changed to DEGRADED"
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.flowId).status == FlowState.DEGRADED }
        verifyAll(northbound.getFlow(flow.flowId).flowStatusDetails) {
            mainFlowPathStatus == "Up"
            protectedFlowPathStatus == "Down"
        }

        cleanup: "Delete the flow and restore available bandwidth"
        isls.each { database.resetIslBandwidth(it) }
    }

    @Tags(ISL_PROPS_DB_RESET)
    def "Able to create a flow with protected path when there is not enough bandwidth and ignoreBandwidth=true"() {
        given: "Two active neighboring switches"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        and: "Update all ISLs which can be used by protected path"
        def bandwidth = 100
        isls[1..-1].each { database.updateIslAvailableBandwidth(it, 90) }

        when: "Create flow with protected path"
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = bandwidth
        flow.allocateProtectedPath = true
        flow.ignoreBandwidth = true
        flowHelperV2.addFlow(flow)

        then: "Flow is created with protected path"
        northbound.getFlowPath(flow.flowId).protectedPath

        and: "One transit vlan is created for main and protected paths"
        def flowInfo = database.getFlow(flow.flowId)
        database.getTransitVlans(flowInfo.forwardPathId, flowInfo.reversePathId).size() == 1
        database.getTransitVlans(flowInfo.protectedForwardPathId, flowInfo.protectedReversePathId).size() == 1

        cleanup: "Delete the flow and restore available bandwidth"
        isls.each { database.resetIslBandwidth(it) }
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "System is able to recalculate protected path when protected path is broken"() {
        given: "Two active not neighboring switches with three diverse paths at least"
        def allIsls = northbound.getAllLinks()
        def switchPair = switchPairs.all().nonNeighbouring().withAtLeastNNonOverlappingPaths(3).random()

        when: "Create a flow with protected path"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.allocateProtectedPath = true
        flowHelperV2.addFlow(flow)

        then: "Flow is created with protected path"
        def flowPathInfo = northbound.getFlowPath(flow.flowId)
        flowPathInfo.protectedPath

        def currentPath = pathHelper.convert(flowPathInfo)
        def currentProtectedPath = pathHelper.convert(flowPathInfo.protectedPath)
        currentPath != currentProtectedPath

        def protectedIsls = pathHelper.getInvolvedIsls(currentProtectedPath)
        def protectedIslsInfo = protectedIsls.collect { islUtils.getIslInfo(it).get() }

        allIsls.each { isl ->
            protectedIslsInfo.each { protectedIsl ->
                if (isl.id == protectedIsl.id) {
                    assert isl.availableBandwidth - protectedIsl.availableBandwidth == flow.maximumBandwidth
                }
            }
        }

        when: "Break ISL on the protected path (bring port down) to init the recalculate procedure"
        def islToBreakProtectedPath = protectedIsls[0]
        islHelper.breakIsl(islToBreakProtectedPath)

        then: "Protected path is recalculated"
        def newProtectedPath
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            newProtectedPath = pathHelper.convert(northbound.getFlowPath(flow.flowId).protectedPath)
            assert newProtectedPath != currentProtectedPath
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP
        }

        and: "Current path is not changed"
        currentPath == pathHelper.convert(northbound.getFlowPath(flow.flowId))

        and: "Bandwidth is reserved for new protected path on involved ISLs"
        def allLinks
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            def newProtectedIsls = pathHelper.getInvolvedIsls(newProtectedPath)
            allLinks = northbound.getAllLinks()
            def newProtectedIslsInfo = newProtectedIsls.collect { islUtils.getIslInfo(allLinks, it).get() }

            allIsls.each { isl ->
                newProtectedIslsInfo.each { protectedIsl ->
                    if (isl.id == protectedIsl.id) {
                        assert isl.availableBandwidth - protectedIsl.availableBandwidth == flow.maximumBandwidth
                    }
                }
            }
        }

        and: "Reservation is deleted on the broken ISL"
        def originInfoBrokenIsl = allIsls.find {
            it.destination.switchId == islToBreakProtectedPath.dstSwitch.dpId &&
                    it.destination.portNo == islToBreakProtectedPath.dstPort
        }
        def currentInfoBrokenIsl = islUtils.getIslInfo(allLinks, islToBreakProtectedPath).get()

        originInfoBrokenIsl.availableBandwidth == currentInfoBrokenIsl.availableBandwidth

        when: "Restore port status"
        islHelper.restoreIsl(islToBreakProtectedPath)

        then: "Path is not recalculated again"
        pathHelper.convert(northbound.getFlowPath(flow.flowId).protectedPath) == newProtectedPath

        cleanup: "Revert system to original state"
        islHelper.restoreIsl(islToBreakProtectedPath)
        database.resetCosts(topology.isls)
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    @IterationTag(tags = [LOW_PRIORITY], iterationNameRegex = /unmetered/)
    def "Able to update #flowDescription flow to enable protected path if all alternative paths are unavailable"() {
        given: "Two active neighboring switches with two not overlapping paths at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(2).random()


        and: "A flow without protected path"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.allocateProtectedPath = false
        flow.maximumBandwidth = bandwidth
        flow.ignoreBandwidth = bandwidth == 0
        flowHelperV2.addFlow(flow)

        and: "All alternative paths are unavailable (bring ports down on the source switch)"
        def flowPathPortOnSourceSwitch = pathHelper.convert(northbound.getFlowPath(flow.flowId)).first().portNo
        def broughtDownIsls = topology.getRelatedIsls(switchPair.getSrc())
                .findAll{it.srcSwitch == switchPair.getSrc() && it.srcPort != flowPathPortOnSourceSwitch }
        islHelper.breakIsls(broughtDownIsls)

        when: "Update flow: enable protected path(allocateProtectedPath=true)"
        northboundV2.updateFlow(flow.flowId, flow.tap { it.allocateProtectedPath = true })

        then: "Flow state is changed to DEGRADED"
        Wrappers.wait(WAIT_OFFSET) { assert northbound.getFlowStatus(flow.flowId).status == FlowState.DEGRADED }
        verifyAll(northbound.getFlow(flow.flowId)) {
            flowStatusDetails.mainFlowPathStatus == "Up"
            flowStatusDetails.protectedFlowPathStatus == "Down"
        }

        cleanup: "Restore topology, delete flows and reset costs"
        islHelper.restoreIsls(broughtDownIsls)
        database.resetCosts(topology.isls)

        where:
        flowDescription | bandwidth
        "a metered"     | 1000
        "an unmetered"  | 0
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    @IterationTag(tags = [LOW_PRIORITY], iterationNameRegex = /unmetered/)
    def "#flowDescription flow is DEGRADED when protected and alternative paths are not available"() {
        given: "Two active neighboring switches with two not overlapping paths at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(2).random()


        and: "A flow with protected path"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.allocateProtectedPath = true
        flow.maximumBandwidth = bandwidth
        flow.ignoreBandwidth = bandwidth == 0
        flowHelperV2.addFlow(flow)
        def flowInfoPath = northbound.getFlowPath(flow.flowId)
        assert flowInfoPath.protectedPath

        when: "All alternative paths are unavailable"
        def mainPath = pathHelper.convert(flowInfoPath)
        def untouchableIsls = pathHelper.getInvolvedIsls(mainPath).collectMany { [it, it.reversed] }
        def altPaths = switchPair.paths.findAll { [it, it.reverse()].every { it != mainPath }}
        def islsToBreak = altPaths.collectMany { pathHelper.getInvolvedIsls(it) }
                                                 .collectMany { [it, it.reversed] }.unique()
                                                 .findAll { !untouchableIsls.contains(it) }.unique { [it, it.reversed].sort() }
        islHelper.breakIsls(islsToBreak)

        then: "Flow status is DEGRADED"
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(northbound.getFlow(flow.flowId)) {
                status == FlowState.DEGRADED.toString()
                statusInfo == StatusInfo.OVERLAPPING_PROTECTED_PATH
            }
            assert flowHelper.getLatestHistoryEntry(flow.flowId).payload.find { it.action == REROUTE_FAIL }
            assert northboundV2.getFlowHistoryStatuses(flow.flowId, 1).historyStatuses*.statusBecome == ["DEGRADED"]
        }

        when: "Update flow: disable protected path(allocateProtectedPath=false)"
        northboundV2.updateFlow(flow.flowId, flow.tap { it.allocateProtectedPath = false })

        then: "Flow status is UP"
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(northbound.getFlow(flow.flowId)) {
                status == FlowState.UP.toString()
                !statusInfo //statusInfo is cleared after changing flowStatus to UP
            }
        }

        cleanup: "Restore topology, delete flow and reset costs"
        islHelper.restoreIsls(islsToBreak)
        database.resetCosts(topology.isls)

        where:
        flowDescription | bandwidth
        "A metered"     | 1000
        "An unmetered"  | 0
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "System properly reroutes both paths if protected path breaks during auto-swap"() {
        given: "Switch pair with at least 4 diverse paths"
        def switchPair = switchPairs.all(false).withAtLeastNNonOverlappingPaths(4).random()

        and: "A protected flow"
        def flow = flowHelperV2.randomFlow(switchPair).tap { it.allocateProtectedPath = true }
        flowHelperV2.addFlow(flow)

        when: "Main paths breaks"
        def paths = northbound.getFlowPath(flow.flowId)
        def mainPath = pathHelper.convert(paths)
        def mainPathIsl = pathHelper.getInvolvedIsls(mainPath).first()
        def protectedPath = pathHelper.convert(paths.protectedPath)
        def protectedPathIsl = pathHelper.getInvolvedIsls(protectedPath).first()
        islHelper.breakIsl(mainPathIsl)

        and: "Protected path breaks when swap is in progress"
        //we want to break the second ISL right when the protected path reroute starts. race here
        //+750ms correction was found experimentally. helps to hit the race condition more often (local env)
        sleep(Math.max(0, (rerouteDelay - antiflapMin) * 1000 + 750))
        islHelper.breakIsl(protectedPathIsl)

        then: "Both paths are successfully evacuated from broken isls and the flow is UP"
        log.debug("original main: $mainPath\n original protected: $protectedPath")
        Wrappers.wait(rerouteDelay + PATH_INSTALLATION_TIME) {
            Wrappers.timedLoop(3) { //this should be a stable result, all reroutes must finish
                assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP
                def currentPath = northbound.getFlowPath(flow.flowId)
                [currentPath, currentPath.protectedPath].each {
                   assert pathHelper.getInvolvedIsls(pathHelper.convert(it)).findAll {
                       it in [mainPathIsl, protectedPathIsl]
                   }.empty, "Found broken ISL being used in path: $it"
                }
            }
        }

        cleanup:
        islHelper.restoreIsls([mainPathIsl, protectedPathIsl])
        database.resetCosts(topology.isls)

    }

    def "System reuses current protected path when can't find new non overlapping protected path while intentional\
 rerouting"() {
        given: "Two active neighboring switches with three diverse paths"
        def switchPair = switchPairs.all().neighbouring().withExactlyNNonOverlappingPaths(3).random()

        and: "A flow with protected path"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.allocateProtectedPath = true
        flowHelperV2.addFlow(flow)

        def flowPathInfo = northbound.getFlowPath(flow.flowId)
        assert flowPathInfo.protectedPath

        def currentPath = pathHelper.convert(flowPathInfo)
        def currentProtectedPath = pathHelper.convert(flowPathInfo.protectedPath)
        assert currentPath != currentProtectedPath

        when: "Make the current and protected path less preferable than alternatives"
        def alternativePaths = switchPair.paths.findAll { it != currentPath && it != currentProtectedPath }
        withPool {
            alternativePaths.eachParallel { pathHelper.makePathMorePreferable(it, currentPath) }
            alternativePaths.eachParallel { pathHelper.makePathMorePreferable(it, currentProtectedPath) }
        }

        and: "Init intentional reroute"
        def rerouteResponse = northboundV2.rerouteFlow(flow.flowId)

        then: "Flow should be rerouted"
        rerouteResponse.rerouted

        and: "Flow main path should be rerouted to a new path and ignore protected path"
        def flowPathInfoAfterRerouting
        def newCurrentPath
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            flowPathInfoAfterRerouting = northbound.getFlowPath(flow.flowId)
            newCurrentPath = pathHelper.convert(flowPathInfoAfterRerouting)
            newCurrentPath != currentPath
            newCurrentPath != currentProtectedPath
        }

        and: "Flow protected path shouldn't be rerouted due to lack of non overlapping path"
        pathHelper.convert(flowPathInfoAfterRerouting.protectedPath) == currentProtectedPath

        and: "Flow and both its paths are UP"
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(northboundV2.getFlow(flow.flowId)) {
                status == "Up"
                statusDetails.mainPath == "Up"
                statusDetails.protectedPath == "Up"
            }
        }

        cleanup: "Revert system to original state"
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Protected path is created in different POP even if this path is not preferable"(){
        given: "Not neighboring switch pair with three diverse paths at least"
        def allPaths // all possible paths
        def allPathsWithThreeSwitches //all possible paths with 3 involved switches
        def allPathCandidates // 3 diverse paths at least
        def swPair = switchPairs.all().nonNeighbouring().getSwitchPairs().find { swP ->
            allPaths = swP.paths
            allPathsWithThreeSwitches = allPaths.findAll { pathHelper.getInvolvedSwitches(it).size() == 3 }
            allPathCandidates = allPathsWithThreeSwitches.unique(false) { a, b ->
                a.intersect(b) == [] ? 1 : 0
            } // just to avoid parallel links
            allPathsWithThreeSwitches.unique(false) { a, b ->
                def p1 = pathHelper.getInvolvedSwitches(a)[1..-2]*.dpId
                def p2 = pathHelper.getInvolvedSwitches(b)[1..-2]*.dpId
                p1.intersect(p2) == [] ? 1 : 0
            }.size() >= 3
        } ?: assumeTrue(false, "No suiting switches found")

        //select paths for further manipulations
        def mainPath1 = allPathCandidates.first()
        def mainPath2 = allPathCandidates.find { it != mainPath1 }
        def protectedPath = allPathCandidates.find { it != mainPath1 && it != mainPath2}
        def involvedSwP1 = pathHelper.getInvolvedSwitches(mainPath1)*.dpId
        def involvedSwP2 = pathHelper.getInvolvedSwitches(mainPath2)*.dpId
        def involvedSwProtected = pathHelper.getInvolvedSwitches(protectedPath)*.dpId

        and: "Src, dst and transit switches belongs to different POPs(src:1, dst:4, tr1/tr2:2, tr3:3)"
        // tr1/tr2 for the main path and tr3 for the protected path
        northboundV2.partialSwitchUpdate(swPair.src.dpId, new SwitchPatchDto().tap { it.pop = "1" })
        [involvedSwP1[1], involvedSwP2[1]].each { swId ->
            northboundV2.partialSwitchUpdate(swId, new SwitchPatchDto().tap { it.pop = "2" })
        }
        northboundV2.partialSwitchUpdate(swPair.dst.dpId, new SwitchPatchDto().tap { it.pop = "4" })
        northboundV2.partialSwitchUpdate(involvedSwProtected[1], new SwitchPatchDto().tap { it.pop = "3" })

        and: "Path which contains tr3 is non preferable"
        /** There is not possibility to use the 'makePathNotPreferable' method,
         * because it sets too high value for protectedPath.
         *
         *  totalCostOFProtectedPath should be the following:
         *  totalCostOfMainPath + (amountOfInvolvedIsls * diversity.pop.isl.cost * diversityGroupPerPopUseCounter) - 1,
         *  where:
         *  diversity.pop.isl.cost = 1000
         *  diversityGroupPerPopUseCounter = amount of switches in the same POP
         *  (in this test there are two switches in the same POP) */
        List<Isl> islsToUpdate = []
        allPathsWithThreeSwitches.findAll { involvedSwProtected[1] in pathHelper.getInvolvedSwitches(it)*.dpId }.each {
            pathHelper.getInvolvedIsls(it).each {
                islsToUpdate << it
            }
        }
        def involvedIslsOfMainPath = pathHelper.getInvolvedIsls(mainPath1)
        def defaultIslCost = 700
        def totalCostOfMainPath = involvedIslsOfMainPath.sum { northbound.getLink(it).cost ?: defaultIslCost }
        def amountOfIlslsOnMainPath = involvedIslsOfMainPath.size()
        def diversityPopIslCost = 1000
        def diversityGroupPerPopUseCounter = 2
        Integer newIslCost = ((totalCostOfMainPath +
                (amountOfIlslsOnMainPath * diversityPopIslCost * diversityGroupPerPopUseCounter) - 1) /
                pathHelper.getInvolvedIsls(protectedPath).size()).toInteger()
        log.debug("newCost: $newIslCost")

        islsToUpdate.unique().each { isl ->
            northbound.updateLinkProps([islUtils.toLinkProps(isl, ["cost": newIslCost.toString()])])
        }

        and: "All alternative paths unavailable (bring ports down on the source switch)"
        List<Isl> broughtDownIsls = topology.getRelatedIsls(swPair.src) -
                [mainPath1, mainPath2, protectedPath].collect {pathHelper.getInvolvedIsls(it).first()}
        islHelper.breakIsls(broughtDownIsls)

        when: "Create a protected flow"
        /** At this point we have the following topology:
         *
         *             srcSwitch_POP_1
         *             /       |       \
         *            / 700    | 700    \ newIslCost
         *           /         |         \
         *   trSw1_POP_2   trSw2_POP_2   trSw3_POP_3
         *          \          |         /
         *           \ 700     | 700    / newIslCost
         *            \        |       /
         *             dstSwitch_POP_4
         *
         *  In the topology above the system is going to choose trSw1 or trSw2 for main path,
         *  because these path are more preferable that the path with trSw3.
         *
         *  System takes into account PoP and try not to place protected path into the same transit PoPs.
         *  So, the protected path will be built through the trSw3 because trSw1 and trSw2 are in the same PoP zone.
         * */
        def flow = flowHelperV2.randomFlow(swPair)
        flow.allocateProtectedPath = true
        flowHelperV2.addFlow(flow)

        then: "Main path is built through the preferable path(tr1 or tr2)"
        def flowPaths = northbound.getFlowPath(flow.flowId)
        def realFlowPathInvolvedSwitches = pathHelper.getInvolvedSwitches(pathHelper.convert(flowPaths))*.dpId
        realFlowPathInvolvedSwitches == involvedSwP1 || realFlowPathInvolvedSwitches == involvedSwP2

        and: "Protected path is built through the non preferable path(tr3)"
        pathHelper.getInvolvedSwitches(pathHelper.convert(flowPaths.protectedPath))*.dpId == involvedSwProtected

        cleanup:
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
        islHelper.restoreIsls(broughtDownIsls)
        withPool {
            (involvedSwP1 + involvedSwP2 + involvedSwProtected).unique().eachParallel { swId ->
                northboundV2.partialSwitchUpdate(swId, new SwitchPatchDto().tap { it.pop = "" })
            }
        }
        database.resetCosts(topology.isls)
    }

    @Tags(LOW_PRIORITY)
    def "Unable to perform the 'swap' request for a flow without protected path"() {
        given: "Two active neighboring switches"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        and: "A flow without protected path"
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = false
        flowHelperV2.addFlow(flow)
        !northbound.getFlowPath(flow.flowId).protectedPath

        when: "Try to swap paths for flow that doesn't have protected path"
        northbound.swapFlowPath(flow.flowId)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorDescription ==
                "Could not swap paths: Flow $flow.flowId doesn't have protected path"
    }

    @Tags(LOW_PRIORITY)
    def "Unable to swap paths for a non-existent flow"() {
        when: "Try to swap path on a non-existent flow"
        northbound.swapFlowPath(NON_EXISTENT_FLOW_ID)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorDescription ==
                "Could not swap paths: Flow $NON_EXISTENT_FLOW_ID not found"
    }

    @Tags(ISL_PROPS_DB_RESET)
    def "Unable to create a flow with protected path when there is not enough bandwidth"() {
        given: "Two active neighboring switches"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        and: "Update all ISLs which can be used by protected path"
        def bandwidth = 100
        isls[1..-1].each { database.updateIslAvailableBandwidth(it, 90) }

        when: "Create flow with protected path"
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = bandwidth
        flow.allocateProtectedPath = true
        flowHelperV2.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not create flow"
        errorDetails.errorDescription == "Not enough bandwidth or no path found. " +
                "Couldn't find non overlapping protected path"

        cleanup: "Restore available bandwidth"
        isls.each { database.resetIslBandwidth(it) }
    }

    @Tags([LOW_PRIORITY, ISL_RECOVER_ON_FAIL])
    def "Unable to swap paths for an inactive flow"() {
        given: "Two active neighboring switches with two not overlapping paths at least"
        def switchPair = switchPairs.all().neighbouring()
                //https://github.com/telstra/open-kilda/issues/5608
                .excludeSwitches(topology.activeSwitches.findAll {it.dpId.toString().endsWith("08")})
                .withAtLeastNNonOverlappingPaths(2).random()

        and: "A flow with protected path"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.allocateProtectedPath = true
        flowHelperV2.addFlow(flow)

        and: "All alternative paths are unavailable (bring ports down on the source switch)"
        def flowPathIsl = pathHelper.getInvolvedIsls(pathHelper.convert(northbound.getFlowPath(flow.flowId)))
        def broughtDownIsls = topology.getRelatedIsls(switchPair.src) - flowPathIsl
        islHelper.breakIsls(broughtDownIsls)

        when: "Break ISL on a protected path (bring port down) for changing the flow state to DEGRADED"
        def flowPathInfo = northbound.getFlowPath(flow.flowId)
        def currentPath = pathHelper.convert(flowPathInfo)
        def currentProtectedPath = pathHelper.convert(flowPathInfo.protectedPath)
        def protectedIsls = pathHelper.getInvolvedIsls(currentProtectedPath)
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        islHelper.breakIsl(protectedIsls[0])

        then: "Flow state is changed to DEGRADED"
        Wrappers.wait(WAIT_OFFSET) { assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.DEGRADED }
        verifyAll(northboundV2.getFlow(flow.flowId).statusDetails) {
            mainPath == "Up"
            protectedPath == "Down"
        }

        when: "Break ISL on the main path (bring port down) for changing the flow state to DOWN"
        islHelper.breakIsl(currentIsls[0])

        then: "Flow state is changed to DOWN"
        Wrappers.wait(WAIT_OFFSET) {
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.DOWN
            assert flowHelper.getHistoryEntriesByAction(flow.flowId, REROUTE_ACTION).find{
                it.taskId =~ (/.+ : retry #1 ignore_bw true/)
            }?.payload?.last()?.action == REROUTE_FAIL
        }
        verifyAll(northboundV2.getFlow(flow.flowId).statusDetails) {
            mainPath == "Down"
            protectedPath == "Down"
        }

        when: "Try to swap paths when main/protected paths are not available"
        northbound.swapFlowPath(flow.flowId)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorDescription ==
                "Could not swap paths: Protected flow path $flow.flowId is not in ACTIVE state"

        when: "Restore ISL for the main path only"
        islHelper.restoreIsl(currentIsls[0])

        then: "Flow state is still DEGRADED"
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.DEGRADED
            verifyAll(northboundV2.getFlow(flow.flowId)) {
                statusDetails.mainPath == "Up"
                statusDetails.protectedPath == "Down"
                statusInfo == StatusInfo.OVERLAPPING_PROTECTED_PATH
            }
        }

        when: "Try to swap paths when the main path is available and the protected path is not available"
        northbound.swapFlowPath(flow.flowId)

        then: "Human readable error is returned"
        def exc1 = thrown(HttpClientErrorException)
        exc1.rawStatusCode == 400
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not swap paths for flow"
        errorDetails.errorDescription ==
                "Could not swap paths: Protected flow path $flow.flowId is not in ACTIVE state"

        when: "Restore ISL for the protected path"
        islHelper.restoreIsl(protectedIsls[0])

        then: "Flow state is changed to UP"
        //it often fails in scope of the whole spec on the hardware env, that's why '* 1.5' is added
        Wrappers.wait(discoveryInterval * 1.5 + WAIT_OFFSET) {
            assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP
        }

        cleanup: "Restore topology, delete flows and reset costs"
        islHelper.restoreIsls(broughtDownIsls)
        database.resetCosts(topology.isls)
    }

    @Tags(LOW_PRIORITY)
    def "Unable to create a single switch flow with protected path"() {
        given: "A switch"
        def sw = topology.activeSwitches.first()

        when: "Create single switch flow"
        def flow = flowHelperV2.singleSwitchFlow(sw)
        flow.allocateProtectedPath = true
        flowHelperV2.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not create flow"
        errorDetails.errorDescription == "Couldn't setup protected path for one-switch flow"
    }

    @Tags(LOW_PRIORITY)
    def "Unable to update a single switch flow to enable protected path"() {
        given: "A switch"
        def sw = topology.activeSwitches.first()

        and: "A flow without protected path"
        def flow = flowHelperV2.singleSwitchFlow(sw)
        flowHelperV2.addFlow(flow)

        when: "Update flow: enable protected path"
        northboundV2.updateFlow(flow.flowId, flow.tap { it.allocateProtectedPath = true })

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not update flow"
        errorDetails.errorDescription == "Couldn't setup protected path for one-switch flow"
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    @IterationTag(tags = [LOW_PRIORITY], iterationNameRegex = /unmetered/)
    def "Unable to create #flowDescription flow with protected path if all alternative paths are unavailable"() {
        given: "Two active neighboring switches without alt paths"
        def switchPair = switchPairs.all().neighbouring().random()
        def broughtDownIsls = topology.getRelatedIsls(switchPair.src)[1..-1]
        islHelper.breakIsls(broughtDownIsls)

        when: "Try to create a new flow with protected path"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.allocateProtectedPath = true
        flow.maximumBandwidth = bandwidth
        flow.ignoreBandwidth = bandwidth == 0
        flowHelperV2.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not create flow"
        errorDetails.errorDescription == "Not enough bandwidth or no path found." +
                " Couldn't find non overlapping protected path"

        cleanup: "Restore topology, delete flows and reset costs"
        islHelper.restoreIsls(broughtDownIsls)
        database.resetCosts(topology.isls)

        where:
        flowDescription | bandwidth
        "a metered"     | 1000
        "an unmetered"  | 0
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "System doesn't reroute main flow path when protected path is broken and new alt path is available\
(altPath is more preferable than mainPath)"() {
        given: "Two active neighboring switches with three diverse paths at least"
        def switchPair = switchPairs.all().neighbouring().withExactlyNNonOverlappingPaths(3).random()


        and: "A flow with protected path"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.allocateProtectedPath = true
        flowHelperV2.addFlow(flow)

        and: "All alternative paths are unavailable (bring ports down on the source switch)"
        def flowPathInfo = northbound.getFlowPath(flow.flowId)
        def currentPath = pathHelper.convert(flowPathInfo)
        def currentPathIsls = pathHelper.getInvolvedIsls(currentPath)
        def currentProtectedPath = pathHelper.convert(flowPathInfo.protectedPath)
        def protectedIslToBreak = pathHelper.getInvolvedIsls(currentProtectedPath)[0]
        def broughtDownIsls = topology.getRelatedIsls(switchPair.src) - currentPathIsls.first() - protectedIslToBreak
        islHelper.breakIsls(broughtDownIsls)

        and: "ISL on a protected path is broken(bring port down) for changing the flow state to DEGRADED"
        islHelper.breakIsl(protectedIslToBreak)
        Wrappers.wait(WAIT_OFFSET) { assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.DEGRADED }

        when: "Make the current path less preferable than alternative path"
        def alternativePath = switchPair.paths.find { it != currentPath && it != currentProtectedPath }
        def currentIsl = pathHelper.getInvolvedIsls(currentPath)[0]
        def alternativeIsl = pathHelper.getInvolvedIsls(alternativePath)[0]

        switchPair.paths.findAll { it != alternativePath }.each {
            pathHelper.makePathMorePreferable(alternativePath, it)
        }
        assert northbound.getLink(currentIsl).cost > northbound.getLink(alternativeIsl).cost

        and: "Make alternative path available(bring port up on the source switch)"
        islHelper.restoreIsl(alternativeIsl)

        then: "Flow state is changed to UP"
        Wrappers.wait(WAIT_OFFSET) { assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP }

        and: "Protected path is recalculated only"
        def newFlowPathInfo = northbound.getFlowPath(flow.flowId)
        pathHelper.convert(newFlowPathInfo) == currentPath
        pathHelper.convert(newFlowPathInfo.protectedPath) == alternativePath

        cleanup: "Restore topology, delete flow and reset costs"
        islHelper.restoreIsls(broughtDownIsls + protectedIslToBreak)
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
        database.resetCosts(topology.isls)
    }

    @Tags(LOW_PRIORITY)
    def "System doesn't allow to enable the pinned flag on a protected flow"() {
        given: "A protected flow"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.allocateProtectedPath = true
        flowHelperV2.addFlow(flow)

        when: "Update flow: enable the pinned flag(pinned=true)"
        northboundV2.updateFlow(flow.flowId, flow.tap { it.pinned = true })

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        def errorDetails = exc.responseBodyAsString.to(MessageError)
        errorDetails.errorMessage == "Could not update flow"
        errorDetails.errorDescription == "Flow flags are not valid, unable to process pinned protected flow"
    }

    List<Integer> getCreatedMeterIds(SwitchId switchId) {
        return northbound.getAllMeters(switchId).meterEntries.findAll {
            it.meterId > MAX_SYSTEM_RULE_METER_ID
        }*.meterId
    }
}
