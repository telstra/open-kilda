package org.openkilda.performancetests

import org.openkilda.functionaltests.extension.fixture.SetupOnce
import org.openkilda.functionaltests.extension.healthcheck.HealthCheck
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.messaging.model.system.FeatureTogglesDto
import org.openkilda.performancetests.helpers.TopologyHelper
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.labservice.LabService
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.tools.IslUtils

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

@ContextConfiguration(locations = ["classpath:/spring-context.xml"])
class BaseSpecification extends Specification implements SetupOnce {
    @Autowired
    NorthboundService northbound

    @Autowired
    LabService labService

    @Autowired
    LockKeeperService lockKeeper

    @Autowired
    FlowHelper flowHelper

    @Autowired
    @Qualifier("performance")
    TopologyHelper topoHelper

    @Autowired
    Database database

    @Autowired
    IslUtils islUtils

    @Value("#{'\${floodlight.regions}'.split(',')}")
    List<String> regions

    @Value("#{'\${floodlight.controllers.management}'.split(',')}")
    List<String> managementControllers

    @Value("#{'\${floodlight.controllers.stat}'.split(',')}")
    List<String> statControllers

    @Value('${discovery.interval}')
    int discoveryInterval

    @Value('${discovery.timeout}')
    int discoveryTimeout

    @Value('${use.hs}')
    boolean useHs

    /**
     * Use this instead of setupSpec in order to have access to Spring Context and do actions BeforeClass.
     * Can be overridden by inheritor specs.
     * @see {@link org.openkilda.functionaltests.extension.fixture.SetupOnceExtension}
     */
    @Override
    def setupOnce() {
    }

    @HealthCheck
    def "Configure feature toggles"() {
        expect: "Feature toggles are configured"
        def features = FeatureTogglesDto.builder()
                                        .createFlowEnabled(true)
                                        .updateFlowEnabled(true)
                                        .deleteFlowEnabled(true)
                                        .flowsRerouteOnIslDiscoveryEnabled(true)
                                        .useBfdForIslIntegrityCheck(true)
                                        .flowsRerouteViaFlowHs(useHs)
                                        .build()
        northbound.toggleFeature(features)
    }

    def setup() {
        //setup with empty body in order to trigger a SETUP invocation, which is intercepted in several extensions
        //this can have implementation if required
    }
}
