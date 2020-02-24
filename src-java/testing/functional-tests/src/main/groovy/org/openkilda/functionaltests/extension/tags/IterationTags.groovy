package org.openkilda.functionaltests.extension.tags

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Container annotation for multiple {@link IterationTag}
 */
@Target([ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@interface IterationTags {
    IterationTag[] value()
}
