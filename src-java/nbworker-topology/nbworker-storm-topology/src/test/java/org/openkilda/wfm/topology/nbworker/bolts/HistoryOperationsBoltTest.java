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

package org.openkilda.wfm.topology.nbworker.bolts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.nbtopology.request.GetFlowHistoryRequest;
import org.openkilda.messaging.payload.history.HaFlowHistoryEntry;
import org.openkilda.messaging.payload.history.HaFlowPathPayload;
import org.openkilda.messaging.payload.history.HaSubFlowPayload;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.GroupId;
import org.openkilda.model.HaFlow;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.history.DumpType;
import org.openkilda.model.history.HaFlowEvent;
import org.openkilda.model.history.HaFlowEvent.HaFlowEventDataImpl;
import org.openkilda.model.history.HaFlowEventAction;
import org.openkilda.model.history.HaFlowEventAction.HaFlowEventActionDataImpl;
import org.openkilda.model.history.HaFlowEventDump;
import org.openkilda.model.history.HaFlowEventDump.HaFlowEventDumpDataImpl;
import org.openkilda.model.history.HaFlowEventDump.HaSubFlowDumpWrapper;
import org.openkilda.model.history.HaFlowEventDump.PathNodePayload;
import org.openkilda.wfm.share.history.service.HistoryService;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class HistoryOperationsBoltTest {

    public static final String ENTRY_HA_FLOW_ID = "HA flow id";
    public static final String ACTOR = "Actor";
    public static final Instant TIMESTAMP = Instant.now();
    public static final String DETAILS = "details";
    public static final String EVENT_ACTION = "event action";
    public static final String EVENT_DETAILS = "event details";
    public static final String EVENT_TASK_ID = "task ID";
    public static final Instant EVENT_TIMESTAMP = Instant.now().plus(1, ChronoUnit.MINUTES);
    public static final String AFFINITY_GROUP_ID = "AFFINITY_GROUP_ID";
    public static final boolean ALLOCATE_PROTECTED_PATH = false;
    public static final String DESCRIPTION = "DESCRIPTION";
    public static final String DIVERSE_GROUP_ID = "DIVERSE_GROUP_ID";
    public static final DumpType DUMP_TYPE = DumpType.STATE_BEFORE;
    public static final FlowEncapsulationType FLOW_ENCAPSULATION_TYPE = FlowEncapsulationType.VXLAN;
    public static final String FLOW_TIME_CREATE = Instant.now().minus(1, ChronoUnit.HOURS).toString();
    public static final String FLOW_TIME_MODIFY = Instant.now().toString();
    public static final String HA_FLOW_ID_IN_DUMP = "HA flow ID in dump";
    public static final boolean IGNORE_BANDWIDTH = true;
    public static final long MAXIMUM_BANDWIDTH = 100_500L;
    public static final long MAX_LATENCY = 100L;
    public static final long MAX_LATENCY_TIER_2 = 1000L;
    public static final PathComputationStrategy PATH_COMPUTATION_STRATEGY = PathComputationStrategy.COST;
    public static final boolean PINNED = true;
    public static final int PRIORITY = 4;
    public static final boolean PERIODIC_PINGS = false;
    public static final FlowStatus FLOW_STATUS = FlowStatus.UP;
    public static final int SHARED_PORT = 1;
    public static final int SHARED_INNER_VLAN = 1000;
    public static final int SHARED_OUTER_VLAN = 2000;
    public static final String SHARED_SWITCH_ID = "00:00:FF:FF:FF:FF:00:01";
    public static final boolean STRICT_BANDWIDTH = true;
    public static final String DUMP_TASK_ID = "correlationId";
    public static final String ACTION = "CREATE action";
    public static final String TASK_ID = "correlation ID";
    public static final String HA_FLOW_PATH_ID = "HA flow path ID";
    public static final GroupId Y_POINT_GROUP_ID = GroupId.MIN_FLOW_GROUP_ID;
    public static final String Y_POINT_SWITCH_ID = "00:03";
    public static final MeterId Y_POINT_METER_ID = MeterId.LACP_REPLY_METER_ID;
    public static final String PATH_TIME_CREATE = Instant.now().minus(1, ChronoUnit.HOURS).toString();
    public static final String PATH_TIME_MODIFY = Instant.now().toString();
    public static final String SHARED_POINT_METER_ID = MeterId.LACP_REPLY_METER_ID.toString();
    public static final boolean PATH_IGNORE_BANDWIDTH = true;
    public static final String PATH_STATUS = "ACTIVE";
    public static final long PATH_BANDWIDTH = 9000L;
    public static final String COOKIE = "0x4000000000000000";
    public static final String SUBFLOW_DUMP_HA_SUB_FLOW_ID = "HA_SUB_FLOW_ID";
    public static final String SUBFLOW_DUMP_HA_FLOW_ID = "HA_FLOW_ID";
    public static final FlowStatus SUBFLOW_DUMP_STATUS = FlowStatus.DEGRADED;
    public static final String SUBFLOW_DUMP_ENDPOINT_SWITCH_ID = "00:12:13:14:15";
    public static final int SUBFLOW_DUMP_ENDPOINT_PORT = 1;
    public static final int SUBFLOW_DUMP_ENDPOINT_VLAN = 2;
    public static final int SUBFLOW_DUMP_ENDPOINT_INNER_VLAN = 1500;
    public static final String SUBFLOW_DUMP_DESCRIPTION = "Subflow DESCRIPTION";
    public static final String SUBFLOW_DUMP_TIME_CREATE = Instant.now().minus(4, ChronoUnit.HOURS).toString();
    public static final String SUBFLOW_DUMP_TIME_MODIFY = Instant.now().minus(5, ChronoUnit.HOURS).toString();

    private HistoryOperationsBolt historyOperationsBolt;

    @BeforeEach
    public void setUp() {
        HistoryService historyService = mock(HistoryService.class);
        when(historyService.getHaFlowHistoryEvents(any(), any(), any(), anyInt())).thenReturn(getHaFlowEventList());
        historyOperationsBolt = new HistoryOperationsBolt(null, historyService);
    }

    @Test
    public void mappersSanityCheck() {
        GetFlowHistoryRequest request = GetFlowHistoryRequest.builder()
                .modelType(HaFlow.class)
                .build();
        List<InfoData> result = historyOperationsBolt.processRequest(null, request);

        assertNotNull(result);
        assertNotNull(result.get(0));
        assertTrue(result.get(0) instanceof HaFlowHistoryEntry);
        HaFlowHistoryEntry entry = (HaFlowHistoryEntry) result.get(0);

        assertEquals(ENTRY_HA_FLOW_ID, entry.getHaFlowId());
        assertEquals(ACTOR, entry.getActor());
        assertEquals(ACTION, entry.getAction());
        assertEquals(DETAILS, entry.getDetails());
        assertEquals(TASK_ID, entry.getTaskId());
        assertEquals(TIMESTAMP, entry.getTime());
        assertEquals(TIMESTAMP.atOffset(ZoneOffset.UTC).toString(), entry.getTimestampIso());

        assertNotNull(entry.getPayloads());
        assertEquals(1, entry.getPayloads().size());
        assertEquals(EVENT_ACTION, entry.getPayloads().get(0).getAction());
        assertEquals(EVENT_TIMESTAMP, entry.getPayloads().get(0).getTimestamp());
        assertEquals(EVENT_TIMESTAMP.atOffset(ZoneOffset.UTC).toString(), entry.getPayloads().get(0).getTimestampIso());
        assertEquals(EVENT_DETAILS, entry.getPayloads().get(0).getDetails());


        assertNotNull(entry.getDumps());
        assertEquals(1, entry.getDumps().size());
        assertEquals(AFFINITY_GROUP_ID, entry.getDumps().get(0).getAffinityGroupId());
        assertEquals(ALLOCATE_PROTECTED_PATH, entry.getDumps().get(0).getAllocateProtectedPath());
        assertEquals(DESCRIPTION, entry.getDumps().get(0).getDescription());
        assertEquals(DIVERSE_GROUP_ID, entry.getDumps().get(0).getDiverseGroupId());
        assertEquals(DUMP_TYPE, entry.getDumps().get(0).getDumpType());
        assertEquals(FLOW_ENCAPSULATION_TYPE, entry.getDumps().get(0).getEncapsulationType());
        assertEquals(FLOW_TIME_CREATE, entry.getDumps().get(0).getFlowTimeCreate());
        assertEquals(FLOW_TIME_MODIFY, entry.getDumps().get(0).getFlowTimeModify());
        assertEquals(HA_FLOW_ID_IN_DUMP, entry.getDumps().get(0).getHaFlowId());
        assertEquals(IGNORE_BANDWIDTH, entry.getDumps().get(0).getIgnoreBandwidth());
        assertEquals(Long.valueOf(MAXIMUM_BANDWIDTH), entry.getDumps().get(0).getMaximumBandwidth());
        assertEquals(Long.valueOf(MAX_LATENCY), entry.getDumps().get(0).getMaxLatency());
        assertEquals(Long.valueOf(MAX_LATENCY_TIER_2), entry.getDumps().get(0).getMaxLatencyTier2());
        assertEquals(PINNED, entry.getDumps().get(0).getPinned());
        assertEquals(Integer.valueOf(PRIORITY), entry.getDumps().get(0).getPriority());
        assertEquals(Integer.valueOf(SHARED_INNER_VLAN), entry.getDumps().get(0).getSharedInnerVlan());
        assertEquals(Integer.valueOf(SHARED_PORT), entry.getDumps().get(0).getSharedPort());
        assertEquals(Integer.valueOf(SHARED_OUTER_VLAN), entry.getDumps().get(0).getSharedOuterVlan());
        assertEquals(FLOW_STATUS, entry.getDumps().get(0).getStatus());
        assertEquals(STRICT_BANDWIDTH, entry.getDumps().get(0).getStrictBandwidth());
        assertEquals(SHARED_SWITCH_ID, entry.getDumps().get(0).getSharedSwitchId());
        assertEquals(DUMP_TASK_ID, entry.getDumps().get(0).getTaskId());

        assertNotNull(entry.getDumps().get(0).getForwardPath());
        HaFlowPathPayload path = entry.getDumps().get(0).getForwardPath();
        assertEquals(HA_FLOW_PATH_ID, path.getHaPathId());
        assertEquals(PATH_TIME_MODIFY, path.getTimeModify());
        assertEquals(PATH_TIME_CREATE, path.getTimeCreate());
        assertEquals(Long.valueOf(PATH_BANDWIDTH), path.getBandwidth());
        assertEquals(COOKIE, path.getCookie());
        assertEquals(PATH_STATUS, path.getStatus().toString());
        assertEquals(PATH_IGNORE_BANDWIDTH, path.getIgnoreBandwidth());
        assertEquals(Y_POINT_GROUP_ID.toString(), path.getYPointGroupId());
        assertEquals(Y_POINT_SWITCH_ID, path.getYPointSwitchId());
        assertEquals(Y_POINT_METER_ID.toString(), path.getYPointMeterId());

        assertNotNull(entry.getDumps());
        assertEquals(1, entry.getDumps().size());
        assertNotNull(entry.getDumps().get(0).getHaSubFlows());
        assertEquals(1, entry.getDumps().get(0).getHaSubFlows().size());

        HaSubFlowPayload subFlowPayload = entry.getDumps().get(0).getHaSubFlows().get(0);
        assertEquals(SUBFLOW_DUMP_HA_SUB_FLOW_ID, subFlowPayload.getHaSubFlowId());
        assertEquals(Integer.valueOf(SUBFLOW_DUMP_ENDPOINT_PORT), subFlowPayload.getEndpointPort());
        assertEquals(Integer.valueOf(SUBFLOW_DUMP_ENDPOINT_VLAN), subFlowPayload.getEndpointVlan());
        assertEquals(Integer.valueOf(SUBFLOW_DUMP_ENDPOINT_INNER_VLAN), subFlowPayload.getEndpointInnerVlan());
        assertEquals(SUBFLOW_DUMP_DESCRIPTION, subFlowPayload.getDescription());
        assertEquals(SUBFLOW_DUMP_STATUS, subFlowPayload.getStatus());
        assertEquals(SUBFLOW_DUMP_HA_FLOW_ID, subFlowPayload.getHaFlowId());
        assertEquals(SUBFLOW_DUMP_TIME_CREATE, subFlowPayload.getTimeCreate());
        assertEquals(SUBFLOW_DUMP_TIME_MODIFY, subFlowPayload.getTimeModify());
    }

    private List<HaFlowEvent> getHaFlowEventList() {
        HaFlowEventDataImpl data = HaFlowEventDataImpl.builder()
                .haFlowId(ENTRY_HA_FLOW_ID)
                .actor(ACTOR)
                .timestamp(TIMESTAMP)
                .details(DETAILS)
                .action(ACTION)
                .taskId(TASK_ID)
                .eventActions(Lists.newArrayList(new HaFlowEventAction(HaFlowEventActionDataImpl.builder()
                        .action(EVENT_ACTION)
                        .details(EVENT_DETAILS)
                        .taskId(EVENT_TASK_ID)
                        .timestamp(EVENT_TIMESTAMP)
                        .build())))
                .eventDumps(Lists.newArrayList(new HaFlowEventDump(HaFlowEventDumpDataImpl.builder()
                        .affinityGroupId(AFFINITY_GROUP_ID)
                        .allocateProtectedPath(ALLOCATE_PROTECTED_PATH)
                        .description(DESCRIPTION)
                        .diverseGroupId(DIVERSE_GROUP_ID)
                        .dumpType(DUMP_TYPE)
                        .encapsulationType(FLOW_ENCAPSULATION_TYPE)
                        .flowTimeCreate(FLOW_TIME_CREATE)
                        .flowTimeModify(FLOW_TIME_MODIFY)
                        .haFlowId(HA_FLOW_ID_IN_DUMP)
                        .ignoreBandwidth(IGNORE_BANDWIDTH)
                        .maximumBandwidth(MAXIMUM_BANDWIDTH)
                        .maxLatency(MAX_LATENCY)
                        .maxLatencyTier2(MAX_LATENCY_TIER_2)
                        .pathComputationStrategy(PATH_COMPUTATION_STRATEGY)
                        .pinned(PINNED)
                        .priority(PRIORITY)
                        .periodicPings(PERIODIC_PINGS)
                        .status(FLOW_STATUS)
                        .sharedPort(SHARED_PORT)
                        .sharedInnerVlan(SHARED_INNER_VLAN)
                        .sharedOuterVlan(SHARED_OUTER_VLAN)
                        .sharedSwitchId(SHARED_SWITCH_ID)
                        .strictBandwidth(STRICT_BANDWIDTH)
                        .taskId(DUMP_TASK_ID)
                        .haSubFlows(createPersistenceHaSubFlowDumpWrapper())
                        .protectedForwardPath(createPersistenceHaFlowPathDump(FlowPathDirection.FORWARD))
                        .protectedReversePath(null)
                        .forwardPath(createPersistenceHaFlowPathDump(FlowPathDirection.FORWARD))
                        .reversePath(createPersistenceHaFlowPathDump(FlowPathDirection.REVERSE))
                        .build())))
                .build();

        HaFlowEvent haFlowEvent = new HaFlowEvent(data);

        return Lists.newArrayList(haFlowEvent);
    }

    private HaSubFlowDumpWrapper createPersistenceHaSubFlowDumpWrapper() {
        return HaSubFlowDumpWrapper.builder()
                .haSubFlowDumpList(Lists.newArrayList(createPersistenceHaSubFlowDump()))
                .build();
    }

    private HaFlowEventDump.HaFlowPathDump createPersistenceHaFlowPathDump(FlowPathDirection direction) {
        List<PathNodePayload> pathNodes = new ArrayList<>();
        pathNodes.add(PathNodePayload.builder().switchId("00:01").inputPort(1).outputPort(2).build());
        pathNodes.add(PathNodePayload.builder().switchId("00:02").inputPort(3).outputPort(4).build());
        pathNodes.add(PathNodePayload.builder().switchId("00:03").inputPort(5).outputPort(6).build());
        List<List<PathNodePayload>> pathNodesList = new ArrayList<>();
        pathNodesList.add(pathNodes);

        return HaFlowEventDump.HaFlowPathDump.builder()
                .haPathId(HA_FLOW_PATH_ID)
                .yPointGroupId(Y_POINT_GROUP_ID.toString())
                .yPointSwitchId(Y_POINT_SWITCH_ID)
                .yPointMeterId(Y_POINT_METER_ID.toString())
                .timeCreate(PATH_TIME_CREATE)
                .timeModify(PATH_TIME_MODIFY)
                .sharedPointMeterId(SHARED_POINT_METER_ID)
                .cookie(FlowSegmentCookie.builder().direction(direction).build().toString())
                .ignoreBandwidth(PATH_IGNORE_BANDWIDTH)
                .bandwidth(PATH_BANDWIDTH)
                .status(PATH_STATUS)
                .paths(pathNodesList)
                .haSubFlows(Lists.newArrayList(createPersistenceHaSubFlowDump()))
                .build();
    }

    private HaFlowEventDump.HaSubFlowDump createPersistenceHaSubFlowDump() {
        return HaFlowEventDump.HaSubFlowDump.builder()
                .haSubFlowId(SUBFLOW_DUMP_HA_SUB_FLOW_ID)
                .haFlowId(SUBFLOW_DUMP_HA_FLOW_ID)
                .status(SUBFLOW_DUMP_STATUS)
                .endpointSwitchId(SUBFLOW_DUMP_ENDPOINT_SWITCH_ID)
                .endpointPort(SUBFLOW_DUMP_ENDPOINT_PORT)
                .endpointVlan(SUBFLOW_DUMP_ENDPOINT_VLAN)
                .endpointInnerVlan(SUBFLOW_DUMP_ENDPOINT_INNER_VLAN)
                .description(SUBFLOW_DUMP_DESCRIPTION)
                .timeCreate(SUBFLOW_DUMP_TIME_CREATE)
                .timeModify(SUBFLOW_DUMP_TIME_MODIFY)
                .build();
    }
}
