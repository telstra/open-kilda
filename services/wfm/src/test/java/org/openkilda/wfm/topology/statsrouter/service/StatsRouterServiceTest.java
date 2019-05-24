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

package org.openkilda.wfm.topology.statsrouter.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.stats.StatsRequest;
import org.openkilda.messaging.command.switches.ListSwitchRequest;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.switches.ListSwitchResponse;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StatsRouterServiceTest {

    private StatsRouterService statsRouterService;

    private MessageSender messageSender;

    private Clock clock;

    private static final int TIMEOUT = 5;

    private static final long TIMESTAMP = 1;

    private static final String CORRELATION_ID = "corrId";

    private static final String CONTROLLER_ID_1 = "Controller1";

    private static final String CONTROLLER_ID_2 = "Controller2";

    private static final SwitchId SWITCH_ID_1 = new SwitchId("00:01");

    private static final SwitchId SWITCH_ID_2 = new SwitchId("00:02");

    private static final SwitchId SWITCH_ID_3 = new SwitchId("00:03");

    @Before
    public void init() {
        clock = mock(Clock.class);
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        messageSender = mock(MessageSender.class);
        statsRouterService = new StatsRouterService(TIMEOUT, messageSender, clock);
    }

    @Test
    public void initialStateTest() {
        statsRouterService.handleStatsRequest(getStatsRequest());

        ArgumentCaptor<CommandMessage> mgmtMessageCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        ArgumentCaptor<CommandMessage> statsMessageCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        verify(messageSender).sendToMgmt(mgmtMessageCaptor.capture());
        verify(messageSender).sendToStats(statsMessageCaptor.capture());

        CommandMessage mgmtMessage = mgmtMessageCaptor.getValue();
        assertEquals(TIMESTAMP, mgmtMessage.getTimestamp());
        assertEquals(CORRELATION_ID, mgmtMessage.getCorrelationId());
        assertTrue(mgmtMessage.getData() instanceof StatsRequest);
        assertExcludeIs(mgmtMessage);
        CommandMessage statsMessage = statsMessageCaptor.getValue();
        assertEquals(TIMESTAMP, statsMessage.getTimestamp());
        assertEquals(CORRELATION_ID, statsMessage.getCorrelationId());
        assertTrue(statsMessage.getData() instanceof StatsRequest);
        assertExcludeIs(statsMessage);
    }

    @Test
    public void excludeTest() {
        when(clock.instant()).thenReturn(Instant.now());
        statsRouterService.handleListSwitchesResponse(getListSwitches(CONTROLLER_ID_1, SWITCH_ID_1));
        statsRouterService.handleStatsRequest(getStatsRequest());

        ArgumentCaptor<CommandMessage> mgmtMessageCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        ArgumentCaptor<CommandMessage> statsMessageCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        verify(messageSender).sendToMgmt(mgmtMessageCaptor.capture());
        verify(messageSender).sendToStats(statsMessageCaptor.capture());

        CommandMessage mgmtMessage = mgmtMessageCaptor.getValue();
        assertExcludeIs(mgmtMessage, SWITCH_ID_1);
        CommandMessage statsMessage = statsMessageCaptor.getValue();
        assertExcludeIs(statsMessage);
    }

    @Test
    public void excludeUpdateTest() {
        when(clock.instant()).thenReturn(Instant.now());
        statsRouterService.handleListSwitchesResponse(getListSwitches(CONTROLLER_ID_1, SWITCH_ID_1));
        statsRouterService.handleListSwitchesResponse(getListSwitches(CONTROLLER_ID_1, SWITCH_ID_2, SWITCH_ID_3));
        statsRouterService.handleStatsRequest(getStatsRequest());

        ArgumentCaptor<CommandMessage> mgmtMessageCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        ArgumentCaptor<CommandMessage> statsMessageCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        verify(messageSender).sendToMgmt(mgmtMessageCaptor.capture());
        verify(messageSender).sendToStats(statsMessageCaptor.capture());

        CommandMessage mgmtMessage = mgmtMessageCaptor.getValue();
        assertExcludeIs(mgmtMessage, SWITCH_ID_2, SWITCH_ID_3);
        CommandMessage statsMessage = statsMessageCaptor.getValue();
        assertExcludeIs(statsMessage);
    }

    @Test
    public void expireTest() {
        Instant event = Instant.now();
        Instant beforeTimeout = event.plusSeconds(3);
        Instant afterTimeout = event.plusSeconds(6);
        when(clock.instant()).thenReturn(event, beforeTimeout, afterTimeout);
        statsRouterService.handleListSwitchesResponse(getListSwitches(CONTROLLER_ID_1, SWITCH_ID_1));
        statsRouterService.handleTick();
        statsRouterService.handleStatsRequest(getStatsRequest());
        statsRouterService.handleTick();
        statsRouterService.handleStatsRequest(getStatsRequest());

        ArgumentCaptor<CommandMessage> mgmtMessageCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        ArgumentCaptor<CommandMessage> statsMessageCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        verify(messageSender, times(2)).sendToMgmt(mgmtMessageCaptor.capture());
        verify(messageSender, times(4)).sendToStats(statsMessageCaptor.capture());

        List<CommandMessage> mgmtMessageList = mgmtMessageCaptor.getAllValues();
        assertExcludeIs(mgmtMessageList.get(0), SWITCH_ID_1);
        assertExcludeIs(mgmtMessageList.get(1));
        List<CommandMessage> statsMessageList = statsMessageCaptor.getAllValues();
        assertTrue(statsMessageList.get(0).getData() instanceof ListSwitchRequest);
        assertExcludeIs(statsMessageList.get(1));
        assertTrue(statsMessageList.get(2).getData() instanceof ListSwitchRequest);
        assertExcludeIs(statsMessageList.get(3));
    }

    @Test
    public void twoFlStatsInstancesTest() {
        when(clock.instant()).thenReturn(Instant.now());
        statsRouterService.handleListSwitchesResponse(getListSwitches(CONTROLLER_ID_1, SWITCH_ID_1));
        statsRouterService.handleListSwitchesResponse(getListSwitches(CONTROLLER_ID_2, SWITCH_ID_2));
        statsRouterService.handleStatsRequest(getStatsRequest());

        ArgumentCaptor<CommandMessage> mgmtMessageCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        ArgumentCaptor<CommandMessage> statsMessageCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        verify(messageSender).sendToMgmt(mgmtMessageCaptor.capture());
        verify(messageSender).sendToStats(statsMessageCaptor.capture());

        CommandMessage mgmtMessage = mgmtMessageCaptor.getValue();
        assertExcludeIs(mgmtMessage, SWITCH_ID_1, SWITCH_ID_2);
        CommandMessage statsMessage = statsMessageCaptor.getValue();
        assertExcludeIs(statsMessage);
    }

    @Test
    public void twoFlStatsInstancesOneExpireTest() {
        Instant controller1 = Instant.now();
        Instant controller2 = controller1.plusSeconds(4);
        Instant tickTime = controller1.plusSeconds(6);
        when(clock.instant()).thenReturn(controller1, controller2, tickTime);
        statsRouterService.handleListSwitchesResponse(getListSwitches(CONTROLLER_ID_1, SWITCH_ID_1));
        statsRouterService.handleListSwitchesResponse(getListSwitches(CONTROLLER_ID_2, SWITCH_ID_2));
        statsRouterService.handleTick();
        statsRouterService.handleStatsRequest(getStatsRequest());

        ArgumentCaptor<CommandMessage> mgmtMessageCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        ArgumentCaptor<CommandMessage> statsMessageCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        verify(messageSender).sendToMgmt(mgmtMessageCaptor.capture());
        verify(messageSender, times(2)).sendToStats(statsMessageCaptor.capture());

        CommandMessage mgmtMessage = mgmtMessageCaptor.getValue();
        assertExcludeIs(mgmtMessage, SWITCH_ID_2);
        List<CommandMessage> statsMessageList = statsMessageCaptor.getAllValues();
        assertTrue(statsMessageList.get(0).getData() instanceof ListSwitchRequest);
        assertExcludeIs(statsMessageList.get(1));
    }

    private CommandMessage getStatsRequest() {
        return new CommandMessage(new StatsRequest(ImmutableList.of()), TIMESTAMP, CORRELATION_ID);
    }

    private InfoMessage getListSwitches(String controllerId, SwitchId... switchIds) {
        return new InfoMessage(new ListSwitchResponse(Arrays.asList(switchIds), controllerId), TIMESTAMP,
                CORRELATION_ID);
    }

    private void assertExcludeIs(CommandMessage message, SwitchId... switchIds) {
        Set<SwitchId> actual = new HashSet<>(((StatsRequest) message.getData()).getExcludeSwitchIds());
        Set<SwitchId> expected = new HashSet<>(Arrays.asList(switchIds));
        assertEquals(expected, actual);
    }
}
