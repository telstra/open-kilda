package org.openkilda.functionaltests.helpers.model

import groovy.transform.Memoized
import org.openkilda.functionaltests.helpers.SwitchHelper
import org.openkilda.functionaltests.helpers.TopologyHelper
import org.openkilda.functionaltests.model.switches.Manufacturer
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import static org.junit.jupiter.api.Assumptions.assumeFalse

import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import static org.openkilda.model.SwitchFeature.NOVIFLOW_COPY_FIELD
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

/**
 * Class which simplifies search for corresponding switch pair. Just chain existing methods to combine requirements
 * Usage: switchPairs.all()
 * .nonNeighbouring()
 * .withTraffgensOnBothEnds()
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
    @Autowired
    TopologyDefinition topology
    @Autowired
    @Qualifier("islandNb")
    NorthboundService northbound

    SwitchPairs(List<SwitchPair> switchPairs) {
        this.switchPairs = switchPairs
    }

    SwitchPairs all(Boolean includeReverse = true) {
        def allPairs = getSwitchPairsCached().collect()
        switchPairs = includeReverse ? allPairs.collectMany { [it, it.reversed] } : allPairs
        return this
    }

    SwitchPairs singleSwitch() {
        switchPairs = topology.activeSwitches.collect { SwitchPair.singleSwitchInstance(it) }
        return this
    }

    SwitchPairs withAtLeastNNonOverlappingPaths(int nonOverlappingPaths) {
        switchPairs = switchPairs.findAll {
            it.paths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size() >= nonOverlappingPaths
        }
        return this
    }

    SwitchPairs withExactlyNNonOverlappingPaths(int nonOverlappingPaths) {
        switchPairs = switchPairs.findAll {
            it.paths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size() == nonOverlappingPaths
        }
        return this
    }

    SwitchPairs withShortestPathShorterThanOthers() {
        switchPairs = switchPairs.findAll { it.getPaths()[0].size() != it.getPaths()[1].size() }
        return this
    }

    SwitchPairs withAtLeastNPaths(int minimumPathsAmount) {
        switchPairs = switchPairs.findAll { it.getPaths().size() > minimumPathsAmount }
        return this
    }

    SwitchPairs withTraffgensOnBothEnds() {
        switchPairs = switchPairs.findAll { [it.src, it.dst].every { !it.traffGens.isEmpty() } }
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
        List<SwitchPair> includeReversePairsForExclusion = []
        excludePairs.each { swPair ->
            includeReversePairsForExclusion.addAll([swPair, swPair.getReversed()])
        }
        switchPairs = switchPairs.findAll { !includeReversePairsForExclusion.contains(it) }
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

    SwitchPairs includeSourceSwitch(Switch sw) {
        switchPairs = switchPairs.findAll { it.src == sw }
        return this
    }

    SwitchPairs excludeSwitches(List<Switch> switchesList) {
        switchPairs = switchPairs.findAll { !(it.src in switchesList) && !(it.dst in switchesList) }
        return this
    }

    SwitchPairs excludeDestinationSwitches(List<Switch> switchesList) {
        switchPairs = switchPairs.findAll { it.dst !in switchesList }
        return this
    }

    List<Switch> collectSwitches() {
        switchPairs.collectMany { return [it.src, it.dst] }.unique()
    }

    SwitchPairs withAtLeastNTraffgensOnSource(int traffgensConnectedToSource) {
        switchPairs = switchPairs.findAll { it.getSrc().getTraffGens().size() >= traffgensConnectedToSource }
        return this
    }

    SwitchPairs withAtLeastNTraffgensOnDestination(int traffgensConnectedToDestination) {
        switchPairs = switchPairs.findAll { it.getDst().getTraffGens().size() >= traffgensConnectedToDestination }
        return this
    }

    SwitchPairs withPathHavingAtLeastNSwitches(int minimumSwitchesAmountInAnyPath) {
        switchPairs = switchPairs.findAll {
            it.paths.find {
                it.unique(false) { it.switchId }.size() >= minimumSwitchesAmountInAnyPath
            }
        }
        return this
    }

    SwitchPairs withBothSwitchesVxLanEnabled() {
        switchPairs = switchPairs.findAll { [it.src, it.dst].every { sw -> switchHelper.isVxlanEnabled(sw.dpId) } }
        return this
    }

    SwitchPairs withSwitchesManufacturedBy(Manufacturer srcManufacturer, Manufacturer dstManufacturer) {
        switchPairs = switchPairs.findAll {
            srcManufacturer.isSwitchMatch(it.getSrc())
                    && dstManufacturer.isSwitchMatch(it.getDst())
                    && it.hasOf13Path()
        }
        return this
    }

    SwitchPairs withSourceSwitchManufacturedBy(Manufacturer srcManufacturer) {
        switchPairs = switchPairs.findAll {
            srcManufacturer.isSwitchMatch(it.getSrc()) && it.hasOf13Path()
        }
        return this
    }

    SwitchPairs withSourceSwitchNotManufacturedBy(Manufacturer srcManufacturer) {
        switchPairs = switchPairs.findAll {
            !srcManufacturer.isSwitchMatch(it.getSrc()) && it.hasOf13Path()
        }
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

    SwitchPairs withAtLeastNIslsBetweenNeighbouringSwitches(int expectedMinimalIslsBetweenSwitches) {
        this.assertAllSwitchPairsAreNeighbouring()
        switchPairs = switchPairs.findAll {
            it.paths.findAll { it.size() == 2 }
                    .size() >= expectedMinimalIslsBetweenSwitches
        }
        return this
    }

    SwitchPairs withDestinationSwitchConnectedToServer42() {
        switchPairs = switchPairs.findAll { it.dst in topology.getActiveServer42Switches() }
        return this
    }

    SwitchPairs withOnlySourceSwitchConnectedToServer42() {
        switchPairs = switchPairs.findAll {
            it.src in topology.getActiveServer42Switches() && !(it.dst in topology.getActiveServer42Switches())
        }
        return this
    }

    SwitchPairs withBothSwitchesConnectedToServer42() {
        switchPairs = switchPairs.findAll {
            [it.dst, it.src].every { it in topology.getActiveServer42Switches() }
        }
        return this
    }

    SwitchPairs withBothSwitchesConnectedToSameServer42Instance() {
        switchPairs = switchPairs.findAll {
            it.src.prop?.server42MacAddress != null && it.src.prop?.server42MacAddress == it.dst.prop?.server42MacAddress
        }
        return this
    }

    private void assertAllSwitchPairsAreNeighbouring() {
        assert switchPairs.size() == this.neighbouring().getSwitchPairs().size(),
                "This method is applicable only to the neighbouring switch pairs"
    }

    @Memoized
    private List<SwitchPair> getSwitchPairsCached() {
        return [topology.activeSwitches, topology.activeSwitches].combinations()
                .findAll { src, dst -> src != dst } //non-single-switch
                .unique { it.sort() } //no reversed versions of same flows
                .collect { Switch src, Switch dst ->
                    new SwitchPair(src: src,
                            dst: dst,
                            paths: topologyHelper.getDbPathsCached(src.dpId, dst.dpId),
                            northboundService: northbound,
                            topologyDefinition: topology)
                }
    }


}
