package org.openkilda.functionaltests

import static org.junit.Assume.assumeTrue
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.extension.fixture.SetupOnce
import org.openkilda.functionaltests.extension.healthcheck.HealthCheck
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.floodlight.FloodlightService
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.otsdb.OtsdbQueryService
import org.openkilda.testing.tools.IslUtils

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.ContextConfiguration

@ContextConfiguration(locations = ["classpath:/spring-context.xml"])
class BaseSpecification extends SpringSpecification implements SetupOnce {

    @Autowired
    TopologyDefinition topology

    @Autowired
    NorthboundService northbound

    @Autowired
    FloodlightService floodlight

    @Autowired
    LockKeeperService lockKeeper

    @Autowired
    Database database

    @Autowired
    OtsdbQueryService otsdb

    @Autowired
    IslUtils islUtils

    @Autowired
    FlowHelper flowHelper

    @Autowired
    PathHelper pathHelper

    @Value('${spring.profiles.active}')
    String profile

    @Value('${reroute.delay}')
    int rerouteDelay

    @Value('${discovery.interval}')
    int discoveryInterval

    @Value('${discovery.timeout}')
    int discoveryTimeout

    @Value('${antiflap.cooldown}')
    int antiflapCooldown

    @Value('${antiflap.min}')
    int antiflapMin

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

    @HealthCheck
    def "Kilda is UP and topology is clean"() {
        expect: "Kilda's health check request is successful"
        northbound.getHealthCheck().components["kafka"] == "operational"

        and: "All switches and links are active. No flows and link props are present"
        def links = northbound.getAllLinks()
        verifyAll {
            Wrappers.wait(WAIT_OFFSET) {
                assert northbound.activeSwitches.size() == topology.activeSwitches.size()
            }
            links.findAll { it.state == IslChangeType.FAILED }.empty
            def topoLinks = topology.islsForActiveSwitches.collectMany {
                [islUtils.getIslInfo(links, it).get(), islUtils.getIslInfo(links, it.reversed).get()]
            }
            def missingLinks = links - topoLinks
            missingLinks.empty
            northbound.allFlows.empty
            northbound.allLinkProps.empty
        }

        and: "Link bandwidths and speeds are equal. No excess and missing switch rules are present"
        verifyAll {
            links.findAll { it.availableBandwidth != it.speed }.empty
            topology.activeSwitches.findAll {
                def rules = northbound.validateSwitchRules(it.dpId)
                !rules.excessRules.empty || !rules.missingRules.empty
            }.empty

            topology.activeSwitches.findAll {
                !it.virtual && it.ofVersion != "OF_12" && !floodlight.getMeters(it.dpId).findAll {
                    it.key > MAX_SYSTEM_RULE_METER_ID
                }.isEmpty()
            }.empty
        }
    }

    def requireProfiles(String[] profiles) {
        assumeTrue("This test requires one of these profiles: '${profiles.join("', '")}'; " +
                "but current active profile is '${this.profile}'", this.profile in profiles)
    }

    void verifySwitchRules(SwitchId switchId) {
        def rules = northbound.validateSwitchRules(switchId)
        assert rules.excessRules.empty
        assert rules.missingRules.empty
    }
}
