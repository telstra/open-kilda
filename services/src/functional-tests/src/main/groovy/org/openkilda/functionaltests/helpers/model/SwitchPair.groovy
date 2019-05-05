package org.openkilda.functionaltests.helpers.model

import org.openkilda.messaging.info.event.PathNode
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.transform.Canonical

@Canonical
class SwitchPair {
    Switch src
    Switch dst
    List<List<PathNode>> paths

    static SwitchPair singleSwitchInstance(Switch sw) {
        new SwitchPair(src: sw, dst: sw, paths: [])
    }
}
