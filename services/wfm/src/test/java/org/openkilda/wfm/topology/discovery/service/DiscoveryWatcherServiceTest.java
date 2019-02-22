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

package org.openkilda.wfm.topology.discovery.service;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.discovery.model.Endpoint;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DiscoveryWatcherServiceTest {
    private final Integer taskId = 0;

    @Mock
    IWatcherCarrier carrier;

    @Before
    public void setup() {
        reset(carrier);
    }

    @Test
    public void addWatch() {
        DiscoveryWatcherService w = new DiscoveryWatcherService(10, taskId);
        w.addWatch(carrier, Endpoint.of(new SwitchId(1), 1), 1);
        w.addWatch(carrier, Endpoint.of(new SwitchId(1), 2), 1);
        w.addWatch(carrier, Endpoint.of(new SwitchId(2), 1), 2);
        w.addWatch(carrier, Endpoint.of(new SwitchId(2), 1), 2);
        w.addWatch(carrier, Endpoint.of(new SwitchId(2), 2), 3);

        assertThat(w.getConfirmedPackets().size(), is(0));
        assertThat(w.getTimeouts().size(), is(3));

        verify(carrier, times(5)).sendDiscovery(any(DiscoverIslCommandData.class));
    }

    @Test
    public void removeWatch() {
        //TODO: not critical
    }

    @Test
    public void tick() {
        DiscoveryWatcherService w = new DiscoveryWatcherService(10, taskId);
        w.addWatch(carrier, Endpoint.of(new SwitchId(1), 1), 1);
        w.addWatch(carrier, Endpoint.of(new SwitchId(1), 2), 1);
        w.addWatch(carrier, Endpoint.of(new SwitchId(2), 1), 2);
        w.addWatch(carrier, Endpoint.of(new SwitchId(2), 1), 2);
        w.addWatch(carrier, Endpoint.of(new SwitchId(2), 2), 3);

        assertThat(w.getConfirmedPackets().size(), is(0));
        assertThat(w.getTimeouts().size(), is(3));
        verify(carrier, times(5)).sendDiscovery(any(DiscoverIslCommandData.class));

        w.confirmation(Endpoint.of(new SwitchId(1), 1), 0);
        w.confirmation(Endpoint.of(new SwitchId(2), 1), 2);

        assertThat(w.getConfirmedPackets().size(), is(2));

        w.tick(carrier, 100);

        assertThat(w.getConfirmedPackets().size(), is(0));

        verify(carrier).discoveryFailed(eq(Endpoint.of(new SwitchId(1), 1)), anyLong());
        verify(carrier).discoveryFailed(eq(Endpoint.of(new SwitchId(2), 1)), anyLong());
        verify(carrier, times(2)).discoveryFailed(any(Endpoint.class), anyLong());

        assertThat(w.getTimeouts().size(), is(0));
    }

    @Test
    public void discovery() {
        DiscoveryWatcherService w = new DiscoveryWatcherService(10, taskId);
        w.addWatch(carrier, Endpoint.of(new SwitchId(1), 1), 1);
        w.addWatch(carrier, Endpoint.of(new SwitchId(1), 2), 1);
        w.addWatch(carrier, Endpoint.of(new SwitchId(2), 1), 2);
        w.addWatch(carrier, Endpoint.of(new SwitchId(2), 1), 2);
        w.addWatch(carrier, Endpoint.of(new SwitchId(2), 2), 3);

        assertThat(w.getConfirmedPackets().size(), is(0));
        assertThat(w.getTimeouts().size(), is(3));
        verify(carrier, times(5)).sendDiscovery(any(DiscoverIslCommandData.class));

        w.confirmation(Endpoint.of(new SwitchId(1), 1), 0);
        w.confirmation(Endpoint.of(new SwitchId(2), 1), 2);
        assertThat(w.getConfirmedPackets().size(), is(2));

        PathNode source = new PathNode(new SwitchId(1), 1, 0);
        PathNode destination = new PathNode(new SwitchId(2), 1, 0);

        IslInfoData islAlphaBeta = IslInfoData.builder().source(source).destination(destination).packetId(0L).build();
        IslInfoData islBetaAlpha = IslInfoData.builder().source(destination).destination(source).packetId(2L).build();
        w.discovery(carrier, islAlphaBeta);
        w.discovery(carrier, islBetaAlpha);

        w.tick(carrier, 100);

        assertThat(w.getConfirmedPackets().size(), is(0));

        verify(carrier).discoveryReceived(eq(new Endpoint(islAlphaBeta.getSource())), eq(islAlphaBeta), anyLong());
        verify(carrier).discoveryReceived(eq(new Endpoint(islBetaAlpha.getSource())), eq(islBetaAlpha), anyLong());
        verify(carrier, times(2)).discoveryReceived(any(Endpoint.class), any(IslInfoData.class), anyLong());

        assertThat(w.getTimeouts().size(), is(0));
    }

    @Test
    public void discoveryBeforeConfirmation() {
        final int awaitTime = 10;
        PathNode source = new PathNode(new SwitchId(1), 1, 0);
        PathNode destination = new PathNode(new SwitchId(2), 1, 0);

        DiscoveryWatcherService w = new DiscoveryWatcherService(awaitTime, taskId);
        w.addWatch(carrier, Endpoint.of(source.getSwitchId(), source.getPortNo()), 1);

        verify(carrier, times(1)).sendDiscovery(any(DiscoverIslCommandData.class));

        IslInfoData islAlphaBeta = IslInfoData.builder().source(source).destination(destination).packetId(0L).build();

        w.discovery(carrier, islAlphaBeta);
        w.confirmation(new Endpoint(source), 0);
        w.tick(carrier, awaitTime + 1);

        verify(carrier).discoveryReceived(eq(new Endpoint(source)), eq(islAlphaBeta), anyLong());
        verify(carrier, never()).discoveryFailed(eq(new Endpoint(source)), anyLong());

        assertThat(w.getConfirmedPackets().size(), is(0));
    }
}
