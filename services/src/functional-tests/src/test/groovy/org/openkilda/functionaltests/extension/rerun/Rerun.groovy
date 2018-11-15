package org.openkilda.functionaltests.extension.rerun

import org.spockframework.runtime.extension.ExtensionAnnotation

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * @see RerunExtension
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ExtensionAnnotation(RerunExtension)
@interface Rerun {
    /**
     * How many times to rerun the test. '1' means that the test will run twice: original run + 1 rerun.
     * Should be >=0
     */
    int times() default 1
}
