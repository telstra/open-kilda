package org.openkilda.functionaltests.helpers

import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@Slf4j
class TopologyHelper {

    @Autowired
    TopologyDefinition topology
    @Autowired
    Database database

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
            it.paths.min { it.size() }.size() == 2
        }
    }

    SwitchPair getNotNeighboringSwitchPair() {
        getSwitchPairs().find {
            it.paths.min { it.size() }.size() > 2
        }
    }

    List<SwitchPair> getAllNeighboringSwitchPairs() {
        getSwitchPairs().findAll {
            it.paths.min { it.size() }.size() == 2
        }
    }

    List<SwitchPair> getAllNotNeighboringSwitchPairs() {
        getSwitchPairs().findAll {
            it.paths.min { it.size() }.size() > 2
        }
    }

    List<SwitchPair> getSwitchPairs() {
        //get deep copy
        def mapper = new ObjectMapper()
        return mapper.readValue(mapper.writeValueAsString(getSwitchPairsCached()), SwitchPair[]).toList()
    }

    @Memoized
    private List<SwitchPair> getSwitchPairsCached() {
        return [topology.activeSwitches, topology.activeSwitches].combinations()
                .findAll { src, dst -> src != dst } //non-single-switch
                .unique { it.sort() } //no reversed versions of same flows
                .collect { Switch src, Switch dst ->
            new SwitchPair(src: src, dst: dst, paths: database.getPaths(src.dpId, dst.dpId)*.path)
        }
    }
}
