package org.openkilda.functionaltests.healthcheck


import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.Wrappers.WaitTimeoutException
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.testing.service.northbound.NorthboundService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import spock.lang.Shared

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.openkilda.testing.Constants.TOPOLOGY_DISCOVERING_TIME
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

@Component("activeLinksHealthCheck")
@Scope(SCOPE_PROTOTYPE)

class ActiveLinksHealthCheck implements HealthCheck {
    @Autowired
    @Shared
    @Qualifier("islandNb")
    NorthboundService northbound

    @Override
    List<HealthCheckException> getPotentialProblems() {
        return northbound.getAllLinks()
                .findAll { it.state != IslChangeType.DISCOVERED }
                .collect {
                    new HealthCheckException("ISL ${it.toString()} is inactive", it.getId())
                }

    }

    @Override
    List<HealthCheckException> attemptToFix(List<HealthCheckException> problems) {
        return withPool {
            problems.collectParallel {
                if (!isLinkActivated(it)) {
                    return it
                }
            }
        }
    }

    private Boolean isLinkActivated(HealthCheckException exception) {
        def link = northbound.getAllLinks().find {it.getId() == exception.getId()}
        def srcSwitchId = link.getSource().getSwitchId()
        def srcPort = link.getSource().getPortNo()
        def dstSwitchId = link.getDestination().getSwitchId()
        def dstPort = link.getDestination().getPortNo()
        try {
            Wrappers.wait(TOPOLOGY_DISCOVERING_TIME) {
                northbound.portDown(srcSwitchId, srcPort)
                northbound.portDown(dstSwitchId, dstPort)
                northbound.portUp(srcSwitchId, srcPort)
                northbound.portUp(dstSwitchId, dstPort)
                assert northbound.getAllLinks().find {it.getId() == exception.getId()}.getState() == IslChangeType.DISCOVERED
            }
        } catch (WaitTimeoutException exc) {
            return false
        }
        return true
    }
}
