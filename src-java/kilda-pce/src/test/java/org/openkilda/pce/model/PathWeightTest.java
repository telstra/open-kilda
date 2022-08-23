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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.openkilda.pce.model.PathWeight.Penalty;

import org.junit.Test;

import java.util.EnumMap;

public class PathWeightTest {

    @Test
    public void shouldAddWithAlterWeight() {
        PathWeight first = new PathWeight(2L, 7L);
        PathWeight second = new PathWeight(1L, 2L);

        PathWeight actual = first.add(second);

        assertEquals(0, actual.compareTo(new PathWeight(3L, 9L)));
    }

    @Test
    public void shouldComparePathWeights() {
        final PathWeight first = new PathWeight(2L, 7L);
        final PathWeight second = new PathWeight(5L, 7L);
        final PathWeight equalToSecond = new PathWeight(5L, 7L);
        final PathWeight third = new PathWeight(7L);
        final PathWeight fourth = new PathWeight(7L, 2L);

        assertTrue(first.compareTo(second) < 0);
        assertTrue(second.compareTo(first) > 0);
        assertTrue(second.compareTo(equalToSecond) == 0);
        assertTrue(second.compareTo(third) < 0);
        assertTrue(third.compareTo(fourth) < 0);
        assertTrue(fourth.compareTo(third) > 0);
    }

    @Test
    public void shouldAddWithPenalties() {
        EnumMap<Penalty, Long> firstPenalties = new EnumMap<>(Penalty.class);
        firstPenalties.put(Penalty.UNSTABLE, 1L);
        firstPenalties.put(Penalty.UNDER_MAINTENANCE, 2L);
        firstPenalties.put(Penalty.AFFINITY_ISL_LATENCY, 3L);
        firstPenalties.put(Penalty.DIVERSITY_ISL_LATENCY, 4L);
        firstPenalties.put(Penalty.DIVERSITY_POP_ISL_COST, 5L);
        firstPenalties.put(Penalty.DIVERSITY_SWITCH_LATENCY, 6L);
        PathWeight first = new PathWeight(2L, firstPenalties, 5L);

        EnumMap<Penalty, Long> secondPenalties = new EnumMap<>(Penalty.class);
        secondPenalties.put(Penalty.UNSTABLE, 1L);
        secondPenalties.put(Penalty.UNDER_MAINTENANCE, 2L);
        secondPenalties.put(Penalty.AFFINITY_ISL_LATENCY, 3L);
        secondPenalties.put(Penalty.DIVERSITY_ISL_LATENCY, 4L);
        secondPenalties.put(Penalty.DIVERSITY_POP_ISL_COST, 5L);
        secondPenalties.put(Penalty.DIVERSITY_SWITCH_LATENCY, 6L);
        PathWeight second = new PathWeight(1L, secondPenalties, 2L);

        PathWeight result = first.add(second);

        assertEquals(3L, result.getBaseWeight());
        assertEquals(7L, result.getAlternativeBaseWeight());
        assertEquals(2L, result.getPenaltyValue(Penalty.UNSTABLE));
        assertEquals(4L, result.getPenaltyValue(Penalty.UNDER_MAINTENANCE));
        assertEquals(6L, result.getPenaltyValue(Penalty.AFFINITY_ISL_LATENCY));
        assertEquals(8L, result.getPenaltyValue(Penalty.DIVERSITY_ISL_LATENCY));
        assertEquals(10L, result.getPenaltyValue(Penalty.DIVERSITY_POP_ISL_COST));
        assertEquals(12L, result.getPenaltyValue(Penalty.DIVERSITY_SWITCH_LATENCY));
        assertEquals(45L, result.getTotalWeight());
    }

    @Test
    public void shouldComparePathWeightsWithPenalties() {
        EnumMap<Penalty, Long> firstPenalties = new EnumMap<>(Penalty.class);
        firstPenalties.put(Penalty.UNSTABLE, 1L);
        final PathWeight first = new PathWeight(2L, firstPenalties);

        EnumMap<Penalty, Long> secondPenalties = new EnumMap<>(Penalty.class);
        secondPenalties.put(Penalty.UNDER_MAINTENANCE, 1L);
        final PathWeight second = new PathWeight(5L, secondPenalties);

        final PathWeight equalToSecond = new PathWeight(5L, secondPenalties);

        EnumMap<Penalty, Long> thirdPenalties = new EnumMap<>(Penalty.class);
        thirdPenalties.put(Penalty.AFFINITY_ISL_LATENCY, 10L);
        final PathWeight third = new PathWeight(1L, thirdPenalties);

        EnumMap<Penalty, Long> fourthPenalties = new EnumMap<>(Penalty.class);
        fourthPenalties.put(Penalty.AFFINITY_ISL_LATENCY, 2L);
        fourthPenalties.put(Penalty.DIVERSITY_SWITCH_LATENCY, 2L);
        fourthPenalties.put(Penalty.DIVERSITY_POP_ISL_COST, 2L);
        fourthPenalties.put(Penalty.DIVERSITY_ISL_LATENCY, 2L);
        fourthPenalties.put(Penalty.UNSTABLE, 2L);
        fourthPenalties.put(Penalty.UNDER_MAINTENANCE, 2L);
        final PathWeight fourth = new PathWeight(1L, fourthPenalties);

        EnumMap<Penalty, Long> fifthPenalties = new EnumMap<>(Penalty.class);
        fifthPenalties.put(Penalty.UNDER_MAINTENANCE, 30L);
        final PathWeight fifth = new PathWeight(1L, fifthPenalties);

        assertTrue(first.compareTo(second) < 0);
        assertTrue(second.compareTo(first) > 0);
        assertTrue(second.compareTo(equalToSecond) == 0);
        assertTrue(second.compareTo(third) < 0);
        assertTrue(third.compareTo(fourth) < 0);
        assertTrue(fourth.compareTo(third) > 0);
        assertTrue(fifth.compareTo(fourth) > 0);
    }
}
