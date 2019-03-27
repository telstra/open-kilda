/* Copyright 2019 Telstra Open Source
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

package org.openkilda.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MeterTest {

    @Test
    public void calculateBurstSize() {
        assertEquals(1024, Meter.calculateBurstSize(512L, 1024L, 1.0, "centec"));
        assertEquals(32000, Meter.calculateBurstSize(32333L, 1024L, 1.0, "centec"));
        assertEquals(10030, Meter.calculateBurstSize(10000L, 1024L, 1.003, "centec"));
        assertEquals(1105500, Meter.calculateBurstSize(1100000L, 1024L, 1.005, "NW000.0.0"));
        assertEquals(1105500, Meter.calculateBurstSize(1100000L, 1024L, 1.05, "NW000.0.0"));
    }
}
