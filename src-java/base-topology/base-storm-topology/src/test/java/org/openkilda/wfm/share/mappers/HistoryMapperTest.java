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
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.model.history.FlowEvent;
import org.openkilda.model.history.FlowEventAction;

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
    public static String DESTINATION_SWITCH = "5";
    public static int DESTINATION_VLAN = 6;
    public static String DIVERSE_GROUP_ID = "7";
    public static FlowEncapsulationType FLOW_ENCAPSULATION_TYPE = FlowEncapsulationType.VXLAN;
    public static String FORWARD_COOKIE = "8";
    public static Long FORWARD_METER_ID = 9L;
    public static String FORWARD_PATH = "10";
    public static String FORWARD_STATUS = "11";
    public static boolean IGNORE_BANDWIDTH = false;
    public static SwitchId LOOP_SWITCH_ID = new SwitchId(12);
    public static int MAX_LATENCY = 13;
    public static PathComputationStrategy PATH_COMPUTATION_STRATEGY = PathComputationStrategy.COST;
    public static boolean PERIODIC_PINGS = false;
    public static boolean PINNED = false;
    public static String REVERSE_COOKIE = "14";
    public static long REVERSE_METER_ID = 15;
    public static String REVERSE_PATH = "16";
    public static String REVERSE_STATUS = "17";
    public static int SOURCE_INNER_VLAN = 18;
    public static int SOURCE_PORT = 19;
    public static String SOURCE_SWITCH = "20";
    public static int SOURCE_VLAN = 21;
    public static String TYPE = "22";

    public static FlowEventAction action;

    public static FlowEvent event;
    public static FlowDumpPayload flowDumpPayload;

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
        flowDumpPayload = FlowDumpPayload.builder()
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
                .reverseMeterId(REVERSE_METER_ID)
                .reversePath(REVERSE_PATH)
                .reverseStatus(REVERSE_STATUS)
                .sourceInnerVlan(SOURCE_INNER_VLAN)
                .sourcePort(SOURCE_PORT)
                .sourceSwitch(SOURCE_SWITCH)
                .sourceVlan(SOURCE_VLAN)
                .type(TYPE)
                .build();
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
        flowDumpPayloads.add(flowDumpPayload);

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
}
