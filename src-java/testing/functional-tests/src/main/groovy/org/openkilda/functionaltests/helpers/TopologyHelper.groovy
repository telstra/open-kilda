package org.openkilda.functionaltests.helpers

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Status
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.model.topology.TopologyDefinition.TraffGenConfig
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.floodlight.FloodlightsHelper
import org.openkilda.testing.service.northbound.NorthboundService

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
@Slf4j
@Scope(SCOPE_PROTOTYPE)
class TopologyHelper {
    @Autowired @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired @Qualifier("northboundServiceImpl")
    NorthboundService nb
    @Autowired
    TopologyDefinition topology
    @Autowired
    Database database
    @Autowired
    FloodlightsHelper flHelper

    /**
     * Get a switch pair of random switches.
     *
     * @param forceDifferent whether to exclude the picked src switch when looking for dst switch
     */
    Tuple2<Switch, Switch> getRandomSwitchPair(boolean forceDifferent = true) {
        def randomSwitch = { List<Switch> switches ->
            switches[new Random().nextInt(switches.size())]
        }
        def src = randomSwitch(topology.activeSwitches)
        def dst = randomSwitch(forceDifferent ? topology.activeSwitches - src : topology.activeSwitches)
        return new Tuple2(src, dst)
    }

    SwitchPair getSingleSwitchPair() {
        return SwitchPair.singleSwitchInstance(topology.activeSwitches.first())
    }

    List<SwitchPair> getAllSingleSwitchPairs() {
        return topology.activeSwitches.collect { SwitchPair.singleSwitchInstance(it) }
    }

    SwitchPair getNeighboringSwitchPair() {
        getSwitchPairs().find {
            it.paths.min { it.size() }?.size() == 2
        }
    }

    SwitchPair getNotNeighboringSwitchPair() {
        getSwitchPairs().find {
            it.paths.min { it.size() }?.size() > 2
        }
    }

    List<SwitchPair> getAllNeighboringSwitchPairs() {
        getSwitchPairs().findAll {
            it.paths.min { it.size() }?.size() == 2
        }
    }

    List<SwitchPair> getAllNotNeighboringSwitchPairs() {
        getSwitchPairs().findAll {
            it.paths.min { it.size() }?.size() > 2
        }
    }

    def traffgenEnabled = { SwitchPair swPair ->
        def tgSwitches = topology.activeTraffGens*.switchConnected
        swPair.src in tgSwitches && swPair.dst in tgSwitches
    }

    List<SwitchPair> getSwitchPairs(boolean includeReverse = false) {
        //get deep copy
        def mapper = new ObjectMapper()
        def result = mapper.readValue(mapper.writeValueAsString(getSwitchPairsCached()), SwitchPair[]).toList()
        return includeReverse ? result.collectMany { [it, it.reversed] } : result
    }

    List<SwitchTriplet> getSwitchTriplets(boolean includeReverse = false, boolean includeSingleSwitch = false) {
        //get deep copy
        def mapper = new ObjectMapper()
        def result = mapper.readValue(mapper.writeValueAsString(getSwitchTripletsCached(includeSingleSwitch)), SwitchTriplet[]).toList()
        return includeReverse ? result.collectMany { [it, it.reversed] } : result
    }

    TopologyDefinition readCurrentTopology() {
        readCurrentTopology(false)
    }

    TopologyDefinition readCurrentTopology(Boolean generateTopology) {
        def switches = generateTopology ? nb.getAllSwitches() : northbound.getAllSwitches()
        def links = generateTopology ? nb.getAllLinks() : northbound.getAllLinks()
        def i = 0
        def switchIdsPerRegion = flHelper.fls.collectEntries {
            [(it.region): it.floodlightService.getSwitches()*.switchId] }
        def topoSwitches = switches.collect { sw ->
            i++
            List<String> applicableRegions = switchIdsPerRegion.findAll { it.value.contains(sw.switchId) }*.key
            new Switch("ofsw$i", sw.switchId, sw.ofVersion, switchStateToStatus(sw.state), applicableRegions, [],
                    null, null, null)
        }
        def topoLinks = links.collect { link ->
            new Isl(topoSwitches.find { it.dpId == link.source.switchId }, link.source.portNo,
                    topoSwitches.find { it.dpId == link.destination.switchId }, link.destination.portNo,
                    link.maxBandwidth, null)
        }.unique { a, b -> a == b || a == b.reversed ? 0 : 1 }

        return new TopologyDefinition(topoSwitches, topoLinks, [], TraffGenConfig.defaultConfig())
    }

    private static Status switchStateToStatus(SwitchChangeType state) {
        switch (state) {
            case SwitchChangeType.ACTIVATED:
                return Status.Active
            default:
                return Status.Inactive
        }
    }

    @Memoized
    private List<SwitchPair> getSwitchPairsCached() {
        return [topology.activeSwitches, topology.activeSwitches].combinations()
                .findAll { src, dst -> src != dst } //non-single-switch
                .unique { it.sort() } //no reversed versions of same flows
                .collect { Switch src, Switch dst ->
                    new SwitchPair(src: src, dst: dst, paths: getDbPathsCached(src.dpId, dst.dpId))
                }
    }

    @Memoized
    private List<SwitchTriplet> getSwitchTripletsCached(boolean includeSingleSwitch) {
        return [topology.activeSwitches, topology.activeSwitches, topology.activeSwitches].combinations()
                .findAll { shared, ep1, ep2 -> includeSingleSwitch || shared != ep1 && shared != ep2 } //non-single-switch
                .unique { triplet -> [triplet[0], [triplet[1], triplet[2]].sort()] } //no reversed versions of ep1/ep2
                .collect { Switch shared, Switch ep1, Switch ep2 ->
                    new SwitchTriplet(shared, ep1, ep2, getDbPathsCached(shared.dpId, ep1.dpId), getDbPathsCached(shared.dpId, ep2.dpId))
                }
    }

    @Memoized
    List<List<PathNode>> getDbPathsCached(SwitchId src, SwitchId dst) {
        database.getPaths(src, dst)*.path
    }
}
