/* Copyright 2022 Telstra Open Source
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
import static org.openkilda.wfm.share.mappers.HistoryMapper.INSTANCE;

import org.openkilda.messaging.payload.history.FlowDumpPayload;
import org.openkilda.messaging.payload.history.FlowHistoryEntry;
import org.openkilda.messaging.payload.history.FlowHistoryPayload;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.history.FlowEvent;
import org.openkilda.model.history.FlowEventAction;
import org.openkilda.model.history.FlowEventDump;

import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;

public class HistoryMapperTest {

    public static Instant TIMESTAMP = Instant.ofEpochSecond(1612898925);
    public static String ACTION = "Action";
    public static String TASK_ID = "Task ID";
    public static String DETAILS = "Details";
    public static String ACTOR = "Actor";
    public static String AFFINITY_GROUP_ID = "1";
    public static boolean ALLOCATE_PROTECTED_PATH = true;
    public static int BANDWIDTH = 2;
    public static int DESTINATION_INNER_VLAN = 3;
    public static int DESTINATION_PORT = 4;
    public static String DESTINATION_SWITCH = "00:00:00:00:00:00:00:05";
    public static int DESTINATION_VLAN = 6;
    public static String DIVERSE_GROUP_ID = "7";
    public static FlowEncapsulationType FLOW_ENCAPSULATION_TYPE = FlowEncapsulationType.VXLAN;
    public static long FORWARD_COOKIE = 8L;
    public static Long FORWARD_METER_ID = 9L;
    public static String FORWARD_PATH = "10";
    public static String FORWARD_STATUS = "ACTIVE";
    public static boolean IGNORE_BANDWIDTH = false;
    public static SwitchId LOOP_SWITCH_ID = new SwitchId(12);
    public static long MAX_LATENCY = 13;
    public static PathComputationStrategy PATH_COMPUTATION_STRATEGY = PathComputationStrategy.COST;
    public static boolean PERIODIC_PINGS = false;
    public static boolean PINNED = false;
    public static long REVERSE_COOKIE = 14L;
    public static long REVERSE_METER_ID = 15;
    public static String REVERSE_PATH = "16";
    public static String REVERSE_STATUS = "DEGRADED";
    public static int SOURCE_INNER_VLAN = 18;
    public static int SOURCE_PORT = 19;
    public static String SOURCE_SWITCH = "00:00:00:00:00:00:00:20";
    public static int SOURCE_VLAN = 21;
    public static String TYPE = "22";

    public static long MAX_LATENCY_TIER_2 = 23;

    public static Integer PRIORITY = 24;

    public static boolean STRICT_BANDWIDTH = true;

    public static FlowEventAction action;

    public static FlowEvent event;
    public static FlowDumpPayload expectedPayload;

    public static FlowEventDump flowEventDump;

    @BeforeClass
    public static void initializeData() {
        action = new FlowEventAction();
        action.setTimestamp(TIMESTAMP);
        action.setAction(ACTION);
        action.setTaskId(TASK_ID);
        action.setDetails(DETAILS);

        event = FlowEvent.builder()
                .taskId(TASK_ID)
                .action(ACTION)
                .details(DETAILS)
                .timestamp(TIMESTAMP)
                .actor(ACTOR)
                .build();

        expectedPayload = FlowDumpPayload.builder()
                .affinityGroupId(AFFINITY_GROUP_ID)
                .allocateProtectedPath(ALLOCATE_PROTECTED_PATH)
                .bandwidth(BANDWIDTH)
                .destinationInnerVlan(DESTINATION_INNER_VLAN)
                .destinationPort(DESTINATION_PORT)
                .destinationSwitch(DESTINATION_SWITCH)
                .destinationVlan(DESTINATION_VLAN)
                .diverseGroupId(DIVERSE_GROUP_ID)
                .encapsulationType(FLOW_ENCAPSULATION_TYPE)
                .forwardCookie(FORWARD_COOKIE)
                .forwardCookieHex("8")
                .forwardMeterId(FORWARD_METER_ID)
                .forwardPath(FORWARD_PATH)
                .forwardStatus(FORWARD_STATUS)
                .ignoreBandwidth(IGNORE_BANDWIDTH)
                .loopSwitchId(LOOP_SWITCH_ID)
                .maxLatency(MAX_LATENCY)
                .pathComputationStrategy(PATH_COMPUTATION_STRATEGY)
                .periodicPings(PERIODIC_PINGS)
                .pinned(PINNED)
                .reverseCookie(REVERSE_COOKIE)
                .reverseCookieHex("e")
                .reverseMeterId(REVERSE_METER_ID)
                .reversePath(REVERSE_PATH)
                .reverseStatus(REVERSE_STATUS)
                .sourceInnerVlan(SOURCE_INNER_VLAN)
                .sourcePort(SOURCE_PORT)
                .sourceSwitch(SOURCE_SWITCH)
                .sourceVlan(SOURCE_VLAN)
                .type(TYPE)
                .maxLatencyTier2(MAX_LATENCY_TIER_2)
                .priority(PRIORITY)
                .strictBandwidth(STRICT_BANDWIDTH)
                .build();

        flowEventDump = new FlowEventDump();

        flowEventDump.setAffinityGroupId(AFFINITY_GROUP_ID);
        flowEventDump.setAllocateProtectedPath(ALLOCATE_PROTECTED_PATH);
        flowEventDump.setBandwidth(BANDWIDTH);
        flowEventDump.setDestinationInnerVlan(DESTINATION_INNER_VLAN);
        flowEventDump.setDestinationPort(DESTINATION_PORT);
        flowEventDump.setDestinationSwitch(new SwitchId(DESTINATION_SWITCH));
        flowEventDump.setDestinationVlan(DESTINATION_VLAN);
        flowEventDump.setDiverseGroupId(DIVERSE_GROUP_ID);
        flowEventDump.setEncapsulationType(FLOW_ENCAPSULATION_TYPE);
        flowEventDump.setForwardCookie(new FlowSegmentCookie(FORWARD_COOKIE));
        flowEventDump.setForwardMeterId(new MeterId(FORWARD_METER_ID));
        flowEventDump.setForwardPath(FORWARD_PATH);
        flowEventDump.setForwardStatus(FlowPathStatus.ACTIVE);
        flowEventDump.setIgnoreBandwidth(IGNORE_BANDWIDTH);
        flowEventDump.setLoopSwitchId(LOOP_SWITCH_ID);
        flowEventDump.setMaxLatency(MAX_LATENCY);
        flowEventDump.setPathComputationStrategy(PATH_COMPUTATION_STRATEGY);
        flowEventDump.setPeriodicPings(PERIODIC_PINGS);
        flowEventDump.setPinned(PINNED);
        flowEventDump.setReverseCookie(new FlowSegmentCookie(REVERSE_COOKIE));
        flowEventDump.setReverseMeterId(new MeterId(REVERSE_METER_ID));
        flowEventDump.setReversePath(REVERSE_PATH);
        flowEventDump.setReverseStatus(FlowPathStatus.DEGRADED);
        flowEventDump.setSourceInnerVlan(SOURCE_INNER_VLAN);
        flowEventDump.setSourcePort(SOURCE_PORT);
        flowEventDump.setSourceSwitch(new SwitchId(SOURCE_SWITCH));
        flowEventDump.setSourceVlan(SOURCE_VLAN);
        flowEventDump.setType(TYPE);
        flowEventDump.setPriority(PRIORITY);
        flowEventDump.setMaxLatencyTier2(MAX_LATENCY_TIER_2);
        flowEventDump.setStrictBandwidth(STRICT_BANDWIDTH);
    }

    @Test
    public void toFlowHistoryPayloadTest() {
        FlowHistoryPayload payload = INSTANCE.map(action);

        assertEquals(payload.getTimestamp(), action.getTimestamp().getEpochSecond());
        assertEquals(payload.getAction(), action.getAction());
        assertEquals(payload.getDetails(), action.getDetails());
        assertEquals(payload.getTimestampIso(), action.getTimestamp().atOffset(ZoneOffset.UTC).toString());
    }

    @Test
    public void toFlowHistoryEntryTest() {
        FlowHistoryPayload flowHistoryPayload = INSTANCE.map(action);

        ArrayList<FlowHistoryPayload> flowHistoryPayloads = new ArrayList<>();
        flowHistoryPayloads.add(flowHistoryPayload);

        ArrayList<FlowDumpPayload> flowDumpPayloads = new ArrayList<>();
        flowDumpPayloads.add(expectedPayload);

        FlowHistoryEntry entry = INSTANCE.map(event, flowHistoryPayloads, flowDumpPayloads);

        assertEquals(entry.getAction(), action.getAction());
        assertEquals(entry.getDetails(), action.getDetails());
        assertEquals(entry.getTimestamp(), event.getTimestamp().getEpochSecond());
        assertEquals(entry.getFlowId(), event.getFlowId());
        assertEquals(entry.getActor(), event.getActor());
        assertEquals(entry.getDumps(), flowDumpPayloads);
        assertEquals(entry.getPayload(), flowHistoryPayloads);
        assertEquals(entry.getTimestampIso(), event.getTimestamp().atOffset(ZoneOffset.UTC).toString());
        assertEquals(entry.getTimestampIso(), "2021-02-09T19:28:45Z");
        assertEquals(entry.getTaskId(), event.getTaskId());
    }

    @Test
    public void toFlowDumpPayloadTest() {
        FlowDumpPayload payload = INSTANCE.map(flowEventDump);
        assertEquals(payload, expectedPayload);

    }
}
