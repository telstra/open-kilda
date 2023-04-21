package org.openkilda.functionaltests.spec.flows.haflows

import groovy.util.logging.Slf4j
import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.exception.ExpectedHttpClientErrorException
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.openkilda.functionaltests.helpers.HaPathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.haflows.HaFlowCreatePayload
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

import static groovyx.gpars.GParsPool.withPool
import static org.assertj.core.api.AssertionsForClassTypes.assertThat
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.springframework.http.HttpStatus.NOT_FOUND

@Slf4j
@Narrative("Verify paths response for ha-flows.")
class HaFlowPathsSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    HaFlowHelper haFlowHelper
    @Autowired
    @Shared
    HaPathHelper haPathHelper

    @Tidy
    @Tags(LOW_PRIORITY)
    def "Meaningful error is returned when requested paths for non-existing HA flow"() {
        when: "Request paths for non-existing HA flow"
        def flowId = "non-existing flow"
        northboundV2.getHaFlowPaths(flowId)

        then: "Meaningful error is returned"
        def actualException = thrown(HttpClientErrorException)
        new ExpectedHttpClientErrorException(NOT_FOUND, ~/HA-flow ${flowId} not found\./).equals(actualException)

    }

}
