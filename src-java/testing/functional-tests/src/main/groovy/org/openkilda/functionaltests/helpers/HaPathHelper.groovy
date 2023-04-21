package org.openkilda.functionaltests.helpers

import groovy.util.logging.Slf4j
import org.openkilda.northbound.dto.v2.haflows.HaFlowPaths
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE
import static org.openkilda.functionaltests.helpers.PathHelper.convert

/**
 * Holds utility methods for working with flow paths.
 */
@Component
@Slf4j
@Scope(SCOPE_PROTOTYPE)
class HaPathHelper{
    @Autowired
    PathHelper pathHelper

    Set<Isl> "get common ISLs"(HaFlowPaths haFlowPaths1, HaFlowPaths haFlowPaths2) {
        return getInvolvedIsls(haFlowPaths1).intersect(getInvolvedIsls(haFlowPaths2))
    }

    Set<Isl> getInvolvedIsls(HaFlowPaths haFlowPaths) {
        return haFlowPaths.getSubFlowPaths()
                .collect {pathHelper.getInvolvedIsls(convert(it.getForward()))}
        .flatten() as Set
    }
}
