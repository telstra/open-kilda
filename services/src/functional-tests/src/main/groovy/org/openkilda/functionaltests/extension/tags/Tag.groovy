package org.openkilda.functionaltests.extension.tags

/**
 * All available tags go here.
 * See {@link TagExtension}, {@link Tags}, {@link IterationTags}
 */
enum Tag {
    //pre-defined sets
    SMOKE, REGRESSION, ONDEMAND,

    //environments
    HARDWARE, VIRTUAL,

    //additional markers
    TOPOLOGY_DEPENDENT, //changing the environment or topology may affect the amount of iterations executed
    SMOKE_SWITCHES, // to make sure switches works fine

    //speed
    SLOW,//usually for 1minute+ tests
}
