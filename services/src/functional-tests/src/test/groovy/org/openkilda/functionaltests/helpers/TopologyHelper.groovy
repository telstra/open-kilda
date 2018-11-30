package org.openkilda.functionaltests.helpers

import org.openkilda.messaging.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.tools.IslUtils

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
        List<SwitchId> destinations = []
        if (passedNodes.size() == topo*.srcSwitch.dpId.unique().size()) { //no more switches left in topology
            return []
        }
        if (dist == 0) { //search has reached the required distance
            return [src]
        } else { //if not, look deeper in the topology
            passedNodes.add(src) //shortest path to the switch is shorter than required - remove from the results
            topo.findAll { it.srcSwitch.dpId == src }.unique { it.dstSwitch.dpId }.each {
                destinations.addAll(findPaths(topo, it.dstSwitch.dpId, dist - 1, passedNodes))
            }
            return destinations - passedNodes
        }

    }

    /**
     * Finds all pairs of switches with a given minimum distance (in hops) between them
     * @param topo List<Isl>
     * @param dist - distance (N = n-1 transit switches, 1 = neighbouring, 0 = same switch)
     * @return List < List < Switch > > - lists with source/destination pairs
     */
    List<List<Switch>> getSwitchPairs(List<Isl> topo, int dist) {
        IslUtils u = new IslUtils()
        def switches = (topo*.srcSwitch + topo*.dstSwitch).unique { it.dpId }
        def reverseTopo = []
        topo.each { reverseTopo.add(u.reverseIsl(it)) } //TODO: find a way to make this more elegant
        List<List<Switch>> switchPairs = []

        switches.each { sw ->
            def destinations = findPaths(topo + reverseTopo, sw.dpId, dist)
            destinations.each { dstDpId ->
                switchPairs.add([sw, switches.find { it.dpId == dstDpId }])
            }
        }

        def uniquePairs = []
        switchPairs.each {
            if (!([it, it.reverse()].intersect(uniquePairs))) { //get rid of permutations
                uniquePairs.add(it)
            }
        }
        return uniquePairs
    }
}
