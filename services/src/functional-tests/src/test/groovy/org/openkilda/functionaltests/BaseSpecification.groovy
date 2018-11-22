package org.openkilda.functionaltests

import static org.junit.Assume.assumeTrue

import org.openkilda.functionaltests.extension.fixture.SetupOnce
import org.openkilda.functionaltests.extension.healthcheck.HealthCheck
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.model.SwitchId
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

    void verifySwitchRules(SwitchId switchId) {
        def rules = northbound.validateSwitchRules(switchId)
        assert rules.excessRules.empty
        assert rules.missingRules.empty
    }

    @HealthCheck
    def "Kilda is UP and topology is clean"() {
        expect: "Kilda's health check request is successful"
        northbound.getHealthCheck().components["kafka"] == "operational"

        and: "All switches and links are active. No flows and link props are present"
        def links = northbound.getAllLinks()
        verifyAll {
            northbound.activeSwitches.size() == topologyDefinition.activeSwitches.size()
            links.findAll { it.state == IslChangeType.FAILED }.empty
            links.findAll {
                it.state == IslChangeType.DISCOVERED
            }.size() == topologyDefinition.islsForActiveSwitches.size() * 2
            northbound.allFlows.empty
            northbound.allLinkProps.empty
        }

        and: "Link bandwidths and speeds are equal. No excess and missing switch rules are present"
        verifyAll {
            links.findAll { it.availableBandwidth != it.speed }.empty
            topologyDefinition.activeSwitches.findAll {
                def rules = northbound.validateSwitchRules(it.dpId)
                !rules.excessRules.empty || !rules.missingRules.empty
            }.empty

            def nonVirtualSwitches = northbound.getActiveSwitches()
                    .findAll { !it.description.contains("Nicira, Inc") }
                    .collect { sw -> topologyDefinition.getSwitches().find { it.dpId == sw.switchId }}

            nonVirtualSwitches.findAll {
                it.ofVersion != "OF_12" && !floodlight.getMeters(it.dpId).isEmpty()
            }.empty
        }
    }
}
