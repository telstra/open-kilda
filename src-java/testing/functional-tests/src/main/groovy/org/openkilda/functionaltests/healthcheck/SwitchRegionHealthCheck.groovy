package org.openkilda.functionaltests.healthcheck

import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.floodlight.FloodlightsHelper
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import spock.lang.Shared

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

@Component("switchRegionHealthCheck")
@Scope(SCOPE_PROTOTYPE)

class SwitchRegionHealthCheck implements HealthCheck {
    @Autowired
    @Shared
    FloodlightsHelper flHelper
    @Shared
    @Autowired
    TopologyDefinition topology

    @Override
    List<HealthCheckException> getPotentialProblems() {
        return withPool {
            flHelper.fls.collectParallel { fl ->
                def expectedSwitchIds = topology.activeSwitches.findAll { fl.region in it.regions }*.dpId
                if (!expectedSwitchIds.empty && fl.floodlightService.switches*.switchId.sort() != expectedSwitchIds.sort()) {
                    return new HealthCheckException("Not all switches are connected to Floodlight Region ${fl.getRegion()}", null)
                }
            }
        }
    }
}

