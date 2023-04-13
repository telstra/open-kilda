package org.openkilda.functionaltests.healthcheck


import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.tools.IslUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import spock.lang.Shared

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

@Component("topologyLinksHealthCheck")
@Scope(SCOPE_PROTOTYPE)

class TopologyLinksHealthCheck implements HealthCheck {
    @Autowired
    @Shared
    @Qualifier("islandNb")
    NorthboundService northbound
    @Shared
    @Autowired
    TopologyDefinition topology
    @Shared
    @Autowired
    IslUtils islUtils

    @Override
    List<HealthCheckException> getPotentialProblems() {
        def environmentLinksInfo = northbound.getAllLinks()
        def environmentLinks = environmentLinksInfo.collect { it.getId() }
        def topologyLinks = topology.getIsls().collectMany { isl ->
            [islUtils.getIslInfo(environmentLinksInfo, isl).orElse(null),
             islUtils.getIslInfo(environmentLinksInfo, isl.reversed).orElse(null)
            ]
        }.findAll {it != null}
        .collect {it.getId()}
        return (topologyLinks - environmentLinks).collect {
            new HealthCheckException("ISL ${it} is described in topology.yaml, but doesn't present in environment", it)
        } + (environmentLinks - topologyLinks).collect {
            new HealthCheckException("ISL ${it} presents in environment, but is not described in topology.yaml", it)
        }
    }
}
