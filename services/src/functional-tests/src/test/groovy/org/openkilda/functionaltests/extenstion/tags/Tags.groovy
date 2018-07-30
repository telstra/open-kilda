package org.openkilda.functionaltests.extenstion.tags

import java.lang.annotation.*

/**
 * Container annotation for {@link Tag}
 */
@Target([ElementType.TYPE, ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@interface Tags {
    Tag[] value()
}
