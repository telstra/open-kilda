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
    @Autowired
    Switches switches

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
            topology.getRelatedIsls(it.shared.switchId).size() >= islNumber
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
        def server42switches = switches.all().withS42Support().getListOfSwitches()
        switchTriplets = switchTriplets.findAll {
            it.shared in server42switches && it.ep1 in server42switches
                    && it.ep2 in server42switches
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
        singleSwitch().switchTriplets.find { it.shared.switchId == sw.dpId}

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
            it.shared.switchId == shared
                    && it.ep1.switchId == ep1
                    && it.ep2.switchId == ep2
        }
    }

    SwitchTriplet findSwitchTripletForYFlowWithProtectedPaths(boolean isYPointOnSharedEp = false) {
        return switchTriplets.find {
            def ep1paths = it.pathsEp1.findAll { path -> !path.any { node -> node.switchId == it.ep2.switchId } }
                    .unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }
            def ep2paths = it.pathsEp2.findAll { path -> !path.any { node -> node.switchId == it.ep1.switchId } }
                    .unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }
            def yPoints = it.findPotentialYPoints()

            yPoints.size() == 1 && (isYPointOnSharedEp ? yPoints[0] == it.shared.switchId : yPoints[0] != it.shared.switchId)
                    && yPoints[0] != it.ep1.switchId && yPoints[0] != it.ep2.switchId
                    && ep1paths.size() >= 2 && ep2paths.size() >= 2
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
            it.findPotentialYPoints().size() == 1 && it.shared.switchId == it.findPotentialYPoints().get(0)
        }
    }

    SwitchTriplet findSwitchTripletWithSharedEpThatIsNotNeighbourToEp1AndEp2() {
        return switchTriplets.find {
            it.pathsEp1[0].size() > 2 && it.pathsEp2[0].size() > 2 }
    }


    SwitchTriplet findSwitchTripletWithOnlySharedSwS42Support() {
        def server42switches = switches.all().withS42Support().getListOfSwitches()
        return switchTriplets.find {
            it.shared in server42switches
                    && !(it.ep1 in server42switches)
                    && !(it.ep2 in server42switches)
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
        return [switches.all().getListOfSwitches(), switches.all().getListOfSwitches(), switches.all().getListOfSwitches()].combinations()
                .findAll { shared, ep1, ep2 -> includeSingleSwitch || shared != ep1 && shared != ep2 } //non-single-switch
                .unique { triplet -> [triplet[0], [triplet[1], triplet[2]].sort()] } //no reversed versions of ep1/ep2
                .collect { SwitchExtended shared, SwitchExtended ep1, SwitchExtended ep2 ->
                    new SwitchTriplet(
                            shared: shared,
                            ep1: ep1,
                            ep2: ep2,
                            pathsEp1: retrievePairPathNode(shared.switchId, ep1.switchId),
                            pathsEp2: retrievePairPathNode(shared.switchId, ep2.switchId),
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
