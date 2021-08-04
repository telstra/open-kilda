/* Copyright 2020 Telstra Open Source
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

package org.openkilda.persistence.ferma.repositories;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.history.FlowEvent;
import org.openkilda.model.history.FlowEventAction;
import org.openkilda.model.history.FlowStatusView;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.history.FlowEventActionRepository;
import org.openkilda.persistence.repositories.history.FlowEventRepository;

import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FermaFlowEventRepositoryTest extends InMemoryGraphBasedTest {

    private static final String FLOW_1 = "FLOW_1";
    private static final String FLOW_2 = "FLOW_2";
    private static final String ACTION_1 = "ACTION_1";
    private static final String ACTION_2 = "ACTION_2";
    private static final String ACTION_3 = "ACTION_3";
    private static final String ACTION_4 = "ACTION_4";
    private static final String ACTION_5 = "ACTION_5";
    private static final String TASK_1 = "TASK_1";
    private static final String TASK_2 = "TASK_2";
    private static final String TASK_3 = "TASK_3";
    private static final String TASK_4 = "TASK_4";
    private static final String TASK_5 = "TASK_5";
    private static final Instant TIME_1 = Instant.parse("2020-07-06T11:04:41Z");
    private static final Instant TIME_2 = Instant.parse("2020-07-06T11:04:41.482Z");
    private static final Instant TIME_3 = Instant.parse("2020-07-06T11:04:42.321Z");
    private static final Instant TIME_4 = Instant.parse("2020-07-06T11:04:50.321Z");

    private static FlowEventRepository flowEventRepository;
    private static FlowEventActionRepository flowEventActionRepository;

    @BeforeClass
    public static void setUp() {
        flowEventRepository = repositoryFactory.createFlowEventRepository();
        flowEventActionRepository = repositoryFactory.createFlowEventActionRepository();
    }

    @Test
    public void findByFlowIdAndTimeFrameOrderTest() {
        List<FlowEvent> expected = new ArrayList<>();
        expected.add(buildFlowEvent(FLOW_1, TASK_1, ACTION_1, TIME_1));
        expected.add(buildFlowEvent(FLOW_1, TASK_2, ACTION_2, TIME_2));
        expected.add(buildFlowEvent(FLOW_1, TASK_3, ACTION_3, TIME_3));

        for (FlowEvent flowHistory : expected) {
            flowEventRepository.add(flowHistory);
        }

        List<FlowEvent> actual = new ArrayList<>(flowEventRepository.findByFlowIdAndTimeFrame(
                FLOW_1, TIME_1.minusSeconds(1), TIME_3.plusSeconds(1), 100));
        assertEquals(expected, actual);
        // result must be sorted by time
        assertTrue(actual.get(0).getTimestamp().isBefore(actual.get(1).getTimestamp()));
        assertTrue(actual.get(1).getTimestamp().isBefore(actual.get(2).getTimestamp()));
    }

    @Test
    public void findByFlowIdAndTimeFrameTest() {
        flowEventRepository.add(buildFlowEvent(FLOW_1, TASK_1, ACTION_1, TIME_1));
        flowEventRepository.add(buildFlowEvent(FLOW_1, TASK_2, ACTION_2, TIME_2));
        flowEventRepository.add(buildFlowEvent(FLOW_1, TASK_3, ACTION_3, TIME_3));
        flowEventRepository.add(buildFlowEvent(FLOW_1, TASK_4, ACTION_4, TIME_4));
        flowEventRepository.add(buildFlowEvent(FLOW_2, TASK_5, ACTION_5, TIME_3));

        List<FlowEvent> events = new ArrayList<>(flowEventRepository.findByFlowIdAndTimeFrame(
                FLOW_1, TIME_2, TIME_3, 1000));
        assertEquals(2, events.size());
        assertEquals(ACTION_2, events.get(0).getAction());
        assertEquals(ACTION_3, events.get(1).getAction());
        assertTrue(events.get(0).getTimestamp().isBefore(events.get(1).getTimestamp()));
    }

    @Test
    public void findByFlowIdTimeFrameAndMaxCountTest() {
        flowEventRepository.add(buildFlowEvent(FLOW_1, TASK_1, ACTION_1, TIME_1));
        flowEventRepository.add(buildFlowEvent(FLOW_1, TASK_2, ACTION_2, TIME_2));
        flowEventRepository.add(buildFlowEvent(FLOW_1, TASK_3, ACTION_3, TIME_3));
        flowEventRepository.add(buildFlowEvent(FLOW_1, TASK_4, ACTION_4, TIME_4));
        flowEventRepository.add(buildFlowEvent(FLOW_2, TASK_5, ACTION_5, TIME_3));

        List<FlowEvent> events = new ArrayList<>(flowEventRepository.findByFlowIdAndTimeFrame(
                FLOW_1, TIME_1, TIME_4, 2));
        assertEquals(2, events.size());
        assertEquals(ACTION_3, events.get(0).getAction());
        assertEquals(ACTION_4, events.get(1).getAction());
        assertTrue(events.get(0).getTimestamp().isBefore(events.get(1).getTimestamp()));
    }

    @Test
    public void findFlowStatusesByFlowIdAndTimeFrameOrderTest() {
        flowEventRepository.add(buildFlowEvent(FLOW_1, TASK_1, ACTION_1, TIME_1));
        flowEventRepository.add(buildFlowEvent(FLOW_1, TASK_2, ACTION_2, TIME_2));
        flowEventRepository.add(buildFlowEvent(FLOW_1, TASK_3, ACTION_3, TIME_3));

        flowEventActionRepository.add(buildFlowHistory(TASK_1,
                FlowEvent.FLOW_STATUS_ACTION_PARTS.get(0) + "UP", TIME_1));
        flowEventActionRepository.add(buildFlowHistory(TASK_2,
                FlowEvent.FLOW_STATUS_ACTION_PARTS.get(1) + "DOWN", TIME_2));
        flowEventActionRepository.add(buildFlowHistory(TASK_3, FlowEvent.FLOW_DELETED_ACTION, TIME_3));

        List<FlowStatusView> actual = flowEventRepository.findFlowStatusesByFlowIdAndTimeFrame(
                FLOW_1, TIME_1.minusSeconds(1), TIME_3.plusSeconds(1), 100);

        assertEquals(TIME_1, actual.get(0).getTimestamp());
        assertEquals("UP", actual.get(0).getStatusBecome());

        assertEquals(TIME_2, actual.get(1).getTimestamp());
        assertEquals("DOWN", actual.get(1).getStatusBecome());

        assertEquals(TIME_3, actual.get(2).getTimestamp());
        assertEquals("DELETED", actual.get(2).getStatusBecome());
    }

    @Test
    public void findFlowStatusesByFlowIdAndTimeFrameTest() {
        flowEventRepository.add(buildFlowEvent(FLOW_1, TASK_1, ACTION_1, TIME_1));
        flowEventRepository.add(buildFlowEvent(FLOW_1, TASK_2, ACTION_2, TIME_2));
        flowEventRepository.add(buildFlowEvent(FLOW_1, TASK_3, ACTION_3, TIME_3));
        flowEventRepository.add(buildFlowEvent(FLOW_1, TASK_4, ACTION_4, TIME_4));
        flowEventRepository.add(buildFlowEvent(FLOW_2, TASK_5, ACTION_5, TIME_3));

        flowEventActionRepository.add(buildFlowHistory(TASK_1,
                FlowEvent.FLOW_STATUS_ACTION_PARTS.get(0) + "UP", TIME_1));
        flowEventActionRepository.add(buildFlowHistory(TASK_2,
                FlowEvent.FLOW_STATUS_ACTION_PARTS.get(1) + "DEGRADED", TIME_2));
        flowEventActionRepository.add(buildFlowHistory(TASK_3,
                FlowEvent.FLOW_STATUS_ACTION_PARTS.get(0) + "DOWN", TIME_3));
        flowEventActionRepository.add(buildFlowHistory(TASK_4, FlowEvent.FLOW_DELETED_ACTION, TIME_4));
        flowEventActionRepository.add(buildFlowHistory(TASK_5,
                FlowEvent.FLOW_STATUS_ACTION_PARTS.get(1) + "DEGRADED", TIME_3));

        List<FlowStatusView> actual = flowEventRepository.findFlowStatusesByFlowIdAndTimeFrame(
                FLOW_1, TIME_2, TIME_3, 1000);

        assertEquals(TIME_2, actual.get(0).getTimestamp());
        assertEquals("DEGRADED", actual.get(0).getStatusBecome());

        assertEquals(TIME_3, actual.get(1).getTimestamp());
        assertEquals("DOWN", actual.get(1).getStatusBecome());
    }

    @Test
    public void findFlowStatusesByFlowIdAndTimeFrameMaxCountTest() {
        flowEventRepository.add(buildFlowEvent(FLOW_1, TASK_1, ACTION_1, TIME_1));
        flowEventRepository.add(buildFlowEvent(FLOW_1, TASK_2, ACTION_2, TIME_2));
        flowEventRepository.add(buildFlowEvent(FLOW_1, TASK_3, ACTION_3, TIME_3));
        flowEventRepository.add(buildFlowEvent(FLOW_1, TASK_4, ACTION_4, TIME_4));
        flowEventRepository.add(buildFlowEvent(FLOW_2, TASK_5, ACTION_5, TIME_3));

        flowEventActionRepository.add(buildFlowHistory(TASK_1,
                FlowEvent.FLOW_STATUS_ACTION_PARTS.get(0) + "UP", TIME_1));
        flowEventActionRepository.add(buildFlowHistory(TASK_2,
                FlowEvent.FLOW_STATUS_ACTION_PARTS.get(1) + "DEGRADED", TIME_2));
        flowEventActionRepository.add(buildFlowHistory(TASK_3,
                FlowEvent.FLOW_STATUS_ACTION_PARTS.get(0) + "DOWN", TIME_3));
        flowEventActionRepository.add(buildFlowHistory(TASK_4, FlowEvent.FLOW_DELETED_ACTION, TIME_4));
        flowEventActionRepository.add(buildFlowHistory(TASK_5,
                FlowEvent.FLOW_STATUS_ACTION_PARTS.get(1) + "DEGRADED", TIME_3));

        List<FlowStatusView> actual = flowEventRepository.findFlowStatusesByFlowIdAndTimeFrame(
                FLOW_1, TIME_1, TIME_4, 2);

        assertEquals(TIME_3, actual.get(0).getTimestamp());
        assertEquals("DOWN", actual.get(0).getStatusBecome());

        assertEquals(TIME_4, actual.get(1).getTimestamp());
        assertEquals("DELETED", actual.get(1).getStatusBecome());
    }

    private FlowEvent buildFlowEvent(String flowId, String taskId, String action, Instant timestamp) {
        return FlowEvent.builder()
                .flowId(flowId)
                .action(action)
                .taskId(taskId)
                .actor(action + "_actor")
                .details(action + "_details")
                .timestamp(timestamp)
                .build();
    }

    private FlowEventAction buildFlowHistory(String taskId, String action, Instant timestamp) {
        FlowEventAction flowEventAction = new FlowEventAction();
        flowEventAction.setTaskId(taskId);
        flowEventAction.setAction(action);
        flowEventAction.setTimestamp(timestamp);
        return flowEventAction;
    }
}
