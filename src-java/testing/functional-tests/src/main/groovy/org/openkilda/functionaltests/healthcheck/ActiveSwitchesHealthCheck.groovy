package org.openkilda.functionaltests.healthcheck

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.Wrappers.WaitTimeoutException
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import spock.lang.Shared

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.openkilda.testing.Constants.SWITCHES_ACTIVATION_TIME
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

@Component("activeSwitchesHealthCheck")
@Scope(SCOPE_PROTOTYPE)

class ActiveSwitchesHealthCheck implements HealthCheck {
    @Autowired
    @Shared
    @Qualifier("islandNb")
    NorthboundService northbound
    @Shared
    @Autowired
    TopologyDefinition topology

    @Override
    List<HealthCheckException> getPotentialProblems() {
        def environmentSwitches = northbound.getActiveSwitches().collect { it.getSwitchId() }
        def topologySwitches = topology.getActiveSwitches().collect { it.getDpId() }
        return (topologySwitches - environmentSwitches).collect {getSwitchNotActivatedException(it.getDpId())}
    }

    @Override
    List<HealthCheckException> attemptToFix(List<HealthCheckException> problems) {
        return withPool {
            problems.collectParallel {
                if (!isSwitchActivated(it)) {
                    return getSwitchNotActivatedException(it.getId())
                }
            }
        }
    }

    private static Boolean isSwitchActivated(HealthCheckException exception) {
        try {
            Wrappers.wait(SWITCHES_ACTIVATION_TIME) {
                northbound.getActiveSwitches().collect { it.getSwitchId() }.contains(exception.getId())
            }
        } catch (WaitTimeoutException exc) {
            return false
        }
        return true
    }

    private static HealthCheckException getSwitchNotActivatedException(SwitchId switchId) {
        return new HealthCheckException(
                "Switch ${switchId} was inactive, however it's marked active in topology.yaml", switchId)
    }
}
