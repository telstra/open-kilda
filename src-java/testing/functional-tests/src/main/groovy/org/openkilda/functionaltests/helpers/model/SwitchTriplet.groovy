package org.openkilda.functionaltests.helpers.model

import static org.openkilda.functionaltests.helpers.TopologyHelper.convertToPathNodePayload

import org.openkilda.messaging.info.event.PathNode
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor

@TupleConstructor
@EqualsAndHashCode
class SwitchTriplet {
    Switch shared
    Switch ep1
    Switch ep2
    List<List<PathNode>> pathsEp1
    List<List<PathNode>> pathsEp2

    @JsonIgnore
    TopologyDefinition topologyDefinition

    @JsonIgnore
    SwitchTriplet getReversed() {
        new SwitchTriplet(shared, ep2, ep1, pathsEp2, pathsEp1)
    }

    @JsonIgnore
    Boolean isTraffExamAvailable() {
        return !(shared.getTraffGens().isEmpty() || ep1.getTraffGens().isEmpty() || ep2.getTraffGens().isEmpty())
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
        return "[$shared.hwSwString]-<$ep1.hwSwString>-<$ep2.hwSwString>"
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
        SwitchTriplet swT -> !swT.shared.wb5164 && !swT.ep1.wb5164 && !swT.ep2.wb5164
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

    List<Path> retrieveAvailablePathsEp1(){
        convertToPathNodePayload(pathsEp1).collect{
            new Path(it, topologyDefinition)
        }
    }
    List<Path> retrieveAvailablePathsEp2(){
        convertToPathNodePayload(pathsEp2).collect{
            new Path(it, topologyDefinition)
        }
    }
}
