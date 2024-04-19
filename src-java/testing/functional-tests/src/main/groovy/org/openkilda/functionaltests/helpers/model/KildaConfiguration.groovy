package org.openkilda.functionaltests.helpers.model

import org.openkilda.messaging.model.system.KildaConfigurationDto
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.PathComputationStrategy

import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
class KildaConfiguration {

    @Autowired @Qualifier("islandNb")
    NorthboundService northbound

    KildaConfigurationDto getKildaConfiguration() {
        return northbound.getKildaConfiguration()
    }

    KildaConfigurationDto updateKildaConfiguration(KildaConfigurationDto newKildaConfiguration) {
        return northbound.updateKildaConfiguration(newKildaConfiguration)
    }

    KildaConfigurationDto updateFlowEncapsulationType(FlowEncapsulationType flowEncapsulationType) {
        return updateKildaConfiguration(
                new KildaConfigurationDto(flowEncapsulationType: flowEncapsulationType))
    }

    KildaConfigurationDto updateFlowEncapsulationType(String flowEncapsulationType) {
        return updateKildaConfiguration(
                new KildaConfigurationDto(flowEncapsulationType: flowEncapsulationType.toUpperCase())
        )
    }

    KildaConfigurationDto updatePathComputationStrategy(PathComputationStrategy pathComputationStrategy) {
        return updateKildaConfiguration(
                new KildaConfigurationDto(pathComputationStrategy: pathComputationStrategy))
    }

    KildaConfigurationDto updatePathComputationStrategy(String pathComputationStrategy) {
        return updateKildaConfiguration(
                new KildaConfigurationDto(pathComputationStrategy: pathComputationStrategy.toUpperCase())
        )
    }

}