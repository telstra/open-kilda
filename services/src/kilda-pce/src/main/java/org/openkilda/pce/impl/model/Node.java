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

package org.openkilda.pce.impl.model;

import org.openkilda.model.SwitchId;

import com.google.common.base.Preconditions;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Represents graph node. Holds outbound relations set.
 */
@Getter
@RequiredArgsConstructor
@EqualsAndHashCode(exclude = "outboundEdges")
public class Node {

    private final SwitchId switchId;

    Set<Edge> outboundEdges = new HashSet<>();

    /**
     * Adds outbound edge.
     */
    public void addEdge(Edge edge) {
        Preconditions.checkArgument(edge.getSrcSwitch().equals(switchId), "Edge is invalid for this switch");
        outboundEdges.add(edge);
    }

    /**
     * Apply filter predicate to edges set.
     */
    public void filterEdges(Predicate<? super Edge> predicate) {
        outboundEdges = outboundEdges.stream()
                .filter(predicate)
                .collect(Collectors.toSet());
    }

    /**
     * Removes duplicate links to the same dst node. Keeps link with lowest cost.
     */
    public void reduceByCost() {
        outboundEdges = outboundEdges.stream()
                .collect(Collectors.groupingBy(Edge::getDestSwitch))
                .entrySet().stream()
                .map(entry -> entry.getValue().stream().min(Comparator.comparingInt(Edge::getCost)))
                .map(Optional::get)
                .collect(Collectors.toSet());
    }
}
