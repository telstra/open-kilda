package org.openkilda.functionaltests.healthcheck

import org.openkilda.model.SwitchFeature
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

@Component("switchConfigurationHealthCheck")
@Scope(SCOPE_PROTOTYPE)

class SwitchConfigurationHealthCheck implements HealthCheck {
    @Autowired
    @Qualifier("islandNb")
    NorthboundService northbound
    @Value('${use.multitable}')
    boolean useMultitable
    @Autowired
    TopologyDefinition topology

    @Override
    List<HealthCheckException> getPotentialProblems() {
        return withPool {
            topology.getActiveSwitches().findAllParallel {
                !(northbound.getSwitchProperties(it.getDpId()).multiTable == useMultitable
                        && it.features.contains(SwitchFeature.MULTI_TABLE))
            }.collectParallel {
                return new HealthCheckException("Switch ${it.getDpId()} has multitable feature opposite to expected one", it.getDpId())
            }
        }.findAll()
    }

    @Override
    List<HealthCheckException> attemptToFix(List<HealthCheckException> problems) {
        withPool {
            problems.eachParallel{
                northbound.updateSwitchProperties(it.getId(), northbound.getSwitchProperties(it.getId()).tap {it.multiTable = useMultitable})
            }
        }
        return getPotentialProblems()
    }

}
