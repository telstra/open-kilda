package org.openkilda.functionaltests.helpers.model

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.messaging.info.event.PathNode
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import static org.junit.jupiter.api.Assumptions.assumeFalse

import groovy.transform.Memoized
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

/**
 * Class which simplifies search for corresponding switch triplet. Just chain existing methods to combine requirements
 * Usage: switchTriplets.all()
 * .nonNeighbouring()
 * .withTraffgensOnEachEnd()
 * .random()
 * Also it eliminates need to verify if no switch can be found (skips test immediately)
 */

@Component
@Scope(SCOPE_PROTOTYPE)
class SwitchTriplets {
    List<SwitchTriplet> switchTriplets
    @Autowired
    TopologyDefinition topology

    SwitchTriplets(List<SwitchTriplet> switchTriplets) {
        this.switchTriplets = switchTriplets
    }

    SwitchTriplets all(boolean includeReverse = false, boolean includeSingleSwitch = false) {
        def allTriplets = getSwitchTripletsCached(includeSingleSwitch).collect()
        switchTriplets = includeReverse ? allTriplets.collectMany { [it, it.reversed] } : allTriplets
        return this
    }

   SwitchTriplets nonNeighbouring() {
        switchTriplets = switchTriplets.findAll {
            it.shared != it.ep1 && it.ep1 != it.ep2 && it.ep2 != it.shared &&
                    it.pathsEp1.every {it.size() > 2} &&
                    it.pathsEp2.every {it.size() > 2}}

       return this
    }

    SwitchTriplets withTraffgensOnEachEnd() {
        switchTriplets = switchTriplets.findAll { it.isTraffExamAvailable()}
        return this
    }

    SwitchTriplets withAtLeastNNonOverlappingPaths(int nonOverlappingPaths) {
        switchTriplets = switchTriplets.findAll {
            [it.pathsEp1, it.pathsEp2].every {
                        it.unique(false) { a, b -> a.intersect(b) ? 0 : 1 }.size() >= nonOverlappingPaths
            }
        }
        return this
    }

    SwitchTriplets withAtLeastNIslOnSharedEndpoint(int islNumber) {
        switchTriplets = switchTriplets.findAll {
            topology.getRelatedIsls(it.shared).size() >= 5
        }
        return this
    }

    SwitchTriplets withAllDifferentEndpoints() {
        switchTriplets = switchTriplets.findAll { SwitchTriplet.ALL_ENDPOINTS_DIFFERENT(it) }
        return this
    }

    SwitchTriplets withoutWBSwitch() {
        switchTriplets = switchTriplets.findAll { SwitchTriplet.NOT_WB_ENDPOINTS(it) }
        return this
    }

    SwitchTriplets withS42Support() {
        def server42switches = topology.getActiveServer42Switches()
        switchTriplets = switchTriplets.findAll {
            it.shared.dpId in server42switches.dpId && it.ep1.dpId in server42switches.dpId
                    && it.ep2.dpId in server42switches.dpId
        }
        return this
    }

    SwitchTriplets withSharedEpEp1Ep2InChain() {
        switchTriplets = switchTriplets.findAll {
            SwitchTriplet.ONLY_ONE_EP_IS_NEIGHBOUR_TO_SHARED_EP(it)
        }
        return this
    }

    SwitchTriplets withSharedEpInTheMiddleOfTheChain() {
        switchTriplets = switchTriplets.findAll {
            (it.pathsEp1[0].size() == 2 && it.pathsEp2[0].size() == 2)
        }
        return this
    }


    SwitchTriplet random() {
        switchTriplets.shuffle()
        return switchTriplets.first()
    }

    SwitchTriplet first() {
        assumeFalse(switchTriplets.isEmpty(), "No suiting switch triplet found")
        return switchTriplets.first()
    }

    SwitchTriplet withSpecificSingleSwitch(Switch sw) {
        singleSwitch().switchTriplets.find { it.shared.dpId == sw.dpId}

    }

    SwitchTriplets singleSwitch() {
        switchTriplets = switchTriplets.findAll { SwitchTriplet.ONE_SWITCH_TRIPLET(it) }
        return this
    }

    SwitchTriplet findSwitchTripletForOneSwitchSubflow() {
        return switchTriplets.find {
            SwitchTriplet.ONE_SUB_FLOW_IS_ONE_SWITCH_FLOW(it)
        }
    }

    SwitchTriplet findSpecificSwitchTriplet(SwitchId shared, SwitchId ep1, SwitchId ep2) {
        return switchTriplets.find {
            it.shared.getDpId() == shared
                    && it.ep1.getDpId() == ep1
                    && it.ep2.getDpId() == ep2
        }
    }

    SwitchTriplet findSwitchTripletForYFlowWithProtectedPaths(boolean isYPointOnSharedEp = false) {
        return switchTriplets.find {
            def ep1paths = it.pathsEp1.findAll { path -> !path.any { node -> node.switchId == it.ep2.dpId } }
                    .unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }
            def ep2paths = it.pathsEp2.findAll { path -> !path.any { node -> node.switchId == it.ep1.dpId } }
                    .unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }
            def yPoints = it.findPotentialYPoints()

            yPoints.size() == 1 && (isYPointOnSharedEp ? yPoints[0] == it.shared.dpId : yPoints[0] != it.shared.dpId) && yPoints[0] != it.ep1.dpId && yPoints[0] != it.ep2.dpId &&
                    ep1paths.size() >= 2 && ep2paths.size() >= 2
        }
    }

    SwitchTriplet findSwitchTripletForHaFlowWithProtectedPaths() {
        return withAllDifferentEndpoints().switchTriplets.find {
            def pathsCombinations = [it.pathsEp1, it.pathsEp2].combinations()
            for (i in 0..<pathsCombinations.size()) {
                for (j in i + 1..<pathsCombinations.size()) {
                    if (!areHaPathsIntersect(pathsCombinations[i], pathsCombinations[j])) {
                        return true
                    }
                }
            }
            return false
        }
    }

    SwitchTriplet findSwitchTripletWithAlternativeFirstPortPaths() {
        return withAllDifferentEndpoints().switchTriplets.find {
            def firstIslPorts = it.pathsEp1*.first().portNo as Set
            firstIslPorts.addAll(it.pathsEp2*.first().portNo)
            return firstIslPorts.size() > 1
        }
    }

    SwitchTriplet findSwitchTripletWithAlternativePaths() {
        return withAllDifferentEndpoints().switchTriplets.find {
            return it.pathsEp1.size() > 1 || it.pathsEp2.size() > 1
        }
    }

    SwitchTriplet findSwitchTripletWithYPointOnSharedEp() {
        return switchTriplets.find {
            it.findPotentialYPoints().size() == 1 && it.shared.dpId == it.findPotentialYPoints().get(0)
        }
    }

    SwitchTriplet findSwitchTripletWithSharedEpThatIsNotNeighbourToEp1AndEp2() {
        return switchTriplets.find {
            it.pathsEp1[0].size() > 2 && it.pathsEp2[0].size() > 2 }
    }


    SwitchTriplet findSwitchTripletWithOnlySharedSwS42Support() {
        def server42switches = topology.getActiveServer42Switches()
        return switchTriplets.find {
            it.shared.dpId in server42switches.dpId
                    && !(it.ep1.dpId in server42switches.dpId)
                    && !(it.ep2.dpId in server42switches.dpId)
        }
    }

    private static boolean areHaPathsIntersect(subPaths1, subPaths2) {
        for (List<PathNode> subPath1 : subPaths1) {
            for (List<PathNode> subPath2 : subPaths2) {
                if (subPaths1.intersect(subPaths2)) {
                    return true
                }
            }
        }
        return false
    }


    @Memoized
    private List<SwitchTriplet> getSwitchTripletsCached(boolean includeSingleSwitch) {
        return [topology.activeSwitches, topology.activeSwitches, topology.activeSwitches].combinations()
                .findAll { shared, ep1, ep2 -> includeSingleSwitch || shared != ep1 && shared != ep2 } //non-single-switch
                .unique { triplet -> [triplet[0], [triplet[1], triplet[2]].sort()] } //no reversed versions of ep1/ep2
                .collect { Switch shared, Switch ep1, Switch ep2 ->
                    new SwitchTriplet(
                            shared: shared,
                            ep1: ep1,
                            ep2: ep2,
                            pathsEp1: retrievePairPathNode(shared.dpId, ep1.dpId),
                            pathsEp2: retrievePairPathNode(shared.dpId, ep2.dpId),
                            topology: topology)
                }
    }

    private retrievePairPathNode(SwitchId srcId, SwitchId dstId) {
        String pair = "$srcId-$dstId"
        String reversePair = "$dstId-$srcId"
        topology.switchesDbPathsNodes.containsKey(pair) ? topology.switchesDbPathsNodes.get(pair) :
                topology.switchesDbPathsNodes.get(reversePair).collect { it.reverse() }
    }
}
