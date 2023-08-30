/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.service.haflow;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.messaging.command.haflow.HaFlowResponse;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.topology.flowhs.service.AbstractHaFlowTest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

public class HaFlowReadServiceTest extends AbstractHaFlowTest<FlowSegmentRequest> {
    private static HaFlowReadService haFlowReadService;

    @BeforeAll
    public static void setUpOnce() {
        haFlowReadService = new HaFlowReadService(persistenceManager, 0, Duration.ofMillis(1));
    }

    @Test
    public void dumpReturnsEmptyListTest() {
        List<HaFlowResponse> haFlows = haFlowReadService.getAllHaFlows();
        Assertions.assertEquals(0, haFlows.size());
    }

    @Test
    public void failingFetchingForUnknownHaFlowIdTest() {
        assertThrows(FlowNotFoundException.class, () -> {
            String haFlowId = "unknown_ha_flow";

            haFlowReadService.getHaFlow(haFlowId);
        });
    }

    @Test
    public void failingFetchingPathsForUnknownHaFlowIdTest() {
        assertThrows(FlowNotFoundException.class, () -> {
            String haFlowId = "unknown_ha_flow";

            haFlowReadService.getHaFlowPaths(haFlowId);
        });
    }
}
