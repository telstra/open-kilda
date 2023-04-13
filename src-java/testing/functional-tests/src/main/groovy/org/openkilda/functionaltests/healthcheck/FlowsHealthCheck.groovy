package org.openkilda.functionaltests.healthcheck

import org.openkilda.functionaltests.helpers.FlowHelperV2
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

@Component("flowsHealthCheck")
@Scope(SCOPE_PROTOTYPE)

class FlowsHealthCheck implements HealthCheck {
    @Autowired
    @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundV2
    @Autowired
    FlowHelperV2 flowHelperV2

    @Override
    List<HealthCheckException> getPotentialProblems() {
        return northboundV2.getAllFlows()
        .findAll {it.getYFlowId() == null}
                .collect {
            new HealthCheckException("Flow ${it.getFlowId()} was not removed", it.getFlowId())
        }

    }

    @Override
    List<HealthCheckException> attemptToFix(List<HealthCheckException> problems) {
        withPool {
            problems.collectParallel{
                flowHelperV2.deleteFlow(it.getId())
            }
        }
        return getPotentialProblems()
    }

}
