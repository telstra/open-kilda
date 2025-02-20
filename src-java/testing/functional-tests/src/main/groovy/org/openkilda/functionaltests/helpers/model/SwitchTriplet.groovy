package org.openkilda.functionaltests.helpers.model

import static org.openkilda.functionaltests.helpers.TopologyHelper.convertToPathNodePayload

import org.openkilda.messaging.info.event.PathNode
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.EqualsAndHashCode
import groovy.transform.Memoized
import groovy.transform.TupleConstructor

@TupleConstructor
@EqualsAndHashCode
class SwitchTriplet {
    SwitchExtended shared
    SwitchExtended ep1
    SwitchExtended ep2
    List<List<PathNode>> pathsEp1
    List<List<PathNode>> pathsEp2

    @JsonIgnore
    TopologyDefinition topology

    @JsonIgnore
    SwitchTriplet getReversed() {
        new SwitchTriplet(shared, ep2, ep1, pathsEp2, pathsEp1)
    }

    @JsonIgnore
    Boolean isTraffExamAvailable() {
        return !(shared.traffGenPorts.isEmpty() || ep1.traffGenPorts.isEmpty() || ep2.traffGenPorts.isEmpty())
    }

    @JsonIgnore
    Boolean isSingleSwitch() {
        return shared == ep1 && ep1 == ep2
    }

    @Override
    String toString() {
        return "[$shared.name]-<$ep1.name>-<$ep2.name>"
    }

    String hwSwString() {
        return "[${shared.hwSwString()}]-<${ep1.hwSwString()}>-<${ep2.hwSwString()}>"
    }

    static Closure ALL_ENDPOINTS_DIFFERENT = {
        SwitchTriplet swT -> swT.ep1 != swT.ep2 && swT.ep1 != swT.shared && swT.ep2 != swT.shared
    }

    static Closure ONE_SUB_FLOW_IS_ONE_SWITCH_FLOW = {
        SwitchTriplet swT -> swT.shared == swT.ep1 && swT.shared != swT.ep2
    }

    static Closure ONE_SWITCH_TRIPLET = {
        SwitchTriplet swT -> swT.shared == swT.ep1 && swT.shared == swT.ep2
    }

    static Closure NOT_WB_ENDPOINTS = {
        SwitchTriplet swT -> !swT.shared.isWb5164() && !swT.ep1.isWb5164() && !swT.ep2.isWb5164()
    }

    static Closure ONLY_ONE_EP_IS_NEIGHBOUR_TO_SHARED_EP = {
        SwitchTriplet swT ->
            def areEp1Ep2AndEp1OrEp2AndShEpNeighbour = null
            if (swT.pathsEp1[0].size() == 2 && swT.pathsEp2[0].size() > 2) {
                //both pair sh_ep+ep1 and ep1+ep2 are neighbours, sh_ep and ep2 is not neighbour
                areEp1Ep2AndEp1OrEp2AndShEpNeighbour = swT.pathsEp2.find { ep2Path -> ep2Path.containsAll(swT.pathsEp1[0]) && ep2Path.size() == 4 }
            } else if (swT.pathsEp2[0].size() == 2 && swT.pathsEp1[0].size() > 2) {
                //both pair sh_ep+ep2 and ep2+ep1 are neighbours, sh_ep and ep1 is not neighbour
                areEp1Ep2AndEp1OrEp2AndShEpNeighbour = swT.pathsEp1.find { ep1Path -> ep1Path.containsAll(swT.pathsEp2[0]) && ep1Path.size() == 4 }
            }
            areEp1Ep2AndEp1OrEp2AndShEpNeighbour
    }

    @Memoized
    List<SwitchId> findPotentialYPoints() {
        def sortedEp1Paths = pathsEp1.sort { it.size() }
        def potentialEp1Paths = sortedEp1Paths.takeWhile { it.size() == sortedEp1Paths[0].size() }
        def potentialEp2Paths = potentialEp1Paths.collect { potentialEp1Path ->
            def sortedEp2Paths = pathsEp2.sort {
                it.size() - it.intersect(potentialEp1Path).size()
            }
            [path1: potentialEp1Path,
             potentialPaths2: sortedEp2Paths.takeWhile {it.size() == sortedEp2Paths[0].size() }]
        }
        return potentialEp2Paths.collectMany {path1WithPath2 ->
            path1WithPath2.potentialPaths2.collect { List<PathNode> potentialPath2 ->
                def switches = path1WithPath2.path1.switchId
                        .intersect(potentialPath2.switchId)
                switches ? switches[-1] : null
            }
        }.findAll().unique()
    }

    List<Path> retrieveAvailablePathsEp1(){
        convertToPathNodePayload(pathsEp1).collect{
            new Path(it, topology)
        }
    }
    List<Path> retrieveAvailablePathsEp2(){
        convertToPathNodePayload(pathsEp2).collect{
            new Path(it, topology)
        }
    }
}
