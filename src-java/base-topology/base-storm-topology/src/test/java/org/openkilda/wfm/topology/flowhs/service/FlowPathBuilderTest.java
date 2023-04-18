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
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.pce.HaPath;
import org.openkilda.pce.Path;
import org.openkilda.pce.Path.Segment;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Optional;

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

    @Before
    public void setUp() {
        SwitchRepository switchRepository = mock(SwitchRepository.class);
        SwitchPropertiesRepository switchPropertiesRepository = mock(SwitchPropertiesRepository.class);
        KildaConfigurationRepository kildaConfigurationRepository = mock(KildaConfigurationRepository.class);
        when(switchRepository.findById(any())).thenAnswer(invocation ->
                Optional.of(Switch.builder().switchId(invocation.getArgument(0)).build()));
        when(switchPropertiesRepository.findBySwitchId(any())).thenAnswer(invocation -> {
            Switch sw = Switch.builder().switchId(invocation.getArgument(0)).build();
            return Optional.of(SwitchProperties.builder().switchObj(sw).multiTable(false)
                    .supportedTransitEncapsulation(SwitchProperties.DEFAULT_FLOW_ENCAPSULATION_TYPES).build());
        });
        builder = new FlowPathBuilder(switchPropertiesRepository, kildaConfigurationRepository);
    }

    @Test
    public void detectSameSwitchPathsTest() {
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

        assertTrue(builder.isSamePath(path, flowPath));
    }

    @Test
    public void detectNotSameSwitchPathsTest() {
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

        assertFalse(builder.isSamePath(path, flowPath));
    }

    @Test
    public void detectSame2SwitchPathsTest() {
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

        assertTrue(builder.isSamePath(path, flowPath));
    }

    @Test
    public void detectDifferenceInPortsFor2SwitchPathsTest() {
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

        assertFalse(builder.isSamePath(path, flowPath));
    }

    @Test
    public void detectSame3SwitchPathsTest() {
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

        assertTrue(builder.isSamePath(path, flowPath));
    }

    @Test
    public void buildFlowPathFor1SwitchPathTest() {
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

        assertEquals(pathId, flowPath.getPathId());
        assertEquals(meterId, flowPath.getMeterId());
        assertEquals(cookie, flowPath.getCookie());
        assertEquals(0, flowPath.getSegments().size());
    }

    @Test
    public void buildFlowPathFor2SwitchPathTest() {
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

        assertEquals(SWITCH_ID_1, flowPath.getSrcSwitchId());
        assertEquals(SWITCH_ID_2, flowPath.getDestSwitchId());
        assertEquals(pathId, flowPath.getPathId());
        assertEquals(meterId, flowPath.getMeterId());
        assertEquals(cookie, flowPath.getCookie());
        assertEquals(1, flowPath.getSegments().size());
    }

    @Test
    public void buildFlowPathFor3SwitchPathTest() {
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

        assertEquals(SWITCH_ID_1, flowPath.getSrcSwitchId());
        assertEquals(SWITCH_ID_2, flowPath.getDestSwitchId());
        assertEquals(PATH_ID_1, flowPath.getPathId());
        assertEquals(meterId, flowPath.getMeterId());
        assertEquals(cookie, flowPath.getCookie());
        assertEquals(2, flowPath.getSegments().size());
    }

    @Test
    public void flowPathsOverlappedTest() {
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

        assertTrue(builder.arePathsOverlapped(path, flowPath));
    }

    @Test
    public void flowPathsNotOverlappedTest() {
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

        assertFalse(builder.arePathsOverlapped(path, flowPath));
    }

    @Test
    public void haFlowPathsOverlappedTest() {
        Path path = Path.builder()
                .srcSwitchId(SWITCH_ID_1)
                .destSwitchId(SWITCH_ID_3)
                .segments(asList(buildSegment(SWITCH_ID_1, 1, SWITCH_ID_2, 2),
                        buildSegment(SWITCH_ID_2, 3, SWITCH_ID_2, 4)))
                .build();

        HaPath haPath = HaPath.builder().sharedSwitchId(SWITCH_ID_1).yPointSwitchId(SWITCH_ID_2)
                .subPaths(singletonList(path)).build();
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
        assertTrue(builder.arePathsOverlapped(haPath, haFlowPath));
    }

    @Test
    public void haFlowPathsNotOverlappedTest() {
        Path path = Path.builder()
                .srcSwitchId(SWITCH_ID_1)
                .destSwitchId(SWITCH_ID_3)
                .segments(asList(buildSegment(SWITCH_ID_1, 1, SWITCH_ID_2, 2),
                        buildSegment(SWITCH_ID_2, 3, SWITCH_ID_2, 4)))
                .build();

        HaPath haPath = HaPath.builder().sharedSwitchId(SWITCH_ID_1).yPointSwitchId(SWITCH_ID_2)
                .subPaths(singletonList(path)).build();
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

        assertFalse(builder.arePathsOverlapped(haPath, haFlowPath));
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
}
