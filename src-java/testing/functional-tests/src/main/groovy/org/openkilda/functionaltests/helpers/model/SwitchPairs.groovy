package org.openkilda.functionaltests.helpers.model

import static org.junit.jupiter.api.Assumptions.assumeFalse
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.helpers.TopologyHelper
import org.openkilda.functionaltests.model.switches.Manufacturer
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService

import groovy.transform.Memoized
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

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
    TopologyHelper topologyHelper
    @Autowired
    TopologyDefinition topology
    @Autowired
    @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired
    Switches switches

    SwitchPairs(List<SwitchPair> switchPairs) {
        this.switchPairs = switchPairs
    }

    SwitchPairs all(Boolean includeReverse = true) {
        def allPairs = getSwitchPairsCached().collect()
        switchPairs = includeReverse ? allPairs.collectMany { [it, it.reversed] } : allPairs
        return this
    }

    SwitchPairs singleSwitch() {
        switchPairs = switches.all().getListOfSwitches().collect { SwitchPair.singleSwitchInstance(it) }
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
        switchPairs = switchPairs.findAll { it.paths[0].size() != it.paths[1].size() }
        return this
    }

    SwitchPairs withAtLeastNPaths(int minimumPathsAmount) {
        switchPairs = switchPairs.findAll { it.paths.size() > minimumPathsAmount }
        return this
    }

    SwitchPairs withoutOf12Switches() {
        switchPairs = switchPairs.findAll { !it.src.isOf12Version() && !it.dst.isOf12Version() }
        return this
    }

    SwitchPairs withTraffgensOnBothEnds() {
        switchPairs = switchPairs.findAll { [it.src, it.dst].every { !it.traffGenPorts.isEmpty() } }
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

    SwitchPairs includeSwitch(SwitchExtended sw) {
        switchPairs = switchPairs.findAll { it.src == sw || it.dst == sw }
        return this
    }

    SwitchPair specificPair(SwitchExtended source, SwitchExtended destination) {
        switchPairs = switchPairs.findAll { it.src == source && it.dst == destination }
        return switchPairs.first()
    }

    SwitchPair specificPair(SwitchId srcId, SwitchId dstId) {
        switchPairs = switchPairs.findAll { it.src.switchId == srcId && it.dst.switchId == dstId }
        return switchPairs.first()
    }

    SwitchPairs includeSourceSwitch(SwitchExtended sw) {
        switchPairs = switchPairs.findAll { it.src == sw }
        return this
    }

    SwitchPairs includeDestinationSwitch(SwitchExtended sw) {
        switchPairs = switchPairs.findAll { it.dst == sw }
        return this
    }

    SwitchPairs excludeSwitches(List<SwitchExtended> switchesList) {
        switchPairs = switchPairs.findAll { !(it.src in switchesList) && !(it.dst in switchesList) }
        return this
    }

    SwitchPairs excludeDestinationSwitches(List<SwitchExtended> switchList) {
        switchPairs = switchPairs.findAll { it.dst !in switchList }
        return this
    }

    List<SwitchExtended> collectSwitches() {
        switchPairs.collectMany { return [it.src, it.dst] }.unique()
    }

    SwitchPairs withAtLeastNTraffgensOnSource(int traffgensConnectedToSource) {
        switchPairs = switchPairs.findAll { it.getSrc().traffGenPorts.size() >= traffgensConnectedToSource }
        return this
    }

    SwitchPairs withAtLeastNTraffgensOnDestination(int traffgensConnectedToDestination) {
        switchPairs = switchPairs.findAll { it.dst.traffGenPorts.size() >= traffgensConnectedToDestination }
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
        switchPairs = switchPairs.findAll { [it.src, it.dst].every { sw -> sw.isVxlanEnabled() } }
        return this
    }

    SwitchPairs withSwitchesManufacturedBy(Manufacturer srcManufacturer, Manufacturer dstManufacturer) {
        def switchIdsWithOF12 = switches.all().getListOfSwitches()
                .findAll { it.isOf12Version() }.switchId

        switchPairs = switchPairs.findAll {
            srcManufacturer.isSwitchMatch(it.src)
                    && dstManufacturer.isSwitchMatch(it.dst)
                    && (switchIdsWithOF12.isEmpty() || !it.possibleDefaultPaths()
                    .find { it[1..-2].every { it.switchId in switchIdsWithOF12 }})
        }
        return this
    }

    SwitchPairs withSourceSwitchManufacturedBy(Manufacturer srcManufacturer) {
        def switchIdsWithOF12 = switches.all().getListOfSwitches()
                .findAll { it.isOf12Version() }.switchId
        switchPairs = switchPairs.findAll {
            srcManufacturer.isSwitchMatch(it.src)
                    && (switchIdsWithOF12.isEmpty() || !it.possibleDefaultPaths()
                    .find { it[1..-2].every { it.switchId in switchIdsWithOF12 }})
        }
        return this
    }

    SwitchPairs withSourceSwitchNotManufacturedBy(Manufacturer srcManufacturer) {
        def notOF12VersionSwIds = switches.all().notOF12Version().switchId
        switchPairs = switchPairs.findAll {
            !srcManufacturer.isSwitchMatch(it.src)
                    && it.possibleDefaultPaths().find { it[1..-2].every{ it.switchId in notOF12VersionSwIds }}
        }
        return this
    }

    SwitchPairs withIslRttSupport() {
        this.assertAllSwitchPairsAreNeighbouring()
        switchPairs = switchPairs.findAll { [it.src, it.dst].every { it.isRtlSupported() } }
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
        def s42Switches = switches.all().withS42Support().getListOfSwitches()
        switchPairs = switchPairs.findAll { it.dst in s42Switches }
        return this
    }

    SwitchPairs withOnlySourceSwitchConnectedToServer42() {
        def s42Switches = switches.all().withS42Support().getListOfSwitches()
        switchPairs = switchPairs.findAll {
            it.src in s42Switches && !(it.dst in s42Switches)
        }
        return this
    }

    SwitchPairs withBothSwitchesConnectedToServer42() {
        def s42Switches = switches.all().withS42Support().getListOfSwitches()
        switchPairs = switchPairs.findAll {
            [it.dst, it.src].every { it in s42Switches }
        }
        return this
    }

    SwitchPairs withBothSwitchesConnectedToSameServer42Instance() {
        switchPairs = switchPairs.findAll {
            it.src.sw.prop?.server42MacAddress != null && it.src.sw.prop?.server42MacAddress == it.dst.sw.prop?.server42MacAddress
        }
        return this
    }

    SwitchPairs withoutWBSwitch() {
        switchPairs = switchPairs.findAll { SwitchPair.NOT_WB_ENDPOINTS(it) }
        return this
    }

    private void assertAllSwitchPairsAreNeighbouring() {
        assert switchPairs.size() == this.neighbouring().getSwitchPairs().size(),
                "This method is applicable only to the neighbouring switch pairs"
    }

    @Memoized
    private List<SwitchPair> getSwitchPairsCached() {
        return [switches.all().getListOfSwitches(), switches.all().getListOfSwitches()].combinations()
                .findAll { src, dst -> src != dst } //non-single-switch
                .unique { it.sort() } //no reversed versions of same flows
                .collect { SwitchExtended src, SwitchExtended dst ->
                    String pair = "${src.switchId}-${dst.switchId}"
                    String reversePair = "${dst.switchId}-${src.switchId}"
                    def pathsBetweenPair = topology.switchesDbPathsNodes.containsKey(pair) ?
                            topology.switchesDbPathsNodes.get(pair) :
                            topology.switchesDbPathsNodes.get(reversePair).collect { it.reverse() }
                    new SwitchPair(src: src,
                            dst: dst,
                            paths: pathsBetweenPair,
                            northboundService: northbound,
                            topologyDefinition: topology)
                }
    }
}
