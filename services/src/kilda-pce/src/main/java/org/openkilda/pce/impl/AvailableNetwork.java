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

import org.openkilda.model.FlowSegment;
import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.pce.model.Edge;
import org.openkilda.pce.model.Node;
import org.openkilda.pce.model.WeightFunction;

import com.google.common.annotations.VisibleForTesting;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

    public Node getSwitch(SwitchId dpid) {
        return switches.get(dpid);
    }

    /**
     * Creates switches (if they are not created yet) and ISL between them.
     */
    public void addLink(Isl isl) {
        Node srcSwitch = getOrInitSwitch(isl.getSrcSwitch());
        Node dstSwitch = getOrInitSwitch(isl.getDestSwitch());

        Edge edge = Edge.fromIslToBuilder(isl)
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .build();
        boolean srcAdded = srcSwitch.getOutgoingLinks().add(edge);
        boolean dstAdded = dstSwitch.getIncomingLinks().add(edge);
        if (!(srcAdded && dstAdded)) {
            log.warn("Duplicate ISL has been passed to AvailableNetwork: {}", isl);
        }
    }

    private Node getOrInitSwitch(Switch sw) {
        return switches.computeIfAbsent(sw.getSwitchId(), Node::fromSwitchId);
    }

    /**
     * Adds diversity weights into {@link AvailableNetwork} based on passed flow segments and configuration.
     */
    public void processDiversitySegments(Collection<FlowSegment> segments, PathComputerConfig config) {
        for (FlowSegment segment : segments) {
            Node srcNode = getSwitch(segment.getSrcSwitch().getSwitchId());
            Node dstNode = getSwitch(segment.getDestSwitch().getSwitchId());
            Edge segmentEdge = Edge.builder()
                    .srcSwitch(srcNode)
                    .srcPort(segment.getSrcPort())
                    .destSwitch(dstNode)
                    .destPort(segment.getDestPort())
                    .build();

            Optional<Edge> edgeOptional = dstNode.getIncomingLinks().stream().filter(segmentEdge::equals).findAny();
            if (edgeOptional.isPresent()) {
                Edge edge = edgeOptional.get();

                edge.setDiversityWeight(edge.getDiversityWeight() + config.getDiversityIslWeight());
                dstNode.setDiversityWeight(dstNode.getDiversityWeight() + config.getDiversitySwitchWeight());
                if (segment.getSeqId() == 0) {
                    srcNode.setDiversityWeight(srcNode.getDiversityWeight() + config.getDiversitySwitchWeight());
                }
            }
        }
    }

    /**
     * Call this function to reduce the network to single (directed) links between src and dst switches.
     */
    public void reduceByWeight(WeightFunction weightFunction) {
        for (Node node : switches.values()) {
            Set<Edge> reduced = node.reduceByWeight(weightFunction);
            reduced.forEach(e -> {
                switches.get(e.getSrcSwitch().getSwitchId()).getIncomingLinks().remove(e);
                switches.get(e.getSrcSwitch().getSwitchId()).getOutgoingLinks().remove(e);
                switches.get(e.getDestSwitch().getSwitchId()).getIncomingLinks().remove(e);
                switches.get(e.getDestSwitch().getSwitchId()).getOutgoingLinks().remove(e);
            });
        }
    }
}
