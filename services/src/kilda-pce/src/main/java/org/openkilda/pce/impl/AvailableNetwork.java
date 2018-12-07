/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.pce.impl;

import static java.util.Objects.requireNonNull;

import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.impl.model.Edge;
import org.openkilda.pce.impl.model.Node;

import com.google.common.annotations.VisibleForTesting;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Semantically, this class represents an "available network". That means everything in it is available for path
 * allocation.
 */
@Slf4j
@ToString
public class AvailableNetwork {
    @VisibleForTesting
    final Map<SwitchId, Node> switches = new HashMap<>();

    /**
     * Returns a graph switch node by switchId.
     *
     * @return the switch node.
     */
    public Node getSwitchNode(SwitchId switchId) {
        return switches.get(switchId);
    }

    /**
     * Creates nodes (if they are not created yet) and edge between them.
     */
    void addLink(Isl isl) {
        Node srcSwitch = getOrInitSwitch(requireNonNull(isl.getSrcSwitch()));
        Edge edge = Edge.fromIsl(isl);

        if (srcSwitch.getOutboundEdges().contains(edge)) {
            log.warn("Duplicate ISL has been added to AvailableNetwork: {}", isl);
            return;
        }

        srcSwitch.addEdge(edge);
    }

    private Node getOrInitSwitch(Switch sw) {
        return switches.computeIfAbsent(sw.getSwitchId(), Node::new);
    }

    /**
     * Call this function to reduce the network to single (directed) links between src and dst switches.
     */
    public void reduceByCost() {
        for (Node sw : switches.values()) {
            if (sw.getOutboundEdges().isEmpty()) {
                log.warn("Switch {} has NO OUTBOUND isls", sw.getSwitchId());
            } else {
                sw.reduceByCost();
            }
        }
    }

    /**
     * Eliminates any self loops (ie src and dst switch is the same).
     *
     * @return this
     */
    AvailableNetwork removeSelfLoops() {
        for (Node sw : switches.values()) {
            sw.getOutboundEdges().removeIf(link -> link.getSrcSwitch().equals(link.getDestSwitch()));
        }
        return this;
    }

    /**
     * Excludes switches from network, that are used by {@param isls} ISLs.
     *
     * @param isls ISLs to exclude used switches
     * @return this
     */
    public AvailableNetwork excludeSwitches(Collection<Isl> isls) {
        Set<SwitchId> toExclude = new HashSet<>();
        for (Isl isl : isls) {
            toExclude.add(isl.getSrcSwitch().getSwitchId());
            toExclude.add(isl.getDestSwitch().getSwitchId());
        }

        for (Entry<SwitchId, Node> entry: switches.entrySet()) {
            entry.getValue().filterEdges(
                    edge -> !toExclude.contains(edge.getSrcSwitch()) && !toExclude.contains(edge.getDestSwitch()));
        }
        return this;
    }

    /**
     * Excludes {@param isls} ISLs from network.
     *
     * @param isls ISLs to exclude
     * @return this
     */
    public AvailableNetwork excludeIsls(Collection<Isl> isls) {
        for (Isl isl : isls) {
            excludeEdge(Edge.fromIsl(isl));
        }
        return this;
    }

    private void excludeEdge(Edge edge) {
        SwitchId src = edge.getSrcSwitch();
        if (switches.containsKey(src)) {
            switches.get(src)
                    .getOutboundEdges()
                    .removeIf(link -> link.equals(edge));
        }
    }
}
