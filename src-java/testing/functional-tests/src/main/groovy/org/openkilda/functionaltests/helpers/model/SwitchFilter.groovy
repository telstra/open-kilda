package org.openkilda.functionaltests.helpers.model;

import org.openkilda.testing.model.topology.TopologyDefinition.Switch

class SwitchFilter {
    static final Closure<List<Switch>> HAS_CONNECTED_TRAFFGENS = { Switch sw -> !sw.getTraffGens().empty}
}
