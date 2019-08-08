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

import java.time.Duration;
import java.time.Instant;

public class IslTest {
    private final IslConfig islConfig = IslConfig.builder()
            .underMaintenanceCostRaise(1000)
            .unstableCostRaise(1000)
            .unstableIslTimeout(Duration.ofSeconds(120))
            .build();

    @Test
    public void shouldComputeEffectiveCostAccordingUnderMaintenanceFlag() {
        int islCost = 700;

        Isl isl = new Isl();
        isl.setCost(islCost);
        isl.setIslConfig(islConfig);
        assertEquals(islCost, isl.getEffectiveCost());

        isl.setUnderMaintenance(true);
        assertEquals(islCost + islConfig.getUnderMaintenanceCostRaise(), isl.getEffectiveCost());
    }

    @Test
    public void shouldComputeEffectiveCostAccordingUnstableTime() {
        int islCost = 700;

        Isl isl = new Isl();
        isl.setCost(islCost);
        isl.setIslConfig(islConfig);
        assertEquals(islCost, isl.getEffectiveCost());

        isl.setTimeUnstable(Instant.now().minus(islConfig.getUnstableIslTimeout()));
        assertEquals(islCost, isl.getEffectiveCost());

        isl.setTimeUnstable(Instant.now());
        assertEquals(islCost + islConfig.getUnstableCostRaise(), isl.getEffectiveCost());

        isl.setTimeUnstable(Instant.now().minus(islConfig.getUnstableIslTimeout().minus(Duration.ofSeconds(1))));
        assertEquals(islCost + islConfig.getUnstableCostRaise(), isl.getEffectiveCost());
    }
}
