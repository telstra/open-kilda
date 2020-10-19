package org.openkilda.functionaltests.extension.tags

/**
 * All available tags go here.
 * See {@link TagExtension}, {@link Tags}, {@link IterationTags}
 */
enum Tag {
    //pre-defined sets
    SMOKE, REGRESSION, ONDEMAND,
    SMOKE_SWITCHES, //focus on switch-related operations, useful for testing new switch firmware

    //environments
    HARDWARE, VIRTUAL,

    //additional markers
    TOPOLOGY_DEPENDENT, //changing the environment or topology may affect the amount of iterations executed
    LOW_PRIORITY, //executed rarely and have lower chance of catching defects. Not executed for each PR
    LOCKKEEPER, //calls lockkeeper in test. may help to exclude such tests if lockkeeper is not available

    //speed
    SLOW,//usually for 1minute+ tests
}
