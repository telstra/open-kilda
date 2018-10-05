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

import static org.mockito.Mockito.when;

import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkDumpBeginMarker;
import org.openkilda.messaging.info.discovery.NetworkDumpEndMarker;
import org.openkilda.messaging.info.discovery.NetworkDumpPortData;
import org.openkilda.messaging.info.discovery.NetworkDumpSwitchData;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.event.bolt.FlMonitor.OutputAdapter;
import org.openkilda.wfm.topology.event.model.Sync;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SyncProcessTest {
    @Mock
    OutputAdapter output;

    @Test
    public void filter() {
        CommandContext context = new CommandContext();
        when(output.getContext())
                .thenReturn(context);

        SwitchId swAlpha = new SwitchId("00:01");
        SwitchId swBeta = new SwitchId("00:02");
        SwitchId swGamma = new SwitchId("00:03");

        SyncProcess process = new SyncProcess(output, 1000, 3000);

        long timestamp = System.currentTimeMillis();
        String validCorrelationId = context.getCorrelationId();
        String invalidCorrelationId = validCorrelationId + "-invalid";

        process.input(new InfoMessage(new NetworkDumpBeginMarker(), timestamp, validCorrelationId));

        process.input(new InfoMessage(new NetworkDumpSwitchData(swAlpha), timestamp, validCorrelationId));
        process.input(new InfoMessage(new NetworkDumpPortData(swAlpha, 1), timestamp, validCorrelationId));
        process.input(new InfoMessage(new NetworkDumpSwitchData(swBeta), timestamp, validCorrelationId));
        process.input(new InfoMessage(new NetworkDumpPortData(swBeta, 2), timestamp, validCorrelationId));
        process.input(new InfoMessage(new NetworkDumpPortData(swBeta, 3), timestamp, validCorrelationId));

        process.input(new InfoMessage(new NetworkDumpSwitchData(swGamma), timestamp, invalidCorrelationId));
        process.input(new InfoMessage(new NetworkDumpPortData(swGamma, 4), timestamp, invalidCorrelationId));

        Assert.assertFalse(process.isComplete());
        process.input(new InfoMessage(new NetworkDumpEndMarker(), timestamp, invalidCorrelationId));

        Assert.assertFalse(process.isComplete());
        process.input(new InfoMessage(new NetworkDumpEndMarker(), timestamp, validCorrelationId));

        Sync actual = process.getPayload();
        Sync expected = new Sync();
        expected.addActiveSwitch(swAlpha);
        expected.addActivePort(swAlpha, 1);
        expected.addActiveSwitch(swBeta);
        expected.addActivePort(swBeta, 2);
        expected.addActivePort(swBeta, 3);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void stale() throws Exception {
        CommandContext context = new CommandContext();
        when(output.getContext())
                .thenReturn(context);

        SyncProcess process = new SyncProcess(output, 1000, 0);
        Assert.assertFalse(process.isStale(900));
        Assert.assertTrue(process.isStale(1100));
    }
}
