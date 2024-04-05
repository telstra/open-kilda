package org.openkilda.functionaltests.spec.flows.yflows

import static org.junit.jupiter.api.Assumptions.assumeTrue

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.northbound.dto.v2.yflows.YFlow
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchPayload
import org.openkilda.testing.service.traffexam.TraffExamService

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

import jakarta.inject.Provider

@Slf4j
@Narrative("Verify reroute operations on y-flows.")
class YFlowProtectedSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    YFlowHelper yFlowHelper
    @Autowired
    @Shared
    Provider<TraffExamService> traffExamProvider

    def "Able to enable/disable protected path on a flow"() {
        given: "A simple y-flow"
        def swT = topologyHelper.switchTriplets.find {
            def ep1paths = it.pathsEp1.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }
            def ep2paths = it.pathsEp2.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }
            def yPoints = topologyHelper.findPotentialYPoints(it)
            //se == yp
            yPoints.size() == 1 && yPoints[0] == it.shared && yPoints[0] != it.ep1 && yPoints[0] != it.ep2 &&
                    it.ep1 != it.ep2 && ep1paths.size() >= 2 && ep2paths.size() >= 2
        }
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")
        def yFlowRequest = yFlowHelper.randomYFlow(swT)
        YFlow yFlow = yFlowHelper.addYFlow(yFlowRequest)
        assert !northboundV2.getYFlowPaths(yFlow.YFlowId).sharedPath.protectedPath

        when: "Update flow: enable protected path(allocateProtectedPath=true)"
        def update = yFlowHelper.convertToUpdate(yFlow.tap { it.allocateProtectedPath = true })
        def updateResponse = yFlowHelper.updateYFlow(yFlow.YFlowId, update)

        then: "Update response contains enabled protected path"
        updateResponse.allocateProtectedPath

        and: "Protected path is really enabled on the YFlow"
        northboundV2.getYFlow(yFlow.YFlowId).allocateProtectedPath

        and: "Protected path is really enabled on the sub flows"
        yFlow.subFlows.each {
            assert northbound.getFlow(it.flowId).allocateProtectedPath
        }

        and: "Protected path is really created"
        def paths = northboundV2.getYFlowPaths(yFlow.YFlowId)
        paths.subFlowPaths.each {
            assert it.protectedPath
            assert it.forward != it.protectedPath.forward
        }

        and: "YFlow and related sub-flows are valid"
        northboundV2.validateYFlow(yFlow.YFlowId).asExpected
        yFlow.subFlows.each {
            assert northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
        }

        and: "All involved switches pass switch validation"
        def involvedSwitches = pathHelper.getInvolvedYSwitches(northboundV2.getYFlowPaths(yFlow.YFlowId))
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitches*.getDpId()).isEmpty()

        when: "Disable protected path via partial update"
        def patch = YFlowPatchPayload.builder().allocateProtectedPath(false).build()
        def patchResponse = yFlowHelper.partialUpdateYFlow(yFlow.YFlowId, patch)

        then: "Partial update response contains disabled protected path"
        !patchResponse.allocateProtectedPath

        then: "Protected path is really disabled for YFlow/sub-flows"
        !northboundV2.getYFlow(yFlow.YFlowId).allocateProtectedPath
        yFlow.subFlows.each {
            assert !northbound.getFlow(it.flowId).allocateProtectedPath
        }

        and: "Protected path is deleted"
        !northboundV2.getYFlowPaths(yFlow.YFlowId).sharedPath.protectedPath

        and: "YFlow and related sub-flows are valid"
        northboundV2.validateYFlow(yFlow.YFlowId).asExpected
        yFlow.subFlows.each {
            assert northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
        }

        and: "All involved switches passes switch validation"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitches*.getDpId()).isEmpty()

        cleanup:
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
    }
}
