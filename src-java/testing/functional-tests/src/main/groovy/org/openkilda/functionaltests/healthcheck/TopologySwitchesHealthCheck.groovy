package org.openkilda.functionaltests.healthcheck


import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import spock.lang.Shared

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

@Component("topologySwitchesHealthCheck")
@Scope(SCOPE_PROTOTYPE)

class TopologySwitchesHealthCheck implements HealthCheck {
    @Autowired
    @Shared
    @Qualifier("islandNb")
    NorthboundService northbound
    @Shared
    @Autowired
    TopologyDefinition topology

    @Override
    List<HealthCheckException> getPotentialProblems() {
        def environmentSwitches = northbound.getAllSwitches().collect { it.getSwitchId() }
        def topologySwitches = topology.getSwitches().collect { it.getDpId() }
        def lostSwitches = (topologySwitches - environmentSwitches)
                .collect { new HealthCheckException("Switch ${it.getDpId()} is described in topology.yaml, but doesn't present in environment", it.getDpId()) }
        return lostSwitches + (environmentSwitches - topologySwitches)
                .collect { new HealthCheckException("Switch ${it.getDpId()} presents in environment, but is not described in topology.yaml", it.getDpId()) }
    }
}
