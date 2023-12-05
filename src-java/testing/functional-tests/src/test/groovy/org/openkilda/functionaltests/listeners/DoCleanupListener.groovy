package org.openkilda.functionaltests.listeners

import static groovyx.gpars.GParsPool.withPool

import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService

import groovy.util.logging.Slf4j
import org.spockframework.runtime.model.ErrorInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

@Slf4j
class DoCleanupListener extends AbstractSpringListener {

    @Autowired
    TopologyDefinition topology

    @Autowired @Qualifier("islandNb")
    NorthboundService northbound

    @Autowired
    Database database

    private List<String> resetIslRelatedSpecs = ["YFlowPathSwapSpec", "HaFlowIntentionalRerouteSpec", "ProtectedPathV1Spec",
                                                 "ProtectedPathSpec", "MultiRerouteSpec", "IntentionalRerouteSpec", "FlowCrudV1Spec"]

    @Override
    void error(ErrorInfo error) {
        def thrown = error.exception
        if (thrown instanceof AssertionError) {
            if (thrown.getMessage() && thrown.getMessage().contains("SwitchValidationExtendedResult(")) {
                withPool {
                    topology.activeSwitches.eachParallel { sw ->
                        northbound.synchronizeSwitch(sw.dpId, true)
                    }
                }
            }
        }
        if (error.method.parent.name in resetIslRelatedSpecs) {
            def isls = topology.getIslsForActiveSwitches().collectMany { [it, it.reversed] }
            log.info("Resetting ISLs bandwidth due to the failure in " + error.method.parent.name + "\nISLs: " + isls)
            isls.each { database.resetIslBandwidth(it) }
            log.info("Resetting ISLs cost due to the failure in " + error.method.parent.name)
            database.resetCosts(topology.isls)
        }
    }
}
