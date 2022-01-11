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

    @Override
    String toString() {
        return "[$shared.name]-<$ep1.name>-<$ep2.name>"
    }

    String hwSwString() {
        return "[$shared.hwSwString]-<$ep1.hwSwString>-<$ep2.hwSwString>"
    }
}
