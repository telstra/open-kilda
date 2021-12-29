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

public class ExecutionGraphTest {
    @Test
    public void testSimpleTopologicalSort() {
        ExecutionGraph g = new ExecutionGraph();
        g.add("A", new ArrayList<>());
        g.add("B", new ArrayList<>(Arrays.asList("A")));

        g.buildStages();
        assertEquals(2, g.stages.size());
        assertEquals(Arrays.asList("A"), g.stages.get(0));
        assertEquals(Arrays.asList("B"), g.stages.get(1));
    }

    @Test
    public void testTopologicalSort() {
        ExecutionGraph g = new ExecutionGraph();
        g.add("A", new ArrayList<>());
        g.add("B", new ArrayList<>(Arrays.asList("A")));
        g.add("C", new ArrayList<>(Arrays.asList("A", "B")));

        g.buildStages();
        assertEquals(3, g.stages.size());
        assertEquals(Arrays.asList("A"), g.stages.get(0));
        assertEquals(Arrays.asList("B"), g.stages.get(1));
        assertEquals(Arrays.asList("C"), g.stages.get(2));
    }

    @Test(expected = IllegalStateException.class)
    public void testTopologicalSortWithCycles() {
        ExecutionGraph g = new ExecutionGraph();
        g.add("A", new ArrayList<>(Arrays.asList("B")));
        g.add("B", new ArrayList<>(Arrays.asList("A")));
        g.buildStages();
    }

    @Test(expected = IllegalStateException.class)
    public void testGraphImmutableAfterBuild() {
        ExecutionGraph g = new ExecutionGraph();
        g.add("A", new ArrayList<>());
        g.add("B", new ArrayList<>(Arrays.asList("A")));

        g.buildStages();
        g.add("C", new ArrayList<>());
    }
}
