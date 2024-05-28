package org.openkilda.functionaltests.spec.flows.haflows

import static org.openkilda.functionaltests.extension.tags.Tag.HA_FLOW

import org.openkilda.functionaltests.helpers.HaFlowFactory
import org.openkilda.functionaltests.helpers.model.HaFlowExtended

import groovy.util.logging.Slf4j
import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.haflow.HaFlowNotFoundExpectedError
import org.openkilda.functionaltests.extension.tags.Tags

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY

import spock.lang.Shared

@Slf4j
@Narrative("Verify paths response for ha-flows.")
@Tags([HA_FLOW])
class HaFlowPathsSpec extends HealthCheckSpecification {

    @Shared
    @Autowired
    HaFlowFactory haFlowFactory

    @Tags(LOW_PRIORITY)
    def "Meaningful error is returned when requested paths for non-existing HA flow"() {
        when: "Request paths for non-existing HA flow"
        def flowId = "non-existing flow"
        northboundV2.getHaFlowPaths(flowId)

        then: "Meaningful error is returned"
        def actualException = thrown(HttpClientErrorException)
        new HaFlowNotFoundExpectedError(~/HA-flow ${flowId} not found\./).matches(actualException)
    }

    def "HA flow main path is not overlapped with protected path"() {
        given: "An HA-flow with protected path"
        def swT = topologyHelper.findSwitchTripletForHaFlowWithProtectedPaths()
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")
        HaFlowExtended haFlow = haFlowFactory.getBuilder(swT).withProtectedPath(true)
                .build().create()

        when: "Request path for HA flow with protected path"
        def haFlowPaths = haFlow.retrievedAllEntityPaths()

        then: "HA flow main and protected paths do not have common ISLs"
        assert haFlowPaths.subFlowPaths.protectedPath.size() == 2
        haFlowPaths.subFlowPaths.each {subFlowPath ->
            assert subFlowPath.getCommonIslsWithProtected().isEmpty()
        }
    }
}
