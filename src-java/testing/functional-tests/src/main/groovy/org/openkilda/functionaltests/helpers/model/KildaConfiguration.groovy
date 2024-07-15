package org.openkilda.functionaltests.helpers.model

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.model.system.KildaConfigurationDto
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.PathComputationStrategy

import org.openkilda.testing.service.northbound.NorthboundService

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_KILDA_CONFIGURATION

@Component
@Scope(SCOPE_PROTOTYPE)
class KildaConfiguration {

    @Autowired @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired
    CleanupManager cleanupManager
    KildaConfigurationDto initialState

    @PostConstruct
    void init() {
        initialState = getKildaConfiguration()
    }

    KildaConfigurationDto getKildaConfiguration() {
        return northbound.getKildaConfiguration()
    }

    KildaConfigurationDto updateKildaConfiguration(KildaConfigurationDto newKildaConfiguration) {
        cleanupManager.addAction(RESTORE_KILDA_CONFIGURATION, {northbound.updateKildaConfiguration(initialState)})
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