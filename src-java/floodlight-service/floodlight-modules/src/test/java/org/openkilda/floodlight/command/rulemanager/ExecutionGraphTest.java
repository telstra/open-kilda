/* Copyright 2021 Telstra Open Source
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

package org.openkilda.floodlight.command.rulemanager;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

public class ExecutionGraphTest {
    private static final UUID UUID_A = UUID.fromString("26cbe2f2-74a8-469d-be47-f9909638fe23");
    private static final UUID UUID_B = UUID.fromString("cd5701d5-5521-404e-8fa2-1edeb02bb654");
    private static final UUID UUID_C = UUID.fromString("32bcf0ee-34d9-41fe-bba0-5c92ae830492");

    @Test
    public void testSimpleTopologicalSort() {
        ExecutionGraph g = new ExecutionGraph();
        g.add(UUID_A, new ArrayList<>());
        g.add(UUID_B, new ArrayList<>(Collections.singletonList(UUID_A)));

        g.buildStages();
        assertEquals(2, g.stages.size());
        assertEquals(Collections.singletonList(UUID_A), g.stages.get(0));
        assertEquals(Collections.singletonList(UUID_B), g.stages.get(1));
    }

    @Test
    public void testTopologicalSort() {
        ExecutionGraph g = new ExecutionGraph();
        g.add(UUID_A, new ArrayList<>());
        g.add(UUID_B, new ArrayList<>(Collections.singletonList(UUID_A)));
        g.add(UUID_C, new ArrayList<>(Arrays.asList(UUID_A, UUID_B)));

        g.buildStages();
        assertEquals(3, g.stages.size());
        assertEquals(Collections.singletonList(UUID_A), g.stages.get(0));
        assertEquals(Collections.singletonList(UUID_B), g.stages.get(1));
        assertEquals(Collections.singletonList(UUID_C), g.stages.get(2));
    }

    @Test(expected = IllegalStateException.class)
    public void testTopologicalSortWithCycles() {
        ExecutionGraph g = new ExecutionGraph();
        g.add(UUID_A, new ArrayList<>(Collections.singletonList(UUID_B)));
        g.add(UUID_B, new ArrayList<>(Collections.singletonList(UUID_A)));
        g.buildStages();
    }

    @Test(expected = IllegalStateException.class)
    public void testGraphImmutableAfterBuild() {
        ExecutionGraph g = new ExecutionGraph();
        g.add(UUID_A, new ArrayList<>());
        g.add(UUID_B, new ArrayList<>(Collections.singletonList(UUID_A)));

        g.buildStages();
        g.add(UUID_C, new ArrayList<>());
    }
}
