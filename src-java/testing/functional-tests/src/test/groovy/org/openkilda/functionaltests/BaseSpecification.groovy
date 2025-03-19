package org.openkilda.functionaltests

import org.openkilda.functionaltests.helpers.IslHelper
import org.openkilda.functionaltests.helpers.model.ASwitchFlows
import org.openkilda.functionaltests.helpers.model.ASwitchPorts
import org.openkilda.functionaltests.helpers.model.KildaConfiguration
import org.openkilda.functionaltests.helpers.model.FeatureToggles
import org.openkilda.functionaltests.helpers.model.SwitchPairs
import org.openkilda.functionaltests.helpers.model.SwitchTriplets
import org.openkilda.functionaltests.helpers.model.Switches
import org.openkilda.functionaltests.model.cleanup.CleanupManager

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue

import org.openkilda.functionaltests.helpers.PortAntiflapHelper
import org.openkilda.functionaltests.helpers.StatsHelper
import org.openkilda.functionaltests.helpers.SwitchHelper
import org.openkilda.functionaltests.helpers.TopologyHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.floodlight.FloodlightsHelper
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.tools.IslUtils
import org.openkilda.testing.tools.TopologyPool

import groovy.util.logging.Slf4j
import org.spockframework.spring.EnableSharedInjection
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.ContextConfiguration
import spock.lang.Shared
import spock.lang.Specification

@ContextConfiguration(locations = ["classpath:/spring-context.xml"])
@EnableSharedInjection
@Slf4j
class BaseSpecification extends Specification {

    @Shared @Autowired
    KildaConfiguration kildaConfiguration
    @Shared @Autowired
    FeatureToggles featureToggles
    @Shared @Autowired
    TopologyDefinition topology
    @Shared @Autowired
    TopologyPool topologyPool
    @Autowired @Shared @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired @Shared
    FloodlightsHelper flHelper
    @Autowired @Shared
    LockKeeperService lockKeeper
    @Autowired @Shared
    Database database
    @Autowired @Shared
    IslUtils islUtils
    @Autowired @Shared
    TopologyHelper topologyHelper
    @Autowired @Shared
    SwitchHelper switchHelper
    @Autowired @Shared
    PortAntiflapHelper antiflap
    //component overrides getting existing flows per topology lab(flow, y-flow, ha_flow)
    @Autowired @Shared @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundV2
    @Autowired @Shared
    StatsHelper statsHelper
    @Autowired @Shared
    Switches switches
    @Autowired @Shared
    SwitchPairs switchPairs
    @Autowired @Shared
    SwitchTriplets switchTriplets
    @Autowired @Shared
    IslHelper islHelper
    @Autowired @Shared
    ASwitchFlows aSwitchFlows
    @Autowired @Shared
    ASwitchPorts aSwitchPorts

    @Value('${spring.profiles.active}') @Shared
    String profile
    @Value('${reroute.delay}') @Shared
    int rerouteDelay
    @Value('${discovery.generic.interval}') @Shared
    int discoveryInterval
    @Value('${discovery.timeout}') @Shared
    int discoveryTimeout
    @Value('${discovery.exhausted.interval}') @Shared
    int discoveryExhaustedInterval
    @Value('${discovery.auxiliary.interval}') @Shared
    int discoveryAuxiliaryInterval
    @Value('${antiflap.cooldown}') @Shared
    int antiflapCooldown
    @Value('${antiflap.min}') @Shared
    int antiflapMin
    @Value('${zookeeper.connect_string}') @Shared
    String zkConnectString
    @Value('${affinity.isl.cost:10000}') @Shared
    int affinityIslCost
    @Value('${statsrouter.request.interval:60}') @Shared //statsrouter.request.interval = 60
    int statsRouterRequestInterval

    static ThreadLocal<TopologyDefinition> threadLocalTopology = new ThreadLocal<>()
    static ThreadLocal<CleanupManager> threadLocalCleanupManager = new ThreadLocal<>()

    def setupSpec() {
        log.info "Booked lab with id ${topology.getLabId().toString()} for spec ${this.class.simpleName}, thread: " +
                "${Thread.currentThread()}. sw: ${topology.getSwitches()[0].dpId}"
    }

    def setup() {
        //setup with empty body in order to trigger a SETUP invocation, which is intercepted in several extensions
        //this can have implementation if required
    }

    def cleanupSpec() {
    }

    def requireProfiles(String[] profiles) {
        assumeTrue(this.profile in profiles, "This test requires one of these profiles: '${profiles.join("")}'; " +
                "but current active profile is '${this.profile}'")
    }

    // this cleanup section should be removed after fixing the issue https://github.com/telstra/open-kilda/issues/5480
    void deleteAnyFlowsLeftoversIssue5480() {
        Wrappers.benchmark("Deleting flows leftovers") {
            withPool { northboundV2.getAllFlows().eachParallel { !it.YFlowId && northboundV2.deleteFlow(it.flowId) } }
            withPool { northboundV2.getAllYFlows().eachParallel { northboundV2.deleteYFlow(it.YFlowId) } }
            withPool { northboundV2.getAllHaFlows().eachParallel { northboundV2.deleteHaFlow(it.haFlowId) } }
        }
    }
}
