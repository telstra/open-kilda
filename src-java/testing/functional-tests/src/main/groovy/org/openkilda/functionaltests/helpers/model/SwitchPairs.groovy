package org.openkilda.functionaltests.helpers.model

import static org.junit.jupiter.api.Assumptions.assumeFalse

import org.openkilda.testing.model.topology.TopologyDefinition.Switch

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
    SwitchPairs neighbouring() {
        return new SwitchPairs(switchPairs.findAll { it.paths.min { it.size() }?.size() == 2})
    }

    SwitchPairs excludePairs(List<SwitchPair> excludePairs) {
        return new SwitchPairs(switchPairs.findAll { !excludePairs.contains(it) })
    }

    SwitchPairs sortedByShortestPathLengthAscending() {
        return new SwitchPairs(switchPairs.sort {it.paths.min { it.size() }?.size() > 2})
    }

    SwitchPairs sortedBySmallestPathsAmount() {
        return new SwitchPairs(switchPairs.sort{it.paths.size()})
    }

    SwitchPair random() {
        return new SwitchPairs(switchPairs.shuffled()).first()
    }

    SwitchPair first() {
        assumeFalse(switchPairs.isEmpty(), "No suiting switch pair found")
        return switchPairs.first()
    }

    SwitchPairs includeSwitch(Switch sw) {
        return new SwitchPairs(switchPairs.findAll { it.src == sw || it.dst == sw})
    }

    SwitchPairs excludeSwitches(List<Switch> switchesList) {
        return new SwitchPairs(switchPairs.findAll { !(it.src in switchesList) || !(it.dst in switchesList)})
    }

    List<Switch> collectSwitches() {
        switchPairs.collectMany { return [it.src, it.dst] }.unique()
    }
}
