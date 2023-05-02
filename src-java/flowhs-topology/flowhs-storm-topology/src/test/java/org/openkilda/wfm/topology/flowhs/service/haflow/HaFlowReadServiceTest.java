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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.messaging.command.haflow.HaFlowResponse;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.topology.flowhs.service.AbstractYFlowTest;

import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Duration;
import java.util.List;

public class HaFlowReadServiceTest extends AbstractYFlowTest<FlowSegmentRequest> {
    private static HaFlowReadService haFlowReadService;

    @BeforeClass
    public static void setUpOnce() {
        haFlowReadService = new HaFlowReadService(persistenceManager, 0, Duration.ofMillis(1));
    }

    @Test
    public void dumpReturnsEmptyListTest() {
        List<HaFlowResponse> haFlows = haFlowReadService.getAllHaFlows();
        assertEquals(0, haFlows.size());
    }

    @Test(expected = FlowNotFoundException.class)
    public void failingFetchingForUnknownHaFlowIdTest() throws FlowNotFoundException {
        String haFlowId = "unknown_ha_flow";

        haFlowReadService.getHaFlow(haFlowId);
        fail();
    }

    @Test(expected = FlowNotFoundException.class)
    public void failingFetchingPathsForUnknownHaFlowIdTest() throws FlowNotFoundException {
        String haFlowId = "unknown_ha_flow";

        haFlowReadService.getHaFlowPaths(haFlowId);
        fail();
    }
}
