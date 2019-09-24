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

import static com.google.common.collect.Sets.newHashSet;
import static org.junit.Assert.assertEquals;
import static org.openkilda.model.SwitchFeature.MAX_BURST_COEFFICIENT_LIMITATION;

import org.junit.Test;

import java.util.Set;

public class MeterTest {

    @Test
    public void calculateBurstSize() {
        assertEquals(1024, Meter.calculateBurstSize(512L, 1024L, 1.0, "centec"));
        assertEquals(32000, Meter.calculateBurstSize(32333L, 1024L, 1.0, "centec"));
        assertEquals(10030, Meter.calculateBurstSize(10000L, 1024L, 1.003, "centec"));
        assertEquals(1105500, Meter.calculateBurstSize(1100000L, 1024L, 1.005, "NW000.0.0"));
        assertEquals(1105500, Meter.calculateBurstSize(1100000L, 1024L, 1.05, "NW000.0.0"));
    }

    @Test
    public void convertRateToKiloBitsTest() {
        assertEquals(800, Meter.convertRateToKiloBits(100, 1024));
        assertEquals(64, Meter.convertRateToKiloBits(1, 1));
        assertEquals(64, Meter.convertRateToKiloBits(8, 16));
    }

    @Test
    public void convertBurstSizeToKiloBitsTest() {
        assertEquals(800, Meter.convertBurstSizeToKiloBits(100, 1024));
        assertEquals(1, Meter.convertBurstSizeToKiloBits(8, 16));
    }

    @Test
    public void convertCalculateBurstSizeConsideringHardwareLimitations() {
        Set<SwitchFeature> limitationFeature = newHashSet(MAX_BURST_COEFFICIENT_LIMITATION);
        assertEquals(4096, Meter.calculateBurstSizeConsideringHardwareLimitations(1, 4096, newHashSet()));
        assertEquals(1, Meter.calculateBurstSizeConsideringHardwareLimitations(1, 4096, limitationFeature));
        assertEquals(100, Meter.calculateBurstSizeConsideringHardwareLimitations(100, 100, limitationFeature));
        assertEquals(10, Meter.calculateBurstSizeConsideringHardwareLimitations(100, 10, limitationFeature));
    }
}
