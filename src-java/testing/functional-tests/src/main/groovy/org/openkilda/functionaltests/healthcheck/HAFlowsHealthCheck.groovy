package org.openkilda.functionaltests.healthcheck


import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import spock.lang.Shared

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

@Component("hAFlowsHealthCheck")
@Scope(SCOPE_PROTOTYPE)

class HAFlowsHealthCheck implements HealthCheck {
    @Autowired
    @Shared
    @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundV2

    @Override
    List<HealthCheckException> getPotentialProblems() {
        return northboundV2.getAllHaFlows().collect {
            new HealthCheckException("HA Flow ${it.getHaFlowId()} was not removed", it.getHaFlowId())
        }

    }

    @Override
    List<HealthCheckException> attemptToFix(List<HealthCheckException> problems) {
        withPool {
            problems.collectParallel{
                northboundV2.deleteHaFlow(it.getId())
            }
        }
        return []
    }

}
