package org.openkilda.functionaltests.extension.healthcheck

import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.spockframework.runtime.model.SpecInfo

/**
 * Any test annotated with @HealthCheck will be run only once and will have higher order priority above any other 
 * tests without this annotation. Can be applied to tests in 'parent' specifications to run a single healthcheck
 * for all the descendants.
 * Execution order is guaranteed by {@link org.openkilda.functionaltests.extension.FeatureOrderExtension}
 *
 * @see HealthCheck
 * @see org.openkilda.functionaltests.extension.FeatureOrderExtension
 */
class HealthCheckExtension extends AbstractGlobalExtension {

    def healthChecksRun = []

    @Override
    void visitSpec(SpecInfo spec) {
        spec.allFeatures.findAll { it.featureMethod.getAnnotation(HealthCheck) }.each {
            def reflection = it.featureMethod.reflection.toString()
            if (!healthChecksRun.contains(reflection)) {
                it.excluded = false
                healthChecksRun << reflection
            } else {
                it.excluded = true
            }
        }
    }
}
