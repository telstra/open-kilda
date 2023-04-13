package org.openkilda.functionaltests.healthcheck

import groovy.transform.Memoized
import org.openkilda.messaging.info.event.IslInfoData
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.tools.IslUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

@Component("linkSpeedHealthCheck")
@Scope(SCOPE_PROTOTYPE)

class LinkSpeedHealthCheck implements HealthCheck {
    @Autowired
    @Qualifier("islandNb")
    NorthboundService northbound
     @Autowired
     Database database
    @Autowired
    IslUtils islUtils
    @Autowired
    TopologyDefinition topology

    @Override
    List<HealthCheckException> getPotentialProblems() {
        return northbound.getAllLinks()
                .findAll { it.availableBandwidth != it.speed }
                .collect {
                    new HealthCheckException("Link ${it.toString()} has available bandwidth which differs from link speed", it)
                }
    }

    @Override
    List<HealthCheckException> attemptToFix(List<HealthCheckException> problems) {
        withPool {
            problems.eachParallel {
                database.resetIslBandwidth(findIslByInfo(it.getId()))
            }
        }
        return getPotentialProblems()
    }

    private Isl findIslByInfo(IslInfoData islInfo) {
        return getTopologyIsls().find {
            it.srcSwitch.dpId == islInfo.source.switchId && it.srcPort == islInfo.source.portNo &&
                            it.dstSwitch.dpId == islInfo.destination.switchId && it.dstPort == islInfo.destination.portNo
        }
    }
    @Memoized
    private List<Isl> getTopologyIsls() {
        return topology.isls.collectMany { [it, it.reversed] }
    }
}
