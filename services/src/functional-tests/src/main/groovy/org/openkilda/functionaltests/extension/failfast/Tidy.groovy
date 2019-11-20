package org.openkilda.functionaltests.extension.failfast

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Informs that annotated test has a 100% perfect cleanup that eliminates any impact on subsequent tests even if it
 * fails unexpectedly on any line of code.
 * Annotated test will not fall under 'failfast' policy and its failure will not prevent execution of further tests.
 * See {@link FailFastExtension}
 */
@Target([ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@interface Tidy {}