package org.openkilda.functionaltests.extension.tags

/**
 * All available tags go here.
 * See {@link TagExtension}, {@link Tags}, {@link IterationTags}
 */
enum Tag {
    //regularity
    ONDEMAND,

    //positiveness
    POSITIVE, NEGATIVE,

    //speed
    SLOW,//usually for 1minute+ tests
}
