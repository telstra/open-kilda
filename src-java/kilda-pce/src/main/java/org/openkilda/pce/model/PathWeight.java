/* Copyright 2022 Telstra Open Source
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

import lombok.ToString;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Vector path weight representation. Each value in vector corresponds to some path param defined
 * by {@link WeightFunction}. Only PathWeights created by one WeightFunction should be added and compared.
 */
@ToString
public class PathWeight implements Comparable<PathWeight> {

    private final long baseWeight;
    private final long alternativeBaseWeight;
    private final Map<Penalty, Long> penalties = new EnumMap<>(Penalty.class);

    public PathWeight() {
        this(0);
    }

    public PathWeight(long baseWeight) {
        this(baseWeight, 0L);
    }

    public PathWeight(long baseWeight, Map<Penalty, Long> penalties) {
        this(baseWeight, penalties, 0L);
    }

    public PathWeight(long baseWeight, Long alternativeBaseWeight) {
        this(baseWeight, Collections.emptyMap(), alternativeBaseWeight);
    }

    public PathWeight(long baseWeight, Map<Penalty, Long> penalties, Long alternativeBaseWeight) {
        this.baseWeight = baseWeight;
        this.penalties.putAll(penalties);
        this.alternativeBaseWeight = alternativeBaseWeight;
    }

    /**
     * Raw weight without penalties.
     */
    public long getBaseWeight() {
        return baseWeight;
    }

    public long getAlternativeBaseWeight() {
        return alternativeBaseWeight;
    }

    public long getPenaltyValue(Penalty name) {
        return penalties.getOrDefault(name, 0L);
    }

    /**
     * Simple scalar representation of weight.
     */
    public long getTotalWeight() {
        return baseWeight + getPenaltiesWeight();
    }

    public long getPenaltiesWeight() {
        return penalties.values().stream().reduce(0L, Long::sum);
    }

    /**
     * Summarize two PathWeights.
     */
    public PathWeight add(PathWeight toAdd) {
        long baseWeightResult = baseWeight + toAdd.getBaseWeight();
        long alternativeBaseWeightResult = alternativeBaseWeight + toAdd.alternativeBaseWeight;
        Map<Penalty, Long> penaltiesResult = new EnumMap<>(penalties);
        toAdd.penalties.forEach((k, v) -> {
            long result = penaltiesResult.getOrDefault(k, 0L) + v;
            penaltiesResult.put(k, result);
        });
        return new PathWeight(baseWeightResult, penaltiesResult, alternativeBaseWeightResult);
    }

    @Override
    public int compareTo(PathWeight o) {
        if (getTotalWeight() != o.getTotalWeight()) {
            return getTotalWeight() > o.getTotalWeight() ? 1 : -1;
        }

        if (alternativeBaseWeight != o.getAlternativeBaseWeight()) {
            return alternativeBaseWeight > o.getAlternativeBaseWeight() ? 1 : -1;
        }

        int firstSize = penalties.size();
        int secondSize = o.penalties.size();

        if (firstSize == secondSize) {
            return 0;
        } else {
            return Integer.compare(firstSize, secondSize);
        }
    }

    public enum Penalty {
        UNDER_MAINTENANCE,
        UNSTABLE,
        AFFINITY_ISL_LATENCY,
        DIVERSITY_ISL_LATENCY,
        DIVERSITY_SWITCH_LATENCY,
        DIVERSITY_POP_ISL_COST,
        PROTECTED_DIVERSITY_ISL_LATENCY,
        PROTECTED_DIVERSITY_SWITCH_LATENCY
    }
}
