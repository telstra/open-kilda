package org.openkilda.functionaltests.helpers.model

import org.openkilda.messaging.model.system.FeatureTogglesDto
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Slf4j
@Component
class FeatureToggles {

    @Autowired @Qualifier("islandNb")
    NorthboundService northbound

    FeatureTogglesDto getFeatureToggles() {
        return northbound.getFeatureToggles()
    }

    FeatureTogglesDto toggleMultipleFeatures(FeatureTogglesDto features) {
        log.debug("Updating multiple Feature Toggles: '${features}'")
        return northbound.toggleFeature(features)
    }

    FeatureTogglesDto createFlowEnabled(boolean enabled) {
        log.debug("Updating Feature Toggle \'createFlowEnabled\' to: '${enabled}'")
        northbound.toggleFeature(FeatureTogglesDto.builder()
                .createFlowEnabled(enabled)
                .build()
        )
    }

    FeatureTogglesDto updateFlowEnabled (boolean enabled) {
        log.debug("Updating Feature Toggle \'updateFlowEnabled\' to: '${enabled}'")
        northbound.toggleFeature(FeatureTogglesDto.builder()
                .updateFlowEnabled(enabled)
                .build()
        )
    }

    FeatureTogglesDto deleteFlowEnabled (boolean enabled) {
        log.debug("Updating Feature Toggle \'deleteFlowEnabled\' to: '${enabled}'")
        northbound.toggleFeature(FeatureTogglesDto.builder()
                .deleteFlowEnabled(enabled)
                .build()
        )
    }

    FeatureTogglesDto flowsRerouteUsingDefaultEncapType (boolean enabled) {
        log.debug("Updating Feature Toggle \'flowsRerouteUsingDefaultEncapType\' to: '${enabled}'")
        northbound.toggleFeature(FeatureTogglesDto.builder()
                .flowsRerouteUsingDefaultEncapType(enabled)
                .build()
        )
    }

    FeatureTogglesDto flowsRerouteOnIslDiscoveryEnabled (boolean enabled) {
        log.debug("Updating Feature Toggle \'flowsRerouteOnIslDiscoveryEnabled\' to: '${enabled}'")
        northbound.toggleFeature(FeatureTogglesDto.builder()
                .flowsRerouteOnIslDiscoveryEnabled(enabled)
                .build()
        )
    }

    FeatureTogglesDto floodlightRoutePeriodicSync (boolean enabled) {
        log.debug("Updating Feature Toggle \'floodlightRoutePeriodicSync\' to: '${enabled}'")
        northbound.toggleFeature(FeatureTogglesDto.builder()
                .floodlightRoutePeriodicSync(enabled)
                .build()
        )
    }

    FeatureTogglesDto useBfdForIslIntegrityCheck (boolean enabled) {
        log.debug("Updating Feature Toggle \'useBfdForIslIntegrityCheck\' to: '${enabled}'")
        northbound.toggleFeature(FeatureTogglesDto.builder()
                .useBfdForIslIntegrityCheck(enabled)
                .build()
        )
    }

    FeatureTogglesDto server42FlowRtt (boolean enabled) {
        log.debug("Updating Feature Toggle \'server42FlowRtt\' to: '${enabled}'")
        northbound.toggleFeature(FeatureTogglesDto.builder()
                .server42FlowRtt(enabled)
                .build()
        )
    }

    FeatureTogglesDto server42IslRtt (boolean enabled) {
        log.debug("Updating Feature Toggle \'server42IslRtt\' to: '${enabled}'")
        northbound.toggleFeature(FeatureTogglesDto.builder()
                .server42IslRtt(enabled)
                .build()
        )
    }

    FeatureTogglesDto flowLatencyMonitoringReactions (boolean enabled) {
        log.debug("Updating Feature Toggle \'flowLatencyMonitoringReactions\' to: '${enabled}'")
        northbound.toggleFeature(FeatureTogglesDto.builder()
                .flowLatencyMonitoringReactions(enabled)
                .build()
        )
    }
}
