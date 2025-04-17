package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_PROPS_DB_RESET
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.helpers.model.FlowActionType.REROUTE
import static org.openkilda.functionaltests.helpers.model.FlowActionType.REROUTE_FAILED
import static org.openkilda.functionaltests.helpers.model.Isls.breakIsls
import static org.openkilda.functionaltests.helpers.model.Isls.restoreIsls
import static org.openkilda.functionaltests.helpers.model.SwitchExtended.isDefaultMeter
import static org.openkilda.functionaltests.helpers.model.Switches.synchronizeAndCollectFixedDiscrepancies
import static org.openkilda.functionaltests.helpers.model.Switches.validateAndCollectFoundDiscrepancies
import static org.openkilda.messaging.payload.flow.FlowState.DEGRADED
import static org.openkilda.messaging.payload.flow.FlowState.UP
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
import org.openkilda.functionaltests.helpers.model.IslExtended
import org.openkilda.functionaltests.helpers.model.Path
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.StatusInfo
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.northbound.dto.v2.switches.SwitchPatchDto
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

import javax.inject.Provider

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
        def involvedSwitches = switches.all().findSwitchesInPath(flowPathInfo)
        def flowDBInfo = flow.retrieveDetailsFromDB()
        Wrappers.wait(WAIT_OFFSET) {
            flow.verifyRulesForProtectedFlowOnSwitches(involvedSwitches, flowDBInfo)
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
        def initialSrcValidation =  switchPair.src.validate()
        initialSrcValidation.isAsExpected()

        and: "Cookies are created by flow"
        HashMap<SwitchId, Integer> initialAmountOfFlowRules = [switchPair.src, switchPair.dst].collectEntries { sw ->
            def createdCookies = sw.rulesManager.getNotDefaultRules().cookie
            def amountOfFlowRules = sw.collectFlowRelatedRulesAmount(flow)
            assert createdCookies.size() == amountOfFlowRules
            [(sw.switchId): amountOfFlowRules]
        }

        when: "Update flow: enable protected path(allocateProtectedPath=true)"
        def updatedFlow = flow.update(flow.tap { it.allocateProtectedPath = true })

        then: "Flow has been updated successfully and protected path is enabled"
        updatedFlow.statusDetails
        initialFlowInfo.lastUpdated < updatedFlow.lastUpdated

        def pathAfterUpdating = updatedFlow.retrieveAllEntityPaths()
        !pathAfterUpdating.flowPath.protectedPath.isPathAbsent()
        def protectedPathSwitches = switches.all().findSpecific(pathAfterUpdating.getProtectedPathSwitches())

        def flowInfoFromDb = flow.retrieveDetailsFromDB()
        def protectedFlowCookies = [flowInfoFromDb.protectedForwardPath.cookie.value, flowInfoFromDb.protectedReversePath.cookie.value]

        and: "Rules for main and protected paths are created"
        def involvedSwitches = switches.all().findSwitchesInPath(pathAfterUpdating)
        def flowDBInfo = flow.retrieveDetailsFromDB()
        Wrappers.wait(WAIT_OFFSET) {
            flow.verifyRulesForProtectedFlowOnSwitches(involvedSwitches, flowDBInfo)
            def cookiesAfterEnablingProtectedPath = switchPair.src.rulesManager.getNotDefaultRules().cookie
            // initialAmountOfFlowRules was collected for flow without protected path + one for protected path
            assert cookiesAfterEnablingProtectedPath.size() == initialAmountOfFlowRules.get(switchPair.src.switchId) + 1
        }

        def srcValidation =  switchPair.src.validate()
        srcValidation.isAsExpected()
        srcValidation.rules.proper.cookie.findAll(REQUIRED_COOKIE).size() == initialSrcValidation.rules.proper.cookie.findAll(REQUIRED_COOKIE).size() + 1

        when: "Update flow: disable protected path(allocateProtectedPath=false)"
        updatedFlow = updatedFlow.update(updatedFlow.tap { it.allocateProtectedPath = false})

        then: "Flow has been updated successfully and protected path is disabled"
        !updatedFlow.statusDetails
        !updatedFlow.retrieveAllEntityPaths().flowPath.protectedPath

        and: "Rules for protected path are deleted"
        Wrappers.wait(WAIT_OFFSET) {
            assert flow.retrieveFlowStatus().status == UP
            protectedPathSwitches.each { sw ->
                def rules = sw.rulesManager.getNotDefaultRules()
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
        def involvedSwitches = switches.all().findSwitchesInPath(flowPathInfo)
        def flowDBInfo = flow.retrieveDetailsFromDB()
        Wrappers.wait(WAIT_OFFSET) {
            flow.verifyRulesForProtectedFlowOnSwitches(involvedSwitches, flowDBInfo)
        }

        and: "Number of flow-related cookies has been collected for both source and destination switch"
        HashMap<SwitchId, Integer> initialAmountOfFlowRules = [switchPair.src, switchPair.dst]
                .collectEntries {
                    [(it.switchId): it.validate().rules.proper.cookie.findAll(REQUIRED_COOKIE).size()]
                } as HashMap<SwitchId, Integer>

        and: "No rule discrepancies on every switch of the flow on the main path"
        def mainPathSwitches = switches.all().findSpecific(flowPathInfo.getMainPathSwitches())
        synchronizeAndCollectFixedDiscrepancies(mainPathSwitches).isEmpty()

        and: "No rule discrepancies on every switch of the flow on the protected path)"
        def protectedPathSwitches = switches.all().findSpecific(flowPathInfo.getProtectedPathSwitches())
        synchronizeAndCollectFixedDiscrepancies(protectedPathSwitches).isEmpty()

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
        def srcSwitchCreatedMeterIds = switchPair.src.metersManager.getCreatedMeterIds()
        def dstSwitchCreatedMeterIds = switchPair.dst.metersManager.getCreatedMeterIds()
        def flowLastUpdate = flow.retrieveDetails().lastUpdated

        flow.swapFlowPath()

        then: "Flow paths are swapped"
        flow.waitForBeingInState(UP)
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
        def involvedSwitchesAfterUpdating = switches.all().findSwitchesInPath(flowPathInfoAfterSwapping)
        def flowDBInfoAfterUpdating = flow.retrieveDetailsFromDB()
        Wrappers.wait(WAIT_OFFSET) {
            flow.verifyRulesForProtectedFlowOnSwitches(involvedSwitchesAfterUpdating, flowDBInfoAfterUpdating)
        }

        and: "New meter is created on the src and dst switches"
        def newSrcSwitchCreatedMeterIds = switchPair.src.metersManager.getCreatedMeterIds()
        def newDstSwitchCreatedMeterIds = switchPair.dst.metersManager.getCreatedMeterIds()
        //added || x.empty to allow situation when meters are not available on src or dst
        newSrcSwitchCreatedMeterIds.sort() != srcSwitchCreatedMeterIds.sort() || srcSwitchCreatedMeterIds.empty
        newDstSwitchCreatedMeterIds.sort() != dstSwitchCreatedMeterIds.sort() || dstSwitchCreatedMeterIds.empty


        and: "Old meter is deleted on the src and dst switches"
        [switchPair.src, switchPair.dst].each { sw ->
            def switchValidateInfo = sw.validate()
            if (switchValidateInfo.meters) {
                assert switchValidateInfo.meters.proper.findAll({ dto -> !isDefaultMeter(dto) }).size() == 1
            }
            assert switchValidateInfo.rules.proper.cookie.findAll(REQUIRED_COOKIE).size() == initialAmountOfFlowRules.get(sw.switchId)
            assert switchValidateInfo.isAsExpected()
        }

        and: "Initial transit switches store the correct info about rules and meters"
        def transitSwitches = involvedSwitches.findAll { !(it in switchPair.toList()) }
        Wrappers.wait(WAIT_OFFSET) {
            assert validateAndCollectFoundDiscrepancies(transitSwitches).isEmpty()
        }

        and: "No rule discrepancies on every switch of the flow on the main path"
        def newMainSwitches = switches.all().findSpecific(flowPathInfoAfterSwapping.getMainPathSwitches())
        synchronizeAndCollectFixedDiscrepancies(newMainSwitches).isEmpty()

        and: "No rule discrepancies on every switch of the flow on the protected path)"
        def newProtectedSwitches = switches.all().findSpecific(flowPathInfoAfterSwapping.getProtectedPathSwitches())
        synchronizeAndCollectFixedDiscrepancies(newProtectedSwitches).isEmpty()

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
        def mainPathIsls = isls.all().findInPath(flowPathsInfo.first().getMainPath())
        def protectedPathIsls = isls.all().findInPath(flowPathsInfo.first().getProtectedPath())

        assert mainPathIsls.intersect(protectedPathIsls).isEmpty()

        //check that all other flows use the same paths, so above verification applies to all of them
        flowPathsInfo.each { flowPathInfo ->
            assert isls.all().findInPath(flowPathInfo.getMainPath()) == mainPathIsls
            assert isls.findInPath(flowPathInfo.getProtectedPath()) == protectedPathIsls
        }

        and: "Bandwidth is reserved for protected paths on involved ISLs"
        def protectedIslsInfo = protectedPathIsls.collect { it.getNbDetails() }
        initialIsls.each { initialIsl ->
            protectedIslsInfo.each { currentIsl ->
                if (initialIsl.id == currentIsl.id) {
                    assert initialIsl.availableBandwidth - currentIsl.availableBandwidth == flows.sum { it.maximumBandwidth }
                }
            }
        }

        when: "Break ISL on the main path (bring port down) to init auto swap"
        def islToBreak = mainPathIsls.first()
        islToBreak.breakIt()

        then: "Flows are switched to protected paths"
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            flows.each { flow ->
                assert flow.retrieveFlowStatus().status == UP
                def flowPathInfoAfterRerouting = flow.retrieveAllEntityPaths()

                assert isls.all().findInPath(flowPathInfoAfterRerouting.getMainPath()) == protectedPathIsls
                if (4 <= uniquePathCount) {
                    // protected path is recalculated due to the main path broken ISl
                    def newProtectedPathIsls = isls.all().findInPath(flowPathInfoAfterRerouting.getProtectedPath())
                    assert newProtectedPathIsls != mainPathIsls
                    assert newProtectedPathIsls != protectedPathIsls
                }
            }
        }

        when: "Restore port status"
        islToBreak.restore()

        then: "Path of the flow is not changed"
        flows.each { flow ->
            flow.waitForBeingInState(UP)
            assert isls.all().findInPath(flow.retrieveAllEntityPaths().getMainPath()) == protectedPathIsls
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
        def originalMainPathIsls = isls.all().findInPath(initialFlowPath.getMainPath())
        def originalProtectedPathIsls = isls.all().findInPath(initialFlowPath.getProtectedPath())

        isls.all().excludeIsls(originalMainPathIsls + originalProtectedPathIsls)
                .updateIslsAvailableAndMaxBandwidthInDb(flow.maximumBandwidth - 1)

        and: "Main flow path breaks"
        def mainIsl = originalMainPathIsls.first()
        mainIsl.breakIt()

        then: "Main path swaps to protected, flow becomes degraded, main path UP, protected DOWN"
        Wrappers.wait(WAIT_OFFSET) {
            def newPath = flow.retrieveAllEntityPaths()
            assert isls.all().findInPath(newPath.getMainPath()) == originalProtectedPathIsls
            verifyAll(flow.retrieveDetails()) {
                status == DEGRADED
                statusDetails.mainPath == "Up"
                statusDetails.protectedPath == "Down"
                statusInfo == StatusInfo.OVERLAPPING_PROTECTED_PATH
            }
        }

        when: "ISL gets back up"
        mainIsl.restore()

        then: "Main path remains the same, flow becomes UP, main path UP, protected UP"
        Wrappers.wait(WAIT_OFFSET * 2) {
            def newPath = flow.retrieveAllEntityPaths()
            assert isls.all().findInPath(newPath.getMainPath()) == originalProtectedPathIsls
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
        def originalMainPathIsls = isls.all().findInPath(initialFlowPath.getMainPath())
        def originalProtectedPathIsls = isls.all().findInPath(initialFlowPath.getProtectedPath())

        def otherIsls = isls.all().relatedTo(switchPair)
                .excludeIsls(originalMainPathIsls + originalProtectedPathIsls).getListOfIsls()

        breakIsls(otherIsls)

        and: "Main flow path breaks"
        def mainIsl = originalMainPathIsls.first()
        mainIsl.breakIt()

        then: "Main path swaps to protected, flow becomes degraded, main path UP, protected DOWN"
        Wrappers.wait(WAIT_OFFSET) {
            def newPath = flow.retrieveAllEntityPaths()
            assert isls.all().findInPath(newPath.getMainPath()) == originalProtectedPathIsls
            verifyAll(flow.retrieveDetails()) {
                status == DEGRADED
                statusDetails.mainPath == "Up"
                statusDetails.protectedPath == "Down"
                statusInfo == StatusInfo.OVERLAPPING_PROTECTED_PATH
            }
        }

        when: "ISL on broken path gets back up"
        mainIsl.restore()

        then: "Main path remains the same (no swap), flow becomes UP, main path remains UP, protected path becomes UP"
        Wrappers.wait(WAIT_OFFSET) {
            def newPath = flow.retrieveAllEntityPaths()
            assert isls.all().findInPath(newPath.getMainPath()) == originalProtectedPathIsls
            assert isls.all().findInPath(newPath.getProtectedPath()) == originalMainPathIsls
            verifyAll(flow.retrieveDetails()) {
                status == UP
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

        def initialMainPathIsls = isls.all().findInPath(flowPathInfo.getMainPath())
        def initialProtectedPathIsls = isls.all().findInPath(flowPathInfo.getProtectedPath())
        assert initialMainPathIsls.intersect(initialProtectedPathIsls).isEmpty()

        when: "Make the current and protected path less preferable than alternatives"
        def alternativePaths = switchPair.retrieveAvailablePaths().collect { isls.all().findInPath(it) }
                .findAll { it != initialMainPathIsls && it != initialProtectedPathIsls }
        alternativePaths.each { isls.all().makePathIslsMorePreferable(it, initialMainPathIsls)}
        alternativePaths.each { isls.all().makePathIslsMorePreferable(it, initialProtectedPathIsls) }

        and: "Init intentional reroute"
        def rerouteResponse = flow.reroute()

        then: "Flow is rerouted"
        rerouteResponse.rerouted
        Wrappers.wait(WAIT_OFFSET) {
            flow.retrieveFlowStatus().status == UP
        }

        and: "Path is not changed to protected path"
        def flowPathInfoAfterRerouting = flow.retrieveAllEntityPaths()
        def mainPathIsls = isls.all().findInPath(flowPathInfoAfterRerouting.getMainPath())
        mainPathIsls != initialMainPathIsls
        mainPathIsls != initialProtectedPathIsls
        //protected path is rerouted too, because more preferable path is exist
        def newCurrentProtectedPathIsls = isls.all().findInPath(flowPathInfoAfterRerouting.getProtectedPath())
        newCurrentProtectedPathIsls != initialMainPathIsls
        newCurrentProtectedPathIsls != initialProtectedPathIsls
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == UP }

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

        def initialMainPathIsls = isls.all().findInPath(flowPathInfo.getMainPath())
        def initialProtectedPathIsls = isls.all().findInPath(flowPathInfo.getProtectedPath())
        assert initialMainPathIsls.intersect(initialProtectedPathIsls).isEmpty()

        when: "Make the current and protected path less preferable than alternatives"
        def alternativePaths = switchPair.retrieveAvailablePaths().collect { isls.all().findInPath(it) }
                .findAll { it != initialMainPathIsls && it != initialProtectedPathIsls }
        alternativePaths.each { isls.all().makePathIslsMorePreferable(it, initialMainPathIsls) }
        alternativePaths.each { isls.all().makePathIslsMorePreferable(it, initialProtectedPathIsls) }

        and: "Break ISL on the main path (bring port down) to init auto swap"
        def islToBreak = initialMainPathIsls.first()
        islToBreak.breakIt()

        then: "Flow is switched to protected path"
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            assert flow.retrieveFlowStatus().status == UP
            def newPathInfo = flow.retrieveAllEntityPaths()
            def newMainPath = isls.all().findInPath(newPathInfo.getMainPath())
            assert newMainPath != initialMainPathIsls
            assert newMainPath == initialProtectedPathIsls

            def newCurrentProtectedPath = isls.all().findInPath(newPathInfo.getProtectedPath())
            if (4 <= uniquePathCount) {
                assert newCurrentProtectedPath != initialMainPathIsls
                assert newCurrentProtectedPath != initialProtectedPathIsls
            }
        }

        when: "Restore port status"
        islToBreak.restore()

        then: "Path of the flow is not changed"
        isls.all().findInPath(flow.retrieveAllEntityPaths().getMainPath()) == initialProtectedPathIsls

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
        def swPair = switchPairs.all().neighbouring().random()
        long bandwidth = 100

        and: "Create flow without protected path"
        def flow = flowFactory.getBuilder(swPair)
                .withBandwidth(bandwidth)
                .withProtectedPath(false).build()
                .create()
        def flowPath = flow.retrieveAllEntityPaths()
        assert !flowPath.flowPath.protectedPath

        and: "Update all ISLs which can be used by protected path"
        def flowIsls = isls.all().findInPath(flowPath)
        isls.all().relatedTo(swPair.src).excludeIsls(flowIsls).updateIslsAvailableBandwidth(bandwidth - 1)

        when: "Update flow: enable protected path"
        flow.update(flow.tap { it.allocateProtectedPath = true }, DEGRADED)

        then: "Flow state is changed to DEGRADED"
        verifyAll(flow.retrieveDetails().statusDetails) {
            mainPath == "Up"
            protectedPath == "Down"
        }
    }

    @Tags(ISL_PROPS_DB_RESET)
    def "Able to create a flow with protected path when there is not enough bandwidth and ignoreBandwidth=true"() {
        given: "Two active neighboring switches"
        def swPair = switchPairs.all().neighbouring().random()
        def bandwidth = 100

        and: "Update all ISLs which can be used by protected path"
        def directIsl = isls.all().betweenSwitchPair(swPair).first()
        isls.all().relatedTo(swPair.src).excludeIsls([directIsl]).updateIslsAvailableBandwidth(bandwidth - 1)

        when: "Create flow with protected path"
        def flow = flowFactory.getBuilder(swPair)
                .withBandwidth(bandwidth)
                .withIgnoreBandwidth(true)
                .withProtectedPath(true).build()
                .create()


        then: "Flow is created with protected path"
        !flow.retrieveAllEntityPaths().flowPath.protectedPath.isPathAbsent()

        and: "One transit vlan is created for main and protected paths"
        def flowInfo = flow.retrieveDetailsFromDB()
        flow.getTransitVlansForMainPath(flowInfo).size() == 1
        flow.getTransitVlansForProtectedPath().size() == 1
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

        def initialMainPathIsls = isls.all().findInPath(initialFlowPathInfo.getMainPath())
        def initialProtectedPathIsls = isls.all().findInPath(initialFlowPathInfo.getProtectedPath())
        assert initialMainPathIsls.intersect(initialProtectedPathIsls).isEmpty()

        initialProtectedPathIsls.each {
            def currentAvailableBandwidth = it.getInfo().availableBandwidth
            def initialAvailableBandwidth = it.getInfo(allIsls).availableBandwidth
            assert initialAvailableBandwidth - currentAvailableBandwidth == flow.maximumBandwidth
        }

        when: "Break ISL on the protected path (bring port down) to init the recalculate procedure"
        def islToBreakProtectedPath = initialProtectedPathIsls[0]
        islToBreakProtectedPath.breakIt()

        then: "Protected path is recalculated"
        FlowEntityPath newFlowPathInfo
        List<IslExtended> newProtectedIsls
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            newFlowPathInfo = flow.retrieveAllEntityPaths()
            newProtectedIsls = isls.all().findInPath(newFlowPathInfo.getProtectedPath())
            assert newProtectedIsls != initialProtectedPathIsls
            assert flow.retrieveFlowStatus().status == UP
        }

        and: "Current path is not changed"
        initialMainPathIsls == isls.all().findInPath(newFlowPathInfo.getMainPath())

        and: "Bandwidth is reserved for new protected path on involved ISLs"
        def allLinks
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            allLinks = northbound.getAllLinks()
            newProtectedIsls.each {
                def currentAvailableBandwidth = it.getInfo().availableBandwidth
                def initialAvailableBandwidth = it.getInfo(allIsls).availableBandwidth
                assert initialAvailableBandwidth - currentAvailableBandwidth == flow.maximumBandwidth
            }
        }

        and: "Reservation is deleted on the broken ISL"
        def originInfoBrokenIsl = islToBreakProtectedPath.getInfo(allLinks)
        def currentInfoBrokenIsl = islToBreakProtectedPath.getInfo()
        originInfoBrokenIsl.availableBandwidth == currentInfoBrokenIsl.availableBandwidth

        when: "Restore port status"
        islToBreakProtectedPath.restore()

        then: "Path is not recalculated again"
        isls.all().findInPath(flow.retrieveAllEntityPaths().getProtectedPath()) == newProtectedIsls
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
        def flowIsls = isls.all().findInPath(initialFlowPathInfo)
        def broughtDownIsls = isls.all().relatedTo(switchPair).excludeIsls(flowIsls).getListOfIsls()
        breakIsls(broughtDownIsls)

        when: "Update flow: enable protected path(allocateProtectedPath=true)"
        flow.update(flow.tap { it.allocateProtectedPath = true }, DEGRADED)

        then: "Flow state is changed to DEGRADED as protected path is DOWN"
        verifyAll(flow.retrieveDetails()) {
            status == DEGRADED
            statusDetails.mainPath == "Up"
            statusDetails.protectedPath == "Down"
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
        def mainPathIsls = isls.all().findInPath(flowPathInfo.getMainPath())
        def islsToBreak = isls.all().relatedTo(switchPair).excludeIsls(mainPathIsls).getListOfIsls()
        breakIsls(islsToBreak)

        then: "Flow status is DEGRADED"
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(flow.retrieveDetails()) {
                status == DEGRADED
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
                status == UP
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
                assert flow.retrieveFlowStatus().status == UP
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

        def initialMainPathIsls = isls.all().findInPath(flowPathInfo.getMainPath())
        def initialProtectedPathIsls = isls.all().findInPath(flowPathInfo.getProtectedPath())
        assert initialMainPathIsls.intersect(initialProtectedPathIsls).isEmpty()

        when: "Make the current and protected path less preferable than alternatives"
        def alternativePaths = switchPair.retrieveAvailablePaths().collect { isls.all().findInPath(it) }
                .findAll { it != initialMainPathIsls && it != initialProtectedPathIsls }
        alternativePaths.each { isls.all().makePathIslsMorePreferable(it, initialMainPathIsls) }
        alternativePaths.each { isls.all().makePathIslsMorePreferable(it, initialProtectedPathIsls) }

        and: "Init intentional reroute"
        def rerouteResponse = flow.reroute()

        then: "Flow should be rerouted"
        rerouteResponse.rerouted

        and: "Flow main path should be rerouted to a new path and ignore protected path"
        FlowEntityPath flowPathInfoAfterRerouting
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            flowPathInfoAfterRerouting = flow.retrieveAllEntityPaths()
            def newMainIsls = isls.all().findInPath(flowPathInfoAfterRerouting.getMainPath())
            newMainIsls != initialMainPathIsls && newMainIsls != initialProtectedPathIsls
        }

        and: "Flow protected path shouldn't be rerouted due to lack of non overlapping path"
        isls.all().findInPath(flowPathInfoAfterRerouting.getProtectedPath()) == initialProtectedPathIsls

        and: "Flow and both its paths are UP"
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(flow.retrieveDetails()) {
                status == UP
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
        List<List<IslExtended>> availablePathsIsls = allPathCandidates.collect { isls.all().findInPath(it) }
        def mainPath1Isls = availablePathsIsls.first()
        def mainPath2Isls = availablePathsIsls.find { it != mainPath1Isls }
        def protectedPathIsls = availablePathsIsls.find { it != mainPath1Isls && it != mainPath2Isls }

        def involvedSwP1 = switches.all().findSpecific(mainPath1Isls.collectMany{ it.involvedSwIds })
        def involvedSwP2 =  switches.all().findSpecific(mainPath2Isls.collectMany{ it.involvedSwIds })
        def involvedSwProtected = switches.all().findSpecific(protectedPathIsls.collectMany{ it.involvedSwIds })
        def transitProtectedSw = involvedSwProtected.find { it !in swPair.toList() }

        and: "Src, dst and transit switches belongs to different POPs(src:1, dst:4, tr1/tr2:2, tr3:3)"
        // tr1/tr2 for the main path and tr3 for the protected path
        swPair.src.partialUpdate(new SwitchPatchDto().tap { it.pop = "1" })
        [involvedSwP1.find { !(it in swPair.toList()) }, involvedSwP2.find { !(it in swPair.toList()) }].each { sw ->
            sw.partialUpdate(new SwitchPatchDto().tap { it.pop = "2" })
        }
        swPair.dst.partialUpdate(new SwitchPatchDto().tap { it.pop = "4" })
        transitProtectedSw.partialUpdate(new SwitchPatchDto().tap { it.pop = "3" })

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
        def pathsIncludesTransitProtectedSw = allPathsWithThreeSwitches.findAll {
            transitProtectedSw.switchId in it.getInvolvedSwitches()
        }

        def defaultIslCost = 700
        def totalCostOfMainPath = mainPath1Isls.sum { it.getNbDetails().cost ?: defaultIslCost }
        def amountOfIlslsOnMainPath = mainPath1Isls.size()
        def diversityPopIslCost = 1000
        def diversityGroupPerPopUseCounter = 2
        Integer newIslCost = ((totalCostOfMainPath +
                (amountOfIlslsOnMainPath * diversityPopIslCost * diversityGroupPerPopUseCounter) - 1) /
                protectedPathIsls.size()).toInteger()
        log.debug("newCost: $newIslCost")

        isls.all().collectIslsFromPaths(pathsIncludesTransitProtectedSw).updateCost(newIslCost)

        and: "All alternative paths unavailable (bring ports down on the source switch)"
        List<IslExtended> broughtDownIsls = isls.all().relatedTo(swPair)
                .excludeIsls(mainPath1Isls + mainPath2Isls + protectedPathIsls).getListOfIsls()

        breakIsls(broughtDownIsls)

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
        def realFlowPathInvolvedSwitches = flowPaths.getMainPathSwitches().sort()
        realFlowPathInvolvedSwitches == involvedSwP1.switchId.sort() || realFlowPathInvolvedSwitches == involvedSwP2.switchId.sort()

        and: "Protected path is built through the non preferable path(tr3)"
        flowPaths.getProtectedPathSwitches().sort() == involvedSwProtected.switchId.sort()
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
        def swPair = switchPairs.all().neighbouring().random()
        def bandwidth = 100
        def directIsl = isls.all().betweenSwitchPair(swPair).first()

        and: "Update all ISLs which can be used by protected path"
        isls.all().relatedTo(swPair.src).excludeIsls([directIsl]).updateIslsAvailableBandwidth(bandwidth - 1)

        when: "Create flow with protected path"
        flowFactory.getBuilder(swPair)
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
                .excludeSwitches(switches.all().getListOfSwitches().findAll {it.switchId.toString().endsWith("08")})
                .withAtLeastNNonOverlappingPaths(2).random()

        and: "A flow with protected path"
        def flow = flowFactory.getBuilder(switchPair).withProtectedPath(true).build().create()
        def flowPath = flow.retrieveAllEntityPaths()

        and: "All alternative paths are unavailable (bring ports down on the source switch)"
        def flowMainPathIsl = isls.all().findInPath(flowPath.getMainPath())
        def flowProtectedPathIsl = isls.all().findInPath(flowPath.getProtectedPath())
        def broughtDownIsls = isls.all().relatedTo(switchPair)
                .excludeIsls(flowMainPathIsl + flowProtectedPathIsl).getListOfIsls()
        breakIsls(broughtDownIsls)

        when: "Break ISL on a protected path (bring port down) for changing the flow state to DEGRADED"
        flowProtectedPathIsl.first().breakIt()

        then: "Flow state is changed to DEGRADED"
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == DEGRADED }
        verifyAll(flow.retrieveDetails()) {
            statusDetails.mainPath == "Up"
            statusDetails.protectedPath == "Down"
        }

        when: "Break ISL on the main path (bring port down) for changing the flow state to DOWN"
        flowMainPathIsl.first().breakIt()

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
        flowMainPathIsl.first().restore()

        then: "Flow state is still DEGRADED"
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            assert flow.retrieveFlowStatus().status == DEGRADED
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
        flowProtectedPathIsl.first().restore()

        then: "Flow state is changed to UP"
        //it often fails in scope of the whole spec on the hardware env, that's why '* 1.5' is added
        Wrappers.wait(discoveryInterval * 1.5 + WAIT_OFFSET) {
            assert flow.retrieveFlowStatus().status == UP
        }
    }

    @Tags(LOW_PRIORITY)
    def "Unable to create a single switch flow with protected path"() {
        given: "A switch"
        def sw = switches.all().first()

        when: "Create single switch flow"
        flowFactory.getSingleSwBuilder(sw).withProtectedPath(true).build().sendCreateRequest()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(~/Couldn't setup protected path for one-switch flow/).matches(exc)
    }

    @Tags(LOW_PRIORITY)
    def "Unable to update a single switch flow to enable protected path"() {
        given: "A switch"
        def sw = switches.all().first()

        and: "A flow without protected path"
        def flow = flowFactory.getSingleSwBuilder(sw).build().create()

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
        //one ISL for flow without protected path
        def directIsl = isls.all().betweenSwitchPair(switchPair).first()
        def broughtDownIsls = isls.all().relatedTo(switchPair).excludeIsls([directIsl]).getListOfIsls()
        breakIsls(broughtDownIsls)

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
        def mainPathIsl = isls.all().findInPath(initialFlowPathInfo.getMainPath())
        def protectedIslToBreak = isls.all().findInPath(initialFlowPathInfo.getProtectedPath())

        def broughtDownIsls = isls.all().relatedTo(switchPair.src)
                .excludeIsls([mainPathIsl.first(), protectedIslToBreak.first()]).getListOfIsls()
        breakIsls(broughtDownIsls)

        and: "ISL on a protected path is broken(bring port down) for changing the flow state to DEGRADED"
        protectedIslToBreak.first().breakIt()
        flow.waitForHistoryEvent(REROUTE_FAILED)
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == DEGRADED }

        when: "Make the current paths(main and protected) less preferable than alternative path"
        def availablePathsIsls = switchPair.retrieveAvailablePaths().collect { isls.all().findInPath(it) }
        def alternativeIsl = availablePathsIsls.find { it != mainPathIsl && it != protectedIslToBreak }
        availablePathsIsls.findAll { !it.containsAll(alternativeIsl) }
                .each { isls.all().makePathIslsMorePreferable(alternativeIsl, it) }

        int alternativeIslCost = alternativeIsl.sum { it.getNbDetails().cost }
        assert mainPathIsl.sum { it.getNbDetails().cost } > alternativeIslCost

        and: "Make alternative path available(bring port up on the source switch)"
        restoreIsls(alternativeIsl)

        then: "Reroute has been executed successfully and flow state is changed to UP"
        flow.waitForHistoryEvent(REROUTE)
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == UP }

        and: "Protected path is recalculated only"
        def newFlowPathInfo = flow.retrieveAllEntityPaths()
        isls.all().findInPath(newFlowPathInfo.getMainPath()) == mainPathIsl
        isls.all().findInPath(newFlowPathInfo.getProtectedPath()) == alternativeIsl
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
}
