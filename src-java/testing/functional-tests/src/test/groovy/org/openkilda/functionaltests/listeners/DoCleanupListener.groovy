package org.openkilda.functionaltests.listeners

import static groovyx.gpars.GParsPool.withPool

import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService

import org.spockframework.runtime.model.ErrorInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

class DoCleanupListener extends AbstractSpringListener {

    @Autowired
    TopologyDefinition topology

    @Autowired @Qualifier("islandNb")
    NorthboundService northbound

    @Override
    void error(ErrorInfo error) {
        def thrown = error.exception
        if (thrown instanceof AssertionError) {
            if (thrown.getMessage().contains("SwitchValidationExtendedResult(")) {
                withPool {
                    topology.activeSwitches.eachParallel { sw ->
                        northbound.synchronizeSwitch(sw.dpId, true)
                    }
                }
            }
        }
    }
}
