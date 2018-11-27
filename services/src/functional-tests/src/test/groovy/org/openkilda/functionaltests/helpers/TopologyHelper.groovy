package org.openkilda.functionaltests.helpers

import org.openkilda.messaging.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Component
@Slf4j
/**
 * A helper class for the advanced path search operations
 */
class TopologyHelper {

    /**
     * Finds all shortest paths of given length starting at the specified source
     * Unoptimal paths (with extra hops) will be ignored
     * For example, if we are looking for paths with source = 3 and distance = 5 in the following topology:
     *  1 - 2 - 5
     *  |      / \
     *  3     6 - 7 - 8
     * We will only get this path:
     * 3 - 1 - 2 - 5 - 7 - 8
     * But not this one:
     * 3 - 1 - 2 - 5 - 6 - 7, as shorter path 3 - 1 - 2 - 5 - 7 exists
     *
     * @param topo - List<Isl>
     * @param src - Source SwitchId
     * @param dist - distance (1 equals to neighbouring switches, 0 equals to same switch, N means N-1 transit switches)
     * @param passedNodes - List<SwitchId> to exclude from the path computation
     * @return List < SwitchId >  list of possible destination switches
     */
    private findPaths(List<Isl> topo, SwitchId src, int dist, List<SwitchId> passedNodes = []) {
        def possibleDestinations = []
        passedNodes.add(src) //don't go back
        def nextHops = []
        if (dist > 1) { //if there is still way to go
            topo.each { //finding all links which are connected to source switch with an unvisited destinations
                if ((it.srcSwitch.dpId == src) && (!passedNodes.contains(it.dstSwitch.dpId))) {
                    nextHops.add(it.dstSwitch.dpId)
                    passedNodes.add(src)
                }
            }
            def candidates = topo.findAll { nextHops.unique().contains(it.srcSwitch.dpId) }. //where to go next
                    findAll { !(passedNodes.contains(it.srcSwitch.dpId) || passedNodes.contains(it.dstSwitch.dpId)) }
            candidates.unique { it.dstSwitch.dpId }.each { //we need to go deeper - analyzing the next hops recursively
                possibleDestinations.addAll(findPaths(topo, it.srcSwitch.dpId, dist - 1, passedNodes))
            }

            return possibleDestinations.unique().findAll { !nextHops.contains(it) } //filter out cyclic references
        } else if (dist == 1) { //hit the bottom of recursion - looking for a neighbouring link to destination
            topo.each {
                if (!nextHops.contains(it.dstSwitch.dpId)) { //looking for all neighbouring links
                    if (it.srcSwitch.dpId == src) {
                        possibleDestinations.add(it.dstSwitch.dpId)
                    }
                }
            }
            return possibleDestinations.unique().findAll() { !passedNodes.contains(it) } //filtering out cycles
        } else if (dist == 0) {
            return [src]
        }
    }

    /**
     * Finds all pairs of switches
     * @param topo List<Isl>
     * @param dist - distance (N = n-1 transit switches, 1 = neighbouring, 0 = same switch)
     * @return List < List < Switch > > - lists with source/destination pairs
     */
    List<List<Switch>> findSwitchPairs(List<Isl> topo, int dist) {
        def switches = topo*.srcSwitch.unique { it.dpId }
        def switchPairs = []
        topo*.srcSwitch.unique { it.dpId }.each { sw ->
            def destinations = findPaths(topo, sw.dpId, dist)
            destinations.each { dstDpId ->
                switchPairs.add([sw, switches.find { it.dpId == dstDpId }])
            }
        }
        return switchPairs
    }

}
