package org.openkilda.functionaltests

import groovy.util.logging.Slf4j
import org.openkilda.functionaltests.healthcheck.HealthCheck
import org.openkilda.functionaltests.healthcheck.HealthCheckException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

import static groovyx.gpars.GParsExecutorsPool.withPool

@Slf4j
class HealthCheckSpecification extends HealthCheckBaseSpecification {

    static Throwable healthCheckError
    static boolean healthCheckRan = false
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


    def healthCheck() {
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
                     linkSpeedHealthCheck,
                     switchSynchronizationHealthCheck,
                     switchRegionHealthCheck,
                     switchConfigurationHealthCheck,
                     hAFlowsHealthCheck,
                     yFlowsHealthCheck
                    ].collectParallel {
                        toCheckAndFixClosure(it)()
                    }
            unresolvedProblems += toCheckAndFixClosure(flowsHealthCheck)()
            unresolvedProblems += toCheckAndFixClosure(switchSynchronizationHealthCheck)()
            unresolvedProblems = unresolvedProblems
                    .flatten()
                    .findAll { it != null }
            if (!unresolvedProblems.isEmpty()) {
                throw new HealthCheckException(unresolvedProblems.collect { it.getMessage() }.join("\n"), null)
            }

        }
    }

    Closure toCheckAndFixClosure(HealthCheck healthCheck) {
        return {
            def checkResult = healthCheck.getPotentialProblems()
            return checkResult.isEmpty() ? null : healthCheck.attemptToFix(checkResult)
        }
    }

    boolean getHealthCheckRan() { healthCheckRan }

    Throwable getHealthCheckError() { healthCheckError }

    void setHealthCheckRan(boolean hcRan) { healthCheckRan = hcRan }

    void setHealthCheckError(Throwable t) { healthCheckError = t }

}
