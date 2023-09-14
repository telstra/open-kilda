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

package org.openkilda.northbound.converter;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.openkilda.messaging.command.haflow.HaFlowDto;
import org.openkilda.messaging.command.haflow.HaFlowPartialUpdateRequest;
import org.openkilda.messaging.command.haflow.HaFlowPathsResponse;
import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.command.haflow.HaFlowRerouteResponse;
import org.openkilda.messaging.command.haflow.HaFlowSyncResponse;
import org.openkilda.messaging.command.haflow.HaSubFlowDto;
import org.openkilda.messaging.command.haflow.HaSubFlowPartialUpdateDto;
import org.openkilda.messaging.command.yflow.FlowPartialUpdateEndpoint;
import org.openkilda.messaging.command.yflow.SubFlowPathDto;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.model.FlowPathDto;
import org.openkilda.messaging.model.FlowPathDto.FlowProtectedPathDto;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.v2.flows.BaseFlowEndpointV2;
import org.openkilda.northbound.dto.v2.flows.FlowPathV2;
import org.openkilda.northbound.dto.v2.haflows.HaFlow;
import org.openkilda.northbound.dto.v2.haflows.HaFlowCreatePayload;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchEndpoint;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPaths;
import org.openkilda.northbound.dto.v2.haflows.HaFlowRerouteResult;
import org.openkilda.northbound.dto.v2.haflows.HaFlowSharedEndpoint;
import org.openkilda.northbound.dto.v2.haflows.HaFlowSyncResult;
import org.openkilda.northbound.dto.v2.haflows.HaFlowSyncResult.SyncSharedPath;
import org.openkilda.northbound.dto.v2.haflows.HaFlowSyncResult.SyncSubFlowPath;
import org.openkilda.northbound.dto.v2.haflows.HaFlowUpdatePayload;
import org.openkilda.northbound.dto.v2.haflows.HaSubFlow;
import org.openkilda.northbound.dto.v2.haflows.HaSubFlowCreatePayload;
import org.openkilda.northbound.dto.v2.haflows.HaSubFlowPatchPayload;
import org.openkilda.northbound.dto.v2.haflows.HaSubFlowUpdatePayload;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@ExtendWith(SpringExtension.class)
public class HaFlowMapperTest {
    private static final String HA_FLOW_ID = "ha_flow_id";
    private static final String SUB_FLOW_1_NAME = "flow_1";
    private static final String SUB_FLOW_2_NAME = "flow_2";
    private static final String FLOW_1 = "flow_1";
    private static final String FLOW_2 = "flow_2";
    private static final String FLOW_3 = "flow_3";
    private static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    private static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    private static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    private static final SwitchId SWITCH_ID_4 = new SwitchId(4);
    private static final int PORT_1 = 1;
    private static final int PORT_2 = 2;
    private static final int PORT_3 = 3;
    private static final int PORT_4 = 4;
    private static final int VLAN_1 = 4;
    private static final int VLAN_2 = 5;
    private static final int VLAN_3 = 6;
    private static final int VLAN_4 = 7;
    private static final int VLAN_5 = 8;
    private static final int INNER_VLAN_1 = 9;
    private static final int INNER_VLAN_2 = 10;
    private static final int INNER_VLAN_3 = 11;
    private static final int BANDWIDTH = 100;
    private static final long MILLION = 1_000_000L;
    private static final long MAX_LATENCY = 10L;
    private static final long MAX_LATENCY_TIER_2 = 20L;
    private static final int PRIORITY = 15;
    private static final String DESC_1 = "desc1";
    private static final String DESC_2 = "desc2";
    private static final String DESC_3 = "desc3";
    private static final String ERROR = "some error";
    private static final String STATUS_INFO = "some info";
    private static final HaSubFlowCreatePayload CREATE_SUB_FLOW_1 = new HaSubFlowCreatePayload(
            new BaseFlowEndpointV2(SWITCH_ID_1, PORT_1, VLAN_1, INNER_VLAN_1), DESC_2);
    private static final HaSubFlowCreatePayload CREATE_SUB_FLOW_2 = new HaSubFlowCreatePayload(
            new BaseFlowEndpointV2(SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2), DESC_3);
    private static final HaSubFlowUpdatePayload UPDATE_SUB_FLOW_1 = HaSubFlowUpdatePayload.builder()
            .flowId(SUB_FLOW_1_NAME).endpoint(new BaseFlowEndpointV2(SWITCH_ID_1, PORT_1, VLAN_1, INNER_VLAN_1))
            .description(DESC_2).build();
    private static final HaSubFlowUpdatePayload UPDATE_SUB_FLOW_2 = HaSubFlowUpdatePayload.builder()
            .flowId(SUB_FLOW_2_NAME).endpoint(new BaseFlowEndpointV2(SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2))
            .description(DESC_3).build();
    private static final HaFlowSharedEndpoint SHARED_ENDPOINT = new HaFlowSharedEndpoint(
            SWITCH_ID_3, PORT_3, VLAN_3, INNER_VLAN_3);

    @Autowired
    private HaFlowMapper mapper;

    @Test
    public void createRequest() {
        HaFlowCreatePayload request = new HaFlowCreatePayload(
                HA_FLOW_ID, SHARED_ENDPOINT, BANDWIDTH, PathComputationStrategy.COST.name(),
                FlowEncapsulationType.VXLAN.name(), MAX_LATENCY, MAX_LATENCY_TIER_2, true, false, true, PRIORITY,
                false, DESC_1, true, FLOW_3, newArrayList(CREATE_SUB_FLOW_1, CREATE_SUB_FLOW_2));

        HaFlowRequest result = mapper.toHaFlowCreateRequest(request);
        assertEquals(request.getHaFlowId(), result.getHaFlowId());
        assertEquals(request.getMaximumBandwidth(), result.getMaximumBandwidth());
        assertEquals(request.getPathComputationStrategy(), result.getPathComputationStrategy().toString());
        assertEquals(request.getEncapsulationType(), result.getEncapsulationType().toString());
        assertEquals((Long) (request.getMaxLatency() * MILLION), result.getMaxLatency());
        assertEquals((Long) (request.getMaxLatencyTier2() * MILLION), result.getMaxLatencyTier2());
        assertEquals(request.isIgnoreBandwidth(), result.isIgnoreBandwidth());
        assertEquals(request.isPeriodicPings(), result.isPeriodicPings());
        assertEquals(request.isPinned(), result.isPinned());
        assertEquals(request.getPriority(), result.getPriority());
        assertEquals(request.isStrictBandwidth(), result.isStrictBandwidth());
        assertEquals(request.getDescription(), result.getDescription());
        assertEquals(request.isAllocateProtectedPath(), result.isAllocateProtectedPath());
        assertEquals(request.getDiverseFlowId(), result.getDiverseFlowId());
        assertSharedEndpoint(request.getSharedEndpoint(), result.getSharedEndpoint());

        assertEquals(2, result.getSubFlows().size());
        assertSubFlow(request.getSubFlows().get(0), result.getSubFlows().get(0));
        assertSubFlow(request.getSubFlows().get(1), result.getSubFlows().get(1));
    }

    @Test
    public void updateRequest() {
        HaFlowUpdatePayload request = new HaFlowUpdatePayload(
                SHARED_ENDPOINT, BANDWIDTH, PathComputationStrategy.COST.name(), FlowEncapsulationType.VXLAN.name(),
                MAX_LATENCY, MAX_LATENCY_TIER_2, true, false, true, PRIORITY, false, DESC_1, true, FLOW_3,
                newArrayList(UPDATE_SUB_FLOW_1, UPDATE_SUB_FLOW_2));

        HaFlowRequest result = mapper.toHaFlowUpdateRequest(HA_FLOW_ID, request);
        assertEquals(HA_FLOW_ID, result.getHaFlowId());
        assertEquals(request.getMaximumBandwidth(), result.getMaximumBandwidth());
        assertEquals(request.getPathComputationStrategy(), result.getPathComputationStrategy().toString());
        assertEquals(request.getEncapsulationType(), result.getEncapsulationType().toString());
        assertEquals((Long) (request.getMaxLatency() * MILLION), result.getMaxLatency());
        assertEquals((Long) (request.getMaxLatencyTier2() * MILLION), result.getMaxLatencyTier2());
        assertEquals(request.isIgnoreBandwidth(), result.isIgnoreBandwidth());
        assertEquals(request.isPeriodicPings(), result.isPeriodicPings());
        assertEquals(request.isPinned(), result.isPinned());
        assertEquals(request.getPriority(), result.getPriority());
        assertEquals(request.isStrictBandwidth(), result.isStrictBandwidth());
        assertEquals(request.getDescription(), result.getDescription());
        assertEquals(request.isAllocateProtectedPath(), result.isAllocateProtectedPath());
        assertEquals(request.getDiverseFlowId(), result.getDiverseFlowId());

        assertEquals(2, result.getSubFlows().size());
        assertSubFlow(request.getSubFlows().get(0), result.getSubFlows().get(0));
        assertSubFlow(request.getSubFlows().get(1), result.getSubFlows().get(1));
    }

    @Test
    public void patchRequest() {
        HaFlowPatchPayload request = new HaFlowPatchPayload(
                new HaFlowPatchEndpoint(SWITCH_ID_3, PORT_3, VLAN_3, INNER_VLAN_3), (long) BANDWIDTH,
                PathComputationStrategy.COST.name(), FlowEncapsulationType.VXLAN.name(), MAX_LATENCY,
                MAX_LATENCY_TIER_2, true, false, true, PRIORITY, false, DESC_1, true, FLOW_3,
                newArrayList(
                        new HaSubFlowPatchPayload(SUB_FLOW_1_NAME,
                                new HaFlowPatchEndpoint(SWITCH_ID_1, PORT_1, VLAN_1, VLAN_2), DESC_2),
                        new HaSubFlowPatchPayload(SUB_FLOW_2_NAME,
                                new HaFlowPatchEndpoint(SWITCH_ID_2, PORT_2, VLAN_4, VLAN_5), DESC_3)));

        HaFlowPartialUpdateRequest result = mapper.toHaFlowPatchRequest(HA_FLOW_ID, request);
        assertEquals(HA_FLOW_ID, result.getHaFlowId());
        assertEquals(request.getMaximumBandwidth(), result.getMaximumBandwidth());
        assertEquals(request.getPathComputationStrategy(), result.getPathComputationStrategy().toString());
        assertEquals(request.getEncapsulationType(), result.getEncapsulationType().toString());
        assertEquals((Long) (request.getMaxLatency() * MILLION), result.getMaxLatency());
        assertEquals((Long) (request.getMaxLatencyTier2() * MILLION), result.getMaxLatencyTier2());
        assertEquals(request.getIgnoreBandwidth(), result.getIgnoreBandwidth());
        assertEquals(request.getPeriodicPings(), result.getPeriodicPings());
        assertEquals(request.getPinned(), result.getPinned());
        assertEquals(request.getPriority(), result.getPriority());
        assertEquals(request.getStrictBandwidth(), result.getStrictBandwidth());
        assertEquals(request.getDescription(), result.getDescription());
        assertEquals(request.getAllocateProtectedPath(), result.getAllocateProtectedPath());
        assertEquals(request.getDiverseFlowId(), result.getDiverseFlowId());
        assertSharedEndpoint(request.getSharedEndpoint(), result.getSharedEndpoint());

        assertEquals(2, result.getSubFlows().size());
        assertSubFlow(request.getSubFlows().get(0), result.getSubFlows().get(0));
        assertSubFlow(request.getSubFlows().get(1), result.getSubFlows().get(1));
    }

    @Test
    public void getResponse() {
        HaFlowDto response = new HaFlowDto(
                HA_FLOW_ID, FlowStatus.UP, STATUS_INFO, new FlowEndpoint(SWITCH_ID_3, PORT_3, VLAN_3, INNER_VLAN_3),
                BANDWIDTH, PathComputationStrategy.COST, FlowEncapsulationType.VXLAN, MAX_LATENCY * MILLION,
                MAX_LATENCY_TIER_2 * MILLION, true, false, true, PRIORITY, false, DESC_1, true,
                newHashSet(FLOW_1), newHashSet(FLOW_2), newHashSet(FLOW_3),
                newArrayList(
                        HaSubFlowDto.builder().flowId(SUB_FLOW_1_NAME)
                                .endpoint(new FlowEndpoint(SWITCH_ID_1, PORT_1))
                                .status(FlowStatus.UP)
                                .description(DESC_2)
                                .timeCreate(Instant.MIN)
                                .timeUpdate(Instant.MAX)
                                .build(),
                        HaSubFlowDto.builder().flowId(SUB_FLOW_2_NAME)
                                .endpoint(new FlowEndpoint(SWITCH_ID_2, PORT_2))
                                .status(FlowStatus.UP)
                                .description(DESC_3)
                                .timeCreate(Instant.MAX)
                                .timeUpdate(Instant.MIN)
                                .build()), Instant.MIN, Instant.MAX);

        HaFlow result = mapper.toHaFlow(response);
        assertEquals(HA_FLOW_ID, result.getHaFlowId());
        assertEquals(response.getMaximumBandwidth(), result.getMaximumBandwidth());
        assertEquals(response.getPathComputationStrategy().toString().toLowerCase(),
                result.getPathComputationStrategy());
        assertEquals(response.getEncapsulationType().toString().toLowerCase(), result.getEncapsulationType());
        assertEquals(MAX_LATENCY, result.getMaxLatency().longValue());
        assertEquals(MAX_LATENCY_TIER_2, result.getMaxLatencyTier2().longValue());
        assertEquals(response.isIgnoreBandwidth(), result.isIgnoreBandwidth());
        assertEquals(response.isPeriodicPings(), result.isPeriodicPings());
        assertEquals(response.isPinned(), result.isPinned());
        assertEquals(response.getPriority(), result.getPriority());
        assertEquals(response.isStrictBandwidth(), result.isStrictBandwidth());
        assertEquals(response.getDescription(), result.getDescription());
        assertEquals(response.isAllocateProtectedPath(), result.isAllocateProtectedPath());
        assertEquals(response.getDiverseWithFlows(), result.getDiverseWithFlows());
        assertEquals(response.getStatus().toString(), result.getStatus().toUpperCase());
        assertEquals(response.getStatusInfo(), result.getStatusInfo());
        assertEquals(response.getDiverseWithFlows(), result.getDiverseWithFlows());
        assertEquals(response.getDiverseWithYFlows(), result.getDiverseWithYFlows());
        assertEquals(response.getDiverseWithHaFlows(), result.getDiverseWithHaFlows());
        assertSharedEndpoint(response.getSharedEndpoint(), result.getSharedEndpoint());

        assertEquals(2, result.getSubFlows().size());
        assertSubFlow(response.getSubFlows().get(0), result.getSubFlows().get(0));
        assertSubFlow(response.getSubFlows().get(1), result.getSubFlows().get(1));
    }

    @Test
    public void toRerouteResult() {
        PathInfoData sharedPath = buildPathInfoData(MAX_LATENCY, SWITCH_ID_1);
        List<SubFlowPathDto> subFlowPaths = newArrayList(
                new SubFlowPathDto(FLOW_1, buildPathInfoData(MAX_LATENCY_TIER_2, SWITCH_ID_2)));
        HaFlowRerouteResponse response = new HaFlowRerouteResponse(sharedPath, subFlowPaths, true);

        HaFlowRerouteResult result = mapper.toRerouteResult(response);
        assertEquals(1, result.getSharedPath().getNodes().size());
        assertEquals(1, result.getSharedPath().getNodes().get(0).getPortNo());
        assertPathNode(sharedPath.getPath().get(0), result.getSharedPath().getNodes().get(0));
        assertEquals(1, result.getSubFlowPaths().size());
        assertEquals(subFlowPaths.get(0).getFlowId(), result.getSubFlowPaths().get(0).getFlowId());
        assertEquals(1, result.getSubFlowPaths().get(0).getNodes().size());
        assertPathNode(subFlowPaths.get(0).getPath().getPath().get(0),
                result.getSubFlowPaths().get(0).getNodes().get(0));
    }

    @Test
    public void mapToHaFlowPaths() {
        FlowPathDto sharedPath = FlowPathDto.builder()
                .forwardPath(newArrayList(
                        new PathNodePayload(SWITCH_ID_1, PORT_1, PORT_2),
                        new PathNodePayload(SWITCH_ID_2, PORT_1, PORT_2)
                ))
                .reversePath(newArrayList(
                        new PathNodePayload(SWITCH_ID_2, PORT_2, PORT_1),
                        new PathNodePayload(SWITCH_ID_1, PORT_2, PORT_1)
                ))
                .protectedPath(FlowProtectedPathDto.builder()
                        .forwardPath(newArrayList(
                                new PathNodePayload(SWITCH_ID_1, PORT_3, PORT_4),
                                new PathNodePayload(SWITCH_ID_2, PORT_3, PORT_4)
                        ))
                        .reversePath(newArrayList(
                                new PathNodePayload(SWITCH_ID_2, PORT_4, PORT_3),
                                new PathNodePayload(SWITCH_ID_1, PORT_4, PORT_3)
                        ))
                        .build())
                .build();

        FlowPathDto firstSubFlow = FlowPathDto.builder()
                .id(SUB_FLOW_1_NAME)
                .forwardPath(newArrayList(
                        new PathNodePayload(SWITCH_ID_1, PORT_1, PORT_2),
                        new PathNodePayload(SWITCH_ID_2, PORT_1, PORT_2),
                        new PathNodePayload(SWITCH_ID_3, PORT_1, PORT_2)
                ))
                .reversePath(newArrayList(
                        new PathNodePayload(SWITCH_ID_3, PORT_2, PORT_1),
                        new PathNodePayload(SWITCH_ID_2, PORT_2, PORT_1),
                        new PathNodePayload(SWITCH_ID_1, PORT_2, PORT_1)
                ))
                .protectedPath(FlowProtectedPathDto.builder()
                        .forwardPath(newArrayList(
                                new PathNodePayload(SWITCH_ID_1, PORT_3, PORT_4),
                                new PathNodePayload(SWITCH_ID_2, PORT_3, PORT_4),
                                new PathNodePayload(SWITCH_ID_3, PORT_3, PORT_4)
                        ))
                        .reversePath(newArrayList(
                                new PathNodePayload(SWITCH_ID_3, PORT_4, PORT_3),
                                new PathNodePayload(SWITCH_ID_2, PORT_4, PORT_3),
                                new PathNodePayload(SWITCH_ID_1, PORT_4, PORT_3)
                        ))
                        .build())
                .build();

        FlowPathDto secondSubFlow = FlowPathDto.builder()
                .id(SUB_FLOW_2_NAME)
                .forwardPath(newArrayList(
                        new PathNodePayload(SWITCH_ID_1, PORT_1, PORT_2),
                        new PathNodePayload(SWITCH_ID_2, PORT_1, PORT_2),
                        new PathNodePayload(SWITCH_ID_4, PORT_1, PORT_2)
                ))
                .reversePath(newArrayList(
                        new PathNodePayload(SWITCH_ID_4, PORT_2, PORT_1),
                        new PathNodePayload(SWITCH_ID_2, PORT_2, PORT_1),
                        new PathNodePayload(SWITCH_ID_1, PORT_2, PORT_1)
                ))
                .protectedPath(FlowProtectedPathDto.builder()
                        .forwardPath(newArrayList(
                                new PathNodePayload(SWITCH_ID_1, PORT_3, PORT_4),
                                new PathNodePayload(SWITCH_ID_2, PORT_3, PORT_4),
                                new PathNodePayload(SWITCH_ID_4, PORT_3, PORT_4)
                        ))
                        .reversePath(newArrayList(
                                new PathNodePayload(SWITCH_ID_4, PORT_4, PORT_3),
                                new PathNodePayload(SWITCH_ID_2, PORT_4, PORT_3),
                                new PathNodePayload(SWITCH_ID_1, PORT_4, PORT_3)
                        ))
                        .build())
                .build();

        HaFlowPathsResponse response = new HaFlowPathsResponse(sharedPath,
                newArrayList(firstSubFlow, secondSubFlow), Collections.emptyMap());
        HaFlowPaths result = mapper.toHaFlowPaths(response);
        assertEquals(response.getSharedPath().getForwardPath(), result.getSharedPath().getForward());
        assertEquals(response.getSharedPath().getReversePath(), result.getSharedPath().getReverse());
        assertEquals(response.getSharedPath().getProtectedPath().getForwardPath(),
                result.getSharedPath().getProtectedPath().getForward());
        assertEquals(response.getSharedPath().getProtectedPath().getReversePath(),
                result.getSharedPath().getProtectedPath().getReverse());
        assertEquals(response.getSubFlowPaths().get(0).getId(), result.getSubFlowPaths().get(0).getFlowId());
        assertEquals(response.getSubFlowPaths().get(0).getForwardPath(), result.getSubFlowPaths().get(0).getForward());
        assertEquals(response.getSubFlowPaths().get(0).getReversePath(), result.getSubFlowPaths().get(0).getReverse());
        assertEquals(response.getSubFlowPaths().get(0).getProtectedPath().getForwardPath(),
                result.getSubFlowPaths().get(0).getProtectedPath().getForward());
        assertEquals(response.getSubFlowPaths().get(0).getProtectedPath().getReversePath(),
                result.getSubFlowPaths().get(0).getProtectedPath().getReverse());
        assertEquals(response.getSubFlowPaths().get(1).getId(), result.getSubFlowPaths().get(1).getFlowId());
        assertEquals(response.getSubFlowPaths().get(1).getForwardPath(), result.getSubFlowPaths().get(1).getForward());
        assertEquals(response.getSubFlowPaths().get(1).getReversePath(), result.getSubFlowPaths().get(1).getReverse());
        assertEquals(response.getSubFlowPaths().get(1).getProtectedPath().getForwardPath(),
                result.getSubFlowPaths().get(1).getProtectedPath().getForward());
        assertEquals(response.getSubFlowPaths().get(1).getProtectedPath().getReversePath(),
                result.getSubFlowPaths().get(1).getProtectedPath().getReverse());
    }

    @Test
    public void mapToSyncResult() {
        PathInfoData sharedPath = buildPathInfoData(MAX_LATENCY, SWITCH_ID_1);
        PathInfoData protectedSharedPath = buildPathInfoData(MAX_LATENCY, SWITCH_ID_2);
        List<SubFlowPathDto> subFlowPaths = newArrayList(
                new SubFlowPathDto(FLOW_1, buildPathInfoData(MAX_LATENCY_TIER_2, SWITCH_ID_3)));
        List<SubFlowPathDto> protectedSubFlowPaths = newArrayList(
                new SubFlowPathDto(FLOW_1, buildPathInfoData(MAX_LATENCY_TIER_2, SWITCH_ID_4)));
        Set<SwitchId> unsyncedSwitchIds = Sets.newHashSet(SWITCH_ID_2, SWITCH_ID_3);

        HaFlowSyncResponse response = new HaFlowSyncResponse(sharedPath, subFlowPaths, protectedSharedPath,
                protectedSubFlowPaths, unsyncedSwitchIds, ERROR, true);

        HaFlowSyncResult result = mapper.toSyncResult(response);
        assertSyncSharedPaths(sharedPath, result.getSharedPath());
        assertSyncSharedPaths(protectedSharedPath, result.getProtectedSharedPath());
        assertSyncSubPaths(subFlowPaths, result.getSubFlowPaths());
        assertSyncSubPaths(protectedSubFlowPaths, result.getProtectedSubFlowPaths());
        assertEquals(unsyncedSwitchIds, result.getUnsyncedSwitches());
        assertEquals(ERROR, result.getError());
        assertTrue(response.isSynced());
    }

    private PathInfoData buildPathInfoData(long latency, SwitchId switchId1) {
        PathInfoData pathInfoData = new PathInfoData();
        pathInfoData.setLatency(latency);
        pathInfoData.setPath(Collections.singletonList(new PathNode(switchId1, 1, 1, 1L, 1L)));
        return pathInfoData;
    }

    private void assertSyncSharedPaths(PathInfoData expected, SyncSharedPath actual) {
        assertEquals(expected.getPath().size(), actual.getNodes().size());
        for (int i = 0; i < expected.getPath().size(); i++) {
            assertPathNode(expected.getPath().get(i), actual.getNodes().get(i));
        }
    }

    private void assertSyncSubPaths(List<SubFlowPathDto> expected, List<SyncSubFlowPath> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertSyncSubPath(expected.get(i), actual.get(i));
        }
    }

    private void assertSyncSubPath(SubFlowPathDto expected, SyncSubFlowPath actual) {
        assertEquals(expected.getFlowId(), actual.getFlowId());
        assertEquals(expected.getPath().getPath().size(), actual.getNodes().size());

        for (int i = 0; i < expected.getPath().getPath().size(); i++) {
            assertPathNode(expected.getPath().getPath().get(i), actual.getNodes().get(i));
        }
    }

    private void assertSubFlow(HaSubFlowUpdatePayload expected, HaSubFlowDto actual) {
        assertSubFlow((HaSubFlowCreatePayload) expected, actual);
        assertEquals(expected.getFlowId(), actual.getFlowId());
    }

    private void assertSubFlow(HaSubFlowCreatePayload expected, HaSubFlowDto actual) {
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getEndpoint().getSwitchId(), actual.getEndpoint().getSwitchId());
        assertEquals(expected.getEndpoint().getPortNumber(), actual.getEndpoint().getPortNumber());
        assertEquals(expected.getEndpoint().getVlanId(), actual.getEndpoint().getOuterVlanId());
        assertEquals(expected.getEndpoint().getInnerVlanId(), actual.getEndpoint().getInnerVlanId());
    }

    private void assertSubFlow(HaSubFlowPatchPayload expected, HaSubFlowPartialUpdateDto actual) {
        assertEquals(expected.getFlowId(), actual.getFlowId());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getEndpoint().getSwitchId(), actual.getEndpoint().getSwitchId());
        assertEquals(expected.getEndpoint().getPortNumber(), actual.getEndpoint().getPortNumber());
        assertEquals(expected.getEndpoint().getVlanId(), actual.getEndpoint().getVlanId());
        assertEquals(expected.getEndpoint().getInnerVlanId(), actual.getEndpoint().getInnerVlanId());
    }

    private void assertSubFlow(HaSubFlowDto expected, HaSubFlow actual) {
        assertEquals(expected.getFlowId(), actual.getFlowId());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getEndpoint().getSwitchId(), actual.getEndpoint().getSwitchId());
        assertEquals(expected.getEndpoint().getPortNumber(), actual.getEndpoint().getPortNumber());
        assertEquals(expected.getEndpoint().getOuterVlanId(), actual.getEndpoint().getVlanId());
        assertEquals(expected.getEndpoint().getInnerVlanId(), actual.getEndpoint().getInnerVlanId());
    }

    private void assertSharedEndpoint(HaFlowSharedEndpoint expected, FlowEndpoint actual) {
        assertEquals(expected.getSwitchId(), actual.getSwitchId());
        assertEquals(expected.getPortNumber(), actual.getPortNumber().intValue());
        assertEquals(expected.getVlanId(), actual.getOuterVlanId());
        assertEquals(expected.getInnerVlanId(), actual.getInnerVlanId());
    }

    private void assertSharedEndpoint(HaFlowPatchEndpoint expected, FlowPartialUpdateEndpoint actual) {
        assertEquals(expected.getSwitchId(), actual.getSwitchId());
        assertEquals(expected.getPortNumber(), actual.getPortNumber());
        assertEquals(expected.getVlanId(), actual.getVlanId());
        assertEquals(expected.getInnerVlanId(), actual.getInnerVlanId());
    }

    private void assertSharedEndpoint(FlowEndpoint expected, HaFlowSharedEndpoint actual) {
        assertEquals(expected.getSwitchId(), actual.getSwitchId());
        assertEquals(expected.getPortNumber().intValue(), actual.getPortNumber());
        assertEquals(expected.getOuterVlanId(), actual.getVlanId());
        assertEquals(expected.getInnerVlanId(), actual.getInnerVlanId());
    }

    private void assertPathNode(PathNode expected, FlowPathV2.PathNodeV2 actual) {
        assertEquals(expected.getPortNo(), actual.getPortNo());
        assertEquals(expected.getSegLatency(), actual.getSegmentLatency());
        assertEquals(expected.getSwitchId(), actual.getSwitchId());
    }

    @TestConfiguration
    @ComponentScan({"org.openkilda.northbound.converter"})
    static class Config {
        // nothing to define here
    }
}
