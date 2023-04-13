package org.openkilda.functionaltests.healthcheck

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

import org.openkilda.testing.service.northbound.NorthboundService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import spock.lang.Shared


@Component("kafkaHealthCheck")
@Scope(SCOPE_PROTOTYPE)

class KafkaHealthCheck implements HealthCheck{
    @Autowired @Shared @Qualifier("islandNb")
    NorthboundService northbound

    @Override
    List<HealthCheckException> getPotentialProblems() {
        if (northbound.getHealthCheck().components["kafka"] == "operational") {
            return []
        } else {
            return [new HealthCheckException("Kafka is not operational", null)]
        }
    }
}
