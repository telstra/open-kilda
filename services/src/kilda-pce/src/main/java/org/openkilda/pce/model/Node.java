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

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@Getter
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "switchId")
@ToString(exclude = {"incomingLinks", "outgoingLinks", "backupIncomingLinks", "backupOutgoingLinks"})
public class Node {
    @NonNull
    private final SwitchId switchId;

    @NonNull
    private Set<Edge> incomingLinks;
    @NonNull
    private Set<Edge> outgoingLinks;

    private Set<Edge> backupIncomingLinks;
    private Set<Edge> backupOutgoingLinks;

    private int diversityGroupUseCounter;

    public void increaseDiversityGroupUseCounter() {
        diversityGroupUseCounter++;
    }

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
     * @return the reducing difference.
     */
    public Set<Edge> reduceByWeight(WeightFunction weightFunction) {
        Set<Edge> reducedOutgoing =
                reduceByWeight(outgoingLinks, Edge::getDestSwitch, Edge::getDestPort, weightFunction);
        Set<Edge> reducedIncoming =
                reduceByWeight(incomingLinks, Edge::getSrcSwitch, Edge::getSrcPort, weightFunction);

        Set<Edge> diff = Sets.newHashSet();
        diff.addAll(Sets.difference(outgoingLinks, reducedOutgoing));
        diff.addAll(Sets.difference(incomingLinks, reducedIncoming));

        outgoingLinks = reducedOutgoing;
        incomingLinks = reducedIncoming;
        return diff;
    }

    private Set<Edge> reduceByWeight(
            Set<Edge> edges, Function<Edge, Node> groupingFunction, Function<Edge, Integer> resolvePortCollisionsFn,
            WeightFunction weightFunction) {
        if (edges.isEmpty()) {
            return edges;
        }

        Comparator<Edge> comparator = comparingLong(weightFunction::apply);
        comparator = comparator.thenComparing(resolvePortCollisionsFn);
        return edges.stream()
                .collect(groupingBy(groupingFunction, minBy(comparator)))
                .values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toSet());
    }

    /**
     * Remove node.
     */
    public void remove() {
        backupIncomingLinks = incomingLinks;
        backupOutgoingLinks = outgoingLinks;

        incomingLinks = new HashSet<>();
        outgoingLinks = new HashSet<>();
    }

    /**
     * Restore node.
     */
    public void restore() {
        if (backupIncomingLinks != null && !backupIncomingLinks.isEmpty()
                && backupOutgoingLinks != null && !backupOutgoingLinks.isEmpty()) {
            incomingLinks = backupIncomingLinks;
            outgoingLinks = backupOutgoingLinks;
        }
    }
}
