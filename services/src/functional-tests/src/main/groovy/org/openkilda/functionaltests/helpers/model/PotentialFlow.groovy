package org.openkilda.functionaltests.helpers.model

import org.openkilda.messaging.info.event.PathNode
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.transform.Canonical

@Canonical
class PotentialFlow {
    Switch src
    Switch dst
    List<List<PathNode>> paths

    static PotentialFlow singleSwitchInstance(Switch sw) {
        new PotentialFlow(src: sw, dst: sw, paths: [])
    }
}
