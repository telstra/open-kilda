/* Copyright 2020 Telstra Open Source
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

import org.junit.Test;

public class PathWeightTest {

    @Test
    public void add() {
        PathWeight first = new PathWeight(2, 7);
        PathWeight second = new PathWeight(1, 2);

        PathWeight actual = first.add(second);

        assertEquals(0, actual.compareTo(new PathWeight(3, 9)));
    }

    @Test
    public void compareTo() {
        final PathWeight first = new PathWeight(2, 7);
        final PathWeight second = new PathWeight(5, 7);
        final PathWeight equalToSecond = new PathWeight(5, 7);
        final PathWeight third = new PathWeight(7);
        final PathWeight fourth = new PathWeight(7, 2);

        assertTrue(first.compareTo(second) < 0);
        assertTrue(second.compareTo(first) > 0);
        assertTrue(second.compareTo(equalToSecond) == 0);
        assertTrue(second.compareTo(third) < 0);
        assertTrue(third.compareTo(fourth) < 0);
        assertTrue(fourth.compareTo(third) > 0);
    }
}
