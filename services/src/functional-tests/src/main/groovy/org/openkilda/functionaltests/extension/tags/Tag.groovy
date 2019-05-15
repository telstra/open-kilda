package org.openkilda.functionaltests.extension.tags

/**
 * All available tags go here.
 * See {@link TagExtension}, {@link Tags}, {@link IterationTags}
 */
enum Tag {
    //pre-defined sets
    SMOKE, REGRESSION,

    //environments
    HARDWARE, VIRTUAL,

    //regularity
    ONDEMAND,

    //positiveness
    POSITIVE, NEGATIVE,

    //speed
    SLOW,//usually for 1minute+ tests
}
