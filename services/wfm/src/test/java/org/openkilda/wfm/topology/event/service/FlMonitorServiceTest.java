/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.event.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.command.discovery.NetworkCommandData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkDumpEndMarker;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.event.bolt.FlMonitor.OutputAdapter;
import org.openkilda.wfm.topology.event.model.Sync;

import org.junit.Before;
import org.junit.Test;

public class FlMonitorServiceTest {
    private CommandContext context;
    private FlMonitorService monitor;

    @Before
    public void setUp() throws Exception {
        context = new CommandContext();
        monitor = new FlMonitorService(3000L, 4000L, 0);
    }

    @Test
    public void syncProxyLost() {
        // sync
        OutputAdapter output = makeOutputMock();
        monitor.timerTick(output, 0);
        verify(output).speakerCommand(any(NetworkCommandData.class));

        monitor.speakerMessage(output, new InfoMessage(new NetworkDumpEndMarker(), 0, context.getCorrelationId()));
        verify(output).shareSync(any(Sync.class));

        // proxy
        output = makeOutputMock();
        monitor.speakerMessage(output, new InfoMessage(
                new PortInfoData(new SwitchId(1L), 1, PortChangeType.UP), 0, "port-up-message"));
        verify(output).proxyCurrentTuple();

        // lost
        output = makeOutputMock();
        monitor.timerTick(output, 1000);
        monitor.timerTick(output, 6000);
        monitor.speakerMessage(output, new InfoMessage(
                new PortInfoData(new SwitchId(1L), 1, PortChangeType.UP), 0, "port-up-message"));
        verify(output, never()).proxyCurrentTuple();
    }

    @Test
    public void repeatableDumpRequests() {
        OutputAdapter output = makeOutputMock();
        monitor.timerTick(output, 1000L);
        verify(output).speakerCommand(any(NetworkCommandData.class));

        output = makeOutputMock();
        monitor.timerTick(output, 3000L);
        verify(output, never()).speakerCommand(any(NetworkCommandData.class));
        monitor.timerTick(output, 6000L);
        verify(output).speakerCommand(any(NetworkCommandData.class));
    }

    OutputAdapter makeOutputMock() {
        OutputAdapter output = mock(OutputAdapter.class);
        when(output.getContext())
                .thenReturn(context);
        return output;
    }
}
