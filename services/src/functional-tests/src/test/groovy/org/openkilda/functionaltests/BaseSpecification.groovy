package org.openkilda.functionaltests

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.extension.fixture.SetupOnce
import org.openkilda.functionaltests.extension.healthcheck.HealthCheck
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.floodlight.FloodlightService
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
    @Autowired
    FloodlightService floodlight

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
    def "Given hardware topology is suitable for running the test suite"() {
        requireProfiles("hardware")

        expect: "Topology and overall kilda state are suitable for running further test suite"
        def links = northbound.getAllLinks()
        verifyAll {
            northbound.activeSwitches.size() == topologyDefinition.activeSwitches.size()
            northbound.allFlows.empty
            northbound.allLinkProps.empty
            links.findAll {
                it.state == IslChangeType.DISCOVERED
            }.size() == topologyDefinition.islsForActiveSwitches.size() * 2
            links.findAll { it.state == IslChangeType.FAILED }.empty
        }

        and: "More detailed inspection also reports no issues"
        verifyAll {
            links.findAll { it.availableBandwidth != it.speed }.empty
            topologyDefinition.activeSwitches.findAll {
                def rules = northbound.validateSwitchRules(it.dpId)
                !rules.excessRules.empty || !rules.missingRules.empty
            }.empty
            topologyDefinition.activeSwitches.findAll {
                it.ofVersion != "OF_12" && !floodlight.getMeters(it.dpId).isEmpty()
            }.empty
        }
    }

    @HealthCheck
    def "Create casual flow to ensure Kilda's up state"() {
        requireProfiles("virtual")

        when: "Create a flow"
        def flow = flowHelper.randomFlow(topologyDefinition.activeSwitches[0], topologyDefinition.activeSwitches[1])
        northbound.addFlow(flow)

        then: "The flow switches to 'Up' state in a reasonable amount of time"
        Wrappers.wait(WAIT_OFFSET * 3) { northbound.getFlowStatus(flow.id).status == FlowState.UP }

        and: "Remove the flow"
        northbound.deleteFlow(flow.id)
        Wrappers.wait(WAIT_OFFSET) {
            northbound.getAllLinks().every { it.availableBandwidth == it.speed }
        }
    }
}
