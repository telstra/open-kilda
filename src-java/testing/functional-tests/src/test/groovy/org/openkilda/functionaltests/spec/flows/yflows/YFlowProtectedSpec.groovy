package org.openkilda.functionaltests.spec.flows.yflows

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.helpers.model.Switches.synchronizeAndCollectFixedDiscrepancies

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.helpers.factory.YFlowFactory
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchPayload

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

@Slf4j
@Narrative("Verify reroute operations on y-flows.")
class YFlowProtectedSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    YFlowFactory yFlowFactory

    def "Able to enable/disable protected path on a flow"() {
        given: "A simple y-flow"
        def swT = switchTriplets.all().withAtLeastNNonOverlappingPaths(4)
                .findSwitchTripletForYFlowWithProtectedPaths(true)
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")

        def yFlow = yFlowFactory.getRandom(swT)
        yFlow.retrieveAllEntityPaths().subFlowPaths.each { assert !it.protectedPath }

        when: "Update flow: enable protected path(allocateProtectedPath=true)"
        def updateRequest = yFlow.convertToUpdate().tap { it.allocateProtectedPath = true }
        def yFlowAfterUpdate = yFlow.update(updateRequest)

        then: "Protected path is really enabled on the YFlow"
        yFlowAfterUpdate.allocateProtectedPath

        and: "Protected path is really enabled on the sub flows"
        yFlow.subFlows.each {
            assert northbound.getFlow(it.flowId).allocateProtectedPath
        }

        and: "Protected path is really created"
        def paths = yFlow.retrieveAllEntityPaths()
        paths.subFlowPaths.each {
            assert !it.protectedPath?.isPathAbsent()
            assert it.getCommonIslsWithProtected().isEmpty()
        }

        and: "YFlow and related sub-flows are valid"
        yFlow.validate().asExpected
        yFlow.subFlows.each {
            assert northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
        }

        and: "All involved switches pass switch validation"
        def involvedSwitches = switches.all().findSwitchesInPath(paths)
        synchronizeAndCollectFixedDiscrepancies(involvedSwitches).isEmpty()

        when: "Disable protected path via partial update"
        def patchRequest = YFlowPatchPayload.builder().allocateProtectedPath(false).build()
        def yFlowAfterPartialUpdate = yFlow.partialUpdate(patchRequest)

        then: "Protected path is really disabled for YFlow/sub-flows"
        !yFlowAfterPartialUpdate.allocateProtectedPath
        yFlow.subFlows.each {
            assert !northbound.getFlow(it.flowId).allocateProtectedPath
        }

        and: "Protected path is deleted"
        yFlow.retrieveAllEntityPaths().subFlowPaths.each { assert it.protectedPath?.isPathAbsent() }

        and: "YFlow and related sub-flows are valid"
        yFlow.validate().asExpected
        yFlow.subFlows.each {
            assert northbound.validateFlow(it.flowId).each { direction -> assert direction.asExpected }
        }

        and: "All involved switches passes switch validation"
        synchronizeAndCollectFixedDiscrepancies(involvedSwitches).isEmpty()
    }
}
