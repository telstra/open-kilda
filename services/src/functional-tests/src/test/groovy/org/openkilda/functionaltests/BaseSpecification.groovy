package org.openkilda.functionaltests

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.extension.fixture.SetupOnce
import org.openkilda.functionaltests.extension.healthcheck.HealthCheck
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.ContextConfiguration

@ContextConfiguration(locations = ["classpath:/spring-context.xml"])
class BaseSpecification extends SpringSpecification implements SetupOnce {
    @Value('${spring.profiles.active}')
    String profile

    @Autowired
    FlowHelper flowHelper
    @Autowired
    TopologyDefinition topologyDefinition
    @Autowired
    NorthboundService northbound

    /**
     * Use this instead of setupSpec in order to have access to Spring Context and do actions BeforeClass.
     * Can be overridden by inheritor specs.
     * @see {@link org.openkilda.functionaltests.extension.fixture.SetupOnceExtension}
     */
    def setupOnce() {
    }

    def setup() {
        //setup with empty body in order to trigger a SETUP invocation, which is intercepted in several extensions
        //this can have implementation if required
    }

    def requireProfiles(String[] profiles) {
        assumeTrue("This test required one of these profiles: ${profiles.join(',')}; " +
                "but current active profile is '${this.profile}'", this.profile in profiles)
    }
    
    @HealthCheck
    def "Create casual flow to ensure Kilda's up state"() {
        when: "Create a flow"
        def flow = flowHelper.randomFlow(topologyDefinition.activeSwitches[0], topologyDefinition.activeSwitches[1])
        northbound.addFlow(flow)

        then: "Flow switches to UP state in a reasonable amount of time"
        Wrappers.wait(15) { northbound.getFlowStatus(flow.id).status == FlowState.UP }

        and: "remove the flow"
        northbound.deleteFlow(flow.id)
        Wrappers.wait(WAIT_OFFSET) { northbound.getAllLinks().every { it.availableBandwidth == it.speed } }
    }
}
