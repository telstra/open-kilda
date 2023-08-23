package org.openkilda.functionaltests.helpers.model

import static org.junit.jupiter.api.Assumptions.assumeFalse

/**
 * Class which simplifies search for corresponding switch pair. Just chain existing methods to combine requirements
 * Usage: topologyHelper.getAllSwitchPairs()
 * .nonNeighouring()
 * .withAllTraffgerns()
 * .random()
 * Also it eliminates need to verify if no switch can be found (skips test immediately)
 */
class SwitchPairs {
    private List<SwitchPair> switchPairs

    SwitchPairs(List<SwitchPair> switchPairs) {
        this.switchPairs = switchPairs
    }

    SwitchPairs withAtLeastNNonOverlappingPaths(int nonOverlappingPaths) {
        return new SwitchPairs(switchPairs.findAll {
            it.paths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size() >= nonOverlappingPaths})
    }

    SwitchPairs withShortestPathShorterThanOthers() {
        return new SwitchPairs(switchPairs.findAll {it.getPaths()[0].size() != it.getPaths()[1].size()})
    }

    SwitchPairs nonNeighbouring() {
        return new SwitchPairs(switchPairs.findAll { it.paths.min { it.size() }?.size() > 2})
    }

    SwitchPairs sortedByShortestPathLengthAscending() {
        return new SwitchPairs(switchPairs.sort {it.paths.min { it.size() }?.size() > 2})
    }

    SwitchPair random() {
        return new SwitchPairs(switchPairs.shuffled()).first()
    }

    SwitchPair first() {
        assumeFalse(switchPairs.isEmpty(), "No suiting switch pair found")
        return switchPairs.first()
    }
}
