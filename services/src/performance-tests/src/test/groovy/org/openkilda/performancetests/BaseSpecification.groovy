package org.openkilda.performancetests

import org.openkilda.functionaltests.extension.fixture.SetupOnce
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.performancetests.helpers.TopologyHelper
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.labservice.LabService
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.tools.IslUtils

import org.springframework.beans.factory.annotation.Autowired
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
    TopologyHelper topoHelper

    @Autowired
    Database database

    @Autowired
    IslUtils islUtils

    @Value('#{\'${floodlight.controller.uri}\'.split(\',\')}')
    List<String> controllerHosts;

    @Value('${discovery.interval}')
    int discoveryInterval

    @Value('${discovery.timeout}')
    int discoveryTimeout

    /**
     * Use this instead of setupSpec in order to have access to Spring Context and do actions BeforeClass.
     * Can be overridden by inheritor specs.
     * @see {@link org.openkilda.functionaltests.extension.fixture.SetupOnceExtension}
     */
    @Override
    def setupOnce() {

    }

    def setup() {
        //setup with empty body in order to trigger a SETUP invocation, which is intercepted in several extensions
        //this can have implementation if required
    }
}
