package org.openkilda.functionaltests.healthcheck

import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import spock.lang.Shared

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

@Component("switchSynchronizationHealthCheck")
@Scope(SCOPE_PROTOTYPE)

class SwitchSynchronizationHealthCheck implements HealthCheck {
    @Autowired
    @Shared
    @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired
    @Shared
    @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundServiceV2

    @Override
    List<HealthCheckException> getPotentialProblems() {
        return withPool {
            northbound.getAllSwitches().collectParallel {
                if (!northboundServiceV2.validateSwitch(it.getSwitchId())) {
                    return new HealthCheckException("Switch ${it.getSwitchId()} isn't synchronized with kilda", it.getSwitchId())
                }
            }
        }.findAll()
    }

    @Override
    List<HealthCheckException> attemptToFix(List<HealthCheckException> problems) {
        withPool {
            problems.collectParallel{
                northbound.synchronizeSwitch(it.getId(), true) //Does it remove excess meters as well?
            }
        }
        return getPotentialProblems()
    }

}
