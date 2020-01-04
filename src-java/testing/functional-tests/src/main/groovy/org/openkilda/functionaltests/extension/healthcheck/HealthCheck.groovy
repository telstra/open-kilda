package org.openkilda.functionaltests.extension.healthcheck

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Any test annotated with @HealthCheck will be run only once and will have higher order priority above any other 
 * tests without this annotation. Can be applied to tests in 'parent' specifications.
 *
 * @see HealthCheckExtension
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface HealthCheck {}