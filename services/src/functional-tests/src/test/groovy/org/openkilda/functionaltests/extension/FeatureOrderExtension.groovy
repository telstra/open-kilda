package org.openkilda.functionaltests.extension

import org.openkilda.functionaltests.extension.healthcheck.HealthCheck
import org.openkilda.functionaltests.extension.spring.PreparesSpringContextDummy

import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.spockframework.runtime.model.SpecInfo

/**
 * This extension builds the correct order for all 'special' features.
 *
 * @see org.openkilda.functionaltests.extension.spring.PreparesSpringContextDummy
 * @see org.openkilda.functionaltests.extension.healthcheck.HealthCheck
 */
class FeatureOrderExtension extends AbstractGlobalExtension {
    void visitSpec(SpecInfo spec) {
        def features = spec.allFeatures
        int orderIndex = 0
        //run dummy test first
        def dummy = features.find {
            it.featureMethod.getAnnotation(PreparesSpringContextDummy)
        }
        dummy && dummy.setExecutionOrder(orderIndex++)

        //run all healthcheck features
        def healthchecks = features.findAll {
            it.featureMethod.getAnnotation(HealthCheck)
        }
        healthchecks.each { it.executionOrder = orderIndex++ }

        //run others
        features.findAll {
            !(healthchecks + dummy).contains(it)
        }.each { it.executionOrder = orderIndex++ }
    }
}
