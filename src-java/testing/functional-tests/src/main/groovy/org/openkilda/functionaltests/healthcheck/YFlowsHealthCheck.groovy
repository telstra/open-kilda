package org.openkilda.functionaltests.healthcheck

import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

@Component("yFlowsHealthCheck")
@Scope(SCOPE_PROTOTYPE)

class YFlowsHealthCheck implements HealthCheck {
    @Autowired
    @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundV2
    @Autowired
    YFlowHelper yFlowHelper

    @Override
    List<HealthCheckException> getPotentialProblems() {
        return northboundV2.getAllYFlows().collect {
            new HealthCheckException("Y-Flow ${it.getYFlowId()} was not removed", it.getYFlowId())
        }

    }

    @Override
    List<HealthCheckException> attemptToFix(List<HealthCheckException> problems) {
        withPool {
            problems.collectParallel{
                yFlowHelper.deleteYFlow(it.getId())
            }
        }
        return []
    }

}
