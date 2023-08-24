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

package org.openkilda.wfm.topology.flowhs.service.yflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.messaging.command.yflow.SubFlowsResponse;
import org.openkilda.messaging.command.yflow.YFlowDto;
import org.openkilda.messaging.command.yflow.YFlowPathsResponse;
import org.openkilda.messaging.command.yflow.YFlowResponse;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.topology.flowhs.service.AbstractYFlowTest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

public class YFlowReadServiceTest extends AbstractYFlowTest<FlowSegmentRequest> {
    private static YFlowReadService yFlowReadService;

    @BeforeAll
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
        Assertions.assertNotNull(yFlow);
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
        Assertions.assertNotNull(yFlow);
        assertEquals(2, yFlow.getSubFlows().size());
    }

    @Test
    public void failFetchingForUnknownYFlowId() {
        Assertions.assertThrows(FlowNotFoundException.class, () -> {
            String yFlowId = "unknown_y_flow";

            yFlowReadService.getYFlow(yFlowId);
        });
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

    @Test
    public void failFetchingSubFlowsForUnknownYFlowId() {
        Assertions.assertThrows(FlowNotFoundException.class, () -> {
            String yFlowId = "unknown_y_flow";

            yFlowReadService.getYFlowSubFlows(yFlowId);
        });

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
        assertEquals(2, yFlowResponse.getSharedPath().getForwardPath().size());
        assertEquals(SWITCH_SHARED, yFlowResponse.getSharedPath().getForwardPath().get(0).getSwitchId());
        assertEquals(2, yFlowResponse.getSubFlowPaths().size());
        // No protected paths
        Assertions.assertNull(yFlowResponse.getSharedPath().getProtectedPath());
    }

    @Test
    public void failFetchingPathsForUnknownYFlowId() {
        Assertions.assertThrows(FlowNotFoundException.class, () -> {
            String yFlowId = "unknown_y_flow";

            yFlowReadService.getYFlowPaths(yFlowId);
        });

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
        Assertions.assertNotNull(yFlow);
        assertEquals(2, yFlow.getSubFlows().size());

        // and when
        YFlowPathsResponse yFlowPathsResponse = yFlowReadService.getYFlowPaths(yFlowId);
        // then
        // Only 1 shared segment
        assertEquals(2, yFlowPathsResponse.getSharedPath().getForwardPath().size());
        assertEquals(2, yFlowPathsResponse.getSharedPath().getReversePath().size());
        assertEquals(SWITCH_SHARED, yFlowPathsResponse.getSharedPath().getForwardPath().get(0).getSwitchId());
        assertEquals(2, yFlowPathsResponse.getSubFlowPaths().size());
        // The protected paths
        assertEquals(2, yFlowPathsResponse.getSharedPath().getProtectedPath().getForwardPath().size());
        assertEquals(2, yFlowPathsResponse.getSharedPath().getProtectedPath().getReversePath().size());
        assertEquals(SWITCH_SHARED, yFlowPathsResponse.getSharedPath().getProtectedPath().getForwardPath()
                .get(0).getSwitchId());
    }
}
