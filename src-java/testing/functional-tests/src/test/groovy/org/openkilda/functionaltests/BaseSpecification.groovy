package org.openkilda.functionaltests

import static org.junit.Assume.assumeTrue

import org.openkilda.functionaltests.extension.fixture.SetupOnce
import org.openkilda.functionaltests.extension.spring.PrepareSpringContextDummy
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.FlowHelperV2
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.PortAntiflapHelper
import org.openkilda.functionaltests.helpers.StatsHelper
import org.openkilda.functionaltests.helpers.SwitchHelper
import org.openkilda.functionaltests.helpers.TopologyHelper
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.floodlight.FloodlightsHelper
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.service.otsdb.OtsdbQueryService
import org.openkilda.testing.tools.IslUtils

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

@ContextConfiguration(locations = ["classpath:/spring-context.xml"])
class BaseSpecification extends Specification implements SetupOnce {

    @Autowired
    TopologyDefinition topology
    @Autowired
    NorthboundService northbound
    @Autowired
    FloodlightsHelper flHelper
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
    TopologyHelper topologyHelper
    @Autowired
    PathHelper pathHelper
    @Autowired
    SwitchHelper switchHelper
    @Autowired
    PortAntiflapHelper antiflap
    @Autowired
    NorthboundServiceV2 northboundV2
    @Autowired
    FlowHelperV2 flowHelperV2
    @Autowired
    StatsHelper statsHelper

    @Value('${spring.profiles.active}')
    String profile
    @Value('${reroute.delay}')
    int rerouteDelay
    @Value('${discovery.generic.interval}')
    int discoveryInterval
    @Value('${discovery.timeout}')
    int discoveryTimeout
    @Value('${discovery.exhausted.interval}')
    int discoveryExhaustedInterval
    @Value('${discovery.auxiliary.interval}')
    int discoveryAuxiliaryInterval
    @Value('${antiflap.cooldown}')
    int antiflapCooldown
    @Value('${antiflap.min}')
    int antiflapMin
    @Value('${use.multitable}')
    boolean useMultitable

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


    /**
     * This is a dummy test which is ran as the first ever test to init Spring context.
     * @see org.openkilda.functionaltests.extension.spring.SpringContextExtension
     */
    @PrepareSpringContextDummy
    def "Spring context is set UP"() {
        expect: true
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
