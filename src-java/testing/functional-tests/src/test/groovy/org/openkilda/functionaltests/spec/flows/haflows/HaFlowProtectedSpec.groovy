package org.openkilda.functionaltests.spec.flows.haflows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.haflows.HaFlow

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

@Slf4j
@Narrative("Verify operations with protected paths on HA-flows.")
class HaFlowProtectedSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    HaFlowHelper haFlowHelper
    @Autowired
    @Shared
    YFlowHelper yFlowHelper

    @Tidy
    def "Able to enable protected path on an HA-flow"() {
        assumeTrue(useMultitable, "HA-flow operations require multiTable switch mode")
        given: "A simple HA-flow"
        def swT = topologyHelper.switchTriplets.find {
            if (it.ep1 == it.ep2 || it.ep1 == it.shared || it.ep2 == it.shared) {
                return false
            }
            def pathsCombinations = [it.pathsEp1, it.pathsEp2].combinations()
            for (i in 0..<pathsCombinations.size()) {
                for (j in i + 1..<pathsCombinations.size()) {
                    if (!areHaPathsIntersect(pathsCombinations[i], pathsCombinations[j])) {
                        return true
                    }
                }
            }
            return false
        }
        assumeTrue(swT != null, "These cases cannot be covered on given topology:")
        def haFlowRequest = haFlowHelper.randomHaFlow(swT)
        HaFlow haFlow = haFlowHelper.addHaFlow(haFlowRequest)
        assert !northboundV2.getHaFlowPaths(haFlow.haFlowId).sharedPath.protectedPath

        when: "Update flow: enable protected path(allocateProtectedPath=true)"
        def update = haFlowHelper.convertToUpdate(haFlow.tap { it.allocateProtectedPath = true })
        def updateResponse = haFlowHelper.updateHaFlow(haFlow.haFlowId, update)

        then: "Update response contains enabled protected path"
        updateResponse.allocateProtectedPath

        and: "Protected path is really enabled on the HA-Flow"
        northboundV2.getHaFlow(haFlow.haFlowId).allocateProtectedPath

        and: "Protected path is really created"
        def paths = northboundV2.getHaFlowPaths(haFlow.haFlowId)
        paths.subFlowPaths.each {
            assert it.protectedPath
            assert it.forward != it.protectedPath.forward
        }

        // not implemented yet https://github.com/telstra/open-kilda/issues/5152
        // and: "HA-Flow and related sub-flows are valid"
        // northboundV2.validateHaFlow(haFlow.haFlowId).asExpected

        and: "All involved switches passes switch validation"
        withPool {
            haFlowHelper.getInvolvedSwitches(haFlow.haFlowId).eachParallel { SwitchId switchId ->
                assert northboundV2.validateSwitch(switchId).isAsExpected()
            }
        }

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
    }

    static boolean areHaPathsIntersect(subPaths1, subPaths2) {
        for (List<PathNode> subPath1 : subPaths1) {
            for (List<PathNode> subPath2 : subPaths2) {
                if (subPaths1.intersect(subPaths2)) {
                    return true
                }
            }
        }
        return false
    }
}

