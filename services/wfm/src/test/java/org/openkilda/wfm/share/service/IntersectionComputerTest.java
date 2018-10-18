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

package org.openkilda.wfm.share.service;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

import org.openkilda.messaging.payload.flow.OverlappingSegmentsStats;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

public class IntersectionComputerTest {
    private static final SwitchId SWITCH_ID_A = new SwitchId("00:00:00:00:00:00:00:0A");
    private static final SwitchId SWITCH_ID_B = new SwitchId("00:00:00:00:00:00:00:0B");
    private static final SwitchId SWITCH_ID_C = new SwitchId("00:00:00:00:00:00:00:0C");
    private static final SwitchId SWITCH_ID_D = new SwitchId("00:00:00:00:00:00:00:0D");
    private static final SwitchId SWITCH_ID_E = new SwitchId("00:00:00:00:00:00:00:0E");

    private static final String flowId = "old-flow";
    private static final String newFlowId = "new-flow";

    private static final OverlappingSegmentsStats ZERO_STATS =
            new OverlappingSegmentsStats(0, 0, 0, 0);

    @Test
    public void onlyCurrentFlowSegments() {
        FlowPath flowPath = getFlowPath(flowId);

        IntersectionComputer computer = new IntersectionComputer(flowId, asList(flowPath));
        OverlappingSegmentsStats stats = computer.getOverlappingStats();

        assertEquals(ZERO_STATS, stats);
    }

    @Test
    public void shouldNoIntersections() {
        FlowPath flowPath = getFlowPath(flowId);
        FlowPath newFlowPath = getFlowPath(newFlowId);
        newFlowPath.setSegments(new ArrayList<>());
        addPathSegment(newFlowPath, SWITCH_ID_D, SWITCH_ID_E, 10, 10);
        addPathSegment(newFlowPath, SWITCH_ID_E, SWITCH_ID_D, 10, 10);

        IntersectionComputer computer = new IntersectionComputer(flowId, asList(flowPath, newFlowPath));
        OverlappingSegmentsStats stats = computer.getOverlappingStats(newFlowId);

        assertEquals(ZERO_STATS, stats);
    }

    @Test
    public void shouldNotFailIfNoSegments() {
        IntersectionComputer computer = new IntersectionComputer(flowId, Collections.emptyList());
        OverlappingSegmentsStats stats = computer.getOverlappingStats(newFlowId);

        assertEquals(ZERO_STATS, stats);
    }

    @Test
    public void shouldNotFailIfNoSegmentsForIntersect() {
        FlowPath flowPath = getFlowPath(flowId);

        IntersectionComputer computer = new IntersectionComputer(flowId, asList(flowPath));
        OverlappingSegmentsStats stats = computer.getOverlappingStats(newFlowId);

        assertEquals(ZERO_STATS, stats);
    }

    @Test
    public void switchIntersection() {
        FlowPath flowPath = getFlowPath(flowId);
        FlowPath newFlowPath = getFlowPath(newFlowId);
        newFlowPath.setSegments(new ArrayList<>());
        addPathSegment(newFlowPath, SWITCH_ID_A, SWITCH_ID_D, 10, 10);

        IntersectionComputer computer = new IntersectionComputer(flowId, asList(flowPath, newFlowPath));
        OverlappingSegmentsStats stats = computer.getOverlappingStats(newFlowId);

        assertEquals(new OverlappingSegmentsStats(0, 1, 0, 33), stats);
    }

    @Test
    public void partialIntersection() {
        FlowPath flowPath = getFlowPath(flowId);
        FlowPath newFlowPath = getFlowPath(newFlowId);
        newFlowPath.setSegments(new ArrayList<>());
        addPathSegment(newFlowPath, SWITCH_ID_A, SWITCH_ID_B, 1, 1);

        IntersectionComputer computer = new IntersectionComputer(flowId, asList(flowPath, newFlowPath));
        OverlappingSegmentsStats stats = computer.getOverlappingStats(newFlowId);

        assertEquals(new OverlappingSegmentsStats(1, 2, 50, 66), stats);
    }

    @Test
    public void fullIntersection() {
        FlowPath flowPath = getFlowPath(flowId);
        FlowPath newFlowPath = getFlowPath(newFlowId);

        IntersectionComputer computer = new IntersectionComputer(flowId, asList(flowPath, newFlowPath));
        OverlappingSegmentsStats stats = computer.getOverlappingStats(newFlowId);

        assertEquals(new OverlappingSegmentsStats(2, 3, 100, 100), stats);
    }

    private FlowPath getFlowPath(String flowId) {
        FlowPath flowPath = FlowPath.builder()
                .flowId(flowId)
                .pathId(new PathId(UUID.randomUUID().toString()))
                .srcSwitch(Switch.builder().switchId(SWITCH_ID_A).build())
                .destSwitch(Switch.builder().switchId(SWITCH_ID_D).build())
                .segments(new ArrayList<>())
                .build();
        addPathSegment(flowPath, SWITCH_ID_A, SWITCH_ID_B, 1, 1);
        addPathSegment(flowPath, SWITCH_ID_B, SWITCH_ID_A, 1, 1);
        addPathSegment(flowPath, SWITCH_ID_B, SWITCH_ID_C, 2, 2);
        addPathSegment(flowPath, SWITCH_ID_C, SWITCH_ID_B, 2, 2);
        return flowPath;
    }

    private void addPathSegment(FlowPath flowPath, SwitchId srcDpid, SwitchId dstDpid, int srcPort, int dstPort) {
        Switch srcSwitch = Switch.builder().switchId(srcDpid).build();
        Switch dstSwitch = Switch.builder().switchId(dstDpid).build();

        flowPath.getSegments().add(PathSegment.builder()
                .pathId(flowPath.getPathId())
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .srcPort(srcPort)
                .destPort(dstPort)
                .build());
    }
}

