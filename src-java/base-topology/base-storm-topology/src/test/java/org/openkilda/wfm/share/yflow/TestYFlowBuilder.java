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

package org.openkilda.wfm.share.yflow;

import static com.google.common.base.Preconditions.checkArgument;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;

import lombok.AccessLevel;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Setter
@Accessors(fluent = true)
public class TestYFlowBuilder {
    private String yFlowId = UUID.randomUUID().toString();

    private YFlow.SharedEndpoint sharedEndpoint;
    private MeterId sharedEndpointMeterId;

    private SwitchId yPoint;
    private MeterId meterId;

    private SwitchId protectedPathYPoint;
    private MeterId protectedPathMeterId;

    private long maximumBandwidth;
    private boolean ignoreBandwidth;
    private boolean strictBandwidth;
    private String description;
    private boolean periodicPings;
    private FlowEncapsulationType encapsulationType;
    private FlowStatus status;
    private Long maxLatency;
    private Long maxLatencyTier2;
    private Integer priority;
    private Instant timeCreate;
    private Instant timeModify;
    private boolean pinned;
    private PathComputationStrategy pathComputationStrategy;

    @Setter(AccessLevel.NONE)
    private List<TestYSubFlowBuilder> subFlows = new ArrayList<>();

    public TestYFlowBuilder subFlow(TestYSubFlowBuilder subFlowBuilder) {
        subFlows.add(subFlowBuilder);
        return this;
    }

    /**
     * Build {@link YFlow} instance.
     */
    public YFlow build() {
        boolean allocateProtectedPath = false;
        if (protectedPathYPoint != null || protectedPathMeterId != null) {
            checkArgument(
                    protectedPathYPoint != null && protectedPathMeterId != null,
                    "Insufficient data provided to make YFlow with protected path");
            allocateProtectedPath = true;
        }

        YFlow.YFlowBuilder yFlowBuilder = YFlow.builder()
                .yFlowId(yFlowId)
                .sharedEndpoint(sharedEndpoint)
                .sharedEndpointMeterId(sharedEndpointMeterId)
                .yPoint(yPoint)
                .meterId(meterId)
                .allocateProtectedPath(allocateProtectedPath)
                .maximumBandwidth(maximumBandwidth)
                .ignoreBandwidth(ignoreBandwidth)
                .strictBandwidth(strictBandwidth)
                .description(description)
                .periodicPings(periodicPings)
                .encapsulationType(encapsulationType)
                .status(status)
                .maxLatency(maxLatency)
                .maxLatencyTier2(maxLatencyTier2)
                .priority(priority)
                .pinned(pinned)
                .pathComputationStrategy(pathComputationStrategy);
        if (allocateProtectedPath) {
            yFlowBuilder = yFlowBuilder
                    .protectedPathYPoint(protectedPathYPoint)
                    .protectedPathMeterId(protectedPathMeterId);
        }

        YFlow yFlow = yFlowBuilder.build();

        Set<YSubFlow> subFlowsGoal = new HashSet<>();
        for (TestYSubFlowBuilder subFlowBuilder : subFlows) {
            subFlowsGoal.add(subFlowBuilder
                    .yFlow(yFlow)
                    .build());
        }
        yFlow.setSubFlows(subFlowsGoal);

        yFlow.setTimeCreate(timeCreate);
        yFlow.setTimeModify(timeModify);

        return yFlow;
    }
}
