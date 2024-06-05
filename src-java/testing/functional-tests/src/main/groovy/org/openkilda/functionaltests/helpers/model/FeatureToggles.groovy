package org.openkilda.functionaltests.helpers.model

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.model.cleanup.CleanupAfter
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.model.system.FeatureTogglesDto
import org.openkilda.testing.service.northbound.NorthboundService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_FEATURE_TOGGLE
import static org.openkilda.functionaltests.model.cleanup.CleanupAfter.TEST

@Slf4j
@Component
@Scope(SCOPE_PROTOTYPE)
class FeatureToggles {

    @Autowired @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired
    CleanupManager cleanupManager

    FeatureTogglesDto initialState

    @PostConstruct
    void init() {
        initialState = getFeatureToggles()
    }

    FeatureTogglesDto setFeatureToggles(FeatureTogglesDto featureTogglesDto, CleanupAfter cleanupAfter= TEST) {
        cleanupManager.addAction(RESTORE_FEATURE_TOGGLE, {northbound.toggleFeature(initialState)}, cleanupAfter)
        northbound.toggleFeature(featureTogglesDto)
    }

    FeatureTogglesDto getFeatureToggles() {
        return northbound.getFeatureToggles()
    }

    FeatureTogglesDto toggleMultipleFeatures(FeatureTogglesDto features) {
        log.debug("Updating multiple Feature Toggles: '${features}'")
        return setFeatureToggles(features)
    }

    FeatureTogglesDto createFlowEnabled(boolean enabled) {
        log.debug("Updating Feature Toggle \'createFlowEnabled\' to: '${enabled}'")
        setFeatureToggles(FeatureTogglesDto.builder()
                .createFlowEnabled(enabled)
                .build()
        )
    }

    FeatureTogglesDto updateFlowEnabled (boolean enabled) {
        log.debug("Updating Feature Toggle \'updateFlowEnabled\' to: '${enabled}'")
        setFeatureToggles(FeatureTogglesDto.builder()
                .updateFlowEnabled(enabled)
                .build()
        )
    }

    FeatureTogglesDto deleteFlowEnabled (boolean enabled) {
        log.debug("Updating Feature Toggle \'deleteFlowEnabled\' to: '${enabled}'")
        setFeatureToggles(FeatureTogglesDto.builder()
                .deleteFlowEnabled(enabled)
                .build()
        )
    }

    FeatureTogglesDto flowsRerouteUsingDefaultEncapType (boolean enabled) {
        log.debug("Updating Feature Toggle \'flowsRerouteUsingDefaultEncapType\' to: '${enabled}'")
        setFeatureToggles(FeatureTogglesDto.builder()
                .flowsRerouteUsingDefaultEncapType(enabled)
                .build()
        )
    }

    FeatureTogglesDto flowsRerouteOnIslDiscoveryEnabled (boolean enabled) {
        log.debug("Updating Feature Toggle \'flowsRerouteOnIslDiscoveryEnabled\' to: '${enabled}'")
        setFeatureToggles(FeatureTogglesDto.builder()
                .flowsRerouteOnIslDiscoveryEnabled(enabled)
                .build()
        )
    }

    FeatureTogglesDto floodlightRoutePeriodicSync (boolean enabled, CleanupAfter cleanupAfter = TEST) {
        log.debug("Updating Feature Toggle \'floodlightRoutePeriodicSync\' to: '${enabled}'")
        setFeatureToggles(FeatureTogglesDto.builder()
                .floodlightRoutePeriodicSync(enabled)
                .build(),
                cleanupAfter
        )
    }

    FeatureTogglesDto useBfdForIslIntegrityCheck (boolean enabled) {
        log.debug("Updating Feature Toggle \'useBfdForIslIntegrityCheck\' to: '${enabled}'")
        setFeatureToggles(FeatureTogglesDto.builder()
                .useBfdForIslIntegrityCheck(enabled)
                .build()
        )
    }

    FeatureTogglesDto server42FlowRtt (boolean enabled) {
        log.debug("Updating Feature Toggle \'server42FlowRtt\' to: '${enabled}'")
        setFeatureToggles(FeatureTogglesDto.builder()
                .server42FlowRtt(enabled)
                .build()
        )
    }

    FeatureTogglesDto server42IslRtt (boolean enabled) {
        log.debug("Updating Feature Toggle \'server42IslRtt\' to: '${enabled}'")
        setFeatureToggles(FeatureTogglesDto.builder()
                .server42IslRtt(enabled)
                .build()
        )
    }

    FeatureTogglesDto flowLatencyMonitoringReactions (boolean enabled) {
        log.debug("Updating Feature Toggle \'flowLatencyMonitoringReactions\' to: '${enabled}'")
        setFeatureToggles(FeatureTogglesDto.builder()
                .flowLatencyMonitoringReactions(enabled)
                .build()
        )
    }
}
