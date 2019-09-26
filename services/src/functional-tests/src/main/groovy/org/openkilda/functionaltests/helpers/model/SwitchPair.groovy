package org.openkilda.functionaltests.helpers.model

import org.openkilda.messaging.info.event.PathNode
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor

@TupleConstructor
@EqualsAndHashCode
class SwitchPair {
    Switch src
    Switch dst
    List<List<PathNode>> paths

    static SwitchPair singleSwitchInstance(Switch sw) {
        new SwitchPair(src: sw, dst: sw, paths: [])
    }

    @JsonIgnore
    SwitchPair getReversed() {
        new SwitchPair(src: dst, dst: src, paths: paths.collect { it.reverse() })
    }

    @Override
    String toString() {
        return "$src.dpId-$dst.dpId"
    }
}
