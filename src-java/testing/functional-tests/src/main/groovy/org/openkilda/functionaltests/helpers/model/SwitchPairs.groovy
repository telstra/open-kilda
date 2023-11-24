package org.openkilda.functionaltests.helpers.model

import org.openkilda.functionaltests.helpers.SwitchHelper
import org.openkilda.functionaltests.helpers.TopologyHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import static org.junit.jupiter.api.Assumptions.assumeFalse

import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import static org.openkilda.model.SwitchFeature.NOVIFLOW_COPY_FIELD
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

/**
 * Class which simplifies search for corresponding switch pair. Just chain existing methods to combine requirements
 * Usage: switchPairs.all()
 * .nonNeighouring()
 * .withAllTraffgerns()
 * .random()
 * Also it eliminates need to verify if no switch can be found (skips test immediately)
 */

@Component
@Scope(SCOPE_PROTOTYPE)
class SwitchPairs {
    List<SwitchPair> switchPairs
    @Autowired
    SwitchHelper switchHelper
    @Autowired
    TopologyHelper topologyHelper

    SwitchPairs(List<SwitchPair> switchPairs) {
        this.switchPairs = switchPairs
    }

    SwitchPairs all(Boolean includeReverse = true) {
        switchPairs = topologyHelper.getAllSwitchPairs(includeReverse)
        return this
    }

    SwitchPairs singleSwitch() {
        switchPairs = topologyHelper.getAllSingleSwitchPairs()
        return this
    }

    SwitchPairs withAtLeastNNonOverlappingPaths(int nonOverlappingPaths) {
        switchPairs = switchPairs.findAll {
            it.paths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size() >= nonOverlappingPaths
        }
        return this
    }

    SwitchPairs withShortestPathShorterThanOthers() {
        switchPairs = switchPairs.findAll { it.getPaths()[0].size() != it.getPaths()[1].size() }
        return this
    }

    SwitchPairs nonNeighbouring() {
        switchPairs = switchPairs.findAll { it.paths.min { it.size() }?.size() > 2 }
        return this
    }

    SwitchPairs neighbouring() {
        switchPairs = switchPairs.findAll { it.paths.min { it.size() }?.size() == 2 }
        return this
    }

    SwitchPairs excludePairs(List<SwitchPair> excludePairs) {
        switchPairs = switchPairs.findAll { !excludePairs.contains(it) }
        return this
    }

    SwitchPairs sortedByShortestPathLengthAscending() {
        switchPairs = switchPairs.sort { it.paths.min { it.size() }?.size() > 2 }
        return this
    }

    SwitchPair random() {
        switchPairs = switchPairs.shuffled()
        return this.first()
    }

    SwitchPair first() {
        assumeFalse(switchPairs.isEmpty(), "No suiting switch pair found")
        return switchPairs.first()
    }

    SwitchPairs includeSwitch(Switch sw) {
        switchPairs = switchPairs.findAll { it.src == sw || it.dst == sw }
        return this
    }

    SwitchPairs excludeSwitches(List<Switch> switchesList) {
        switchPairs = switchPairs.findAll { !(it.src in switchesList) || !(it.dst in switchesList) }
        return this
    }

    List<Switch> collectSwitches() {
        switchPairs.collectMany { return [it.src, it.dst] }.unique()
    }

    SwitchPairs withAtLeastNTraffgensOnSource(int traffgensConnectedToSource) {
        switchPairs = switchPairs.findAll { it.getSrc().getTraffGens().size() >= traffgensConnectedToSource }
        return this
    }

    SwitchPairs withBothSwitchesVxLanEnabled() {
        switchPairs = switchPairs.findAll { [it.src, it.dst].every { sw -> switchHelper.isVxlanEnabled(sw.dpId) } }
        return this
    }

    SwitchPairs withIslRttSupport() {
        this.assertAllSwitchPairsAreNeighbouring()
        switchPairs = switchPairs.findAll { [it.src, it.dst].every { it.features.contains(NOVIFLOW_COPY_FIELD) } }
        return this
    }

    SwitchPairs withExactlyNIslsBetweenSwitches(int expectedIslsBetweenSwitches) {
        this.assertAllSwitchPairsAreNeighbouring()
        switchPairs = switchPairs.findAll { it.paths.findAll { it.size() == 2 }.size() == expectedIslsBetweenSwitches }
        return this
    }

    SwitchPairs withMoreThanNIslsBetweenSwitches(int expectedMinimalIslsBetweenSwitches) {
        this.assertAllSwitchPairsAreNeighbouring()
        switchPairs = switchPairs.findAll {
                    it.paths.findAll { it.size() == 2 }
                            .size() > expectedMinimalIslsBetweenSwitches
                }
        return this
    }

    private void assertAllSwitchPairsAreNeighbouring() {
        assert switchPairs.size() == this.neighbouring().getSwitchPairs().size(),
                "This method is applicable only to the neighbouring switch pairs"
    }
}
