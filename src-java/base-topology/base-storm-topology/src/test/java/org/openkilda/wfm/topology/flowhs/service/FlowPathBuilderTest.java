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

package org.openkilda.wfm.topology.flowhs.service;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.pce.HaPath;
import org.openkilda.pce.Path;
import org.openkilda.pce.Path.Segment;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class FlowPathBuilderTest {
    private static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    private static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    private static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    private static final Switch SWITCH_1 = Switch.builder().switchId(SWITCH_ID_1).build();
    private static final Switch SWITCH_2 = Switch.builder().switchId(SWITCH_ID_2).build();
    private static final Switch SWITCH_3 = Switch.builder().switchId(SWITCH_ID_3).build();
    private static final PathId PATH_ID_1 = new PathId("test_path_id");
    private static final String HA_SUB_FLOW = "ha_sub_flow";

    private FlowPathBuilder builder;

    @BeforeEach
    public void setUp() {
        builder = new FlowPathBuilder();
    }

    @Test
    public void detectSameSwitchPaths() {
        SwitchId switchId = new SwitchId(1);
        Switch switchEntity = Switch.builder().switchId(switchId).build();

        Path path = Path.builder()
                .srcSwitchId(switchId)
                .destSwitchId(switchId)
                .segments(Collections.emptyList())
                .build();

        FlowPath flowPath = FlowPath.builder()
                .srcSwitch(switchEntity)
                .destSwitch(switchEntity)
                .pathId(new PathId("test_path_id"))
                .build();

        Assertions.assertTrue(builder.isSamePath(path, flowPath));
    }

    @Test
    public void detectNotSameSwitchPaths() {
        Path path = Path.builder()
                .srcSwitchId(SWITCH_ID_1)
                .destSwitchId(SWITCH_ID_1)
                .segments(Collections.emptyList())
                .build();

        FlowPath flowPath = FlowPath.builder()
                .srcSwitch(SWITCH_2)
                .destSwitch(SWITCH_2)
                .pathId(new PathId("test_path_id"))
                .build();

        Assertions.assertFalse(builder.isSamePath(path, flowPath));
    }

    @Test
    public void detectSame2SwitchPaths() {
        Path path = Path.builder()
                .srcSwitchId(SWITCH_ID_1)
                .destSwitchId(SWITCH_ID_2)
                .segments(singletonList(buildSegment(SWITCH_ID_1, 1, SWITCH_ID_2, 2)))
                .build();

        PathId flowPathId = new PathId("test_path_id");
        FlowPath flowPath = FlowPath.builder()
                .srcSwitch(SWITCH_1)
                .destSwitch(SWITCH_2)
                .pathId(flowPathId)
                .segments(singletonList(buildPathSegment(flowPathId, SWITCH_1, 1, SWITCH_2, 2)))
                .build();

        Assertions.assertTrue(builder.isSamePath(path, flowPath));
    }

    @Test
    public void detectDifferenceInPortsFor2SwitchPaths() {
        Path path = Path.builder()
                .srcSwitchId(SWITCH_ID_1)
                .destSwitchId(SWITCH_ID_2)
                .segments(singletonList(buildSegment(SWITCH_ID_1, 1, SWITCH_ID_2, 2)))
                .build();

        PathId flowPathId = new PathId("test_path_id");
        FlowPath flowPath = FlowPath.builder()
                .srcSwitch(SWITCH_1)
                .destSwitch(SWITCH_2)
                .pathId(flowPathId)
                .segments(singletonList(PathSegment.builder()
                        .pathId(flowPathId)
                        .srcSwitch(SWITCH_1).srcPort(2).destSwitch(SWITCH_2).destPort(3).build()))
                .build();

        Assertions.assertFalse(builder.isSamePath(path, flowPath));
    }

    @Test
    public void detectSame3SwitchPaths() {
        Path path = Path.builder()
                .srcSwitchId(SWITCH_ID_1)
                .destSwitchId(SWITCH_ID_2)
                .segments(asList(buildSegment(SWITCH_ID_1, 1, SWITCH_ID_3, 2),
                        buildSegment(SWITCH_ID_3, 1, SWITCH_ID_2, 2)))
                .build();

        PathId flowPathId = new PathId("test_path_id");
        FlowPath flowPath = FlowPath.builder()
                .srcSwitch(SWITCH_1)
                .destSwitch(SWITCH_2)
                .pathId(flowPathId)
                .segments(asList(
                        PathSegment.builder().pathId(flowPathId)
                                .srcSwitch(SWITCH_1).srcPort(1).destSwitch(SWITCH_3).destPort(2).build(),
                        PathSegment.builder().pathId(flowPathId)
                                .srcSwitch(SWITCH_3).srcPort(1).destSwitch(SWITCH_2).destPort(2).build()))
                .build();

        Assertions.assertTrue(builder.isSamePath(path, flowPath));
    }

    @Test
    public void buildFlowPathFor1SwitchPath() {
        Path path = Path.builder()
                .srcSwitchId(SWITCH_ID_1)
                .destSwitchId(SWITCH_ID_1)
                .segments(Collections.emptyList())
                .build();

        Flow flow = buildFlow(SWITCH_1, SWITCH_1);
        PathId pathId = new PathId("test_path_id");
        MeterId meterId = new MeterId(MeterId.MIN_FLOW_METER_ID);
        PathResources pathResources = PathResources.builder()
                .pathId(pathId)
                .meterId(meterId)
                .build();
        FlowSegmentCookie cookie = new FlowSegmentCookie(FlowPathDirection.FORWARD, 1);

        FlowPath flowPath = builder.buildFlowPath(flow, pathResources, path, cookie, false, flow.getFlowId());

        Assertions.assertEquals(pathId, flowPath.getPathId());
        Assertions.assertEquals(meterId, flowPath.getMeterId());
        Assertions.assertEquals(cookie, flowPath.getCookie());
        Assertions.assertEquals(0, flowPath.getSegments().size());
    }

    @Test
    public void buildFlowPathFor2SwitchPath() {
        Path path = Path.builder()
                .srcSwitchId(SWITCH_ID_1)
                .destSwitchId(SWITCH_ID_2)
                .segments(singletonList(buildSegment(SWITCH_ID_1, 1, SWITCH_ID_2, 3)))
                .build();

        Flow flow = buildFlow(SWITCH_1, SWITCH_2);
        PathId pathId = new PathId("test_path_id");
        MeterId meterId = new MeterId(MeterId.MIN_FLOW_METER_ID);
        PathResources pathResources = PathResources.builder()
                .pathId(pathId)
                .meterId(meterId)
                .build();
        FlowSegmentCookie cookie = new FlowSegmentCookie(FlowPathDirection.FORWARD, 1);

        FlowPath flowPath = builder.buildFlowPath(flow, pathResources, path, cookie, false, flow.getFlowId());

        Assertions.assertEquals(SWITCH_ID_1, flowPath.getSrcSwitchId());
        Assertions.assertEquals(SWITCH_ID_2, flowPath.getDestSwitchId());
        Assertions.assertEquals(pathId, flowPath.getPathId());
        Assertions.assertEquals(meterId, flowPath.getMeterId());
        Assertions.assertEquals(cookie, flowPath.getCookie());
        Assertions.assertEquals(1, flowPath.getSegments().size());
    }

    @Test
    public void buildFlowPathFor3SwitchPath() {
        Path path = Path.builder()
                .srcSwitchId(SWITCH_ID_1)
                .destSwitchId(SWITCH_ID_2)
                .segments(asList(buildSegment(SWITCH_ID_1, 1, SWITCH_ID_3, 2), 
                        buildSegment(SWITCH_ID_3, 1,  SWITCH_ID_2, 2)))
                .build();

        Flow flow = buildFlow(SWITCH_1, SWITCH_2);
        MeterId meterId = new MeterId(MeterId.MIN_FLOW_METER_ID);
        PathResources pathResources = PathResources.builder()
                .pathId(PATH_ID_1)
                .meterId(meterId)
                .build();
        FlowSegmentCookie cookie = new FlowSegmentCookie(FlowPathDirection.FORWARD, 1);

        FlowPath flowPath = builder.buildFlowPath(flow, pathResources, path, cookie, false, flow.getFlowId());

        Assertions.assertEquals(SWITCH_ID_1, flowPath.getSrcSwitchId());
        Assertions.assertEquals(SWITCH_ID_2, flowPath.getDestSwitchId());
        Assertions.assertEquals(PATH_ID_1, flowPath.getPathId());
        Assertions.assertEquals(meterId, flowPath.getMeterId());
        Assertions.assertEquals(cookie, flowPath.getCookie());
        Assertions.assertEquals(2, flowPath.getSegments().size());
    }

    @Test
    public void flowPathsOverlapped() {
        Path path = Path.builder()
                .srcSwitchId(SWITCH_ID_1)
                .destSwitchId(SWITCH_ID_3)
                .segments(asList(buildSegment(SWITCH_ID_1, 1, SWITCH_ID_2, 2),
                        buildSegment(SWITCH_ID_2, 3, SWITCH_ID_2, 4)))
                .build();

        FlowPath flowPath = FlowPath.builder()
                .srcSwitch(SWITCH_1)
                .destSwitch(SWITCH_2)
                .pathId(PATH_ID_1)
                .segments(ImmutableList.of(buildPathSegment(PATH_ID_1, SWITCH_1, 1, SWITCH_2, 2)))
                .build();

        Assertions.assertTrue(builder.arePathsOverlapped(path, flowPath));
    }

    @Test
    public void flowPathsNotOverlapped() {
        Path path = Path.builder()
                .srcSwitchId(SWITCH_ID_1)
                .destSwitchId(SWITCH_ID_3)
                .segments(asList(buildSegment(SWITCH_ID_1, 1, SWITCH_ID_2, 2),
                        buildSegment(SWITCH_ID_2, 3, SWITCH_ID_2, 4)))
                .build();

        FlowPath flowPath = FlowPath.builder()
                .srcSwitch(SWITCH_1)
                .destSwitch(SWITCH_2)
                .pathId(PATH_ID_1)
                .segments(ImmutableList.of(buildPathSegment(PATH_ID_1, SWITCH_1, 7, SWITCH_2, 8)))
                .build();

        Assertions.assertFalse(builder.arePathsOverlapped(path, flowPath));
    }

    @Test
    public void haFlowPathsOverlapped() {
        Path path = Path.builder()
                .srcSwitchId(SWITCH_ID_1)
                .destSwitchId(SWITCH_ID_3)
                .segments(asList(buildSegment(SWITCH_ID_1, 1, SWITCH_ID_2, 2),
                        buildSegment(SWITCH_ID_2, 3, SWITCH_ID_2, 4)))
                .build();

        HaPath haPath = HaPath.builder().sharedSwitchId(SWITCH_ID_1).yPointSwitchId(SWITCH_ID_2)
                .subPaths(buildPathMap(HA_SUB_FLOW, path)).build();
        FlowPath subPath = FlowPath.builder()
                .srcSwitch(SWITCH_1)
                .destSwitch(SWITCH_2)
                .pathId(PATH_ID_1)
                .segments(singletonList(buildPathSegment(PATH_ID_1, SWITCH_1, 1, SWITCH_2, 2)))
                .build();
        HaSubFlow haSubFlow = HaSubFlow.builder().haSubFlowId(HA_SUB_FLOW).endpointSwitch(SWITCH_2).build();
        subPath.setHaSubFlow(haSubFlow);

        HaFlowPath haFlowPath = HaFlowPath.builder().sharedSwitch(SWITCH_1).haPathId(PATH_ID_1).build();
        haFlowPath.setSubPaths(singletonList(subPath));
        Assertions.assertTrue(builder.arePathsOverlapped(haPath, haFlowPath));
    }

    @Test
    public void haFlowPathsNotOverlapped() {
        Path path = Path.builder()
                .srcSwitchId(SWITCH_ID_1)
                .destSwitchId(SWITCH_ID_3)
                .segments(asList(buildSegment(SWITCH_ID_1, 1, SWITCH_ID_2, 2),
                        buildSegment(SWITCH_ID_2, 3, SWITCH_ID_2, 4)))
                .build();

        HaPath haPath = HaPath.builder().sharedSwitchId(SWITCH_ID_1).yPointSwitchId(SWITCH_ID_2)
                .subPaths(buildPathMap(HA_SUB_FLOW, path)).build();
        FlowPath subPath = FlowPath.builder()
                .srcSwitch(SWITCH_1)
                .destSwitch(SWITCH_2)
                .pathId(PATH_ID_1)
                .segments(singletonList(buildPathSegment(PATH_ID_1, SWITCH_1, 8, SWITCH_2, 9)))
                .build();
        HaSubFlow haSubFlow = HaSubFlow.builder().haSubFlowId(HA_SUB_FLOW).endpointSwitch(SWITCH_2).build();
        subPath.setHaSubFlow(haSubFlow);

        HaFlowPath haFlowPath = HaFlowPath.builder().sharedSwitch(SWITCH_1).haPathId(PATH_ID_1).build();
        haFlowPath.setSubPaths(singletonList(subPath));

        Assertions.assertFalse(builder.arePathsOverlapped(haPath, haFlowPath));
    }

    private static Flow buildFlow(Switch srcSwitch, Switch dstSwitch) {
        return Flow.builder()
                .flowId("test_flow")
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .build();
    }

    private static PathSegment buildPathSegment(
            PathId flowPathId, Switch srcSwitch, int srcPort, Switch dstSwitch, int dstPort) {
        return PathSegment.builder()
                .pathId(flowPathId)
                .srcSwitch(srcSwitch)
                .srcPort(srcPort)
                .destSwitch(dstSwitch)
                .destPort(dstPort)
                .build();
    }

    private static Segment buildSegment(SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort) {
        return Segment.builder()
                .srcSwitchId(srcSwitchId)
                .srcPort(srcPort)
                .destSwitchId(dstSwitchId)
                .destPort(dstPort)
                .build();
    }

    private Map<String, Path> buildPathMap(String subFlowId, Path path) {
        Map<String, Path> map = new HashMap<>();
        map.put(subFlowId, path);
        return map;
    }
}
