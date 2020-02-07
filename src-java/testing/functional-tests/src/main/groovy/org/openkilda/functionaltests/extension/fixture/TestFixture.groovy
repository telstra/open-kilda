package org.openkilda.functionaltests.extension.fixture

import org.spockframework.runtime.extension.ExtensionAnnotation

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * See {@link TestFixtureExtension}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ExtensionAnnotation(TestFixtureExtension)
@interface TestFixture {
    String setup()

    String cleanup()
}
