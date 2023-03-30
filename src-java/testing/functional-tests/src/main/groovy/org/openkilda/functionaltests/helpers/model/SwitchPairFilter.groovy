package org.openkilda.functionaltests.helpers.model

class SwitchPairFilter {
    final static Closure WITH_AT_LEAST_N_NON_OVERLAPPING_PATHS = { SwitchPair switchPair, int pathsNumber ->
        switchPair.paths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size() >= pathsNumber
    }
    final static Closure SHORTEST_PATH_IS_SHORTER_THAN_OTHERS = { SwitchPair switchPair ->
        switchPair.getPaths()[0].size() != switchPair.getPaths()[1].size()
    }

}
