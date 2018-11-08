package org.openkilda.functionaltests.extension.fixture.rule

import org.spockframework.runtime.extension.ExtensionAnnotation

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Ensures that certain feature leaves all switches with no flow rules. If applied to a spec, the annotation will be 
 * applied to every feature in the spec
 * <br> See {@link CleanupSwitchesRule}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.TYPE])
@ExtensionAnnotation(CleanupSwitchesRule)
@interface CleanupSwitches {
}
