package org.openkilda.functionaltests.helpers

import groovy.util.logging.Slf4j
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.tools.IslUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

/**
 * Holds utility methods for manipulating y-flows.
 */
@Component
@Slf4j
@Scope(SCOPE_PROTOTYPE)
class IslHelper {
    @Autowired
    IslUtils islUtils
    @Autowired
    PortAntiflapHelper antiflapHelper


    def breakIsl(Isl islToBreak) {
        if (getIslStatus(islToBreak).equals(DISCOVERED)) {
            antiflapHelper.portDown(islToBreak.getSrcSwitch().getDpId(), islToBreak.getSrcPort())
        }
        islUtils.waitForIslStatus([islToBreak], FAILED)
    }

    def breakIsls(Set<Isl> islsToBreak) {
        withPool {
            islsToBreak.eachParallel{
                breakIsl(it)
            }
        }
    }

    def breakIsls(List<Isl> islsToBreak) {
        breakIsls(islsToBreak as Set)
    }

    def restoreIsl(Isl islToRestore) {
        if(!getIslStatus(islToRestore).equals(DISCOVERED)) {
            withPool{
                [{antiflapHelper.portUp(islToRestore.getSrcSwitch().getDpId(), islToRestore.getSrcPort())},
                 {antiflapHelper.portUp(islToRestore.getDstSwitch().getDpId(), islToRestore.getDstPort())}
                ].eachParallel{it()}
            }
        }
        islUtils.waitForIslStatus([islToRestore], DISCOVERED)
    }

    def restoreIsls(Set<Isl> islsToRestore) {
        withPool {
            islsToRestore.eachParallel{
                restoreIsl(it)
            }
        }
    }

    def restoreIsls(List<Isl> islsToRestore) {
        restoreIsls(islsToRestore as Set)
    }

    def getIslStatus(Isl isl) {
        def islInfo = islUtils.getIslInfo(isl)
        if (islInfo.isPresent()) {
            return islInfo.get().state
        } else {
            return null
        }

    }
}
