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

@Component("linkPropertiesHealthCheck")
@Scope(SCOPE_PROTOTYPE)

class LinkPropertiesHealthCheck implements HealthCheck {
    @Autowired
    @Shared
    @Qualifier("islandNb")
    NorthboundService northbound

    @Override
    List<HealthCheckException> getPotentialProblems() {
        return northbound.getAllLinkProps().collect {
            new HealthCheckException("Link ${it.toString()} has properties", it)
        }

    }

    @Override
    List<HealthCheckException> attemptToFix(List<HealthCheckException> problems) {
        northbound.deleteLinkProps(problems.collect {it.getId()})
        return getPotentialProblems()
    }

}
