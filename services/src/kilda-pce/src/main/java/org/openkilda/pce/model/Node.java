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

package org.openkilda.pce.model;

import static java.util.Comparator.comparingLong;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.minBy;
import static java.util.stream.Collectors.toSet;

import org.openkilda.model.SwitchId;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Getter
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "switchId")
@ToString(exclude = {"incomingLinks", "outgoingLinks"})
public class Node {
    @NonNull
    private final SwitchId switchId;

    @Setter
    private int cost;

    @NonNull
    private Set<Edge> incomingLinks;
    @NonNull
    private Set<Edge> outgoingLinks;

    /**
     * Constructs {@link Node} instance with passed {@link SwitchId}.
     *
     * @param swId the {@link SwitchId} instance.
     * @return new {@link Node} instance.
     */
    public static Node fromSwitchId(SwitchId swId) {
        return Node.builder()
                .switchId(swId)
                .incomingLinks(new HashSet<>())
                .outgoingLinks(new HashSet<>())
                .build();
    }

    /**
     * Performs links reducing for current node by passed {@link WeightFunction}.
     *
     * @param weightFunction the function for weigh calculation.
     */
    public void reduceByWeight(WeightFunction weightFunction) {
        outgoingLinks = reduceByWeight(outgoingLinks, weightFunction);
        incomingLinks = reduceByWeight(incomingLinks, weightFunction);
    }

    private Set<Edge> reduceByWeight(Set<Edge> edges, WeightFunction weightFunction) {
        if (edges.isEmpty()) {
            return edges;
        }
        return edges.stream()
                .collect(groupingBy(Edge::getDestSwitch, minBy(comparingLong(weightFunction::apply))))
                .values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toSet());
    }
}
