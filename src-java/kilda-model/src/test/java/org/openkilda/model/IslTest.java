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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

public class IslTest {
    private final IslConfig islConfig = IslConfig.builder()
            .unstableIslTimeout(Duration.ofSeconds(120))
            .build();

    @Test
    public void shouldComputeIsUnstable() {
        Isl isl = Isl.builder()
                .srcSwitch(Switch.builder().switchId(new SwitchId(1)).build())
                .destSwitch(Switch.builder().switchId(new SwitchId(2)).build())
                .build();
        isl.setIslConfig(islConfig);

        isl.setTimeUnstable(Instant.now().minus(islConfig.getUnstableIslTimeout()));
        assertFalse(isl.isUnstable());

        isl.setTimeUnstable(Instant.now());
        assertTrue(isl.isUnstable());
    }
}
