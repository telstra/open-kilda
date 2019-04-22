package org.openkilda.functionaltests.extension.tags

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Allows to put tags on certain iterations of data-driven test (test with 'where' block).
 * Multiple IterationTag annotations can be put via {@link IterationTags}
 * See {@link TagExtension}
 */
@Target([ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@interface IterationTag {
    Tag[] tags() default [] //these tags will be applied to iterations that are matched by 'iterationNameRegex'
    String iterationNameRegex() default ".*" //Regex used to match certain iteration's name. Full match.
    /**Additional limitation when unable to exactly pin-point certain iteration(s) by name.
     Will pick first N iterations matched by iterationNameRegex*/
    int take() default 2147483647
}
