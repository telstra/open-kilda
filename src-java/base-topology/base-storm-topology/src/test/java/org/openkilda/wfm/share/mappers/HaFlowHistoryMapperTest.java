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

package org.openkilda.wfm.share.mappers;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.openkilda.messaging.payload.history.HaFlowDumpPayload;
import org.openkilda.messaging.payload.history.HaFlowHistoryEntry;
import org.openkilda.messaging.payload.history.HaFlowHistoryPayload;
import org.openkilda.messaging.payload.history.HaFlowPathPayload;
import org.openkilda.messaging.payload.history.HaSubFlowPayload;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.GroupId;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.history.HaFlowEvent;
import org.openkilda.model.history.HaFlowEventAction;
import org.openkilda.model.history.HaFlowEventDump;
import org.openkilda.model.history.HaFlowEventDump.HaFlowEventDumpDataImpl;
import org.openkilda.model.history.HaFlowEventDump.HaSubFlowDumpWrapper;
import org.openkilda.model.history.HaFlowEventDump.PathNodePayload;
import org.openkilda.wfm.share.history.model.DumpType;
import org.openkilda.wfm.share.history.model.HaFlowDumpData;
import org.openkilda.wfm.share.history.model.HaFlowEventData;
import org.openkilda.wfm.share.history.model.HaFlowEventData.Event;
import org.openkilda.wfm.share.history.model.HaFlowEventData.Initiator;
import org.openkilda.wfm.share.history.model.HaFlowHistoryData;
import org.openkilda.wfm.share.history.model.HaFlowPathDump;
import org.openkilda.wfm.share.history.model.HaSubFlowDump;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class HaFlowHistoryMapperTest {

    public static final String DETAILS = "details";
    public static final Event EVENT_CREATE = Event.CREATE;
    public static final String ACTOR = "actor";
    public static final String CORRELATION_ID = "correlation id";
    public static final String FAKE_TIMESTAMP = "fake timestamp";
    private static final String HA_FLOW_ID = "HA flow ID";
    public static final Initiator INITIATOR = Initiator.NB;
    public static final String HISTORY_ACTION = "Ha flow history action";
    private static final Integer SHARED_PORT = 1;
    private static final Integer SHARED_OUTER_VLAN = 1000;
    private static final Integer SHARED_INNER_VLAN = 2000;
    private static final Long MAXIMUM_BANDWIDTH = 100_000L;
    private static final PathComputationStrategy PATH_COMPUTATION_STRATEGY = PathComputationStrategy.COST;
    private static final FlowEncapsulationType FLOW_ENCAPSULATION_TYPE = FlowEncapsulationType.VXLAN;
    private static final Long MAX_LATENCY = 42L;
    private static final Long MAX_LATENCY_TIER_2 = 84L;
    private static final Boolean IGNORE_BANDWIDTH = false;
    private static final Boolean PERIODIC_PINGS = false;
    private static final Boolean PINNED = false;
    private static final Integer PRIORITY = 455;
    public static final String STATUS_INFO = "Status info";
    private static final Boolean STRICT_BANDWIDTH = false;
    private static final String DESCRIPTION = "HA flow description";
    private static final Boolean ALLOCATE_PROTECTED_PATH = true;
    private static final FlowStatus FLOW_STATUS = FlowStatus.UP;
    private static final String AFFINITY_GROUP_ID = "affinity group ID";
    private static final String DIVERSE_GROUP_ID = "diverse group ID";
    private static final Switch SHARED_SWITCH = Switch.builder().switchId(new SwitchId("00:00:01")).build();
    private static final Instant TIME_CREATE = Instant.now().minus(1, ChronoUnit.HOURS);
    private static final Instant TIME_MODIFY = Instant.now();
    private static final FlowPathStatus FLOW_PATH_STATUS = FlowPathStatus.ACTIVE;
    private static final String HA_SUB_FLOW_ID = "HA sub flow ID";
    private static final SwitchId ENDPOINT_SWITCH_ID = new SwitchId("00:01");
    private static final Integer ENDPOINT_PORT = 1;
    private static final Integer ENDPOINT_VLAN = 1000;
    private static final Integer ENDPOINT_INNER_VLAN = 2000;

    private final HaFlowHistoryMapper mapper = HaFlowHistoryMapper.INSTANCE;

    @Test
    public void createHaFlowEvent() {
        HaFlowEventData source = HaFlowEventData.builder()
                .event(EVENT_CREATE)
                .details(DETAILS)
                .initiator(INITIATOR)
                .haFlowId(HA_FLOW_ID)
                .time(TIME_CREATE)
                .build();
        HaFlowEvent result = mapper.createHaFlowEvent(source);

        assertEquals(source.getHaFlowId(), result.getHaFlowId());
        assertEquals(source.getDetails(), result.getDetails());
        assertEquals(source.getInitiator().toString(), result.getActor());
        assertEquals(source.getTime(), result.getTimestamp());
        assertEquals(source.getEvent().getDescription(), result.getAction());
    }

    @Test
    public void createHaFlowEventAction() {
        HaFlowHistoryData source = HaFlowHistoryData.builder()
                .action(HISTORY_ACTION)
                .description(DESCRIPTION)
                .haFlowId(HA_FLOW_ID)
                .time(TIME_CREATE)
                .build();

        HaFlowEventAction result = mapper.createHaFlowEventAction(source);

        assertEquals(source.getTime(), result.getTimestamp());
        assertEquals(source.getAction(), result.getAction());
        assertEquals(source.getDescription(), result.getDetails());
    }

    @Test
    public void createHaFlowHistoryEntries() {
        HaFlowEvent haFlowEvent = HaFlowEvent.builder()
                .haFlowId(HA_FLOW_ID)
                .actor(ACTOR)
                .timestamp(TIME_CREATE)
                .taskId(CORRELATION_ID)
                .details(DETAILS)
                .build();
        List<HaFlowHistoryPayload> payloads = Lists.newArrayList(HaFlowHistoryPayload.builder()
                        .details(DETAILS)
                        .timestampIso(FAKE_TIMESTAMP)
                        .action(HISTORY_ACTION)
                        .timestamp(TIME_CREATE)
                .build());
        List<HaFlowDumpPayload> dumps = Lists.newArrayList(HaFlowDumpPayload.builder()
                .affinityGroupId(AFFINITY_GROUP_ID)
                .allocateProtectedPath(ALLOCATE_PROTECTED_PATH)
                .description(DESCRIPTION)
                .diverseGroupId(DIVERSE_GROUP_ID)
                .dumpType(org.openkilda.model.history.DumpType.STATE_BEFORE)
                .encapsulationType(FLOW_ENCAPSULATION_TYPE)
                .flowTimeCreate(TIME_CREATE.toString())
                .flowTimeModify(TIME_MODIFY.toString())
                .haFlowId(HA_FLOW_ID)
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
                .sharedSwitchId(SHARED_SWITCH.getSwitchId().toString())
                .strictBandwidth(STRICT_BANDWIDTH)
                .taskId(CORRELATION_ID)
                .haSubFlows(Lists.newArrayList(createHaSubFlowPayload()))
                .protectedForwardPath(createHaFlowPathPayload(FlowPathDirection.FORWARD))
                .protectedReversePath(createHaFlowPathPayload(FlowPathDirection.REVERSE))
                .forwardPath(createHaFlowPathPayload(FlowPathDirection.FORWARD))
                .reversePath(createHaFlowPathPayload(FlowPathDirection.REVERSE))
                .build());

        HaFlowHistoryEntry result = mapper.createHaFlowHistoryEntry(haFlowEvent, payloads, dumps);

        assertEquals(haFlowEvent.getHaFlowId(), result.getHaFlowId());
        assertEquals(haFlowEvent.getDetails(), result.getDetails());
        assertEquals(haFlowEvent.getTaskId(), result.getTaskId());
        assertEquals(haFlowEvent.getActor(), result.getActor());
        assertEquals(haFlowEvent.getAction(), result.getAction());
        assertEquals(haFlowEvent.getTimestamp(), result.getTime());
        assertEquals(TIME_CREATE.atOffset(ZoneOffset.UTC).toString(), result.getTimestampIso());

        assertNotNull(result.getDumps());
        assertTrue(result.getDumps().size() > 0);
        HaFlowDumpPayload resultDump = result.getDumps().get(0);
        
        assertEquals(HA_FLOW_ID, resultDump.getHaFlowId());
        assertEquals(SHARED_PORT, resultDump.getSharedPort());
        assertEquals(SHARED_OUTER_VLAN, resultDump.getSharedOuterVlan());
        assertEquals(SHARED_INNER_VLAN, resultDump.getSharedInnerVlan());
        assertEquals(MAXIMUM_BANDWIDTH, resultDump.getMaximumBandwidth());
        assertEquals(PATH_COMPUTATION_STRATEGY, resultDump.getPathComputationStrategy());
        assertEquals(FLOW_ENCAPSULATION_TYPE, resultDump.getEncapsulationType());
        assertEquals(MAX_LATENCY, resultDump.getMaxLatency());
        assertEquals(MAX_LATENCY_TIER_2, resultDump.getMaxLatencyTier2());
        assertEquals(IGNORE_BANDWIDTH, resultDump.getIgnoreBandwidth());
        assertEquals(PERIODIC_PINGS, resultDump.getPeriodicPings());
        assertEquals(PINNED, resultDump.getPinned());
        assertEquals(PRIORITY, resultDump.getPriority());
        assertEquals(STRICT_BANDWIDTH, resultDump.getStrictBandwidth());
        assertEquals(DESCRIPTION, resultDump.getDescription());
        assertEquals(ALLOCATE_PROTECTED_PATH, resultDump.getAllocateProtectedPath());
        assertEquals(FLOW_STATUS, resultDump.getStatus());
        assertEquals(AFFINITY_GROUP_ID, resultDump.getAffinityGroupId());
        assertEquals(DIVERSE_GROUP_ID, resultDump.getDiverseGroupId());

        assertNotNull(result.getPayloads());
        assertTrue(result.getPayloads().size() > 0);
        HaFlowHistoryPayload resultPayload = result.getPayloads().get(0);

        assertEquals(FAKE_TIMESTAMP, resultPayload.getTimestampIso());
        assertEquals(DETAILS, resultPayload.getDetails());
        assertEquals(TIME_CREATE, resultPayload.getTimestamp());
        assertEquals(HISTORY_ACTION, resultPayload.getAction());
    }

    private HaFlowPathPayload createHaFlowPathPayload(FlowPathDirection direction) {
        List<org.openkilda.messaging.payload.flow.PathNodePayload> pathNodes = new ArrayList<>();
        pathNodes.add(new org.openkilda.messaging.payload.flow.PathNodePayload(new SwitchId("00:01"), 1, 2));
        pathNodes.add(new org.openkilda.messaging.payload.flow.PathNodePayload(new SwitchId("00:02"), 2, 3));
        pathNodes.add(new org.openkilda.messaging.payload.flow.PathNodePayload(new SwitchId("00:03"), 4, 5));
        List<List<org.openkilda.messaging.payload.flow.PathNodePayload>> pathNodesList = new ArrayList<>();
        pathNodesList.add(pathNodes);

        List<HaSubFlowPayload> haSubFlowDump = Lists.newArrayList(createHaSubFlowPayload());
        
        return HaFlowPathPayload.builder()
                .haPathId(new PathId("HA flow path ID").toString())
                .yPointGroupId(GroupId.MIN_FLOW_GROUP_ID.toString())
                .yPointSwitchId(new SwitchId("00:03").toString())
                .yPointMeterId(MeterId.LACP_REPLY_METER_ID.toString())
                .timeCreate(TIME_CREATE.toString())
                .timeModify(TIME_MODIFY.toString())
                .sharedPointMeterId(MeterId.LACP_REPLY_METER_ID.toString())
                .cookie(FlowSegmentCookie.builder().direction(direction).build().toString())
                .ignoreBandwidth(IGNORE_BANDWIDTH)
                .status(FLOW_PATH_STATUS)
                .paths(pathNodesList)
                .haSubFlows(haSubFlowDump)
                .build();
    }
    
    private HaSubFlowPayload createHaSubFlowPayload() {
        return HaSubFlowPayload.builder()
                .haSubFlowId(HA_SUB_FLOW_ID)
                .haFlowId(HA_FLOW_ID)
                .status(FLOW_STATUS)
                .endpointSwitchId(ENDPOINT_SWITCH_ID.toString())
                .endpointPort(ENDPOINT_PORT)
                .endpointVlan(ENDPOINT_VLAN)
                .endpointInnerVlan(ENDPOINT_INNER_VLAN)
                .description(DESCRIPTION)
                .timeCreate(TIME_CREATE.toString())
                .timeModify(TIME_MODIFY.toString())
                .build();
    }
    
    @Test
    public void persistenceToPayload() {
        HaFlowEventDump source =
                createHaFlowEventDump(org.openkilda.model.history.DumpType.STATE_BEFORE, "correlation ID");

        HaFlowDumpPayload result = mapper.persistenceToPayload(source);

        assertEquals(source.getHaFlowId(), result.getHaFlowId());
        assertEquals("correlation ID", result.getTaskId());
        assertEquals(source.getSharedPort(), result.getSharedPort());
        assertEquals(source.getSharedOuterVlan(), result.getSharedOuterVlan());
        assertEquals(source.getSharedInnerVlan(), result.getSharedInnerVlan());
        assertEquals(source.getMaximumBandwidth(), result.getMaximumBandwidth());
        assertEquals(source.getPathComputationStrategy(), result.getPathComputationStrategy());
        assertEquals(source.getEncapsulationType(), result.getEncapsulationType());
        assertEquals(source.getMaxLatency(), result.getMaxLatency());
        assertEquals(source.getMaxLatencyTier2(), result.getMaxLatencyTier2());
        assertEquals(source.getIgnoreBandwidth(), result.getIgnoreBandwidth());
        assertEquals(source.getPeriodicPings(), result.getPeriodicPings());
        assertEquals(source.getPinned(), result.getPinned());
        assertEquals(source.getPriority(), result.getPriority());
        assertEquals(source.getStrictBandwidth(), result.getStrictBandwidth());
        assertEquals(source.getDescription(), result.getDescription());
        assertEquals(source.getAllocateProtectedPath(), result.getAllocateProtectedPath());
        assertEquals(source.getStatus(), result.getStatus());
        assertEquals(source.getStatusInfo(), result.getStatusInfo());
        assertEquals(source.getAffinityGroupId(), result.getAffinityGroupId());
        assertEquals(source.getDiverseGroupId(), result.getDiverseGroupId());

        assertNotNull(result.getForwardPath());
        assertEquals(source.getForwardPath().getHaPathId(), result.getForwardPath().getHaPathId());
        assertEquals(source.getForwardPath().getBandwidth(), result.getForwardPath().getBandwidth());
        assertEquals(source.getForwardPath().getCookie(), result.getForwardPath().getCookie());
        assertEquals(source.getForwardPath().getSharedPointMeterId(),
                result.getForwardPath().getSharedPointMeterId());
        assertEquals(source.getForwardPath().getSharedSwitchId(),
                result.getForwardPath().getSharedSwitchId());
        assertEquals(source.getForwardPath().getTimeCreate(), result.getForwardPath().getTimeCreate());
        assertEquals(source.getForwardPath().getTimeModify(), result.getForwardPath().getTimeModify());
        assertEquals(source.getForwardPath().getYPointGroupId(),
                result.getForwardPath().getYPointGroupId());
        assertEquals(source.getForwardPath().getYPointMeterId(),
                result.getForwardPath().getYPointMeterId());
        assertEquals(source.getForwardPath().getYPointSwitchId(),
                result.getForwardPath().getYPointSwitchId());
        assertEquals(source.getForwardPath().getStatus(), result.getForwardPath().getStatus().toString());

        assertNotNull(result.getReversePath());
        assertEquals(source.getReversePath().getHaPathId(), result.getReversePath().getHaPathId());
        assertEquals(source.getReversePath().getBandwidth(), result.getReversePath().getBandwidth());
        assertEquals(source.getReversePath().getCookie(), result.getReversePath().getCookie());
        assertEquals(source.getReversePath().getSharedPointMeterId(),
                result.getReversePath().getSharedPointMeterId());
        assertEquals(source.getReversePath().getSharedSwitchId(),
                result.getReversePath().getSharedSwitchId());
        assertEquals(source.getReversePath().getTimeCreate(), result.getReversePath().getTimeCreate());
        assertEquals(source.getReversePath().getTimeModify(), result.getReversePath().getTimeModify());
        assertEquals(source.getReversePath().getYPointGroupId(), result.getReversePath().getYPointGroupId());
        assertEquals(source.getReversePath().getYPointMeterId(), result.getReversePath().getYPointMeterId());
        assertEquals(source.getReversePath().getYPointSwitchId(),
                result.getReversePath().getYPointSwitchId());
        assertEquals(source.getReversePath().getStatus(), result.getReversePath().getStatus().toString());
    }

    @Test
    public void messagingToPersistence() {
        HaFlowDumpData source = createHaFlowDumpData(DumpType.STATE_AFTER, "correlation ID");
        HaFlowEventDumpDataImpl result = mapper.createHaFlowEventDump(source);

        assertEquals(source.getHaFlowId(), result.getHaFlowId());
        assertEquals(source.getDumpType().toString(), result.getDumpType().toString());
        assertEquals(source.getSharedPort(), result.getSharedPort());
        assertEquals(source.getSharedOuterVlan(), result.getSharedOuterVlan());
        assertEquals(source.getSharedInnerVlan(), result.getSharedInnerVlan());
        assertEquals(source.getMaximumBandwidth(), result.getMaximumBandwidth());
        assertEquals(source.getPathComputationStrategy(), result.getPathComputationStrategy());
        assertEquals(source.getEncapsulationType(), result.getEncapsulationType());
        assertEquals(source.getMaxLatency(), result.getMaxLatency());
        assertEquals(source.getMaxLatencyTier2(), result.getMaxLatencyTier2());
        assertEquals(source.getIgnoreBandwidth(), result.getIgnoreBandwidth());
        assertEquals(source.getPeriodicPings(), result.getPeriodicPings());
        assertEquals(source.getPinned(), result.getPinned());
        assertEquals(source.getPriority(), result.getPriority());
        assertEquals(source.getStrictBandwidth(), result.getStrictBandwidth());
        assertEquals(source.getDescription(), result.getDescription());
        assertEquals(source.getAllocateProtectedPath(), result.getAllocateProtectedPath());
        assertEquals(source.getStatus(), result.getStatus());
        assertEquals(source.getAffinityGroupId(), result.getAffinityGroupId());
        assertEquals(source.getDiverseGroupId(), result.getDiverseGroupId());

        assertNotNull(result.getForwardPath());
        assertEquals(source.getForwardPath().getHaPathId().toString(), result.getForwardPath().getHaPathId());
        assertEquals(source.getForwardPath().getBandwidth(), result.getForwardPath().getBandwidth());
        assertEquals(source.getForwardPath().getCookie().toString(), result.getForwardPath().getCookie());
        assertEquals(source.getForwardPath().getSharedPointMeterId().toString(),
                result.getForwardPath().getSharedPointMeterId());
        assertEquals(source.getForwardPath().getSharedSwitchId().toString(),
                result.getForwardPath().getSharedSwitchId());
        assertEquals(source.getForwardPath().getTimeCreate().toString(), result.getForwardPath().getTimeCreate());
        assertEquals(source.getForwardPath().getTimeModify().toString(), result.getForwardPath().getTimeModify());
        assertEquals(source.getForwardPath().getYPointGroupId().toString(),
                result.getForwardPath().getYPointGroupId());
        assertEquals(source.getForwardPath().getYPointMeterId().toString(),
                result.getForwardPath().getYPointMeterId());
        assertEquals(source.getForwardPath().getYPointSwitchId().toString(),
                result.getForwardPath().getYPointSwitchId());
        assertEquals(source.getForwardPath().getStatus().toString(), result.getForwardPath().getStatus());

        assertNotNull(result.getReversePath());
        assertEquals(source.getReversePath().getHaPathId().toString(), result.getReversePath().getHaPathId());
        assertEquals(source.getReversePath().getBandwidth(), result.getReversePath().getBandwidth());
        assertEquals(source.getReversePath().getCookie().toString(), result.getReversePath().getCookie());
        assertEquals(source.getReversePath().getSharedPointMeterId().toString(),
                result.getReversePath().getSharedPointMeterId());
        assertEquals(source.getReversePath().getSharedSwitchId().toString(),
                result.getReversePath().getSharedSwitchId());
        assertEquals(source.getReversePath().getTimeCreate().toString(), result.getReversePath().getTimeCreate());
        assertEquals(source.getReversePath().getTimeModify().toString(), result.getReversePath().getTimeModify());
        assertEquals(source.getReversePath().getYPointGroupId().toString(), result.getReversePath().getYPointGroupId());
        assertEquals(source.getReversePath().getYPointMeterId().toString(), result.getReversePath().getYPointMeterId());
        assertEquals(source.getReversePath().getYPointSwitchId().toString(),
                result.getReversePath().getYPointSwitchId());
        assertEquals(source.getReversePath().getStatus().toString(), result.getReversePath().getStatus());
    }

    @Test
    public void createHaFlowDump() {
        HaFlow source = createHaFlow();

        HaFlowDumpData dump = mapper.toHaFlowDumpData(source, "correlation ID", DumpType.STATE_AFTER);

        assertEquals(source.getHaFlowId(), dump.getHaFlowId());
        assertEquals(Integer.valueOf(source.getSharedPort()), dump.getSharedPort());
        assertEquals(Integer.valueOf(source.getSharedOuterVlan()), dump.getSharedOuterVlan());
        assertEquals(Integer.valueOf(source.getSharedInnerVlan()), dump.getSharedInnerVlan());
        assertEquals(Long.valueOf(source.getMaximumBandwidth()), dump.getMaximumBandwidth());
        assertEquals(source.getPathComputationStrategy(), dump.getPathComputationStrategy());
        assertEquals(source.getEncapsulationType(), dump.getEncapsulationType());
        assertEquals(source.getMaxLatency(), dump.getMaxLatency());
        assertEquals(source.getMaxLatencyTier2(), dump.getMaxLatencyTier2());
        assertEquals(Boolean.valueOf(source.isIgnoreBandwidth()), dump.getIgnoreBandwidth());
        assertEquals(Boolean.valueOf(source.isPeriodicPings()), dump.getPeriodicPings());
        assertEquals(Boolean.valueOf(source.isPinned()), dump.getPinned());
        assertEquals(source.getPriority(), dump.getPriority());
        assertEquals(Boolean.valueOf(source.isStrictBandwidth()), dump.getStrictBandwidth());
        assertEquals(source.getDescription(), dump.getDescription());
        assertEquals(Boolean.valueOf(source.isAllocateProtectedPath()), dump.getAllocateProtectedPath());
        assertEquals(source.getStatus(), dump.getStatus());
        assertEquals(source.getStatusInfo(), dump.getStatusInfo());
        assertEquals(source.getAffinityGroupId(), dump.getAffinityGroupId());
        assertEquals(source.getDiverseGroupId(), dump.getDiverseGroupId());

        assertNotNull(dump.getForwardPath());
        assertEquals(source.getForwardPath().getHaPathId(), dump.getForwardPath().getHaPathId());
        assertEquals(Long.valueOf(source.getForwardPath().getBandwidth()), dump.getForwardPath().getBandwidth());
        assertEquals(source.getForwardPath().getCookie(), dump.getForwardPath().getCookie());
        assertEquals(source.getForwardPath().getSharedPointMeterId(), dump.getForwardPath().getSharedPointMeterId());
        assertEquals(source.getForwardPath().getSharedSwitchId(), dump.getForwardPath().getSharedSwitchId());
        assertEquals(source.getForwardPath().getTimeCreate(), dump.getForwardPath().getTimeCreate());
        assertEquals(source.getForwardPath().getTimeModify(), dump.getForwardPath().getTimeModify());
        assertEquals(source.getForwardPath().getYPointGroupId(), dump.getForwardPath().getYPointGroupId());
        assertEquals(source.getForwardPath().getYPointMeterId(), dump.getForwardPath().getYPointMeterId());
        assertEquals(source.getForwardPath().getYPointSwitchId(), dump.getForwardPath().getYPointSwitchId());
        assertEquals(source.getForwardPath().getStatus(), dump.getForwardPath().getStatus());

        assertNotNull(dump.getReversePath());
        assertEquals(source.getReversePath().getHaPathId(), dump.getReversePath().getHaPathId());
        assertEquals(Long.valueOf(source.getReversePath().getBandwidth()), dump.getReversePath().getBandwidth());
        assertEquals(source.getReversePath().getCookie(), dump.getReversePath().getCookie());
        assertEquals(source.getReversePath().getSharedPointMeterId(), dump.getReversePath().getSharedPointMeterId());
        assertEquals(source.getReversePath().getSharedSwitchId(), dump.getReversePath().getSharedSwitchId());
        assertEquals(source.getReversePath().getTimeCreate(), dump.getReversePath().getTimeCreate());
        assertEquals(source.getReversePath().getTimeModify(), dump.getReversePath().getTimeModify());
        assertEquals(source.getReversePath().getYPointGroupId(), dump.getReversePath().getYPointGroupId());
        assertEquals(source.getReversePath().getYPointMeterId(), dump.getReversePath().getYPointMeterId());
        assertEquals(source.getReversePath().getYPointSwitchId(), dump.getReversePath().getYPointSwitchId());
        assertEquals(source.getReversePath().getStatus(), dump.getReversePath().getStatus());
    }

    private HaFlowEventDump createHaFlowEventDump(org.openkilda.model.history.DumpType dumpType, String correlationId) {
        return new HaFlowEventDump(HaFlowEventDumpDataImpl.builder()
                .affinityGroupId(AFFINITY_GROUP_ID)
                .allocateProtectedPath(ALLOCATE_PROTECTED_PATH)
                .description(DESCRIPTION)
                .diverseGroupId(DIVERSE_GROUP_ID)
                .dumpType(dumpType)
                .encapsulationType(FLOW_ENCAPSULATION_TYPE)
                .flowTimeCreate(TIME_CREATE.toString())
                .flowTimeModify(TIME_MODIFY.toString())
                .haFlowId(HA_FLOW_ID)
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
                .sharedSwitchId(SHARED_SWITCH.getSwitchId().toString())
                .strictBandwidth(STRICT_BANDWIDTH)
                .taskId(correlationId)
                .haSubFlows(createPersistenceHaSubFlowDumpWrapper())
                .protectedForwardPath(createPersistenceHaFlowPathDump(FlowPathDirection.FORWARD))
                .protectedReversePath(createPersistenceHaFlowPathDump(FlowPathDirection.REVERSE))
                .forwardPath(createPersistenceHaFlowPathDump(FlowPathDirection.FORWARD))
                .reversePath(createPersistenceHaFlowPathDump(FlowPathDirection.REVERSE))
                .build());
    }

    private HaFlowEventDump.HaFlowPathDump createPersistenceHaFlowPathDump(FlowPathDirection direction) {
        List<PathNodePayload> pathNodes = new ArrayList<>();
        pathNodes.add(PathNodePayload.builder().switchId("00:01").inputPort(1).outputPort(2).build());
        pathNodes.add(PathNodePayload.builder().switchId("00:02").inputPort(3).outputPort(4).build());
        pathNodes.add(PathNodePayload.builder().switchId("00:03").inputPort(5).outputPort(6).build());
        List<List<PathNodePayload>> pathNodesList = new ArrayList<>();
        pathNodesList.add(pathNodes);
        
        return HaFlowEventDump.HaFlowPathDump.builder()
                .haPathId("HA flow path ID")
                .yPointGroupId(GroupId.MIN_FLOW_GROUP_ID.toString())
                .yPointSwitchId("00:03")
                .yPointMeterId(MeterId.LACP_REPLY_METER_ID.toString())
                .timeCreate(TIME_CREATE.toString())
                .timeModify(TIME_MODIFY.toString())
                .sharedPointMeterId(MeterId.LACP_REPLY_METER_ID.toString())
                .cookie(FlowSegmentCookie.builder().direction(direction).build().toString())
                .ignoreBandwidth(IGNORE_BANDWIDTH)
                .status(FLOW_PATH_STATUS.toString())
                .paths(pathNodesList)
                .haSubFlows(Lists.newArrayList(createPersistenceHaSubFlowDump()))
                .build();
    }

    private HaFlowEventDump.HaSubFlowDump createPersistenceHaSubFlowDump() {
        return HaFlowEventDump.HaSubFlowDump.builder()
                .haSubFlowId(HA_SUB_FLOW_ID)
                .haFlowId(HA_FLOW_ID)
                .status(FLOW_STATUS)
                .endpointSwitchId(ENDPOINT_SWITCH_ID.toString())
                .endpointPort(ENDPOINT_PORT)
                .endpointVlan(ENDPOINT_VLAN)
                .endpointInnerVlan(ENDPOINT_INNER_VLAN)
                .description(DESCRIPTION)
                .timeCreate(TIME_CREATE.toString())
                .timeModify(TIME_MODIFY.toString())
                .build();
    }

    private HaSubFlowDumpWrapper createPersistenceHaSubFlowDumpWrapper() {
        return HaSubFlowDumpWrapper.builder()
                .haSubFlowDumpList(Lists.newArrayList(createPersistenceHaSubFlowDump()))
                .build();
    }

    private HaFlowDumpData createHaFlowDumpData(DumpType dumpType, String correlationId) {
        return HaFlowDumpData.builder()
                .affinityGroupId(AFFINITY_GROUP_ID)
                .allocateProtectedPath(ALLOCATE_PROTECTED_PATH)
                .description(DESCRIPTION)
                .diverseGroupId(DIVERSE_GROUP_ID)
                .dumpType(dumpType)
                .encapsulationType(FLOW_ENCAPSULATION_TYPE)
                .flowTimeCreate(TIME_CREATE)
                .flowTimeModify(TIME_MODIFY)
                .haFlowId(HA_FLOW_ID)
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
                .sharedSwitchId(SHARED_SWITCH.getSwitchId())
                .strictBandwidth(STRICT_BANDWIDTH)
                .taskId(correlationId)
                .haSubFlows(Lists.newArrayList(createHaSubFlowDump()))
                .protectedForwardPath(createHaFlowPathDump(FlowPathDirection.FORWARD))
                .protectedReversePath(createHaFlowPathDump(FlowPathDirection.REVERSE))
                .forwardPath(createHaFlowPathDump(FlowPathDirection.FORWARD))
                .reversePath(createHaFlowPathDump(FlowPathDirection.REVERSE))
                .build();
    }

    private HaFlowPathDump createHaFlowPathDump(FlowPathDirection direction) {
        List<org.openkilda.messaging.payload.flow.PathNodePayload> pathNodes = new ArrayList<>();
        pathNodes.add(new org.openkilda.messaging.payload.flow.PathNodePayload(new SwitchId("00:01"), 1, 2));
        pathNodes.add(new org.openkilda.messaging.payload.flow.PathNodePayload(new SwitchId("00:02"), 2, 3));
        pathNodes.add(new org.openkilda.messaging.payload.flow.PathNodePayload(new SwitchId("00:03"), 4, 5));
        List<List<org.openkilda.messaging.payload.flow.PathNodePayload>> pathNodesList = new ArrayList<>();
        pathNodesList.add(pathNodes);

        List<HaSubFlowDump> haSubFlowDump = Lists.newArrayList(createHaSubFlowDump());

        return HaFlowPathDump.builder()
                .haPathId(new PathId("HA flow path ID"))
                .yPointGroupId(GroupId.MIN_FLOW_GROUP_ID)
                .yPointSwitchId(new SwitchId("00:03"))
                .yPointMeterId(MeterId.LACP_REPLY_METER_ID)
                .timeCreate(TIME_CREATE)
                .timeModify(TIME_MODIFY)
                .sharedPointMeterId(MeterId.LACP_REPLY_METER_ID)
                .sharedSwitchId(new SwitchId("00:01"))
                .cookie(FlowSegmentCookie.builder().direction(direction).build())
                .ignoreBandwidth(IGNORE_BANDWIDTH)
                .status(FLOW_PATH_STATUS)
                .paths(pathNodesList)
                .haSubFlows(haSubFlowDump)
                .build();
    }

    private HaSubFlowDump createHaSubFlowDump() {
        return HaSubFlowDump.builder()
                .haSubFlowId(HA_SUB_FLOW_ID)
                .haFlowId(HA_FLOW_ID)
                .status(FLOW_STATUS)
                .endpointSwitchId(ENDPOINT_SWITCH_ID)
                .endpointPort(ENDPOINT_PORT)
                .endpointVlan(ENDPOINT_VLAN)
                .endpointInnerVlan(ENDPOINT_INNER_VLAN)
                .description(DESCRIPTION)
                .timeCreate(TIME_CREATE)
                .timeModify(TIME_MODIFY)
                .build();
    }

    private HaFlow createHaFlow() {
        HaFlow result = new HaFlow(HA_FLOW_ID,
                SHARED_SWITCH,
                SHARED_PORT,
                SHARED_OUTER_VLAN,
                SHARED_INNER_VLAN,
                MAXIMUM_BANDWIDTH,
                PATH_COMPUTATION_STRATEGY,
                FLOW_ENCAPSULATION_TYPE,
                MAX_LATENCY,
                MAX_LATENCY_TIER_2,
                IGNORE_BANDWIDTH,
                PERIODIC_PINGS,
                PINNED,
                PRIORITY,
                STRICT_BANDWIDTH,
                DESCRIPTION,
                ALLOCATE_PROTECTED_PATH,
                FLOW_STATUS,
                STATUS_INFO,
                AFFINITY_GROUP_ID,
                DIVERSE_GROUP_ID);

        HaFlowPath forward = createHaFlowPath("forward ID", FlowPathDirection.FORWARD);
        HaFlowPath reverse = createHaFlowPath("reverse ID", FlowPathDirection.REVERSE);

        result.setForwardPath(forward);
        result.setReversePath(reverse);
        result.addPaths(forward, reverse);

        return result;
    }

    private HaFlowPath createHaFlowPath(String pathId, FlowPathDirection direction) {
        return HaFlowPath.builder()
                .haPathId(new PathId(pathId))
                .sharedSwitch(SHARED_SWITCH)
                .bandwidth(50_000L)
                .cookie(FlowSegmentCookie.builder().direction(direction).build())
                .build();
    }
}
