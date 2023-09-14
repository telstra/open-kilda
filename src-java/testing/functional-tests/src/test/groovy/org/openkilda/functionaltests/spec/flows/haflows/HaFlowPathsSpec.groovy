package org.openkilda.functionaltests.spec.flows.haflows

import org.openkilda.functionaltests.helpers.model.HaFlowExtended

import groovy.util.logging.Slf4j
import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.haflow.HaFlowNotFoundExpectedError
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY

@Slf4j
@Narrative("Verify paths response for ha-flows.")
class HaFlowPathsSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    HaFlowHelper haFlowHelper

    @Tidy
    @Tags(LOW_PRIORITY)
    def "Meaningful error is returned when requested paths for non-existing HA flow"() {
        when: "Request paths for non-existing HA flow"
        def flowId = "non-existing flow"
        northboundV2.getHaFlowPaths(flowId)

        then: "Meaningful error is returned"
        def actualException = thrown(HttpClientErrorException)
        new HaFlowNotFoundExpectedError(~/HA-flow ${flowId} not found\./).matches(actualException)
    }

    @Tidy
    def "HA flow main path is not overlapped with protected path"() {
        given: "An HA-flow with protected path"
        def swT = topologyHelper.findSwitchTripletForHaFlowWithProtectedPaths()
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")
        HaFlowExtended haFlow = HaFlowExtended.build(swT, northboundV2, topology).withProtectedPath(true).create()

        when: "Request path for HA flow with protected path"
        def haFlowPaths = haFlow.getAllEntityPaths()

        then: "HA flow main and protected paths do not have common ISLs"
        assert haFlowPaths.subFlowPaths.protectedPath.size() == 2
        haFlowPaths.subFlowPaths.each {subFlowPath ->
            assert subFlowPath.getCommonIslsWithProtected().isEmpty()
        }

        cleanup:
        //resources should be cleaned up automatically after each test execution (using old logic for now)
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
    }
}
