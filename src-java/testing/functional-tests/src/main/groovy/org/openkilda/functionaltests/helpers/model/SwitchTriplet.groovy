package org.openkilda.functionaltests.helpers.model

import org.openkilda.messaging.info.event.PathNode
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
    SwitchTriplet getReversed() {
        new SwitchTriplet(shared, ep2, ep1, pathsEp2, pathsEp1)
    }

    @JsonIgnore
    Boolean isHaTraffExamAvailable() {
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

    static Closure ONE_SWITCH_FLOW = {
        SwitchTriplet swT -> swT.shared == swT.ep1 && swT.shared == swT.ep2
    }

    static Closure TRAFFGEN_CAPABLE = { SwitchTriplet swT ->
        !(swT.ep1.getTraffGens().isEmpty() || swT.ep2.getTraffGens().isEmpty() || swT.shared.getTraffGens().isEmpty())
    }

    static Closure NOT_WB_ENDPOINTS = {
        SwitchTriplet swT -> !swT.shared.wb5164 && !swT.ep1.wb5164 && !swT.ep2.wb5164
    }
}
