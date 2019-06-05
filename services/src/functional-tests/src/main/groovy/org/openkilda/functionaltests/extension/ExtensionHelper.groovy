package org.openkilda.functionaltests.extension

import org.openkilda.functionaltests.extension.healthcheck.HealthCheck
import org.openkilda.functionaltests.extension.spring.PrepareSpringContextDummy

import org.spockframework.runtime.model.FeatureInfo

class ExtensionHelper {
    static boolean isFeatureSpecial(FeatureInfo feature) {
        feature.featureMethod.getAnnotation(PrepareSpringContextDummy) ||
                feature.featureMethod.getAnnotation(HealthCheck)
    }
}
