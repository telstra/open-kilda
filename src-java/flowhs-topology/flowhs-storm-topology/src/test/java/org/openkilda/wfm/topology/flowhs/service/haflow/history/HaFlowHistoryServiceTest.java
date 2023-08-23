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

package org.openkilda.wfm.topology.flowhs.service.haflow.history;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.history.model.HaFlowDumpData;
import org.openkilda.wfm.share.history.model.HaFlowEventData;
import org.openkilda.wfm.share.history.model.HaFlowEventData.Event;
import org.openkilda.wfm.share.history.model.HaFlowEventData.Initiator;
import org.openkilda.wfm.topology.flowhs.service.common.HistoryUpdateCarrier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class HaFlowHistoryServiceTest {

    public static final String TASK_ID = "task ID";
    public static final String ACTION = "action";
    public static final String DESCRIPTION = "description";
    public static final String HA_FLOW_ID = "HA-flow ID";
    private FakeCarrier fakeCarrier;

    @BeforeEach
    public void setUp() throws Exception {
        fakeCarrier = new FakeCarrier();
    }

    @Test
    public void using() {
        assertThrows(RuntimeException.class, () -> HaFlowHistoryService.using(null));
    }

    @Test
    public void save() {
        HaFlowHistoryService.using(fakeCarrier).save(HaFlowHistory.withTaskId(TASK_ID)
                .withAction(ACTION)
                .withDescription(DESCRIPTION)
                .withHaFlowId(HA_FLOW_ID));

        assertEquals(1, fakeCarrier.getHistoryHolderList().size());
        assertEquals(TASK_ID, fakeCarrier.getHistoryHolderList().get(0).getTaskId());
        assertEquals(ACTION, fakeCarrier.getHistoryHolderList().get(0).getHaFlowHistoryData().getAction());
        assertEquals(DESCRIPTION, fakeCarrier.getHistoryHolderList().get(0).getHaFlowHistoryData().getDescription());
        assertEquals(HA_FLOW_ID, fakeCarrier.getHistoryHolderList().get(0).getHaFlowHistoryData().getHaFlowId());
        assertTrue(Instant.now().plus(1, ChronoUnit.SECONDS).isAfter(
                fakeCarrier.getHistoryHolderList().get(0).getHaFlowHistoryData().getTime()));

        assertNull(fakeCarrier.getHistoryHolderList().get(0).getHaFlowDumpData());
        assertNull(fakeCarrier.getHistoryHolderList().get(0).getHaFlowEventData());
        assertNull(fakeCarrier.getHistoryHolderList().get(0).getFlowHistoryData());
    }

    @Test
    public void saveWithDump() {
        HaFlowHistoryService.using(fakeCarrier).save(HaFlowHistory.withTaskId(TASK_ID)
                .withAction(ACTION)
                .withDescription(DESCRIPTION)
                .withHaFlowDump(HaFlowDumpData.builder().haFlowId(HA_FLOW_ID).build())
                .withHaFlowId(HA_FLOW_ID));

        assertEquals(1, fakeCarrier.getHistoryHolderList().size());
        assertEquals(TASK_ID, fakeCarrier.getHistoryHolderList().get(0).getTaskId());
        assertEquals(ACTION, fakeCarrier.getHistoryHolderList().get(0).getHaFlowHistoryData().getAction());
        assertEquals(DESCRIPTION, fakeCarrier.getHistoryHolderList().get(0).getHaFlowHistoryData().getDescription());
        assertEquals(HA_FLOW_ID, fakeCarrier.getHistoryHolderList().get(0).getHaFlowHistoryData().getHaFlowId());
        assertTrue(Instant.now().plus(1, ChronoUnit.SECONDS).isAfter(
                fakeCarrier.getHistoryHolderList().get(0).getHaFlowHistoryData().getTime()));

        assertNotNull(fakeCarrier.getHistoryHolderList().get(0).getHaFlowDumpData());
        assertEquals(HA_FLOW_ID, fakeCarrier.getHistoryHolderList().get(0).getHaFlowDumpData().getHaFlowId());

        assertNull(fakeCarrier.getHistoryHolderList().get(0).getHaFlowEventData());
        assertNull(fakeCarrier.getHistoryHolderList().get(0).getFlowHistoryData());
    }

    @Test
    public void saveNewHaFlowEvent() {
        HaFlowHistoryService.using(fakeCarrier).saveNewHaFlowEvent(HaFlowEventData.builder()
                .action(ACTION)
                .details(DESCRIPTION)
                .event(HaFlowEventData.Event.CREATE)
                .taskId(TASK_ID)
                .haFlowId(HA_FLOW_ID)
                .initiator(Initiator.AUTO)
                .build());

        assertEquals(1, fakeCarrier.getHistoryHolderList().size());
        assertEquals(TASK_ID, fakeCarrier.getHistoryHolderList().get(0).getTaskId());
        assertEquals(HaFlowEventData.Event.CREATE.getDescription(),
                fakeCarrier.getHistoryHolderList().get(0).getHaFlowHistoryData().getAction());
        assertNull(fakeCarrier.getHistoryHolderList().get(0).getHaFlowHistoryData().getDescription());
        assertEquals(HA_FLOW_ID, fakeCarrier.getHistoryHolderList().get(0).getHaFlowHistoryData().getHaFlowId());
        assertTrue(Instant.now().plus(1, ChronoUnit.SECONDS).isAfter(
                fakeCarrier.getHistoryHolderList().get(0).getHaFlowHistoryData().getTime()));
        assertTrue(Instant.now().plus(1, ChronoUnit.SECONDS).isAfter(
                fakeCarrier.getHistoryHolderList().get(0).getHaFlowEventData().getTime()));

        assertNotNull(fakeCarrier.getHistoryHolderList().get(0).getHaFlowEventData());
        assertEquals(HA_FLOW_ID, fakeCarrier.getHistoryHolderList().get(0).getHaFlowEventData().getHaFlowId());
        assertEquals(Event.CREATE, fakeCarrier.getHistoryHolderList().get(0).getHaFlowEventData().getEvent());

        assertEquals(ACTION, fakeCarrier.getHistoryHolderList().get(0).getHaFlowEventData().getAction());
        assertEquals(Initiator.AUTO, fakeCarrier.getHistoryHolderList().get(0).getHaFlowEventData().getInitiator());
        assertEquals(DESCRIPTION, fakeCarrier.getHistoryHolderList().get(0).getHaFlowEventData().getDetails());
        assertEquals(TASK_ID, fakeCarrier.getHistoryHolderList().get(0).getHaFlowEventData().getTaskId());

        assertNull(fakeCarrier.getHistoryHolderList().get(0).getHaFlowDumpData());
        assertNull(fakeCarrier.getHistoryHolderList().get(0).getFlowHistoryData());
    }

    @Test
    public void whenInvalidEventData_returnFalseAndLogTheError() {
        assertFalse(HaFlowHistoryService.using(fakeCarrier).saveNewHaFlowEvent(HaFlowEventData.builder()
                .action(ACTION)
                .details(DESCRIPTION)
                .event(HaFlowEventData.Event.CREATE)
                .taskId(null)
                .haFlowId(HA_FLOW_ID)
                .initiator(Initiator.AUTO)
                .build()));

        assertFalse(HaFlowHistoryService.using(fakeCarrier).saveNewHaFlowEvent(HaFlowEventData.builder()
                        .action(ACTION)
                        .details(DESCRIPTION)
                        .event(HaFlowEventData.Event.CREATE)
                        .taskId(TASK_ID)
                        .haFlowId(null)
                        .initiator(Initiator.AUTO)
                        .build()));
    }

    @Test
    public void saveError() {
        HaFlowHistoryService.using(fakeCarrier).saveError(HaFlowHistory
                .withTaskId(TASK_ID)
                .withAction(ACTION)
                .withDescription(DESCRIPTION)
                .withHaFlowId(HA_FLOW_ID));

        assertEquals(1, fakeCarrier.getHistoryHolderList().size());
        assertEquals(TASK_ID, fakeCarrier.getHistoryHolderList().get(0).getTaskId());
        assertEquals(ACTION, fakeCarrier.getHistoryHolderList().get(0).getHaFlowHistoryData().getAction());
        assertEquals(DESCRIPTION, fakeCarrier.getHistoryHolderList().get(0).getHaFlowHistoryData().getDescription());
        assertEquals(HA_FLOW_ID, fakeCarrier.getHistoryHolderList().get(0).getHaFlowHistoryData().getHaFlowId());
        assertTrue(Instant.now().plus(1, ChronoUnit.SECONDS).isAfter(
                fakeCarrier.getHistoryHolderList().get(0).getHaFlowHistoryData().getTime()));

        assertNull(fakeCarrier.getHistoryHolderList().get(0).getHaFlowDumpData());
        assertNull(fakeCarrier.getHistoryHolderList().get(0).getHaFlowEventData());
        assertNull(fakeCarrier.getHistoryHolderList().get(0).getFlowHistoryData());
    }

    private static class FakeCarrier implements HistoryUpdateCarrier {
        private final List<FlowHistoryHolder> historyHolderList = new ArrayList<>();

        @Override
        public void sendHistoryUpdate(FlowHistoryHolder historyHolder) {
            historyHolderList.add(historyHolder);
        }

        public List<FlowHistoryHolder> getHistoryHolderList() {
            return new ArrayList<>(historyHolderList);
        }
    }
}
