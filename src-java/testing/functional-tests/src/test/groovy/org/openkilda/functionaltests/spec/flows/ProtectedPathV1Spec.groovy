package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_PROPS_DB_RESET
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.helpers.model.FlowActionType.REROUTE
import static org.openkilda.functionaltests.helpers.model.FlowActionType.REROUTE_FAILED
import static org.openkilda.functionaltests.helpers.model.SwitchExtended.isDefaultMeter
import static org.openkilda.functionaltests.helpers.model.Switches.synchronizeAndCollectFixedDiscrepancies
import static org.openkilda.functionaltests.helpers.model.Switches.validateAndCollectFoundDiscrepancies
import static org.openkilda.model.cookie.CookieBase.CookieType.SERVICE_OR_FLOW_SEGMENT
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID
import static org.openkilda.testing.Constants.PROTECTED_PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowNotCreatedExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotCreatedWithMissingPathExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotUpdatedExpectedError
import org.openkilda.functionaltests.error.flow.FlowPathNotSwappedExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.testing.service.traffexam.TraffExamService

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared

import java.time.Instant
import javax.inject.Provider

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
@Tags([LOW_PRIORITY])
class ProtectedPathV1Spec extends HealthCheckSpecification {

    @Autowired
    @Shared
    FlowFactory flowFactory

    @Autowired
    @Shared
    Provider<TraffExamService> traffExamProvider

    public static final Closure REQUIRED_COOKIE = { Long cookie ->  !new Cookie(cookie).serviceFlag && new Cookie(cookie).type == SERVICE_OR_FLOW_SEGMENT }

    def "Able to create a flow with protected path when maximumBandwidth=#bandwidth, vlan=#vlanId"() {
        given: "Two active not neighboring switches with two diverse paths at least"
        def switchPair = switchPairs.all().nonNeighbouring().withAtLeastNNonOverlappingPaths(2).random()

        when: "Create flow with protected path"
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(true)
                .withBandwidth(bandwidth)
                .withIgnoreBandwidth(bandwidth == 0)
                .withSourceVlan(vlanId).build()
                .createV1()

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
        0         | 0
    }

    def "Able to enable/disable protected path on a flow"() {
        given: "Two active not neighboring switches with two diverse paths at least"
        def switchPair = switchPairs.all().nonNeighbouring().withAtLeastNNonOverlappingPaths(2).random()

        when: "Create flow without protected path"
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(false).build()
                .createV1()

        then: "Flow is created without protected path"
        !flow.retrieveAllEntityPaths().flowPath.protectedPath
        def flowInfo = flow.retrieveDetailsV1()
        !flowInfo.statusDetails

        and: "Source switch passes validation"
        def initialSrcValidation =  switchPair.src.validate()
        initialSrcValidation.isAsExpected()

        and: "Cookies are created by flow"
        HashMap<SwitchId, Integer> initialAmountOfFlowRules = [switchPair.src, switchPair.dst]
                .collectEntries {sw ->
                    def createdCookies = sw.rulesManager.getNotDefaultRules().cookie
                    def amountOfFlowRules = sw.collectFlowRelatedRulesAmount(flow)
                    assert createdCookies.size() == amountOfFlowRules
                    [(sw.switchId): amountOfFlowRules]
                }

        when: "Update flow: enable protected path(allocateProtectedPath=true)"
        def currentLastUpdate = flowInfo.lastUpdated
        flow.updateV1(flow.tap { it.allocateProtectedPath = true })

        then: "Protected path is enabled"
        def flowPathInfoAfterUpdating = flow.retrieveAllEntityPaths()
        !flowPathInfoAfterUpdating.flowPath.protectedPath.isPathAbsent()
        flow.retrieveDetailsV1().statusDetails
        def flowInfoFromDb = flow.retrieveDetailsFromDB()
        def protectedFlowCookies = [flowInfoFromDb.protectedForwardPath.cookie.value, flowInfoFromDb.protectedReversePath.cookie.value]

        Instant.parse(currentLastUpdate) < Instant.parse(flow.retrieveDetailsV1().lastUpdated)

        and: "Rules for main and protected paths are created"
        def involvedSwitches = switches.all().findSwitchesInPath(flowPathInfoAfterUpdating)
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
        def protectedPathSwitches = switches.all().findSpecific(flowPathInfoAfterUpdating.getProtectedPathSwitches())
        flow.updateV1(flow.tap { it.allocateProtectedPath = false })

        then: "Protected path is disabled"
        !flow.retrieveAllEntityPaths().flowPath.protectedPath
        !flow.retrieveDetailsV1().statusDetails

        and: "Source switch passes validation"
        verifyAll(switchPair.src.validate()) {
            it.isAsExpected()
            it.rules.proper.cookie.findAll(REQUIRED_COOKIE).size() == initialSrcValidation.rules.proper.cookie.findAll(REQUIRED_COOKIE).size()

        }
        and: "Rules for protected path are deleted"
        Wrappers.wait(WAIT_OFFSET) {
            assert flow.retrieveFlowStatus().status == FlowState.UP
            protectedPathSwitches.each { sw ->
                def rules = sw.rulesManager.getNotDefaultRules()
                assert rules.findAll { it.cookie in protectedFlowCookies }.isEmpty()            }
        }
    }

    def "Unable to create a single switch flow with protected path"() {
        given: "A switch"
        def sw = switches.all().random()

        when: "Create single switch flow"
        flowFactory.getSingleSwBuilder(sw).withProtectedPath(true).build().sendCreateRequestV1()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(~/Couldn't setup protected path for one-switch flow/).matches(exc)
    }

    def "Unable to update a single switch flow to enable protected path"() {
        given: "A switch"
        def sw = switches.all().random()

        and: "A flow without protected path"
        def flow = flowFactory.getSingleSwRandom(sw)

        when: "Update flow: enable protected path"
        flow.updateV1(flow.tap { it.allocateProtectedPath = true })

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotUpdatedExpectedError(~/Couldn't setup protected path for one-switch flow/).matches(exc)
    }

    @Tags(ISL_PROPS_DB_RESET)
    def "Unable to create a flow with protected path when there is not enough bandwidth"() {
        given: "Two active neighboring switches"
        def swPair = switchPairs.all().neighbouring().random()
        def isls = topology.getRelatedIsls(swPair.src.switchId)

        and: "Update all ISLs which can be used by protected path"
        def bandwidth = 100
        islHelper.setAvailableBandwidth(isls[1..-1], 90)

        when: "Create flow with protected path"
       flowFactory.getBuilder(swPair)
                .withBandwidth(bandwidth)
                .withProtectedPath(true).build()
                .sendCreateRequestV1()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedWithMissingPathExpectedError(
                ~/Not enough bandwidth or no path found. Couldn't find non overlapping protected path/).matches(exc)
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Unable to create #flowDescription flow with protected path if all alternative paths are unavailable"() {
        given: "Two active neighboring switches without alt paths"
        def switchPair = switchPairs.all().neighbouring().random()
        def broughtDownIsls = topology.getRelatedIsls(switchPair.src.switchId)[1..-1]
        islHelper.breakIsls(broughtDownIsls)

        when: "Try to create a new flow with protected path"
        flowFactory.getBuilder(switchPair)
                .withProtectedPath(true)
                .withBandwidth(bandwidth)
                .withIgnoreBandwidth(bandwidth == 0).build()
                .sendCreateRequestV1()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedWithMissingPathExpectedError(
                ~/Not enough bandwidth or no path found. Couldn't find non overlapping protected path/).matches(exc)

        where:
        flowDescription | bandwidth
        "a metered"     | 1000
        "an unmetered"  | 0
    }

    def "Able to swap main and protected paths manually"() {
        given: "A simple flow"
        def switchPair = switchPairs.all()
                .nonNeighbouring()
                .withTraffgensOnBothEnds()
                .withPathHavingAtLeastNSwitches(4)
                .random()
        def flow = flowFactory.getBuilder(switchPair, true)
                .withProtectedPath(false).build()
                .createV1()

        assert !flow.retrieveAllEntityPaths().flowPath.protectedPath

        and: "Number of flow-related cookies has been collected for both source and destination switch"
        HashMap<SwitchId, Integer> initialAmountOfFlowRules = [switchPair.src, switchPair.dst]
                .collectEntries {
                    [(it.switchId): it.validate().rules.proper.cookie.findAll(REQUIRED_COOKIE).size()]
                } as HashMap<SwitchId, Integer>

        when: "Update flow: enable protected path(allocateProtectedPath=true)"
        flow.updateV1(flow.tap { it.allocateProtectedPath = true })

        then: "Protected path is enabled"
        def flowPathInfo = flow.retrieveAllEntityPaths()
        !flowPathInfo.flowPath.protectedPath.isPathAbsent()

        def initialMainPath = flowPathInfo.getPathNodes(Direction.FORWARD, false)
        def initialProtectedPath = flowPathInfo.getPathNodes(Direction.FORWARD, true)
        initialMainPath != initialProtectedPath

        and: "Rules for main and protected paths are created"
        def involvedSwitches = switches.all().findSwitchesInPath(flowPathInfo)
        def flowDBInfo = flow.retrieveDetailsFromDB()
        Wrappers.wait(WAIT_OFFSET) {
            flow.verifyRulesForProtectedFlowOnSwitches(involvedSwitches, flowDBInfo)
        }

        and: "Source and destination switches pass validation"
        [switchPair.src, switchPair.dst].each { sw ->
            def switchValidateInfo = sw.validate()
            // + 1 for protected path
            assert switchValidateInfo.rules.proper.cookie.findAll(REQUIRED_COOKIE).size() == initialAmountOfFlowRules.get(sw.switchId) + 1
            assert switchValidateInfo.isAsExpected()
        }

        and: "No rule discrepancies on every switch of the flow on the main path"
        def mainSwitches = switches.all().findSpecific(flowPathInfo.getMainPathSwitches())
        synchronizeAndCollectFixedDiscrepancies(mainSwitches).isEmpty()

        and: "No rule discrepancies on every switch of the flow on the protected path)"
        def protectedSwitches = switches.all().findSpecific(flowPathInfo.getProtectedPathSwitches())
        synchronizeAndCollectFixedDiscrepancies(protectedSwitches).isEmpty()

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
        def currentLastUpdate = flow.retrieveDetailsV1().lastUpdated
        flow.swapFlowPath()

        then: "Flow paths are swapped"
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.UP }
        def flowPathInfoAfterSwapping = flow.retrieveAllEntityPaths()
        def newMainPath = flowPathInfoAfterSwapping.getPathNodes(Direction.FORWARD, false)
        def newProtectedPath = flowPathInfoAfterSwapping.getPathNodes(Direction.FORWARD, true)
        verifyAll {
            newMainPath == initialProtectedPath
            newProtectedPath == initialMainPath
        }

        Instant.parse(currentLastUpdate) < Instant.parse(flow.retrieveDetailsV1().lastUpdated)

        and: "New meter is created on the src and dst switches"
        def newSrcSwitchCreatedMeterIds = switchPair.src.metersManager.getCreatedMeterIds()
        def newDstSwitchCreatedMeterIds = switchPair.dst.metersManager.getCreatedMeterIds()
        //added || x.empty to allow situation when meters are not available on src or dst
        newSrcSwitchCreatedMeterIds.sort() != srcSwitchCreatedMeterIds.sort() || srcSwitchCreatedMeterIds.empty
        newDstSwitchCreatedMeterIds.sort() != dstSwitchCreatedMeterIds.sort() || dstSwitchCreatedMeterIds.empty

        and: "No rule discrepancies when doing flow validation"
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "Rules are updated"
        def flowDBInfoAfterUpdating = flow.retrieveDetailsFromDB()
        def involvedSwitchesAfterUpdating = switches.all().findSwitchesInPath(flowPathInfoAfterSwapping)
        Wrappers.wait(WAIT_OFFSET) {
            flow.verifyRulesForProtectedFlowOnSwitches(involvedSwitchesAfterUpdating, flowDBInfoAfterUpdating)
        }

        and: "Old meter is deleted on the src and dst switches"
        [switchPair.src, switchPair.dst].each { sw ->
            def switchValidateInfo = sw.validate()
            if (switchValidateInfo.meters) {
                assert switchValidateInfo.meters.proper.findAll({ dto -> !isDefaultMeter(dto) }).size() == 1
            }
            assert switchValidateInfo.rules.proper.cookie.findAll(REQUIRED_COOKIE).size() == initialAmountOfFlowRules.get(sw.switchId) + 1
            assert switchValidateInfo.isAsExpected()
        }

        and: "Transit switches store the correct info about rules and meters"
        def involvedTransitSwitches = (mainSwitches + protectedSwitches).unique()
                .findAll { !(it in switchPair.toList())}
        Wrappers.wait(WAIT_OFFSET) {
            assert validateAndCollectFoundDiscrepancies(involvedTransitSwitches).isEmpty()
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

    def "Unable to perform the 'swap' request for a flow without protected path"() {
        given: "Two active neighboring switches"
        def swPair = switchPairs.all().neighbouring().random()

        and: "A flow without protected path"
        def flow = flowFactory.getBuilder(swPair)
                .withProtectedPath(false).build()
                .createV1()

        assert !flow.retrieveAllEntityPaths().flowPath.protectedPath

        when: "Try to swap paths for flow that doesn't have protected path"
        flow.swapFlowPath()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowPathNotSwappedExpectedError(
                ~/Could not swap paths: Flow $flow.flowId doesn't have protected path/).matches(exc)
    }

    def "Unable to swap paths for a non-existent flow"() {
        when: "Try to swap path on a non-existent flow"
        northbound.swapFlowPath(NON_EXISTENT_FLOW_ID)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowPathNotSwappedExpectedError(HttpStatus.NOT_FOUND,
                ~/Could not swap paths: Flow $NON_EXISTENT_FLOW_ID not found/).matches(exc)
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Unable to swap paths for an inactive flow"() {
        given: "Two active neighboring switches with two not overlapping paths at least"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNNonOverlappingPaths(2).random()

        and: "A flow with protected path"
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(true).build()
                .createV1()

        and: "All alternative paths are unavailable (bring ports down on the source switch)"
        def flowPathIsl = flow.retrieveAllEntityPaths().getMainPathInvolvedIsls()
        def broughtDownIsls = topology.getRelatedIsls(switchPair.src.switchId) - flowPathIsl
        islHelper.breakIsls(broughtDownIsls)

        when: "Break ISL on a protected path (bring port down) for changing the flow state to DEGRADED"
        def flowPathInfo = flow.retrieveAllEntityPaths()
        def protectedIsls = flowPathInfo.getProtectedPathInvolvedIsls()
        def currentIsls = flowPathInfo.getMainPathInvolvedIsls()
        islHelper.breakIsl(protectedIsls[0])

        then: "Flow state is changed to DEGRADED"
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.DEGRADED }
        verifyAll(flow.retrieveDetailsV1().statusDetails) {
            mainPath == "Up"
            protectedPath == "Down"
        }

        when: "Break ISL on the main path (bring port down) for changing the flow state to DOWN"
        islHelper.breakIsl(currentIsls[0])

        then: "Flow state is changed to DOWN"
        Wrappers.wait(WAIT_OFFSET) {
            assert flow.retrieveFlowStatus().status == FlowState.DOWN
            assert flow.retrieveFlowHistory().getEntriesByType(REROUTE).find {
                it.taskId =~ (/.+ : retry #1 ignore_bw true/)
            }?.payload?.last()?.action == REROUTE_FAILED.payloadLastAction
        }
        verifyAll(flow.retrieveDetailsV1().statusDetails) {
            mainPath == "Down"
            protectedPath == "Down"
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
            verifyAll(flow.retrieveDetailsV1().statusDetails) {
                mainPath == "Up"
                protectedPath == "Down"
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
        //it often fails in scope of the whole spec on the hardware env, that's why '* 2' is added
        Wrappers.wait(discoveryInterval * 2 + WAIT_OFFSET) {
            assert flow.retrieveFlowStatus().status == FlowState.UP
        }
    }

    def "System doesn't allow to enable the pinned flag on a protected flow"() {
        given: "A protected flow"
        def switchPair = switchPairs.all().neighbouring().withAtLeastNPaths(2).random()
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(true).build()
                .createV1()

        when: "Update flow: enable the pinned flag(pinned=true)"
        flow.updateV1(flow.tap { it.pinned = true })

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotUpdatedExpectedError(~/Flow flags are not valid, unable to process pinned protected flow/).matches(exc)
    }
}
