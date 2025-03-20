package org.openkilda.performancetests

import static org.openkilda.testing.Constants.WAIT_OFFSET
import org.openkilda.functionaltests.helpers.PortAntiflapHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchExtended
import org.openkilda.functionaltests.helpers.model.Switches
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.model.system.FeatureTogglesDto
import org.openkilda.messaging.model.system.KildaConfigurationDto
import org.openkilda.performancetests.helpers.TopologyHelper
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.floodlight.FloodlightsHelper
import org.openkilda.testing.service.labservice.LabService
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.tools.IslUtils

import org.spockframework.spring.EnableSharedInjection
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.ContextConfiguration
import spock.lang.Shared
import spock.lang.Specification

@ContextConfiguration(locations = ["classpath:/spring-context.xml"])
@EnableSharedInjection
class BaseSpecification extends Specification {

    private static Throwable healthCheckError
    private static boolean healthCheckRan = false

    @Autowired
    @Shared @Qualifier("northboundServiceImpl")
    NorthboundService northbound
    @Autowired
    @Shared @Qualifier("northboundServiceV2Impl")
    NorthboundServiceV2 northboundV2
    @Autowired
    @Shared
    FloodlightsHelper flHelper
    @Autowired
    @Shared
    LabService labService
    @Autowired
    @Shared
    LockKeeperService lockKeeper
    @Autowired
    @Shared
    FlowFactory flowFactory
    @Autowired
    @Shared
    @Qualifier("performance")
    TopologyHelper topoHelper
    @Autowired
    @Shared
    Database database
    @Autowired
    @Shared
    IslUtils islUtils
    @Autowired
    @Shared
    PortAntiflapHelper antiflap
    @Autowired
    @Shared
    Switches switches

    @Value('${discovery.generic.interval}')
    int discoveryInterval

    @Value('${discovery.timeout}')
    int discoveryTimeout

    @Value('${use.hs}')
    boolean useHs

    @Value('${use.multitable}')
    boolean useMultitable

    @Value('${perf.debug}')
    boolean debug

    //CleanupManager is not called during perf-tests execution due to the implementation
    //to use automatic cleanup the appropriate listeners should be added
    static ThreadLocal<CleanupManager> threadLocalCleanupManager = new ThreadLocal<>()

    def setupSpec() {
        healthCheck()
        northbound.getAllFlows().each { northbound.deleteFlow(it.id) }
        Wrappers.wait(WAIT_OFFSET * 5) {
            assert northbound.getAllFlows().empty
        }
        topoHelper.purgeTopology()
        flowFactory.setNorthbound(northbound)
        flowFactory.setNorthboundV2(northboundV2)
        antiflap.setNorthbound(northbound)
        antiflap.setNorthboundV2(northboundV2)
    }

    def healthCheck() {
        if (healthCheckRan && !healthCheckError) {
            return
        }
        if (healthCheckRan && healthCheckError) {
            throw healthCheckError
        }
        try {
            def features = FeatureTogglesDto.builder()
                    .createFlowEnabled(true)
                    .updateFlowEnabled(true)
                    .deleteFlowEnabled(true)
                    .flowsRerouteOnIslDiscoveryEnabled(true)
                    .useBfdForIslIntegrityCheck(true)
                    .syncSwitchOnConnect(true)
                    .build()
            northbound.toggleFeature(features)
            northbound.updateKildaConfiguration(KildaConfigurationDto.builder().useMultiTable(useMultitable).build())
        } catch (Throwable t) {
            healthCheckError = t
            throw t
        } finally {
            healthCheckRan = true
        }
    }

    def setup() {
        //setup with empty body in order to trigger a SETUP invocation, which is intercepted in several extensions
        //this can have implementation if required
    }

    SwitchExtended pickRandom(List<SwitchExtended> switches) {
        switches[new Random().nextInt(switches.size())]
    }

    void deleteFlows( List<FlowExtended> flows) {
        flows.each { it.sendDeleteRequest() }
        def waitTime = northbound.getAllFlows().size()
        if(waitTime < discoveryTimeout) {
            waitTime = discoveryTimeout
        }
        Wrappers.wait(waitTime) {
            northbound.getAllFlows().isEmpty()
        }
    }

    void setTopologyInContext(TopologyDefinition topology){
        flowFactory.setTopology(topology)
        switches.setTopology(topology)
        switches.switchFactory.setTopology(topology)
    }
}
