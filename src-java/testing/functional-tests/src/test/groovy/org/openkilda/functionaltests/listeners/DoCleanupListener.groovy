package org.openkilda.functionaltests.listeners

import org.openkilda.functionaltests.healthcheck.HealthCheck
import org.openkilda.functionaltests.healthcheck.HealthCheckException
import org.spockframework.runtime.model.SpecInfo


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

import static groovyx.gpars.GParsExecutorsPool.withPool

class DoCleanupListener extends AbstractSpringListener {
    @Autowired
    @Qualifier("kafkaHealthCheck")
    HealthCheck kafkaHealthCheck
    @Autowired
    @Qualifier("zookeeperHealthCheck")
    HealthCheck zookeeperHealthCheck
    @Autowired
    @Qualifier("activeSwitchesHealthCheck")
    HealthCheck activeSwitchesHealthCheck
    @Autowired
    @Qualifier("topologySwitchesHealthCheck")
    HealthCheck topologySwitchesHealthCheck
    @Autowired
    @Qualifier("activeLinksHealthCheck")
    HealthCheck activeLinksHealthCheck
    @Autowired
    @Qualifier("topologyLinksHealthCheck")
    HealthCheck topologyLinksHealthCheck
    @Autowired
    @Qualifier("hAFlowsHealthCheck")
    HealthCheck hAFlowsHealthCheck
    @Autowired
    @Qualifier("yFlowsHealthCheck")
    HealthCheck yFlowsHealthCheck
    @Autowired
    @Qualifier("flowsHealthCheck")
    HealthCheck flowsHealthCheck
    @Autowired
    @Qualifier("linkPropertiesHealthCheck")
    HealthCheck linkPropertiesHealthCheck
    @Autowired
    @Qualifier("linkSpeedHealthCheck")
    HealthCheck linkSpeedHealthCheck
    @Autowired
    @Qualifier("switchSynchronizationHealthCheck")
    HealthCheck switchSynchronizationHealthCheck
    @Autowired
    @Qualifier("switchRegionHealthCheck")
    HealthCheck switchRegionHealthCheck
    @Autowired
    @Qualifier("switchConfigurationHealthCheck")
    HealthCheck switchConfigurationHealthCheck

    @Override
    void afterSpec(SpecInfo spec) {
        println("=====================Running HC=================")
        withPool {
            List<HealthCheckException> unresolvedProblems =
                    [kafkaHealthCheck,
                     zookeeperHealthCheck,
                     topologySwitchesHealthCheck,
                     activeSwitchesHealthCheck,
                     topologyLinksHealthCheck,
                     activeLinksHealthCheck,
                     linkPropertiesHealthCheck,
                     switchSynchronizationHealthCheck,
                     switchRegionHealthCheck,
                     switchConfigurationHealthCheck,
                     hAFlowsHealthCheck,
                     yFlowsHealthCheck,
                     flowsHealthCheck
                    ].collectParallel {
                        toCheckAndFixClosure(it)()
                    }
            /* These two healthchecks are dependent on some ones from previous group. While the best solution would be
            to set tasks dependency and run  them in one pool, I didn't find a good way how to do that so I just simply
            perform these tasks after the main group.
             */
            unresolvedProblems += toCheckAndFixClosure(linkSpeedHealthCheck)()
            unresolvedProblems += toCheckAndFixClosure(switchSynchronizationHealthCheck)()
            unresolvedProblems = unresolvedProblems
                    .flatten()
                    .findAll { it != null }
            if (!unresolvedProblems.isEmpty()) {
                throw new HealthCheckException(unresolvedProblems.collect { it.getMessage() }.join("\n"), null)
            }
        }
    }

    private static Closure toCheckAndFixClosure(HealthCheck healthCheck) {
        return {
            def checkResult = healthCheck.getPotentialProblems()
            return checkResult.isEmpty() ? null : healthCheck.attemptToFix(checkResult)
        }
    }
}
