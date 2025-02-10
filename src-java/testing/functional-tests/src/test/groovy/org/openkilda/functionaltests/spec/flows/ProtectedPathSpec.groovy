package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_PROPS_DB_RESET
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.helpers.SwitchHelper.isDefaultMeter
import static org.openkilda.functionaltests.helpers.model.FlowActionType.REROUTE
import static org.openkilda.functionaltests.helpers.model.FlowActionType.REROUTE_FAILED
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.model.cookie.CookieBase.CookieType.SERVICE_OR_FLOW_SEGMENT
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.PROTECTED_PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowNotCreatedExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotCreatedWithMissingPathExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotUpdatedExpectedError
import org.openkilda.functionaltests.error.flow.FlowPathNotSwappedExpectedError
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowEntityPath
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.FlowStatusHistoryEvent
import org.openkilda.functionaltests.helpers.model.Path
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.functionaltests.helpers.model.SwitchRulesFactory
import org.openkilda.messaging.info.rule.FlowEntry
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.StatusInfo
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.northbound.dto.v2.switches.SwitchPatchDto
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.service.traffexam.TraffExamService

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared

import jakarta.inject.Provider

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

    @Autowired
    @Shared
    FlowFactory flowFactory

    @Autowired
    @Shared
    SwitchRulesFactory switchRulesFactory

    @Autowired @Shared
    Provider<TraffExamService> traffExamProvider

    public static final Closure REQUIRED_COOKIE = { Long cookie ->  !new Cookie(cookie).serviceFlag && new Cookie(cookie).type == SERVICE_OR_FLOW_SEGMENT }

    @Tags(LOW_PRIORITY)
    def "Able to create a flow with protected path when maximumBandwidth=#bandwidth, vlan=#vlanId"() {
        given: "Two active not neighboring switches with two diverse paths at least"
        def switchPair = switchPairs.all().nonNeighbouring().withAtLeastNNonOverlappingPaths(2).random()

        when: "Create flow with protected path"
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(true)
                .withBandwidth(bandwidth)
                .withIgnoreBandwidth(bandwidth == 0)
                .withSourceVlan(vlanId).build()
                .create()


        then: "Flow is created with protected path"
        def flowPathInfo = flow.retrieveAllEntityPaths()
        !flowPathInfo.flowPath.protectedPath.isPathAbsent()

        and: "Rules for main and protected paths are created"
        def flowDBInfo = flow.retrieveDetailsFromDB()
        Wrappers.wait(WAIT_OFFSET) {
            HashMap<SwitchId, List<FlowEntry>> flowInvolvedSwitchesWithRules = flowPathInfo.getInvolvedSwitches()
                    .collectEntries{ [(it): switchRulesFactory.get(it).getRules()] } as HashMap<SwitchId, List<FlowEntry>>
            flow.verifyRulesForProtectedFlowOnSwitches(flowInvolvedSwitchesWithRules, flowDBInfo)
        }

        and: "Validation of flow must be successful"
        flow.validateAndCollectDiscrepancies().isEmpty()

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
        def flow = flowFactory.getBuilder(switchPair).withProtectedPath(false).build().create()

        then: "Flow is created without protected path"
        def flowPathInfo = flow.retrieveAllEntityPaths()
        def initialFlowInfo = flow.retrieveDetails()
        verifyAll {
            !flowPathInfo.flowPath.protectedPath
            !initialFlowInfo.statusDetails
            !initialFlowInfo.allocateProtectedPath
        }

        and: "Source switch passes validation"
        def initialSrcValidation =  switchHelper.validate(switchPair.src.dpId)
        initialSrcValidation.isAsExpected()

        and: "Cookies are created by flow"
        HashMap<SwitchId, Integer> initialAmountOfFlowRules = [switchPair.src.dpId, switchPair.dst.dpId]
                .collectEntries {swId ->
            def createdCookies = switchRulesFactory.get(swId).getRules()
                    .findAll { !new Cookie(it.cookie).serviceFlag }*.cookie

            def swProps = switchHelper.getCachedSwProps(swId)
            def amountOfServer42Rules = 0

            if(swProps.server42FlowRtt){
                amountOfServer42Rules +=1
                swId == switchPair.src.dpId && flow.source.vlanId && ++amountOfServer42Rules
                swId == switchPair.dst.dpId && flow.destination.vlanId && ++amountOfServer42Rules
            }
            def amountOfFlowRules = 3 + amountOfServer42Rules
            assert createdCookies.size() == amountOfFlowRules
            [(swId): amountOfFlowRules]
        }

        when: "Update flow: enable protected path(allocateProtectedPath=true)"
        def updatedFlow = flow.update(flow.tap { it.allocateProtectedPath = true })

        then: "Flow has been updated successfully and protected path is enabled"
        updatedFlow.statusDetails
        initialFlowInfo.lastUpdated < updatedFlow.lastUpdated

        def flowPathInfoAfterUpdating = updatedFlow.retrieveAllEntityPaths()
        !flowPathInfoAfterUpdating.flowPath.protectedPath.isPathAbsent()
        def protectedPathSwitches = flowPathInfoAfterUpdating.flowPath.protectedPath.forward.getInvolvedSwitches()

        def flowInfoFromDb = flow.retrieveDetailsFromDB()
        def protectedFlowCookies = [flowInfoFromDb.protectedForwardPath.cookie.value, flowInfoFromDb.protectedReversePath.cookie.value]

        and: "Rules for main and protected paths are created"
        def flowDBInfo = flow.retrieveDetailsFromDB()
        Wrappers.wait(WAIT_OFFSET) {
            HashMap<SwitchId, List<FlowEntry>> flowInvolvedSwitchesWithRules  = flowPathInfo.getInvolvedSwitches()
                    .collectEntries{ [(it): switchRulesFactory.get(it).getRules()] } as HashMap<SwitchId, List<FlowEntry>>
            flow.verifyRulesForProtectedFlowOnSwitches(flowInvolvedSwitchesWithRules, flowDBInfo)

            def cookiesAfterEnablingProtectedPath = flowInvolvedSwitchesWithRules.get(switchPair.src.dpId)
                    .findAll { !new Cookie(it.cookie).serviceFlag }*.cookie
            // initialAmountOfFlowRules was collected for flow without protected path + one for protected path
            assert cookiesAfterEnablingProtectedPath.size() == initialAmountOfFlowRules.get(switchPair.src.dpId) + 1
        }

        def srcValidation =  switchHelper.validate(switchPair.src.dpId)
        srcValidation.isAsExpected()
        srcValidation.rules.proper.cookie.findAll(REQUIRED_COOKIE).size() == initialSrcValidation.rules.proper.cookie.findAll(REQUIRED_COOKIE).size() + 1

        when: "Update flow: disable protected path(allocateProtectedPath=false)"
        updatedFlow = updatedFlow.update(updatedFlow.tap { it.allocateProtectedPath = false})

        then: "Flow has been updated successfully and protected path is disabled"
        !updatedFlow.statusDetails
        !updatedFlow.retrieveAllEntityPaths().flowPath.protectedPath

        and: "Rules for protected path are deleted"
        Wrappers.wait(WAIT_OFFSET) {
            assert flow.retrieveFlowStatus().status == FlowState.UP
            protectedPathSwitches.each { sw ->
                def rules = switchRulesFactory.get(sw).getRules().findAll {
                    !new Cookie(it.cookie).serviceFlag
                }
                assert rules.findAll { it.cookie in protectedFlowCookies }.isEmpty()
            }
        }
    }

    @Tags(SMOKE)
    def "Able to swap main and protected paths manually"() {
        when: "Flow with protected path has been created successfully"
        def switchPair = switchPairs.all()
                .nonNeighbouring()
                .withTraffgensOnBothEnds()
                .withPathHavingAtLeastNSwitches(4)
                .random()

        def flow = flowFactory.getBuilder(switchPair, true)
                .withProtectedPath(true).build()
                .create()

        then: "Protected path is enabled"
        def flowPathInfo = flow.retrieveAllEntityPaths()
        !flowPathInfo.flowPath.protectedPath.isPathAbsent()

        def initialMainPath =  flowPathInfo.getMainPathInvolvedIsls()
        def initialProtectedPath = flowPathInfo.getProtectedPathInvolvedIsls()
        initialMainPath.intersect(initialProtectedPath).isEmpty()

        and: "Rules for main and protected paths are created"
        def flowDBInfo = flow.retrieveDetailsFromDB()
        Wrappers.wait(WAIT_OFFSET) {
            HashMap<SwitchId, List<FlowEntry>> flowInvolvedSwitchesWithRules = flowPathInfo.getInvolvedSwitches()
                    .collectEntries{ [(it): switchRulesFactory.get(it).getRules()] } as HashMap<SwitchId, List<FlowEntry>>
            flow.verifyRulesForProtectedFlowOnSwitches(flowInvolvedSwitchesWithRules, flowDBInfo)
        }

        and: "Number of flow-related cookies has been collected for both source and destination switch"
        HashMap<SwitchId, Integer> initialAmountOfFlowRules = [switchPair.src.dpId, switchPair.dst.dpId]
                .collectEntries {
                    [(it): switchHelper.validate(it).rules.proper.cookie.findAll(REQUIRED_COOKIE).size()]
                } as HashMap<SwitchId, Integer>

        and: "No rule discrepancies on every switch of the flow on the main path"
        def mainPathSwitches = flowPathInfo.flowPath.path.forward.getInvolvedSwitches()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(mainPathSwitches).isEmpty()

        and: "No rule discrepancies on every switch of the flow on the protected path)"
        def protectedPathSwitches = flowPathInfo.flowPath.protectedPath.forward.getInvolvedSwitches()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(protectedPathSwitches).isEmpty()

        and: "The flow allows traffic(on the main path)"
        def traffExam = traffExamProvider.get()
        def exam = flow.traffExam(traffExam, 1000, 5)
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
        def flowLastUpdate = flow.retrieveDetails().lastUpdated

        flow.swapFlowPath()

        then: "Flow paths are swapped"
        flow.waitForBeingInState(FlowState.UP)
        def flowPathInfoAfterSwapping = flow.retrieveAllEntityPaths()
        def newMainPath = flowPathInfoAfterSwapping.getMainPathInvolvedIsls()
        def newProtectedPath = flowPathInfoAfterSwapping.getProtectedPathInvolvedIsls()
        verifyAll {
            assert newMainPath == initialProtectedPath
            assert newProtectedPath == initialMainPath
        }
        flowLastUpdate < flow.retrieveDetails().lastUpdated

        and: "No rule discrepancies when doing flow validation"
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "Rules are updated"
        def updatedFlowDBInfo = flow.retrieveDetailsFromDB()
        Wrappers.wait(WAIT_OFFSET) {
            HashMap<SwitchId, List<FlowEntry>> flowInvolvedSwitchesWithRules = flowPathInfoAfterSwapping.getInvolvedSwitches()
                    .collectEntries{ [(it): switchRulesFactory.get(it).getRules()] } as HashMap<SwitchId, List<FlowEntry>>
            flow.verifyRulesForProtectedFlowOnSwitches(flowInvolvedSwitchesWithRules, updatedFlowDBInfo)
        }

        and: "New meter is created on the src and dst switches"
        def newSrcSwitchCreatedMeterIds = getCreatedMeterIds(switchPair.src.dpId)
        def newDstSwitchCreatedMeterIds = getCreatedMeterIds(switchPair.dst.dpId)
        //added || x.empty to allow situation when meters are not available on src or dst
        newSrcSwitchCreatedMeterIds.sort() != srcSwitchCreatedMeterIds.sort() || srcSwitchCreatedMeterIds.empty
        newDstSwitchCreatedMeterIds.sort() != dstSwitchCreatedMeterIds.sort() || dstSwitchCreatedMeterIds.empty


        and: "Old meter is deleted on the src and dst switches"
        [switchPair.src.dpId, switchPair.dst.dpId].each { switchId ->
            def switchValidateInfo = switchHelper.validate(switchId)
            if (switchValidateInfo.meters) {
                assert switchValidateInfo.meters.proper.findAll({ dto -> !isDefaultMeter(dto) }).size() == 1
            }
            assert switchValidateInfo.rules.proper.cookie.findAll(REQUIRED_COOKIE).size() == initialAmountOfFlowRules.get(switchId)
            assert switchValidateInfo.isAsExpected()
        }

        and: "Transit switches store the correct info about rules and meters"
        List<SwitchId> involvedTransitSwitches = (islHelper.retrieveInvolvedSwitches(initialMainPath)[1..-2]
                + islHelper.retrieveInvolvedSwitches(initialProtectedPath[1..-2])).dpId.unique() as List<SwitchId>
        Wrappers.wait(WAIT_OFFSET) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(involvedTransitSwitches).isEmpty()
        }

        and: "No rule discrepancies on every switch of the flow on the main path"
        def newMainSwitches = flowPathInfoAfterSwapping.flowPath.path.forward.getInvolvedSwitches()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(newMainSwitches).isEmpty()

        and: "No rule discrepancies on every switch of the flow on the protected path)"
        def newProtectedSwitches = flowPathInfoAfterSwapping.flowPath.protectedPath.forward.getInvolvedSwitches()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(newProtectedSwitches).isEmpty()

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
        List<FlowExtended> flows = []
        List<SwitchPortVlan> busyEndpoints = []
        5.times {
            def flow = flowFactory.getBuilder(switchPair, false, busyEndpoints)
                    .withBandwidth(bandwidth)
                    .withIgnoreBandwidth(bandwidth == 0)
                    .withProtectedPath(true).build()
                    .create()
            busyEndpoints.addAll(flow.occupiedEndpoints())
            flows << flow
        }

        then: "Flows are created with protected path"
        def flowPathsInfo = flows.collect { it.retrieveAllEntityPaths() }
        flowPathsInfo.each { assert !it.flowPath.protectedPath.isPathAbsent()}

        and: "Current paths are not equal to protected paths"
        def firstFlowMainPathIsls = flowPathsInfo.first().getMainPathInvolvedIsls()
        def firstFlowProtectedPathIsls = flowPathsInfo.first().getProtectedPathInvolvedIsls()
        assert firstFlowMainPathIsls.intersect(firstFlowProtectedPathIsls).isEmpty()
        //check that all other flows use the same paths, so above verification applies to all of them
        flowPathsInfo.each { flowPathInfo ->
            assert flowPathInfo.getMainPathInvolvedIsls() == firstFlowMainPathIsls
            assert flowPathInfo.getProtectedPathInvolvedIsls() == firstFlowProtectedPathIsls
        }

        and: "Bandwidth is reserved for protected paths on involved ISLs"
        def protectedIslsInfo = firstFlowProtectedPathIsls.collect { islUtils.getIslInfo(it).get() }
        initialIsls.each { initialIsl ->
            protectedIslsInfo.each { currentIsl ->
                if (initialIsl.id == currentIsl.id) {
                    assert initialIsl.availableBandwidth - currentIsl.availableBandwidth == flows.sum { it.maximumBandwidth }
                }
            }
        }

        when: "Break ISL on the main path (bring port down) to init auto swap"
        def islToBreak = firstFlowMainPathIsls.first()
        islHelper.breakIsl(islToBreak)

        then: "Flows are switched to protected paths"
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            flows.each { flow ->
                assert flow.retrieveFlowStatus().status == FlowState.UP
                def flowPathInfoAfterRerouting = flow.retrieveAllEntityPaths()

                assert flowPathInfoAfterRerouting.getMainPathInvolvedIsls() == firstFlowProtectedPathIsls
                if (4 <= uniquePathCount) {
                    // protected path is recalculated due to the main path broken ISl
                    assert flowPathInfoAfterRerouting.getProtectedPathInvolvedIsls() != firstFlowMainPathIsls
                    assert flowPathInfoAfterRerouting.getProtectedPathInvolvedIsls() != firstFlowProtectedPathIsls
                }
            }
        }

        when: "Restore port status"
        islHelper.restoreIsl(islToBreak)

        then: "Path of the flow is not changed"
        flows.each { flow ->
            flow.waitForBeingInState(FlowState.UP)
            assert flow.retrieveAllEntityPaths().getMainPathInvolvedIsls() == firstFlowProtectedPathIsls
        }

        where:
        flowDescription | bandwidth
        "a metered"     | 1000
        "an unmetered"  | 0
    }

    @Tags([ISL_RECOVER_ON_FAIL, ISL_PROPS_DB_RESET])
    def "Flow swaps to protected path when main path gets broken, becomes DEGRADED if protected path is unable to reroute(no bw)"() {
        given: "Two switches with 2 diverse paths at least"
        def switchPair = switchPairs.all().withAtLeastNNonOverlappingPaths(2).random()

        when: "Create flow with protected path"
        def flow = flowFactory.getBuilder(switchPair).withProtectedPath(true).build().create()
        def initialFlowPath = flow.retrieveAllEntityPaths()

        and: "Other paths have not enough bandwidth to host the flow in case of reroute"
        def originalMainPathIsls = initialFlowPath.getMainPathInvolvedIsls()
        def originalProtectedPathIsls = initialFlowPath.getProtectedPathInvolvedIsls()
        def usedIsls = originalMainPathIsls + originalProtectedPathIsls
        def otherIsls = switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }
                .findAll { !it.containsAll(originalMainPathIsls) && !it.containsAll(originalProtectedPathIsls) }.flatten()
                .unique().findAll { !usedIsls.contains(it) && !usedIsls.contains(it.reversed) }
                .unique { a, b -> a == b || a == b.reversed ? 0 : 1 } as List<Isl>
        islHelper.setAvailableAndMaxBandwidth(otherIsls.collectMany{[it, it.reversed]}, flow.maximumBandwidth - 1)

        and: "Main flow path breaks"
        def mainIsl = originalMainPathIsls.first()
        islHelper.breakIsl(mainIsl)

        then: "Main path swaps to protected, flow becomes degraded, main path UP, protected DOWN"
        Wrappers.wait(WAIT_OFFSET) {
            def newPath = flow.retrieveAllEntityPaths()
            assert newPath.getMainPathInvolvedIsls() == originalProtectedPathIsls
            verifyAll(flow.retrieveDetails()) {
                status == FlowState.DEGRADED
                statusDetails.mainPath == "Up"
                statusDetails.protectedPath == "Down"
                statusInfo == StatusInfo.OVERLAPPING_PROTECTED_PATH
            }
        }

        when: "ISL gets back up"
        islHelper.restoreIsl(mainIsl)

        then: "Main path remains the same, flow becomes UP, main path UP, protected UP"
        Wrappers.wait(WAIT_OFFSET * 2) {
            def newPath = flow.retrieveAllEntityPaths()
            assert newPath.getMainPathInvolvedIsls() == originalProtectedPathIsls
            verifyAll(flow.retrieveDetails()) {
                status == FlowState.UP
                statusDetails.mainPath == "Up"
                statusDetails.protectedPath == "Up"
            }
        }
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Flow swaps to protected path when main path gets broken, becomes DEGRADED if protected path is unable to reroute(no path)"() {
        given: "Two switches with 2 diverse paths at least"
        def switchPair = switchPairs.all().withAtLeastNNonOverlappingPaths(2).random()

        when: "Create flow with protected path"
        def flow = flowFactory.getBuilder(switchPair).withProtectedPath(true).build().create()

        def initialFlowPath = flow.retrieveAllEntityPaths()

        and: "Other paths are not available (ISLs are down)"
        def originalMainPathIsls = initialFlowPath.getMainPathInvolvedIsls()
        def originalProtectedPathIsls = initialFlowPath.getProtectedPathInvolvedIsls()
        def usedIsls = originalMainPathIsls + originalProtectedPathIsls
        def otherIsls = switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls()}
                .findAll { it != originalMainPathIsls && it != originalProtectedPathIsls }.flatten()
                .unique().findAll { !usedIsls.contains(it) && !usedIsls.contains(it.reversed) }
                .unique { a, b -> a == b || a == b.reversed ? 0 : 1 } as List<Isl>
        islHelper.breakIsls(otherIsls)

        and: "Main flow path breaks"
        def mainIsl = originalMainPathIsls.first()
        islHelper.breakIsl(mainIsl)

        then: "Main path swaps to protected, flow becomes degraded, main path UP, protected DOWN"
        Wrappers.wait(WAIT_OFFSET) {
            def newPath = flow.retrieveAllEntityPaths()
            assert newPath.getMainPathInvolvedIsls() == originalProtectedPathIsls
            verifyAll(flow.retrieveDetails()) {
                status == FlowState.DEGRADED
                statusDetails.mainPath == "Up"
                statusDetails.protectedPath == "Down"
                statusInfo == StatusInfo.OVERLAPPING_PROTECTED_PATH
            }
        }

        when: "ISL on broken path gets back up"
        islHelper.restoreIsl(mainIsl)

        then: "Main path remains the same (no swap), flow becomes UP, main path remains UP, protected path becomes UP"
        Wrappers.wait(WAIT_OFFSET) {
            def newPath = flow.retrieveAllEntityPaths()
            assert newPath.getMainPathInvolvedIsls() == originalProtectedPathIsls
            assert newPath.getProtectedPathInvolvedIsls() == originalMainPathIsls
            verifyAll(flow.retrieveDetails()) {
                status == FlowState.UP
                statusDetails.mainPath == "Up"
                statusDetails.protectedPath == "Up"
            }
        }
    }

    def "System reroutes #flowDescription flow to more preferable path and ignores protected path when reroute\
 is intentional"() {
        // 'and ignores protected path' means that the main path won't changed to protected
        given: "Two active neighboring switches with four diverse paths at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(4).random()

        and: "A flow with protected path"
        def flow = flowFactory.getBuilder(switchPair)
                .withBandwidth(bandwidth)
                .withIgnoreBandwidth(bandwidth == 0)
                .withProtectedPath(true).build()
                .create()

        def flowPathInfo = flow.retrieveAllEntityPaths()
        assert !flowPathInfo.flowPath.protectedPath.isPathAbsent()

        def initialMainPathIsls = flowPathInfo.getMainPathInvolvedIsls()
        def initialProtectedPathIsls = flowPathInfo.getProtectedPathInvolvedIsls()
        assert initialMainPathIsls.intersect(initialProtectedPathIsls).isEmpty()

        when: "Make the current and protected path less preferable than alternatives"
        def alternativePaths = switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }
                .findAll { it != initialMainPathIsls && it != initialProtectedPathIsls }
        alternativePaths.each { islHelper.makePathIslsMorePreferable(it, initialMainPathIsls)}
        alternativePaths.each { islHelper.makePathIslsMorePreferable(it, initialProtectedPathIsls) }

        and: "Init intentional reroute"
        def rerouteResponse = flow.reroute()

        then: "Flow is rerouted"
        rerouteResponse.rerouted
        Wrappers.wait(WAIT_OFFSET) {
            flow.retrieveFlowStatus().status == FlowState.UP
        }

        and: "Path is not changed to protected path"
        def flowPathInfoAfterRerouting = flow.retrieveAllEntityPaths()
        def mainPathIsls = flowPathInfoAfterRerouting.getMainPathInvolvedIsls()
        mainPathIsls != initialMainPathIsls
        mainPathIsls != initialProtectedPathIsls
        //protected path is rerouted too, because more preferable path is exist
        def newCurrentProtectedPathIsls = flowPathInfoAfterRerouting.getProtectedPathInvolvedIsls()
        newCurrentProtectedPathIsls != initialMainPathIsls
        newCurrentProtectedPathIsls != initialProtectedPathIsls
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.UP }

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
        def flow = flowFactory.getBuilder(switchPair)
                .withBandwidth(bandwidth)
                .withIgnoreBandwidth(bandwidth == 0)
                .withProtectedPath(true).build()
                .create()

        def flowPathInfo = flow.retrieveAllEntityPaths()
        assert !flowPathInfo.flowPath.protectedPath.isPathAbsent()

        def initialMainPathIsls = flowPathInfo.getMainPathInvolvedIsls()
        def initialProtectedPathIsls = flowPathInfo.getProtectedPathInvolvedIsls()
        assert initialMainPathIsls.intersect(initialProtectedPathIsls).isEmpty()

        when: "Make the current and protected path less preferable than alternatives"
        def alternativePaths = switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }
                .findAll { it != initialMainPathIsls && it != initialProtectedPathIsls }
        alternativePaths.each { islHelper.makePathIslsMorePreferable(it, initialMainPathIsls) }
        alternativePaths.each { islHelper.makePathIslsMorePreferable(it, initialProtectedPathIsls) }

        and: "Break ISL on the main path (bring port down) to init auto swap"
        def islToBreak = initialMainPathIsls.first()
        islHelper.breakIsl(islToBreak)

        then: "Flow is switched to protected path"
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            def newPathInfo = flow.retrieveAllEntityPaths()
            def newMainPath = newPathInfo.getMainPathInvolvedIsls()
            assert flow.retrieveFlowStatus().status == FlowState.UP
            assert newMainPath != initialMainPathIsls
            assert newMainPath == initialProtectedPathIsls

            def newCurrentProtectedPath = newPathInfo.getProtectedPathInvolvedIsls()
            if (4 <= uniquePathCount) {
                assert newCurrentProtectedPath != initialMainPathIsls
                assert newCurrentProtectedPath != initialProtectedPathIsls
            }
        }

        when: "Restore port status"
        islHelper.restoreIsl(islToBreak)

        then: "Path of the flow is not changed"
        flow.retrieveAllEntityPaths().getMainPathInvolvedIsls() == initialProtectedPathIsls

        where:
        flowDescription | bandwidth
        "a metered"     | 1000
        "an unmetered"  | 0
    }

    def "A flow with protected path does not get rerouted if already on the best path"() {
        given: "Two active neighboring switches and a flow"
        def switchPair = switchPairs.all().neighbouring().random()

        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(true).build()
                .create()

        def flowPathInfo = flow.retrieveAllEntityPaths()
        assert !flowPathInfo.flowPath.protectedPath.isPathAbsent()

        def initialMainPath = flowPathInfo.getMainPathInvolvedIsls()
        def initialProtectedPath = flowPathInfo.getProtectedPathInvolvedIsls()

        when: "Init intentional reroute"
        def rerouteResponse = flow.reroute()

        then: "Flow is not rerouted"
        !rerouteResponse.rerouted

        def newFlowPathInfo = flow.retrieveAllEntityPaths()
        newFlowPathInfo.getMainPathInvolvedIsls() == initialMainPath
        newFlowPathInfo.getProtectedPathInvolvedIsls() == initialProtectedPath
    }

    @Tags([LOW_PRIORITY, ISL_PROPS_DB_RESET])
    def "Able to update a flow to enable protected path when there is not enough bandwidth"() {
        given: "Two active neighboring switches"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        and: "Update all ISLs which can be used by protected path"
        def bandwidth = 100
        islHelper.setAvailableBandwidth(isls[1..-1], 90)

        when: "Create flow without protected path"
        def flow = flowFactory.getBuilder(srcSwitch, dstSwitch)
                .withBandwidth(bandwidth)
                .withProtectedPath(false).build()
                .create()

        then: "Flow is created without protected path"
        !flow.retrieveAllEntityPaths().flowPath.protectedPath

        when: "Update flow: enable protected path"
        flow.update(flow.tap { it.allocateProtectedPath = true }, FlowState.DEGRADED)

        then: "Flow state is changed to DEGRADED"
        verifyAll(flow.retrieveDetails().statusDetails) {
            mainPath == "Up"
            protectedPath == "Down"
        }
    }

    @Tags(ISL_PROPS_DB_RESET)
    def "Able to create a flow with protected path when there is not enough bandwidth and ignoreBandwidth=true"() {
        given: "Two active neighboring switches"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        and: "Update all ISLs which can be used by protected path"
        def bandwidth = 100
        islHelper.setAvailableBandwidth(isls[1..-1], 90)

        when: "Create flow with protected path"
        def flow = flowFactory.getBuilder(srcSwitch, dstSwitch)
                .withBandwidth(bandwidth)
                .withIgnoreBandwidth(true)
                .withProtectedPath(true).build()
                .create()


        then: "Flow is created with protected path"
        !flow.retrieveAllEntityPaths().flowPath.protectedPath.isPathAbsent()

        and: "One transit vlan is created for main and protected paths"
        def flowInfo = flow.retrieveDetailsFromDB()
        database.getTransitVlans(flowInfo.forwardPathId, flowInfo.reversePathId).size() == 1
        database.getTransitVlans(flowInfo.protectedForwardPathId, flowInfo.protectedReversePathId).size() == 1
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "System is able to recalculate protected path when protected path is broken"() {
        given: "Two active not neighboring switches with three diverse paths at least"
        def allIsls = northbound.getAllLinks()
        def switchPair = switchPairs.all().nonNeighbouring().withAtLeastNNonOverlappingPaths(3).random()

        when: "Create a flow with protected path"
        def flow = flowFactory.getBuilder(switchPair).withProtectedPath(true).build().create()

        then: "Flow is created with protected path"
        def initialFlowPathInfo = flow.retrieveAllEntityPaths()
        !initialFlowPathInfo.flowPath.protectedPath.isPathAbsent()

        def initialMainPathIsls = initialFlowPathInfo.getMainPathInvolvedIsls()
        def initialProtectedPathIsls = initialFlowPathInfo.getProtectedPathInvolvedIsls()
        assert initialMainPathIsls.intersect(initialProtectedPathIsls).isEmpty()

        def protectedIslsInfo = initialProtectedPathIsls.collect { islUtils.getIslInfo(it).get() }
        allIsls.each { isl ->
            protectedIslsInfo.each { protectedIsl ->
                if (isl.id == protectedIsl.id) {
                    assert isl.availableBandwidth - protectedIsl.availableBandwidth == flow.maximumBandwidth
                }
            }
        }

        when: "Break ISL on the protected path (bring port down) to init the recalculate procedure"
        def islToBreakProtectedPath = initialProtectedPathIsls[0]
        islHelper.breakIsl(islToBreakProtectedPath)

        then: "Protected path is recalculated"
        FlowEntityPath newFlowPathInfo
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            newFlowPathInfo = flow.retrieveAllEntityPaths()
            assert newFlowPathInfo.getProtectedPathInvolvedIsls() != initialProtectedPathIsls
            assert flow.retrieveFlowStatus().status == FlowState.UP
        }

        and: "Current path is not changed"
        initialMainPathIsls == newFlowPathInfo.getMainPathInvolvedIsls()

        and: "Bandwidth is reserved for new protected path on involved ISLs"
        def allLinks
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            def newProtectedIsls = newFlowPathInfo.getProtectedPathInvolvedIsls()
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
        flow.retrieveAllEntityPaths().getProtectedPathInvolvedIsls()== newFlowPathInfo.getProtectedPathInvolvedIsls()
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    @IterationTag(tags = [LOW_PRIORITY], iterationNameRegex = /unmetered/)
    def "Able to update #flowDescription flow to enable protected path if all alternative paths are unavailable"() {
        given: "Two active neighboring switches with two not overlapping paths at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(2).random()


        and: "A flow without protected path"
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(false)
                .withBandwidth(bandwidth)
                .withIgnoreBandwidth(bandwidth == 0).build()
                .create()
        def initialFlowPathInfo = flow.retrieveAllEntityPaths()

        and: "All alternative paths are unavailable (bring ports down on the source switch)"
        def flowPathPortOnSourceSwitch = initialFlowPathInfo.getMainPathInvolvedIsls().first().srcPort
        def broughtDownIsls = topology.getRelatedIsls(switchPair.getSrc())
                .findAll{it.srcSwitch == switchPair.getSrc() && it.srcPort != flowPathPortOnSourceSwitch }
        islHelper.breakIsls(broughtDownIsls)

        when: "Update flow: enable protected path(allocateProtectedPath=true)"
        flow.update(flow.tap { it.allocateProtectedPath = true }, FlowState.DEGRADED)

        then: "Flow state is changed to DEGRADED as protected path is DOWN"
        verifyAll(flow.retrieveDetails().statusDetails) {
            mainPath == "Up"
            protectedPath == "Down"
        }

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
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(true)
                .withBandwidth(bandwidth)
                .withIgnoreBandwidth(bandwidth == 0).build()
                .create()

        def flowPathInfo = flow.retrieveAllEntityPaths()
        assert !flowPathInfo.flowPath.protectedPath.isPathAbsent()

        when: "All alternative paths are unavailable"
        def mainPathIsls = flowPathInfo.getMainPathInvolvedIsls()
        def untouchableIsls = mainPathIsls.collectMany { [it, it.reversed] }
        def altPaths = switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }
                .findAll { !it.containsAll(mainPathIsls) }
        def islsToBreak = altPaths.flatten().unique().collectMany { [it, it.reversed] }
                .findAll { !untouchableIsls.contains(it) }.unique { [it, it.reversed].sort() } as List<Isl>
        islHelper.breakIsls(islsToBreak)

        then: "Flow status is DEGRADED"
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(flow.retrieveDetails()) {
                status == FlowState.DEGRADED
                statusInfo == StatusInfo.OVERLAPPING_PROTECTED_PATH
            }
            def rerouteEvent = flow.retrieveFlowHistory().getEntriesByType(REROUTE)
            assert rerouteEvent && rerouteEvent.last().payload.last().action == REROUTE_FAILED.payloadLastAction
            assert flow.retrieveFlowHistoryStatus(1).statusBecome == [FlowStatusHistoryEvent.DEGRADED]
        }

        when: "Update flow: disable protected path(allocateProtectedPath=false)"
      flow.update(flow.tap { it.allocateProtectedPath = false })

        then: "Flow status is UP"
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(flow.retrieveDetails()) {
                status == FlowState.UP
                !statusInfo //statusInfo is cleared after changing flowStatus to UP
            }
        }

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
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(true).build()
                .create()

        when: "Main paths breaks"
        def flowPathInfo = flow.retrieveAllEntityPaths()
        def mainPathIsl = flowPathInfo.getMainPathInvolvedIsls().first()
        def protectedPathIsl = flowPathInfo.getProtectedPathInvolvedIsls().first()
        islHelper.breakIsl(mainPathIsl)

        and: "Protected path breaks when swap is in progress"
        //we want to break the second ISL right when the protected path reroute starts. race here
        //+750ms correction was found experimentally. helps to hit the race condition more often (local env)
        sleep(Math.max(0, (rerouteDelay - antiflapMin) * 1000 + 750))
        islHelper.breakIsl(protectedPathIsl)

        then: "Both paths are successfully evacuated from broken isls and the flow is UP"
        log.debug("original main: $mainPathIsl\n original protected: $protectedPathIsl")
        Wrappers.wait(rerouteDelay + PATH_INSTALLATION_TIME) {
            Wrappers.timedLoop(3) { //this should be a stable result, all reroutes must finish
                assert flow.retrieveFlowStatus().status == FlowState.UP
                def currentPath = flow.retrieveAllEntityPaths()
                assert currentPath.getMainPathInvolvedIsls().intersect([mainPathIsl, protectedPathIsl]).isEmpty()
                assert currentPath.getProtectedPathInvolvedIsls().intersect([mainPathIsl, protectedPathIsl]).isEmpty()

            }
        }
    }

    @Issue("https://github.com/telstra/open-kilda/issues/5731")
    @Ignore
    def "System reuses current protected path when can't find new non overlapping protected path while intentional\
 rerouting"() {
        given: "Two active neighboring switches with three diverse paths"
        def switchPair = switchPairs.all().neighbouring().withExactlyNNonOverlappingPaths(3).random()

        and: "A flow with protected path"
        def flow = flowFactory.getBuilder(switchPair).withProtectedPath(true).build().create()

        def flowPathInfo = flow.retrieveAllEntityPaths()
        assert !flowPathInfo.flowPath.protectedPath.isPathAbsent()

        def initialMainPathIsls = flowPathInfo.getMainPathInvolvedIsls()
        def initialProtectedPathIsls = flowPathInfo.getProtectedPathInvolvedIsls()
        assert initialMainPathIsls.intersect(initialProtectedPathIsls).isEmpty()

        when: "Make the current and protected path less preferable than alternatives"
        def alternativePaths = switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }
                .findAll { it != initialMainPathIsls && it != initialProtectedPathIsls }
        withPool {
            alternativePaths.eachParallel { islHelper.makePathIslsMorePreferable(it, initialMainPathIsls) }
            alternativePaths.eachParallel { islHelper.makePathIslsMorePreferable(it, initialProtectedPathIsls) }
        }

        and: "Init intentional reroute"
        def rerouteResponse = flow.reroute()

        then: "Flow should be rerouted"
        rerouteResponse.rerouted

        and: "Flow main path should be rerouted to a new path and ignore protected path"
        FlowEntityPath flowPathInfoAfterRerouting
        def newMainPath
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            flowPathInfoAfterRerouting = flow.retrieveAllEntityPaths()
            newMainPath = flowPathInfoAfterRerouting.getMainPathInvolvedIsls()
            newMainPath != initialMainPathIsls && newMainPath != initialProtectedPathIsls
        }

        and: "Flow protected path shouldn't be rerouted due to lack of non overlapping path"
        flowPathInfoAfterRerouting.getProtectedPathInvolvedIsls() == initialProtectedPathIsls

        and: "Flow and both its paths are UP"
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(flow.retrieveDetails()) {
                status == FlowState.UP
                statusDetails.mainPath == "Up"
                statusDetails.protectedPath == "Up"
            }
        }
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Protected path is created in different POP even if this path is not preferable"(){
        given: "Not neighboring switch pair with three diverse paths at least"
        List<Path> allPathsWithThreeSwitches //all possible paths with 3 involved switches
        List<Path> allPathCandidates // 3 diverse paths at least

        def swPair = switchPairs.all().nonNeighbouring().getSwitchPairs().find { swP ->
            allPathsWithThreeSwitches = swP.retrieveAvailablePaths().findAll { it.getInvolvedSwitches().size() == 3 }
            allPathCandidates = allPathsWithThreeSwitches.unique(false) { a, b ->
                a.retrieveNodes().intersect(b.retrieveNodes()) == [] ? 1 : 0
            } // just to avoid parallel links
            allPathsWithThreeSwitches.unique(false) { a, b ->
                def p1 = a.getInvolvedSwitches()[1..-2]
                def p2 = b.getInvolvedSwitches()[1..-2]
                p1.intersect(p2) == [] ? 1 : 0
            }.size() >= 3
        } ?: assumeTrue(false, "No suiting switches found")

        //select paths for further manipulations
        def availablePathsIsls = allPathCandidates.collect { it.getInvolvedIsls()}
        def mainPath1Isls = availablePathsIsls.first()
        def mainPath2Isls = availablePathsIsls.find { it != mainPath1Isls }
        def protectedPathIsls = availablePathsIsls.find { it != mainPath1Isls && it != mainPath2Isls }

        def involvedSwP1 = islHelper.retrieveInvolvedSwitches(mainPath1Isls).dpId
        def involvedSwP2 = islHelper.retrieveInvolvedSwitches(mainPath2Isls).dpId
        def involvedSwProtected = islHelper.retrieveInvolvedSwitches(protectedPathIsls).dpId

        and: "Src, dst and transit switches belongs to different POPs(src:1, dst:4, tr1/tr2:2, tr3:3)"
        // tr1/tr2 for the main path and tr3 for the protected path
        switchHelper.partialUpdate(swPair.src.dpId, new SwitchPatchDto().tap { it.pop = "1" })
        [involvedSwP1[1], involvedSwP2[1]].each { swId ->
            switchHelper.partialUpdate(swId, new SwitchPatchDto().tap { it.pop = "2" })
        }
        switchHelper.partialUpdate(swPair.dst.dpId, new SwitchPatchDto().tap { it.pop = "4" })
        switchHelper.partialUpdate(involvedSwProtected[1], new SwitchPatchDto().tap { it.pop = "3" })

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
        allPathsWithThreeSwitches.findAll { involvedSwProtected[1] in it.getInvolvedSwitches() }.each {
            it.getInvolvedIsls().each {
                islsToUpdate << it
            }
        }
        def defaultIslCost = 700
        def totalCostOfMainPath = mainPath1Isls.sum { northbound.getLink(it).cost ?: defaultIslCost }
        def amountOfIlslsOnMainPath = mainPath1Isls.size()
        def diversityPopIslCost = 1000
        def diversityGroupPerPopUseCounter = 2
        Integer newIslCost = ((totalCostOfMainPath +
                (amountOfIlslsOnMainPath * diversityPopIslCost * diversityGroupPerPopUseCounter) - 1) /
                protectedPathIsls.size()).toInteger()
        log.debug("newCost: $newIslCost")

        islHelper.updateIslsCost(islsToUpdate, newIslCost)

        and: "All alternative paths unavailable (bring ports down on the source switch)"
        List<Isl> broughtDownIsls = topology.getRelatedIsls(swPair.src) -
                [mainPath1Isls, mainPath2Isls, protectedPathIsls].collect { it.first()}
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
        def flow = flowFactory.getBuilder(swPair).withProtectedPath(true).build().create()

        then: "Main path is built through the preferable path(tr1 or tr2)"
        def flowPaths = flow.retrieveAllEntityPaths()
        def realFlowPathInvolvedSwitches = flowPaths.flowPath.path.forward.getInvolvedSwitches()
        realFlowPathInvolvedSwitches == involvedSwP1 || realFlowPathInvolvedSwitches == involvedSwP2

        and: "Protected path is built through the non preferable path(tr3)"
        flowPaths.flowPath.protectedPath.forward.getInvolvedSwitches() == involvedSwProtected
    }

    @Tags(LOW_PRIORITY)
    def "Unable to perform the 'swap' request for a flow without protected path"() {
        given: "Two active neighboring switches"
        def switchPair = switchPairs.all(false).neighbouring().random()

        and: "A flow without protected path"
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(false).build()
                .create()
        assert !flow.retrieveAllEntityPaths().flowPath.protectedPath

        when: "Try to swap paths for flow that doesn't have protected path"
        flow.swapFlowPath()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowPathNotSwappedExpectedError(
                ~/Could not swap paths: Flow $flow.flowId doesn't have protected path/).matches(exc)
    }

    @Tags(LOW_PRIORITY)
    def "Unable to swap paths for a non-existent flow"() {
        when: "Try to swap path on a non-existent flow"
        northbound.swapFlowPath(NON_EXISTENT_FLOW_ID)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowPathNotSwappedExpectedError(HttpStatus.NOT_FOUND,
                ~/Could not swap paths: Flow $NON_EXISTENT_FLOW_ID not found/).matches(exc)
    }

    @Tags(ISL_PROPS_DB_RESET)
    def "Unable to create a flow with protected path when there is not enough bandwidth"() {
        given: "Two active neighboring switches"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        and: "Update all ISLs which can be used by protected path"
        def bandwidth = 100
        islHelper.setAvailableBandwidth(isls[1..-1], 90)

        when: "Create flow with protected path"
        flowFactory.getBuilder(srcSwitch, dstSwitch)
                .withBandwidth(bandwidth)
                .withProtectedPath(true).build()
                .sendCreateRequest()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedWithMissingPathExpectedError(
                ~/Not enough bandwidth or no path found. Couldn't find non overlapping protected path/).matches(exc)
    }

    @Tags([LOW_PRIORITY, ISL_RECOVER_ON_FAIL])
    def "Unable to swap paths for an inactive flow"() {
        given: "Two active neighboring switches with two not overlapping paths at least"
        def switchPair = switchPairs.all().neighbouring()
                //https://github.com/telstra/open-kilda/issues/5608
                .excludeSwitches(topology.activeSwitches.findAll {it.dpId.toString().endsWith("08")})
                .withAtLeastNNonOverlappingPaths(2).random()

        and: "A flow with protected path"
        def flow = flowFactory.getBuilder(switchPair).withProtectedPath(true).build().create()

        and: "All alternative paths are unavailable (bring ports down on the source switch)"
        def flowPathIsl = flow.retrieveAllEntityPaths().getMainPathInvolvedIsls()
        def broughtDownIsls = topology.getRelatedIsls(switchPair.src) - flowPathIsl
        islHelper.breakIsls(broughtDownIsls)

        when: "Break ISL on a protected path (bring port down) for changing the flow state to DEGRADED"
        def flowPathInfo = flow.retrieveAllEntityPaths()
        def protectedIsls = flowPathInfo.getProtectedPathInvolvedIsls()
        def currentIsls = flowPathInfo.getMainPathInvolvedIsls()
        islHelper.breakIsl(protectedIsls[0])

        then: "Flow state is changed to DEGRADED"
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.DEGRADED }
        verifyAll(flow.retrieveDetails()) {
            statusDetails.mainPath == "Up"
            statusDetails.protectedPath == "Down"
        }

        when: "Break ISL on the main path (bring port down) for changing the flow state to DOWN"
        islHelper.breakIsl(currentIsls[0])

        then: "Flow state is changed to DOWN"
        Wrappers.wait(WAIT_OFFSET) {
            assert flow.retrieveFlowStatus().status == FlowState.DOWN
            assert flow.retrieveFlowHistory().getEntriesByType(REROUTE).find{
                it.taskId =~ (/.+ : retry #1 ignore_bw true/)
            }?.payload?.last()?.action == REROUTE_FAILED.payloadLastAction
        }
        verifyAll(flow.retrieveDetails()) {
            statusDetails.mainPath == "Down"
            statusDetails.protectedPath == "Down"
        }

        when: "Try to swap paths when main/protected paths are not available"
        flow.swapFlowPath()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowPathNotSwappedExpectedError(
                ~/Could not swap paths: Protected flow path ${flow.flowId} is not in ACTIVE state/).matches(exc)

        when: "Restore ISL for the main path only"
        islHelper.restoreIsl(currentIsls[0])

        then: "Flow state is still DEGRADED"
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            assert flow.retrieveFlowStatus().status == FlowState.DEGRADED
            verifyAll(flow.retrieveDetails()) {
                statusDetails.mainPath == "Up"
                statusDetails.protectedPath == "Down"
                statusInfo == StatusInfo.OVERLAPPING_PROTECTED_PATH
            }
        }

        when: "Try to swap paths when the main path is available and the protected path is not available"
        flow.swapFlowPath()

        then: "Human readable error is returned"
        def exc1 = thrown(HttpClientErrorException)
        new FlowPathNotSwappedExpectedError(
                ~/Could not swap paths: Protected flow path ${flow.flowId} is not in ACTIVE state/).matches(exc1)

        when: "Restore ISL for the protected path"
        islHelper.restoreIsl(protectedIsls[0])

        then: "Flow state is changed to UP"
        //it often fails in scope of the whole spec on the hardware env, that's why '* 1.5' is added
        Wrappers.wait(discoveryInterval * 1.5 + WAIT_OFFSET) {
            assert flow.retrieveFlowStatus().status == FlowState.UP
        }
    }

    @Tags(LOW_PRIORITY)
    def "Unable to create a single switch flow with protected path"() {
        given: "A switch"
        def sw = topology.activeSwitches.first()

        when: "Create single switch flow"
        flowFactory.getBuilder(sw, sw).withProtectedPath(true).build().sendCreateRequest()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(~/Couldn't setup protected path for one-switch flow/).matches(exc)
    }

    @Tags(LOW_PRIORITY)
    def "Unable to update a single switch flow to enable protected path"() {
        given: "A switch"
        def sw = topology.activeSwitches.first()

        and: "A flow without protected path"
        def flow = flowFactory.getBuilder(sw, sw).build().create()

        when: "Update flow: enable protected path"
        flow.update(flow.tap { it.allocateProtectedPath = true })

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotUpdatedExpectedError(~/Couldn't setup protected path for one-switch flow/).matches(exc)
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    @IterationTag(tags = [LOW_PRIORITY], iterationNameRegex = /unmetered/)
    def "Unable to create #flowDescription flow with protected path if all alternative paths are unavailable"() {
        given: "Two active neighboring switches without alt paths"
        def switchPair = switchPairs.all().neighbouring().random()
        def broughtDownIsls = topology.getRelatedIsls(switchPair.src)[1..-1]
        islHelper.breakIsls(broughtDownIsls)

        when: "Try to create a new flow with protected path"
       flowFactory.getBuilder(switchPair)
                .withProtectedPath(true)
                .withBandwidth(bandwidth)
                .withIgnoreBandwidth(bandwidth == 0).build()
                .sendCreateRequest()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedWithMissingPathExpectedError(
                ~/Not enough bandwidth or no path found. Couldn't find non overlapping protected path/).matches(exc)

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
        def flow = flowFactory.getBuilder(switchPair).withProtectedPath(true).build().create()

        and: "All alternative paths are unavailable (bring ports down on the source switch)"
        def initialFlowPathInfo = flow.retrieveAllEntityPaths()
        def mainPathIsl = initialFlowPathInfo.getMainPathInvolvedIsls()
        def protectedIslToBreak = initialFlowPathInfo.getProtectedPathInvolvedIsls()
        def broughtDownIsls = topology.getRelatedIsls(switchPair.src) - mainPathIsl.first() - protectedIslToBreak.first()
        islHelper.breakIsls(broughtDownIsls)

        and: "ISL on a protected path is broken(bring port down) for changing the flow state to DEGRADED"
        islHelper.breakIsl(protectedIslToBreak.first())
        flow.waitForHistoryEvent(REROUTE_FAILED)
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.DEGRADED }

        when: "Make the current paths(main and protected) less preferable than alternative path"
        def availablePathsIsls = switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls()}
        def alternativeIsl = availablePathsIsls.find { it != mainPathIsl && it != protectedIslToBreak }
        availablePathsIsls.findAll { !it.containsAll(alternativeIsl) }.each {islHelper.makePathIslsMorePreferable(alternativeIsl, it)}

        int alternativeIslCost = alternativeIsl.sum { northbound.getLink(it).cost }
        assert mainPathIsl.sum { northbound.getLink(it).cost } > alternativeIslCost

        and: "Make alternative path available(bring port up on the source switch)"
        islHelper.restoreIsl(alternativeIsl.first())

        then: "Reroute has been executed successfully and flow state is changed to UP"
        flow.waitForHistoryEvent(REROUTE)
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.UP }

        and: "Protected path is recalculated only"
        def newFlowPathInfo = flow.retrieveAllEntityPaths()
        newFlowPathInfo.getMainPathInvolvedIsls() == mainPathIsl
        newFlowPathInfo.getProtectedPathInvolvedIsls() == alternativeIsl
    }

    @Tags(LOW_PRIORITY)
    def "System doesn't allow to enable the pinned flag on a protected flow"() {
        given: "A protected flow"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        def flow = flowFactory.getBuilder(switchPair).withProtectedPath(true).build().create()

        when: "Update flow: enable the pinned flag(pinned=true)"
        flow.update(flow.tap { it.pinned = true })

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotUpdatedExpectedError(~/Flow flags are not valid, unable to process pinned protected flow/).matches(exc)
    }

    List<Integer> getCreatedMeterIds(SwitchId switchId) {
        return northbound.getAllMeters(switchId).meterEntries.findAll {
            it.meterId > MAX_SYSTEM_RULE_METER_ID
        }*.meterId
    }
}
