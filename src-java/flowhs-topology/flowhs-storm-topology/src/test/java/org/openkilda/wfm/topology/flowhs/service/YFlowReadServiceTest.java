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

package org.openkilda.wfm.topology.flowhs.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.openkilda.messaging.command.yflow.SubFlowsResponse;
import org.openkilda.messaging.command.yflow.YFlowDto;
import org.openkilda.messaging.command.yflow.YFlowPathsResponse;
import org.openkilda.messaging.command.yflow.YFlowResponse;
import org.openkilda.wfm.error.FlowNotFoundException;

import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Duration;
import java.util.List;

public class YFlowReadServiceTest extends AbstractYFlowTest {
    private static YFlowReadService yFlowReadService;

    @BeforeClass
    public static void setUpOnce() {
        yFlowReadService = new YFlowReadService(persistenceManager, 0, Duration.ofMillis(1));
    }

    @Test
    public void shouldDumpYFlows() throws FlowNotFoundException {
        // given
        String yFlowId = "test_y_flow_1";
        createYFlowViaTransit(yFlowId);
        // when
        List<YFlowResponse> yFlows = yFlowReadService.getAllYFlows();
        // then
        assertEquals(1, yFlows.size());
        YFlowDto yFlow = yFlows.get(0).getYFlow();
        assertNotNull(yFlow);
        assertEquals(yFlowId, yFlow.getYFlowId());
        assertEquals(2, yFlow.getSubFlows().size());
    }

    @Test
    public void shouldDumpReturnEmptyList() throws FlowNotFoundException {
        List<YFlowResponse> yFlows = yFlowReadService.getAllYFlows();
        assertEquals(0, yFlows.size());
    }

    @Test
    public void shouldFetchYFlow() throws FlowNotFoundException {
        // given
        String yFlowId = "test_y_flow_1";
        createYFlowViaTransit(yFlowId);
        // when
        YFlowResponse yFlowResponse = yFlowReadService.getYFlow(yFlowId);
        // then
        YFlowDto yFlow = yFlowResponse.getYFlow();
        assertNotNull(yFlow);
        assertEquals(2, yFlow.getSubFlows().size());
    }

    @Test(expected = FlowNotFoundException.class)
    public void failFetchingForUnknownYFlowId() throws FlowNotFoundException {
        String yFlowId = "unknown_y_flow";

        yFlowReadService.getYFlow(yFlowId);
        fail();
    }

    @Test
    public void shouldFetchYFlowSubFlows() throws FlowNotFoundException {
        // given
        String yFlowId = "test_y_flow_1";
        createYFlowViaTransit(yFlowId);
        // when
        SubFlowsResponse yFlowResponse = yFlowReadService.getYFlowSubFlows(yFlowId);
        // then
        assertEquals(2, yFlowResponse.getFlows().size());
    }

    @Test(expected = FlowNotFoundException.class)
    public void failFetchingSubFlowsForUnknownYFlowId() throws FlowNotFoundException {
        String yFlowId = "unknown_y_flow";

        yFlowReadService.getYFlowSubFlows(yFlowId);
        fail();
    }

    @Test
    public void shouldFetchYFlowPaths() throws FlowNotFoundException {
        // given
        String yFlowId = "test_y_flow_1";
        createYFlowViaTransit(yFlowId);
        // when
        YFlowPathsResponse yFlowResponse = yFlowReadService.getYFlowPaths(yFlowId);
        // then
        // Only 1 shared segment
        assertEquals(2, yFlowResponse.getSharedPath().getPath().size());
        assertEquals(SWITCH_SHARED, yFlowResponse.getSharedPath().getPath().get(0).getSwitchId());
        assertEquals(SWITCH_TRANSIT, yFlowResponse.getSharedPath().getPath().get(1).getSwitchId());
        assertEquals(2, yFlowResponse.getSubFlowPaths().size());
        // No protected paths
        assertNull(yFlowResponse.getSharedProtectedPath().getPath());
        assertEquals(0, yFlowResponse.getSubFlowProtectedPaths().size());
    }

    @Test(expected = FlowNotFoundException.class)
    public void failFetchingPathsForUnknownYFlowId() throws FlowNotFoundException {
        String yFlowId = "unknown_y_flow";

        yFlowReadService.getYFlowPaths(yFlowId);
        fail();
    }

    @Test
    public void shouldFetchYFlowWithProtectedPaths() throws FlowNotFoundException {
        // given
        String yFlowId = "test_y_flow_1";
        createYFlowWithProtected(yFlowId);
        // when
        YFlowResponse yFlowResponse = yFlowReadService.getYFlow(yFlowId);
        // then
        YFlowDto yFlow = yFlowResponse.getYFlow();
        assertNotNull(yFlow);
        assertEquals(2, yFlow.getSubFlows().size());

        // and when
        YFlowPathsResponse yFlowPathsResponse = yFlowReadService.getYFlowPaths(yFlowId);
        // then
        // Only 1 shared segment
        assertEquals(2, yFlowPathsResponse.getSharedPath().getPath().size());
        assertEquals(SWITCH_SHARED, yFlowPathsResponse.getSharedPath().getPath().get(0).getSwitchId());
        assertEquals(SWITCH_TRANSIT, yFlowPathsResponse.getSharedPath().getPath().get(1).getSwitchId());
        assertEquals(2, yFlowPathsResponse.getSubFlowPaths().size());
        // The protected paths
        assertEquals(2, yFlowPathsResponse.getSharedProtectedPath().getPath().size());
        assertEquals(SWITCH_SHARED, yFlowPathsResponse.getSharedProtectedPath().getPath().get(0).getSwitchId());
        assertEquals(SWITCH_ALT_TRANSIT, yFlowPathsResponse.getSharedProtectedPath().getPath().get(1).getSwitchId());
        assertEquals(2, yFlowPathsResponse.getSubFlowProtectedPaths().size());
    }
}
